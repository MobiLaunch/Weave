# Weave — a 3D web of thoughts (Android)

A note-taking app where your notes don't sit in a list. Each one becomes a glowing thought
in a 3D web that keeps growing as you add to it. It's written in Kotlin with Jetpack Compose and
Material 3. On Pixel phones and other Android 12+ devices it picks up your wallpaper
colors through Material You.

## What it does

| | |
|---|---|
| **Web of thoughts** | Every note is a node in 3D space. New notes branch off the thought you last focused (or your latest one), and faint silk strands tie nearby thoughts together. |
| **Timestamped & editable** | Each note records when it was created and last edited. Tap a thought to read, edit or delete it. Deleting one reconnects its branches to the thought it grew from. |
| **Explore in 3D** | Drag to spin the web freely in any direction. Pinch to zoom, twist two fingers to roll it, and fling it to keep it turning. It slowly drifts when left alone. |
| **Weave animation** | When you save a new note, the card shrinks and flies into the web while the camera pulls back. The thought springs out from its parent, strands reach over from its neighbours, and a small spider web spins up around it with a soft chime. |
| **Snap to move** | Long-press a thought to pick it up (along with its branches), then drag it. A dashed line previews where it will attach. Let go and the old strand snaps and recoils, the thought springs into place and the new strand twangs. A shock wave runs through the web, with a snap sound and haptics. |
| **A world for every thought** | "New thought" grows into a full-screen editor, and tapping an orb opens the same editor out of that orb. At the top sits a little planet that reads the mood of what you type, on device, and reshapes itself as you go: 🔥 angry becomes a world on fire (volcanoes, flames, embers), 🌧️ sad becomes a stormy ocean (rain, lightning, a lighthouse), 😊 happy becomes a meadow (hopping sheep, trees, birds, a sun), 😌 calm becomes a lagoon (palms, a surfacing whale, clouds, a moon), 🤔 curious becomes a mushroom forest with fireflies, and ✨ inspired becomes a crystal world with an aurora and rings. The planet grows as you write. Tap 🪄 to let it sense your mood, or pick one yourself. |
| **Solar-system web** | When you save, the planet shrinks and flies into the web while the camera pulls back. Thoughts with a mood show as tiny versions of their worlds. Each category, plus the uncategorized thoughts, has a glowing sun with faint orbits through its thoughts, so the web reads as clustered solar systems joined by strands. |
| **Living orbs** | Every orb breathes, shimmers and slowly swirls. Its mood gives it a personality: 😊 joyful orbs bounce with sparkles, 😌 calm ones breathe slowly, 🤔 curious ones wobble, ✨ inspired ones twinkle with star glints, 🔥 fired-up ones flicker and throw embers, and 🌧️ blue ones sink gently and drip. |
| **Moods & categories** | Pick a mood and a category (Ideas, Personal, Work, Dreams, To-do, Memories) for any thought. Categories colour the orbs. The filter chips under the title light up one category, dim the rest and fly the camera over to it. New thoughts inherit the category you're viewing, or their parent's. |
| **Micro-interactions** | Orbs pop when tapped. The snap target swells and ticks as you drag past it. Pinching bumps at the zoom limits, and double-tapping empty space recenters. Buttons squish when pressed, mood emojis hop, and the thought count rolls. |
| **All thoughts** | The list button shows every thought with its timestamps. Pick one to fly to it. |

Everything stays on the device (`files/weave.json`). The sounds are synthesized in code,
so there are no audio assets.

## Install on your phone

Open **https://github.com/MobiLaunch/Weave/releases/latest/download/weave.apk** in Chrome on the
phone (while the repo is private you need to be signed in to GitHub in that browser). When the
download finishes, tap it. The first time, Android asks you to allow installs from Chrome. Turn
that on, go back and tap **Install**.

## Build & run

Requirements: Android Studio Ladybug or newer (or JDK 17 plus the Android SDK with API 35).

```bash
./gradlew installDebug        # onto a connected device / emulator
./gradlew assembleRelease     # minified APK in app/build/outputs/apk/release
```

The release build is signed with the debug key so it installs directly. Add a real signing
config before publishing.

CI (`.github/workflows/android.yml`) builds a debug APK on every push and PR
and uploads it as the `weave-debug-apk` artifact. Pushes to `main` also publish it as a
GitHub Release (`weave.apk`).

## Code map

```
app/src/main/java/com/mobilaunch/weave/
├── MainActivity.kt          edge-to-edge host
├── WeaveViewModel.kt
├── data/                    Note model + JSON-backed repository
├── web/
│   ├── Vec3.kt, Camera.kt   3D math and the orbit camera (perspective projection)
│   ├── WebLayout.kt         where new thoughts grow, silk links, subtrees
│   ├── WebScene.kt          per-frame state, animations (spawn, snap, ripple) and rendering
│   └── WebCanvas.kt         Compose canvas + gestures
├── audio/WebSounds.kt       synthesized snap + weave sounds
└── ui/                      main screen, note editor, thought list, Material You theme
```
