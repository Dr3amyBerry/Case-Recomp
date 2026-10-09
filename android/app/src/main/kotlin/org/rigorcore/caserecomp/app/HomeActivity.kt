package org.rigorcore.caserecomp.app

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.widget.Button
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast

/**
 * Launcher: the titles this build can play, each with its cover and an import or play button.
 * Nothing of a title ships in the APK: the user imports their own Director content ZIP, and the
 * cover shown is the bitmap that package names as its cover.
 */
class HomeActivity : Activity() {
    private data class Game(val title: String, val ready: Boolean)

    private lateinit var repository: PrivateDirectorRepository
    private lateinit var sdaRepository: PrivateSdaRepository
    private lateinit var games: LinearLayout
    private var importing = false
    private var importingSda = false
    private var workerToken = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PrivateDirectorRepository(this)
        sdaRepository = PrivateSdaRepository(this)

        val sdaImportPath = intent.getStringExtra(EXTRA_IMPORT_SDA_PATH)
        if (!sdaImportPath.isNullOrBlank()) {
            val importFile = java.io.File(sdaImportPath)
            if (importFile.isFile) {
                runCatching {
                    importFile.inputStream().use(sdaRepository::import)
                    Log.i(TAG, "Direct SDA import succeeded: $sdaImportPath")
                }.onFailure {
                    Log.e(TAG, "Direct SDA import failed: $sdaImportPath", it)
                }
            }
        }

