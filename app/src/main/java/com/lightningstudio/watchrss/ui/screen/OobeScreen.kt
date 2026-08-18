package com.lightningstudio.watchrss.ui.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withLink
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lightningstudio.watchrss.R
import com.lightningstudio.watchrss.data.network.InternetAvailabilityStatus
import com.lightningstudio.watchrss.data.settings.DEFAULT_MEDIA_VOLUME_CONTROL_ENABLED
import com.lightningstudio.watchrss.data.settings.DEFAULT_MEDIA_VOLUME_GUARD_ENABLED
import com.lightningstudio.watchrss.data.settings.DEFAULT_READING_FONT_SIZE_SP
import com.lightningstudio.watchrss.data.settings.defaultMediaPlaybackStartVolumeLimitPercentForGuard
import com.lightningstudio.watchrss.data.settings.formatMediaPlaybackStartVolumeLimitPercent
import com.lightningstudio.watchrss.data.settings.nextMediaPlaybackStartVolumeLimitPercent
import com.lightningstudio.watchrss.data.settings.previousMediaPlaybackStartVolumeLimitPercent
import com.lightningstudio.watchrss.ui.components.InternetAvailabilityPage
import com.lightningstudio.watchrss.ui.components.WatchCheckbox
import com.lightningstudio.watchrss.ui.components.WatchSurface
import com.lightningstudio.watchrss.ui.components.WatchSwitch
import com.lightningstudio.watchrss.ui.components.internetAvailabilityGuidanceMessage
import com.lightningstudio.watchrss.ui.components.internetAvailabilityStatusMessage
import com.lightningstudio.watchrss.ui.input.InstallDigitalCrownScrollHandler
import com.lightningstudio.watchrss.ui.settings.MainSettingsCatalog
import com.lightningstudio.watchrss.ui.settings.WatchReadingThemeToggle
import com.lightningstudio.watchrss.ui.settings.MainSettingInfo
import com.lightningstudio.watchrss.ui.settings.WatchRoundIconButtonIcon
import com.lightningstudio.watchrss.ui.settings.WatchSettingsPillRow
import com.lightningstudio.watchrss.ui.settings.WatchStepperValue
import com.lightningstudio.watchrss.ui.testing.OobeTestTags
import com.lightningstudio.watchrss.ui.theme.WatchDimens
import com.lightningstudio.watchrss.ui.theme.watchDimensionResource
import com.lightningstudio.watchrss.ui.viewmodel.OobeUiState

private val OobeOrange = Color(0xFFFF8A3D)
private const val OOBE_WELCOME_PAGE = 0
private const val OOBE_AGREEMENT_PAGE = 1
private const val OOBE_CUSTOM_PAGE = 2
private const val OOBE_INTERNET_PAGE = 3

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
    onOpenPrivacy: () -> Unit,
    readerDisplayDark: Boolean = true,
    onToggleReaderDisplayMode: () -> Unit = {}
) {
    BackHandler(enabled = uiState.introPage > 0) {
        onSetIntroPage(uiState.introPage - 1)
    }

    WatchSurface(pureBlack = uiState.introPage == OOBE_CUSTOM_PAGE) {
        OobeIntroStep(
            uiState = uiState,
            onSetIntroPage = onSetIntroPage,
            onContinue = onContinueFromIntro,
            onOpenUserAgreement = onOpenUserAgreement,
            onOpenPrivacy = onOpenPrivacy,
            readerDisplayDark = readerDisplayDark,
            onToggleReaderDisplayMode = onToggleReaderDisplayMode
        )
    }
}

