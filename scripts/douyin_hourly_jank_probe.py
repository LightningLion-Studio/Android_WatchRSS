#!/usr/bin/env python3
import argparse
import datetime as dt
import json
import re
import subprocess
import sys
import time
from dataclasses import dataclass
from pathlib import Path


APP_PACKAGE = "com.lightningstudio.watchrss"
DEBUG_ACTIVITY = f"{APP_PACKAGE}/.DouyinDebugEntryActivity"
DEBUG_RECEIVER = f"{APP_PACKAGE}/com.lightningstudio.watchrss.debug.DouyinPlaybackDebugReceiver"
DEBUG_OPEN_ACTION = "com.lightningstudio.watchrss.debug.action.OPEN_DOUYIN"
DEBUG_AUTO_ENTER_EXTRA = "watchrss.debug.auto_enter_douyin_flow"
ADVANCE_ACTION = "com.lightningstudio.watchrss.debug.action.ADVANCE_TO_NEXT_DOUYIN_VIDEO"
PROVIDER_COMPONENT = f"{APP_PACKAGE}/.debug.DebugLogProvider"
PROVIDER_SENTINEL = "DOUYIN_PLAYBACK_DEBUG"

LOGCAT_FILTERS = [
    "DouyinImmersive:D",
    "DouyinPreviewCache:D",
    "DouyinPreviewDataSource:D",
    "DouyinPlaybackDebug:D",
    "*:S",
]

START_RE = re.compile(
    r"^(?P<date>\d\d-\d\d) (?P<time>\d\d:\d\d:\d\d\.\d{3}).*"
    r"DouyinImmersive.*TEST_EVENT playback_started awemeId=(?P<aweme>\d+) "
    r"mode=(?P<mode>[\w_]+) slot=(?P<slot>\w+)"
)
SETTLED_RE = re.compile(
    r"^(?P<date>\d\d-\d\d) (?P<time>\d\d:\d\d:\d\d\.\d{3}).*"
    r"DouyinImmersive.*TEST_EVENT page_settled awemeId=(?P<aweme>\d*) page=(?P<page>\d+)"
)
FAIL_RE = re.compile(
    r"^(?P<date>\d\d-\d\d) (?P<time>\d\d:\d\d:\d\d\.\d{3}).*"
    r"DouyinImmersive.*TEST_EVENT playback_failed awemeId=(?P<aweme>\d+) "
    r"slot=(?P<slot>\w+) retryCount=(?P<retry>\d+)"
)
ERROR_RE = re.compile(r"DouyinImmersive.*playback error|DouyinPreviewCache.*prefetch failed")
MEMINFO_APP_SUMMARY_RE = re.compile(r"^\s*(?P<name>[A-Za-z ]+):\s+(?P<pss>\d+)(?:\s+.*?TOTAL RSS:\s+(?P<rss>\d+))?")
MEMINFO_TOTAL_PSS_RE = re.compile(r"TOTAL PSS:\s*(?P<pss>\d+)")
MEMINFO_TOTAL_RSS_RE = re.compile(r"TOTAL RSS:\s*(?P<rss>\d+)")
MEMINFO_OBJECT_RE = re.compile(r"(?P<name>Views|ViewRootImpl|AppContexts|Activities|Assets|AssetManagers):\s*(?P<count>\d+)")

MEMORY_TSV_COLUMNS = [
    "step",
    "device_time",
    "status",
    "reason",
    "total_pss_kb",
    "total_rss_kb",
    "java_heap_pss_kb",
    "native_heap_pss_kb",
    "graphics_pss_kb",
    "code_pss_kb",
    "stack_pss_kb",
    "private_other_pss_kb",
    "system_pss_kb",
    "java_heap_alloc_kb",
    "native_heap_alloc_kb",
    "java_heap_free_kb",
    "native_heap_free_kb",
    "views",
    "view_roots",
    "activities",
    "app_contexts",
    "assets",
    "asset_managers",
    "raw_meminfo",
]


@dataclass(frozen=True)
class PlaybackStart:
    aweme_id: str
    mode: str
    slot: str
    at: dt.datetime


@dataclass(frozen=True)
class PageSettled:
    aweme_id: str
    page: int
    at: dt.datetime


@dataclass(frozen=True)
class PlaybackFailure:
    aweme_id: str
    slot: str
    retry_count: int
    at: dt.datetime


def run_text(cmd: list[str], check: bool = True, timeout: float | None = None) -> subprocess.CompletedProcess[str]:
    return subprocess.run(cmd, check=check, capture_output=True, text=True, timeout=timeout)


