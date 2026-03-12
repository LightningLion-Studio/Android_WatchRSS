package com.lightningstudio.watchrss.ui.components

import androidx.compose.foundation.text.InlineTextContent
import androidx.compose.foundation.text.appendInlineContent
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.Placeholder
import androidx.compose.ui.text.PlaceholderVerticalAlign
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lightningstudio.watchrss.sdk.bili.BiliCommentContent

@Composable
fun CommentRichText(
    content: BiliCommentContent?,
    modifier: Modifier = Modifier
) {
    if (content == null) return

    val message = content.message ?: ""
    val emotes = content.emote.orEmpty()
    val members = content.members.orEmpty()

    val (annotatedText, inlineContent) = buildRichText(message, emotes, members)

    Text(
        text = annotatedText,
        inlineContent = inlineContent,
        style = LocalTextStyle.current,
        modifier = modifier
    )
}

private fun buildRichText(
    message: String,
    emotes: Map<String, com.lightningstudio.watchrss.sdk.bili.BiliEmote>,
    members: List<com.lightningstudio.watchrss.sdk.bili.BiliCommentAtMember>
): Pair<AnnotatedString, Map<String, InlineTextContent>> {
    val inlineContentMap = mutableMapOf<String, InlineTextContent>()
    var text = message

    // Replace emotes with placeholders
    emotes.forEach { (key, emote) ->
        val placeholder = "[emote_${emote.id}]"
        text = text.replace(key, placeholder)

        inlineContentMap[placeholder] = InlineTextContent(
            placeholder = Placeholder(
                width = 20.sp,
                height = 20.sp,
                placeholderVerticalAlign = PlaceholderVerticalAlign.TextCenter
            )
        ) {
            AsyncImage(
                model = emote.url,
                contentDescription = emote.text
            )
        }
    }

    val annotatedString = buildAnnotatedString {
        var currentIndex = 0

        // Find and style @mentions
        val mentionRegex = Regex("@([^\\s@]+)")
        val matches = mentionRegex.findAll(text).toList()

        if (matches.isEmpty()) {
            // No mentions, check for emote placeholders
            val emoteRegex = Regex("\\[emote_\\d+\\]")
            val emoteMatches = emoteRegex.findAll(text).toList()

            if (emoteMatches.isEmpty()) {
                append(text)
            } else {
                emoteMatches.forEach { match ->
                    if (match.range.first > currentIndex) {
                        append(text.substring(currentIndex, match.range.first))
                    }
                    appendInlineContent(match.value, match.value)
                    currentIndex = match.range.last + 1
                }
                if (currentIndex < text.length) {
                    append(text.substring(currentIndex))
                }
            }
        } else {
            // Process mentions and emotes together
            val allMatches = (matches.map { it.range to "mention" } +
                    Regex("\\[emote_\\d+\\]").findAll(text).map { it.range to "emote" })
                .sortedBy { it.first.first }

            allMatches.forEach { (range, type) ->
                if (range.first > currentIndex) {
                    append(text.substring(currentIndex, range.first))
                }

                when (type) {
                    "mention" -> {
                        withStyle(SpanStyle(color = androidx.compose.ui.graphics.Color(0xFF00A1D6))) {
                            append(text.substring(range))
                        }
                    }
                    "emote" -> {
                        appendInlineContent(text.substring(range), text.substring(range))
                    }
                }
                currentIndex = range.last + 1
            }

            if (currentIndex < text.length) {
                append(text.substring(currentIndex))
            }
        }
    }

    return Pair(annotatedString, inlineContentMap)
}
