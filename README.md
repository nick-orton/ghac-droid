# ghac for Android

An Android port of [ghac](https://github.com/nick-orton/ghac), the Great Home
Audio Controller. It controls the same two backends over raw TCP:

- **Music Player Daemon (MPD)** — library browsing, queue, playback
- **SnapCast** — synchronised multi-room audio and per-room volume

The app is a remote control only. It plays no audio itself.

## Requirements

- A running MPD instance
- A running SnapCast server
- Android 8.0 (API 26) or later

## Building

```sh
./gradlew assembleDebug          # debug APK
./gradlew testDebugUnitTest      # unit + loopback tests
```

The build needs a JDK 17+ and an Android SDK with `compileSdk 37`. Point
`local.properties` at your SDK:

```properties
sdk.dir=/path/to/android-sdk
```

To put the app on a phone — release signing, installing over USB or Wi-Fi with
`adb`, or copying the APK across — see **[INSTALLING.md](INSTALLING.md)**.

## Configuration

The TUI reads `~/.config/.ghacrc`. There is no equivalent place for a dotfile on
a phone, so the same four values live on the **Settings** tab and are persisted
with DataStore. On first launch the app opens Settings automatically.

| Field | Default |
| ----- | ------- |
| MPD host | — |
| MPD port | 6600 |
| SnapCast host | — |
| SnapCast port | 1705 |

Saving is what triggers connection: the app watches the stored settings and
redials both backends whenever they change.

## Screens

**Volume** — one row per SnapCast client, with a slider and a mute toggle.
Offline clients stay listed but are greyed out and disabled. Unmuted rows take
the theme's accent colour and muted rows a contrasting warm tone, filling the
TUI's `volume_unmuted` / `volume_muted` theme roles.

**Queue** — the MPD play queue. Tap to play, long-press to start a selection,
and use the contextual bar to remove or reorder. The list follows the playing
song.

**Library** — browse the MPD library. Tap a folder to descend, use the system
back gesture to go up, tap `+` to enqueue. Files already in the queue are
ticked. Long-press to multi-select for a bulk enqueue.

**Settings** — server addresses, live connection status, and the colour theme
(Turquoise, Nord, Gruvbox, Dracula, Solarized, Catppuccin, Monochrome).

## How this differs from the TUI

The keyboard is the thing that does not survive the port. `h`/`l` to nudge
volume, `space` to select, `gg`/`G`, and `f<letter>` have no touch equivalent
worth inventing, so they are replaced by direct manipulation. Three behavioural
differences are worth calling out:

- **A lost connection is not fatal.** The desktop version prints the error and
  quits. A phone changes networks constantly, so both repositories reconnect
  with exponential backoff and the screen shows an inline banner meanwhile,
  keeping the last known state visible.
- **Connections follow the visible lifecycle.** Sockets open in `onStart` and
  close in `onStop`. There is nothing to keep alive in the background, and a
  parked MPD `idle` connection would only get dropped by the router or the
  platform.
- **The app is always dark.** All eight themes in the original `themes.toml`
  assume a dark terminal; there is no light-mode palette to derive. The app's
  themes are all dark to match: Turquoise is the TUI's default, and the rest
  follow the palettes of the editor themes they are named after.

## Not yet ported

Renaming SnapCast clients (the repository plumbing exists, the UI does not), MPD
library update, and the bulk-edit confirmation prompts.

## Architecture

```
data/mpd/        MpdProtocol (pure parsing) → MpdConnection (one socket) → MpdClient
data/snapcast/   SnapcastProtocol (pure parsing) → SnapcastClient (JSON-RPC)
data/settings/   DataStore-backed ServerSettings
repo/            MpdRepository, SnapcastRepository — StateFlows + reconnect
ui/              One Screen + ViewModel per tab, Compose + Material 3
```

Both protocol clients are reimplementations, not ports: the Go originals return
`tea.Cmd`/`tea.Msg` and are inseparable from Bubble Tea. MPD's text protocol is
spoken directly rather than through a library, so the gompd quirk that
lowercased keys for `lsinfo` alone does not apply — one mapping covers every
command.

MPD needs two sockets, as in the original: `idle` occupies its connection for as
long as it is parked, so commands travel on a second one.

## Tests

`./gradlew testDebugUnitTest` runs 54 tests:

- **Pure parsing** — MPD framing, `ACK` errors, argument quoting, fractional-second
  durations; SnapCast status flattening and the host-name fallback.
- **Loopback integration** — `FakeMpdServer` replays scripted transcripts over a
  real socket on a loopback port, covering connect, commands, `idle`, and
  descending-order deletion. This stands in for the Go project's build-tagged
  integration tests.
- **ViewModel** — queue selection behaviour.

Note that JVM unit tests do not catch everything: Android's ICU regex engine is
stricter than the JVM's, and rejected a pattern the JVM accepted. Run the app on
a device or emulator before trusting a protocol change.
