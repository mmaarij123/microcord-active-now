# Active Now for Microcord

See which friends are online, what they’re doing, and who’s in voice from your Microcord DM sidebar.

## What it does

- **Active Now below DM search.** Browse friends who are Online, Idle, or Do Not Disturb, along with shared activities like games, music, and streams. Cards also show voice channels and calls your account can see.
- **Browse without leaving DMs.** Swipe the cards or use the arrows. Tap a card to open that friend’s profile.
- **Join voice from a profile.** The live **In voice** card shows the channel and server. Tap **Join VC** when you have permission to join, or tap the server name to open it in Microcord.

## Install

1. Open the [latest GitHub release](https://github.com/mmaarij123/microcord-active-now/releases/latest) and download **ActiveNow.apk** from **Assets**.
2. Install the APK on your Android device.
3. In Microcord, go to **Settings → Plugins**, enable **Active Now**, and confirm the plugin signer if prompted.
4. Open **Direct Messages**. The carousel appears below search when there’s friend activity to show.

If you’re upgrading from **1.4.0 or older**, uninstall the old Active Now APK first. The Android package changed in 1.4.1.

## Technical details

Active Now is a Kotlin Android plugin based on the [Microcord plugin template](https://github.com/microcord/plugin-template). It uses Microcord’s existing presence and voice-state data and its built-in voice and server navigation. The Android package is `io.github.mmaarij123.activenow`. It requires Android 6.0 or later and Microcord plugin API level 3 or newer.

### Build from source

Install **JDK 21** and **Android SDK Platform 36.1**, then run the Gradle wrapper from the repository root.

**Windows**

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

**macOS or Linux**

```bash
./gradlew testDebugUnitTest assembleDebug
```

The debug APK is written to `build/outputs/apk/debug/`. For a signed release build, configure `keystore.properties` using [the example](keystore.properties.example) and run `assembleRelease`.

## License

The original Active Now plugin code is licensed under MIT. Copyright © 2026 Maarij. You may use, copy, modify, and redistribute the code, including for commercial use. Include the copyright notice and permission notice with copies or substantial portions of the code. The code is provided as is, without warranty. The full terms include a limitation of liability. [Read the MIT license](LICENSE)

See the notices for the host-provided libraries, artwork displayed by the plugin, and the Gradle Wrapper. [Third-party notices](THIRD_PARTY_NOTICES.md)
