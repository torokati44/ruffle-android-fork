package rs.ruffle

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION_CODES
import android.os.Bundle
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowManager
import android.widget.Button
import android.widget.PopupMenu
import android.widget.Toast
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.androidgamesdk.GameActivity
import java.io.DataInputStream
import java.io.File
import java.io.IOException

class PlayerActivity : GameActivity() {
    @Suppress("unused")
    // Used by Rust
    private val swfBytes: ByteArray?
        get() {
            val uri = intent.data
            if (uri?.scheme == "content") {
                try {
                    contentResolver.openInputStream(uri).use { inputStream ->
                        if (inputStream == null) {
                            return null
                        }
                        val bytes = ByteArray(inputStream.available())
                        val dataInputStream = DataInputStream(inputStream)
                        dataInputStream.readFully(bytes)
                        return bytes
                    }
                } catch (ignored: IOException) {
                }
            }
            return null
        }

    @Suppress("unused")
    // Used by Rust
    private val swfUri: String?
        get() {
            return intent.dataString
        }

    @Suppress("unused")
    // Used by Rust
    private val traceOutput: String?
        get() {
            return intent.getStringExtra("traceOutput")
        }

    @Suppress("unused")
    // Used by Rust
    private fun navigateToUrl(url: String?) {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    /** Key under which this content's display settings are saved; see [DisplaySettings.contentKey]. */
    private val contentKey: String? by lazy { DisplaySettings.contentKey(this, intent.data) }

    @Suppress("unused")
    // Used by Rust
    private val displaySettings: IntArray
        get() = contentKey?.let { DisplaySettings.load(this, it) }
            ?: IntArray(5) { DisplaySettings.UNSET }

    private var loc = IntArray(2)

    @Suppress("unused")
    // Handle of an EventLoopProxy over in rust-land
    private val eventLoopHandle: Long = 0

    @Suppress("unused")
    // Used by Rust
    private val locInWindow: IntArray
        get() {
            mSurfaceView.getLocationInWindow(loc)
            return loc
        }

    @Suppress("unused")
    // Used by Rust
    private val surfaceWidth: Int
        get() = mSurfaceView.width

    @Suppress("unused")
    // Used by Rust
    private val surfaceHeight: Int
        get() = mSurfaceView.height

    private external fun keydown(keyTag: String)
    private external fun keyup(keyTag: String)
    private external fun requestContextMenu()
    private external fun runContextMenuCallback(index: Int)
    private external fun clearContextMenu()
    private external fun requestDisplayMenu()
    private external fun setDisplayOption(kind: Int, value: Int)

    @Suppress("unused")
    // Used by Rust
    private fun showContextMenu(items: Array<String>) {
        runOnUiThread {
            val popup = PopupMenu(this, findViewById(R.id.button_cm))
            val menu = popup.menu
            if (Build.VERSION.SDK_INT >= VERSION_CODES.P) {
                menu.setGroupDividerEnabled(true)
            }
            var group = 1
            for (i in items.indices) {
                val elements = items[i].split(" ".toRegex(), limit = 4).toTypedArray()
                val enabled = elements[0].toBoolean()
                val separatorBefore = elements[1].toBoolean()
                val checked = elements[2].toBoolean()
                val caption = elements[3]
                if (separatorBefore) group += 1
                val item = menu.add(group, i, Menu.NONE, caption)
                item.setEnabled(enabled)
                if (checked) {
                    item.setCheckable(true)
                    item.setChecked(true)
                }
            }
            val exitItemId: Int = items.size
            menu.add(group, exitItemId, Menu.NONE, "Exit")
            popup.setOnMenuItemClickListener { item: MenuItem ->
                if (item.itemId == exitItemId) {
                    finish()
                } else {
                    runContextMenuCallback(item.itemId)
                }
                true
            }
            popup.setOnDismissListener { clearContextMenu() }
            popup.show()
        }
    }

    @Suppress("unused")
    // Used by Rust
    private fun showDisplayMenu(
        scaleMode: Int,
        forceScaleMode: Boolean,
        align: Int,
        forceAlign: Boolean,
        letterbox: Boolean
    ) {
        runOnUiThread {
            val popup = PopupMenu(this, findViewById(R.id.button_dm))
            val menu = popup.menu

            val scaleMenu = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.scale_mode)
            SCALE_MODES.forEachIndexed { i, (_, label) ->
                scaleMenu.add(GROUP_SCALE_MODE, ITEM_SCALE_MODE_BASE + i, Menu.NONE, label)
            }
            scaleMenu.setGroupCheckable(GROUP_SCALE_MODE, true, true)
            val scaleIndex = SCALE_MODES.indexOfFirst { it.first == scaleMode }
            if (scaleIndex >= 0) {
                scaleMenu.findItem(ITEM_SCALE_MODE_BASE + scaleIndex).isChecked = true
            }
            menu.add(Menu.NONE, ITEM_FORCE_SCALE_MODE, Menu.NONE, R.string.force_scale_mode).apply {
                isCheckable = true
                isChecked = forceScaleMode
            }

            // Alignment has no effect when the content is stretched to fill the screen.
            val alignEnabled = scaleMode != DisplaySettings.SCALE_EXACT_FIT
            val alignMenu = menu.addSubMenu(Menu.NONE, Menu.NONE, Menu.NONE, R.string.alignment)
            alignMenu.item.isEnabled = alignEnabled
            ALIGNMENTS.forEachIndexed { i, (_, label) ->
                alignMenu.add(GROUP_ALIGN, ITEM_ALIGN_BASE + i, Menu.NONE, label)
            }
            alignMenu.setGroupCheckable(GROUP_ALIGN, true, true)
            val alignIndex = ALIGNMENTS.indexOfFirst { it.first == align }
            if (alignIndex >= 0) {
                alignMenu.findItem(ITEM_ALIGN_BASE + alignIndex).isChecked = true
            }
            menu.add(Menu.NONE, ITEM_FORCE_ALIGN, Menu.NONE, R.string.force_alignment).apply {
                isCheckable = true
                isChecked = forceAlign
                isEnabled = alignEnabled
            }

            menu.add(Menu.NONE, ITEM_LETTERBOX, Menu.NONE, R.string.letterbox).apply {
                isCheckable = true
                isChecked = letterbox
                // Ruffle only draws the letterbox for centered "show all" content.
                isEnabled = scaleMode == DisplaySettings.SCALE_SHOW_ALL &&
                    align == DisplaySettings.ALIGN_CENTER
            }

            menu.add(Menu.NONE, ITEM_RESET, Menu.NONE, R.string.reset_display_settings)

            popup.setOnMenuItemClickListener { item ->
                onDisplayMenuItemClick(item, scaleMode, align)
            }
            popup.show()
        }
    }

