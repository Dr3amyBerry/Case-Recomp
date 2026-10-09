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
import org.rigorcore.caserecomp.sda.SdaFirstRiddleGame
import org.rigorcore.caserecomp.sda.SdaCaptionRuns
import org.rigorcore.caserecomp.sda.SdaCampaignPhase
import org.rigorcore.caserecomp.sda.SdaClickResult
import org.rigorcore.caserecomp.sda.SdaClock
import org.rigorcore.caserecomp.sda.SdaScene
import org.rigorcore.caserecomp.sda.SdaTileRotGame
import org.rigorcore.caserecomp.sda.SdaTileSwapGame
import org.rigorcore.caserecomp.sda.SdaWordSearchGame
import org.rigorcore.caserecomp.sda.SdaJigsawGame
import org.rigorcore.caserecomp.sda.SdaArgbPixelSource
import org.rigorcore.caserecomp.sda.SdaPixelSource

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
    var visuals: SdaVisualProfile? = null,
) : View(context) {

    private val photoPaint=Paint(Paint.FILTER_BITMAP_FLAG)
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

    var isPaused: Boolean = false
        private set
    var onPauseChangedListener: (() -> Unit)? = null
    private var resumePointerId: Int? = null

    var onBonusInputListener: (() -> Unit)? = null
    private var wordPointerId: Int? = null
    private val wordLetterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; textSize = 24f; textAlign = Paint.Align.CENTER
    }
    private var jigsawRasterOwner: SdaJigsawGame? = null
    private val jigsawBitmaps = java.util.IdentityHashMap<SdaPixelSource, Bitmap>()
    private fun jigsawBitmap(source: SdaPixelSource): Bitmap {
        (source.nativeImage as? Bitmap)?.let { return it }
        return jigsawBitmaps.getOrPut(source) {
            val pixels = if (source is SdaArgbPixelSource) source.copyPixels() else
                IntArray(source.width * source.height) { source.getArgb(it % source.width, it / source.width) }
            Bitmap.createBitmap(pixels, source.width, source.height, Bitmap.Config.ARGB_8888)
        }
    }
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

        if(isPaused) visuals?.drawPause(canvas)
        canvas.restore()
    }

    private fun drawScene(canvas: Canvas, camp: SdaCampaign?) {
        // Draw background bitmap or placeholder canvas
        if (backgroundBitmap != null) {
            canvas.drawBitmap(backgroundBitmap!!, 0f, 0f, photoPaint)
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
                    canvas.drawBitmap(bmp, sprite.x.toFloat(), sprite.y.toFloat(), photoPaint)
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
                    canvas.drawBitmap(bmp, null, dstRect, photoPaint)
                } else {
                    canvas.drawRect(dstRect, animPlaceholderPaint)
                }
            }
        }

        visuals?.let { it.drawHud(canvas,camp,scene,clock); return }

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
        visuals?.let { it.drawMap(canvas,camp); return }
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

        visuals?.let {
            it.drawBonusBase(canvas,camp)
            if(it.drawTileBonus(canvas,camp) || it.drawWordSearch(canvas,camp)) return
        }
        if(visuals==null) {
        // Banner
        canvas.drawText("MINIJUEGO DE PISTA ADICIONAL (+25,000 PTS)", 40f, 45f, titlePaint)
        canvas.drawText("Tiempo restante: ${camp.clock.text()} · Tipo: ${bonus?.kind ?: "Puzle"}", 40f, 75f, subPaint)

        }
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
                    if (bonus.lockedTiles[idx]) continue
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
                    if (bonus.lockedTiles[idx]) continue
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
        } else if (bonus is SdaJigsawGame) {
            if (jigsawRasterOwner !== bonus) {
                jigsawBitmaps.values.forEach { it.recycle() }; jigsawBitmaps.clear(); jigsawRasterOwner = bonus
            }
            val board = bonus.interaction.board
            bonus.referenceImage?.let {
                canvas.drawBitmap(jigsawBitmap(it), bonus.originX.toFloat(), bonus.originY.toFloat(), Paint(Paint.FILTER_BITMAP_FLAG).apply { alpha = 102 })
            }
            for (id in board.placed) {
                val piece = board.pieces.getValue(id)
                canvas.drawBitmap(jigsawBitmap(bonus.pieceImages.getValue(id)),piece.x.toFloat(),piece.y.toFloat(),photoPaint)
            }
            for (rect in bonus.trayRectangles()) canvas.drawBitmap(jigsawBitmap(bonus.image(rect.id,true)),rect.x.toFloat(),rect.y.toFloat(),null)
            for (rect in listOfNotNull(bonus.arrowUp, bonus.arrowDown)) {
                val disabled = if (rect == bonus.arrowUp) bonus.interaction.tray.firstVisible == 0 else
                    bonus.interaction.tray.firstVisible >= maxOf(0,bonus.interaction.tray.order.size-bonus.interaction.tray.visibleCount)
                val image = bonus.arrowImages[rect.id + if(disabled) "_disabled" else ""]
                if(image != null) canvas.drawBitmap(jigsawBitmap(image),rect.x.toFloat(),rect.y.toFloat(),null)
            }
            board.selected?.let { id ->
                val left = board.heldLeft; val top = board.heldTop
                if(left != null && top != null) canvas.drawBitmap(jigsawBitmap(bonus.image(id,false)),left.toFloat(),top.toFloat(),photoPaint)
            }
            canvas.drawRect(10f,450f,140f,490f,buttonPaint)
            canvas.drawText("GIRAR PIEZA",15f,475f,buttonTextPaint)
            canvas.drawText("${board.placed.size}/${bonus.totalPieces}",15f,520f,textPaint)
        } else if (bonus is SdaWordSearchGame) {
            val retired = bonus.foundWords.flatMap { bonus.board.placements.getValue(it) }.toSet()
            val selected = bonus.selectedCells
            for (cell in 0 until bonus.rows * bonus.cols) {
                val col = cell % bonus.cols; val row = cell / bonus.cols
                val left = bonus.originX + col * bonus.cellWidth
                val top = bonus.originY + row * bonus.cellHeight
                val rect = Rect(left, top, left + bonus.cellWidth, top + bonus.cellHeight)
                val state = if (cell in retired) "locked" else if (cell in selected) "selected" else "normal"
                val bitmap = bonus.tileImages[state]?.nativeImage as? Bitmap
                if (bitmap != null) canvas.drawBitmap(bitmap, null, rect, null)
                else canvas.drawRect(rect, cardPaint)
                canvas.drawText(bonus.board.displayGrid[row][col].toString(),
                    left + bonus.cellWidth / 2f, top + bonus.cellHeight / 2f -
                    (wordLetterPaint.ascent() + wordLetterPaint.descent()) / 2f, wordLetterPaint)
            }
            for ((index, word) in bonus.words.withIndex()) {
                textPaint.isStrikeThruText = word in bonus.foundWords
                canvas.drawText(word, 12f, 140f + index * 27f, textPaint)
            }
            textPaint.isStrikeThruText = false
        } else {
            // Generic bonus display
            canvas.drawRect(172f, 95f, 784f, 503f, cardPaint)
            canvas.drawRect(172f, 95f, 784f, 503f, cardBorderPaint)
            canvas.drawText("Puzle interactivo en progreso...", 320f, 300f, textPaint)
        }

        if(visuals!=null) return
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
        canvas.drawRect(0f,0f,800f,600f,bgPaint)
        val game=camp.bonusGame as? SdaFirstRiddleGame
        if(game==null) {
            canvas.drawText("Desenlace heredado: controlador no compatible",40f,50f,textPaint)
            return
        }
        val definition=game.definition
        canvas.save()
        canvas.clipRect(144,0,800,600)
        canvas.drawBitmap(jigsawBitmap(game.background),definition.backgroundX.toFloat(),game.backgroundY.toFloat(),null)
        for(id in game.interaction.board.placed) {
            val destination=definition.destinations.getValue(id)
            canvas.drawBitmap(jigsawBitmap(game.images.getValue(id)),definition.backgroundX+destination.x,
                game.backgroundY+destination.y,Paint().apply { alpha=(destination.finalAlpha*255).toInt() })
        }
        canvas.restore()
        for(cell in game.interaction.cells()) {
            val source=game.images.getValue(cell.id)
            val fit=minOf(cell.width.toFloat()/source.width,cell.height.toFloat()/source.height)
            val width=maxOf(1,(source.width*fit).toInt());val height=maxOf(1,(source.height*fit).toInt())
            val rect=game.interaction.imageRect(cell.id,width,height)
            canvas.drawBitmap(jigsawBitmap(source),null,Rect(rect.x,rect.y,rect.x+width,rect.y+height),null)
        }
        for(key in listOf("up","down")) {
            val arrow=definition.arrows[key] ?: continue
            val uri=if(game.arrowEnabled(key)) arrow.normalUri else arrow.disabledUri
            canvas.drawBitmap(jigsawBitmap(game.arrowImages.getValue(uri)),arrow.x.toFloat(),arrow.y.toFloat(),null)
        }
        game.caption?.let { caption ->
            val layout=checkNotNull(definition.captionLayout)
            game.captionPaper?.let { canvas.drawBitmap(jigsawBitmap(it),layout.paperX.toFloat(),layout.paperY.toFloat(),null) }
            // Original segment offsets and center alignment; Android font metrics remain provisional.
            for(run in SdaCaptionRuns.parse(caption,20)) {
                canvas.drawText(run.text,layout.x+layout.width/2f-textPaint.measureText(run.text)/2f,
                    layout.y+run.verticalAdvance-textPaint.ascent(),textPaint)
            }
        }
        game.interaction.board.selected?.let { id ->
            val bitmap=jigsawBitmap(game.images.getValue(id))
            canvas.drawBitmap(bitmap,(game.pointerX!!-bitmap.width/2).toFloat(),(game.pointerY!!-bitmap.height/2).toFloat(),null)
        }
        if(game.isSolved) canvas.drawText("Primera fase completada; siguiente fase pendiente",160f,570f,textPaint)
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
        // A resume click belongs to the overlay, including its release; never click through.
        if(resumePointerId!=null) {
            if(event.actionMasked==MotionEvent.ACTION_UP || event.actionMasked==MotionEvent.ACTION_CANCEL) resumePointerId=null
            return true
        }
        if(isPaused) {
            if(event.actionMasked==MotionEvent.ACTION_DOWN) {
                isPaused=false;resumePointerId=event.getPointerId(event.actionIndex)
                onPauseChangedListener?.invoke();invalidate()
            }
            return true
        }
        if(event.actionMasked==MotionEvent.ACTION_DOWN) {
            val camp=campaign
            val x=((event.x-offsetX)/scale).toInt();val y=((event.y-offsetY)/scale).toInt()
            if(camp!=null && visuals?.pauseRect(camp)?.contains(x,y)==true) {
                isPaused=true;onPauseChangedListener?.invoke();invalidate();return true
            }
        }

        val wordCamp = campaign
        if (wordPointerId != null && wordCamp?.phase != SdaCampaignPhase.BONUS) {
            wordCamp?.cancelBonusSelection(); wordPointerId = null
        }
        if (wordCamp?.phase == SdaCampaignPhase.BONUS && wordCamp.bonusGame is SdaWordSearchGame) {
            val x = ((event.x - offsetX) / scale).toInt()
            val y = ((event.y - offsetY) / scale).toInt()
            val solveButton = (visuals?.solveRect ?: Rect(580,530,771,576)).contains(x,y)
            if (event.actionMasked == MotionEvent.ACTION_DOWN && !solveButton) {
                if (event.buttonState and MotionEvent.BUTTON_SECONDARY != 0) return true
                wordPointerId = event.getPointerId(0)
                wordCamp.beginBonusSelection(x, y)
                onBonusInputListener?.invoke()
                invalidate()
                return true
            }
            if (wordPointerId != null) {
                val index = event.findPointerIndex(wordPointerId!!)
                when (event.actionMasked) {
                    MotionEvent.ACTION_MOVE -> if (index >= 0) wordCamp.moveBonusSelection(
                        ((event.getX(index) - offsetX) / scale).toInt(), ((event.getY(index) - offsetY) / scale).toInt())
                    MotionEvent.ACTION_UP -> {
                        if (index >= 0) wordCamp.endBonusSelection(
                            ((event.getX(index) - offsetX) / scale).toInt(), ((event.getY(index) - offsetY) / scale).toInt())
                        else wordCamp.cancelBonusSelection()
                        wordPointerId = null
                        onBonusInputListener?.invoke()
                    }
                    MotionEvent.ACTION_CANCEL -> {
                        wordCamp.cancelBonusSelection(); wordPointerId = null
                        onBonusInputListener?.invoke()
                    }
                    MotionEvent.ACTION_POINTER_UP -> if (event.getPointerId(event.actionIndex) == wordPointerId) {
                        wordCamp.cancelBonusSelection(); wordPointerId = null
                        onBonusInputListener?.invoke()
                    }
                }
                invalidate()
                return true
            }
        }
        val riddleCamp=campaign
        val riddle=riddleCamp?.bonusGame as? SdaFirstRiddleGame
        if(riddleCamp?.phase==SdaCampaignPhase.FINALE_1 && riddle!=null) {
            val x=((event.x-offsetX)/scale).toInt();val y=((event.y-offsetY)/scale).toInt()
            when(event.actionMasked) {
                MotionEvent.ACTION_DOWN -> if(event.buttonState and MotionEvent.BUTTON_SECONDARY==0) {
                    riddleCamp.clickBonus(x,y);onBonusInputListener?.invoke()
                }
                MotionEvent.ACTION_MOVE,MotionEvent.ACTION_HOVER_MOVE -> if(riddle.movePixel(x,y)) onBonusInputListener?.invoke()
            }
            invalidate();return true
        }
        val jigsawCamp = campaign
        val jigsaw = jigsawCamp?.bonusGame as? SdaJigsawGame
        if (jigsawCamp?.phase == SdaCampaignPhase.BONUS && jigsaw != null) {
            val x = ((event.x-offsetX)/scale).toInt(); val y = ((event.y-offsetY)/scale).toInt()
            when (event.actionMasked) {
                MotionEvent.ACTION_UP -> return true // Native tap picks; the next primary tap drops.
                MotionEvent.ACTION_MOVE, MotionEvent.ACTION_HOVER_MOVE -> {
                    if(jigsaw.movePixel(x,y)) onBonusInputListener?.invoke()
                    invalidate(); return true
                }
                MotionEvent.ACTION_DOWN -> if (listOfNotNull(jigsaw.arrowUp,jigsaw.arrowDown).any { x in it.x until it.x+it.width && y in it.y until it.y+it.height }) {
                    jigsawCamp.clickBonus(x,y)
                    onBonusInputListener?.invoke();invalidate();return true
                } else if (x in 10 until 140 && y in 450 until 490) {
                    jigsawCamp.clickBonus(x,y,clockwise=true)
                    onBonusInputListener?.invoke(); invalidate(); return true
                }
            }
        }
        if (event.action == MotionEvent.ACTION_DOWN) {
            val logicalX = ((event.x - offsetX) / scale).toInt()
            val logicalY = ((event.y - offsetY) / scale).toInt()

            val camp = campaign
            if (camp != null) {
                when (camp.phase) {
                    SdaCampaignPhase.MAP -> {
                        if(visuals!=null) {
                            visuals!!.sceneAt(camp,logicalX,logicalY)?.let { onSceneSelectedListener?.invoke(it) }
                            invalidate();return true
                        }
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
                        if ((visuals?.returnMapRect ?: Rect(10,550,133,587)).contains(logicalX,logicalY)) {
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
                        if ((visuals?.solveRect ?: Rect(580,530,771,576)).contains(logicalX,logicalY)) {
                            camp.solveBonus()
                            onBonusInputListener?.invoke()
                            invalidate()
                            return true
                        }
                        camp.clickBonus(logicalX, logicalY, clockwise = event.buttonState and MotionEvent.BUTTON_SECONDARY != 0)
                        onBonusInputListener?.invoke()
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
                    SdaCampaignPhase.FINALE_1, SdaCampaignPhase.FINALE_2, SdaCampaignPhase.FINALE_3 -> return true
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
        if(isPaused) { postInvalidateOnAnimation();return }
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
