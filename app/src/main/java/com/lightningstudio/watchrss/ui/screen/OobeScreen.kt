package com.lightningstudio.watchrss.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.testing.OobeTestTags
import com.lightningstudio.watchrss.ui.viewmodel.OobeUiState

private data class IntroPageContent(
    val label: String,
    val title: String,
    val badgeText: String,
    val accentColor: Color
)

@Composable
fun OobeScreen(
    uiState: OobeUiState,
    onSetIntroPage: (Int) -> Unit,
    onContinueFromIntro: () -> Unit,
    onOpenUserAgreement: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    BackHandler(enabled = uiState.introPage > 0) {
        onSetIntroPage(uiState.introPage - 1)
    }

    WatchSurface {
        OobeIntroStep(
            currentPage = uiState.introPage,
            onSetIntroPage = onSetIntroPage,
            onContinue = onContinueFromIntro,
            onOpenUserAgreement = onOpenUserAgreement,
            onOpenPrivacy = onOpenPrivacy
        )
    }
}

@Composable
private fun OobeIntroStep(
    currentPage: Int,
    onSetIntroPage: (Int) -> Unit,
    onContinue: () -> Unit,
    onOpenUserAgreement: () -> Unit,
    onOpenPrivacy: () -> Unit
) {
    val horizontalSafePadding = dimensionResource(R.dimen.watch_safe_padding)
    val verticalSafePadding = 8.dp
    val introPage = currentPage.coerceAtLeast(0)
    val introContent = remember {
        IntroPageContent(
            label = "腕上RSS",
            title = "腕上RSS",
            badgeText = "RSS",
            accentColor = Color(0xFFFF8A3D)
        )
    }

    var isAgreed by remember { mutableStateOf(false) }
    var showError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = horizontalSafePadding, vertical = verticalSafePadding)
            .testTag(OobeTestTags.ROOT),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(2.dp))

        IntroPage(
            page = introContent,
            showTitle = introPage == 0,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag(OobeTestTags.INTRO_PAGE)
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (introPage > 0) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // 错误提示
                if (showError) {
                    Text(
                        text = "请勾选\"同意《用户协议》与《隐私政策》\"",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier
                            .padding(bottom = 2.dp)
                            .testTag(OobeTestTags.ERROR_TEXT)
                    )
                }

                // Checkbox 和协议文本
                Row(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    Checkbox(
                        checked = isAgreed,
                        onCheckedChange = {
                            isAgreed = it
                            if (isAgreed) showError = false
                        },
                        modifier = Modifier
                            .size(18.dp)
                            .testTag(OobeTestTags.AGREEMENT_CHECKBOX)
                    )

                    Spacer(modifier = Modifier.size(4.dp))

                    val annotatedText = buildAnnotatedString {
                        append("同意")
                        pushStringAnnotation(tag = "user_agreement", annotation = "user_agreement")
                        withStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append("《用户协议》")
                        }
                        pop()
                        append("与")
                        pushStringAnnotation(tag = "privacy", annotation = "privacy")
                        withStyle(
                            style = SpanStyle(
                                color = MaterialTheme.colorScheme.primary,
                                textDecoration = TextDecoration.Underline
                            )
                        ) {
                            append("《隐私政策》")
                        }
                        pop()
                    }

                    ClickableText(
                        text = annotatedText,
                        style = MaterialTheme.typography.labelMedium.copy(
                            color = MaterialTheme.colorScheme.onSurface
                        ),
                        modifier = Modifier.testTag(OobeTestTags.LEGAL_TEXT),
                        onClick = { offset ->
                            annotatedText.getStringAnnotations(offset, offset)
                                .firstOrNull()?.let { annotation ->
                                    when (annotation.tag) {
                                        "user_agreement" -> onOpenUserAgreement()
                                        "privacy" -> onOpenPrivacy()
                                    }
                                }
                        }
                    )
                }

                OobePrimaryButton(
                    text = "继续",
                    enabled = true,
                    testTag = OobeTestTags.CONTINUE_BUTTON,
                    onClick = {
                        if (isAgreed) {
                            showError = false
                            onContinue()
                        } else {
                            showError = true
                        }
                    }
                )
            }
        } else {
            OobePrimaryButton(
                text = "下一页",
                enabled = true,
                testTag = OobeTestTags.NEXT_BUTTON,
                onClick = { onSetIntroPage(1) }
            )
        }
    }
}

@Composable
private fun IntroPage(
    page: IntroPageContent,
    showTitle: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heroSize = (maxHeight * 0.42f).coerceIn(82.dp, 104.dp)
        val textWidth = (maxWidth * 0.96f).coerceAtMost(196.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IntroHero(
                badgeText = page.badgeText,
                accentColor = page.accentColor,
                size = heroSize
            )

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = page.label,
                style = MaterialTheme.typography.labelLarge,
                color = page.accentColor
            )

            if (showTitle) {
                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = page.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.widthIn(max = textWidth)
                )
            }

            Spacer(modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun IntroHero(
    badgeText: String,
    accentColor: Color,
    size: Dp
) {
    val ringSize = (size * 0.84f).coerceAtLeast(78.dp)
    val coreSize = (size * 0.66f).coerceAtLeast(58.dp)
    val topDotSize = if (size < 110.dp) 10.dp else 12.dp
    val bottomDotSize = if (size < 110.dp) 7.dp else 8.dp

    Box(
        modifier = Modifier.size(size),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(
                            accentColor.copy(alpha = 0.22f),
                            Color.Transparent
                        )
                    )
                )
        )
        Box(
            modifier = Modifier
                .size(ringSize)
                .clip(CircleShape)
                .border(1.dp, accentColor.copy(alpha = 0.35f), CircleShape)
        )
        Box(
            modifier = Modifier
                .size(coreSize)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.94f))
                .border(1.dp, accentColor.copy(alpha = 0.3f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = badgeText,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold
            )
        }
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(
                    top = if (size < 110.dp) 16.dp else 22.dp,
                    end = if (size < 110.dp) 12.dp else 18.dp
                )
                .size(topDotSize)
                .clip(CircleShape)
                .background(accentColor)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(
                    bottom = if (size < 110.dp) 16.dp else 20.dp,
                    start = if (size < 110.dp) 14.dp else 20.dp
                )
                .size(bottomDotSize)
                .clip(CircleShape)
                .background(accentColor.copy(alpha = 0.7f))
        )
    }
}

@Composable
private fun OobePrimaryButton(
    text: String,
    enabled: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = Modifier
            .testTag(testTag)
            .clip(shape)
            .background(
                if (enabled) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.surfaceVariant
                }
            )
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 22.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.titleSmall,
            color = if (enabled) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold
        )
    }
}