@Composable
private fun OobeIntroStep(
    uiState: OobeUiState,
    onSetIntroPage: (Int) -> Unit,
    onContinue: () -> Unit,
    onOpenUserAgreement: () -> Unit,
    onOpenPrivacy: () -> Unit,
    readerDisplayDark: Boolean,
    onToggleReaderDisplayMode: () -> Unit
) {
    val horizontalSafePadding = WatchDimens.watch_safe_padding
    val topSafePadding = WatchDimens.hey_distance_8dp
    val bottomSafePadding = WatchDimens.hey_distance_8dp
    val introPages = remember {
        listOf(
            IntroPageContent(
                label = "欢迎使用",
                title = "腕上RSS",
                badgeText = "RSS",
                accentColor = OobeOrange
            ),
            IntroPageContent(
                label = "腕上RSS",
                title = "腕上RSS",
                badgeText = "RSS",
                accentColor = OobeOrange
            )
        )
    }
    val introPage = uiState.introPage.coerceIn(OOBE_WELCOME_PAGE, OOBE_INTERNET_PAGE)

    var isAgreed by rememberSaveable { mutableStateOf(false) }
    var showAgreementError by rememberSaveable { mutableStateOf(false) }
    var showOfflineWarning by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(uiState.internetAvailabilityStatus) {
        if (uiState.internetAvailabilityStatus == InternetAvailabilityStatus.Available) {
            showOfflineWarning = false
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    start = horizontalSafePadding,
                    end = horizontalSafePadding,
                    top = topSafePadding,
                    bottom = bottomSafePadding
                )
                .testTag(OobeTestTags.ROOT),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            when (introPage) {
                OOBE_INTERNET_PAGE -> {
                    OobeInternetStep(
                        status = uiState.internetAvailabilityStatus,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag(OobeTestTags.INTERNET_PAGE),
                        onContinue = {
                            when (uiState.internetAvailabilityStatus) {
                                InternetAvailabilityStatus.Available -> onContinue()
                                InternetAvailabilityStatus.Unavailable,
                                InternetAvailabilityStatus.Bluetooth -> showOfflineWarning = true
                                InternetAvailabilityStatus.Checking -> Unit
                            }
                        }
                    )
                }

                OOBE_CUSTOM_PAGE -> {
                    OobeCustomizationStep(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag(OobeTestTags.CUSTOM_PAGE),
                        onNext = { onSetIntroPage(OOBE_INTERNET_PAGE) },
                        readerDisplayDark = readerDisplayDark,
                        onToggleReaderDisplayMode = onToggleReaderDisplayMode
                    )
                }

                else -> {
                    Spacer(modifier = Modifier.height(if (introPage == OOBE_WELCOME_PAGE) 2.dp else 0.dp))

                    IntroPage(
                        page = introPages[introPage],
                        showTitle = introPage != OOBE_AGREEMENT_PAGE,
                        compact = introPage > OOBE_WELCOME_PAGE,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                            .testTag(OobeTestTags.INTRO_PAGE)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                }
            }

            when (introPage) {
                OOBE_WELCOME_PAGE -> {
                    OobePrimaryButton(
                        text = "下一页",
                        enabled = true,
                        testTag = OobeTestTags.NEXT_BUTTON,
                        onClick = { onSetIntroPage(OOBE_AGREEMENT_PAGE) }
                    )
                }

                OOBE_AGREEMENT_PAGE -> {
                    OobeAgreementStep(
                        isAgreed = isAgreed,
                        showError = showAgreementError,
                        onAgreementChange = {
                            isAgreed = it
                            if (it) {
                                showAgreementError = false
                            }
                        },
                        onOpenUserAgreement = onOpenUserAgreement,
                        onOpenPrivacy = onOpenPrivacy,
                        onNext = {
                            if (isAgreed) {
                                showAgreementError = false
                                onSetIntroPage(OOBE_CUSTOM_PAGE)
                            } else {
                                showAgreementError = true
                            }
                        }
                    )
                }
                else -> Unit
            }
        }

        if (showOfflineWarning) {
            BackHandler(enabled = true) {
                showOfflineWarning = false
            }
            OobeOfflineWarningDialog(
                status = uiState.internetAvailabilityStatus,
                onConfirm = {
                    showOfflineWarning = false
                    onContinue()
                },
                onCancel = {
                    showOfflineWarning = false
                }
            )
        }
    }
}

@Composable
private fun OobeAgreementStep(
    isAgreed: Boolean,
    showError: Boolean,
    onAgreementChange: (Boolean) -> Unit,
    onOpenUserAgreement: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onNext: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
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

        Row(
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            WatchCheckbox(
                checked = isAgreed,
                onCheckedChange = onAgreementChange,
                modifier = Modifier
                    .size(18.dp)
                    .testTag(OobeTestTags.AGREEMENT_CHECKBOX)
            )

            Spacer(modifier = Modifier.size(4.dp))

            val linkStyle = TextLinkStyles(
                style = SpanStyle(
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
            )
            val annotatedText = buildAnnotatedString {
                append("同意")
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "user_agreement",
                        styles = linkStyle,
                        linkInteractionListener = { onOpenUserAgreement() }
                    )
                ) {
                    append("《用户协议》")
                }
                append("与")
                withLink(
                    LinkAnnotation.Clickable(
                        tag = "privacy",
                        styles = linkStyle,
                        linkInteractionListener = { onOpenPrivacy() }
                    )
                ) {
                    append("《隐私政策》")
                }
                append("。")
            }

            Text(
                text = annotatedText,
                style = MaterialTheme.typography.labelMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag(OobeTestTags.LEGAL_TEXT)
            )
        }

        OobePrimaryButton(
            text = "下一页",
            enabled = true,
            testTag = OobeTestTags.NEXT_BUTTON,
            onClick = onNext
        )
    }
}

