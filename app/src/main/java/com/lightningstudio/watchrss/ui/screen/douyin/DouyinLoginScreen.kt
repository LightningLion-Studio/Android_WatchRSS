package com.lightningstudio.watchrss.ui.screen.douyin

import android.os.Build
import android.view.View
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lightningstudio.watchrss.ui.util.getWebViewUnavailableMessage
import com.lightningstudio.watchrss.util.AppLogger
import com.lightningstudio.watchrss.ui.theme.rememberIsRoundWatch
import kotlinx.coroutines.delay

@Composable
fun DouyinLoginScreen(
    initialErrorMessage: String?,
    onWebViewInitFailed: (String) -> Unit,
    onLoginComplete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val isRoundWatch = rememberIsRoundWatch()

    var isLoading by remember(initialErrorMessage) { mutableStateOf(initialErrorMessage == null) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember(initialErrorMessage) { mutableStateOf(initialErrorMessage) }
    var cookieResult by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var loginPanelExists by remember { mutableStateOf(false) }
    var loginPanelWasVisible by remember { mutableStateOf(false) }
    var isOnLoginPage by remember { mutableStateOf(false) }
    var loginStartTime by remember { mutableStateOf<Long?>(null) }
    var webViewLoadComplete by remember { mutableStateOf(false) }

    // QR code detection states
    var needTwoStepVerification by remember { mutableStateOf(false) }
    var qrCodeBase64 by remember { mutableStateOf<String?>(null) }
    var qrCodeType by remember { mutableStateOf<String?>(null) } // "login" or "twoStep"
    var jsErrorMessage by remember { mutableStateOf<String?>(null) }

    // Loading state tracking
    var loadingStateStartTime by remember { mutableStateOf(System.currentTimeMillis()) }
    var loadingStateCounter by remember { mutableStateOf(1) }

    // Swipe back gesture state
    var screenWidth by remember { mutableFloatStateOf(0f) }

    // Track loading state changes
    LaunchedEffect(isLoading) {
        val currentTime = System.currentTimeMillis()
        val duration = currentTime - loadingStateStartTime
        val durationSeconds = duration / 1000.0

        if (loadingStateCounter > 1) {
            // Log previous state duration (skip first state as it's the initial state)
            val previousState = if (isLoading) "不loading" else "loading"
            AppLogger.log("DouyinLogin", "第${loadingStateCounter - 1}段${previousState}状态持续: ${duration}ms (${durationSeconds}秒)")
        }

        // Update for next state
        loadingStateStartTime = currentTime
        loadingStateCounter++
    }

    // Keep screen on when loading
    DisposableEffect(isLoading) {
        val window = (context as? android.app.Activity)?.window
        if (isLoading && window != null) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
        onDispose {
            window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    // Smooth progress animation
    val animatedProgress by animateFloatAsState(
        targetValue = loadProgress,
        animationSpec = tween(durationMillis = 300, easing = LinearEasing),
        label = "progress"
    )

    // Simulate progress after WebView load complete
    LaunchedEffect(webViewLoadComplete) {
        if (webViewLoadComplete) {
            while (loadProgress < 0.94f && !isOnLoginPage && errorMessage == null) {
                delay(1000)
                loadProgress = (loadProgress + 0.01f).coerceAtMost(0.94f)
            }
        }
    }

    // Check for login panel every 56ms
    LaunchedEffect(webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        AppLogger.log("DouyinLogin", "开始检测登录面板循环")
        while (cookieResult == null && errorMessage == null) {
            delay(56)
            webView.evaluateJavascript(LOGIN_PANEL_CHECK_SCRIPT) { result ->
                val exists = result == "true"

                // 只在状态变化时记录日志
                AppLogger.logOnChange("loginPanel", "DouyinLogin", "登录面板状态: exists=$exists, isOnLoginPage=$isOnLoginPage, loginPanelWasVisible=$loginPanelWasVisible")

                // Update login page status
                if (exists && !isOnLoginPage) {
                    isOnLoginPage = true
                    // Record login start time when first entering login page
                    loginStartTime = System.currentTimeMillis()
                    AppLogger.log("DouyinLogin", "检测到登录页面，开始计时")
                }

                if (loginPanelWasVisible && !exists) {
                    // Login panel disappeared - login successful
                    val cookieManager = CookieManager.getInstance()
                    val allCookies = cookieManager.getCookie("https://www.douyin.com") ?: ""
                    val douyinCookies = allCookies.split(";")
                        .map { it.trim() }
                        .filter { it.isNotEmpty() }
                        .joinToString("; ")

                    // Calculate and log login duration
                    val startTime = loginStartTime
                    if (startTime != null) {
                        val duration = System.currentTimeMillis() - startTime
                        AppLogger.log("DouyinLogin", "登录耗时: ${duration}ms (${duration / 1000.0}秒)")
                    }

                    cookieResult = douyinCookies
                    onLoginComplete(douyinCookies)
                }
                loginPanelWasVisible = exists
                loginPanelExists = exists
            }
        }
    }

    // 在检测到登录页面后，继续显示5秒的进度增长
    // 因为代码检测到isOnLoginPage和实际上屏还有5秒的黑屏渲染+网页里的二维码图片加载时间
    LaunchedEffect(isOnLoginPage) {
        if (isOnLoginPage) {
            val startProgress = loadProgress
            val targetProgress = 1.0f
            val duration = 5000L // 5 seconds
            val startTime = System.currentTimeMillis()

            while (System.currentTimeMillis() - startTime < duration) {
                val elapsed = System.currentTimeMillis() - startTime
                val progress = elapsed.toFloat() / duration
                loadProgress = startProgress + (targetProgress - startProgress) * progress
                delay(50)
            }

            loadProgress = targetProgress
            // 5秒后关闭loading状态
            isLoading = false
        }
    }

    // Clean page every 1 second
    LaunchedEffect(webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        AppLogger.log("DouyinLogin", "开始页面清理循环")
        while (cookieResult == null && errorMessage == null) {
            delay(1000)
            AppLogger.logOnChange("pageClean", "DouyinLogin", "执行页面清理: 移除聊天框和SVG元素")
            webView.evaluateJavascript(CLEAN_DOUYIN_CHAT_SCRIPT, null)
            webView.evaluateJavascript(CLEAN_SVG_SCRIPT, null)
        }
        AppLogger.log("DouyinLogin", "页面清理循环结束")
    }

    // Debug: Print FIND_TWO_STEP_VERIFICATION_QR_SCRIPT output every second
    LaunchedEffect(webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        AppLogger.log("DouyinLogin", "开始二步验证二维码脚本输出打印循环")
        while (cookieResult == null && errorMessage == null) {
            delay(1000)
            webView.evaluateJavascript(FIND_TWO_STEP_VERIFICATION_QR_SCRIPT) { result ->
                AppLogger.log("DouyinLogin", "FIND_TWO_STEP_VERIFICATION_QR_SCRIPT 输出: $result")
            }
        }
        AppLogger.log("DouyinLogin", "二步验证二维码脚本输出打印循环结束")
    }

    // Check for QR codes every 1 second
    LaunchedEffect(webViewRef) {
        val webView = webViewRef ?: return@LaunchedEffect
        AppLogger.log("DouyinLogin", "开始二维码检测循环")
        while (cookieResult == null && errorMessage == null) {
            delay(1000)

            // Check if two-step verification is needed
            webView.evaluateJavascript(IS_NEED_TWO_STEP_VERIFICATION_SCRIPT) { result ->
                try {
                    val newValue = result == "true"
                    if (needTwoStepVerification != newValue) {
                        AppLogger.log("DouyinLogin", "二步验证状态变化: $needTwoStepVerification -> $newValue")
                    }
                    needTwoStepVerification = newValue
                } catch (e: Exception) {
                    AppLogger.e("DouyinLogin", "检查二步验证状态失败", e)
                    jsErrorMessage = "检查二步验证状态失败: ${e.message}"
                }
            }

            // Check for two-step verification QR code first
            webView.evaluateJavascript(FIND_TWO_STEP_VERIFICATION_QR_SCRIPT) { result ->
                try {
                    if (result != null && result != "null" && result.startsWith("\"data:image/jpeg;base64,")) {
                        val base64Data = result.trim('"')
                        qrCodeBase64 = base64Data
                        qrCodeType = "twoStep"
                        AppLogger.log("DouyinLogin", "检测到二步验证二维码")
                    } else if (qrCodeType == "twoStep") {
                        // Two-step QR disappeared
                        qrCodeBase64 = null
                        qrCodeType = null
                    }
                } catch (e: Exception) {
                    AppLogger.e("DouyinLogin", "检查二步验证二维码失败", e)
                    jsErrorMessage = "检查二步验证二维码失败: ${e.message}"
                }
            }

            // If no two-step QR, check for login QR
            if (qrCodeBase64 == null) {
                webView.evaluateJavascript(FIND_LOGIN_QR_SCRIPT) { result ->
                    try {
                        if (result != null && result != "null" && result.startsWith("\"data:image/png;base64,")) {
                            val base64Data = result.trim('"')
                            qrCodeBase64 = base64Data
                            qrCodeType = "login"
                            AppLogger.log("DouyinLogin", "检测到登录二维码")
                        } else if (qrCodeType == "login") {
                            // Login QR disappeared
                            qrCodeBase64 = null
                            qrCodeType = null
                            AppLogger.log("DouyinLogin", "登录二维码消失")
                        }
                    } catch (e: Exception) {
                        AppLogger.e("DouyinLogin", "检查登录二维码失败", e)
                        jsErrorMessage = "检查登录二维码失败: ${e.message}"
                    }
                }
            } else {
                AppLogger.logOnChange("qrCodeStatus", "DouyinLogin", "当前二维码状态: type=$qrCodeType, hasData=${qrCodeBase64 != null}")
            }
        }
        AppLogger.log("DouyinLogin", "二维码检测循环结束")
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        // Wait for down event
                        val down = awaitPointerEvent(PointerEventPass.Initial).changes.first()

                        // Store screen width on first touch
                        if (screenWidth == 0f) {
                            screenWidth = size.width.toFloat()
                        }

                        val startX = down.position.x
                        val startY = down.position.y

                        // Check if touch started in left 65% height area
                        val leftEdgeTriggerHeight = size.height * 0.65f
                        if (startY > leftEdgeTriggerHeight) {
                            // Not in trigger zone, don't handle this gesture
                            continue
                        }

                        var totalDrag = 0f
                        var dragStarted = false

                        // Track drag
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.first()

                            if (change.pressed) {
                                val dragAmount = change.position.x - startX

                                // Only track rightward drags from left edge
                                if (dragAmount > 0) {
                                    totalDrag = dragAmount
                                    dragStarted = true
                                }
                            } else {
                                // Finger lifted
                                if (dragStarted) {
                                    val threshold = screenWidth * 0.35f

                                    if (totalDrag >= threshold) {
                                        // Trigger back navigation by finishing activity
                                        (context as? android.app.Activity)?.finish()
                                    }
                                }
                                break
                            }
                        }
                    }
                }
            }
    ) {
        when {
            cookieResult != null -> {
                // Show login success page
                LoginSuccessView()
            }
            errorMessage != null -> {
                // Show error page
                ErrorView(errorMessage = errorMessage!!)
            }
            else -> {
                // Show WebView
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clip(if (isRoundWatch) CircleShape else RectangleShape)
                ) {
                    // Show WebView (hidden when showing native QR code)
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .then(
                                if (qrCodeBase64 != null && !needTwoStepVerification) {
                                    Modifier.background(Color.Black)
                                } else {
                                    Modifier
                                }
                            )
                    ) {
                        AndroidView(
                        factory = { ctx ->
                            try {
                                WebView(ctx).apply {
                                    // 为了避免闪白
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                                        // 对于 API 21+，需要先允许背景设为透明
                                        setLayerType(View.LAYER_TYPE_HARDWARE, null)
                                        // 设置背景色为透明
                                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                                    }

                                    settings.apply {
                                        javaScriptEnabled = true
                                        domStorageEnabled = true
                                        databaseEnabled = true
                                        userAgentString = USER_AGENT
                                        loadWithOverviewMode = true
                                        useWideViewPort = true
                                    }

                                    webViewClient = object : WebViewClient() {
                                        override fun shouldOverrideUrlLoading(
                                            view: WebView?,
                                            request: WebResourceRequest?
                                        ): Boolean {
                                            return false
                                        }

                                        override fun onPageFinished(view: WebView?, url: String?) {
                                            super.onPageFinished(view, url)
                                            // Don't stop loading until we detect login page
                                        }

                                        override fun onReceivedError(
                                            view: WebView?,
                                            request: WebResourceRequest?,
                                            error: WebResourceError?
                                        ) {
                                            super.onReceivedError(view, request, error)
                                            if (request?.isForMainFrame == true) {
                                                // 记录技术性错误信息到日志
                                                AppLogger.e("DouyinLoginScreen", "WebView error: ${error?.description} (code: ${error?.errorCode})")

                                                // 根据错误代码提供友好的中文提示
                                                errorMessage = when (error?.errorCode) {
                                                    WebViewClient.ERROR_HOST_LOOKUP -> "无法找到服务器，请检查网络连接"
                                                    WebViewClient.ERROR_CONNECT -> "连接服务器失败，请稍后重试"
                                                    WebViewClient.ERROR_TIMEOUT -> "连接超时，请检查网络后重试"
                                                    WebViewClient.ERROR_IO -> "网络读写失败，请重试"
                                                    WebViewClient.ERROR_UNSUPPORTED_AUTH_SCHEME -> "不支持的认证方式"
                                                    WebViewClient.ERROR_AUTHENTICATION -> "身份验证失败"
                                                    WebViewClient.ERROR_PROXY_AUTHENTICATION -> "代理认证失败"
                                                    WebViewClient.ERROR_REDIRECT_LOOP -> "页面重定向次数过多"
                                                    WebViewClient.ERROR_UNSUPPORTED_SCHEME -> "不支持的链接协议"
                                                    WebViewClient.ERROR_FAILED_SSL_HANDSHAKE -> "安全连接失败，请检查网络环境"
                                                    WebViewClient.ERROR_BAD_URL -> "网址格式错误"
                                                    WebViewClient.ERROR_FILE -> "文件访问错误"
                                                    WebViewClient.ERROR_FILE_NOT_FOUND -> "文件不存在"
                                                    WebViewClient.ERROR_TOO_MANY_REQUESTS -> "请求过于频繁，请稍后重试"
                                                    WebViewClient.ERROR_UNSAFE_RESOURCE -> "页面存在安全风险，已被拦截"
                                                    WebViewClient.ERROR_UNKNOWN -> "加载失败，请重试"
                                                    else -> "加载失败，请重试"
                                                }
                                                isLoading = false
                                            }
                                        }
                                    }

                                    webChromeClient = object : WebChromeClient() {
                                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                            // Map WebView progress (0-100) to 0-45%
                                            val newMappedProgress = (newProgress / 100f) * 0.45f
                                            // 避免进度倒退，因为WebView的onProgressChanged可能会给出比当前值更小的进度
                                            if (newMappedProgress > loadProgress) {
                                                loadProgress = newMappedProgress
                                            }
                                            if (newProgress >= 100) {
                                                webViewLoadComplete = true
                                            }
                                            // Don't stop loading until we detect login page
                                        }
                                    }

                                    loadUrl(LOGIN_URL)
                                }
                            } catch (throwable: Throwable) {
                                AppLogger.e("DouyinLoginScreen", "Failed to initialize WebView", throwable)
                                val message = getWebViewUnavailableMessage(ctx)
                                    ?: "当前设备无法初始化 WebView，无法打开登录页"
                                FrameLayout(ctx).apply {
                                    post {
                                        errorMessage = message
                                        isLoading = false
                                        onWebViewInitFailed(message)
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { webViewRef = it as? WebView }
                    )
                    }

                    // Show native QR code overlay when detected (with fade animation)
                    // Logic: Show native QR if:
                    // 1. Has two-step verification QR (always show)
                    // 2. OR has login QR and doesn't need two-step verification
                    androidx.compose.animation.AnimatedVisibility(
                        visible = qrCodeBase64 != null && (qrCodeType == "twoStep" || !needTwoStepVerification),
                        enter = androidx.compose.animation.fadeIn(
                            animationSpec = tween(durationMillis = 300)
                        ),
                        exit = androidx.compose.animation.fadeOut(
                            animationSpec = tween(durationMillis = 300)
                        )
                    ) {
                        qrCodeBase64?.let { base64Data ->
                            NativeQRCodeView(
                                base64Data = base64Data,
                                qrCodeType = qrCodeType ?: "unknown"
                            )
                        }
                    }

                    // Show JS error if any
                    jsErrorMessage?.let { error ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.8f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.padding(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Warning,
                                    contentDescription = "Error",
                                    tint = Color(0xFFFF9800),
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(16.dp))
                                Text(
                                    text = "脚本执行错误",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color.White
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    text = error,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.Gray
                                )
                            }
                        }
                    }
                }

                // Circular loading indicator
                if (isLoading) {
                    CircularLoadingIndicator(progress = animatedProgress)
                }
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            // Log final loading state duration before component is destroyed
            val currentTime = System.currentTimeMillis()
            val duration = currentTime - loadingStateStartTime
            val durationSeconds = duration / 1000.0
            val currentState = if (isLoading) "loading" else "不loading"
            AppLogger.log("DouyinLogin", "第${loadingStateCounter - 1}段${currentState}状态持续: ${duration}ms (${durationSeconds}秒) [组件销毁]")

            webViewRef?.destroy()
        }
    }
}

