# Active Now for Microcord

Active Now adds a horizontal row immediately above **Direct Messages** in Microcord's channel sidebar. It shows friends in a visible voice channel or known DM call, friends sharing an activity, and friends who are Online, Idle, or Do Not Disturb. A friend's card can show voice and activity together. Swipe across the cards to browse them, or tap a card to open the friend's profile.

Profiles show an **In voice** card with the current channel and server when that voice state is available to your account. The card updates while the profile is open. Both surfaces use Microcord's Discord typography, and the profile card follows the profile's resolved palette.

The row updates from Microcord's existing stores and hides when there are no matching friends. Voice coverage depends on channel visibility and the call data available to the current account. Activity coverage depends on the presence a friend shares.

## Install

1. Install `ActiveNow.apk` on your Android device.
2. In Microcord, open **Settings → Plugins** and enable **Active Now**. Confirm the plugin signer when prompted.
3. Open the **Direct Messages** sidebar. The row appears when a friend has a visible online status, is sharing an activity, or is in a voice channel or call that Microcord can see.

If an earlier Active Now APK with the same signer is installed, Android can update it in place.

Use a Microcord build with plugin API level 3 or newer. This plugin uses the sidebar and store classes in the 1.0.6 SDK, so a Microcord update that changes those internals may require a plugin update.

## Build

The project uses the [Microcord plugin template](https://github.com/microcord/plugin-template), JDK 21, and Android SDK Platform 36.1. Run:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug
```

On macOS or Linux, use `bash ./gradlew testDebugUnitTest assembleDebug`. The debug APK is written under `build/outputs/apk/debug/`. For a distributable release signed with your own key, set up the ignored `keystore.properties` using the example and run `assembleRelease`. Android requires the same signing key for an in-place update.