@Composable
private fun OobeCustomizationStep(
    modifier: Modifier = Modifier,
    onNext: () -> Unit,
    readerDisplayDark: Boolean,
    onToggleReaderDisplayMode: () -> Unit
) {
    val scrollState = rememberScrollState()
    val readingThemeInfo = remember { MainSettingsCatalog.readingTheme }
    val fontSizeInfo = remember { MainSettingsCatalog.fontSize }
    val mediaVolumeControlInfo = remember { MainSettingsCatalog.mediaVolumeControl }
    val mediaVolumeGuardInfo = remember { MainSettingsCatalog.mediaVolumeGuard }
    val mediaPlaybackStartVolumeLimitInfo = remember { MainSettingsCatalog.mediaPlaybackStartVolumeLimit }
    val fontOptions = remember { (12..32 step 2).toList() }
    val entrySpacing = WatchDimens.hey_distance_8dp
    val pillHeight = WatchDimens.hey_multiple_item_height
    val stepperSpacing = WatchDimens.hey_distance_6dp
    val compactStepperSpacing = WatchDimens.hey_distance_4dp
    val stepperValueWidth = watchDimensionResource(R.dimen.watch_action_button_height)
    val playbackStartVolumeValueWidth = stepperValueWidth + compactStepperSpacing
    var previewFontSizeSp by rememberSaveable { mutableStateOf(DEFAULT_READING_FONT_SIZE_SP) }
    var previewMediaVolumeControlEnabled by rememberSaveable {
        mutableStateOf(DEFAULT_MEDIA_VOLUME_CONTROL_ENABLED)
    }
    var previewMediaVolumeGuardEnabled by rememberSaveable {
        mutableStateOf(DEFAULT_MEDIA_VOLUME_GUARD_ENABLED)
    }
    var previewMediaPlaybackStartVolumeLimitPercent by rememberSaveable {
        mutableStateOf(
            defaultMediaPlaybackStartVolumeLimitPercentForGuard(
                DEFAULT_MEDIA_VOLUME_GUARD_ENABLED
            )
        )
    }
    val lowerFont = fontOptions.lastOrNull { it < previewFontSizeSp }
    val higherFont = fontOptions.firstOrNull { it > previewFontSizeSp }

    InstallDigitalCrownScrollHandler(scrollState)

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(modifier = Modifier.height(10.dp))

        Text(
            text = "自定义",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "这些项目都能在设置中随时调整",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.widthIn(max = 208.dp)
        )

        Spacer(modifier = Modifier.height(12.dp))

        OobeCustomizationSetting(
            info = readingThemeInfo,
            endPaddingMultiplier = 1.5f
        ) {
            WatchReadingThemeToggle(
                isDark = readerDisplayDark,
                modifier = Modifier.testTag(OobeTestTags.CUSTOM_THEME_TOGGLE),
                onToggle = onToggleReaderDisplayMode
            )
        }

        Spacer(modifier = Modifier.height(entrySpacing))

        OobeCustomizationSetting(info = fontSizeInfo) {
            WatchRoundIconButtonIcon(
                icon = Icons.Outlined.Remove,
                contentDescription = "减小字体",
                enabled = lowerFont != null,
                onClick = { lowerFont?.let { previewFontSizeSp = it } }
            )
            Spacer(modifier = Modifier.width(stepperSpacing))
            WatchStepperValue(
                text = "${previewFontSizeSp}sp",
                width = stepperValueWidth,
                modifier = Modifier.testTag(OobeTestTags.CUSTOM_FONT_VALUE)
            )
            Spacer(modifier = Modifier.width(stepperSpacing))
            WatchRoundIconButtonIcon(
                icon = Icons.Filled.Add,
                contentDescription = "增大字体",
                enabled = higherFont != null,
                onClick = { higherFont?.let { previewFontSizeSp = it } }
            )
        }

        Spacer(modifier = Modifier.height(entrySpacing))

        OobeCustomizationSetting(
            info = mediaVolumeControlInfo,
            endPaddingMultiplier = 1.5f
        ) {
            WatchSwitch(
                checked = previewMediaVolumeControlEnabled,
                modifier = Modifier.testTag(OobeTestTags.CUSTOM_MEDIA_VOLUME_CONTROL_SWITCH),
                onCheckedChange = { previewMediaVolumeControlEnabled = it }
            )
        }

        if (previewMediaVolumeControlEnabled) {
            Spacer(modifier = Modifier.height(entrySpacing))

            OobeCustomizationSetting(
                info = mediaVolumeGuardInfo,
                endPaddingMultiplier = 1.5f
            ) {
                WatchSwitch(
                    checked = previewMediaVolumeGuardEnabled,
                    modifier = Modifier.testTag(OobeTestTags.CUSTOM_MEDIA_GUARD_SWITCH),
                    onCheckedChange = { previewMediaVolumeGuardEnabled = it }
                )
            }
        }

        Spacer(modifier = Modifier.height(entrySpacing))

        OobeCustomizationSetting(info = mediaPlaybackStartVolumeLimitInfo) {
            WatchRoundIconButtonIcon(
                icon = Icons.Outlined.Remove,
                contentDescription = "降低静音开播上限",
                enabled = true,
                onClick = {
                    previewMediaPlaybackStartVolumeLimitPercent =
                        previousMediaPlaybackStartVolumeLimitPercent(
                            previewMediaPlaybackStartVolumeLimitPercent
                        )
                }
            )
            Spacer(modifier = Modifier.width(compactStepperSpacing))
            WatchStepperValue(
                text = formatMediaPlaybackStartVolumeLimitPercent(
                    previewMediaPlaybackStartVolumeLimitPercent
                ),
                width = playbackStartVolumeValueWidth,
                modifier = Modifier.testTag(OobeTestTags.CUSTOM_PLAYBACK_START_VOLUME_VALUE)
            )
            Spacer(modifier = Modifier.width(compactStepperSpacing))
            WatchRoundIconButtonIcon(
                icon = Icons.Filled.Add,
                contentDescription = "提高静音开播上限",
                enabled = true,
                onClick = {
                    previewMediaPlaybackStartVolumeLimitPercent =
                        nextMediaPlaybackStartVolumeLimitPercent(
                            previewMediaPlaybackStartVolumeLimitPercent
                        )
                }
            )
        }

        Spacer(modifier = Modifier.height(entrySpacing))

        OobePrimaryButton(
            text = "下一页",
            enabled = true,
            testTag = OobeTestTags.NEXT_BUTTON,
            onClick = onNext
        )

        Spacer(modifier = Modifier.height(pillHeight))
    }
}