def run_bytes(cmd: list[str], check: bool = True, timeout: float | None = None) -> subprocess.CompletedProcess[bytes]:
    return subprocess.run(cmd, check=check, capture_output=True, timeout=timeout)


def adb_args(args: list[str], serial: str | None) -> list[str]:
    cmd = ["adb"]
    if serial:
        cmd += ["-s", serial]
    cmd += args
    return cmd


def adb(args: list[str], serial: str | None = None, check: bool = True, timeout: float | None = None) -> str:
    return run_text(adb_args(args, serial), check=check, timeout=timeout).stdout


def adb_shell(args: list[str], serial: str | None = None, check: bool = True, timeout: float | None = None) -> str:
    return adb(["shell", *args], serial=serial, check=check, timeout=timeout)


def parse_ts(date_str: str, time_str: str) -> dt.datetime:
    year = dt.datetime.now().year
    return dt.datetime.strptime(f"{year}-{date_str} {time_str}", "%Y-%m-%d %H:%M:%S.%f")


def device_now(serial: str | None) -> dt.datetime:
    raw = adb_shell(["date", "+%m-%dT%H:%M:%S.%3N"], serial=serial).strip()
    year = dt.datetime.now().year
    return dt.datetime.strptime(f"{year}-{raw}", "%Y-%m-%dT%H:%M:%S.%f")


def iso_ms(value: dt.datetime | None) -> str | None:
    if value is None:
        return None
    return value.isoformat(timespec="milliseconds")


def resolve_serial(serial: str | None) -> str:
    if serial:
        return serial
    output = adb(["devices"], check=True)
    devices = []
    for line in output.splitlines()[1:]:
        parts = line.split()
        if len(parts) >= 2 and parts[1] == "device":
            devices.append(parts[0])
    if not devices:
        raise RuntimeError("No adb device is available.")
    if len(devices) > 1:
        joined = ", ".join(devices)
        raise RuntimeError(f"Multiple adb devices are available; set --serial. devices={joined}")
    return devices[0]


def wait_for_boot(serial: str, timeout_s: float) -> None:
    deadline = time.monotonic() + timeout_s
    while time.monotonic() < deadline:
        state = adb(["get-state"], serial=serial, check=False).strip()
        booted = adb_shell(["getprop", "sys.boot_completed"], serial=serial, check=False).strip()
        if state == "device" and booted == "1":
            return
        time.sleep(1)
    raise RuntimeError(f"Timed out waiting for adb boot completion on {serial}.")


def get_display_size(serial: str) -> tuple[int, int]:
    raw = adb_shell(["wm", "size"], serial=serial)
    match = re.search(r"Override size:\s*(\d+)x(\d+)", raw)
    if match is None:
        match = re.search(r"Physical size:\s*(\d+)x(\d+)", raw)
    if match is None:
        raise RuntimeError(f"Unable to parse display size from: {raw.strip()}")
    return int(match.group(1)), int(match.group(2))


def launch_douyin_flow(serial: str, warmup_s: float) -> None:
    adb_shell(["input", "keyevent", "KEYCODE_WAKEUP"], serial=serial, check=False)
    adb_shell(["wm", "dismiss-keyguard"], serial=serial, check=False)
    adb_shell(["input", "keyevent", "82"], serial=serial, check=False)
    adb_shell(
        [
            "am",
            "start",
            "-n",
            DEBUG_ACTIVITY,
            "-a",
            DEBUG_OPEN_ACTION,
            "--ez",
            DEBUG_AUTO_ENTER_EXTRA,
            "true",
        ],
        serial=serial,
    )
    time.sleep(max(0.0, warmup_s))


def fetch_debug_text(serial: str) -> tuple[str, str | None]:
    completed = run_text(
        adb_args(
            ["shell", "dumpsys", "activity", "provider", PROVIDER_COMPONENT, "--douyin-playback"],
            serial,
        ),
        check=False,
        timeout=10,
    )
    if completed.returncode != 0:
        return "", completed.stderr.strip() or completed.stdout.strip() or f"adb exited with {completed.returncode}"
    lines = completed.stdout.splitlines()
    try:
        start = next(index for index, line in enumerate(lines) if PROVIDER_SENTINEL in line)
    except StopIteration:
        body = completed.stdout.strip()
        return "", "debug provider returned no playback sentinel" if body else "debug provider returned empty output"
    extracted = []
    for line in lines[start + 1 :]:
        extracted.append(line[6:] if line.startswith("      ") else line.lstrip())
    return "\n".join(extracted).strip(), None


