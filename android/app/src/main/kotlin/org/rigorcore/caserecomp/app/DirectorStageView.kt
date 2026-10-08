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
    private val bitmap = Bitmap.createBitmap(renderer.frame.width, renderer.frame.height, Bitmap.Config.ARGB_8888)
    // Bilinear: a non-integer fit (e.g. 1.2x) otherwise doubles some pixel rows and columns but not others.
    private val drawPaint = Paint().apply { isFilterBitmap = true }
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
                    afterFrame?.invoke()
                    redraw()
                    logStats(start)
                    // Late frames are not made up in a burst: resync when more than one frame behind.
                    nextFrameAt = maxOf(nextFrameAt + intervalMillis, start - intervalMillis)
                    postDelayed(this, (nextFrameAt - android.os.SystemClock.uptimeMillis()).coerceAtLeast(0L))
                } else {
                    active = false
                    onQuit?.invoke()
                }
            } catch (e: Exception) {
                active = false
                onRuntimeError?.invoke(e)
            }
        }
    }
    var onRuntimeError: ((Exception) -> Unit)? = null
    /** Lingo `quit`/`halt`: the title asked to close. */
    var onQuit: (() -> Unit)? = null
    var afterFrame: (() -> Unit)? = null

    /** Debug builds with the tag at DEBUG log ticks per second and the time spent per tick. */
    private fun logStats(start: Long) {
        if (!android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) return
        statsFrames++
        statsWork += android.os.SystemClock.uptimeMillis() - start
        if (statsSince == 0L) statsSince = start
        if (start - statsSince >= 5000) {
            android.util.Log.d(TAG, "fps %.1f, tick %.1f ms, draw %.1f ms (compose %.1f ms)".format(
                statsFrames * 1000f / (start - statsSince), statsWork.toFloat() / statsFrames, drawWork.toFloat() / statsFrames.coerceAtLeast(1),
                composeWork.toFloat() / statsFrames.coerceAtLeast(1)))
            statsSince = start; statsFrames = 0; statsWork = 0; drawWork = 0; composeWork = 0
        }
    }
    private var drawWork = 0L
    private var composeWork = 0L

    init {
        isFocusable = true
        isFocusableInTouchMode = true
        // Android 8 tints a focused view with the theme's focus highlight; the stage keeps its own colours.
        if (android.os.Build.VERSION.SDK_INT >= 26) defaultFocusHighlightEnabled = false
        contentDescription = "Director game stage"
    }

    fun resumeFrames() {
        if (active || runtime.quitRequested) return
        active = true
        redraw()
        nextFrameAt = android.os.SystemClock.uptimeMillis() + intervalMillis
        postDelayed(advance, intervalMillis)
    }

    fun pauseFrames() {
        active = false
        removeCallbacks(advance)
    }

    private var stageDirty = true

    /** The VM state changed: compose a new stage frame on the next draw. */
    private fun redraw() {
        stageDirty = true
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.BLACK)
        val drawStart = android.os.SystemClock.uptimeMillis()
        // Compose only after the VM advanced or took input; overlay-only redraws reuse the frame.
        if (stageDirty) {
            stageDirty = false
            val frame = renderer.render()
            composeWork += android.os.SystemClock.uptimeMillis() - drawStart
            bitmap.setPixels(frame.pixels, 0, frame.width, 0, 0, frame.width, frame.height)
        }
        val area = viewport.fit(width, height)
        destination.set(area.left, area.top, area.left + area.width, area.top + area.height)
        canvas.drawBitmap(bitmap, null, destination, drawPaint)
        drawTouchMark(canvas)
        drawWork += android.os.SystemClock.uptimeMillis() - drawStart
    }

    /** Where the last touch landed (view pixels) and when: a ring that grows and fades out. */
    private var markX = 0f
    private var markY = 0f
    private var markAt = 0L
    private val markPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }

    private fun markTouch(x: Float, y: Float) {
        markX = x; markY = y; markAt = android.os.SystemClock.uptimeMillis()
    }

    private fun drawTouchMark(canvas: Canvas) {
        if (markAt == 0L) return
        val t = (android.os.SystemClock.uptimeMillis() - markAt) / TOUCH_MARK_MILLIS.toFloat()
        if (t >= 1f) { markAt = 0L; return }
        val density = resources.displayMetrics.density
        val radius = (10f + 14f * t) * density
        val alpha = ((1f - t) * 255).toInt()
        // A dark halo under a light ring stays visible on bright and dark scenes alike.
        markPaint.strokeWidth = 4f * density
        markPaint.color = Color.argb(alpha / 2, 0, 0, 0)
        canvas.drawCircle(markX, markY, radius, markPaint)
        markPaint.strokeWidth = 2f * density
        markPaint.color = Color.argb(alpha, 255, 255, 255)
        canvas.drawCircle(markX, markY, radius, markPaint)
        postInvalidateOnAnimation()
    }

    /**
     * A finger has no hover: titles show rollover feedback (mouseEnter) before the click acts.
     * A touch therefore first moves the pointer there, and delivers mouseDown a moment later
     * (or on an earlier release); after the release the pointer leaves the stage again.
     */
    private var pendingDown: Pair<Int, Int>? = null
    private val deliverDown = Runnable { pendingDown?.let { (x, y) -> input { pressAt(x, y) } } }
    private val liftPointer = Runnable { input { runtime.mouseMove(-1, -1) } }

    private fun pressAt(x: Int, y: Int) {
        pendingDown = null
        if (android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) {
            android.util.Log.d(TAG, "down $x,$y -> sprite ${runtime.activeSpriteAt(x, y)?.number}")
        }
        runtime.mouseDown(x, y)
    }

    /** Runs input against the VM, then redraws and honours a quit. */
    private inline fun input(action: () -> Unit): Boolean {
        try {
            action()
        } catch (e: Exception) {
            onRuntimeError?.invoke(e)
            return true
        }
        redraw()
        checkQuit()
        return true
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val point = viewport.stagePoint(event.x, event.y, width, height)
        val mouse = event.getToolType(0) == MotionEvent.TOOL_TYPE_MOUSE
        return when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (point == null) return false
                down = true
                requestFocus()
                removeCallbacks(liftPointer)
                if (!mouse) markTouch(event.x, event.y)
                input {
                    if (mouse) pressAt(point.first, point.second)
                    else {
                        runtime.mouseMove(point.first, point.second)
                        pendingDown = point
                        postDelayed(deliverDown, TOUCH_ROLLOVER_MILLIS)
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> input { point?.let { runtime.mouseMove(it.first, it.second) } }
            MotionEvent.ACTION_UP -> {
                if (!down) return true
                down = false
                removeCallbacks(deliverDown)
                val handled = input {
                    pendingDown?.let { (x, y) -> pressAt(x, y) }
                    // An out-of-stage release still reaches mouseUpOutside through hit-testing.
                    runtime.mouseUp(point?.first ?: -1, point?.second ?: -1)
                }
                performClick()
                if (!mouse) postDelayed(liftPointer, TOUCH_LIFT_MILLIS)
                handled
            }
            MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(deliverDown)
                val pressed = down && pendingDown == null
                pendingDown = null
                down = false
                postDelayed(liftPointer, TOUCH_LIFT_MILLIS)
                input { if (pressed) runtime.mouseUp(-1, -1) }
            }
            else -> false
        }
    }

    /** A connected mouse hovers: rollovers follow it as on the original desktop. */
    override fun onHoverEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_HOVER_ENTER, MotionEvent.ACTION_HOVER_MOVE -> {
                removeCallbacks(liftPointer)
                val point = viewport.stagePoint(event.x, event.y, width, height)
                input { runtime.mouseMove(point?.first ?: -1, point?.second ?: -1) }
            }
            // Also sent when a button is pressed; the following down cancels the lift.
            MotionEvent.ACTION_HOVER_EXIT -> postDelayed(liftPointer, TOUCH_LIFT_MILLIS)
            else -> return super.onHoverEvent(event)
        }
        return true
    }

    private fun checkQuit() {
        if (runtime.quitRequested) { active = false; onQuit?.invoke() }
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
                redraw()
                return true
            }
            override fun deleteSurroundingText(beforeLength: Int, afterLength: Int): Boolean {
                if (beforeLength > 0) sendCharacter(BACKSPACE)
                redraw()
                return true
            }
            override fun performEditorAction(actionCode: Int): Boolean {
                sendCharacter(RETURN)
                redraw()
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

    /** Debug aid for on-device QA (tag at DEBUG): F12 logs the on-stage display list. */
    private fun logDisplayList() {
        for (d in runtime.displayList()) {
            if (d.right <= 0 || d.bottom <= 0 || d.left >= renderer.width || d.top >= renderer.height) continue
            // First point (2 px grid) where a click reaches this sprite, if any.
            var hit = ""
            val sprite = runtime.sprite(d.sprite)
            if (sprite.instances.isNotEmpty()) {
                var tries = 0
                search@ for (y in maxOf(0, d.top) until minOf(renderer.height, d.bottom) step 3) {
                    for (x in maxOf(0, d.left) until minOf(renderer.width, d.right) step 3) {
                        // Cheap own-pixel test first; only then ask which sprite is on top.
                        if (!sprite.hit(x, y)) continue
                        if (runtime.activeSpriteAt(x, y)?.number == d.sprite) { hit = " hit $x,$y"; break@search }
                        if (++tries > 200) break@search
                    }
                }
            }
            android.util.Log.d(TAG, "sprite ${d.sprite} ${d.member.name} [${d.member.type}] ${d.left},${d.top},${d.right},${d.bottom} ink${d.ink} blend${d.blend}$hit")
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_F12 && android.util.Log.isLoggable(TAG, android.util.Log.DEBUG)) {
            logDisplayList()
            return true
        }
        val key = when (keyCode) {
            KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> RETURN
            KeyEvent.KEYCODE_DEL -> BACKSPACE
            KeyEvent.KEYCODE_ESCAPE -> ESCAPE
            else -> event.unicodeChar.takeIf { it > 0 }?.toChar()?.toString() ?: return super.onKeyDown(keyCode, event)
        }
        sendCharacter(key)
        redraw()
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean = true

    fun release() {
        pauseFrames()
        removeCallbacks(deliverDown)
        removeCallbacks(liftPointer)
        bitmap.recycle()
    }

    private companion object {
        const val TAG = "CaseRecompDirector"
        /** How long a touch shows its rollover before mouseDown (about three frames). */
        const val TOUCH_ROLLOVER_MILLIS = 100L
        /** How long the pointer lingers after a release before it leaves the stage. */
        const val TOUCH_LIFT_MILLIS = 150L
        /** Lifetime of the ring drawn where a touch lands. */
        const val TOUCH_MARK_MILLIS = 450L
        // Director's `the key` is the character itself: RETURN, BACKSPACE and ESC are control characters.
        const val RETURN = "\r"
        const val BACKSPACE = "\b"
        const val ESCAPE = "\u001b"
    }
}
