package com.lightningstudio.watchrss.sdk.bili

interface BiliAccountStore {
    suspend fun read(): BiliAccount?
    suspend fun write(account: BiliAccount)
    suspend fun update(transform: (BiliAccount) -> BiliAccount)
}
