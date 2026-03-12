package com.lightningstudio.watchrss

import android.content.Context
import android.os.Bundle
import android.os.SystemClock
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.activity.ComponentActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.lightningstudio.watchrss.debug.PerformanceMonitor
import com.lightningstudio.watchrss.ui.widget.WatchMaskLayout
import com.lightningstudio.watchrss.util.AppLogger
import kotlin.math.abs
import kotlin.math.max

open class BaseWatchActivity : ComponentActivity() {
    private val resetRunnable = Runnable { resetViewState(window.decorView) }
    private var swipeStartX = 0f
    private var swipeStartY = 0f
    private var swipeActive = false
    private var swipeIntercepting = false
    private var swipeLastDx = 0f
    private var swipeCommitted = false
    private var lastNavigationAt = 0L
    private val swipeSlop by lazy { ViewConfiguration.get(this).scaledTouchSlop.toFloat() }
    private val minSwipeDistance by lazy {
        max(swipeSlop * 2f, resources.displayMetrics.density * 48f)
    }
    private val maxSwipeOffPath by lazy {
        max(swipeSlop * 3f, resources.displayMetrics.density * 48f)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        PerformanceMonitor.attach(this)

        // 记录Activity启动
        AppLogger.log("Activity", "启动: ${this.javaClass.simpleName}")
    }

    override fun onResume() {
        super.onResume()
        window.decorView.removeCallbacks(resetRunnable)
        swipeCommitted = false
        resetViewState(window.decorView)

        // 记录Activity活跃
        AppLogger.log("Activity", "活跃: ${this.javaClass.simpleName}")
    }

    override fun setContentView(layoutResID: Int) {
        if (!BuildConfig.DEBUG) {
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
        if (!BuildConfig.DEBUG) {
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
        if (!BuildConfig.DEBUG) {
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
        val swipeHandled = if (isSwipeBackEnabled()) {
            handleSwipeBack(root, ev)
        } else {
            false
        }
        if (swipeHandled) {
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                root.removeCallbacks(resetRunnable)
                if (!swipeCommitted) {
                    root.postDelayed(resetRunnable, RESET_DELAY_MS)
                }
            }
            return true
        }
        val handled = super.dispatchTouchEvent(ev)
        if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            root.removeCallbacks(resetRunnable)
            resetViewState(root)
            root.postDelayed(resetRunnable, RESET_DELAY_MS)
        }
        return handled
    }

    override fun onPause() {
        window.decorView.removeCallbacks(resetRunnable)
        if (!swipeCommitted) {
            resetViewState(window.decorView)
        }
        super.onPause()
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
                    root.translationX = swipeLastDx
                    return true
                }
            }
            MotionEvent.ACTION_UP -> {
                if (swipeIntercepting) {
                    val dx = swipeLastDx
                    val dy = abs(ev.y - swipeStartY)
                    val commitDistance = max(minSwipeDistance, root.width * SWIPE_COMMIT_RATIO)
                    if (dx > commitDistance && dy < maxSwipeOffPath) {
                        animateBackCommit(root)
                    } else {
                        animateBackCancel(root)
                    }
                    resetSwipeState()
                    return true
                }
                resetSwipeState()
            }
            MotionEvent.ACTION_CANCEL -> {
                if (swipeIntercepting) {
                    animateBackCancel(root)
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

    protected fun allowNavigation(minIntervalMs: Long = NAVIGATION_THROTTLE_MS): Boolean {
        val now = SystemClock.elapsedRealtime()
        if (now - lastNavigationAt < minIntervalMs) {
            return false
        }
        lastNavigationAt = now
        return true
    }

    private fun shouldStartSwipe(root: View, ev: MotionEvent): Boolean {
        val width = root.width
        if (width <= 0 || ev.pointerCount != 1) {
            return false
        }
        return ev.x <= width * SWIPE_START_RATIO
    }

    private fun cancelChildTouch(ev: MotionEvent) {
        val cancelEvent = MotionEvent.obtain(ev)
        cancelEvent.action = MotionEvent.ACTION_CANCEL
        super.dispatchTouchEvent(cancelEvent)
        cancelEvent.recycle()
    }

    private fun animateBackCommit(root: View) {
        swipeCommitted = true
        root.removeCallbacks(resetRunnable)
        val target = root.width.toFloat().coerceAtLeast(1f)
        root.animate()
            .translationX(target)
            .setDuration(SWIPE_ANIM_DURATION_MS)
            .setInterpolator(DecelerateInterpolator())
            .withEndAction {
                onBackPressedDispatcher.onBackPressed()
                overridePendingTransition(0, 0)
            }
            .start()
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

    private fun resetSwipeState() {
        swipeActive = false
        swipeIntercepting = false
        swipeLastDx = 0f
    }

    companion object {
        private const val RESET_DELAY_MS = 350L
        private const val SWIPE_START_RATIO = 0.65f
        private const val SWIPE_COMMIT_RATIO = 0.35f
        private const val SWIPE_ANIM_DURATION_MS = 200L
        private const val NAVIGATION_THROTTLE_MS = 600L
    }
}
