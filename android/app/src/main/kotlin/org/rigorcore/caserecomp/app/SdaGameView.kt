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

    private val bgPaint = Paint().apply { color = 0xFF10151E.toInt() }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 18f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val hudPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8B84A.toInt() // GOLD
        textSize = 22f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAAB4C4.toInt() // MUTED
        textSize = 15f
    }

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
            val panelPaint = Paint().apply { color = 0xFF1E2838.toInt() }
            canvas.drawRect(0f, 0f, 800f, 600f, panelPaint)
        }

        // Draw active un-found sprites
        for (identity in scene.targets) {
            val sprite = scene.objects[identity] ?: continue
            if (!sprite.found && !sprite.hidden) {
                val bmp = spriteBitmaps[identity]
                if (bmp != null) {
                    canvas.drawBitmap(bmp, sprite.x.toFloat(), sprite.y.toFloat(), null)
                } else {
                    // Visual placeholder if bitmap not provided
                    val p = Paint().apply { color = 0xFF5588CC.toInt() }
                    canvas.drawRect(
                        sprite.x.toFloat(), sprite.y.toFloat(),
                        (sprite.x + sprite.image.width).toFloat(),
                        (sprite.y + sprite.image.height).toFloat(), p
                    )
                }
            }
        }

        // Draw found sprites during animation
        for (identity in scene.foundOrder) {
            val sprite = scene.objects[identity] ?: continue
            val motion = sprite.motion ?: continue
            if (!motion.removed) {
                val bmp = spriteBitmaps[identity]
                val dstRect = Rect(motion.x, motion.y, motion.x + motion.drawWidth, motion.y + motion.drawHeight)
                if (bmp != null) {
                    canvas.drawBitmap(bmp, null, dstRect, null)
                } else {
                    val p = Paint().apply { color = 0xFFE8B84A.toInt() }
                    canvas.drawRect(dstRect, p)
                }
            }
        }

        // Draw HUD: Score and Clock
        canvas.drawText("Puntos: ${scene.score.points}", 24f, 36f, hudPaint)
        if (clock != null) {
            canvas.drawText("Tiempo: ${clock.text()}", 620f, 36f, hudPaint)
        }

        // Draw target caption list on panel
        val remaining = scene.remainingCaptions()
        var textY = 120f
        canvas.drawText("Objetivos (${remaining.size}):", 24f, 90f, subPaint)
        for (caption in remaining.take(8)) {
            canvas.drawText("• $caption", 24f, textY, textPaint)
            textY += 28f
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
        postInvalidate()
    }
}
