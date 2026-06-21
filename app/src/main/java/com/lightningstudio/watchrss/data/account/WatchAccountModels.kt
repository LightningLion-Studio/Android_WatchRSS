package com.lightningstudio.watchrss.data.account

data class WatchEntitlementSnapshot(
    val plan: String = "free",
    val active: Boolean = true,
    val expiresAtMillis: Long = 0L,
    val features: List<String> = emptyList()
)

data class WatchTelemetryConfig(
    val anonymousEnabled: Boolean = true,
    val diagnosticsEnabled: Boolean = false,
    val sampleRate: Double = 1.0
)

data class WatchAccountState(
    val userId: String,
    val phoneMasked: String,
    val installId: String,
    val watchDeviceToken: String,
    val tokenExpiresAtMillis: Long,
    val backendBaseUrl: String,
    val posthogHost: String,
    val posthogProjectApiKey: String,
    val entitlement: WatchEntitlementSnapshot = WatchEntitlementSnapshot(),
    val telemetryConfig: WatchTelemetryConfig = WatchTelemetryConfig(),
    val updatedAtMillis: Long = System.currentTimeMillis()
) {
    val isTokenExpired: Boolean
        get() = tokenExpiresAtMillis > 0L && tokenExpiresAtMillis <= System.currentTimeMillis()
}

