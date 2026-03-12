package com.lightningstudio.watchrss.sdk.bili

import kotlinx.serialization.json.JsonObject

class BiliComment(private val client: BiliClient) {

    suspend fun getComments(oid: Long, next: Long = 0): BiliResult<BiliCommentPage> {
        val url = "${client.config.webBaseUrl}/x/v2/reply/main"
        val params = mapOf(
            "type" to "1",
            "oid" to oid.toString(),
            "next" to next.toString()
        )
        val response = client.httpClient.get(url, params = params)
        val status = parseBiliStatus(response.body)

        if (status.code != 0) {
            return BiliResult(status.code, status.message)
        }

        val data = status.data?.asObjectOrNull() ?: return BiliResult(-1, "empty_data")
        val commentPage = parseCommentPage(data)

        return BiliResult(status.code, status.message, commentPage)
    }

    suspend fun getReplies(oid: Long, root: Long, pn: Int = 1): BiliResult<BiliCommentReplyPage> {
        val url = "${client.config.webBaseUrl}/x/v2/reply/reply"
        val params = mapOf(
            "type" to "1",
            "oid" to oid.toString(),
            "root" to root.toString(),
            "pn" to pn.toString(),
            "ps" to "20"
        )
        val response = client.httpClient.get(url, params = params)
        val status = parseBiliStatus(response.body)

        if (status.code != 0) {
            return BiliResult(status.code, status.message)
        }

        val data = status.data?.asObjectOrNull() ?: return BiliResult(-1, "empty_data")
        val replyPage = parseReplyPage(data)

        return BiliResult(status.code, status.message, replyPage)
    }

    private fun parseCommentPage(obj: JsonObject): BiliCommentPage {
        val cursor = obj.objOrNull("cursor")?.let { parseCursor(it) }
        val repliesArray = obj.arrayOrNull("replies")
        val replies = repliesArray?.mapNotNull { parseCommentData(it.asObjectOrNull()) }
        val topRepliesArray = obj.arrayOrNull("top_replies")
        val topReplies = topRepliesArray?.mapNotNull { parseCommentData(it.asObjectOrNull()) }
        val upper = obj.objOrNull("upper")?.let {
            BiliCommentUpper(mid = it.longOrNull("mid"))
        }

        return BiliCommentPage(
            cursor = cursor,
            replies = replies,
            topReplies = topReplies,
            upper = upper
        )
    }

    private fun parseReplyPage(obj: JsonObject): BiliCommentReplyPage {
        val root = obj.objOrNull("root")?.let { parseCommentData(it) }
        val repliesArray = obj.arrayOrNull("replies")
        val replies = repliesArray?.mapNotNull { parseCommentData(it.asObjectOrNull()) }
        val cursor = obj.objOrNull("cursor")?.let { parseCursor(it) }

        return BiliCommentReplyPage(
            root = root,
            replies = replies,
            cursor = cursor
        )
    }

    private fun parseCursor(obj: JsonObject): BiliCommentCursor {
        return BiliCommentCursor(
            next = obj.longOrNull("next"),
            isEnd = obj.booleanOrNull("is_end"),
            mode = obj.intOrNull("mode")
        )
    }

    private fun parseCommentData(obj: JsonObject?): BiliCommentData? {
        if (obj == null) return null

        val member = obj.objOrNull("member")?.let { parseMember(it) }
        val content = obj.objOrNull("content")?.let { parseContent(it) }
        val repliesArray = obj.arrayOrNull("replies")
        val replies = repliesArray?.mapNotNull { parseCommentData(it.asObjectOrNull()) }
        val replyControl = obj.objOrNull("reply_control")?.let { parseReplyControl(it) }

        return BiliCommentData(
            rpid = obj.longOrNull("rpid"),
            oid = obj.longOrNull("oid"),
            type = obj.intOrNull("type"),
            mid = obj.longOrNull("mid"),
            root = obj.longOrNull("root"),
            parent = obj.longOrNull("parent"),
            count = obj.intOrNull("count"),
            rcount = obj.intOrNull("rcount"),
            like = obj.longOrNull("like"),
            ctime = obj.longOrNull("ctime"),
            member = member,
            content = content,
            replies = replies,
            replyControl = replyControl
        )
    }