def parse_debug_state(debug_text: str, error: str | None) -> dict[str, object]:
    state: dict[str, object] = {
        "provider_error": error,
        "raw": debug_text,
        "is_loading": False,
        "is_error": False,
    }
    if error or not debug_text.strip():
        state["is_loading"] = True
        return state

    lines = debug_text.splitlines()
    for line in lines:
        if line.startswith("Douyin debug"):
            page_match = re.search(r"page=(?P<page>\d+) settled=(?P<settled>\d+) scroll=(?P<scroll>[01]) fg=(?P<fg>\w)", line)
            if page_match:
                state["page"] = int(page_match.group("page"))
                state["settled_page"] = int(page_match.group("settled"))
                state["is_scroll_in_progress"] = page_match.group("scroll") == "1"
                state["foreground_slot_key"] = page_match.group("fg")
        elif line.startswith("active="):
            active_match = re.search(r"active=(?P<active>\S+) prepared=(?P<prepared>\S+) ram=(?P<ram>\S+) gen=(?P<gen>\S+)", line)
            if active_match:
                state["active_aweme_short"] = active_match.group("active")
                state["prepared_aweme_short"] = active_match.group("prepared")
                state["ram"] = active_match.group("ram")
                state["generation"] = active_match.group("gen")
        elif line.startswith("- * "):
            parts = line.split()
            if len(parts) >= 9:
                flags_index = 8 if len(parts) >= 10 and parts[7].endswith("%") else 7
                flags = parts[flags_index] if len(parts) > flags_index else "-"
                flags_set = set() if flags == "-" else set(flags.split(","))
                slot_state = {
                    "slot": parts[2],
                    "aweme_short": parts[3],
                    "codec": parts[4],
                    "source": parts[5],
                    "progress": " ".join(parts[6:flags_index]),
                    "flags": sorted(flags_set),
                    "uri_short": parts[flags_index + 1] if len(parts) > flags_index + 1 else "-",
                }
                state["foreground_slot"] = slot_state
                state["is_error"] = "err" in flags_set
                state["is_loading"] = (
                    "err" in flags_set or
                    ("buf" in flags_set and "play" not in flags_set and "ff" not in flags_set)
                )
    return state


def read_filtered_logcat(serial: str) -> str:
    return adb(["logcat", "-d", "-v", "time", *LOGCAT_FILTERS], serial=serial, check=False, timeout=20)


def collect_events(log_text: str) -> tuple[list[PlaybackStart], list[PageSettled], list[PlaybackFailure], list[str]]:
    starts: list[PlaybackStart] = []
    settled: list[PageSettled] = []
    failures: list[PlaybackFailure] = []
    errors: list[str] = []
    for line in log_text.splitlines():
        start_match = START_RE.search(line)
        if start_match:
            starts.append(
                PlaybackStart(
                    aweme_id=start_match.group("aweme"),
                    mode=start_match.group("mode"),
                    slot=start_match.group("slot"),
                    at=parse_ts(start_match.group("date"), start_match.group("time")),
                )
            )
            continue
        settled_match = SETTLED_RE.search(line)
        if settled_match:
            settled.append(
                PageSettled(
                    aweme_id=settled_match.group("aweme"),
                    page=int(settled_match.group("page")),
                    at=parse_ts(settled_match.group("date"), settled_match.group("time")),
                )
            )
            continue
        fail_match = FAIL_RE.search(line)
        if fail_match:
            failures.append(
                PlaybackFailure(
                    aweme_id=fail_match.group("aweme"),
                    slot=fail_match.group("slot"),
                    retry_count=int(fail_match.group("retry")),
                    at=parse_ts(fail_match.group("date"), fail_match.group("time")),
                )
            )
            continue
        if ERROR_RE.search(line):
            errors.append(line)
    return starts, settled, failures, errors