@Composable
private fun OobeCustomizationSetting(
    info: MainSettingInfo,
    endPaddingMultiplier: Float = 1f,
    content: @Composable RowScope.() -> Unit
) {
    val valueSpacing = WatchDimens.hey_distance_4dp
    val valueIndent = WatchDimens.hey_distance_10dp

    WatchSettingsPillRow(
        label = info.title,
        endPaddingMultiplier = endPaddingMultiplier,
        content = content
    )
    Text(
        text = info.description,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = valueIndent, top = valueSpacing)
    )
}

@Composable
private fun OobeInternetStep(
    status: InternetAvailabilityStatus,
    modifier: Modifier = Modifier,
    onContinue: () -> Unit
) {
    val continueEnabled = status != InternetAvailabilityStatus.Checking
    val statusMessage = internetAvailabilityStatusMessage(status)
    val guidanceMessage = internetAvailabilityGuidanceMessage(status)
    val statusTag = when (status) {
        InternetAvailabilityStatus.Checking -> OobeTestTags.INTERNET_STATUS_CHECKING
        InternetAvailabilityStatus.Unavailable -> OobeTestTags.INTERNET_STATUS_UNAVAILABLE
        InternetAvailabilityStatus.Bluetooth -> OobeTestTags.INTERNET_STATUS_BLUETOOTH
        InternetAvailabilityStatus.Available -> OobeTestTags.INTERNET_STATUS_AVAILABLE
    }

    InternetAvailabilityPage(
        status = status,
        guidanceMessage = guidanceMessage,
        statusMessage = statusMessage,
        actionText = "继续",
        actionEnabled = continueEnabled,
        actionModifier = Modifier.testTag(OobeTestTags.CONTINUE_BUTTON),
        statusIndicatorModifier = Modifier.testTag(statusTag),
        onAction = onContinue,
        modifier = modifier
    )
}

