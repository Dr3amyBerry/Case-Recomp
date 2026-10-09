package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.Typeface
import android.view.MotionEvent
import android.view.View
import org.rigorcore.caserecomp.sda.SdaClickResult
import org.rigorcore.caserecomp.sda.SdaClock
import org.rigorcore.caserecomp.sda.SdaScene

/**
 * Android View that renders an SDA Scene and handles touch input.
 * Maps touch events from physical display resolution to logical 800x600 scene coordinates.
 */
class SdaGameView(
    context: Context,
    val scene: SdaScene,
    val clock: SdaClock? = null,
    val backgroundBitmap: Bitmap? = null,
    val spriteBitmaps: Map<String, Bitmap> = emptyMap(),
) : View(context) {

    private val bgPaint = Paint().apply { color = 0xFF0D1117.toInt() }
    private val sidebarPaint = Paint().apply { color = 0xFF161B22.toInt() }
    private val dividerPaint = Paint().apply { color = 0xFF30363D.toInt(); strokeWidth = 2f }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 13f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8B84A.toInt() // GOLD
        textSize = 20f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAAB4C4.toInt() // MUTED
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val placeholderPaint = Paint().apply { color = 0xFF5588CC.toInt() }
    private val animPlaceholderPaint = Paint().apply { color = 0xFFE8B84A.toInt() }

    var onObjectFoundListener: ((String, Int) -> Unit)? = null
    var onMissListener: ((Boolean) -> Unit)? = null
    var onSceneCompleteListener: (() -> Unit)? = null

    private var scale = 1.0f
    private var offsetX = 0.0f
    private var offsetY = 0.0f

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), bgPaint)

        scale = minOf(width / 800f, height / 600f)
        offsetX = (width - 800f * scale) / 2f
        offsetY = (height - 600f * scale) / 2f

        canvas.save()
        canvas.translate(offsetX, offsetY)
        canvas.scale(scale, scale)

        // Draw background bitmap or placeholder canvas
        if (backgroundBitmap != null) {
            canvas.drawBitmap(backgroundBitmap, 0f, 0f, null)
        } else {
            val panelPaint = Paint().apply { color = 0xFF10151E.toInt() }
            canvas.drawRect(0f, 0f, 800f, 600f, panelPaint)
        }

        // 1. Draw drawOrder (static backdrop layers, overlays, and unfound target sprites)
        val renderList = if (scene.drawOrder.isNotEmpty()) scene.drawOrder else scene.objects.values.toList()
        for (sprite in renderList) {
            if (!sprite.found && !sprite.hidden) {
                val bmp = spriteBitmaps[sprite.identity] ?: (sprite.image.nativeImage as? Bitmap)
                if (bmp != null) {
                    canvas.drawBitmap(bmp, sprite.x.toFloat(), sprite.y.toFloat(), null)
                } else if (sprite.identity.isNotEmpty()) {
                    // Fallback rectangle for synthetic tests without Bitmaps
                    canvas.drawRect(
                        sprite.x.toFloat(), sprite.y.toFloat(),
                        (sprite.x + sprite.image.width).toFloat(),
                        (sprite.y + sprite.image.height).toFloat(), placeholderPaint
                    )
                }
            }
        }

        // 2. Draw found sprites during flight/scaling animation
        for (identity in scene.foundOrder) {
            val sprite = scene.objects[identity] ?: continue
            val motion = sprite.motion ?: continue
            if (!motion.removed) {
                val bmp = spriteBitmaps[identity] ?: (sprite.image.nativeImage as? Bitmap)
                val dstRect = Rect(motion.x, motion.y, motion.x + motion.drawWidth, motion.y + motion.drawHeight)
                if (bmp != null) {
                    canvas.drawBitmap(bmp, null, dstRect, null)
                } else {
                    canvas.drawRect(dstRect, animPlaceholderPaint)
                }
            }
        }

        // 3. Draw sidebar / HUD at x: 0..142
        canvas.drawRect(0f, 0f, 142f, 600f, sidebarPaint)
        canvas.drawLine(142f, 0f, 142f, 600f, dividerPaint)

        // HUD: Score and Clock
        canvas.drawText("PUNTOS", 12f, 28f, subPaint)
        canvas.drawText("${scene.score.points}", 12f, 50f, hudPaint)

        canvas.drawText("TIEMPO", 12f, 78f, subPaint)
        val timeText = clock?.text() ?: "22:00"
        canvas.drawText(timeText, 12f, 100f, hudPaint)

        // Target caption list
        val remaining = scene.remainingCaptions()
        canvas.drawText("OBJETIVOS (${remaining.size})", 12f, 136f, subPaint)
        var textY = 160f
        for (caption in remaining.take(15)) {
            val displayCaption = if (caption.length > 17) caption.take(15) + "…" else caption
            canvas.drawText("• $displayCaption", 10f, textY, textPaint)
            textY += 22f
        }

        canvas.restore()
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val logicalX = ((event.x - offsetX) / scale).toInt()
            val logicalY = ((event.y - offsetY) / scale).toInt()

            val result = scene.click(logicalX, logicalY)
            when (result) {
                is SdaClickResult.Found -> {
                    onObjectFoundListener?.invoke(result.id, result.gain)
                    if (scene.batchRetired) {
                        onSceneCompleteListener?.invoke()
                    }
                }
                is SdaClickResult.Miss -> {
                    onMissListener?.invoke(result.penalty)
                }
                is SdaClickResult.Outside -> {}
            }
            invalidate()
            return true
        }
        return super.onTouchEvent(event)
    }

    fun step(seconds: Float) {
        scene.advance(seconds)
        clock?.advance(seconds)
        if (scene.batchRetired) {
            onSceneCompleteListener?.invoke()
        }
        postInvalidateOnAnimation()
    }
}
