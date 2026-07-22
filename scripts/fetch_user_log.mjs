#!/usr/bin/env node

import {
    constants,
    createDecipheriv,
    createHash,
    createPrivateKey,
    createPublicKey,
    privateDecrypt,
} from "node:crypto";
import {
    chmod,
    mkdir,
    readFile,
    readdir,
    stat,
    writeFile,
} from "node:fs/promises";
import { homedir } from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { gunzipSync } from "node:zlib";

const SCRIPT_DIR = path.dirname(fileURLToPath(import.meta.url));
const ROOT_DIR = path.resolve(SCRIPT_DIR, "..");
const DEFAULT_KEY_DIR = path.resolve(ROOT_DIR, "../Loger_key");
const DEFAULT_DECRYPTER_DIR = path.resolve(ROOT_DIR, "../log-decrypter");
const DEFAULT_UPLOADER_DIR = path.join(DEFAULT_KEY_DIR, "LogUploader", "LogUploader");
const DEFAULT_ENV_FILE = path.join(DEFAULT_UPLOADER_DIR, ".env");
const DEFAULT_OUTPUT_DIR = path.join(ROOT_DIR, "artifacts", "user_logs");
const ADMIN_QUERY_URL = "https://api.instantdb.com/admin/query";

function usage() {
    console.log(`Usage:
  scripts/fetch_user_log.sh [<6-digit-code>] [options]

Options:
  --log-id <uuid>       Select one record when a pickup code has duplicates.
  --output <path>       Write the decrypted log to this exact path.
  --output-dir <dir>    Output directory (default: artifacts/user_logs).
  --env-file <path>     InstantDB credential file (default: ../Loger_key/LogUploader/LogUploader/.env).
  --key-dir <dir>       Add a directory to search recursively for private keys. Repeatable.
  -h, --help            Show this help message.

Environment:
  INSTANT_APP_ID / VITE_INSTANT_APP_ID
  INSTANT_ADMIN_TOKEN / VITE_INSTANT_ADMIN_TOKEN
  WATCHRSS_LOG_ENV_FILE
  WATCHRSS_LOG_KEY_DIRS      Colon-separated extra private-key directories.
  WATCHRSS_LOG_OUTPUT_DIR

The script queries InstantDB, downloads the linked encrypted file, then tries
all discovered private PEM keys from newest to oldest. Credentials and private
keys remain outside this repository. When run interactively without arguments,
the shell wrapper prompts for the pickup code.`);
}

function parseArgs(argv) {
    const options = {
        code: null,
        envFile: process.env.WATCHRSS_LOG_ENV_FILE || DEFAULT_ENV_FILE,
        keyDirs: [],
        logId: null,
        output: null,
        outputDir: process.env.WATCHRSS_LOG_OUTPUT_DIR || DEFAULT_OUTPUT_DIR,
    };

    for (let index = 0; index < argv.length; index += 1) {
        const argument = argv[index];
        switch (argument) {
            case "-h":
            case "--help":
                usage();
                process.exit(0);
                break;
            case "--env-file":
                options.envFile = requireOptionValue(argv, ++index, argument);
                break;
            case "--key-dir":
                options.keyDirs.push(requireOptionValue(argv, ++index, argument));
                break;
            case "--log-id":
                options.logId = requireOptionValue(argv, ++index, argument);
                break;
            case "--output":
                options.output = requireOptionValue(argv, ++index, argument);
                break;
            case "--output-dir":
                options.outputDir = requireOptionValue(argv, ++index, argument);
                break;
            default:
                if (argument.startsWith("-")) {
                    throw new Error(`Unknown option: ${argument}`);
                }
                if (options.code !== null) {
                    throw new Error(`Unexpected argument: ${argument}`);
                }
                options.code = argument;
        }
    }

    if (!options.code) {
        throw new Error("A 6-digit pickup code is required.");
    }
    if (!/^\d{6}$/.test(options.code)) {
        throw new Error(`Invalid pickup code: ${options.code}`);
    }

    const extraKeyDirs = (process.env.WATCHRSS_LOG_KEY_DIRS || "")
        .split(path.delimiter)
        .filter(Boolean);
    options.keyDirs = [
        DEFAULT_KEY_DIR,
        DEFAULT_DECRYPTER_DIR,
        ...extraKeyDirs,
        ...options.keyDirs,
    ].map(expandHome);
    options.envFile = expandHome(options.envFile);
    options.outputDir = expandHome(options.outputDir);
    options.output = options.output ? expandHome(options.output) : null;
    return options;
}