@Composable
private fun OobeOfflineWarningDialog(
    status: InternetAvailabilityStatus,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.92f))
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {}
                )
        )

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val maxSize = minOf(maxWidth, maxHeight)
            val containerSize = minOf(maxSize, 466.dp)
            val scale = (containerSize.value / 466f).coerceAtMost(1f)
            val scaleDp: (Dp) -> Dp = { value -> (value.value * scale).dp }

            Column(
                modifier = Modifier
                    .size(containerSize)
                    .clip(CircleShape)
                    .background(Color.Black)
                    .padding(top = scaleDp(96.dp), bottom = scaleDp(30.dp))
                    .testTag(OobeTestTags.OFFLINE_WARNING_DIALOG),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(scaleDp(32.dp))
            ) {
                OobeOfflineWarningDialogContent(
                    status = status,
                    scale = scale,
                    scaleDp = scaleDp
                )
                OobeOfflineWarningDialogButtons(
                    scaleDp = scaleDp,
                    onConfirm = onConfirm,
                    onCancel = onCancel
                )
            }
        }
    }
}

@Composable
private fun OobeOfflineWarningDialogContent(
    status: InternetAvailabilityStatus,
    scale: Float,
    scaleDp: (Dp) -> Dp
) {
    val fontFamily = FontFamily(Font(R.font.watch_sans))
    val message = when (status) {
        InternetAvailabilityStatus.Bluetooth -> "当前使用蓝牙网络，网速较慢，建议连接 WiFi 或移动网络"
        else -> "你没有连接到互联网，确定要继续吗"
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = scaleDp(40.dp))
            .height(scaleDp(204.dp)),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(scaleDp(8.dp), Alignment.CenterVertically)
    ) {
        Text(
            text = "警告",
            fontFamily = fontFamily,
            fontWeight = FontWeight.Medium,
            fontSize = (34f * scale).sp,
            lineHeight = (46f * scale).sp,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = message,
            fontFamily = fontFamily,
            fontWeight = FontWeight.Normal,
            fontSize = (34f * scale).sp,
            lineHeight = (46f * scale).sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OobeOfflineWarningDialogButtons(
    scaleDp: (Dp) -> Dp,
    onConfirm: () -> Unit,
    onCancel: () -> Unit
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(scaleDp(32.dp)),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OobeDialogIconButton(
            background = MaterialTheme.colorScheme.surfaceVariant,
            icon = Icons.Filled.Close,
            iconTint = MaterialTheme.colorScheme.onSurface,
            testTag = OobeTestTags.OFFLINE_WARNING_CANCEL_BUTTON,
            scaleDp = scaleDp,
            onClick = onCancel
        )
        OobeDialogIconButton(
            background = MaterialTheme.colorScheme.primary,
            icon = Icons.Filled.Check,
            iconTint = MaterialTheme.colorScheme.onPrimary,
            testTag = OobeTestTags.OFFLINE_WARNING_CONFIRM_BUTTON,
            scaleDp = scaleDp,
            onClick = onConfirm
        )
    }
}

@Composable
private fun OobeDialogIconButton(
    background: Color,
    icon: ImageVector,
    iconTint: Color,
    testTag: String,
    scaleDp: (Dp) -> Dp,
    onClick: () -> Unit
) {
    val size = scaleDp(104.dp)
    val iconSize = scaleDp(48.dp)

    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .testTag(testTag),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = iconTint,
            modifier = Modifier.size(iconSize)
        )
    }
}

@Composable
private fun IntroPage(
    page: IntroPageContent,
    showTitle: Boolean,
    compact: Boolean,
    modifier: Modifier = Modifier
) {
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val heroSize = if (compact) {
            (maxHeight * 0.36f).coerceIn(74.dp, 90.dp)
        } else {
            (maxHeight * 0.42f).coerceIn(82.dp, 104.dp)
        }
        val textWidth = (maxWidth * 0.96f).coerceAtMost(196.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = if (compact) 0.dp else 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IntroHero(
                badgeText = page.badgeText,
                accentColor = page.accentColor,
                size = heroSize
            )

            Spacer(modifier = Modifier.height(if (compact) 6.dp else 10.dp))

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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
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
