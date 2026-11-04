package com.muse.feature.pet.presentation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.core.app.NotificationCompat
import com.muse.MainActivity
import com.muse.R
import com.muse.core.util.FileLogger
import com.muse.feature.pet.data.PetOverlaySettingsRepository
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import javax.inject.Inject
import kotlin.math.abs

/**
 * 悬浮窗：在其他应用上层显示一个可拖动、可缩放的 🐮 图标。
 *
 * 关键设计：**窗口尺寸 ≠ 图标尺寸**。
 * 窗口至少保留 44dp 触控面积（Android 触控下限），图标作为子视图按用户设定的 dp 居中绘制，
 * 所以把尺寸调到 1dp 时图标真的只有 1dp，但依然点得到、拖得动。
 *
 * 不用 ComposeView：Service 窗口缺少 ViewTreeLifecycleOwner，attach 时会抛
 * IllegalStateException 导致崩溃。
 */
@AndroidEntryPoint
class PetOverlayService : Service() {

    @Inject
    lateinit var settings: PetOverlaySettingsRepository

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private lateinit var windowManager: WindowManager
    private var rootView: View? = null
    private var iconView: PetIconView? = null
    private var params: WindowManager.LayoutParams? = null

    /** 当前图标尺寸（dp），唯一真源。 */
    private var sizeDp = PetOverlaySettingsRepository.DEFAULT_SIZE_DP

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        startForeground(NOTIFICATION_ID, buildNotification())

        // 尺寸要先读出来，才能一次算对窗口大小与落点（避免先按默认值显示再跳一下）。
        sizeDp = runBlocking { settings.sizeDpFlow.first() }
        attachOverlay()
        observeSettings()
        FileLogger.i(TAG, "悬浮窗服务启动，图标 ${sizeDp}dp")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        scope.launch {
            if (!settings.enabledFlow.first()) stopSelf()
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        rootView?.let { runCatching { windowManager.removeView(it) } }
        rootView = null
        iconView = null
        params = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // ── 悬浮窗 ───────────────────────────────────────────

    private fun attachOverlay() {
        if (rootView != null) return

        val saved = runBlocking { settings.positionFlow.first() }
        val windowPx = windowSizePx(sizeDp)
        val screen = PetIconView.screenSize(this)

        val lp = WindowManager.LayoutParams(
            windowPx,
            windowPx,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                @Suppress("DEPRECATION")
                WindowManager.LayoutParams.TYPE_PHONE
            },
            // 不加 FLAG_LAYOUT_NO_LIMITS：那会允许窗口越界，图标能被拖出屏幕找不回来。
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            windowAnimations = 0 // 关掉系统入场过渡，出现时直接定格
        }

        // 落点必须在 addView 之前算好，否则会先闪现在 (0,0) 再跳过去。
        if (saved != null && screen.first > 0) {
            lp.x = (saved.first * maxX(screen.first, windowPx)).toInt()
                .coerceIn(0, maxX(screen.first, windowPx))
            lp.y = (saved.second * maxY(screen.second, windowPx)).toInt()
                .coerceIn(0, maxY(screen.second, windowPx))
        } else {
            // 首次显示：右侧偏下，靠近输入区又不挡正文。
            lp.x = (0.9f * maxX(screen.first, windowPx)).toInt().coerceAtLeast(0)
            lp.y = (0.72f * maxY(screen.second, windowPx)).toInt().coerceAtLeast(0)
        }
        params = lp

        val iconPx = dpToPx(sizeDp)
        val icon = PetIconView(this).apply {
            glyph = ICON
            iconSizePx = iconPx
        }
        val container = FrameLayout(this).apply {
            // 窗口负责触控面积，图标只按设定尺寸绘制。
            addView(icon, FrameLayout.LayoutParams(iconPx, iconPx, Gravity.CENTER))
        }
        iconView = icon
        attachGestureHandler(container)

        runCatching { windowManager.addView(container, lp) }
            .onFailure {
                FileLogger.w(TAG, "添加悬浮窗失败（未授予悬浮权限）", it)
                stopSelf()
                return
            }
        rootView = container
    }

    /** 窗口边长：至少 44dp（触控下限），图标更大时跟随图标。 */
    private fun windowSizePx(sizeDp: Float): Int =
        maxOf(dpToPx(sizeDp), dpToPx(MIN_TOUCH_DP))

    private fun maxX(screenW: Int, winPx: Int) = (screenW - winPx).coerceAtLeast(0)
    private fun maxY(screenH: Int, winPx: Int) = (screenH - winPx).coerceAtLeast(0)

