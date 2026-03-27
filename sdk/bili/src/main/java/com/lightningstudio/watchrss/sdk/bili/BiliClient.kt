package com.lightningstudio.watchrss.sdk.bili

class BiliClient(
    val config: BiliSdkConfig,
    val accountStore: BiliAccountStore?,
    val httpClient: BiliHttpService = BiliHttpClient(config, accountStore)
) {
    val identity: BiliIdentity = BiliIdentity(config, httpClient, accountStore)
    val auth: BiliAuth = BiliAuth(this)
    val feed: BiliFeed = BiliFeed(this)
    val video: BiliVideo = BiliVideo(this)
    val play: BiliPlay = BiliPlay(this)
    val action: BiliAction = BiliAction(this)
    val actionRepair: BiliWebActionRepair = BiliWebActionRepair(this)
    val history: BiliHistory = BiliHistory(this)
    val favorite: BiliFavorite = BiliFavorite(this)
    val search: BiliSearch = BiliSearch(this)
    val comment: BiliComment = BiliComment(this)
}
