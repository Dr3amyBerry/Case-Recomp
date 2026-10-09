package org.rigorcore.caserecomp.app

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.rigorcore.caserecomp.MiniJson
import org.rigorcore.caserecomp.sda.*
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Own synthetic data: Android input/render/checkpoint coverage, not original Vegas parity. */
@RunWith(AndroidJUnit4::class)
class SdaWordSearchInstrumentationTest {
    @Test fun drag_cancel_and_resume_at_multiple_display_sizes() {
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            val context = ApplicationProvider.getApplicationContext<Context>()
            withContent(context) { content ->
                for ((width, height) in listOf(800 to 600, 1600 to 1200, 1000 to 600)) {
                    val level = SdaLevel(1, 600f, 1, listOf("one"), "Own fixture", "own.wsg", "board")
                    val campaign = SdaCampaign(listOf(level, level.copy(clue = 2)), seed = 8)
                    val scene = campaign.enterScene("one", content)
                    assertTrue(campaign.clickScene(201, 21) is SdaClickResult.Found)
                    repeat(100) { campaign.advance(.04f) }
                    assertEquals(SdaCampaignPhase.OBJECTS_COMPLETE, campaign.phase)
                    campaign.startBonus(content)
                    var game = campaign.bonusGame as SdaWordSearchGame
                    val before = campaign.points
                    val view = SdaGameView(context, scene, campaign = campaign)
                    var lastSaved = ""
                    view.onBonusInputListener = { lastSaved = campaign.snapshot().toJson() }
                    view.layout(0, 0, width, height)
                    val screen = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    view.draw(Canvas(screen))
                    val scale = minOf(width / 800f, height / 600f)
                    val offsetX = (width - 800f * scale) / 2f
                    val offsetY = (height - 600f * scale) / 2f
                    fun send(action: Int, cell: Int) {
                        val x = game.originX + (cell % game.cols + .5f) * game.cellWidth
                        val y = game.originY + (cell / game.cols + .5f) * game.cellHeight
                        val event = MotionEvent.obtain(0, SystemClock.uptimeMillis(), action,
                            offsetX + x * scale, offsetY + y * scale, 0)
                        try { assertTrue(view.onTouchEvent(event)) } finally { event.recycle() }
                    }
                    val first = game.board.placements.values.first()
                    send(MotionEvent.ACTION_DOWN, first.first())
                    send(MotionEvent.ACTION_MOVE, first.last())
                    assertEquals(first.toSet(), game.selectedCells)
                    send(MotionEvent.ACTION_CANCEL, first.last())
                    assertNull(game.board.selectedStart)
                    assertTrue(game.foundWords.isEmpty())
                    assertEquals(before, campaign.points)
                    assertEquals(campaign.snapshot().toJson(), lastSaved)
                    var resumed = false
                    for (word in game.words) {
                        val path = game.board.placements.getValue(word)
                        send(MotionEvent.ACTION_DOWN, path.last())
                        send(MotionEvent.ACTION_MOVE, path.first())
                        if (!resumed) {
                            val partial = campaign.snapshot().toJson()
                            campaign.restore(SdaCampaignState.fromJson(partial), content)
                            assertEquals(partial, campaign.snapshot().toJson())
                            game = campaign.bonusGame as SdaWordSearchGame
                            resumed = true
                        }
                        send(MotionEvent.ACTION_UP, path.first())
                        assertTrue(word in game.foundWords)
                        assertEquals(campaign.snapshot().toJson(), lastSaved)
                        view.draw(Canvas(screen))
                    }
                    assertTrue(game.isSolved)
                    assertEquals(750, game.placementPoints)
                    assertEquals(before + 750 + game.points, campaign.points)
                    assertEquals(SdaCampaignPhase.LEVEL_COMPLETE, campaign.phase)
                    campaign.confirmLevelComplete()
                    assertEquals(1, campaign.levelIndex)
                    assertEquals(SdaCampaignPhase.MAP, campaign.phase)
                    screen.recycle()
                }
            }
        }
    }

    private fun withContent(context: Context, block: (SdaContent) -> Unit) {
        val bitmap = Bitmap.createBitmap(51, 51, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(0xffeeeeee.toInt())
        val image = ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
        bitmap.recycle()
        val files = mapOf(
            "tile.png" to image,
            "SCENE_ONE.MSL" to """<xui><texture id="t" uri="tile.png"/>
                <eyespyimage id="a" x="200" y="20" tex="t"/>
                <eyespyset objects="a" itemnamelist="Own object"/></xui>""".toByteArray(),
            "own.wsg" to """<xui><texture id="t" uri="tile.png"/>
                <mpi:wordsearchgametiles rows="8" columns="12" text="@ID_OWN" normal="t" selected="t" locked="t"/></xui>""".toByteArray(),
            "WORDSEARCH.TXT" to "ID_OWN = \"Clue,Vault,Casino\"".toByteArray(),
            "ENVS.MSE" to """<xui><image id="wordsearch_board" x="172" y="96"/></xui>""".toByteArray(),
        )
        val manifest = MiniJson.canonical(mapOf("format" to "case-recomp-sda-content", "version" to 1,
            "game_id" to "own-android-fixture", "files" to files.mapValues {
                listOf(it.value.size, MessageDigest.getInstance("SHA-256").digest(it.value)
                    .joinToString("") { b -> "%02x".format(b) })
            })).toByteArray()
        val file = File.createTempFile("sda-wordsearch-own-", ".zip", context.cacheDir)
        try {
            ZipOutputStream(file.outputStream()).use { zip ->
                for ((name, bytes) in files + ("manifest.json" to manifest)) {
                    zip.putNextEntry(ZipEntry(name)); zip.write(bytes); zip.closeEntry()
                }
            }
            SdaContent.open(file, SdaImageDecoder { bytes ->
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { decoded ->
                    object : SdaPixelSource {
                        override val width = decoded.width
                        override val height = decoded.height
                        override val nativeImage = decoded
                        override fun getAlpha(px: Int, py: Int) = decoded.getPixel(px, py).ushr(24)
                    }
                }
            }).use(block)
        } finally { file.delete() }
    }
}
