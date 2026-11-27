# Nug of War

A couch multiplayer game for Android phones, tablets, TV boxes and projectors.
Little nugs on little legs, minimal dark style.

- **Co-op: Defend the Wall.** 1 or 2 players hold the wall against waves of bad brown and yellow nugs. Every 5th wave brings a Moldy Mega Nug.
- **Versus: Bud vs Bud.** Each player stays on their own side. Knock the other bud out or smash their wall. First to 3 rounds wins.
- **Christmas Mode** (toggle on the main menu): light snow, Santa flying over every so often dropping presents (power-ups), Santa hats, tree lights.
- **Streamer Commentary** (toggle on the main menu): a laid-back made-up streamer called *couchcaster* reacts to the game, with a quiet live chat scrolling by the wall.

## 1. Download the APK

1. On the device you'll play on (phone, tablet, Android TV box or projector), open
   **https://github.com/terpenesalad/nug-of-war/releases/latest** in the browser.
   (If the repo is private, sign in to GitHub first, or download it on your phone/computer and copy it over with a USB stick.)
2. Tap **NugOfWar.apk** to download it.

## 2. Install it

1. Open the downloaded file (from the browser's downloads, or the **Files** app).
2. Android will say installing unknown apps is blocked. Tap **Settings**, turn on **Allow from this source**, then go back and tap **Install**.
3. Open **Nug of War**. On Android TV / projectors it shows up in the apps row (you might need to look under "See all apps").

**Android TV / projector tips:**
- If the TV box has no browser, install the free **Downloader** app from the Play Store, type the releases link above into it and install from there. You may need to allow Downloader under *Settings > Device Preferences > Security & restrictions > Unknown sources*.
- Or: put the APK on a USB stick, plug it in and open it with a file manager app (e.g. *File Commander* or *X-plore*).

**Updating:** download the newest APK the same way and install it over the old one. No need to uninstall.

## 3. Pair your controllers (Bluetooth)

Most Bluetooth controllers work: Xbox (One/Series), PlayStation 4/5, 8BitDo, generic Android gamepads.

1. On the Android device go to **Settings > Bluetooth** (on TVs: **Settings > Remotes & Accessories > Add accessory**).
2. Put the controller in pairing mode:
   - **Xbox controller:** turn it on with the Xbox button, then hold the small **pair** button on top until the logo flashes fast.
   - **PS4 controller:** hold **Share + PS** until the light bar double-flashes.
   - **PS5 controller:** hold **Create + PS** until the light flashes.
   - **8BitDo:** use its Android/X-input mode (usually **Start + X** or the mode switch on the back), then hold the pair button.
   - Others: check the manual for "pairing mode".
3. Pick the controller in the Bluetooth list. Repeat for the second controller.
4. Open the game. **The first controller to press a button becomes Player 1, the next one Player 2.** The bottom of the main menu shows which controller is which. If they're the wrong way round, choose **Reset Controllers** and press A on each in the order you want.

USB controllers (with an OTG adapter) work too.

## Controls

| | Controller | Keyboard | Touch |
|---|---|---|---|
| Move | Left stick / D-pad | P1: WASD, P2: Arrows | Drag on the outer side of your half |
| Aim | Right stick (optional, otherwise you shoot where you're facing, with a bit of aim assist) | | |
| Shoot | A, X, R1 or right trigger | P1: Space, P2: Enter | FIRE button |
| Dash (invincible while dashing, rams enemies) | B, L1 or left trigger | P1: Left Shift, P2: Right Shift | DASH button |
| Pause | Start | Esc / P | Pause icon top middle |

In the lobby: left/right changes your colour, up/down (or Y) changes your shape, A = ready.

**Co-op tips:** if a buddy gets knocked down, stand next to them to revive them 3x faster. Pickups: green cross = heal, orange = rapid fire, blue = triple shot, brick = repair the wall.

## Building it yourself

Every push to `main` builds a new APK with GitHub Actions and publishes it as a release.
To build locally: install Android Studio (or the Android SDK + JDK 17) and run `./gradlew assembleRelease`.

The signing key in `keystore/` is a throwaway key for this hobby game. It's committed on purpose, so every build installs over the last one.