    private fun onDisplayMenuItemClick(item: MenuItem, scaleMode: Int, align: Int): Boolean {
        val key = contentKey
        val id = item.itemId
        when {
            id in ITEM_SCALE_MODE_BASE until ITEM_SCALE_MODE_BASE + SCALE_MODES.size -> {
                // Picking a mode also forces it, or the content would likely just change it back.
                val mode = SCALE_MODES[id - ITEM_SCALE_MODE_BASE].first
                setDisplayOption(DisplaySettings.OPTION_SCALE_MODE, mode)
                key?.let { DisplaySettings.saveScaleMode(this, it, mode, force = true) }
            }
            id == ITEM_FORCE_SCALE_MODE -> {
                val force = !item.isChecked
                setDisplayOption(DisplaySettings.OPTION_FORCE_SCALE_MODE, if (force) 1 else 0)
                key?.let { DisplaySettings.saveScaleMode(this, it, scaleMode, force) }
            }
            id in ITEM_ALIGN_BASE until ITEM_ALIGN_BASE + ALIGNMENTS.size -> {
                val newAlign = ALIGNMENTS[id - ITEM_ALIGN_BASE].first
                setDisplayOption(DisplaySettings.OPTION_ALIGN, newAlign)
                key?.let { DisplaySettings.saveAlign(this, it, newAlign, force = true) }
            }
            id == ITEM_FORCE_ALIGN -> {
                val force = !item.isChecked
                setDisplayOption(DisplaySettings.OPTION_FORCE_ALIGN, if (force) 1 else 0)
                key?.let { DisplaySettings.saveAlign(this, it, align, force) }
            }
            id == ITEM_LETTERBOX -> {
                val letterbox = !item.isChecked
                setDisplayOption(DisplaySettings.OPTION_LETTERBOX, if (letterbox) 1 else 0)
                key?.let { DisplaySettings.saveLetterbox(this, it, letterbox) }
            }
            id == ITEM_RESET -> {
                setDisplayOption(DisplaySettings.OPTION_RESET, 0)
                key?.let { DisplaySettings.reset(this, it) }
                Toast.makeText(this, R.string.display_settings_reset, Toast.LENGTH_LONG).show()
            }
            // Submenu headers, which just open their submenu.
            else -> return false
        }
        return true
    }

