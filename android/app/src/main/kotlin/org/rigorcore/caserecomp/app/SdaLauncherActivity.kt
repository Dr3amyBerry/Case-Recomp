package org.rigorcore.caserecomp.app

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import org.rigorcore.caserecomp.sda.SdaCampaign
import org.rigorcore.caserecomp.sda.SdaRiddleBinding
import org.rigorcore.caserecomp.sda.SdaCampaignPhase
import org.rigorcore.caserecomp.sda.SdaCampaignState
import org.rigorcore.caserecomp.sda.SdaClock
import org.rigorcore.caserecomp.sda.SdaContent
import org.rigorcore.caserecomp.sda.SdaImageDecoder
import org.rigorcore.caserecomp.sda.SdaLevels
import org.rigorcore.caserecomp.sda.SdaPixelSource
import org.rigorcore.caserecomp.sda.SdaScene
import java.io.File

/**
 * Launcher activity for SDA engine games (Mystery P.I.: The Vegas Heist).
 * Completely isolated from Director launcher and repositories.
 * Supports full campaign mode (levels, investigation map, scenes, bonus games, finale)
 * as well as standalone scene execution.
 */
class SdaLauncherActivity : Activity() {

    private lateinit var repository: PrivateSdaRepository
    private var content: SdaContent? = null
    private var scene: SdaScene? = null
    private var campaign: SdaCampaign? = null
    private var gameView: SdaGameView? = null
    private var audioSession:SdaAudioSession? = null
    private var hostMenu: android.app.Dialog? = null
    private var changingProfile=false
    private var campaignMusicReady=false
    private var lastMusicRequest:SdaMusicRequest?=null
    private var clock: SdaClock? = null
    private var isFrameLoopRunning = false
    private var lastFrameNanos: Long = 0L

    private val bitmaps = mutableMapOf<String, Bitmap>()
    private var bgBitmap: Bitmap? = null

