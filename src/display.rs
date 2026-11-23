//! Display (scale mode / alignment / letterbox) options that the user can override.
//!
//! The integer encodings here are shared with `PlayerActivity.kt` and `DisplaySettings.kt`;
//! keep them in sync.

use ruffle_core::config::Letterbox;
use ruffle_core::{Player, PlayerBuilder, StageAlign, StageScaleMode};
use std::convert::TryFrom;

pub fn scale_mode_to_int(mode: StageScaleMode) -> i32 {
    match mode {
        StageScaleMode::NoScale => 0,
        StageScaleMode::ShowAll => 1,
        StageScaleMode::ExactFit => 2,
        StageScaleMode::NoBorder => 3,
    }
}

pub fn scale_mode_from_int(value: i32) -> Option<StageScaleMode> {
    match value {
        0 => Some(StageScaleMode::NoScale),
        1 => Some(StageScaleMode::ShowAll),
        2 => Some(StageScaleMode::ExactFit),
        3 => Some(StageScaleMode::NoBorder),
        _ => None,
    }
}

pub fn align_to_int(align: StageAlign) -> i32 {
    align.bits() as i32
}

pub fn align_from_int(value: i32) -> Option<StageAlign> {
    u8::try_from(value).ok().map(StageAlign::from_bits_truncate)
}

/// A single change requested from the display menu.
pub enum DisplayOption {
    /// Set the scale mode, and force it so the movie can't change it back.
    ScaleMode(StageScaleMode),
    ForceScaleMode(bool),
    /// Set the alignment, and force it so the movie can't change it back.
    Align(StageAlign),
    ForceAlign(bool),
    Letterbox(bool),
    /// Stop forcing anything and restore the default letterbox.
    /// The movie's own scale mode/alignment only come back once it sets them again (e.g. on restart).
    Reset,
}

impl DisplayOption {
    /// Decodes the `(kind, value)` pair sent by `PlayerActivity.setDisplayOption`.
    pub fn from_ints(kind: i32, value: i32) -> Option<Self> {
        Some(match kind {
            0 => Self::ScaleMode(scale_mode_from_int(value)?),
            1 => Self::ForceScaleMode(value != 0),
            2 => Self::Align(align_from_int(value)?),
            3 => Self::ForceAlign(value != 0),
            4 => Self::Letterbox(value != 0),
            5 => Self::Reset,
            _ => return None,
        })
    }
}

pub const DEFAULT_LETTERBOX: bool = true;

fn letterbox_from_bool(on: bool) -> Letterbox {
    if on {
        Letterbox::On
    } else {
        Letterbox::Off
    }
}

/// The current display state, as shown in the menu.
pub struct DisplayState {
    pub scale_mode: StageScaleMode,
    pub forced_scale_mode: bool,
    pub align: StageAlign,
    pub forced_align: bool,
    pub letterbox: bool,
}

pub fn current_state(player: &mut Player) -> DisplayState {
    player.mutate_with_update_context(|context| {
        let stage = context.stage;
        DisplayState {
            scale_mode: stage.scale_mode(),
            forced_scale_mode: stage.forced_scale_mode(),
            align: stage.align(),
            forced_align: stage.forced_align(),
            letterbox: stage.letterbox() == Letterbox::On,
        }
    })
}

pub fn apply(player: &mut Player, option: DisplayOption) {
    player.mutate_with_update_context(|context| {
        let stage = context.stage;
        match option {
            DisplayOption::ScaleMode(mode) => {
                // `false`: don't respect an existing force, we are the ones forcing.
                stage.set_scale_mode(context, mode, false);
                stage.set_forced_scale_mode(true);
            }
            DisplayOption::ForceScaleMode(force) => stage.set_forced_scale_mode(force),
            DisplayOption::Align(align) => {
                // `set_align` silently does nothing while the alignment is forced.
                stage.set_forced_align(false);
                stage.set_align(context, align);
                stage.set_forced_align(true);
            }
            DisplayOption::ForceAlign(force) => stage.set_forced_align(force),
            DisplayOption::Letterbox(on) => {
                stage.set_letterbox(letterbox_from_bool(on));
                // The letterbox affects the visible stage bounds computed here.
                stage.build_matrices(context);
            }
            DisplayOption::Reset => {
                stage.set_forced_scale_mode(false);
                stage.set_forced_align(false);
                stage.set_letterbox(letterbox_from_bool(DEFAULT_LETTERBOX));
                stage.build_matrices(context);
            }
        }
        *context.needs_render = true;
    });
}

/// The settings saved for the current content by `DisplaySettings.kt`, applied at startup.
#[derive(Default)]
pub struct SavedSettings {
    scale_mode: Option<(StageScaleMode, bool)>,
    align: Option<(StageAlign, bool)>,
    letterbox: Option<bool>,
}

impl SavedSettings {
    /// Decodes `[scaleMode, forceScaleMode, align, forceAlign, letterbox]`, where `-1` means unset.
    pub fn from_ints(values: &[i32]) -> Self {
        let get = |i: usize| values.get(i).copied().unwrap_or(-1);
        Self {
            scale_mode: scale_mode_from_int(get(0)).map(|mode| (mode, get(1) == 1)),
            align: if get(2) >= 0 {
                align_from_int(get(2)).map(|align| (align, get(3) == 1))
            } else {
                None
            },
            letterbox: match get(4) {
                0 => Some(false),
                1 => Some(true),
                _ => None,
            },
        }
    }

    pub fn apply_to_builder(&self, mut builder: PlayerBuilder) -> PlayerBuilder {
        if let Some((mode, force)) = self.scale_mode {
            builder = builder.with_scale_mode(mode, force);
        }
        if let Some((align, force)) = self.align {
            builder = builder.with_align(align, force);
        }
        builder
    }

    pub fn letterbox(&self) -> Letterbox {
        letterbox_from_bool(self.letterbox.unwrap_or(DEFAULT_LETTERBOX))
    }
}
