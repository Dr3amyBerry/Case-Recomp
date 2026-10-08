package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import org.rigorcore.caserecomp.GameRuntime
import org.rigorcore.caserecomp.Input
import org.rigorcore.caserecomp.Screen

class GameShellView(
    context: Context,
    private val runtime: GameRuntime,
    private val privateContent: LoadedPrivateContent? = null,
    private val bitmapLoader: BitmapAssetLoader = NoopBitmapAssetLoader,
) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(0xFF10141A.toInt())
        val model = runtime.renderFrame()
        val scale = minOf(width / model.designWidth.toFloat(), height / model.designHeight.toFloat())
        val originX = (width - model.designWidth * scale) / 2f
        val originY = (height - model.designHeight * scale) / 2f

        val sceneId = model.sceneId
        if (model.screen == Screen.SCENE && sceneId != null) {
            privateContent?.backgroundAsset(sceneId)?.let(bitmapLoader::load)?.let { bitmap ->
                paint.alpha = 255
                canvas.drawBitmap(bitmap, null, RectF(originX, originY, originX + model.designWidth * scale, originY + model.designHeight * scale), paint)
            }
        }

        paint.textSize = 30f
        paint.color = 0xFFF3F5F7.toInt()
        canvas.drawText(if (privateContent == null) "Case-Recomp synthetic shell" else "Case-Recomp private local content", 28f, 42f, paint)
        canvas.drawText("screen=${model.screen} frame=${model.frame}", 28f, 78f, paint)
        when (model.screen) {
            Screen.MENU -> {
                canvas.drawText("Tap to start", 28f, 130f, paint)
                canvas.drawText(if (privateContent == null) "Long press to import private .crcontent" else "Long press to import verified .crflow / .crscene", 28f, 170f, paint)
            }
            Screen.MAP -> canvas.drawText("Tap to enter scene", 28f, 130f, paint)
            Screen.COMPLETE -> canvas.drawText("Scenario complete", 28f, 130f, paint)
            Screen.SCENE -> {
                for (target in model.targets.sortedBy { it.z }) {
                    val r = target.bounds
                    val rect = RectF(originX + r.left * scale, originY + r.top * scale,
                        originX + r.right * scale, originY + r.bottom * scale)
                    val assetId = model.sceneId?.let { privateContent?.targetAsset(it, target.id) }
                    val bitmap = assetId?.let(bitmapLoader::load)
                    if (bitmap != null) {
                        paint.alpha = if (target.found) 96 else 255
                        canvas.drawBitmap(bitmap, null, rect, paint)
                        paint.alpha = 255
                    } else {
                        paint.style = Paint.Style.FILL
                        paint.color = if (target.found) 0xFF36454F.toInt() else 0xFFCAD6E2.toInt()
                        canvas.drawRect(rect, paint)
                        paint.color = 0xFF10141A.toInt()
                        paint.textSize = 20f
                        canvas.drawText(target.id, rect.left + 8f, rect.top + 28f, paint)
                    }
                }
                paint.color = 0xFFF3F5F7.toInt()
                paint.textSize = 22f
                canvas.drawText("found ${model.foundCount}/${model.totalTargets}", 28f, height - 28f, paint)
                model.acknowledgeRegion?.takeIf { model.awaitingAcknowledge }?.let { r ->
                    paint.color = 0xCC10141A.toInt()
                    canvas.drawRect(originX, originY, originX + model.designWidth * scale, originY + model.designHeight * scale, paint)
                    paint.color = 0xFF4CAF50.toInt()
                    canvas.drawRect(RectF(originX + r.left * scale, originY + r.top * scale,
                        originX + r.right * scale, originY + r.bottom * scale), paint)
                    paint.color = 0xFFF3F5F7.toInt()
                    paint.textSize = 26f
                    canvas.drawText("Scene complete - tap the button to return to the map", 28f, 130f, paint)
                }
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_UP) return true
        val input = when (runtime.session.screen) {
            Screen.MENU -> Input.Start
            Screen.MAP -> Input.EnterScene(runtime.firstSceneId())
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
