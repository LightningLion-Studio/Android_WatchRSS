package com.lightningstudio.watchrss.data.cloud

import org.json.JSONObject
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/** Book metadata travels over the records channel, never inside its immutable JSONL body. */
internal object WatchLayout3BookRecords {
    data class Document(val raw: WatchLayout3RawDocument) {
        val kind: String get() = raw.json().getString("kind")
        val value: WatchLayout3RawDocument get() = WatchLayout3RawDocument.parse(requireNotNull(raw.raw(listOf("value"))))
        fun validate() {
            val root = raw.json()
            require(root.getString("format") == "watchrss-rss-record" && WatchLayout3Format.integer(raw, "version") == 1L)
            if (!WatchLayout3Format.access(raw, emptySet()).readable) throw WatchCloudUpgradeRequired()
            val fields = value.json()
            require(listOf("contentText", "contentHtml", "contentTextGzip", "contentHtmlGzip", "body", "chapters").none(fields::has))
            when (kind) {
                "book" -> WatchLayout3Books.validateState(value)
                "book-reading" -> WatchLayout3Books.validateReading(value)
                else -> throw WatchCloudUpgradeRequired()
            }
        }
    }
    fun document(kind: String, value: WatchLayout3RawDocument): Document = Document(WatchLayout3RawDocument.parse(
        "{\"format\":\"watchrss-rss-record\",\"version\":1,\"kind\":${JSONObject.quote(kind)},\"value\":${value.source}}"
    )).also { it.validate() }
    fun id(anchor: ByteArray, user: String, document: Document): String {
        document.validate()
        require(anchor.size == 32)
        val value = document.value.json()
        val identity = value.getString("bookId") + if (document.kind == "book-reading") ":" + value.getInt("chapterIndex") else ""
        val derived = WatchCloudCodec.hkdf(anchor, user.toByteArray(), "watchrss/cloud/record-id/v1".toByteArray())
        return try {
            Mac.getInstance("HmacSHA256").run {
                init(SecretKeySpec(derived, "HmacSHA256")); doFinal("${document.kind}:$identity".toByteArray())
            }.joinToString("") { "%02x".format(it) }
        } finally { derived.fill(0) }
    }
    fun encode(key: ByteArray, user: String, recordId: String, version: Int, document: Document,
        nonce: ByteArray = ByteArray(12).also(SecureRandom()::nextBytes)): JSONObject {
        document.validate()
        if (!WatchLayout3Format.access(document.raw, emptySet()).writable) throw WatchCloudUpgradeRequired()
        require(key.size == 32 && WatchLayout3Format.hash.matches(recordId) && version > 0 && nonce.size == 12)
        val plain = document.raw.source.toByteArray()
        require(plain.size + 16 <= 65536)
        val derived = WatchCloudCodec.hkdf(key, user.toByteArray(), "watchrss/cloud/record/v1".toByteArray())
        return try {
            val sealed = WatchCloudCodec.aes(true, derived, nonce, plain, "watchrss-cloud-record-v1:$user:$recordId:$version".toByteArray())
            JSONObject().put("algorithm", "A256GCM").put("keyVersion", version)
                .put("nonceBase64", Base64.getEncoder().encodeToString(nonce)).put("ciphertextBase64", Base64.getEncoder().encodeToString(sealed))
        } finally { derived.fill(0); plain.fill(0) }
    }
    fun decode(key: ByteArray, anchor: ByteArray, user: String, recordId: String, envelope: JSONObject): Document {
        require(key.size == 32 && anchor.size == 32 && envelope.getString("algorithm") == "A256GCM" && WatchLayout3Format.hash.matches(recordId))
        val version = WatchLayout3Format.integer(WatchLayout3RawDocument.parse(envelope.toString()), "keyVersion", Int.MAX_VALUE.toLong()).toInt()
        require(version > 0)
        val nonce = WatchLayout3Format.base64(envelope.getString("nonceBase64"), 12)
        val cipher = Base64.getDecoder().decode(envelope.getString("ciphertextBase64"))
        require(cipher.size in 16..65536 && Base64.getEncoder().encodeToString(cipher) == envelope.getString("ciphertextBase64"))
        val derived = WatchCloudCodec.hkdf(key, user.toByteArray(), "watchrss/cloud/record/v1".toByteArray())
        val plain = try { WatchCloudCodec.aes(false, derived, nonce, cipher, "watchrss-cloud-record-v1:$user:$recordId:$version".toByteArray()) }
        finally { derived.fill(0) }
        return try {
            Document(WatchLayout3RawDocument.parse(plain)).also {
                it.validate()
                require(id(anchor, user, it) == recordId || id(key, user, it) == recordId)
            }
        } finally { plain.fill(0) }
    }
}