def summarize_step_events(log_text: str, swipe_at: dt.datetime) -> dict[str, object]:
    starts, settled_events, failures, errors = collect_events(log_text)
    search_floor = swipe_at - dt.timedelta(milliseconds=250)
    settled = next((event for event in settled_events if event.at >= search_floor), None)
    target_aweme = settled.aweme_id if settled and settled.aweme_id else None
    matching_starts = [
        event for event in starts
        if event.at >= search_floor and (target_aweme is None or event.aweme_id == target_aweme)
    ]
    initial_start = matching_starts[0] if matching_starts else None
    resolved_start = next((event for event in matching_starts if event.mode != "settled_buffering"), None)
    failure = next(
        (
            event for event in failures
            if event.at >= search_floor and (target_aweme is None or event.aweme_id == target_aweme)
        ),
        None,
    )
    resolved_latency = None
    initial_latency = None
    if initial_start:
        initial_latency = max(0.0, (initial_start.at - swipe_at).total_seconds())
    if resolved_start:
        resolved_latency = max(0.0, (resolved_start.at - swipe_at).total_seconds())
    return {
        "settled_aweme_id": target_aweme,
        "settled_page": settled.page if settled else None,
        "settled_at": iso_ms(settled.at if settled else None),
        "initial_mode": initial_start.mode if initial_start else None,
        "initial_slot": initial_start.slot if initial_start else None,
        "initial_latency_s": initial_latency,
        "resolved_mode": resolved_start.mode if resolved_start else None,
        "resolved_slot": resolved_start.slot if resolved_start else None,
        "resolved_latency_s": resolved_latency,
        "failure_aweme_id": failure.aweme_id if failure else None,
        "failure_slot": failure.slot if failure else None,
        "failure_retry_count": failure.retry_count if failure else None,
        "error_count": len(errors),
    }


def slug(text: str) -> str:
    return re.sub(r"[^A-Za-z0-9_.-]+", "-", text).strip("-") or "event"


def write_text(path: Path, text: str) -> str:
    path.write_text(text, encoding="utf-8")
    return str(path)


def parse_meminfo(meminfo_text: str) -> dict[str, int]:
    metrics: dict[str, int] = {}
    app_summary_names = {
        "Java Heap": "java_heap_pss_kb",
        "Native Heap": "native_heap_pss_kb",
        "Code": "code_pss_kb",
        "Stack": "stack_pss_kb",
        "Graphics": "graphics_pss_kb",
        "Private Other": "private_other_pss_kb",
        "System": "system_pss_kb",
        "TOTAL": "total_pss_kb",
    }
    detail_names = {
        "Native Heap": "native_heap",
        "Dalvik Heap": "java_heap",
    }

    for line in meminfo_text.splitlines():
        summary_match = MEMINFO_APP_SUMMARY_RE.match(line)
        if summary_match:
            summary_name = " ".join(summary_match.group("name").split())
            key = app_summary_names.get(summary_name)
            if key:
                metrics[key] = int(summary_match.group("pss"))
            rss = summary_match.group("rss")
            if rss is not None:
                metrics["total_rss_kb"] = int(rss)

        total_rss_match = MEMINFO_TOTAL_RSS_RE.search(line)
        if total_rss_match:
            metrics["total_rss_kb"] = int(total_rss_match.group("rss"))
        total_pss_match = MEMINFO_TOTAL_PSS_RE.search(line)
        if total_pss_match:
            metrics["total_pss_kb"] = int(total_pss_match.group("pss"))

        object_matches = MEMINFO_OBJECT_RE.finditer(line)
        for match in object_matches:
            name = match.group("name")
            value = int(match.group("count"))
            if name == "Views":
                metrics["views"] = value
            elif name == "ViewRootImpl":
                metrics["view_roots"] = value
            elif name == "Activities":
                metrics["activities"] = value
            elif name == "AppContexts":
                metrics["app_contexts"] = value
            elif name == "Assets":
                metrics["assets"] = value
            elif name == "AssetManagers":
                metrics["asset_managers"] = value

        for detail_name, prefix in detail_names.items():
            if line.strip().startswith(detail_name):
                values = [int(token) for token in re.findall(r"\d+", line)]
                if len(values) >= 8:
                    metrics[f"{prefix}_alloc_kb"] = values[-2]
                    metrics[f"{prefix}_free_kb"] = values[-1]
                break
    return metrics


def capture_memory_sample(
    output_dir: Path,
    serial: str,
    package_name: str,
    step: int,
    save_raw: bool,
) -> tuple[dict[str, int], str]:
    completed = run_text(
        adb_args(["shell", "dumpsys", "meminfo", package_name], serial),
        check=False,
        timeout=20,
    )
    meminfo_text = completed.stdout if completed.returncode == 0 else completed.stdout + completed.stderr
    raw_path = ""
    if save_raw:
        memory_dir = output_dir / "memory"
        memory_dir.mkdir(parents=True, exist_ok=True)
        raw_path = write_text(memory_dir / f"{step:04d}-meminfo.txt", meminfo_text)
    return parse_meminfo(meminfo_text), raw_path