        games = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), 0, dp(24), dp(16))
        }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(TOP, BOTTOM))
        }
        column.addView(TextView(this).apply {
            text = APP_TITLE
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 34f)
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            letterSpacing = 0.06f
            gravity = Gravity.CENTER
            setPadding(0, dp(14), 0, dp(10))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(HorizontalScrollView(this).apply {
            isFillViewport = true
            isHorizontalScrollBarEnabled = false
            addView(games, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_VERTICAL))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        column.addView(TextView(this).apply {
            text = BETA_NOTICE
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(dp(24), 0, dp(24), dp(10))
            setOnClickListener { openIssues() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        column.addView(TextView(this).apply {
            text = "Licencia no comercial · Avisos legales · Privacidad"
            setTextColor(GOLD)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(dp(12), 0, dp(12), dp(12))
            contentDescription = "Abrir avisos legales, privacidad y términos de Case Recomp"
            setOnClickListener { showLegalNotices() }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        setContentView(column)
        hideSystemBars()
        showBetaNoticeOnce()
    }

    /** First launch: this is a beta, delivered as is; problems are reported on GitHub. */
    private fun showBetaNoticeOnce() {
        val prefs = getSharedPreferences("case-recomp-home", MODE_PRIVATE)
        if (prefs.getBoolean(BETA_SEEN, false)) return
        android.app.AlertDialog.Builder(this)
            .setTitle("Versión beta")
            .setMessage("Case Recomp es una beta independiente: puede tener errores gráficos o de sonido, " +
                "perder partidas o no reproducir todas las funciones del juego original.\n\n" +
                "No incluye juegos comerciales ni está afiliada a Big Fish Games o a otros titulares. " +
                "Debes aportar tu propia copia y respetar las licencias y leyes aplicables.\n\n" +
                "Consulta los avisos legales, términos y privacidad desde la pantalla principal. " +
                "No publiques archivos del juego ni datos personales en GitHub.")
            .setPositiveButton("Entendido") { _, _ -> prefs.edit().putBoolean(BETA_SEEN, true).apply() }
            .setCancelable(false)
            .show()
    }

    private fun openIssues() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(ISSUES_URL))) }
    }

    /** The concise non-affiliation notice is visible offline; full bilingual notices open externally. */
    private fun showLegalNotices() {
        val labels = arrayOf("Licencia: PolyForm Noncommercial 1.0.0", "Aviso legal y marcas",
            "Política de privacidad", "Términos de uso", "Licencias de terceros",
            "Alcance y reserva de derechos comerciales", "Contacto: contact@rigorcore.com")
        val docs = arrayOf("LICENSE", "LEGAL.md", "PRIVACY.md", "TERMS.md",
            "THIRD_PARTY_NOTICES.md", "LICENSING_STATUS.md")
        android.app.AlertDialog.Builder(this)
            .setTitle("Case Recomp — información legal")
            .setItems(labels) { _, which ->
                val isContact = which == docs.size
                val uri = if (isContact) android.net.Uri.parse("mailto:contact@rigorcore.com")
                    else android.net.Uri.parse("https://github.com/Dr3amyBerry/Case-Recomp/blob/main/" + docs[which])
                val action = if (isContact) Intent.ACTION_SENDTO else Intent.ACTION_VIEW
                runCatching { startActivity(Intent(action, uri)) }.onFailure {
                    Toast.makeText(this,
                        if (isContact) "No hay aplicación de correo disponible" else "No hay navegador disponible",
                        Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Cerrar", null)
            .show()
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
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

    /** Rebuild the cards from the repository's state (imported or not, its cover). */
    private fun refresh() {
        games.removeAllViews()
        val width = dp(cardWidth())
        for (game in HomeCatalog.GAMES) {
            val view = when {
                game.engine == EngineFamily.DIRECTOR && game.status == CompatibilityStatus.VERIFIED ->
                    directorCard(game)
                game.engine == EngineFamily.SDA ->
                    sdaCard(game)
                else ->
                    statusCard(game)
            }
            games.addView(view, LinearLayout.LayoutParams(width, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                marginStart = dp(CARD_GAP / 2)
                marginEnd = dp(CARD_GAP / 2)
            })
        }
    }

    private fun sdaCard(game: GameEntry): View {
        val imported = sdaRepository.hasActive()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(CARD, dp(14).toFloat(), CARD_EDGE)
        }
        val cover = sdaRepository.activeCover().takeIf { imported }
            ?.let { file -> runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() }
        card.addView(FrameLayout(this).apply {
            background = rounded(COVER_BACK, dp(10).toFloat(), 0)
            if (cover != null) {
                addView(ImageView(this@HomeActivity).apply {
                    setImageBitmap(cover)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = game.title
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                })
            } else {
                addView(TextView(this@HomeActivity).apply {
                    text = "🎰\n${game.title}"
                    setTextColor(GOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    gravity = Gravity.CENTER
                })
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(coverHeight())))
        card.addView(TextView(this).apply {
            text = game.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(2))
        })
        card.addView(TextView(this).apply {
            text = "${game.subtitle} · ${game.engine.displayName}"
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })
        if (importingSda) {
            card.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(36), dp(36)))
            card.addView(TextView(this).apply {
                text = "Importando SDA…"
                setTextColor(MUTED)
                gravity = Gravity.CENTER
            })
            return card
        }
        card.addView(actionButton(if (imported) "Jugar (SDA)" else "Importar paquete SDA", primary = imported) {
            if (imported) playSda() else selectSdaZip()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        if (imported) {
            card.addView(TextView(this).apply {
                text = "Importar de nuevo"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
                setOnClickListener { selectSdaZip() }
            })
        }
        return card
    }

    /** A title whose engine or runtime is in development: shown clearly, but not importable via Director. */
    private fun statusCard(game: GameEntry): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = rounded(CARD, dp(14).toFloat(), CARD_EDGE)
        alpha = 0.85f
        addView(FrameLayout(this@HomeActivity).apply {
            background = rounded(COVER_BACK, dp(10).toFloat(), 0)
            addView(TextView(this@HomeActivity).apply {
                val icon = if (game.engine == EngineFamily.SDA) "🎰" else "🔒"
                text = "$icon\n${game.title}"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(coverHeight())))
        addView(TextView(this@HomeActivity).apply {
            text = game.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(2))
        })
        addView(TextView(this@HomeActivity).apply {
            text = "${game.subtitle} · ${game.engine.displayName}"
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })
        val btnLabel = when (game.status) {
            CompatibilityStatus.IN_DEVELOPMENT -> "En desarrollo"
            CompatibilityStatus.PLANNED -> "Planificado"
            CompatibilityStatus.VERIFIED -> "Disponible"
        }
        addView(actionButton(btnLabel, primary = false) {
            showDevelopmentNotice(game)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
    }

    private fun showDevelopmentNotice(game: GameEntry) {
        val message = when (game.engine) {
            EngineFamily.SDA ->
                "${game.subtitle}: ${game.title} utiliza el motor SDA.\n\n" +
                "El soporte para este motor se está integrando actualmente en Case-Recomp como parte de la arquitectura multijuego unificada.\n\n" +
                "Los componentes del runtime Kotlin en :engine están en desarrollo activo. Las opciones de importación y juego se habilitarán una vez completada la integración."
            EngineFamily.DIRECTOR ->
                "${game.subtitle}: ${game.title} utiliza el motor Director / Lingo.\n\n" +
                "La compatibilidad con este título está pendiente de verificación para garantizar la fidelidad con los recursos originales."
        }
        android.app.AlertDialog.Builder(this)
            .setTitle(game.title)
            .setMessage(message)
            .setPositiveButton("Cerrar", null)
            .show()
    }

    private fun directorCard(game: GameEntry): View {
        val imported = repository.hasActive()
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(12), dp(12), dp(12))
            background = rounded(CARD, dp(14).toFloat(), CARD_EDGE)
        }
        val cover = repository.activeCover().takeIf { imported }
            ?.let { file -> runCatching { BitmapFactory.decodeFile(file.path) }.getOrNull() }
        card.addView(FrameLayout(this).apply {
            background = rounded(COVER_BACK, dp(10).toFloat(), 0)
            if (cover != null) {
                addView(ImageView(this@HomeActivity).apply {
                    setImageBitmap(cover)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    contentDescription = game.title
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                })
            } else {
                // Before an import there is no cover to show: the title's name stands in.
                addView(TextView(this@HomeActivity).apply {
                    text = "${game.subtitle}\n${game.title}"
                    setTextColor(GOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    gravity = Gravity.CENTER
                })
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(coverHeight())))
        card.addView(TextView(this).apply {
            text = game.title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(2))
        })
        card.addView(TextView(this).apply {
            text = "${game.subtitle} · ${game.engine.displayName}"
            setTextColor(MUTED)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        })
        if (importing) {
            card.addView(ProgressBar(this), LinearLayout.LayoutParams(dp(36), dp(36)))
            card.addView(TextView(this).apply {
                text = "Importando…"
                setTextColor(MUTED)
                gravity = Gravity.CENTER
            })
            return card
        }
        card.addView(actionButton(if (imported) "Jugar" else "Importar", primary = true) {
            if (imported) play() else selectZip()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
        if (imported) {
            card.addView(TextView(this).apply {
                text = "Importar de nuevo"
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                gravity = Gravity.CENTER
                setPadding(0, dp(8), 0, 0)
                setOnClickListener { selectZip() }
            })
        }
        return card
    }

    private fun actionButton(label: String, primary: Boolean, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(if (primary) Color.BLACK else Color.WHITE)
        background = rounded(if (primary) GOLD else CARD_EDGE, dp(24).toFloat(), 0)
        stateListAnimator = null
        setOnClickListener { onClick() }
    }

    private fun rounded(color: Int, radius: Float, stroke: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = radius
        if (stroke != 0) setStroke(dp(1), stroke)
    }

    private fun play() {
        startActivity(Intent(this, DirectorLauncherActivity::class.java).putExtra(DirectorLauncherActivity.EXTRA_PLAY, true))
    }

    private fun playSda() {
        startActivity(Intent(this, SdaLauncherActivity::class.java).putExtra(SdaLauncherActivity.EXTRA_PLAY, true))
    }

    private fun selectZip() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        runCatching { startActivityForResult(intent, REQUEST_ZIP) }
            .onFailure { Toast.makeText(this, "No hay un selector de archivos disponible", Toast.LENGTH_LONG).show() }
    }

    private fun selectSdaZip() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        runCatching { startActivityForResult(intent, REQUEST_SDA_ZIP) }
            .onFailure { Toast.makeText(this, "No hay un selector de archivos disponible", Toast.LENGTH_LONG).show() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val token = ++workerToken

        if (requestCode == REQUEST_ZIP) {
            importing = true
            refresh()
            Thread {
                val result = runCatching {
                    contentResolver.openInputStream(uri)?.use(repository::import) ?: error("no se pudo abrir el archivo")
                }
                runOnUiThread {
                    if (isDestroyed || token != workerToken) return@runOnUiThread
                    importing = false
                    result.exceptionOrNull()?.let {
                        Log.e(TAG, "Director import failed", it)
                        Toast.makeText(this, "Importación rechazada: " + (it.message ?: "paquete no válido"), Toast.LENGTH_LONG).show()
                    }
                    refresh()
                }
            }.start()
        } else if (requestCode == REQUEST_SDA_ZIP) {
            importingSda = true
            refresh()
            Thread {
                val result = runCatching {
                    contentResolver.openInputStream(uri)?.use(sdaRepository::import) ?: error("no se pudo abrir el archivo")
                }
                runOnUiThread {
                    if (isDestroyed || token != workerToken) return@runOnUiThread
                    importingSda = false
                    result.exceptionOrNull()?.let {
                        Log.e(TAG, "SDA import failed", it)
                        Toast.makeText(this, "Importación SDA rechazada: " + (it.message ?: "paquete no válido"), Toast.LENGTH_LONG).show()
                    }
                    refresh()
                }
            }.start()
        }
    }

    override fun onDestroy() {
        workerToken++
        super.onDestroy()
    }

    /** Card width (dp): decoupled from catalog size to support a smooth horizontal carousel. */
    private fun cardWidth(): Int {
        val screenWidth = resources.configuration.screenWidthDp
        return minOf(260, (screenWidth * 0.78f).toInt()).coerceAtLeast(200)
    }

    /** Cover height (dp): what the screen leaves under the title, name and buttons. */
    private fun coverHeight(): Int {
        val config = resources.configuration
        val byHeight = config.screenHeightDp - 220
        return byHeight.coerceIn(120, 240)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val APP_TITLE = "Case Recomp"
        const val CARD_GAP = 16
        const val BETA_SEEN = "beta-notice-seen"
        const val ISSUES_URL = "https://github.com/Dr3amyBerry/Case-Recomp/issues"
        const val BETA_NOTICE = "Beta: puede tener errores. Si encuentras alguno, avísanos en GitHub (toca aquí). " +
            "Se entrega tal cual, sin garantías. No incluye ningún juego."
        const val REQUEST_ZIP = 7001
        const val REQUEST_SDA_ZIP = 7002
        const val EXTRA_IMPORT_SDA_PATH = "import_sda_path"
        const val TAG = "CaseRecompHome"
        const val TOP = 0xFF1B2333.toInt()
        const val BOTTOM = 0xFF07090D.toInt()
        const val CARD = 0xFF222B3B.toInt()
        const val CARD_EDGE = 0xFF3A465C.toInt()
        const val COVER_BACK = 0xFF10151E.toInt()
        const val GOLD = 0xFFE8B84A.toInt()
        const val MUTED = 0xFFAAB4C4.toInt()
    }
}

enum class EngineFamily(val displayName: String) {
    DIRECTOR("Director / Lingo"),
    SDA("SDA Engine"),
}

enum class CompatibilityStatus(val label: String) {
    VERIFIED("Verificado"),
    IN_DEVELOPMENT("En desarrollo"),
    PLANNED("Planificado"),
}

data class GameEntry(
    val id: String,
    val title: String,
    val subtitle: String,
    val engine: EngineFamily,
    val status: CompatibilityStatus,
)

object HomeCatalog {
    val GAMES: List<GameEntry> = listOf(
        GameEntry(
            id = "huntsville",
            title = "Huntsville",
            subtitle = "Mystery Case Files",
            engine = EngineFamily.DIRECTOR,
            status = CompatibilityStatus.VERIFIED,
        ),
        GameEntry(
            id = "prime_suspects",
            title = "Prime Suspects",
            subtitle = "Mystery Case Files",
            engine = EngineFamily.DIRECTOR,
            status = CompatibilityStatus.IN_DEVELOPMENT,
        ),
        GameEntry(
            id = "ravenhearst",
            title = "Ravenhearst",
            subtitle = "Mystery Case Files",
            engine = EngineFamily.DIRECTOR,
            status = CompatibilityStatus.IN_DEVELOPMENT,
        ),
        GameEntry(
            id = "vegas_heist",
            title = "The Vegas Heist",
            subtitle = "Mystery P.I.",
            engine = EngineFamily.SDA,
            status = CompatibilityStatus.IN_DEVELOPMENT,
        ),
    )
}