    /** 按归一化坐标摆位并夹取到屏幕内。 */
    private fun applyPosition() {
        val lp = params ?: return
        val view = rootView ?: return
        val screen = PetIconView.screenSize(this)
        lp.x = lp.x.coerceIn(0, maxX(screen.first, lp.width))
        lp.y = lp.y.coerceIn(0, maxY(screen.second, lp.height))
        runCatching { windowManager.updateViewLayout(view, lp) }
    }

    private fun currentNormalized(): Pair<Float, Float> {
        val lp = params ?: return 0f to 0f
        val screen = PetIconView.screenSize(this)
        val mx = maxX(screen.first, lp.width)
        val my = maxY(screen.second, lp.height)
        return (if (mx > 0) lp.x.toFloat() / mx else 0f).coerceIn(0f, 1f) to
            (if (my > 0) lp.y.toFloat() / my else 0f).coerceIn(0f, 1f)
    }

    /** 拖动移动；松手写回位置并吸附边缘；轻点打开 App。 */
    private fun attachGestureHandler(view: View) {
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        var startX = 0
        var startY = 0
        var downX = 0f
        var downY = 0f
        var dragging = false

        view.setOnTouchListener { _, event ->
            val lp = params ?: return@setOnTouchListener false
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    startX = lp.x
                    startY = lp.y
                    downX = event.rawX
                    downY = event.rawY
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX
                    val dy = event.rawY - downY
                    if (!dragging && (abs(dx) > slop || abs(dy) > slop)) dragging = true
                    if (dragging) {
                        val screen = PetIconView.screenSize(this)
                        lp.x = (startX + dx.toInt()).coerceIn(0, maxX(screen.first, lp.width))
                        lp.y = (startY + dy.toInt()).coerceIn(0, maxY(screen.second, lp.height))
                        runCatching { windowManager.updateViewLayout(view, lp) }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (dragging) {
                        snapToEdge()
                        val (nx, ny) = currentNormalized()
                        scope.launch { settings.setPosition(nx, ny) }
                    } else {
                        openApp()
                    }
                    true
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (dragging) {
                        snapToEdge()
                        val (nx, ny) = currentNormalized()
                        scope.launch { settings.setPosition(nx, ny) }
                    }
                    true
                }
                else -> false
            }
        }
    }

    /** 松手后若靠近左右边缘则贴边。 */
    private fun snapToEdge() {
        val lp = params ?: return
        val screen = PetIconView.screenSize(this)
        val maxX = maxX(screen.first, lp.width)
        val threshold = (screen.first * 0.15f).toInt()
        lp.x = when {
            lp.x < threshold -> 0
            lp.x > maxX - threshold -> maxX
            else -> lp.x
        }
        applyPosition()
    }

    private fun openApp() {
        runCatching {
            startActivity(
                Intent(this, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            )
        }
    }

    // ── 设置联动 ─────────────────────────────────────────

    private fun observeSettings() {
        scope.launch {
            settings.sizeDpFlow.collectLatest { dp ->
                if (dp == sizeDp && rootView != null) return@collectLatest
                sizeDp = dp
                val lp = params ?: return@collectLatest
                val icon = iconView ?: return@collectLatest
                val windowPx = windowSizePx(dp)
                lp.width = windowPx
                lp.height = windowPx
                icon.iconSizePx = dpToPx(dp)
                (icon.layoutParams as? FrameLayout.LayoutParams)?.let {
                    val iconPx = dpToPx(dp)
                    it.width = iconPx
                    it.height = iconPx
                    icon.layoutParams = it
                }
                applyPosition()
            }
        }
        scope.launch {
            settings.enabledFlow.collectLatest { enabled ->
                if (!enabled) stopSelf()
            }
        }
    }

    // ── 通知 ─────────────────────────────────────────────

    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.pet_overlay_channel_name),
                NotificationManager.IMPORTANCE_MIN
            ).apply { description = getString(R.string.pet_overlay_channel_desc) }
            (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager)
                .createNotificationChannel(channel)
        }
        val pending = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_thinking)
            .setContentTitle(getString(R.string.pet_overlay_notification_title))
            .setContentIntent(pending)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .build()
    }

    private fun dpToPx(dp: Float): Int =
        (dp * resources.displayMetrics.density).toInt().coerceAtLeast(1)

    companion object {
        private const val TAG = "PetOverlay"
        private const val CHANNEL_ID = "pet_overlay"
        private const val NOTIFICATION_ID = 4201
        private const val ICON = PetIconView.DEFAULT_GLYPH

        /** Android 触控面积下限（dp）。 */
        private const val MIN_TOUCH_DP = 44f

        /** 启动悬浮窗（需已有悬浮权限）。 */
        fun start(context: Context) {
            if (!Settings.canDrawOverlays(context)) return
            val intent = Intent(context, PetOverlayService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, PetOverlayService::class.java)) }
        }
    }
}