def write_memory_row(
    memory_path: Path,
    step: int,
    device_time: dt.datetime | None,
    status: str,
    reason: str,
    metrics: dict[str, int],
    raw_path: str,
) -> None:
    values: dict[str, object] = {
        "step": step,
        "device_time": iso_ms(device_time),
        "status": status,
        "reason": reason,
        "raw_meminfo": raw_path,
    }
    values.update(metrics)
    with memory_path.open("a", encoding="utf-8") as memory_file:
        memory_file.write(
            "\t".join(
                "" if values.get(column) is None else str(values.get(column, ""))
                for column in MEMORY_TSV_COLUMNS
            ) + "\n"
        )


def capture_jank_artifacts(
    output_dir: Path,
    serial: str,
    package_name: str,
    step: int,
    reason: str,
    samples: list[dict[str, object]],
    log_text: str,
) -> dict[str, str]:
    capture_dir = output_dir / "jank" / f"{step:04d}-{slug(reason)}"
    capture_dir.mkdir(parents=True, exist_ok=True)
    artifacts: dict[str, str] = {}

    debug_blocks = []
    for sample in samples:
        state = sample.get("state")
        raw = state.get("raw", "") if isinstance(state, dict) else ""
        debug_blocks.append(
            f"===== elapsed_s={sample.get('elapsed_s')} monotonic_s={sample.get('monotonic_s')} =====\n{raw}\n"
        )
    artifacts["debug_samples"] = write_text(capture_dir / "debug-samples.txt", "\n".join(debug_blocks))
    artifacts["logcat"] = write_text(capture_dir / "logcat-filtered.txt", log_text)

    for name, command in [
        ("activity_top", ["shell", "dumpsys", "activity", "top"]),
        ("meminfo", ["shell", "dumpsys", "meminfo", package_name]),
        ("gfxinfo", ["shell", "dumpsys", "gfxinfo", package_name]),
        ("gfxinfo_framestats", ["shell", "dumpsys", "gfxinfo", package_name, "framestats"]),
    ]:
        completed = run_text(adb_args(command, serial), check=False, timeout=20)
        artifacts[name] = write_text(
            capture_dir / f"{name}.txt",
            completed.stdout if completed.returncode == 0 else completed.stdout + completed.stderr,
        )

    completed = run_bytes(adb_args(["exec-out", "screencap", "-p"], serial), check=False, timeout=20)
    if completed.returncode == 0 and completed.stdout:
        screenshot_path = capture_dir / "screen.png"
        screenshot_path.write_bytes(completed.stdout)
        artifacts["screenshot"] = str(screenshot_path)

    remote_xml = "/sdcard/watchrss-douyin-jank-window.xml"
    adb_shell(["uiautomator", "dump", remote_xml], serial=serial, check=False, timeout=20)
    completed = run_bytes(adb_args(["exec-out", "cat", remote_xml], serial), check=False, timeout=20)
    if completed.returncode == 0 and completed.stdout:
        xml_path = capture_dir / "window.xml"
        xml_path.write_bytes(completed.stdout)
        artifacts["ui_xml"] = str(xml_path)

    return artifacts


def perform_swipe(serial: str, x: int, start_y: int, end_y: int, duration_ms: int, mode: str) -> None:
    if mode == "broadcast":
        adb_shell(
            [
                "am",
                "broadcast",
                "-n",
                DEBUG_RECEIVER,
                "-a",
                ADVANCE_ACTION,
                "--receiver-foreground",
            ],
            serial=serial,
            check=False,
        )
        return
    adb_shell(
        ["input", "swipe", str(x), str(start_y), str(x), str(end_y), str(duration_ms)],
        serial=serial,
    )


def classify_jank(
    event_summary: dict[str, object],
    state: dict[str, object],
    loading_s: float,
    elapsed_s: float,
    threshold_s: float,
) -> str | None:
    if event_summary.get("failure_aweme_id"):
        return "playback_failed"
    if bool(state.get("is_error")):
        return "foreground_slot_error"
    if bool(state.get("is_loading")) and loading_s >= threshold_s:
        return "loading_over_threshold"
    if event_summary.get("resolved_latency_s") is None and elapsed_s >= threshold_s:
        if event_summary.get("initial_mode") == "settled_buffering":
            return "buffering_without_resolved_playback"
        return "missing_resolved_playback"
    resolved_latency = event_summary.get("resolved_latency_s")
    if isinstance(resolved_latency, float) and resolved_latency >= threshold_s:
        return "slow_resolved_playback"
    return None


