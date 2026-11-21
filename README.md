# Active Now for Microcord

Active Now adds a carousel between the **Direct Messages** search box and conversation list. It shows friends in a visible voice channel or known DM call, friends sharing an activity, and friends who are Online, Idle, or Do Not Disturb. Swipe or use the arrow buttons to browse, then tap a card to open the friend's profile.

Profiles show an **In voice** card with the current channel and server when that voice state is available to your account. Tap **Join VC** to enter a call you can join, or tap the server name to open that server. The card updates while the profile is open. Both surfaces use Microcord's Discord typography, and the profile card follows the profile's resolved palette.

The row updates from Microcord's existing stores and hides when there are no matching friends. Voice coverage depends on channel visibility and the call data available to the current account. Activity coverage depends on the presence a friend shares.

## Install

1. Install `ActiveNow.apk` on your Android device.
2. In Microcord, open **Settings → Plugins** and enable **Active Now**. Confirm the plugin signer when prompted.
3. Open the **Direct Messages** sidebar. The carousel appears below search when a friend has a visible online status, is sharing an activity, or is in a voice channel or call that Microcord can see.

If you have Active Now 1.4.0 or older, uninstall the old **Active Now** APK before installing 1.4.1. The new Android package is `io.github.mmaarij123.activenow`. Later updates to this package can install in place when signed with the same key.

Use a Microcord build with plugin API level 3 or newer. This plugin uses the sidebar and store classes in the 1.0.6 SDK, so a Microcord update that changes those internals may require a plugin update.

## Build

The project uses the [Microcord plugin template](https://github.com/microcord/plugin-template), JDK 21, and Android SDK Platform 36.1. Run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

On macOS or Linux, use `bash ./gradlew testDebugUnitTest assembleDebug`. The debug APK is written under `build/outputs/apk/debug/`. For a distributable release signed with your own key, set up the ignored `keystore.properties` using the example and run `assembleRelease`. Android requires the same signing key for an in-place update.

## License

Active Now plugin code is licensed under the [MIT License](LICENSE). The Gradle Wrapper retains its upstream license and notices.
