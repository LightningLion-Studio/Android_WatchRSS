package com.lightningstudio.watchrss.data.cloud

import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Receipts describe fully authenticated and durably merged objects, never download heartbeats. */
internal class WatchCloudRestoreJournal(private val root: File) {
    data class Progress(val head: String, val stage: String, val completed: Int, val total: Int) {
        val percent: Int get() = if (total == 0) 0 else (completed * 100L / total).toInt()
        val message: String get() = "恢复$stage $percent%（$completed/$total）"
    }
    private fun file(scope: String, head: String) = File(root, WatchCloudCodec.sha256(scope.toByteArray()) + "/" + WatchCloudCodec.sha256(head.toByteArray()) + ".json")
    private fun read(file: File): JSONObject = if (file.isFile) JSONObject(file.readText()) else JSONObject()
    private fun write(file: File, json: JSONObject) {
        check(file.parentFile!!.isDirectory || file.parentFile!!.mkdirs())
        val temporary = File(file.parentFile, file.name + ".tmp")
        FileOutputStream(temporary).use { it.write(json.toString().toByteArray()); it.fd.sync() }
        Files.move(temporary.toPath(), file.toPath(), StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
    }
    @Synchronized fun begin(scope: String, head: String, manifest: String, units: Set<String>): Progress {
        val file = file(scope, head)
        val previous = read(file)
        require(!previous.has("manifest") || previous.getString("manifest") == manifest) { "恢复快照身份发生变化" }
        require(!previous.has("units") || previous.getJSONObject("units").keys().asSequence().toSet() == units) { "恢复投影发生变化" }
        val json = if (previous.has("manifest")) previous else JSONObject().put("manifest", manifest)
            .put("units", JSONObject().apply { units.forEach { put(it, false) } }).put("acked", false)
        if (json.optBoolean("attemptOpen")) countFailedWindow(json)
        json.put("attemptOpen", true).put("attemptStart", completed(json))
        write(file, json)
        return publish(scope, head, json)
    }
    private fun completed(json: JSONObject): Int = json.getJSONObject("units").let { units ->
        units.keys().asSequence().count { units.getBoolean(it) }
    }
    private fun countFailedWindow(json: JSONObject) {
        val stalled = if (completed(json) == json.optInt("attemptStart")) json.optInt("noProgressWindows") + 1 else 0
        json.put("noProgressWindows", stalled).put("attemptOpen", false)
    }
    @Synchronized fun failed(scope: String, head: String): Int {
        val file = file(scope, head); val json = read(file)
        if (json.optBoolean("attemptOpen")) countFailedWindow(json)
        write(file, json)
        return json.optInt("noProgressWindows")
    }
    @Synchronized fun reusable(scope: String, head: String, manifest: String, unit: String): Boolean {
        val json = read(file(scope, head))
        if (!json.has("manifest")) return false
        require(json.getString("manifest") == manifest) { "恢复快照身份发生变化" }
        return json.getJSONObject("units").optBoolean(unit)
    }
    @Synchronized fun received(scope: String, head: String, unit: String): Boolean =
        read(file(scope, head)).getJSONObject("units").getBoolean(unit)
    @Synchronized fun commit(scope: String, head: String, unit: String): Progress = commitAll(scope, head, setOf(unit))
    @Synchronized fun commitAll(scope: String, head: String, units: Set<String>): Progress {
        val file = file(scope, head); val json = read(file)
        require(units.all { json.getJSONObject("units").has(it) })
        units.forEach { json.getJSONObject("units").put(it, true) }
        json.put("noProgressWindows", 0)
        write(file, json)
        return publish(scope, head, json)
    }
    @Synchronized fun acknowledge(scope: String, head: String): Progress {
        val file = file(scope, head); val json = read(file)
        require(json.getJSONObject("units").let { units -> units.keys().asSequence().all { units.getBoolean(it) } })
        json.put("acked", true).put("attemptOpen", false).put("noProgressWindows", 0); write(file, json)
        return publish(scope, head, json)
    }
    fun latest(scope: String): Progress? = read(file(scope, "latest")).takeIf { it.has("head") }?.let {
        Progress(it.getString("head"), it.getString("stage"), it.getInt("completed"), it.getInt("total"))
    }
    @Synchronized fun remember(owner: String, progress: Progress) {
        write(file(owner, "latest"), JSONObject().put("head", progress.head).put("stage", progress.stage)
            .put("completed", progress.completed).put("total", progress.total))
    }
    private fun publish(scope: String, head: String, json: JSONObject): Progress {
        val units = json.getJSONObject("units")
        val done = units.keys().asSequence().count { units.getBoolean(it) }
        val acked = json.getBoolean("acked")
        val progress = Progress(head, if (acked) "完成" else if (done == units.length()) "等待确认" else "合并中",
            done + if (acked) 1 else 0, units.length() + 1)
        write(file(scope, "latest"), JSONObject().put("head", head).put("stage", progress.stage)
            .put("completed", progress.completed).put("total", progress.total))
        return progress
    }
}