def compact_state(state: dict[str, object]) -> dict[str, object]:
    return {
        key: value
        for key, value in state.items()
        if key not in {"raw"}
    }


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(
        description="Swipe the WatchRSS Douyin flow every 8 seconds for one hour and capture loading state when jank is detected."
    )
    parser.add_argument("--serial", help="adb device serial. Defaults to the only connected device.")
    parser.add_argument("--package", default=APP_PACKAGE, help=f"App package. Default: {APP_PACKAGE}")
    parser.add_argument("--duration-seconds", type=float, default=3600.0, help="Total run duration. Default: 3600.")
    parser.add_argument("--interval-seconds", type=float, default=8.0, help="Swipe interval. Default: 8.")
    parser.add_argument("--steps", type=int, help="Override swipe count.")
    parser.add_argument("--jank-threshold-seconds", type=float, default=3.0, help="Loading/playback threshold. Default: 3.")
    parser.add_argument("--sample-seconds", type=float, default=0.5, help="Debug-provider polling interval. Default: 0.5.")
    parser.add_argument("--warmup-seconds", type=float, default=8.0, help="Wait after launching the debug Douyin entry. Default: 8.")
    parser.add_argument("--initial-delay-seconds", type=float, default=0.0, help="Delay before the first swipe. Default: 0.")
    parser.add_argument("--swipe-duration-ms", type=int, default=180, help="Single swipe duration. Default: 180.")
    parser.add_argument("--swipe-start-ratio", type=float, default=0.72, help="Swipe start Y ratio. Default: 0.72.")
    parser.add_argument("--swipe-end-ratio", type=float, default=0.28, help="Swipe end Y ratio. Default: 0.28.")
    parser.add_argument("--x", type=int, help="Swipe X coordinate. Defaults to display center.")
    parser.add_argument("--start-y", type=int, help="Swipe start Y. Overrides --swipe-start-ratio.")
    parser.add_argument("--end-y", type=int, help="Swipe end Y. Overrides --swipe-end-ratio.")
    parser.add_argument("--advance-mode", choices=["gesture", "broadcast"], default="gesture", help="Default: gesture.")
    parser.add_argument("--launch", action=argparse.BooleanOptionalAction, default=True, help="Launch debug Douyin flow first. Default: true.")
    parser.add_argument("--clear-logcat", action=argparse.BooleanOptionalAction, default=True, help="Clear logcat before run. Default: true.")
    parser.add_argument("--output-dir", help="Artifact directory. Default: artifacts/douyin_jank/<timestamp>.")
    parser.add_argument("--boot-timeout-seconds", type=float, default=30.0, help="adb boot wait timeout. Default: 30.")
    parser.add_argument("--fail-on-jank", action="store_true", help="Exit with code 1 if any jank is detected.")
    parser.add_argument("--record-memory", action=argparse.BooleanOptionalAction, default=True, help="Record dumpsys meminfo samples. Default: true.")
    parser.add_argument("--memory-raw-every-steps", type=int, default=30, help="Save full meminfo every N steps. 0 disables periodic raw snapshots. Default: 30.")
    return parser.parse_args()


