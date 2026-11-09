# Mutiny

A party game of hidden mutineers aboard a ship, for 5 to 10 players sharing **one phone**.

## How it plays

- Add 5 to 10 players, then deal the roles. Each player secretly flips a face-down card on the phone (both roles look
  identical from the back, and mutineers also see their fellow mutineers), hides it, and passes the phone on.
- The ship sets out on five voyages. For each one the captain picks a landing party. Everybody votes **secretly**, one
  after another, by passing the phone. All votes are shown together afterwards. A landing party is approved only by a
  strict majority (a tie rejects it), and the captaincy passes round the table.
- An approved landing party goes ashore. Each member secretly plays a **Loyal** or **Sabotage** card. The loyal crew
  can only play Loyal. The cards are shuffled and revealed face down first, so nobody can tell who played what.
- Three safe voyages: the loyal crew wins. Three sabotaged voyages, or five rejected landing parties in a row: the
  mutineers take the ship.
- With 7 or more players the 4th voyage needs two Sabotage cards.

| Players | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|
| Mutineers | 2 | 2 | 3 | 3 | 3 | 4 |
| Landing party sizes | 2 3 2 3 3 | 2 3 4 3 4 | 2 3 3 4* 4 | 3 4 4 5* 5 | 3 4 4 5* 5 | 3 4 4 5* 5 |

\* needs two Sabotage cards.

## Features

- English, Portuguese (*Motim*) and Spanish (*Motín*), with an in-app rules page.
- No internet, no permissions, no data collected.
- Window secure flag on by default, so roles and votes do not show up in screenshots or the recent-apps thumbnail.
- Interrupted games can be resumed from the setup screen.
- Sound effects and music made with ElevenLabs, with the same neutral sound for every secret choice so nothing leaks
  through audio.

## Sound and music

The sound effects and the music are generated with [ElevenLabs](https://elevenlabs.io) from the prompts in
`tools/audio.json`. `tools/audio.py` regenerates them into `app/src/main/res/raw/` (it needs an ElevenLabs API key in
`$ELEVENLABS_API_KEY` or `~/.config/elevenlabs/api_key`; paid generations are cached in `tools/audio-cache/`, which is
not in git, so only changed prompts are generated again). Effects are trimmed and levelled; music is made into a
seamless loop.

Music follows the game: a sea shanty in the menu and the deal, a tense creaking-ship underscore at the table, a
heartbeat below deck while the voyage cards are played, and silence for the final sting.

The 🎵 button on the first screen turns the music off. The sound effects stay on: they are part of the game.

## Build

Requires JDK 17 and the Android SDK (platform 36).

```
./gradlew testDebugUnitTest        # rules tests
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug -PallowScreenshots   # variant without the secure window flag, for screenshots
```

`assembleRelease` signs the APK with the Android debug key, which is fine for sideloading.

## Google Play

Google Play takes an App Bundle signed with the upload key:

```sh
./gradlew bundleRelease -Pplay    # → app/build/outputs/bundle/release/app-release.aab
```

`-Pplay` reads the key from these entries in `~/.gradle/gradle.properties`, which is outside the repo:

```properties
mutiny.upload.storeFile=/home/you/.android/mutiny-upload.jks
mutiny.upload.storePassword=...
mutiny.upload.keyAlias=upload
mutiny.upload.keyPassword=...
```

Google re-signs the app for the store (Play App Signing). If the upload key is lost, it can be reset in the Play
Console. The Play version and a sideloaded APK have different signatures, so switching from one to the other means
uninstalling first. Raise `versionCode` in `app/build.gradle.kts` for every upload.

The privacy policy for the store listing is [PRIVACY.md](PRIVACY.md).