@Composable
private fun CircularLoadingIndicator(progress: Float) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Canvas(
            modifier = Modifier.size(116.dp)
        ) {
            val strokeWidth = 4.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val centerX = size.width / 2
            val centerY = size.height / 2

            // Background circle
            drawCircle(
                color = Color.Gray.copy(alpha = 0.3f),
                radius = radius,
                center = Offset(centerX, centerY),
                style = Stroke(width = strokeWidth)
            )

            // Progress arc
            if (progress > 0f) {
                drawArc(
                    color = Color(0xFF1E88E5),
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    topLeft = Offset(
                        centerX - radius,
                        centerY - radius
                    ),
                    size = Size(radius * 2, radius * 2),
                    style = Stroke(
                        width = strokeWidth,
                        cap = StrokeCap.Round
                    )
                )
            }
        }
    }
}

@Composable
private fun LoginSuccessView() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Green circle background with checkmark
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .background(
                        color = Color(0xFF4CAF50), // Medium saturation green
                        shape = CircleShape
                    ),
                contentAlignment = Alignment.Center
            ) {
                // Draw checkmark
                Canvas(modifier = Modifier.size(60.dp)) {
                    val strokeWidth = 6.dp.toPx()
                    val checkColor = Color.White

                    // Short line (left part of checkmark)
                    drawLine(
                        color = checkColor,
                        start = Offset(size.width * 0.2f, size.height * 0.5f),
                        end = Offset(size.width * 0.4f, size.height * 0.7f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )

                    // Long line (right part of checkmark)
                    drawLine(
                        color = checkColor,
                        start = Offset(size.width * 0.4f, size.height * 0.7f),
                        end = Offset(size.width * 0.8f, size.height * 0.3f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = "登录成功",
                style = MaterialTheme.typography.titleLarge,
                color = Color.White
            )
        }
    }
}

@Composable
private fun NativeQRCodeView(
    base64Data: String,
    qrCodeType: String
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Top hint
            Text(
                text = "请使用手机扫码",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )

            Spacer(modifier = Modifier.weight(1f))

            // QR Code Image (70% of screen size)
            AndroidView(
                factory = { ctx ->
                    android.widget.ImageView(ctx).apply {
                        scaleType = android.widget.ImageView.ScaleType.FIT_CENTER
                    }
                },
                modifier = Modifier
                    .fillMaxSize(0.7f)
                    .clip(androidx.compose.foundation.shape.RoundedCornerShape(8.dp)),
                update = { imageView ->
                    try {
                        // Decode base64 to bitmap
                        val base64String = base64Data.substringAfter("base64,")
                        val decodedBytes = android.util.Base64.decode(base64String, android.util.Base64.DEFAULT)
                        val bitmap = android.graphics.BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)
                        imageView.setImageBitmap(bitmap)
                    } catch (e: Exception) {
                        AppLogger.e("DouyinLogin", "解码二维码图片失败", e)
                    }
                }
            )

            Spacer(modifier = Modifier.weight(1f))

            // Bottom hint
            Text(
                text = "请使用抖音App扫码",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.White
            )
        }
    }
}