    private fun parseMember(obj: JsonObject): BiliCommentMember {
        val vip = obj.objOrNull("vip")?.let { vipObj ->
            BiliCommentVip(
                vipType = vipObj.intOrNull("vipType"),
                vipStatus = vipObj.intOrNull("vipStatus"),
                nicknameColor = vipObj.stringOrNull("nickname_color"),
                label = vipObj.objOrNull("label")?.let { labelObj ->
                    BiliCommentVipLabel(
                        text = labelObj.stringOrNull("text"),
                        labelTheme = labelObj.stringOrNull("label_theme"),
                        textColor = labelObj.stringOrNull("text_color"),
                        bgColor = labelObj.stringOrNull("bg_color")
                    )
                }
            )
        }

        val officialVerify = obj.objOrNull("official_verify")?.let {
            BiliCommentOfficialVerify(
                type = it.intOrNull("type"),
                desc = it.stringOrNull("desc")
            )
        }

        val pendant = obj.objOrNull("pendant")?.let {
            BiliCommentPendant(
                pid = it.longOrNull("pid"),
                name = it.stringOrNull("name"),
                image = it.stringOrNull("image")
            )
        }

        return BiliCommentMember(
            mid = obj.stringOrNull("mid"),
            uname = obj.stringOrNull("uname"),
            sex = obj.stringOrNull("sex"),
            avatar = obj.stringOrNull("avatar"),
            vip = vip,
            officialVerify = officialVerify,
            pendant = pendant
        )
    }

    private fun parseContent(obj: JsonObject): BiliCommentContent {
        val emoteObj = obj.objOrNull("emote")
        val emoteMap = emoteObj?.let { emote ->
            emote.keys.associateWith { key ->
                emote.objOrNull(key)?.let { emoteData ->
                    BiliEmote(
                        id = emoteData.longOrNull("id"),
                        text = emoteData.stringOrNull("text"),
                        url = emoteData.stringOrNull("url"),
                        type = emoteData.intOrNull("type"),
                        size = emoteData.intOrNull("size")
                    )
                }
            }.filterValues { it != null }.mapValues { it.value!! }
        }

        val membersArray = obj.arrayOrNull("members")
        val members = membersArray?.mapNotNull { memberObj ->
            memberObj.asObjectOrNull()?.let {
                BiliCommentAtMember(
                    mid = it.stringOrNull("mid"),
                    uname = it.stringOrNull("uname")
                )
            }
        }

        val jumpUrlObj = obj.objOrNull("jump_url")
        val jumpUrlMap = jumpUrlObj?.let { jumpUrl ->
            jumpUrl.keys.associateWith { key ->
                jumpUrl.objOrNull(key)?.let { urlData ->
                    BiliCommentJumpUrl(
                        title = urlData.stringOrNull("title"),
                        state = urlData.intOrNull("state"),
                        prefixIcon = urlData.stringOrNull("prefix_icon"),
                        clickable = urlData.booleanOrNull("clickable")
                    )
                }
            }.filterValues { it != null }.mapValues { it.value!! }
        }

        val picturesArray = obj.arrayOrNull("pictures")
        val pictures = picturesArray?.mapNotNull { picObj ->
            picObj.asObjectOrNull()?.let {
                BiliCommentPicture(
                    imgSrc = it.stringOrNull("img_src"),
                    imgWidth = it.intOrNull("img_width"),
                    imgHeight = it.intOrNull("img_height"),
                    imgSize = it.doubleOrNull("img_size")
                )
            }
        }

        return BiliCommentContent(
            message = obj.stringOrNull("message"),
            emote = emoteMap,
            members = members,
            jumpUrl = jumpUrlMap,
            pictures = pictures
        )
    }

    private fun parseReplyControl(obj: JsonObject): BiliReplyControl {
        return BiliReplyControl(
            upReply = obj.booleanOrNull("up_reply"),
            isUpTop = obj.booleanOrNull("is_up_top"),
            isNote = obj.booleanOrNull("is_note")
        )
    }
}