function requireOptionValue(argv, index, option) {
    const value = argv[index];
    if (!value || value.startsWith("--")) {
        throw new Error(`${option} requires a value.`);
    }
    return value;
}

function expandHome(value) {
    if (value === "~") {
        return homedir();
    }
    if (value.startsWith(`~${path.sep}`)) {
        return path.join(homedir(), value.slice(2));
    }
    return path.resolve(value);
}

function parseDotEnv(contents) {
    const values = {};
    for (const rawLine of contents.split(/\r?\n/)) {
        const line = rawLine.trim();
        if (!line || line.startsWith("#")) {
            continue;
        }
        const separator = line.indexOf("=");
        if (separator < 1) {
            continue;
        }
        const name = line.slice(0, separator).trim();
        let value = line.slice(separator + 1).trim();
        if (
            value.length >= 2
            && ((value.startsWith('"') && value.endsWith('"'))
                || (value.startsWith("'") && value.endsWith("'")))
        ) {
            value = value.slice(1, -1);
        }
        values[name] = value;
    }
    return values;
}

async function loadCredentials(envFile) {
    let fileValues = {};
    try {
        fileValues = parseDotEnv(await readFile(envFile, "utf8"));
    } catch (error) {
        if (error.code !== "ENOENT") {
            throw error;
        }
    }

    const appId = process.env.INSTANT_APP_ID
        || process.env.VITE_INSTANT_APP_ID
        || fileValues.INSTANT_APP_ID
        || fileValues.VITE_INSTANT_APP_ID;
    const adminToken = process.env.INSTANT_ADMIN_TOKEN
        || process.env.VITE_INSTANT_ADMIN_TOKEN
        || fileValues.INSTANT_ADMIN_TOKEN
        || fileValues.VITE_INSTANT_ADMIN_TOKEN;

    if (!appId || !adminToken) {
        throw new Error(
            `InstantDB credentials were not found in the environment or ${envFile}.`,
        );
    }
    return { adminToken, appId };
}

async function instantQuery(credentials, query) {
    const response = await fetch(ADMIN_QUERY_URL, {
        method: "POST",
        headers: {
            "App-Id": credentials.appId,
            Authorization: `Bearer ${credentials.adminToken}`,
            "Content-Type": "application/json",
        },
        body: JSON.stringify({ query }),
    });
    if (!response.ok) {
        const detail = (await response.text()).slice(0, 500);
        throw new Error(`InstantDB query failed (${response.status}): ${detail}`);
    }
    return response.json();
}

function printDuplicateMatches(rows) {
    console.error("Pickup code matched multiple records. Select one with --log-id:");
    for (const row of rows) {
        console.error(
            `  ${row.id}  ${row.createdAt || "unknown time"}`
            + `  ${row.fileSize ?? "?"} bytes  ${row.charCount ?? "?"} chars`,
        );
    }
}

async function selectLog(credentials, code, logId) {
    const result = await instantQuery(credentials, {
        logs: {
            $: { where: { code } },
        },
    });
    let rows = result.logs || [];
    if (logId) {
        rows = rows.filter((row) => row.id === logId);
    }
    if (rows.length === 0) {
        throw new Error(
            logId
                ? `No log matched pickup code ${code} and log ID ${logId}.`
                : `No log matched pickup code ${code}.`,
        );
    }
    if (rows.length > 1) {
        printDuplicateMatches(rows);
        throw new Error("Refusing to choose between duplicate pickup codes.");
    }
    return rows[0];
}

async function selectEncryptedFile(credentials, logId) {
    const result = await instantQuery(credentials, {
        $files: {
            $: { where: { "log.id": logId } },
        },
    });
    const files = result.$files || [];
    if (files.length !== 1) {
        throw new Error(`Expected one encrypted file for log ${logId}, found ${files.length}.`);
    }
    if (!files[0].url) {
        throw new Error(`Encrypted file ${files[0].id} has no download URL.`);
    }
    return files[0];
}