@Composable
private fun ErrorView(errorMessage: String) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = "Error",
                tint = Color(0xFFFF9800),
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "加载失败",
                style = MaterialTheme.typography.titleMedium,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = errorMessage,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }
    }
}

private const val LOGIN_URL = "https://www.douyin.com/chat?isPopup=1"
private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/144.0.0.0 Safari/537.36 Edg/144.0.0.0"

private const val LOGIN_PANEL_CHECK_SCRIPT = """
(function() {
  // Check for article with id="douyin_login_comp_flat_panel"
  const article = document.querySelector('article#douyin_login_comp_flat_panel');
  if (article) {
    console.log('✅ 找到 <article id="douyin_login_comp_flat_panel">');
    return true;
  }

  // Check for div with id="douyin-login-new-id"
  const divNewId = document.getElementById('douyin-login-new-id');
  if (divNewId) {
    console.log('✅ 找到 <div id="douyin-login-new-id">');
    return true;
  }

  // Check for div with class="douyin_login_new_class"
  const divNewClass = document.querySelector('div.douyin_login_new_class');
  if (divNewClass) {
    console.log('✅ 找到 <div class="douyin_login_new_class">');
    return true;
  }

  // Check for div with id="login-panel-new"
  const loginPanel = document.getElementById('login-panel-new');
  if (loginPanel) {
    console.log('✅ 找到 <div id="login-panel-new">');
    return true;
  }

  // Check for div with text starting with "登录后免费"
  const allDivs = document.querySelectorAll('div');
  for (const div of allDivs) {
    const text = div.textContent.trim();
    if (text.startsWith('登录后免费')) {
      console.log('✅ 找到文本以"登录后免费"开头的 div');
      return true;
    }
  }

  console.log('❌ 未找到任何登录界面标识');
  return false;
})();
"""

