package com.lightningstudio.watchrss.phoneconnection.bluetooth

import org.json.JSONObject
import org.junit.Test

class LibraryAccountScopeTest {
    private val alpha = "https://backend.example/test" to "alpha"
    private fun request(action: String = BluetoothSyncProtocol.ACTION_SYNC_LIBRARY, user: String = "alpha", backend: String = alpha.first) =
        JSONObject().put("action", action).put("libraryAccountUserId", user).put("libraryAccountBackend", backend)

    @Test fun ownAccountCanUseAllLibraryOperations() {
        listOf("syncLibrary", "pullSavedItems", "syncNotes", "syncNoteAsset", "syncReader", "previewReader", "syncLlmTokenUsage", "remoteInput").forEach {
            LibraryAccountScope.requireMatching(request(it), alpha)
        }
    }
    @Test(expected = IllegalArgumentException::class) fun anotherAccountCannotReadBoundLibrary() {
        LibraryAccountScope.requireMatching(request(user = "beta"), alpha)
    }
    @Test(expected = IllegalArgumentException::class) fun sameAccountOnAnotherEnvironmentCannotReadBoundLibrary() {
        LibraryAccountScope.requireMatching(request(backend = "https://backend.example"), alpha)
    }
    @Test(expected = IllegalArgumentException::class) fun legacyClientCannotReadBoundLibrary() {
        LibraryAccountScope.requireMatching(JSONObject().put("action", "syncLibrary"), alpha)
    }
    @Test fun discoveryAndGuestLibraryRemainAvailable() {
        LibraryAccountScope.requireMatching(request(user = "beta").put("phase", "probe"), alpha)
        LibraryAccountScope.requireMatching(JSONObject().put("action", "syncLibrary"), null)
        LibraryAccountScope.requireMatching(request(user = "", backend = ""), null)
    }
    @Test(expected = IllegalArgumentException::class) fun guestCannotImportIntoBoundAccount() {
        LibraryAccountScope.requireMatching(request(action = "remoteInput", user = "", backend = ""), alpha)
    }
    @Test fun automaticAccountRefreshCannotSelectAnotherLibrary() {
        LibraryAccountScope.requireAutomaticAccount(alpha, alpha)
        org.junit.Assert.assertTrue(runCatching { LibraryAccountScope.requireAutomaticAccount(alpha, alpha.first to "beta") }.isFailure)
        org.junit.Assert.assertTrue(runCatching { LibraryAccountScope.requireAutomaticAccount(null, alpha) }.isFailure)
        org.junit.Assert.assertTrue(runCatching { LibraryAccountScope.requireAutomaticAccount(alpha, "https://other.example" to "alpha") }.isFailure)
    }
    @Test fun automaticProbeOnlyConfirmsTheRequestedAccountAndEnvironment() {
        org.junit.Assert.assertTrue(LibraryAccountScope.matchesAccount(request(), alpha))
        org.junit.Assert.assertFalse(LibraryAccountScope.matchesAccount(request(user = "beta"), alpha))
        org.junit.Assert.assertFalse(LibraryAccountScope.matchesAccount(request(backend = "https://other.example"), alpha))
        org.junit.Assert.assertFalse(LibraryAccountScope.matchesAccount(request(), null))
    }
}
