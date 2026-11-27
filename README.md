# Nug of War

A couch brawler for Android phones, TV boxes and projectors. Project it onto your wall, trace your
shelves and picture frames, and they become the platforms your nugs fight on. Minimal dark style,
nugs with little legs.

- **Wall Setup:** point the projector at your wall and trace each shelf, frame or bit of furniture with the controller.
  Thin lines become ledges you can jump up through, tall boxes become solid blocks. Save up to 3 walls.
  There are also 4 built-in arenas (Bookshelf, Gallery Wall, Bedroom, Open Wall) for a normal screen.
- **Versus Brawl (2 to 4 players):** side-on platform fighting with wobbly, launchable nugs. 3 lives each,
  win 2 rounds to win the match. The more damage you've taken, the further you fly.
- **Weapons drop in:** sword, seed blaster, grenades, rocket launcher. No weapon? You've still got a bonk.
- **Block and parry:** hold block to soak hits, tap it right before a hit to parry (stuns them, reflects shots).
- **Round twists:** jetpacks, low gravity, rockets only, sword fight, boom barrels, hot floor (and snowball fights at Christmas).
- **Bad Nug Survival (1 to 4 players, co-op):** waves of brown and yellow bad nugs climb your wall. Every 5th wave is a Moldy Mega Nug.
- **Christmas Mode:** snow, snow caps and fairy lights on your shelves, Santa hats, and Santa flying over dropping presents.
- **Streamer Commentary:** a laid-back made-up streamer, *couchcaster*, reacts to the action with a quiet live chat.
- **Settings:** Projector Mode (pure black background so only the game lights your wall), Christmas, Commentary, Sound.

## 0. Hook up the projector

Any HDMI projector works. Plug the Android device into it: a TV box / Android projector runs the app directly;
an Android phone needs a USB-C to HDMI adapter (the phone must support video out, most Samsung and Pixel phones do)
or screen casting to a Chromecast. Then in the game open **Wall Setup** and trace your wall.

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

Most Bluetooth controllers work: Xbox (One/Series), PlayStation 4/5, 8BitDo, generic Android gamepads. Up to 4 at once.

1. On the Android device go to **Settings > Bluetooth** (on TVs: **Settings > Remotes & Accessories > Add accessory**).
2. Put the controller in pairing mode:
   - **Xbox controller:** turn it on with the Xbox button, then hold the small **pair** button on top until the logo flashes fast.
   - **PS4 controller:** hold **Share + PS** until the light bar double-flashes.
   - **PS5 controller:** hold **Create + PS** until the light flashes.
   - **8BitDo:** use its Android/X-input mode (usually **Start + X** or the mode switch on the back), then hold the pair button.
   - Others: check the manual for "pairing mode".
3. Pick the controller in the Bluetooth list. Repeat for the second controller.
4. Open the game. **The first controller to press a button becomes Player 1, the next Player 2, and so on.** The bottom of the main menu shows which controller is which. If they're the wrong way round, go to **Settings > Reset Controllers** and press A on each in the order you want.

USB controllers (with an OTG adapter) work too.

## Controls

| | Controller | Keyboard P1 / P2 | Touch (2 players) |
|---|---|---|---|
| Move | Left stick / D-pad | WASD / Arrows | Drag on the outer side of your half |
| Jump (again in the air = double jump, down + jump = drop through a ledge) | A | Space / Enter | JUMP |
| Attack / use weapon (up + attack = uppercut) | X, R1 or right trigger | F / Right Ctrl | HIT |
| Block (tap just before a hit = parry) | B, L1 or left trigger | G / Right Shift | BLOCK |
| Pick up, swap or throw weapon | Y | H / ; | walk over it |
| Aim guns | Right stick, or hold up/down | | |
| Pause | Start | Esc | pause icon top right |

**Wall Setup controls:** stick moves the cursor (push gently for fine control), A places a corner (press A at one
corner and again at the opposite corner), X deletes the platform under the cursor (hold X to clear all),
Y toggles the grid, B or Start saves. On touch: drag to draw, tap a platform to delete it.

**Tips for tracing:** for a shelf, trace a thin line along its top edge. For a picture frame or a piece of furniture,
trace the whole box so it becomes a solid block you can stand on and hide behind. Turn a lamp on so you can see your
wall while the projector is on, and keep the projector still afterwards (if it moves, just re-trace).

## Building it yourself

Every push to `main` builds a new APK with GitHub Actions and publishes it as a release.
To build locally: install Android Studio (or the Android SDK + JDK 17) and run `./gradlew assembleRelease`.

`tools/preview` renders screenshots of the game on a normal computer with Robolectric (`gradle test`), and GitHub runs it on every push.

The signing key in `keystore/` is a throwaway key for this hobby game. It's committed on purpose, so every build installs over the last one.
