package com.muse.feature.pet.presentation

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Point
import android.graphics.Rect
import android.os.Build
import android.view.View
import android.view.WindowManager

/**
 * 悬浮图标：在给定窗口内按「目标边长（px）」居中绘制 emoji。
 *
 * 窗口尺寸与图标尺寸解耦 —— 窗口要保留最小触控面积（见 PetOverlayService），
 * 图标只按用户设定的 dp 绘制，所以 1dp 就是真的 1dp。
 *
 * 不用 TextView：其排版受字体 ascent/descent 影响，emoji 字形常超出字号边界被裁切；
 * 且 textSize 以 sp 为单位会被系统字体缩放二次放大。这里量出字形真实外框再等比缩放。
 */
class PetIconView(context: Context) : View(context) {

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.LEFT
        isSubpixelText = true
        setShadowLayer(SHADOW_RADIUS, 0f, SHADOW_DY, SHADOW_COLOR)
    }
    private val bounds = Rect()

    /** 要绘制的字符。 */
    var glyph: String = DEFAULT_GLYPH
        set(value) {
            field = value
            invalidate()
        }

    /** 图标目标边长（px）；0 表示按窗口短边算。 */
    var iconSizePx: Int = 0
        set(value) {
            field = value
            invalidate()
        }

    init {
        setLayerType(LAYER_TYPE_SOFTWARE, null)
    }

    override fun onDraw(canvas: Canvas) {
        val side = if (iconSizePx > 0) iconSizePx.toFloat() else minOf(width, height).toFloat()
        if (side <= 0f || glyph.isEmpty()) return

        // 先量字形真实外框（以字号 100 为基准，缩放比例与字号线性相关）
        paint.textSize = 100f
        paint.getTextBounds(glyph, 0, glyph.length, bounds)
        val gw = bounds.width().toFloat()
        val gh = bounds.height().toFloat()
        if (gw <= 0f || gh <= 0f) return

        // 等比放大到刚好填满目标边长，留一点余量避免贴边
        paint.textSize = 100f * minOf(side / gw, side / gh) * FILL_RATIO
        paint.getTextBounds(glyph, 0, glyph.length, bounds)

        // 让字形外框中心与窗口中心重合
        val dx = width / 2f - (bounds.left + bounds.width() / 2f)
        val dy = height / 2f - (bounds.top + bounds.height() / 2f)
        canvas.drawText(glyph, dx, dy, paint)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        invalidate()
    }

    companion object {
        const val DEFAULT_GLYPH = "🐮"
        private const val FILL_RATIO = 0.96f
        private const val SHADOW_RADIUS = 3f
        private const val SHADOW_DY = 1.5f
        private const val SHADOW_COLOR = 0x55000000

        /** 屏幕真实尺寸（含系统栏）。displayMetrics 不含系统栏，会让落点算偏。 */
        fun screenSize(context: Context): Pair<Int, Int> {
            val wm = context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager
                ?: return 0 to 0
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val b = wm.currentWindowMetrics.bounds
                return b.width() to b.height()
            }
            val p = Point()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealSize(p)
            return p.x to p.y
        }
    }
}
