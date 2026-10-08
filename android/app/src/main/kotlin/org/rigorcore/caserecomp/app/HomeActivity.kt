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
    private lateinit var repository: PrivateDirectorRepository
    private lateinit var games: LinearLayout
    private var importing = false
    private var workerToken = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = PrivateDirectorRepository(this)
        games = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
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
            addView(games, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(column)
        hideSystemBars()
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
        for (title in GAMES) games.addView(card(title), LinearLayout.LayoutParams(dp(coverHeight() * 3 / 2 + 24), ViewGroup.LayoutParams.WRAP_CONTENT))
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
    private fun coverHeight() = (resources.configuration.screenHeightDp - 220).coerceIn(120, 300)

    private fun dp(value: Int) = (value * resources.displayMetrics.density + 0.5f).toInt()

    private companion object {
        const val APP_TITLE = "Case Recomp"
        /** Titles this engine build is verified against; each imports its own private package. */
        val GAMES = listOf("Huntsville")
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
