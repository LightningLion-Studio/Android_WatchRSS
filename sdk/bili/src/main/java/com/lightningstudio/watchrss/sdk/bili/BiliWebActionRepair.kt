package com.lightningstudio.watchrss.sdk.bili

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class BiliWebActionRepair(
    private val client: BiliClient,
    private val nowMillis: () -> Long = { System.currentTimeMillis() }
) {
    private val repairMutex = Mutex()

    suspend fun <T> execute(
        action: String,
        block: suspend () -> BiliResult<T>
    ): BiliResult<T> {
        prepare(action = action, forceCookieRefresh = false, reason = "preflight")

        val firstResult = block()
        when (firstResult.code) {
            -111 -> {
                BiliDebugLog.log("bili_action", "action=$action retry_reason=csrf")
                val repairResult = prepare(
                    action = action,
                    forceCookieRefresh = true,
                    reason = "retry_csrf"
                )
                if (repairResult.canRetry) {
                    return block()
                }
            }

            -101 -> {
                if (!hasRefreshPrerequisites()) {
                    BiliDebugLog.log("bili_action", "action=$action retry_skip=missing_refresh_prerequisites")
                    return firstResult
                }
                BiliDebugLog.log("bili_action", "action=$action retry_reason=login")
                val repairResult = prepare(
                    action = action,
                    forceCookieRefresh = true,
                    reason = "retry_login"
                )
                if (repairResult.canRetry) {
                    return block()
                }
            }

            -352, -412, -509, -799 -> {
                BiliDebugLog.log(
                    "bili_action",
                    "action=$action risk_block code=${firstResult.code} msg=${firstResult.message}"
                )
            }
        }
        return firstResult
    }

    private suspend fun prepare(
        action: String,
        forceCookieRefresh: Boolean,
        reason: String
    ): ActionRepairResult = repairMutex.withLock {
        var account = client.accountStore?.read() ?: return@withLock ActionRepairResult()
        val browserProfile = account.browserProfile
        if (browserProfile == null || browserProfile.version != BiliBrowserProfile.CURRENT_VERSION) {
            val profile = client.auth.ensureWebBrowserProfile()
            BiliDebugLog.log(
                "bili_action",
                "action=$action repair=$reason browser_profile=${profile.version}"
            )
            account = client.accountStore?.read() ?: account
        }
        val currentBuvid3 = account.cookies["buvid3"] ?: account.buvid3
        val missingBuvid = currentBuvid3.isNullOrBlank() ||
            account.cookies["buvid4"].isNullOrBlank() ||
            account.cookies["b_nut"].isNullOrBlank()
        val needsBuvidActivation = !currentBuvid3.isNullOrBlank() &&
            account.activatedBuvid3 != currentBuvid3
        if (missingBuvid || needsBuvidActivation) {
            val buvid = client.identity.fetchBuvid()
            BiliDebugLog.log(
                "bili_action",
                "action=$action repair=$reason buvid_fetch=${buvid != null} " +
                    "activate=$needsBuvidActivation"
            )
        }

        account = client.accountStore?.read() ?: account
        val missingCoreCookies = account.cookies["sid"].isNullOrBlank() ||
            account.cookies["DedeUserID"].isNullOrBlank() ||
            account.cookies["DedeUserID__ckMd5"].isNullOrBlank()
        val canRefreshCookies = !account.refreshToken.isNullOrBlank() && !account.csrfToken().isNullOrBlank()
        val shouldCheckCookieRefresh = canRefreshCookies && (
            forceCookieRefresh ||
                missingCoreCookies ||
                isCookieRefreshCheckExpired(account)
            )

        var refreshResult: WebCookieRefreshResult? = null
        if (shouldCheckCookieRefresh) {
            refreshResult = client.auth.refreshWebCookies(
                forceRefresh = forceCookieRefresh || missingCoreCookies
            )
            BiliDebugLog.log(
                "bili_action",
                "action=$action repair=$reason cookie_refresh code=${refreshResult.code} " +
                    "refreshed=${refreshResult.refreshed} checked=${refreshResult.checked} " +
                    "forced=${refreshResult.forced} msg=${refreshResult.message}"
            )
        }

        account = client.accountStore?.read() ?: account
        val shouldRefreshTicket = account.biliTicket.isNullOrBlank() || isBiliTicketExpired(account)
        if (shouldRefreshTicket) {
            val csrf = account.csrfToken()
            val ticket = if (csrf.isNullOrBlank()) null else client.identity.fetchWebTicket(csrf)
            BiliDebugLog.log(
                "bili_action",
                "action=$action repair=$reason ticket_refresh=${!ticket.isNullOrBlank()}"
            )
        }

        ActionRepairResult(
            canRetry = refreshResult?.isSuccess == true && refreshResult.refreshed
        )
    }

    private suspend fun hasRefreshPrerequisites(): Boolean {
        val account = client.accountStore?.read() ?: return false
        return !account.refreshToken.isNullOrBlank() && !account.csrfToken().isNullOrBlank()
    }

    private fun isBiliTicketExpired(account: BiliAccount): Boolean {
        val fetchedAt = account.biliTicketFetchedAtMillis ?: return account.biliTicket.isNullOrBlank()
        return nowMillis() - fetchedAt >= BILI_TICKET_REFRESH_INTERVAL_MS
    }

    private fun isCookieRefreshCheckExpired(account: BiliAccount): Boolean {
        val checkedAt = account.cookieRefreshCheckedAtMillis ?: return true
        return nowMillis() - checkedAt >= COOKIE_REFRESH_CHECK_INTERVAL_MS
    }

    private companion object {
        private const val BILI_TICKET_REFRESH_INTERVAL_MS = 48L * 60L * 60L * 1000L
        private const val COOKIE_REFRESH_CHECK_INTERVAL_MS = 24L * 60L * 60L * 1000L
    }
}

private data class ActionRepairResult(
    val canRetry: Boolean = false
)
