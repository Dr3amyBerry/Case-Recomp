package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.inputmethod.BaseInputConnection
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import org.rigorcore.caserecomp.director.DirectorRuntime
import org.rigorcore.caserecomp.director.StageRenderer

/**
 * Debug-only Director stage, intentionally separate from the existing GameShellView.
 * One UI thread owns the VM, input dispatch and software compositor.
 */
internal class DirectorStageView(
    context: Context,
    private val runtime: DirectorRuntime,
    private val renderer: StageRenderer,
) : View(context) {
    private val viewport = DirectorViewport(renderer.width, renderer.height)
    private val bitmap = Bitmap.createBitmap(renderer.width, renderer.height, Bitmap.Config.ARGB_8888)
    private val drawPaint = Paint().apply { isFilterBitmap = false }
    private val destination = RectF()
    private var active = false
    private var down = false
    private val intervalMillis = (1000L / runtime.movie.tempo.coerceIn(1, 60)).coerceAtLeast(16L)
    /** Fixed-rate schedule: the next frame is due one interval after the previous one was due. */
    private var nextFrameAt = 0L
    private var statsSince = 0L
    private var statsFrames = 0
    private var statsWork = 0L
    private val advance = object : Runnable {
        override fun run() {
            if (!active) return
            try {
                if (!runtime.quitRequested) {
                    val start = android.os.SystemClock.uptimeMillis()
                    runtime.tick()
                    invalidate()
                    logStats(start)
                    // Late frames are not made up in a burst: resync when more than one frame behind.
                    nextFrameAt = maxOf(nextFrameAt + intervalMillis, start - intervalMillis)
                    postDelayed(this, (nextFrameAt - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0L))
                } else active = false
            } catch (e: Exception) {
                active = false
                onRuntimeError?.invoke(e)
            }
        }
    }
    var onRuntimeError: ((Exception) -> Unit)? = null

    /** Debug builds with the tag at DEBUG log ticks per second and the time spent per tick. */
    private fun logStats(start: Long) {
        if (!android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) return
        statsFrames++
        statsWork += android.os.SystemClock.uptimeMillis() - start
        if (statsSince == 0L) statsSince = start
        if (start - statsSince >= 5000) {
            android.util.Log.d(TAG, "fps %.1f, tick %.1f ms, draw %.1f ms".format(
                statsFrames * 1000f / (start - statsSince), statsWork.toFloat() / statsFrames, drawWork.toFloat() / statsFrames.coerceAtLeast(1)))
            statsSince = start; statsFrames = 0; statsWork = 0; drawWork = 0
        }
    }
    private var drawWork = 0L

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        contentDescription = "Director game stage"
    }

    fun resumeFrames() {
        if (active || runtime.quitRequested) return
        active = true
        invalidate()
        nextFrameAt = android.os.SystemClock.uptimeMillis() + intervalMillis
        postDelayed(advance, intervalMillis)
    }

    fun pauseFrames() {
        active = false
        removeCallbacks(advance)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val drawStart = android.os.SystemClock.uptimeMillis()
        val frame = renderer.render()
        bitmap.setPixels(frame.pixels, 0, frame.width, 0, 0, frame.width, frame.height)
        val area = viewport.fit(width, height)
        destination.set(area.left, area.top, area.left + area.width, area.top + area.height)
        canvas.drawBitmap(bitmap, null, destination, drawPaint)
        drawWork += android.os.SystemClock.uptimeMillis() - drawStart
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val point = viewport.stagePoint(event.x, event.y, width, height)
        try {
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    if (point == null) return false
                    down = true
                    requestFocus()
                    if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) {
                        android.util.Log.d(TAG, "down ${point.first},${point.second} -> sprite ${runtime.activeSpriteAt(point.first, point.second)?.number}")
                    }
                    runtime.mouseDown(point.first, point.second)
                }
                MotionEvent.ACTION_MOVE -> point?.let { runtime.mouseMove(it.first, it.second) }
                MotionEvent.ACTION_UP -> {
                    if (down) {
                        // An out-of-stage release still reaches mouseUpOutside through hit-testing.
                        runtime.mouseUp(point?.first ?: -1, point?.second ?: -1)
                        down = false
                        performClick()
                    }
                }
                MotionEvent.ACTION_CANCEL -> {
                    if (down) { runtime.mouseUp(-1, -1); down = false }
                }
                else -> return false
            }
        } catch (e: Exception) {
            onRuntimeError?.invoke(e)
            return true
        }
        invalidate()
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }

    fun showKeyboard() {
        requestFocus()
        val manager = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        manager.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)
    }

    override fun onCheckIsTextEditor(): Boolean = true

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection {
        outAttrs.inputType = android.text.InputType.TYPE_CLASS_TEXT
        outAttrs.imeOptions = EditorInfo.IME_ACTION_DONE or EditorInfo.IME_FLAG_NO_EXTRACT_UI
        return object : BaseInputConnection(this, false) {
            override fun commitText(text: CharSequence?, newCursorPosition: Int): Boolean {
                // IMEs may commit a newline instead of an editor action.
                text?.forEach { sendCharacter(if (it == '\n') RETURN else it.toString()) }
                invalidate()
                return true
            }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) sendCharacter(BACKSPACE)
                invalidate()
                return true
            }
            override fun performEditorAction(actionCode: Int): Boolean {
                sendCharacter(RETURN)
                invalidate()
                return true
            }
        }
    }

    private fun sendCharacter(key: String) {
        try {
            runtime.keyDown(key)
            runtime.keyUp(key)
        } catch (e: Exception) {
            onRuntimeError?.invoke(e)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        val key = when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> RETURN
            KeyEvent.KEYCODE_DEL -> BACKSPACE
            KeyEvent.KEYCODE_ESCAPE -> ESCAPE
            else -> event.unicodeChar.takeIf { it > 0 }?.toChar()?.toString() ?: return super.onKeyDown(keyCode, event)
        }
        sendCharacter(key)
        invalidate()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = true

    fun release() {
        pauseFrames()
        bitmap.recycle()
    }

    private companion object {
        const val TAG = "CaseRecompDirector"
        // Director's `the key` is the character itself: RETURN, BACKSPACE and ESC are control characters.
        const val RETURN = "\r"
        const val BACKSPACE = "\b"
        const val ESCAPE = "\u001b"
    }
}
