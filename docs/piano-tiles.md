# Piano Tiles

Open the Audio drawer and choose **Piano Tiles · ፒያኖ**, directly below Checkers.

Six original piano arrangements run entirely offline. Choose Easy, Normal or Hard, listen to a short preview, then play. Tap dark tiles when their bottom edge reaches the cyan line. Hold long tiles until their tail reaches it. Chords require two fingers in different lanes. Keyboard controls are 1–4 or D/F/J/K.

A Perfect tap is worth 100 points and a Great tap 70. Completing a hold adds 50. Every ten consecutive notes raises the multiplier, capped at four. Three mistakes end an attempt. Scores and stars are saved separately for each song and difficulty; retrying cannot replace a better score. Three stars require completing every note, no mistakes and at least 90% Perfect judgments.

The game pauses when leaving the activity, receiving an audio focus interruption or unplugging headphones. A stalled rendering frame longer than 250 ms also pauses rather than charging unseen misses. Returning requires Resume. If a hold was interrupted, touch its glowing lane during the one-second frozen resume countdown. Rotation preserves the attempt through a ViewModel; best scores persist across process restarts. An unfinished attempt is not restored after process death.

SoundPool plays a locally generated PCM sample transposed to the arranged notes. Audio focus is requested only for previews or actual play, and abandoned on pause/exit. Audio's existing Media3 player handles its own focus; no changes to its playback service are required. Sound can be turned off to play silently. Speaker or wired headphones avoid Bluetooth output latency; the game does not calibrate Bluetooth delay.

Implementation lives in `PianoTilesActivity.kt` and the `piano` package. No new dependencies, downloaded songs, accounts, advertisements or purchases are required. The reference video informs the four-lane rhythm mechanics; all song sequences and interface artwork here are newly created for Audio.

Regression coverage includes every chart/difficulty completed perfectly, simultaneous pointers, long-note release and regrip, timing boundaries, score persistence, rotation, backgrounding, headphone disconnection, audio focus rejection/loss and the generated WAV format. CI also captures the library, ready screen and playing board using Android API 33/35 native graphics. Physical-device audio/touch latency still needs a device check.
