# Building and side-loading ghac

ghac is not on the Play Store. To run it on a phone you build the APK yourself
and install it directly, which is called side-loading. This guide covers both
steps.

- [1. Set up the build tools](#1-set-up-the-build-tools)
- [2. Choose debug or release](#2-choose-debug-or-release)
- [3. Build the APK](#3-build-the-apk)
- [4. Install it on the phone](#4-install-it-on-the-phone)
- [5. First launch](#5-first-launch)
- [Updating](#updating)
- [Troubleshooting](#troubleshooting)

## 1. Set up the build tools

You need:

- **A JDK, version 17 or later.** Check with `java -version`.
- **The Android SDK**, with the platform for `compileSdk 37` installed. You can
  get it from Android Studio or the standalone command-line tools.
- **`adb`** from the SDK's `platform-tools`. You only need it if you install
  over a cable or Wi-Fi (option A below).

Tell Gradle where the SDK is. Either export `ANDROID_HOME` or create
`local.properties` in the project root:

```properties
sdk.dir=/path/to/android-sdk
```

If you use Homebrew on macOS, the paths are usually these:

```sh
brew install openjdk@21 android-commandlinetools
export JAVA_HOME=/opt/homebrew/opt/openjdk@21
export ANDROID_HOME=/opt/homebrew/share/android-commandlinetools
export PATH="$ANDROID_HOME/platform-tools:$PATH"
sdkmanager "platform-tools" "platforms;android-37"
```

`openjdk@21` is keg-only, so Homebrew does not put it on your `PATH`. That is
why `JAVA_HOME` has to be set explicitly. Without it, `java -version` says it
cannot find a Java runtime even though the JDK is installed.

## 2. Choose debug or release

| | Debug | Release |
|---|---|---|
| Command | `./gradlew assembleDebug` | `./gradlew assembleRelease` |
| Signing | Automatic, with a debug key made on your machine | Needs your own keystore (see below) |
| Size | ~20 MB | ~1.5 MB, shrunk and optimised |
| App id | `com.nickorton.ghac.debug` | `com.nickorton.ghac` |
| Home-screen name | ghac | ghac |

Because the app ids differ, both builds can be installed side by side, and
they are separate apps with separate settings.

**For a phone you will use every day, build release.** It is smaller and
faster. More importantly, its signing key is one you keep. Android only lets
an app update over an existing install when both are signed with the same key.
The debug key lives in `~/.android/debug.keystore` on whichever machine did the
build, so building on another machine, or after wiping that file, means you
have to uninstall before you can update.

### Setting up release signing (one time)

1. Create a keystore. Keep it **outside** the repository:

   ```sh
   keytool -genkeypair -v \
     -keystore ~/keys/ghac-release.jks \
     -alias ghac \
     -keyalg RSA -keysize 4096 -validity 10000
   ```

   `keytool` asks for a password and a name. The name can be anything.

2. Create `keystore.properties` in the project root. It is gitignored:

   ```properties
   storeFile=/Users/you/keys/ghac-release.jks
   storePassword=your-store-password
   keyAlias=ghac
   keyPassword=your-key-password
   ```

3. **Back up both files.** If you lose the key, you can never again update the
   installed app in place. You can only uninstall it and install again, and
   that wipes its saved settings.

Without `keystore.properties`, `assembleRelease` still succeeds, but it
produces an *unsigned* APK (`app-release-unsigned.apk`), which Android will
not install.

## 3. Build the APK

From the project root:

```sh
./gradlew assembleRelease     # or assembleDebug
```

The APK is written to:

- Release: `app/build/outputs/apk/release/app-release.apk`
- Debug: `app/build/outputs/apk/debug/app-debug.apk`

## 4. Install it on the phone

The phone must run **Android 8.0 (API 26) or later**. There are two ways to
install. Option A is easier if you will update often, because each update
takes one command.

### Option A — adb over USB or Wi-Fi

**Turn on developer options.** Open *Settings → About phone* and tap *Build
number* seven times. The exact place varies by manufacturer; on Samsung it is
*Settings → About phone → Software information*.

**Connect the phone.** Use one of these:

- **USB.** In *Settings → System → Developer options*, turn on *USB
  debugging*. Plug the phone in and accept the "Allow USB debugging?" prompt
  on the phone.
- **Wi-Fi** (Android 11 or later; the phone and computer must be on the same
  network).
  1. In *Developer options*, turn on *Wireless debugging* and tap it to open
     its settings.
  2. Tap *Pair device with pairing code*. The phone shows a code and an
     `IP:port`.
  3. Pair from the computer:

     ```sh
     adb pair 192.168.1.50:37123      # the pairing IP:port; it asks for the code
     ```

  4. Connect using the *different* `IP:port` shown on the main *Wireless
     debugging* screen:

     ```sh
     adb connect 192.168.1.50:41234
     ```

**Check that adb can see the phone.** It should be listed as `device`. If it
says `unauthorized`, accept the prompt on the phone:

```sh
adb devices
```

**Install the APK:**

```sh
adb install -r app/build/outputs/apk/release/app-release.apk
```

`-r` replaces an existing install and keeps its data. For debug builds you can
build and install in one step with `./gradlew installDebug`.

If more than one device is connected, use `adb -s <serial> install -r app/...` 
to pick one. The serial is the first column of `adb devices`. The serial must
come before the `install` command.

### Option B — copy the APK to the phone

You do not need developer options or adb for this.

1. Put the APK on the phone. You can use a USB file transfer, a cloud drive,
   or email it to yourself.
2. Open it with the phone's *Files* app, or tap the download notification.
3. Android asks whether the app that opened the file may install unknown apps.
   Tap *Settings*, turn on *Allow from this source*, and go back.
4. Tap *Install*.
5. Google Play Protect may warn that it does not recognise the app. This is
   normal for anything you build yourself. Tap *More details → Install anyway*.

Afterwards you can turn *Allow from this source* back off for that app.

## 5. First launch

- **Local network permission.** On Android 17 and later, ghac asks for access
  to devices on your local network. Allow it. If it is denied, every
  connection to MPD and SnapCast times out, which looks exactly like a wrong IP
  address even when the address is right. If you denied it by mistake, the
  Settings tab shows a *Grant access* button. You can also turn it on in
  *Android Settings → Apps → ghac → Permissions*.
- **Server addresses.** On the first run the app opens the Settings tab. Enter
  the MPD and SnapCast hosts and tap *Save and connect*. The status lines
  under each server should change to "connected".
- **Theme.** Pick one at the bottom of Settings. It applies immediately.

## Updating

Pull the latest code, rebuild, and install over the top:

```sh
git pull
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

If you use option B, copy the new APK over and tap *Install* again. Your
server addresses and theme are kept, as long as the new APK is signed with the
same key as the installed one.

## Troubleshooting

**`INSTALL_FAILED_UPDATE_INCOMPATIBLE`, or "App not installed as package
conflicts with an existing package".** The installed copy was signed with a
different key. Usually this is a debug build from another machine. Uninstall
it first, which also clears its settings:

```sh
adb uninstall com.nickorton.ghac          # release
adb uninstall com.nickorton.ghac.debug    # debug
```

**"App not installed" with no other detail.** The APK is probably unsigned.
Check that you installed `app-release.apk`, not `app-release-unsigned.apk`, and
that `keystore.properties` exists.

**`INSTALL_FAILED_OLDER_SDK`.** The phone runs an Android version older than
8.0, which ghac does not support.

**`adb devices` shows nothing.**
- Try a different USB cable; some are charge-only.
- Set the phone's USB mode to *File transfer*.
- Run `adb kill-server` and try again.
- On Wi-Fi, the port from `adb connect` changes each time *Wireless debugging*
  is toggled, so read it again from the phone.

**The app installs but will not connect.**
- Check the local network permission (see [First launch](#5-first-launch)).
- Make sure the phone is on the same network as the servers, not on mobile
  data or a guest Wi-Fi network that isolates devices from each other.
- The error text under each status line comes straight from the socket.
  "No route to host" or a timeout points at the network or the permission.
  "Connection refused" means the host is reachable but nothing is listening on
  that port.

**The Gradle build fails with "SDK location not found".** Set `ANDROID_HOME`
or create `local.properties` (see [step 1](#1-set-up-the-build-tools)).