async function downloadEncryptedFile(file, expectedSize) {
    const response = await fetch(file.url);
    if (!response.ok) {
        throw new Error(`Encrypted-file download failed (${response.status}).`);
    }
    const buffer = Buffer.from(await response.arrayBuffer());
    if (expectedSize != null && buffer.length !== expectedSize) {
        throw new Error(
            `Encrypted-file size mismatch: expected ${expectedSize}, downloaded ${buffer.length}.`,
        );
    }
    return buffer;
}

function isPemFilename(filename) {
    return /\.pem(?:\..+)?$/i.test(filename);
}

async function walkPrivateKeys(directory, candidates, visitedDirectories) {
    let directoryStat;
    try {
        directoryStat = await stat(directory);
    } catch (error) {
        if (error.code === "ENOENT") {
            return;
        }
        throw error;
    }
    if (!directoryStat.isDirectory()) {
        throw new Error(`Private-key search path is not a directory: ${directory}`);
    }

    const realDirectory = path.resolve(directory);
    if (visitedDirectories.has(realDirectory)) {
        return;
    }
    visitedDirectories.add(realDirectory);

    const entries = await readdir(directory, { withFileTypes: true });
    for (const entry of entries) {
        if (entry.name === "node_modules" || entry.name === ".git" || entry.name === "__MACOSX") {
            continue;
        }
        const entryPath = path.join(directory, entry.name);
        if (entry.isDirectory()) {
            await walkPrivateKeys(entryPath, candidates, visitedDirectories);
        } else if (entry.isFile() && isPemFilename(entry.name)) {
            const entryStat = await stat(entryPath);
            candidates.push({ mtimeMs: entryStat.mtimeMs, path: entryPath });
        }
    }
}

async function discoverPrivateKeys(keyDirs) {
    const candidates = [];
    const visitedDirectories = new Set();
    for (const directory of keyDirs) {
        await walkPrivateKeys(directory, candidates, visitedDirectories);
    }
    candidates.sort((left, right) => right.mtimeMs - left.mtimeMs);

    const uniqueKeys = [];
    const fingerprints = new Set();
    for (const candidate of candidates) {
        try {
            const pem = await readFile(candidate.path, "utf8");
            const privateKey = createPrivateKey(pem);
            const publicDer = createPublicKey(privateKey).export({
                format: "der",
                type: "spki",
            });
            const fingerprint = createHash("sha256").update(publicDer).digest("hex");
            if (!fingerprints.has(fingerprint)) {
                fingerprints.add(fingerprint);
                uniqueKeys.push({ ...candidate, fingerprint, privateKey });
            }
        } catch {
            // Ignore files whose names look relevant but are not usable private PEM keys.
        }
    }
    return uniqueKeys;
}

function decryptWithKey(encryptedFile, encryptedAesKey, privateKey) {
    if (encryptedFile.length < 18) {
        throw new Error("Encrypted file is too short.");
    }
    const ivLength = encryptedFile[0];
    if (ivLength < 1 || ivLength > 32 || encryptedFile.length <= 1 + ivLength + 16) {
        throw new Error(`Invalid encrypted-file IV length: ${ivLength}.`);
    }

    const aesKey = privateDecrypt(
        {
            key: privateKey,
            oaepHash: "sha256",
            padding: constants.RSA_PKCS1_OAEP_PADDING,
        },
        Buffer.from(encryptedAesKey, "base64"),
    );
    if (aesKey.length !== 32) {
        throw new Error(`Unexpected AES key length: ${aesKey.length}.`);
    }

    const iv = encryptedFile.subarray(1, 1 + ivLength);
    const encryptedPayload = encryptedFile.subarray(1 + ivLength);
    const authenticationTag = encryptedPayload.subarray(-16);
    const ciphertext = encryptedPayload.subarray(0, -16);
    const decipher = createDecipheriv("aes-256-gcm", aesKey, iv);
    decipher.setAuthTag(authenticationTag);
    const compressed = Buffer.concat([decipher.update(ciphertext), decipher.final()]);
    return gunzipSync(compressed);
}