def main() -> int:
    args = parse_args()
    if args.duration_seconds <= 0:
        print("--duration-seconds must be > 0", file=sys.stderr)
        return 2
    if args.interval_seconds <= 0:
        print("--interval-seconds must be > 0", file=sys.stderr)
        return 2
    if args.jank_threshold_seconds <= 0:
        print("--jank-threshold-seconds must be > 0", file=sys.stderr)
        return 2
    if args.sample_seconds <= 0:
        print("--sample-seconds must be > 0", file=sys.stderr)
        return 2
    if args.memory_raw_every_steps < 0:
        print("--memory-raw-every-steps must be >= 0", file=sys.stderr)
        return 2

    serial = resolve_serial(args.serial)
    wait_for_boot(serial, args.boot_timeout_seconds)
    display_width, display_height = get_display_size(serial)
    swipe_x = args.x if args.x is not None else max(1, display_width // 2)
    swipe_start_y = args.start_y if args.start_y is not None else max(1, int(display_height * args.swipe_start_ratio))
    swipe_end_y = args.end_y if args.end_y is not None else max(1, int(display_height * args.swipe_end_ratio))
    steps = args.steps if args.steps is not None else int(args.duration_seconds // args.interval_seconds)
    if steps <= 0:
        print("No swipes to run; increase --duration-seconds or set --steps.", file=sys.stderr)
        return 2

    timestamp = dt.datetime.now().strftime("%Y%m%d_%H%M%S")
    output_dir = Path(args.output_dir) if args.output_dir else Path("artifacts") / "douyin_jank" / timestamp
    output_dir.mkdir(parents=True, exist_ok=True)
    summary_path = output_dir / "summary.tsv"
    events_path = output_dir / "jank-events.jsonl"
    memory_path = output_dir / "memory.tsv"

    metadata = {
        "serial": serial,
        "package": args.package,
        "duration_seconds": args.duration_seconds,
        "interval_seconds": args.interval_seconds,
        "steps": steps,
        "jank_threshold_seconds": args.jank_threshold_seconds,
        "sample_seconds": args.sample_seconds,
        "warmup_seconds": args.warmup_seconds if args.launch else 0,
        "initial_delay_seconds": args.initial_delay_seconds,
        "advance_mode": args.advance_mode,
        "display_width": display_width,
        "display_height": display_height,
        "swipe_x": swipe_x,
        "swipe_start_y": swipe_start_y,
        "swipe_end_y": swipe_end_y,
        "swipe_duration_ms": args.swipe_duration_ms,
        "record_memory": args.record_memory,
        "memory_raw_every_steps": args.memory_raw_every_steps,
    }
    write_text(output_dir / "metadata.json", json.dumps(metadata, ensure_ascii=False, indent=2) + "\n")

    if args.launch:
        print("Launching debug Douyin flow...")
        launch_douyin_flow(serial, args.warmup_seconds)

    body, provider_error = fetch_debug_text(serial)
    if provider_error:
        print(f"Warning: initial debug provider read failed: {provider_error}", file=sys.stderr)
    write_text(output_dir / "initial-debug.txt", body or provider_error or "")

    if args.clear_logcat:
        adb(["logcat", "-c"], serial=serial, check=False)
    adb_shell(["dumpsys", "gfxinfo", args.package, "reset"], serial=serial, check=False)

    summary_path.write_text(
        "step\tswipe_device_time\tstatus\treason\tsettled_aweme_id\tsettled_page\t"
        "initial_mode\tinitial_latency_s\tresolved_mode\tresolved_latency_s\tforeground_state\n",
        encoding="utf-8",
    )
    events_path.write_text("", encoding="utf-8")
    if args.record_memory:
        memory_path.write_text("\t".join(MEMORY_TSV_COLUMNS) + "\n", encoding="utf-8")
        metrics, raw_path = capture_memory_sample(
            output_dir=output_dir,
            serial=serial,
            package_name=args.package,
            step=0,
            save_raw=True,
        )
        write_memory_row(
            memory_path=memory_path,
            step=0,
            device_time=device_now(serial),
            status="BASELINE",
            reason="after_launch",
            metrics=metrics,
            raw_path=raw_path,
        )

    run_start = time.monotonic()
    run_end = run_start + args.initial_delay_seconds + (steps * args.interval_seconds)
    jank_count = 0

    print(f"Running {steps} swipes on {serial}; artifacts: {output_dir}")
    for step in range(1, steps + 1):
        scheduled_at = run_start + args.initial_delay_seconds + ((step - 1) * args.interval_seconds)
        while time.monotonic() < scheduled_at:
            time.sleep(min(0.2, scheduled_at - time.monotonic()))

        swipe_at = device_now(serial)
        perform_swipe(
            serial=serial,
            x=swipe_x,
            start_y=swipe_start_y,
            end_y=swipe_end_y,
            duration_ms=args.swipe_duration_ms,
            mode=args.advance_mode,
        )
        step_started = time.monotonic()
        step_deadline = min(run_end, scheduled_at + args.interval_seconds)
        loading_started_at: float | None = None
        samples: list[dict[str, object]] = []
        jank_reason: str | None = None
        jank_artifacts: dict[str, str] = {}
        latest_state: dict[str, object] = {}
        latest_summary: dict[str, object] = {}
        latest_log_text = ""

        while True:
            now = time.monotonic()
            debug_text, error = fetch_debug_text(serial)
            latest_state = parse_debug_state(debug_text, error)
            if bool(latest_state.get("is_loading")):
                if loading_started_at is None:
                    loading_started_at = now
            else:
                loading_started_at = None

            latest_log_text = read_filtered_logcat(serial)
            latest_summary = summarize_step_events(latest_log_text, swipe_at)
            loading_s = 0.0 if loading_started_at is None else max(0.0, now - loading_started_at)
            samples.append(
                {
                    "elapsed_s": round(now - step_started, 3),
                    "monotonic_s": round(now, 3),
                    "loading_s": round(loading_s, 3),
                    "event_summary": latest_summary,
                    "state": latest_state,
                }
            )

            if jank_reason is None:
                jank_reason = classify_jank(
                    event_summary=latest_summary,
                    state=latest_state,
                    loading_s=loading_s,
                    elapsed_s=now - step_started,
                    threshold_s=args.jank_threshold_seconds,
                )
                if jank_reason is not None:
                    jank_artifacts = capture_jank_artifacts(
                        output_dir=output_dir,
                        serial=serial,
                        package_name=args.package,
                        step=step,
                        reason=jank_reason,
                        samples=samples,
                        log_text=latest_log_text,
                    )

            if now >= step_deadline:
                break
            sleep_s = min(args.sample_seconds, max(0.05, step_deadline - now))
            time.sleep(sleep_s)

        status = "JANK" if jank_reason else "OK"
        memory_metrics: dict[str, int] = {}
        memory_raw_path = ""
        if args.record_memory:
            save_raw_memory = bool(jank_reason) or (
                args.memory_raw_every_steps > 0 and step % args.memory_raw_every_steps == 0
            )
            memory_metrics, memory_raw_path = capture_memory_sample(
                output_dir=output_dir,
                serial=serial,
                package_name=args.package,
                step=step,
                save_raw=save_raw_memory,
            )
            write_memory_row(
                memory_path=memory_path,
                step=step,
                device_time=swipe_at,
                status=status,
                reason=jank_reason or "",
                metrics=memory_metrics,
                raw_path=memory_raw_path,
            )
        if jank_reason:
            jank_count += 1
            record = {
                "step": step,
                "swipe_device_time": iso_ms(swipe_at),
                "reason": jank_reason,
                "final_event_summary": latest_summary,
                "final_state": compact_state(latest_state),
                "samples": [
                    {
                        "elapsed_s": sample["elapsed_s"],
                        "loading_s": sample["loading_s"],
                        "event_summary": sample["event_summary"],
                        "state": compact_state(sample["state"]) if isinstance(sample["state"], dict) else sample["state"],
                    }
                    for sample in samples
                ],
                "memory": {
                    "metrics": memory_metrics,
                    "raw_meminfo": memory_raw_path,
                },
                "artifacts": jank_artifacts,
            }
            with events_path.open("a", encoding="utf-8") as events_file:
                events_file.write(json.dumps(record, ensure_ascii=False) + "\n")

        foreground_state = json.dumps(compact_state(latest_state), ensure_ascii=False, sort_keys=True)
        with summary_path.open("a", encoding="utf-8") as summary_file:
            summary_file.write(
                f"{step}\t{iso_ms(swipe_at)}\t{status}\t{jank_reason or ''}\t"
                f"{latest_summary.get('settled_aweme_id') or ''}\t{latest_summary.get('settled_page') or ''}\t"
                f"{latest_summary.get('initial_mode') or ''}\t{latest_summary.get('initial_latency_s') or ''}\t"
                f"{latest_summary.get('resolved_mode') or ''}\t{latest_summary.get('resolved_latency_s') or ''}\t"
                f"{foreground_state}\n"
            )
        print(f"[{step}/{steps}] {status} {jank_reason or ''}".rstrip())
        sys.stdout.flush()

    final_logcat = read_filtered_logcat(serial)
    write_text(output_dir / "final-logcat-filtered.txt", final_logcat)
    final_debug, final_debug_error = fetch_debug_text(serial)
    write_text(output_dir / "final-debug.txt", final_debug or final_debug_error or "")
    final_gfx = adb_shell(["dumpsys", "gfxinfo", args.package], serial=serial, check=False, timeout=20)
    write_text(output_dir / "final-gfxinfo.txt", final_gfx)
    final_meminfo = adb_shell(["dumpsys", "meminfo", args.package], serial=serial, check=False, timeout=20)
    write_text(output_dir / "final-meminfo.txt", final_meminfo)

    print(f"Done. jank_count={jank_count}/{steps}")
    print(f"summary={summary_path}")
    print(f"events={events_path}")
    if args.record_memory:
        print(f"memory={memory_path}")
    return 1 if args.fail_on_jank and jank_count > 0 else 0


if __name__ == "__main__":
    raise SystemExit(main())
