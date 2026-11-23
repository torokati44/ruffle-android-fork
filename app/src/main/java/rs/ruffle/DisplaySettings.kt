package rs.ruffle

import android.content.Context
import android.net.Uri
import androidx.core.content.edit
import java.io.IOException
import java.security.MessageDigest

/**
 * Display overrides (scale mode, alignment, letterbox) chosen from the player's display menu,
 * persisted per content.
 *
 * The integer encodings are shared with `src/display.rs`; keep them in sync.
 */
object DisplaySettings {
    const val SCALE_NO_SCALE = 0
    const val SCALE_SHOW_ALL = 1
    const val SCALE_EXACT_FIT = 2
    const val SCALE_NO_BORDER = 3

    const val ALIGN_CENTER = 0
    const val ALIGN_TOP = 1
    const val ALIGN_BOTTOM = 2
    const val ALIGN_LEFT = 4
    const val ALIGN_RIGHT = 8

    const val OPTION_SCALE_MODE = 0
    const val OPTION_FORCE_SCALE_MODE = 1
    const val OPTION_ALIGN = 2
    const val OPTION_FORCE_ALIGN = 3
    const val OPTION_LETTERBOX = 4
    const val OPTION_RESET = 5

    /** Marks a field in [load]'s result that has no saved value. */
    const val UNSET = -1

    private const val PREFS_NAME = "display_settings"
    private const val SCALE_MODE = "scaleMode"
    private const val FORCE_SCALE_MODE = "forceScaleMode"
    private const val ALIGN = "align"
    private const val FORCE_ALIGN = "forceAlign"
    private const val LETTERBOX = "letterbox"

    /** Order of the array returned by [load], as read by `src/display.rs`. */
    private val FIELDS = listOf(SCALE_MODE, FORCE_SCALE_MODE, ALIGN, FORCE_ALIGN, LETTERBOX)

    private fun prefs(context: Context) =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private fun Boolean.toInt() = if (this) 1 else 0

    /**
     * Identifies the content to store settings for: the SHA-256 of the SWF for local files,
     * or of the URL for content that is fetched over the network (which only Rust ever downloads).
     */
    fun contentKey(context: Context, uri: Uri?): String? {
        if (uri == null) return null
        val digest = MessageDigest.getInstance("SHA-256")
        if (uri.scheme == "content") {
            try {
                val input = context.contentResolver.openInputStream(uri) ?: return null
                input.use {
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = it.read(buffer)
                        if (read < 0) break
                        digest.update(buffer, 0, read)
                    }
                }
            } catch (ignored: IOException) {
                return null
            }
            return "swf:" + digest.digest().toHex()
        }
        digest.update(uri.toString().toByteArray())
        return "url:" + digest.digest().toHex()
    }

    private fun ByteArray.toHex() = joinToString("") { "%02x".format(it) }

    /** Returns `[scaleMode, forceScaleMode, align, forceAlign, letterbox]`, [UNSET] where nothing is saved. */
    fun load(context: Context, contentKey: String): IntArray {
        val prefs = prefs(context)
        return FIELDS.map { prefs.getInt("$contentKey/$it", UNSET) }.toIntArray()
    }

    fun saveScaleMode(context: Context, contentKey: String, scaleMode: Int, force: Boolean) {
        prefs(context).edit {
            putInt("$contentKey/$SCALE_MODE", scaleMode)
            putInt("$contentKey/$FORCE_SCALE_MODE", force.toInt())
        }
    }

    fun saveAlign(context: Context, contentKey: String, align: Int, force: Boolean) {
        prefs(context).edit {
            putInt("$contentKey/$ALIGN", align)
            putInt("$contentKey/$FORCE_ALIGN", force.toInt())
        }
    }

    fun saveLetterbox(context: Context, contentKey: String, letterbox: Boolean) {
        prefs(context).edit { putInt("$contentKey/$LETTERBOX", letterbox.toInt()) }
    }

    fun reset(context: Context, contentKey: String) {
        prefs(context).edit { FIELDS.forEach { remove("$contentKey/$it") } }
    }

    fun clearAll(context: Context) {
        prefs(context).edit { clear() }
    }
}