    private val frameCallback = object : android.view.Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!isFrameLoopRunning) return
            if (lastFrameNanos != 0L) {
                val dt = ((frameTimeNanos - lastFrameNanos) / 1_000_000_000f).coerceIn(0.001f, 0.1f)
                gameView?.step(dt)
            }
            updateCampaignMusic()
            lastFrameNanos = frameTimeNanos
            android.view.Choreographer.getInstance().postFrameCallback(this)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PrivateSdaRepository(this)

        val directPath = intent.getStringExtra(EXTRA_PACKAGE_PATH)
        val packageFile = if (!directPath.isNullOrBlank()) {
            File(directPath).takeIf { it.isFile }
        } else {
            repository.loadActive()?.path
        }

        if (packageFile == null) {
            showNoPackageScreen()
            return
        }

        launchSdaPackage(packageFile)
    }

    private fun showNoPackageScreen() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(0xFF10151E.toInt())
            setPadding(32, 32, 32, 32)
            addView(TextView(this@SdaLauncherActivity).apply {
                text = "Mystery P.I.: The Vegas Heist"
                setTextColor(0xFFE8B84A.toInt())
                textSize = 24f
                gravity = Gravity.CENTER
            })
            addView(TextView(this@SdaLauncherActivity).apply {
                text = "\nNo se ha importado ningún paquete SDA activo.\nImporta un paquete legítimo desde la pantalla principal para jugar."
                setTextColor(Color.WHITE)
                textSize = 16f
                gravity = Gravity.CENTER
            })
        }
        setContentView(layout)
    }

    private fun launchSdaPackage(file: File) {
        try {
            bitmaps.clear()
            bgBitmap = null

            val decoder = SdaImageDecoder { bytes ->
                val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
                if (bmp != null) {
                    object : SdaPixelSource {
                        override val width: Int = bmp.width
                        override val height: Int = bmp.height
                        override fun getAlpha(px: Int, py: Int): Int {
                            if (px !in 0 until width || py !in 0 until height) return 0
                            return (bmp.getPixel(px, py) ushr 24) and 0xFF
                        }
                        override fun getArgb(px: Int, py: Int): Int = if (px in 0 until width && py in 0 until height) bmp.getPixel(px, py) else 0
                        override val nativeImage: Any get() = bmp
                    }
                } else null
            }

            val sdaContent = SdaContent.open(file, decoder)
            content = sdaContent

            val levelsRaw = sdaContent.read("LEVELS_1.XUI")
            if (levelsRaw != null && !intent.hasExtra(EXTRA_SCENE)) {
                // Full campaign mode
                val levels = SdaLevels.parse(levelsRaw)
                val finaleBinding = if (sdaContent.gameId == "vegas_heist") SdaRiddleBinding("ENVS.MSE","firstriddle") else null
                val profile=if(sdaContent.gameId=="vegas_heist") VegasVisualProfile(sdaContent) else null
                val camp = SdaCampaign(levels, seed = System.currentTimeMillis() and 0xFFFFFFFFL, firstRiddle = finaleBinding, hintPolicy=profile?.hintPolicy(), hintRechargeImmediately=profile?.immediateHintRecharge(this)==true, secondRiddle=if(profile!=null) SdaRiddleBinding("ENVS.MSE","secondriddle") else null, interactiveRiddleFactory=profile?.let { adapter -> { c,seed,checkpoint -> requireNotNull(adapter.interactiveRiddle(c,seed,checkpoint)) } })
                campaign = camp
                repository.configureCampaignSlots(sdaContent.gameId)

                // Restore campaign checkpoint if saved
                val savedCampJson = repository.loadCampaignCheckpoint()
                if (!savedCampJson.isNullOrBlank()) {
                    try {
                        val savedState = SdaCampaignState.fromJson(savedCampJson)
                        camp.restore(savedState, sdaContent)
                        Log.i(TAG, "Restored campaign checkpoint at level ${camp.levelIndex + 1}, phase ${camp.phase}")
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to restore campaign checkpoint; preserving stored save", e)
                        campaign = null
                        throw IllegalArgumentException("No se puede restaurar esta partida SDA; se conserva el archivo guardado", e)
                    }
                }

                // Initial scene for view setup
                val initialSceneName = camp.currentSceneName ?: camp.currentLevel.scenes.first()
                val loadedScene = camp.scenes[initialSceneName] ?: sdaContent.loadScene("SCENE_${initialSceneName.uppercase()}.MSL", seed = camp.seed)
                scene = loadedScene
                clock = camp.clock
                cacheBitmaps(loadedScene)

                val view = SdaGameView(this, loadedScene, clock, bgBitmap, bitmaps, camp, profile)
                audioSession=view.visuals?.audioSession(this)
                wireViewCallbacks(view, sdaContent)
                gameView = view
                setContentView(view)
                if(view.visuals!=null) view.post { if(!isFinishing) showResourceMenu(view,SdaMenuEntry.MAIN) }

            } else {
                // Standalone scene mode
                val sceneResource = intent.getStringExtra(EXTRA_SCENE) ?: "SCENE_VAULT.MSL"
                val loadedScene = sdaContent.loadScene(sceneResource, seed = System.currentTimeMillis() and 0xFFFFFFFFL)
                scene = loadedScene
                clock = SdaClock(limit = 1320f)
                cacheBitmaps(loadedScene)

                // Restore standalone scene checkpoint if saved
                val savedStateJson = repository.loadCheckpoint()
                if (!savedStateJson.isNullOrBlank()) {
                    try {
                        val savedState = org.rigorcore.caserecomp.sda.SdaSceneState.fromJson(savedStateJson)
                        if (savedState.sceneName == loadedScene.name) {
                            loadedScene.restore(savedState)
                            clock?.elapsed = savedState.elapsed
                            Log.i(TAG, "Restored SDA checkpoint for ${loadedScene.name}")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to restore SDA checkpoint", e)
                    }
                }

                val view = SdaGameView(this, loadedScene, clock, bgBitmap, bitmaps)
                wireStandaloneCallbacks(view)
                gameView = view
                setContentView(view)
            }

            hideSystemBars()
        } catch (t: Throwable) {
            Log.e(TAG, "Failed to launch SDA package", t)
            Toast.makeText(this, "Error al iniciar paquete SDA: ${t.message}", Toast.LENGTH_LONG).show()
            finish()
        }
    }

    private fun cacheBitmaps(loadedScene: SdaScene) {
        bitmaps.clear()
        bgBitmap = null
        for (sprite in loadedScene.drawOrder) {
            val bmp = sprite.image.nativeImage as? Bitmap
            if (bmp != null) {
                if (sprite.identity.isNotEmpty()) {
                    bitmaps[sprite.identity] = bmp
                } else if (bgBitmap == null && bmp.width >= 600) {
                    bgBitmap = bmp
                }
            }
        }
    }

    private fun updateCampaignMusic(force:Boolean=false) {
        if(!campaignMusicReady || hostMenu!=null || isFinishing || isDestroyed) return
        val phase=campaign?.phase ?: return
        val audio=audioSession ?: return
        val request=gameView?.visuals?.campaignMusic(phase)
        if(!force && request==lastMusicRequest) return
        lastMusicRequest=request
        if(request==null) audio.stopMusic() else audio.playMusic(request.stream,request.loop)
    }

    private fun showResourceMenu(view:SdaGameView,entry:SdaMenuEntry) {
        if(hostMenu?.isShowing==true) return
        view.pauseForMenu()
        autoSave()
        val dialog=android.app.Dialog(this)
        val nativeMenu=view.visuals?.menuView(this,campaign,entry,audioSession) { action ->
            when(action) {
                SdaMenuAction.SWITCH_PROFILE -> {
                    val id=dialog.window?.decorView?.findViewWithTag<SdaResourceMenuView>("sda-resource-menu")?.requestedProfileId
                    val selected=id?.let { repository.profileStorage.getProfile(it) }
                    autoSave()
                    if(selected!=null && repository.switchCampaignProfile(selected)) {
                        changingProfile=true
                        campaign=null;scene=null;gameView=null
                        dialog.dismiss();recreate()
                    } else Toast.makeText(this,"No se pudo cambiar de jugador; se conserva la partida",Toast.LENGTH_SHORT).show()
                }
                SdaMenuAction.RESUME -> dialog.dismiss()
                // Original EXIT returns to the multi-game host after saving.
                SdaMenuAction.RETURN_TO_CATALOGUE -> { autoSave();dialog.dismiss();finish() }
                else -> Toast.makeText(this,"Pantalla pendiente de implementar",Toast.LENGTH_SHORT).show()
            }
        }
        if(nativeMenu!=null) {
            dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
            dialog.setContentView(nativeMenu)
            dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
            dialog.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            dialog.setOnDismissListener { hostMenu=null;if(!changingProfile) { campaignMusicReady=true;view.resumeFromMenu();updateCampaignMusic(force=true);view.post { showRiddleDialog(view) } } }
            hostMenu=dialog;dialog.show()
            dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
            dialog.window?.decorView?.windowInsetsController?.hide(WindowInsets.Type.systemBars())
        } else {
            val fallback=android.app.AlertDialog.Builder(this).setTitle("Case-Recomp")
                .setItems(arrayOf("Continuar partida","Guardar y volver al cat\u00e1logo")) { _,item ->
                    if(item==1) { autoSave();finish() }
                }.setOnDismissListener { hostMenu=null;campaignMusicReady=true;view.resumeFromMenu();updateCampaignMusic(force=true);view.post { showRiddleDialog(view) } }.create()
            hostMenu=fallback;fallback.show()
        }
    }

    /** Uses the title's original XUI dialogs; the engine owns guarded transitions and persisted acknowledgement. */
    private fun showRiddleDialog(view:SdaGameView) {
        if(hostMenu!=null || isFinishing || isDestroyed) return
        val camp=campaign ?: return
        val content=content ?: return
        val dialog=android.app.Dialog(this)
        var confirmed=false
        val panel=view.visuals?.riddleDialog(this,camp) {
            try {
                when(val game=camp.bonusGame) {
                    is org.rigorcore.caserecomp.sda.SdaFirstRiddleGame -> camp.continueFirstRiddle(content)
                    is org.rigorcore.caserecomp.sda.SdaSecondRiddleGame -> if(game.isSolved) camp.continueSecondRiddle(content) else game.start()
                    is org.rigorcore.caserecomp.sda.SdaInteractiveRiddleGame -> if(game.isSolved) camp.confirmInteractiveRiddleComplete() else game.controller.start()
                    else -> return@riddleDialog
                }
                confirmed=true;autoSave();view.invalidate();dialog.dismiss()
            } catch(e:Exception) {
                Log.e(TAG,"Cannot advance riddle; preserving checkpoint",e)
                Toast.makeText(this,"No se pudo cargar la siguiente fase; se conserva la partida",Toast.LENGTH_LONG).show()
            }
        } ?: return
        view.pauseForMenu()
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(panel)
        dialog.window?.setBackgroundDrawable(android.graphics.drawable.ColorDrawable(Color.TRANSPARENT))
        dialog.window?.clearFlags(android.view.WindowManager.LayoutParams.FLAG_DIM_BEHIND)
        dialog.setOnDismissListener { hostMenu=null;view.resumeFromMenu();autoSave();if(confirmed) view.post { if(camp.phase==SdaCampaignPhase.CAMPAIGN_COMPLETE) showResourceMenu(view,SdaMenuEntry.MAIN) else showRiddleDialog(view) } }
        dialog.setCancelable(true)
        hostMenu=dialog;dialog.show()
        dialog.window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.MATCH_PARENT)
        dialog.window?.decorView?.windowInsetsController?.hide(WindowInsets.Type.systemBars())
    }

    private fun wireViewCallbacks(view: SdaGameView, sdaContent: SdaContent) {
        view.onMenuListener = { showResourceMenu(view,SdaMenuEntry.PAUSE) }
        view.onHintListener = {
            val random=org.rigorcore.caserecomp.sda.SdaRng(android.os.SystemClock.uptimeMillis() and 0xFFFFFFFFL)
            if(campaign?.requestHint { size -> random.next()%size }==true) {
                view.visuals?.hintSound()?.let { audioSession?.playEffect(it) }
                autoSave()
            }
        }
        view.onPauseChangedListener = { autoSave() }
        view.onEffectListener = { id -> audioSession?.playEffect(id) }
        view.onBonusInputListener = { autoSave();view.post { showRiddleDialog(view) } }
        view.onSceneSelectedListener = { sceneName ->
            val camp = campaign
            if (camp != null) {
                val sc = camp.enterScene(sceneName, sdaContent)
                scene = sc
                cacheBitmaps(sc)
                view.scene = sc
                view.backgroundBitmap = bgBitmap
                view.spriteBitmaps = bitmaps
                Log.i(TAG, "Entered scene $sceneName in campaign (level ${camp.levelIndex + 1})")
                autoSave()
            }
        }

        view.onReturnToMapListener = {
            campaign?.toInvestigationMap()
            autoSave()
        }

        view.onStartBonusListener = {
            campaign?.startBonus(sdaContent)
            Toast.makeText(this, "¡Minijuego de bonificación desbloqueado!", Toast.LENGTH_SHORT).show()
            autoSave()
        }

        view.onNextLevelListener = {
            val camp = campaign
            if (camp != null) {
                camp.confirmLevelComplete(sdaContent)
                repository.clearCheckpoint()
                if (camp.phase == SdaCampaignPhase.MAP) {
                    Toast.makeText(this, "Nivel ${camp.levelIndex + 1}: ${camp.currentLevel.title}", Toast.LENGTH_SHORT).show()
                }
                autoSave()
            }
        }

        view.onCampaignCompletedListener = {
            autoSave();showResourceMenu(view,SdaMenuEntry.MAIN)
        }

        view.onObjectFoundListener = { id, gain ->
            view.visuals?.sceneFeedback(true)?.let { audioSession?.playEffect(it) }
            Log.i(TAG, "Object found: $id, +$gain pts, total: ${campaign?.points ?: scene?.score?.points}")
            Toast.makeText(this, "¡Objeto encontrado! +$gain pts", Toast.LENGTH_SHORT).show()
            autoSave()
        }

        view.onMissListener = { penalty ->
            view.visuals?.sceneFeedback(false)?.let { audioSession?.playEffect(it) }
            Log.i(TAG, "Miss clicked: penalty=$penalty")
        }

        view.onSceneCompleteListener = {
            val camp = campaign
            if (camp != null) {
                if (camp.phase == SdaCampaignPhase.OBJECTS_COMPLETE) {
                    Toast.makeText(this, "¡Todos los objetos del nivel encontrados!", Toast.LENGTH_SHORT).show()
                } else if (camp.phase == SdaCampaignPhase.SCENE_COMPLETE) {
                    Toast.makeText(this, "Lote de escena completado. Regresa al mapa.", Toast.LENGTH_SHORT).show()
                }
            }
            autoSave()
        }
    }

    private fun autoSave() {
        val camp = campaign
        if (camp != null) {
            try {
                repository.saveCampaignCheckpoint(camp.snapshot().toJson())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to auto-save campaign checkpoint", e)
            }
        } else {
            scene?.let { sc ->
                try {
                    repository.saveCheckpoint(sc.snapshot().toJson())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to auto-save SDA checkpoint", e)
                }
            }
        }
    }

    private fun wireStandaloneCallbacks(view: SdaGameView) {
        view.onObjectFoundListener = { id, gain ->
            view.visuals?.sceneFeedback(true)?.let { audioSession?.playEffect(it) }
            Log.i(TAG, "Object found: $id, +$gain pts, total: ${scene?.score?.points}")
            Toast.makeText(this, "¡Objeto encontrado! +$gain pts", Toast.LENGTH_SHORT).show()
        }
        view.onMissListener = { penalty ->
            view.visuals?.sceneFeedback(false)?.let { audioSession?.playEffect(it) }
            Log.i(TAG, "Miss clicked: penalty=$penalty, points: ${scene?.score?.points}")
        }
        view.onSceneCompleteListener = {
            val sc = scene
            val deck = sc?.deck
            if (sc != null && deck != null && deck.cursor < sc.candidateSets.size) {
                val nextBatch = sc.nextBatch(System.currentTimeMillis() and 0xFFFFFFFFL)
                Log.i(TAG, "Next batch unlocked: ${nextBatch.size} sets")
                Toast.makeText(this, "Siguiente lote de objetivos (${nextBatch.size})", Toast.LENGTH_SHORT).show()
            } else {
                repository.clearCheckpoint()
                Log.i(TAG, "Scene completed! Final score: ${sc?.score?.points}")
                Toast.makeText(this, "¡Escena completada! Puntuación final: ${sc?.score?.points}", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        audioSession?.resume()
        if (!isFrameLoopRunning) {
            isFrameLoopRunning = true
            lastFrameNanos = 0L
            android.view.Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    override fun onPause() {
        audioSession?.pause()
        isFrameLoopRunning = false
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
        val camp = campaign
        if (camp != null) {
            try {
                repository.saveCampaignCheckpoint(camp.snapshot().toJson())
            } catch (e: Exception) {
                Log.w(TAG, "Failed to save campaign checkpoint", e)
            }
        } else {
            scene?.let { sc ->
                try {
                    repository.saveCheckpoint(sc.snapshot().toJson())
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to save SDA checkpoint", e)
                }
            }
        }
        super.onPause()
    }

    override fun onDestroy() {
        hostMenu?.setOnDismissListener(null)
        hostMenu?.dismiss();hostMenu=null
        isFrameLoopRunning = false
        android.view.Choreographer.getInstance().removeFrameCallback(frameCallback)
        audioSession?.close();audioSession=null
        content?.close()
        content = null
        super.onDestroy()
    }

    private fun hideSystemBars() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            window.insetsController?.let {
                it.hide(WindowInsets.Type.systemBars())
                it.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        }
    }

    companion object {
        const val EXTRA_PLAY = "extra-play-sda"
        const val EXTRA_PACKAGE_PATH = "package_path"
        const val EXTRA_SCENE = "extra-scene"
        private const val TAG = "CaseRecompSda"
    }
}
