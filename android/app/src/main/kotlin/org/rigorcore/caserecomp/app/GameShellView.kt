package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.MotionEvent
import android.view.View
import org.rigorcore.caserecomp.GameRuntime
import org.rigorcore.caserecomp.Input
import org.rigorcore.caserecomp.Screen

class GameShellView(context: Context, private val runtime: GameRuntime) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF10141A.toInt())
        val model = runtime.renderFrame()
        paint.textSize = 30f
        paint.color = 0xFFF3F5F7.toInt()
        canvas.drawText("Case-Recomp synthetic shell", 28f, 42f, paint)
        canvas.drawText("screen=${model.screen} frame=${model.frame}", 28f, 78f, paint)
        when (model.screen) {
            Screen.MENU -> canvas.drawText("Tap to start", 28f, 130f, paint)
            Screen.MAP -> canvas.drawText("Tap to enter synthetic scene", 28f, 130f, paint)
            Screen.COMPLETE -> canvas.drawText("Synthetic scenario complete", 28f, 130f, paint)
            Screen.SCENE -> {
                val scale = minOf(width / 640f, height / 360f)
                val originX = (width - 640f * scale) / 2f
                val originY = (height - 360f * scale) / 2f
                for (target in model.targets) {
                    paint.style = Paint.Style.FILL
                    paint.color = if (target.found) 0xFF36454F.toInt() else 0xFFCAD6E2.toInt()
                    val r = target.bounds
                    canvas.drawRect(
                        originX + r.left * scale, originY + r.top * scale,
                        originX + r.right * scale, originY + r.bottom * scale, paint,
                    )
                    paint.color = 0xFF10141A.toInt()
                    paint.textSize = 20f
                    canvas.drawText(target.id, originX + r.left * scale + 8f, originY + r.top * scale + 28f, paint)
                }
                paint.color = 0xFFF3F5F7.toInt()
                paint.textSize = 22f
                canvas.drawText("found ${model.foundCount}/${model.totalTargets}", 28f, height - 28f, paint)
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val input = when (runtime.session.screen) {
            Screen.MENU -> Input.Start
            Screen.MAP -> Input.EnterScene("synthetic-room")
            Screen.SCENE -> runtime.inputForTap(event.x, event.y, width.toFloat(), height.toFloat())
            Screen.COMPLETE -> Input.Reset
        }
        if (input != null) {
            runtime.dispatch(input)
            invalidate()
        }
        return true
    }
}
