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
    private lateinit var games: LinearLayout
    private var importing = false
    private var workerToken = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PrivateDirectorRepository(this)
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
            text = "Proyecto independiente · Aviso legal · Privacidad · Términos"
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
        val labels = arrayOf("Aviso legal y marcas", "Política de privacidad", "Términos de uso",
            "Licencias de terceros", "Estado de licencia del código", "Contacto: contact@rigorcore.com")
        val docs = arrayOf("LEGAL.md", "PRIVACY.md", "TERMS.md", "THIRD_PARTY_NOTICES.md",
            "LICENSING_STATUS.md")
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
        for (game in GAMES) {
            games.addView(if (game.ready) card(game.title) else lockedCard(game.title),
                LinearLayout.LayoutParams(dp(coverHeight() * 3 / 2 + 24), ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                    marginStart = dp(CARD_GAP / 2); marginEnd = dp(CARD_GAP / 2)
                })
        }
    }

    /** A title the engine is not verified against yet: shown, but not importable. */
    private fun lockedCard(title: String): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        setPadding(dp(12), dp(12), dp(12), dp(12))
        background = rounded(CARD, dp(14).toFloat(), CARD_EDGE)
        alpha = 0.6f
        addView(FrameLayout(this@HomeActivity).apply {
            background = rounded(COVER_BACK, dp(10).toFloat(), 0)
            addView(TextView(this@HomeActivity).apply {
                text = "🔒\n" + title
                setTextColor(MUTED)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                gravity = Gravity.CENTER
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(coverHeight())))
        addView(TextView(this@HomeActivity).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(8))
        })
        addView(actionButton("En desarrollo", primary = false) {}.apply { isEnabled = false },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)))
    }

    private fun card(title: String): View {
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
                    contentDescription = title
                    setPadding(dp(8), dp(8), dp(8), dp(8))
                })
            } else {
                // Before an import there is no cover to show: the title's name stands in.
                addView(TextView(this@HomeActivity).apply {
                    text = title
                    setTextColor(GOLD)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 28f)
                    typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
                    gravity = Gravity.CENTER
                })
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(coverHeight())))
        card.addView(TextView(this).apply {
            text = title
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            typeface = Typeface.DEFAULT_BOLD
            gravity = Gravity.CENTER
            setPadding(0, dp(10), 0, dp(8))
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

    private fun selectZip() {
        val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/zip"
        }
        runCatching { startActivityForResult(intent, REQUEST_ZIP) }
            .onFailure { Toast.makeText(this, "No hay un selector de archivos disponible", Toast.LENGTH_LONG).show() }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_ZIP || resultCode != RESULT_OK) return
        val uri = data?.data ?: return
        val token = ++workerToken
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
                    Log.e(TAG, "import failed", it)
                    Toast.makeText(this, "Importación rechazada: " + (it.message ?: "paquete no válido"), Toast.LENGTH_LONG).show()
                }
                refresh()
            }
        }.start()
    }

    override fun onDestroy() {
        workerToken++
        super.onDestroy()
    }

    /** Cover height (dp): what the screen leaves under the title, name and buttons. */
    private fun coverHeight(): Int {
        val config = resources.configuration
        val byHeight = config.screenHeightDp - 250
        // Every title's card fits across the screen.
        val byWidth = ((config.screenWidthDp - 48) / GAMES.size - CARD_GAP - 24) * 2 / 3
        return minOf(byHeight, byWidth).coerceIn(100, 300)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val APP_TITLE = "Case Recomp"
        /** Titles shown; only [Game.ready] ones import a private package and play. */
        val GAMES = listOf(Game("Huntsville", ready = true), Game("Prime Suspects", ready = false), Game("Ravenhearst", ready = false))
        const val CARD_GAP = 16
        const val BETA_SEEN = "beta-notice-seen"
        const val ISSUES_URL = "https://github.com/Dr3amyBerry/Case-Recomp/issues"
        const val BETA_NOTICE = "Beta: puede tener errores. Si encuentras alguno, avísanos en GitHub (toca aquí). " +
            "Se entrega tal cual, sin garantías. No incluye ningún juego."
        const val REQUEST_ZIP = 7001
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
