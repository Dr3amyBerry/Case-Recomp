package org.rigorcore.caserecomp.app

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import org.rigorcore.caserecomp.director.DirectorRuntime
import org.rigorcore.caserecomp.director.Sprite
import org.rigorcore.caserecomp.lingo.LingoValue
import org.rigorcore.caserecomp.lingo.LingoValue.*
import org.rigorcore.caserecomp.lingo.isTruthy
import org.rigorcore.caserecomp.lingo.toInt

/** Applied only after the verified Huntsville profile has been selected. */
internal class HuntsvilleOptions(private val runtime: DirectorRuntime, private val support: () -> Unit) {
    private fun LInstance.property(name: String): LingoValue? = properties.entries.firstOrNull { it.key.equals(name, true) }?.value
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun afterFrame() {
        val manager = runtime.vm.global("gSoundManager") as? LInstance ?: return
        for ((property, method) in listOf("m_musicVolume" to "setMusicVolume", "m_effectsVolume" to "setEffectsVolume")) {
            val raw = manager.property(property)?.toInt() ?: continue
            if (raw !in 0..255) setVolume(manager, method, raw.coerceIn(0, 255))
        }
        for (number in listOf(269, 270)) {
            val slider = runtime.sprite(number)
            val behavior = slider.instances.firstOrNull { it.script.name.equals("options slider bar script", true) } ?: continue
            val base = behavior.property("m_sliderSpriteBase") as? Sprite ?: continue
            val display = behavior.property("m_displaySprite") as? Sprite ?: continue
            val music = (behavior.property("m_type") as? LSymbol)?.name.equals("musicVolume", true)
            val property = if (music) "m_musicVolume" else "m_effectsVolume"
            val method = if (music) "setMusicVolume" else "setEffectsVolume"
            if (behavior.property("m_boolMouseDown")?.isTruthy() == true) {
                // The original computes volume before constraining the thumb. Use its constrained position.
                val volume = HuntsvilleVolume.fromSlider(slider.bounds()[0], base.bounds()[0], base.width, slider.width)
                if (manager.property(property)?.toInt() != volume) setVolume(manager, method, volume)
            }
            val volume = manager.property(property)?.toInt() ?: continue
            if (behavior.property("m_boolMouseDown")?.isTruthy() != true && base.bounds()[0] in 0 until runtime.movie.stageWidth) {
                val left = HuntsvilleVolume.thumbLeft(base.bounds()[0], base.width, slider.width, volume)
                slider.setProp("locH", LInt(slider.locH + left - slider.bounds()[0]))
                val offset = behavior.property("m_displaySpriteOffset") as? LPoint
                if (offset != null) display.setProp("loc", LPoint(LInt(slider.locH + offset.h.toInt()), LInt(slider.locV + offset.v.toInt())))
            }
            display.member?.let { member ->
                val readout = HuntsvilleVolume.readout(volume)
                if (member.text != readout) member.text = readout
            }
        }
    }

    private fun setVolume(manager: LInstance, method: String, volume: Int) {
        runtime.vm.callMethod(manager, method, listOf(manager, LInt(volume)))
    }

    private fun supportBounds(): RectF? {
        val button = runtime.sprite(263)
        if (!button.visible || button.instances.none { it.script.name.equals("fullscreen button script", true) }) return null
        val bounds = button.bounds()
        if (bounds[0] !in 0 until runtime.movie.stageWidth || bounds[1] !in 0 until runtime.movie.stageHeight) return null
        val game = runtime.vm.global("gGameManager") as? LInstance ?: return null
        val dialog = game.properties.values.filterIsInstance<LInstance>().firstOrNull { it.script.name.equals("dialog_options", true) }
        if (dialog?.property("m_boolHelpDisplayed")?.isTruthy() == true) return null
        val check = runtime.sprite(266).bounds()
        return RectF(minOf(bounds[0], check[0]).toFloat(), minOf(bounds[1], check[1]).toFloat(),
            maxOf(bounds[2], check[2]).toFloat(), maxOf(bounds[3], check[3]).toFloat())
    }

    fun actionAt(x: Int, y: Int): (() -> Unit)? = if (supportBounds()?.contains(x.toFloat(), y.toFloat()) == true) support else null

    fun draw(canvas: Canvas) {
        val rect = supportBounds() ?: return
        paint.style = Paint.Style.FILL
        paint.color = Color.rgb(48, 62, 57)
        canvas.drawRoundRect(rect, 5f, 5f, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 2f
        paint.color = Color.rgb(169, 158, 122)
        canvas.drawRoundRect(rect, 5f, 5f, paint)
        paint.style = Paint.Style.FILL
        paint.typeface = android.graphics.Typeface.create("sans-serif", android.graphics.Typeface.BOLD)
        paint.textSize = 12f
        paint.color = Color.rgb(240, 232, 205)
        val label = "Apoya al desarrollador"
        if (paint.measureText(label) > rect.width() - 8) paint.textSize *= (rect.width()-8)/paint.measureText(label)
        val baseline = rect.centerY() - (paint.fontMetrics.ascent + paint.fontMetrics.descent)/2
        canvas.drawText(label, rect.centerX()-paint.measureText(label)/2, baseline, paint)
    }

    companion object { const val SUPPORT_URL = "https://www.paypal.com/paypalme/dreamypay" }
}

internal object HuntsvilleVolume {
    fun fromSlider(left: Int, baseLeft: Int, baseWidth: Int, thumbWidth: Int): Int {
        val travel = baseWidth - thumbWidth
        if (travel <= 0) return 0
        return (((left.toLong() - baseLeft).coerceIn(0, travel.toLong()) * 255) / travel).toInt()
    }
    fun thumbLeft(baseLeft: Int, baseWidth: Int, thumbWidth: Int, volume: Int): Int =
        baseLeft + ((maxOf(0, baseWidth - thumbWidth).toLong() * volume.coerceIn(0, 255) + 127) / 255).toInt()
    fun readout(volume: Int): String = minOf(99, volume.coerceIn(0, 255) * 100 / 255).toString().padStart(2, '0')
}
