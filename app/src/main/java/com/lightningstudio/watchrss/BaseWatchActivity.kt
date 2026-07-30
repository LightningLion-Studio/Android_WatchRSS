package com.lightningstudio.watchrss

import android.annotation.SuppressLint
import com.lightningstudio.watchrss.data.telemetry.WatchUsageTelemetry
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.input.InputManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.Window
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lightningstudio.watchrss.debug.PerformanceMonitor
import com.lightningstudio.watchrss.ui.input.DigitalCrownScrollTracker
import com.lightningstudio.watchrss.ui.input.normalizeDigitalCrownScrollDelta
import com.lightningstudio.watchrss.ui.widget.WatchMaskLayout
import com.lightningstudio.watchrss.util.AppLogger
import kotlin.math.abs
import kotlin.math.max

open class BaseWatchActivity : ComponentActivity() {
    private var screenStartedAt: Long = 0L
    private val resetRunnable = Runnable { resetViewState(window.decorView) }
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeActive = false
    private var swipeIntercepting = false
    private var swipeLastDx = 0f
    private var swipeCommitted = false
    private var lastNavigationAt = 0L
    private var pendingActivityStartAllowanceAt = 0L
    private var hasResumedOnce = false
    private var usesPlatformSwipeDismiss = false
    private var pendingHardwareBackKeyCode: Int? = null
    private var nextDigitalCrownHandlerId = 0
    private val digitalCrownHandlers = LinkedHashMap<Int, DigitalCrownHandlerRegistration>()
    private val swipeSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop.toFloat() }
    private val minSwipeDistance by lazy {
        max(swipeSlop * 2f, resources.displayMetrics.density * 48f)
    }
    private val maxSwipeOffPath by lazy {
        max(swipeSlop * 3f, resources.displayMetrics.density * 48f)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        usesPlatformSwipeDismiss = requestPlatformSwipeDismissFeatureIfNeeded()
        if (BuildConfig.ENABLE_RUNTIME_PERF_MONITOR) {
            PerformanceMonitor.attach(this)
        }

        // 记录Activity启动
        AppLogger.log("Activity", "启动: ${this.javaClass.simpleName}")
    }

    override fun onResume() {
        super.onResume()
        hasResumedOnce = true
        window.decorView.removeCallbacks(resetRunnable)
        swipeCommitted = false
        resetNavigationThrottle()
        resetViewState(window.decorView)
        screenStartedAt = SystemClock.elapsedRealtime()
        telemetryOrNull()?.recordScreenOpen(screenName())

        // 记录Activity活跃
        AppLogger.log("Activity", "活跃: ${this.javaClass.simpleName}")
    }

    override fun setContentView(layoutResID: Int) {
        if (!BuildConfig.ENABLE_WATCH_DEBUG_MASK) {
            super.setContentView(layoutResID)
            return
        }
        val maskLayout = WatchMaskLayout(this)
        layoutInflater.inflate(layoutResID, maskLayout, true)
        super.setContentView(maskLayout)
    }

    override fun setContentView(view: View?) {
        if (view == null) {
            super.setContentView(view)
            return
        }
        if (!BuildConfig.ENABLE_WATCH_DEBUG_MASK) {
            super.setContentView(view)
            return
        }
        val maskLayout = WatchMaskLayout(this)
        maskLayout.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        super.setContentView(maskLayout)
    }

    override fun setContentView(view: View?, params: ViewGroup.LayoutParams?) {
        if (view == null) {
            super.setContentView(view, params)
            return
        }
        if (!BuildConfig.ENABLE_WATCH_DEBUG_MASK) {
            super.setContentView(view, params)
            return
        }
        val maskLayout = WatchMaskLayout(this)
        maskLayout.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        )
        if (params == null) {
            super.setContentView(maskLayout)
        } else {
            super.setContentView(maskLayout, params)
        }
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val root = window.decorView
        val action = ev.actionMasked
        val swipeHandled = if (isSwipeBackEnabled() && !usesPlatformSwipeDismiss) {
            handleSwipeBack(root, ev)
        } else {
            false
        }
        if (swipeHandled) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                root.removeCallbacks(resetRunnable)
                if (!swipeCommitted && shouldScheduleDelayedViewStateResetOnTouchEnd()) {
                    root.postDelayed(resetRunnable, RESET_DELAY_MS)
                }
            }
            return true
        }
        val handled = super.dispatchTouchEvent(ev)
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            root.removeCallbacks(resetRunnable)
            if (shouldResetViewStateImmediatelyOnTouchEnd()) {
                resetViewState(root)
            }
            if (shouldScheduleDelayedViewStateResetOnTouchEnd()) {
                root.postDelayed(resetRunnable, RESET_DELAY_MS)
            }
        }
        return handled
    }

    override fun dispatchGenericMotionEvent(ev: MotionEvent): Boolean {
        if (ev.actionMasked == MotionEvent.ACTION_SCROLL) {
            if (ev.isFromSource(InputDevice.SOURCE_CLASS_POINTER)) {
                DigitalCrownScrollTracker.onScrollEvent(ev.eventTime)
                val delta = ev.getAxisValue(MotionEvent.AXIS_VSCROLL)
                if (delta != 0f && dispatchDigitalCrownHandlers(delta, digitalCrown = true)) {
                    return true
                }
            }
        }
        return super.dispatchGenericMotionEvent(ev)
    }

    @SuppressLint("RestrictedApi")
    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (shouldMapHardwareBackKey(event)) {
            return handleHardwareBackKeyEvent(event)
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onPause() {
        buildResumeIntent()?.let { AppResumeStateStore.save(this, it) }
        window.decorView.removeCallbacks(resetRunnable)
        if (!swipeCommitted) {
            resetViewState(window.decorView)
        }
        val startedAt = screenStartedAt
        if (startedAt > 0L) {
            telemetryOrNull()?.recordScreenDuration(screenName(), SystemClock.elapsedRealtime() - startedAt)
            screenStartedAt = 0L
        }
        pendingActivityStartAllowanceAt = 0L
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing) {
            buildResumeIntent()?.let { AppResumeStateStore.clearIfMatches(this, it) }
        }
        super.onDestroy()
    }

    override fun startActivity(intent: Intent) {
        startActivity(intent, null)
    }

    override fun startActivity(intent: Intent, options: Bundle?) {
        if (!allowActivityStart(intent)) {
            return
        }
        super.startActivity(intent, options)
    }

    override fun attachBaseContext(newBase: Context) {
        val safeBase = if (newBase is SafeReceiverContextWrapper) {
            newBase
        } else {
            SafeReceiverContextWrapper(newBase)
        }
        super.attachBaseContext(safeBase)
    }

    protected fun setupSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.statusBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
    }

    private fun resetViewState(view: View) {
        val skipScale = view.getTag(R.id.tag_skip_scale_reset) == true
        val skipTranslation = view.getTag(R.id.tag_skip_translation_reset) == true
        val skipAnimationCancel = skipScale || skipTranslation
        view.isPressed = false
        view.isSelected = false
        view.isActivated = false
        view.stateListAnimator = null
        view.clearAnimation()
        if (!skipAnimationCancel) {
            view.animate().cancel()
        }
        if (!skipScale) {
            view.scaleX = 1f
            view.scaleY = 1f
        }
        view.alpha = 1f
        if (!skipTranslation) {
            view.translationX = 0f
            view.translationY = 0f
        }
        if (view is ViewGroup) {
            for (index in 0 until view.childCount) {
                val child = view.getChildAt(index)
                if (child != null) {
                    resetViewState(child)
                }
            }
        }
    }

    private fun handleSwipeBack(root: View, ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                root.animate().cancel()
                root.translationX = 0f
                swipeCommitted = false
                swipeActive = shouldStartSwipe(root, ev)
                swipeIntercepting = false
                swipeLastDx = 0f
                swipeStartX = ev.x
                swipeStartY = ev.y
            }
            MotionEvent.ACTION_MOVE -> {
                if (swipeActive && !swipeIntercepting) {
                    val dx = ev.x - swipeStartX
                    val dy = ev.y - swipeStartY
                    val absDx = abs(dx)
                    val absDy = abs(dy)
                    if (dx > swipeSlop && absDx > absDy && absDy < maxSwipeOffPath) {
                        if (shouldDeferSwipeBack(dx, dy)) {
                            return false
                        }
                        if (onSwipeBackAttempt(dx, dy)) {
                            cancelChildTouch(ev)
                            resetSwipeState()
                            return true
                        }
                        swipeIntercepting = true
                        cancelChildTouch(ev)
                    }
                    if (absDy > maxSwipeOffPath) {
                        swipeActive = false
                    }
                }
                if (swipeIntercepting) {
                    swipeLastDx = (ev.x - swipeStartX).coerceAtLeast(0f)
                    if (shouldAnimateSwipeBackGesture()) {
                        root.translationX = swipeLastDx
                    }
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (swipeIntercepting) {
                    val dx = swipeLastDx
                    val dy = abs(ev.y - swipeStartY)
                    val commitDistance = max(minSwipeDistance, root.width * SWIPE_COMMIT_RATIO)
                    if (dx > commitDistance && dy < maxSwipeOffPath) {
                        if (shouldAnimateSwipeBackGesture()) {
                            animateBackCommit(root)
                        } else {
                            commitBack(root)
                        }
                    } else {
                        cancelBack(root)
                    }
                    resetSwipeState()
                    return true
                }
                resetSwipeState()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (swipeIntercepting) {
                    cancelBack(root)
                    resetSwipeState()
                    return true
                }
                resetSwipeState()
            }
        }
        return swipeIntercepting
    }

    protected open fun onSwipeBackAttempt(dx: Float, dy: Float): Boolean = false

    protected open fun shouldDeferSwipeBack(dx: Float, dy: Float): Boolean = false

    protected open fun isSwipeBackEnabled(): Boolean = true

    protected open fun shouldUsePlatformSwipeDismissFeature(): Boolean = true

    protected open fun shouldAnimateSwipeBackGesture(): Boolean = true

    protected open fun shouldResetViewStateImmediatelyOnTouchEnd(): Boolean = true

    protected open fun shouldScheduleDelayedViewStateResetOnTouchEnd(): Boolean = true

    protected open fun buildResumeIntent(): Intent? = null

    protected open fun shouldMapHardwareBackKey(event: KeyEvent): Boolean {
        return when (event.keyCode) {
            KeyEvent.KEYCODE_STEM_1,
            KeyEvent.KEYCODE_STEM_2 -> true
            else -> false
        }
    }

    fun registerDigitalCrownHandler(
        supportsDigitalCrown: Boolean = false,
        handler: (Float) -> Boolean
    ): Int {
        val id = ++nextDigitalCrownHandlerId
        digitalCrownHandlers[id] = DigitalCrownHandlerRegistration(
            supportsDigitalCrown = supportsDigitalCrown,
            handler = handler
        )
        return id
    }

    fun unregisterDigitalCrownHandler(id: Int) {
        digitalCrownHandlers.remove(id)
    }

    protected fun allowNavigation(minIntervalMs: Long = NAVIGATION_THROTTLE_MS): Boolean {
        return tryMarkNavigation(
            now = SystemClock.elapsedRealtime(),
            minIntervalMs = minIntervalMs,
            reserveActivityStart = true
        )
    }

    fun tryAllowNavigation(minIntervalMs: Long = NAVIGATION_THROTTLE_MS): Boolean {
        return allowNavigation(minIntervalMs)
    }

    fun resetNavigationThrottle() {
        lastNavigationAt = 0L
        pendingActivityStartAllowanceAt = 0L
    }


    protected open fun screenName(): String = this.javaClass.simpleName

    private fun telemetryOrNull(): WatchUsageTelemetry? {
        return (applicationContext as? WatchRssApplication)?.container?.watchUsageTelemetry
    }
    private fun shouldStartSwipe(root: View, ev: MotionEvent): Boolean {
        val width = root.width
        if (width <= 0 || ev.pointerCount != 1) {
            return false
        }
        return ev.x <= width * SWIPE_START_RATIO
    }

    @Suppress("DEPRECATION")
    private fun requestPlatformSwipeDismissFeatureIfNeeded(): Boolean {
        if (!isSwipeBackEnabled()) {
            return false
        }
        if (!shouldUsePlatformSwipeDismissFeature()) {
            return false
        }
        if (!isOppoDevice()) {
            return false
        }
        if (!packageManager.hasSystemFeature(PackageManager.FEATURE_WATCH)) {
            return false
        }
        return runCatching {
            requestWindowFeature(Window.FEATURE_SWIPE_TO_DISMISS)
        }.getOrElse { error ->
            AppLogger.d(
                "SwipeBack",
                "系统右滑退出特性启用失败: ${error.javaClass.simpleName}: ${error.message.orEmpty()}"
            )
            false
        }
    }

    private fun isOppoDevice(): Boolean {
        return listOf(
            Build.MANUFACTURER,
            Build.BRAND,
            Build.PRODUCT,
            Build.DEVICE,
            Build.MODEL,
            Build.FINGERPRINT
        ).any { marker ->
            marker.contains("oppo", ignoreCase = true) ||
                marker.contains("oplus", ignoreCase = true)
        }
    }

    private fun handleHardwareBackKeyEvent(event: KeyEvent): Boolean {
        when (event.action) {
            KeyEvent.ACTION_DOWN -> {
                if (event.repeatCount == 0) {
                    pendingHardwareBackKeyCode = event.keyCode
                }
                return true
            }

            KeyEvent.ACTION_UP -> {
                val shouldDispatchBack =
                    pendingHardwareBackKeyCode == event.keyCode && !event.isCanceled
                pendingHardwareBackKeyCode = null
                if (shouldDispatchBack) {
                    AppLogger.d(
                        "Input",
                        "映射硬件返回键: ${KeyEvent.keyCodeToString(event.keyCode)} -> 应用内返回"
                    )
                    onBackPressedDispatcher.onBackPressed()
                }
                return true
            }

            else -> return true
        }
    }

    private fun cancelChildTouch(ev: MotionEvent) {
        val cancelEvent = MotionEvent.obtain(ev)
        cancelEvent.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    private fun dispatchDigitalCrownHandlers(delta: Float, digitalCrown: Boolean = false): Boolean {
        val handlers = digitalCrownHandlers.values.toList().asReversed()
        for (registration in handlers) {
            if (digitalCrown && !registration.supportsDigitalCrown) {
                continue
            }
            if (registration.handler(delta)) {
                return true
            }
        }
        return false
    }

    private fun animateBackCommit(root: View) {
        swipeCommitted = true
        val now = SystemClock.elapsedRealtime()
        markNavigation(now)
        root.removeCallbacks(resetRunnable)
        val target = root.width.toFloat().coerceAtLeast(1f)
        root.animate()
            .translationX(target)
            .setDuration(SWIPE_ANIM_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                onBackPressedDispatcher.onBackPressed()
                clearPendingTransitionAnimation()
            }
            .start()
    }

    private fun commitBack(root: View) {
        swipeCommitted = true
        val now = SystemClock.elapsedRealtime()
        markNavigation(now)
        root.removeCallbacks(resetRunnable)
        root.translationX = 0f
        onBackPressedDispatcher.onBackPressed()
        clearPendingTransitionAnimation()
    }

    private fun animateBackCancel(root: View) {
        root.animate()
            .translationX(0f)
            .setDuration(SWIPE_ANIM_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                root.translationX = 0f
            }
            .start()
    }

    private fun cancelBack(root: View) {
        if (shouldAnimateSwipeBackGesture()) {
            animateBackCancel(root)
            return
        }
        root.translationX = 0f
    }

    private fun resetSwipeState() {
        swipeActive = false
        swipeIntercepting = false
        swipeLastDx = 0f
    }

    private fun allowActivityStart(intent: Intent, minIntervalMs: Long = NAVIGATION_THROTTLE_MS): Boolean {
        if (!hasResumedOnce) {
            return true
        }
        val now = SystemClock.elapsedRealtime()
        if (consumePendingActivityStartAllowance(now)) {
            return true
        }
        val allowed = tryMarkNavigation(
            now = now,
            minIntervalMs = minIntervalMs,
            reserveActivityStart = false
        )
        if (!allowed) {
            val target = intent.component?.shortClassName ?: intent.action ?: "unknown"
            AppLogger.d(
                "Navigation",
                "拦截连续页面启动: ${javaClass.simpleName} -> $target"
            )
        }
        return allowed
    }

    private fun consumePendingActivityStartAllowance(now: Long): Boolean {
        val allowanceAt = pendingActivityStartAllowanceAt
        if (allowanceAt == 0L) {
            return false
        }
        if (now - allowanceAt > ACTIVITY_START_ALLOWANCE_WINDOW_MS) {
            pendingActivityStartAllowanceAt = 0L
            return false
        }
        pendingActivityStartAllowanceAt = 0L
        return true
    }

    private fun tryMarkNavigation(
        now: Long,
        minIntervalMs: Long,
        reserveActivityStart: Boolean
    ): Boolean {
        if (now - lastNavigationAt < minIntervalMs) {
            return false
        }
        markNavigation(now)
        pendingActivityStartAllowanceAt = if (reserveActivityStart) now else 0L
        return true
    }

    private fun markNavigation(now: Long) {
        lastNavigationAt = now
    }

    @Suppress("DEPRECATION")
    private fun clearPendingTransitionAnimation() {
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val RESET_DELAY_MS = 350L
        private const val SWIPE_START_RATIO = 0.65f
        private const val SWIPE_COMMIT_RATIO = 0.35f
        private const val SWIPE_ANIM_DURATION_MS = 200L
        private const val ACTIVITY_START_ALLOWANCE_WINDOW_MS = 150L
        private const val NAVIGATION_THROTTLE_MS = 600L
    }
}

private data class DigitalCrownHandlerRegistration(
    val supportsDigitalCrown: Boolean,
    val handler: (Float) -> Boolean
)