function tryPrivateKeys(encryptedFile, encryptedAesKey, privateKeys) {
    for (let index = 0; index < privateKeys.length; index += 1) {
        const candidate = privateKeys[index];
        console.log(`Trying private key ${index + 1}/${privateKeys.length}: ${candidate.path}`);
        try {
            const plaintext = decryptWithKey(
                encryptedFile,
                encryptedAesKey,
                candidate.privateKey,
            );
            return { candidate, plaintext };
        } catch {
            // A key mismatch is expected while walking key history.
        }
    }
    throw new Error(`None of the ${privateKeys.length} discovered private keys could decrypt the log.`);
}

function safeTimestamp(value) {
    const normalized = String(value || "unknown-time")
        .replace(/[^0-9A-Za-z]+/g, "-")
        .replace(/^-+|-+$/g, "");
    return normalized || "unknown-time";
}

async function writeSecureOutput(options, log, plaintext) {
    const decoder = new TextDecoder("utf-8", { fatal: true });
    const text = decoder.decode(plaintext);
    if (log.charCount != null && text.length !== log.charCount) {
        throw new Error(
            `Decrypted character count mismatch: expected ${log.charCount}, got ${text.length}.`,
        );
    }

    const hasExplicitOutput = Boolean(options.output);
    let outputPath = options.output;
    if (!hasExplicitOutput) {
        await mkdir(options.outputDir, { mode: 0o700, recursive: true });
        await chmod(options.outputDir, 0o700);
        const filename = [
            "watchrss-log",
            options.code,
            safeTimestamp(log.createdAt),
            log.id.slice(0, 8),
        ].join("-") + ".txt";
        outputPath = path.join(options.outputDir, filename);
    } else {
        await mkdir(path.dirname(outputPath), { mode: 0o700, recursive: true });
    }

    const parsedPath = path.parse(outputPath);
    for (let suffix = 0; ; suffix += 1) {
        const candidatePath = suffix === 0
            ? outputPath
            : path.join(parsedPath.dir, `${parsedPath.name}-${suffix}${parsedPath.ext}`);
        try {
            await writeFile(candidatePath, text, { flag: "wx", mode: 0o600 });
            outputPath = candidatePath;
            break;
        } catch (error) {
            if (error.code !== "EEXIST") {
                throw error;
            }
            if (hasExplicitOutput) {
                throw new Error(`Refusing to overwrite existing output: ${outputPath}`);
            }
        }
    }
    await chmod(outputPath, 0o600);
    return { outputPath, text };
}

async function main() {
    const options = parseArgs(process.argv.slice(2));
    const credentials = await loadCredentials(options.envFile);

    console.log(`Querying InstantDB for pickup code ${options.code}...`);
    const log = await selectLog(credentials, options.code, options.logId);
    console.log(
        `Found log ${log.id} (${log.createdAt || "unknown time"}, `
        + `${log.charCount ?? "?"} characters).`,
    );

    const encryptedFileRecord = await selectEncryptedFile(credentials, log.id);
    console.log(`Downloading ${encryptedFileRecord.path || encryptedFileRecord.id}...`);
    const encryptedFile = await downloadEncryptedFile(encryptedFileRecord, log.fileSize);

    const privateKeys = await discoverPrivateKeys(options.keyDirs);
    if (privateKeys.length === 0) {
        throw new Error(`No private PEM keys were found under: ${options.keyDirs.join(", ")}`);
    }
    console.log(`Discovered ${privateKeys.length} unique private keys (newest first).`);
    const { candidate, plaintext } = tryPrivateKeys(
        encryptedFile,
        log.encryptedAesKey,
        privateKeys,
    );
    const { outputPath, text } = await writeSecureOutput(options, log, plaintext);

    console.log(`Decryption succeeded with: ${candidate.path}`);
    console.log(`Output: ${outputPath}`);
    console.log(`Lines: ${text.split("\n").length}; characters: ${text.length}`);
}

main().catch((error) => {
    console.error(`Error: ${error.message}`);
    process.exitCode = 1;
});
