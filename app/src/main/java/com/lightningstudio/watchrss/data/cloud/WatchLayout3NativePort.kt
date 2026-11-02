package com.lightningstudio.watchrss.data.cloud

import java.io.File

/** Account-bound, lossless boundary required before the layout 3 publication gate can open.
 * Main Room entities currently expose a lossy article view of these values.
 * Implementations must preserve raw JSON/package bytes, book/chapter aliases and
 * separate reading/favorite/watchLater provenance across process restarts.
 */
interface WatchLayout3NativePort {
    data class Scope(val backend: String, val userId: String, val installId: String, val epoch: String, val contentGeneration: String)
    data class Snapshot(
        val bookStates: List<String>,
        val bookReadingStates: List<String>,
        val packageFiles: Map<String, File>,
        val articleAliases: Map<String, String>
    )

    /** A consistent native view, including deletion sets and records with no body yet. */
    suspend fun capture(scope: Scope, localDeviceId: String): Snapshot

    /** Merge each raw record and its native projection before returning a durable receipt.
     * No receipt may be returned for an unsupported or partially committed projection.
     * Reading states received before packages must survive and apply when a package arrives.
     */
    suspend fun mergeRecords(scope: Scope, bookStates: List<String>, bookReadingStates: List<String>): Int

    /** The caller has validated the complete immutable package and descriptor hash.
     * A retry must re-use the book/chapter-to-article aliases from the first attempt.
     * Partial native batches may replay; only completion permits an object receipt.
     */
    suspend fun receivePackage(scope: Scope, descriptorRaw: String, verifiedPackage: File): Int
}
