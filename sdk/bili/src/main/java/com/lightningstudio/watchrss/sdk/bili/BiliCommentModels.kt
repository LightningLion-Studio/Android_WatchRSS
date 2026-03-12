package com.lightningstudio.watchrss.sdk.bili

// Comment Page Response
data class BiliCommentPage(
    val cursor: BiliCommentCursor? = null,
    val replies: List<BiliCommentData>? = null,
    val topReplies: List<BiliCommentData>? = null,
    val upper: BiliCommentUpper? = null
)

data class BiliCommentReplyPage(
    val root: BiliCommentData? = null,
    val replies: List<BiliCommentData>? = null,
    val cursor: BiliCommentCursor? = null
)

data class BiliCommentCursor(
    val next: Long? = null,
    val isEnd: Boolean? = null,
    val mode: Int? = null
)

data class BiliCommentUpper(
    val mid: Long? = null
)

// Comment Data
data class BiliCommentData(
    val rpid: Long? = null,
    val oid: Long? = null,
    val type: Int? = null,
    val mid: Long? = null,
    val root: Long? = null,
    val parent: Long? = null,
    val count: Int? = null,
    val rcount: Int? = null,
    val like: Long? = null,
    val ctime: Long? = null,
    val member: BiliCommentMember? = null,
    val content: BiliCommentContent? = null,
    val replies: List<BiliCommentData>? = null,
    val replyControl: BiliReplyControl? = null
)

// Comment Member
data class BiliCommentMember(
    val mid: String? = null,
    val uname: String? = null,
    val sex: String? = null,
    val avatar: String? = null,
    val vip: BiliCommentVip? = null,
    val officialVerify: BiliCommentOfficialVerify? = null,
    val pendant: BiliCommentPendant? = null
)

data class BiliCommentVip(
    val vipType: Int? = null,
    val vipStatus: Int? = null,
    val nicknameColor: String? = null,
    val label: BiliCommentVipLabel? = null
)

data class BiliCommentVipLabel(
    val text: String? = null,
    val labelTheme: String? = null,
    val textColor: String? = null,
    val bgColor: String? = null
)

data class BiliCommentOfficialVerify(
    val type: Int? = null,
    val desc: String? = null
)

data class BiliCommentPendant(
    val pid: Long? = null,
    val name: String? = null,
    val image: String? = null
)

// Comment Content
data class BiliCommentContent(
    val message: String? = null,
    val emote: Map<String, BiliEmote>? = null,
    val members: List<BiliCommentAtMember>? = null,
    val jumpUrl: Map<String, BiliCommentJumpUrl>? = null,
    val pictures: List<BiliCommentPicture>? = null
)

data class BiliEmote(
    val id: Long? = null,
    val text: String? = null,
    val url: String? = null,
    val type: Int? = null,
    val size: Int? = null
)

data class BiliCommentAtMember(
    val mid: String? = null,
    val uname: String? = null
)

data class BiliCommentJumpUrl(
    val title: String? = null,
    val state: Int? = null,
    val prefixIcon: String? = null,
    val clickable: Boolean? = null
)

data class BiliCommentPicture(
    val imgSrc: String? = null,
    val imgWidth: Int? = null,
    val imgHeight: Int? = null,
    val imgSize: Double? = null
)

// Reply Control
data class BiliReplyControl(
    val upReply: Boolean? = null,
    val isUpTop: Boolean? = null,
    val isNote: Boolean? = null
)