private const val CLEAN_DOUYIN_CHAT_SCRIPT = """
(function() {
  const targetDivs = Array.from(document.querySelectorAll("div")).filter(
    (div) => div.textContent.trim() === "抖音聊天"
  );
  if (targetDivs.length === 0) return;

  function getDomDepth(element) {
    let depth = 0;
    let current = element;
    while (current.parentNode && current.parentNode !== document) {
      depth++;
      current = current.parentNode;
    }
    return depth;
  }

  let deepestDiv = targetDivs[0];
  let maxDepth = getDomDepth(deepestDiv);
  targetDivs.forEach((div) => {
    const currentDepth = getDomDepth(div);
    if (currentDepth > maxDepth) {
      maxDepth = currentDepth;
      deepestDiv = div;
    }
  });

  deepestDiv.remove();
})();
"""

private const val CLEAN_SVG_SCRIPT = """
(function() {
  const targetSvgs = Array.from(document.querySelectorAll("svg")).filter(
    (svg) => {
      return (
        svg.getAttribute("xmlns") === "http://www.w3.org/2000/svg" &&
        svg.getAttribute("width") === "37" &&
        svg.getAttribute("height") === "36" &&
        svg.getAttribute("viewBox") === "0 0 37 36" &&
        svg.getAttribute("fill") === "none"
      );
    }
  );

  targetSvgs.forEach((svg) => {
    if (svg && svg.parentNode) {
      svg.parentNode.remove();
    }
  });
})();
"""

