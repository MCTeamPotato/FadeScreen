# 1.1.0
- Added a FadeScreen page to the video settings screen: Embeddium's on 1.20.1, Sodium's on 1.21.1.
  The master switch, the fade duration and the new low-resolution option can now be changed in-game
  instead of by editing `config/fadescreen-client.toml`. Changes are written back immediately.
- Added `lowResTransition` (default off). It renders the offscreen fade copy at GUI-scaled resolution
  instead of physical resolution, which saves a lot of VRAM on 4K displays. Text is slightly softer
  for the duration of the fade, which is why it stays opt-in.
- Swapping screens no longer re-renders the outgoing screen. Its last offscreen frame is kept and
  reused as the fade-out source, removing a render spike on every swap after the first.
- Fixed flickering when opening screens built on Modern UI (pause menu, ModernEmb's video settings).
  Those screens submit straight to the main framebuffer, so they are now skipped entirely instead of
  being composited on top of their own output - the longer the fade, the worse the flicker used to be.
- Fixed `skippedScreens` changes being ignored when one class name was replaced by another, which
  left the entry count unchanged.

# 1.0.0
- Initial release
