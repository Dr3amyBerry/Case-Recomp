package org.rigorcore.caserecomp.fontlab

import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView

/** Separate glyph comparison: no game bundle, save access or production renderer. */
class FontLabActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val model = SampleState(
            Typeface.createFromAsset(assets, "tekton-recovered.otf"),
            Typeface.createFromAsset(assets, "tekton-italic-recovered.otf"),
        )
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 18, 24, 18)
        }
        fun label(text: String) = TextView(this).apply { this.text = text; textSize = 16f }
            .also { column.addView(it) }
        label("Tekton: laboratorio experimental")
        label("No modifica Huntsville. Fuente extraída del original; conversión aún sin aprobar. Hay glifos deformados. Sin hinting ni kerning originales verificados.")
        val input = EditText(this).apply {
            setText("Agente: Dream\nCaso N° 2: Dinero Fácil\nLímite de tiempo: 15 Minutos\nÁÉÍÓÚ áéíóú Ññü ¡¿ 0123456789")
            minLines = 4
        }
        model.text = input.text.toString()
        column.addView(input)
        val sizeLabel = label("Tamaño nominal: 24 px")
        val size = SeekBar(this).apply { max = 42; progress = 14 }
        column.addView(size)
        val italic = CheckBox(this).apply { text = "Tekton itálica" }
        column.addView(italic)
        label("Arriba: sans-serif Android con escalas de Tekton del perfil aprobado (revisión 6). Abajo: contornos PFR1 recuperados a tamaño nominal. Línea roja = baseline de cada trazo. No reproduce el ajuste de cajas del juego.")
        val preview = GlyphComparison(this, model)
        column.addView(preview, LinearLayout.LayoutParams(-1, 820))
        input.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                model.text = s.toString().take(1024)
                preview.invalidate()
            }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        italic.setOnCheckedChangeListener { _, checked -> model.italic = checked; preview.invalidate() }
        size.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, progress: Int, fromUser: Boolean) {
                model.size = (progress + 10).toFloat()
                sizeLabel.text = "Tamaño nominal: ${progress + 10} px"
                preview.invalidate()
            }
            override fun onStartTrackingTouch(bar: SeekBar?) = Unit
            override fun onStopTrackingTouch(bar: SeekBar?) = Unit
        })
        setContentView(ScrollView(this).apply { addView(column) })
    }
}

internal class SampleState(val regular: Typeface, val slanted: Typeface) {
    var text = ""
    var size = 24f
    var italic = false
}

internal class GlyphComparison(context: Context, private val sample: SampleState) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        canvas.drawColor(Color.rgb(244, 236, 218))
        val lines = sample.text.lines().take(6)
        val pitch = 64f
        for ((index, line) in lines.withIndex()) {
            val baseline = 48f + index * pitch * 2
            for (candidate in listOf(false, true)) {
                val y = baseline + if (candidate) pitch / 2 else 0f
                paint.color = Color.rgb(197, 151, 144)
                paint.strokeWidth = 1f
                canvas.drawLine(0f, y, width.toFloat(), y, paint)
                paint.color = Color.rgb(32, 40, 75)
                paint.textSkewX = 0f
                paint.textSize = sample.size * if (candidate) 1f else if (sample.italic) 0.68f else 0.8f
                paint.textScaleX = if (candidate) 1f else if (sample.italic) 0.95f else 1.15f
                paint.typeface = if (candidate) {
                    if (sample.italic) sample.slanted else sample.regular
                } else Typeface.create("sans-serif", if (sample.italic) Typeface.ITALIC else Typeface.NORMAL)
                canvas.drawText(line, 8f, y, paint)
            }
        }
    }
}
