package org.rigorcore.caserecomp.fontlab

import android.app.Instrumentation
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import java.io.File

/** Loads private fonts and renders comparison evidence without opening an activity. */
class FontLabProbe : Instrumentation() {
    override fun onCreate(arguments: Bundle?) { super.onCreate(arguments); start() }
    override fun onStart() {
        val result = Bundle()
        try {
            val bitmap = Bitmap.createBitmap(1200, 760, Bitmap.Config.ARGB_8888)
            val canvas = Canvas(bitmap)
            canvas.drawColor(Color.rgb(244, 236, 218))
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val samples = listOf("Agente: Dream", "Caso N\u00b0 2: Dinero F\u00e1cil", "L\u00edmite de tiempo: 15 Minutos", "\u00c1\u00c9\u00cd\u00d3\u00da \u00e1\u00e9\u00ed\u00f3\u00fa \u00d1\u00f1\u00fc \u00a1\u00bf 0123456789")
            for ((index, italic) in listOf(false, true).withIndex()) {
                val recovered = Typeface.createFromAsset(targetContext.assets,
                    if (italic) "tekton-italic-recovered.otf" else "tekton-recovered.otf")
                val baseline = Typeface.create("sans-serif", if (italic) Typeface.ITALIC else Typeface.NORMAL)
                paint.typeface = Typeface.DEFAULT
                paint.textSize = 24f
                paint.textScaleX = 1f
                paint.color = Color.BLACK
                canvas.drawText(if (italic) "TEKTON ITALICA: ACTUAL / RECUPERADA" else "TEKTON: ACTUAL / RECUPERADA", 24f, 32f + index * 370f, paint)
                for ((row, sample) in samples.withIndex()) {
                    val y = 85f + index * 370f + row * 75f
                    for ((candidate, face) in listOf(baseline, recovered).withIndex()) {
                        val yy = y + candidate * 32f
                        paint.color = Color.rgb(197, 151, 144)
                        canvas.drawLine(15f, yy, 1180f, yy, paint)
                        paint.color = Color.rgb(32, 40, 75)
                        paint.typeface = face
                        paint.textSize = 32f * if (candidate == 1) 1f else if (italic) 0.68f else 0.8f
                        paint.textScaleX = if (candidate == 1) 1f else if (italic) 0.95f else 1.15f
                        check(paint.measureText(sample) > 0f) { "zero sample advance" }
                        if (candidate == 1) {
                            for (char in sample.filterNot { it.isWhitespace() })
                                check(paint.hasGlyph(char.toString())) { "missing glyph: $char" }
                        }
                        canvas.drawText(sample, 24f, yy, paint)
                    }
                }
            }
            File(targetContext.filesDir, "comparison-android.png").outputStream().use {
                check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, it))
            }
            bitmap.recycle()
            result.putString("result", "PASS: fonts loaded, sample characters supported, comparison drawn")
            finish(0, result)
        } catch (error: Throwable) {
            result.putString("error", error.toString())
            finish(-1, result)
        }
    }
}
