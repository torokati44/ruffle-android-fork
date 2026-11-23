package rs.ruffle

/**
 * Display overrides (scale mode, alignment, letterbox) chosen from the player's display menu.
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
}