private const val FIND_LOGIN_QR_SCRIPT = """
(function() {
  function findQrcodeImage() {
    const allImages = document.querySelectorAll('img');
    for (const img of allImages) {
      const ariaLabel = img.getAttribute('aria-label');
      if (ariaLabel !== '二维码') {
        continue;
      }
      const src = img.getAttribute('src');
      if (src && src.startsWith('data:image/png;base64,')) {
        return src;
      }
    }
    return null;
  }
  const qrcodeImg = findQrcodeImage();
  return qrcodeImg;
})();
"""

private const val FIND_TWO_STEP_VERIFICATION_QR_SCRIPT = """
(function() {
  function findQrcodeImage() {
    const allImages = document.querySelectorAll('img');
    for (const img of allImages) {
      const ariaLabel = img.getAttribute('aria-label');
      if (ariaLabel !== '二维码') {
        continue;
      }
      const src = img.getAttribute('src');
      if (src && src.startsWith('data:image/jpeg;base64,')) {
        return src;
      }
    }
    return null;
  }
  const qrcodeImg = findQrcodeImage();
  return qrcodeImg;
})();
"""

private const val IS_NEED_TWO_STEP_VERIFICATION_SCRIPT = """
(function() {
  function checkUcSecondVerifyDiv() {
    const targetDiv = document.getElementById('uc-second-verify');
    const isExist = targetDiv !== null && targetDiv.tagName.toLowerCase() === 'div';
    return isExist;
  }
  const hasUcSecondVerify = checkUcSecondVerifyDiv();
  return hasUcSecondVerify;
})();
"""
