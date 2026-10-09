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
import org.rigorcore.caserecomp.sda.SdaCampaign
import org.rigorcore.caserecomp.sda.SdaCampaignPhase
import org.rigorcore.caserecomp.sda.SdaClickResult
import org.rigorcore.caserecomp.sda.SdaClock
import org.rigorcore.caserecomp.sda.SdaScene
import org.rigorcore.caserecomp.sda.SdaTileRotGame
import org.rigorcore.caserecomp.sda.SdaTileSwapGame

/**
 * Android View that renders SDA Scenes, Investigation Map, Bonus Minigames, and Campaign Finale.
 * Maps touch events from physical display resolution to logical 800x600 coordinates.
 */
class SdaGameView(
    context: Context,
    var scene: SdaScene,
    var clock: SdaClock? = null,
    var backgroundBitmap: Bitmap? = null,
    var spriteBitmaps: Map<String, Bitmap> = emptyMap(),
    var campaign: SdaCampaign? = null,
) : View(context) {

    private val bgPaint = Paint().apply { color = 0xFF0D1117.toInt() }
    private val sidebarPaint = Paint().apply { color = 0xFF161B22.toInt() }
    private val dividerPaint = Paint().apply { color = 0xFF30363D.toInt(); strokeWidth = 2f }
    private val cardPaint = Paint().apply { color = 0xFF21262D.toInt() }
    private val cardBorderPaint = Paint().apply { color = 0xFFE8B84A.toInt(); style = Paint.Style.STROKE; strokeWidth = 2f }
    private val buttonPaint = Paint().apply { color = 0xFFE8B84A.toInt() }
    private val buttonTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK
        textSize = 15f
        typeface = Typeface.DEFAULT_BOLD
    }
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
    private val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFE8B84A.toInt()
        textSize = 26f
        typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    }
    private val subPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFAAB4C4.toInt() // MUTED
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
    }
    private val placeholderPaint = Paint().apply { color = 0xFF5588CC.toInt() }
    private val animPlaceholderPaint = Paint().apply { color = 0xFFE8B84A.toInt() }
    private val overlayPaint = Paint().apply { color = 0xCC000000.toInt() }

    var onObjectFoundListener: ((String, Int) -> Unit)? = null
    var onMissListener: ((Boolean) -> Unit)? = null
    var onSceneCompleteListener: (() -> Unit)? = null
    var onSceneSelectedListener: ((String) -> Unit)? = null
    var onReturnToMapListener: (() -> Unit)? = null
    var onStartBonusListener: (() -> Unit)? = null
    var onNextLevelListener: (() -> Unit)? = null
    var onCampaignCompletedListener: (() -> Unit)? = null

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

        val camp = campaign
        if (camp != null) {
            when (camp.phase) {
                SdaCampaignPhase.MAP -> drawMap(canvas, camp)
                SdaCampaignPhase.SCENE, SdaCampaignPhase.SCENE_COMPLETE -> drawScene(canvas, camp)
                SdaCampaignPhase.OBJECTS_COMPLETE -> {
                    drawScene(canvas, camp)
                    drawObjectsCompleteOverlay(canvas, camp)
                }
                SdaCampaignPhase.BONUS -> drawBonus(canvas, camp)
                SdaCampaignPhase.LEVEL_COMPLETE -> drawLevelComplete(canvas, camp)
                SdaCampaignPhase.FINALE_1, SdaCampaignPhase.FINALE_2, SdaCampaignPhase.FINALE_3 -> drawFinale(canvas, camp)
                SdaCampaignPhase.CAMPAIGN_COMPLETE -> drawCampaignComplete(canvas, camp)
                SdaCampaignPhase.TIMEOUT -> drawTimeout(canvas, camp)
            }
        } else {
            // Standalone scene mode
            drawScene(canvas, null)
        }

        canvas.restore()
    }

    private fun drawScene(canvas: Canvas, camp: SdaCampaign?) {
        // Draw background bitmap or placeholder canvas
        if (backgroundBitmap != null) {
            canvas.drawBitmap(backgroundBitmap!!, 0f, 0f, null)
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
        val currentPoints = camp?.points ?: scene.score.points
        canvas.drawText("$currentPoints", 12f, 50f, hudPaint)

        canvas.drawText("TIEMPO", 12f, 78f, subPaint)
        val activeClock = camp?.clock ?: clock
        val timeText = activeClock?.text() ?: "22:00"
        canvas.drawText(timeText, 12f, 100f, hudPaint)

        if (camp != null) {
            canvas.drawText("NIVEL ${camp.levelIndex + 1} (${camp.remainingObjects} obj)", 12f, 126f, subPaint)
        }

        // Target caption list
        val remaining = scene.remainingCaptions()
        canvas.drawText("OBJETIVOS (${remaining.size})", 12f, 150f, subPaint)
        var textY = 172f
        for (caption in remaining.take(13)) {
            val displayCaption = if (caption.length > 17) caption.take(15) + "…" else caption
            canvas.drawText("• $displayCaption", 10f, textY, textPaint)
            textY += 21f
        }

        // Return to Map button (at bottom of sidebar)
        if (camp != null) {
            canvas.drawRect(10f, 550f, 132f, 586f, cardPaint)
            canvas.drawRect(10f, 550f, 132f, 586f, cardBorderPaint)
            canvas.drawText("MAPA", 48f, 573f, textPaint)
        }
    }

    private fun drawMap(canvas: Canvas, camp: SdaCampaign) {
        val mapBgPaint = Paint().apply { color = 0xFF141923.toInt() }
        canvas.drawRect(0f, 0f, 800f, 600f, mapBgPaint)

        // Title and Header
        canvas.drawText("MAPA DE INVESTIGACIÓN", 36f, 48f, titlePaint)
        canvas.drawText("${camp.currentLevel.title} · Pista #${camp.currentLevel.clue}", 36f, 76f, subPaint)

        // Stats Bar
        canvas.drawText("PUNTOS: ${camp.points}", 450f, 48f, hudPaint)
        canvas.drawText("TIEMPO: ${camp.clock.text()}", 650f, 48f, hudPaint)
        canvas.drawText("RANGO: ${camp.rank}", 450f, 76f, subPaint)
        canvas.drawText("OBJETIVOS: ${camp.remainingObjects} pendientes", 650f, 76f, subPaint)

        canvas.drawLine(36f, 96f, 764f, 96f, dividerPaint)

        // Available Scene Cards
        val scenes = camp.currentLevel.scenes
        var startY = 130f
        for (scName in scenes) {
            val cardRect = Rect(60, startY.toInt(), 740, (startY + 70f).toInt())
            canvas.drawRect(cardRect, cardPaint)
            canvas.drawRect(cardRect, cardBorderPaint)

            val displayName = scName.replaceFirstChar { it.uppercase() }
            canvas.drawText("Lugar: $displayName", 80f, startY + 32f, hudPaint)
            canvas.drawText("Toca para investigar esta escena", 80f, startY + 54f, subPaint)

            canvas.drawRect(610f, startY + 16f, 720f, startY + 54f, buttonPaint)
            canvas.drawText("INVESTIGAR", 624f, startY + 40f, buttonTextPaint)

            startY += 86f
        }
    }

    private fun drawObjectsCompleteOverlay(canvas: Canvas, camp: SdaCampaign) {
        canvas.drawRect(142f, 0f, 800f, 600f, overlayPaint)
        val dlgRect = Rect(220, 200, 720, 420)
        canvas.drawRect(dlgRect, cardPaint)
        canvas.drawRect(dlgRect, cardBorderPaint)

        canvas.drawText("¡OBJETIVOS COMPLETADOS!", 250f, 250f, titlePaint)
        canvas.drawText("Has reunido todas las pruebas necesarias para esta pista.", 250f, 285f, textPaint)
        canvas.drawText("Prepárate para resolver el minijuego de bonificación.", 250f, 315f, subPaint)

        canvas.drawRect(350f, 345f, 590f, 395f, buttonPaint)
        canvas.drawText("INICIAR MINIJUEGO", 380f, 376f, buttonTextPaint)
    }

    private fun drawBonus(canvas: Canvas, camp: SdaCampaign) {
        val bonus = camp.bonusGame
        canvas.drawRect(0f, 0f, 800f, 600f, bgPaint)

        // Banner
        canvas.drawText("MINIJUEGO DE PISTA ADICIONAL (+25,000 PTS)", 40f, 45f, titlePaint)
        canvas.drawText("Tiempo restante: ${camp.clock.text()} · Tipo: ${bonus?.kind ?: "Puzle"}", 40f, 75f, subPaint)

        if (bonus is SdaTileRotGame) {
            // Draw 4x6 tile rotation grid
            val bx = 172f
            val by = 95f
            val bw = 612f
            val bh = 408f
            val tw = bw / bonus.cols
            val th = bh / bonus.rows

            for (r in 0 until bonus.rows) {
                for (c in 0 until bonus.cols) {
                    val idx = r * bonus.cols + c
                    val rot = bonus.tileRotations[idx]
                    val rx = bx + c * tw
                    val ry = by + r * th

                    val tilePaint = Paint().apply { color = if (rot == 0) 0xFF2D3748.toInt() else 0xFF1A202C.toInt() }
                    canvas.drawRect(rx + 2, ry + 2, rx + tw - 2, ry + th - 2, tilePaint)
                    canvas.drawRect(rx + 2, ry + 2, rx + tw - 2, ry + th - 2, cardBorderPaint)

                    val angleText = "${rot * 90}°"
                    canvas.drawText(angleText, rx + tw / 2 - 14, ry + th / 2 + 6, textPaint)
                }
            }
        } else if (bonus is SdaTileSwapGame) {
            // Draw 6x6 tile swap grid
            val bx = 172f
            val by = 96f
            val bw = 612f
            val bh = 408f
            val tw = bw / bonus.cols
            val th = bh / bonus.rows

            for (r in 0 until bonus.rows) {
                for (c in 0 until bonus.cols) {
                    val idx = r * bonus.cols + c
                    val tileVal = bonus.tilePositions[idx]
                    val isSel = (bonus.selectedIndex == idx)
                    val rx = bx + c * tw
                    val ry = by + r * th

                    val tilePaint = Paint().apply { color = if (isSel) 0xFF744210.toInt() else 0xFF21262D.toInt() }
                    canvas.drawRect(rx + 2, ry + 2, rx + tw - 2, ry + th - 2, tilePaint)
                    canvas.drawRect(rx + 2, ry + 2, rx + tw - 2, ry + th - 2, cardBorderPaint)
                    canvas.drawText("#$tileVal", rx + tw / 2 - 12, ry + th / 2 + 6, textPaint)
                }
            }
        } else {
            // Generic bonus display
            canvas.drawRect(172f, 95f, 784f, 503f, cardPaint)
            canvas.drawRect(172f, 95f, 784f, 503f, cardBorderPaint)
            canvas.drawText("Puzle interactivo en progreso...", 320f, 300f, textPaint)
        }

        // Solve puzzle button
        canvas.drawRect(580f, 530f, 770f, 575f, buttonPaint)
        canvas.drawText("RESOLVER PUZLE", 605f, 558f, buttonTextPaint)
    }

    private fun drawLevelComplete(canvas: Canvas, camp: SdaCampaign) {
        canvas.drawRect(0f, 0f, 800f, 600f, bgPaint)
        val dlgRect = Rect(120, 80, 680, 520)
        canvas.drawRect(dlgRect, cardPaint)
        canvas.drawRect(dlgRect, cardBorderPaint)

        val summary = camp.levelSummary()
        canvas.drawText("¡NIVEL COMPLETADO!", 220f, 140f, titlePaint)
        canvas.drawText("Pista encontrada: Clave #${summary.clue} verificada", 160f, 190f, hudPaint)

        canvas.drawText("Puntos acumulados: ${summary.points}", 160f, 240f, textPaint)
        canvas.drawText("Tiempo restante: ${summary.remainingTime.toInt()} segundos", 160f, 280f, textPaint)
        canvas.drawText("Bonificación de velocidad: +${summary.speedBonus} pts", 160f, 320f, hudPaint)
        canvas.drawText("Rango actual: ${summary.rank}", 160f, 360f, hudPaint)

        val nextLabel = if (summary.isLastLevel) "IR AL DESENLACE FINAL" else "CONTINUAR AL SIGUIENTE NIVEL"
        canvas.drawRect(220f, 430f, 580f, 485f, buttonPaint)
        canvas.drawText(nextLabel, 245f, 464f, buttonTextPaint)
    }

    private fun drawFinale(canvas: Canvas, camp: SdaCampaign) {
        canvas.drawRect(0f, 0f, 800f, 600f, bgPaint)
        val stageNum = when (camp.phase) {
            SdaCampaignPhase.FINALE_1 -> 1
            SdaCampaignPhase.FINALE_2 -> 2
            else -> 3
        }
        canvas.drawText("DESENLACE FINAL — FASE $stageNum DE 3", 40f, 50f, titlePaint)
        canvas.drawText("El atraco maestro en la bóveda principal de Las Vegas", 40f, 80f, subPaint)

        val box = Rect(100, 110, 700, 480)
        canvas.drawRect(box, cardPaint)
        canvas.drawRect(box, cardBorderPaint)

        when (stageNum) {
            1 -> {
                canvas.drawText("FASE 1: Identificación del sospechoso y llaves maestras", 130f, 160f, hudPaint)
                canvas.drawText("Toca los mecanismos para validar las evidencias recogidas.", 130f, 200f, textPaint)
            }
            2 -> {
                canvas.drawText("FASE 2: Desactivación de sistemas de seguridad de la bóveda", 130f, 160f, hudPaint)
                canvas.drawText("Desactiva los relés y la alimentación auxiliar.", 130f, 200f, textPaint)
            }
            3 -> {
                canvas.drawText("FASE 3: Apertura de la cerradura electromecánica", 130f, 160f, hudPaint)
                canvas.drawText("Introduce la combinación y acciona el mecanismo principal.", 130f, 200f, textPaint)
            }
        }

        canvas.drawRect(520f, 520f, 700f, 565f, buttonPaint)
        canvas.drawText("COMPLETAR PASO", 535f, 548f, buttonTextPaint)
    }

    private fun drawCampaignComplete(canvas: Canvas, camp: SdaCampaign) {
        canvas.drawRect(0f, 0f, 800f, 600f, bgPaint)
        val box = Rect(100, 60, 700, 540)
        canvas.drawRect(box, cardPaint)
        canvas.drawRect(box, cardBorderPaint)

        canvas.drawText("¡CASO RESUELTO!", 240f, 140f, titlePaint)
        canvas.drawText("Mystery P.I.: The Vegas Heist 100% Completado", 160f, 190f, hudPaint)

        canvas.drawText("¡Has recuperado los millones robados y resuelto todos los crímenes!", 140f, 250f, textPaint)
        canvas.drawText("Rango final alcanzado: P.I. Maestro", 140f, 300f, hudPaint)
        canvas.drawText("Puntuación final del detective: ${camp.points} puntos", 140f, 350f, hudPaint)
        canvas.drawText("Tiempo total investigado: ${camp.totalElapsed.toInt()} segundos", 140f, 400f, textPaint)

        canvas.drawRect(260f, 450f, 540f, 505f, buttonPaint)
        canvas.drawText("VOLVER AL MENÚ", 330f, 484f, buttonTextPaint)
    }

    private fun drawTimeout(canvas: Canvas, camp: SdaCampaign) {
        canvas.drawRect(0f, 0f, 800f, 600f, bgPaint)
        canvas.drawText("TIEMPO AGOTADO", 260f, 250f, titlePaint)
        canvas.drawText("El tiempo para resolver esta pista ha finalizado.", 240f, 300f, textPaint)

        canvas.drawRect(300f, 380f, 500f, 430f, buttonPaint)
        canvas.drawText("REINTENTAR", 350f, 410f, buttonTextPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN) {
            val logicalX = ((event.x - offsetX) / scale).toInt()
            val logicalY = ((event.y - offsetY) / scale).toInt()

            val camp = campaign
            if (camp != null) {
                when (camp.phase) {
                    SdaCampaignPhase.MAP -> {
                        // Check click on scene cards
                        var startY = 130
                        for (scName in camp.currentLevel.scenes) {
                            if (logicalX in 60..740 && logicalY in startY..(startY + 70)) {
                                onSceneSelectedListener?.invoke(scName)
                                invalidate()
                                return true
                            }
                            startY += 86
                        }
                    }
                    SdaCampaignPhase.SCENE, SdaCampaignPhase.SCENE_COMPLETE -> {
                        // Check Return to Map button (10..132, 550..586)
                        if (logicalX in 10..132 && logicalY in 550..586) {
                            onReturnToMapListener?.invoke()
                            invalidate()
                            return true
                        }
                        // Click scene objects
                        val result = camp.clickScene(logicalX, logicalY)
                        when (result) {
                            is SdaClickResult.Found -> {
                                onObjectFoundListener?.invoke(result.id, result.gain)
                                if (camp.phase == SdaCampaignPhase.SCENE_COMPLETE || camp.phase == SdaCampaignPhase.OBJECTS_COMPLETE) {
                                    onSceneCompleteListener?.invoke()
                                }
                            }
                            is SdaClickResult.Miss -> onMissListener?.invoke(result.penalty)
                            is SdaClickResult.Outside -> {}
                        }
                        invalidate()
                        return true
                    }
                    SdaCampaignPhase.OBJECTS_COMPLETE -> {
                        // Click to start bonus
                        if (logicalX in 350..590 && logicalY in 345..395) {
                            onStartBonusListener?.invoke()
                            invalidate()
                            return true
                        }
                    }
                    SdaCampaignPhase.BONUS -> {
                        // Solve button (580..770, 530..575)
                        if (logicalX in 580..770 && logicalY in 530..575) {
                            camp.solveBonus()
                            invalidate()
                            return true
                        }
                        camp.clickBonus(logicalX, logicalY)
                        invalidate()
                        return true
                    }
                    SdaCampaignPhase.LEVEL_COMPLETE -> {
                        // Confirm next level button (220..580, 430..485)
                        if (logicalX in 220..580 && logicalY in 430..485) {
                            onNextLevelListener?.invoke()
                            invalidate()
                            return true
                        }
                    }
                    SdaCampaignPhase.FINALE_1, SdaCampaignPhase.FINALE_2, SdaCampaignPhase.FINALE_3 -> {
                        // Solve button (520..700, 520..565)
                        if (logicalX in 520..700 && logicalY in 520..565) {
                            camp.solveBonus()
                            invalidate()
                            return true
                        }
                        camp.clickBonus(logicalX, logicalY)
                        invalidate()
                        return true
                    }
                    SdaCampaignPhase.CAMPAIGN_COMPLETE -> {
                        if (logicalX in 260..540 && logicalY in 450..505) {
                            onCampaignCompletedListener?.invoke()
                            invalidate()
                            return true
                        }
                    }
                    SdaCampaignPhase.TIMEOUT -> {
                        if (logicalX in 300..500 && logicalY in 380..430) {
                            camp.clock.reset()
                            camp.phase = SdaCampaignPhase.MAP
                            invalidate()
                            return true
                        }
                    }
                }
            } else {
                // Standalone scene mode
                val result = scene.click(logicalX, logicalY)
                when (result) {
                    is SdaClickResult.Found -> {
                        onObjectFoundListener?.invoke(result.id, result.gain)
                        if (scene.batchRetired) {
                            onSceneCompleteListener?.invoke()
                        }
                    }
                    is SdaClickResult.Miss -> onMissListener?.invoke(result.penalty)
                    is SdaClickResult.Outside -> {}
                }
                invalidate()
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    fun step(seconds: Float) {
        val camp = campaign
        if (camp != null) {
            camp.advance(seconds)
            if (camp.phase == SdaCampaignPhase.SCENE_COMPLETE || camp.phase == SdaCampaignPhase.OBJECTS_COMPLETE) {
                onSceneCompleteListener?.invoke()
            }
        } else {
            scene.advance(seconds)
            clock?.advance(seconds)
            if (scene.batchRetired) {
                onSceneCompleteListener?.invoke()
            }
        }
        postInvalidateOnAnimation()
    }
}
