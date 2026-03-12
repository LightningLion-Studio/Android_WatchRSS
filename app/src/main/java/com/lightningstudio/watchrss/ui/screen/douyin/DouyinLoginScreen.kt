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
import com.lightningstudio.watchrss.util.AppLogger
import kotlinx.coroutines.delay

@Composable
fun DouyinLoginScreen(
    onLoginComplete: (String) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    var isLoading by remember { mutableStateOf(true) }
    var loadProgress by remember { mutableFloatStateOf(0f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var cookieResult by remember { mutableStateOf<String?>(null) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var loginPanelExists by remember { mutableStateOf(false) }
    var loginPanelWasVisible by remember { mutableStateOf(false) }
    var isOnLoginPage by remember { mutableStateOf(false) }
    var loginStartTime by remember { mutableStateOf<Long?>(null) }
    var webViewLoadComplete by remember { mutableStateOf(false) }

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
        while (cookieResult == null && errorMessage == null) {
            delay(56)
            webView.evaluateJavascript(LOGIN_PANEL_CHECK_SCRIPT) { result ->
                val exists = result == "true"

                // Update login page status
                if (exists && !isOnLoginPage) {
                    isOnLoginPage = true
                    // Record login start time when first entering login page
                    loginStartTime = System.currentTimeMillis()
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
        while (cookieResult == null && errorMessage == null) {
            delay(1000)
            webView.evaluateJavascript(CLEAN_DOUYIN_CHAT_SCRIPT, null)
            webView.evaluateJavascript(CLEAN_SVG_SCRIPT, null)
        }
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
                        .clip(CircleShape)
                ) {
                    AndroidView(
                        factory = { ctx ->
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
                        },
                        modifier = Modifier.fillMaxSize(),
                        update = { webViewRef = it }
                    )
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
