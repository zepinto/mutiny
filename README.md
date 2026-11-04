# Resistance

An Android pass-the-phone version of the social deduction game *The Resistance*, for 5 to 10 players sharing **one phone**.

> Unofficial fan implementation of a well-known party game. Not affiliated with, endorsed by or connected to Indie Boards & Cards. No artwork, text or names from the original product are used.

## How it plays

- Add 5 to 10 players, then deal the roles. Each player secretly flips a face-down card on the phone (both roles look identical from the back, and spies also see their fellow spies), hides it, and passes the phone on.
- Each round the leader picks a team. Everybody votes **secretly**, one after another, by passing the phone. All votes are shown together afterwards. A team is approved only by a strict majority (a tie rejects it).
- An approved team goes on the mission. Each member secretly plays a **Success** or **Fail** card. Resistance members can only play Success. The cards are shuffled and revealed face down first, so nobody can tell who played what.
- Three successful missions: the Resistance wins. Three failed missions, or five rejected teams in a row: the Spies win.
- With 7 or more players the 4th mission needs two Fail cards to fail.

| Players | 5 | 6 | 7 | 8 | 9 | 10 |
|---|---|---|---|---|---|---|
| Spies | 2 | 2 | 3 | 3 | 3 | 4 |
| Mission team sizes | 2 3 2 3 3 | 2 3 4 3 4 | 2 3 3 4* 4 | 3 4 4 5* 5 | 3 4 4 5* 5 | 3 4 4 5* 5 |

\* needs two Fail cards.

## Features

- English, Portuguese and Spanish, with an in-app rules page.
- No internet, no permissions, no data collected.
- Window secure flag on by default, so roles and votes do not show up in screenshots or the recent-apps thumbnail.
- Interrupted games can be resumed from the setup screen.
- Sound effects and music made with ElevenLabs, with the same neutral sound for every secret choice so nothing leaks through audio.

## Sound and music

The sound effects and the music were generated with [ElevenLabs](https://elevenlabs.io) from the prompts in
`tools/audio.json`. `tools/audio.py` regenerates them into `app/src/main/res/raw/` (it needs an ElevenLabs API key in
`$ELEVENLABS_API_KEY` or `~/.config/elevenlabs/api_key`; paid generations are cached in `tools/audio-cache/`, which is
not in git, so only changed prompts are generated again). Effects are trimmed and levelled; music is made into a
seamless loop.

Music follows the game: spy lounge in the menu and the deal, a thriller underscore at the table, a heartbeat while
the mission cards are played, and silence for the final sting.

The 🎵 button on the first screen turns the music off. The sound effects stay on: they are part of the game.

## Build

Requires JDK 17 and the Android SDK (platform 35).

```
./gradlew testDebugUnitTest        # rules tests
./gradlew assembleRelease          # app/build/outputs/apk/release/app-release.apk
./gradlew assembleDebug -PallowScreenshots   # variant without the secure window flag, for screenshots
```

The release build is signed with the debug key, which is fine for sideloading.