    @Suppress("unused")
    // Used by Rust
    private fun getAndroidDataStorageDir(): String {
        // TODO It can also be placed in an external storage path in the future to share archived content
        val storageDirPath = "${filesDir.absolutePath}/ruffle/shared_objects"
        val storageDir = File(storageDirPath)
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        return storageDirPath
    }

    override fun onCreateSurfaceView() {
        val inflater = layoutInflater

        @SuppressLint("InflateParams")
        val layout = inflater.inflate(R.layout.keyboard, null) as ConstraintLayout

        contentViewId = View.generateViewId()
        layout.id = contentViewId
        setContentView(layout)
        mSurfaceView = InputEnabledSurfaceView(this)

        mSurfaceView.contentDescription = "Ruffle Player"

        val placeholder = findViewById<View>(R.id.placeholder)
        val pars = placeholder.layoutParams as ConstraintLayout.LayoutParams
        val parent = placeholder.parent as ViewGroup
        val index = parent.indexOfChild(placeholder)
        parent.removeView(placeholder)
        parent.addView(mSurfaceView, index)
        mSurfaceView.setLayoutParams(pars)
        val keys = gatherAllDescendantsOfType<Button>(
            layout.getViewById(R.id.keyboard),
            Button::class.java
        )
        for (b in keys) {
            b.setOnTouchListener { view: View, motionEvent: MotionEvent ->
                val tag = view.tag as String
                if (motionEvent.action == MotionEvent.ACTION_DOWN) keydown(tag)
                if (motionEvent.action == MotionEvent.ACTION_UP) keyup(tag)
                view.performClick()
                false
            }
        }
        layout.findViewById<View>(R.id.button_kb).setOnClickListener {
            val keyboard = layout.getViewById(R.id.keyboard)
            if (keyboard.visibility == View.VISIBLE) {
                keyboard.visibility = View.GONE
            } else {
                keyboard.visibility = View.VISIBLE
            }
        }
        layout.findViewById<View>(R.id.button_cm)
            .setOnClickListener { requestContextMenu() }
        layout.findViewById<View>(R.id.button_dm)
            .setOnClickListener { requestDisplayMenu() }
        layout.requestLayout()
        layout.requestFocus()
        mSurfaceView.holder.addCallback(this)
        ViewCompat.setOnApplyWindowInsetsListener(mSurfaceView, this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val keyboard = findViewById<View>(R.id.keyboard)
        val isLandscape = newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE
        keyboard.visibility = if (isLandscape) View.GONE else View.VISIBLE
    }

    private fun hideSystemUI() {
        // This will put the game behind any cutouts and waterfalls on devices which have
        // them, so the corresponding insets will be non-zero.
        if (Build.VERSION.SDK_INT >= VERSION_CODES.R) {
            window.attributes.layoutInDisplayCutoutMode =
                WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        }
        // From API 30 onwards, this is the recommended way to hide the system UI, rather than
        // using View.setSystemUiVisibility.
        val decorView = window.decorView
        val controller = WindowInsetsControllerCompat(
            window,
            decorView
        )
        controller.hide(WindowInsetsCompat.Type.systemBars())
        controller.hide(WindowInsetsCompat.Type.displayCutout())
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        nativeInit { message ->
            Log.e("ruffle", "Handling panic: $message")
            startActivity(
                Intent(this, PanicActivity::class.java).apply {
                    putExtra("message", message)
                }
            )
        }
        // When true, the app will fit inside any system UI windows.
        // When false, we render behind any system UI windows.
        WindowCompat.setDecorFitsSystemWindows(window, false)
        hideSystemUI()
        // You can set IME fields here or in native code using GameActivity_setImeEditorInfoFields.
        // We set the fields in native_engine.cpp.
        // super.setImeEditorInfoFields(InputType.TYPE_CLASS_TEXT,
        //     IME_ACTION_NONE, IME_FLAG_NO_FULLSCREEN );
        requestNoStatusBarFeature()
        supportActionBar?.hide()
        super.onCreate(savedInstanceState)
    }

    // Used by Rust
    @Suppress("unused")
    val isGooglePlayGames: Boolean
        get() {
            val pm = packageManager
            return pm.hasSystemFeature("com.google.android.play.feature.HPE_EXPERIENCE")
        }

    private fun requestNoStatusBarFeature() {
        // Hiding the status bar this way makes it see through when pulled down
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        WindowInsetsControllerCompat(
            window,
            mSurfaceView
        ).hide(WindowInsetsCompat.Type.statusBars())
    }

    companion object {
        init {
            // load the native activity
            System.loadLibrary("ruffle_android")
        }

        @JvmStatic
        private external fun nativeInit(crashCallback: CrashCallback)

        private const val GROUP_SCALE_MODE = 1
        private const val GROUP_ALIGN = 2
        private const val ITEM_FORCE_SCALE_MODE = 1
        private const val ITEM_FORCE_ALIGN = 2
        private const val ITEM_LETTERBOX = 3
        private const val ITEM_RESET = 4
        private const val ITEM_SCALE_MODE_BASE = 100
        private const val ITEM_ALIGN_BASE = 200

        /** Scale mode values with their menu labels, in menu order. */
        private val SCALE_MODES = listOf(
            DisplaySettings.SCALE_NO_SCALE to R.string.scale_mode_no_scale,
            DisplaySettings.SCALE_SHOW_ALL to R.string.scale_mode_show_all,
            DisplaySettings.SCALE_EXACT_FIT to R.string.scale_mode_exact_fit,
            DisplaySettings.SCALE_NO_BORDER to R.string.scale_mode_no_border
        )

        /** Alignment flag combinations with their menu labels, in menu order. */
        private val ALIGNMENTS = listOf(
            DisplaySettings.ALIGN_CENTER to R.string.align_center,
            DisplaySettings.ALIGN_TOP to R.string.align_top,
            DisplaySettings.ALIGN_BOTTOM to R.string.align_bottom,
            DisplaySettings.ALIGN_LEFT to R.string.align_left,
            DisplaySettings.ALIGN_RIGHT to R.string.align_right,
            (DisplaySettings.ALIGN_TOP or DisplaySettings.ALIGN_LEFT) to R.string.align_top_left,
            (DisplaySettings.ALIGN_TOP or DisplaySettings.ALIGN_RIGHT) to R.string.align_top_right,
            (DisplaySettings.ALIGN_BOTTOM or DisplaySettings.ALIGN_LEFT) to
                R.string.align_bottom_left,
            (DisplaySettings.ALIGN_BOTTOM or DisplaySettings.ALIGN_RIGHT) to
                R.string.align_bottom_right
        )

        private fun <T> gatherAllDescendantsOfType(v: View, t: Class<*>): List<T> {
            val result: MutableList<T> = ArrayList()
            @Suppress("UNCHECKED_CAST")
            if (t.isInstance(v)) result.add(v as T)
            if (v is ViewGroup) {
                for (i in 0 until v.childCount) {
                    result.addAll(gatherAllDescendantsOfType(v.getChildAt(i), t))
                }
            }
            return result
        }
    }

    fun interface CrashCallback {
        fun onCrash(message: String)
    }
}
