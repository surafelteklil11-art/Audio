# Video playback and six popups

Audio 1.0.320 uses the same Media3 player for fullscreen and floating playback. Open a local video and tap **Popup**, or long-press it in the Video list and choose **Play in popup**. The first use opens Android's **Display over other apps** permission screen. Return to Audio after allowing it.

Repeat with other videos to open up to six independent windows over Audio, the launcher, or other apps. Each window supports play/pause, seek, speed, mute, drag, resize and return to fullscreen. Tap the resize arrow to switch between small and large; drag it for continuous resizing. Tap the video to reveal controls. Controls hide while playing. Close individual windows with ×, or use the notification's Pause all / Close all actions. New videos start muted when another video's sound is already enabled; each window has its own sound toggle.

Fullscreen adds ten-second skip buttons and double-tap seeking, horizontal swipe seeking, left-side brightness and right-side volume gestures, fit/crop/stretch, rotation and control lock. Long-press a video in the library and choose **Play next** to queue the next item; the next button consumes that queue entry.

The six-slot limit includes a fullscreen player during transfer. Players retain position, speed, mute and pause state when their surface moves between fullscreen and popup. Fullscreen playback pauses when the Activity is backgrounded; popup playback is owned by a media-playback foreground service and continues outside Audio. Closing a window releases its decoder and frees its slot. The service does not restart videos after process death.

The limit is six windows, not a guarantee of six simultaneous high-resolution hardware decoders on every phone. Android documents that the actual concurrent codec capacity depends on available resources: https://developer.android.com/reference/android/media/MediaCodecInfo.CodecCapabilities#getMaxSupportedInstances(). Per-player decode failures show a retry message without closing other windows. Use smaller local videos if the device cannot decode all six at the selected resolution.

## Verification

`PopupVideoTest` runs on real Android emulator frameworks at API 33 and 35. It opens six actual AVC/AAC clips, verifies first frames after surface transfer and simultaneous playback, independent pause/speed/mute, rejection of a seventh player, resize/drag bounds, same-player fullscreen return, freeing/reusing a slot, Pause all / Close all, lifecycle recreation and denied overlay permission. Native screenshots join the existing device-preview artifacts. `PopupBoundsTest` checks six-window placement and portrait/landscape clamping on API 24, 33 and 35.

The small test fixture is a synthetic FFmpeg test pattern and sine tone, stored as Base64 to keep the fixture portable through text-based repository tooling. It is decoded only by the instrumentation tests and is absent from the app APK. No media from the user's reference video is bundled.
