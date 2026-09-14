# Green Light

A skydiving jump log, dive planner and skill tracker that follows you from the plane to the ground.

Green Light is a single-file web app wrapped in [Capacitor](https://capacitorjs.com/) for Android.
The whole interface reacts to where you are in the jump: the sky behind the UI changes colour,
the clouds rush past, and the card in front of you swaps to whatever actually matters at that
altitude.

**Package:** `com.savion.greenlight` · **Version:** 1.0.5 (versionCode 15)

---

## The idea

Most logbook apps are a form you fill in on the ground. Green Light is built around the five
bands of a jump, and shows you one thing at a time:

| Band | What the screen gives you |
| --- | --- |
| `plane` | Load number, aircraft, exit altitude, the dive you planned, gear and AAD checks |
| `freefall` | The move you're working, freefall time, your working altitude |
| `pull` | Decision altitude and your emergency procedure, in your own words |
| `canopy` | Canopy drills — flat turns, braked approach, riser work |
| `ground` | Log the run, score it, pack it, get the coach's signature |

---

## What's in it

**Logbook** — log a jump and it numbers itself from your existing total, scores it, tracks
freefall time, and stamps the GPS fix where you landed. Works fully offline; jumps queue
locally and go to your sheet when you have signal again.

**Skill tracks** — four progressions with per-move point values:

- **Freefly** — back-fly, stand-fly, sit-fly, stand turns, transitions, head-down
- **Belly & acro** — turns, front/back/side flips, dock, fall rate, track
- **Canopy** — flat turn, braked approach, front riser, rear-riser flare, clear-and-pull 3,500, inside 10 m
- **Angle** — tracking dive, angle belly, angle back, lead

**Packing Academy** — the pack job broken into steps, each with a plain-language mnemonic
so it sticks: the nose is a *pizza box*, the tail wrap is *the burrito*, the bag is a
*sleeping bag* ("the bag always wins if you are patient"). There's a pack timer, a running
pack count, and a Venmo hand-off for when you'd rather pay a packer.

**B-license and wingsuit progress** — tracks the B-license requirements and a wingsuit
readiness checklist (10 stable tracking dives, back-fly solid, 20 on-heading openings in a
row, line-twist drill signed off, and so on).

**Dropzones** — a built-in list of Florida DZs with coordinates, distance from your current
position, and one-tap calling. You can override any DZ's number in settings.

**Coach signatures** — a coach can sign off a jump on the device.

**Wing loading** — computed from your body weight, gear weight and canopy size, with your
decision altitude kept front and centre.

---

## Setup

### Run the web app

`www/index.html` is the entire app. Open it in a browser and it works — state lives in
`localStorage` under the key `greenlight.v1`.

### Build for Android

```bash
npm install
npx cap add android     # the android/ project is not committed
npm run sync            # cap sync android
npm run open:android    # opens Android Studio
```

`capacitor.config.json` already sets the app id, name, `webDir: "www"`, the `#07112B`
background and an instant splash screen.

### Wire up Google Sheets sync (optional)

Sync is off until you point it at your own sheet. Nothing leaves the device without it.

1. Make a Google Sheet for your log.
2. **Extensions → Apps Script**, and write a `Code.gs` web app that accepts the contract
   below. Set a `SECRET` constant inside it.
3. **Deploy → New deployment → Web app**, with **Execute as: Me** and
   **Who has access: Anyone**. Copy the `/exec` URL.
4. Copy the config template and fill it in:

   ```bash
   cp www/sync-config.example.js www/sync-config.js
   ```

   ```js
   window.GL_SYNC = {
     script: 'https://script.google.com/macros/s/YOUR_DEPLOYMENT_ID/exec',
     secret: 'same-as-SECRET-in-Code.gs'
   };
   ```

5. Rebuild, or paste the same three values into **Settings & Sync** in the app and hit
   **Test sheet connection**.

`www/sync-config.js` is gitignored. See [Security](#security) for why that matters.

#### The Apps Script contract

| Direction | Shape |
| --- | --- |
| `GET  ?secret=…` | → `{ ok: true, last: { no } }` — the last row in the sheet |
| `POST {secret, jumps[], packs[]}` | → `{ ok: true, added: [...] }` — append rows |
| `POST {secret, deleteJump: no}` | → `{ ok: true }` — remove a jump |
| any failure | → `{ ok: false, error: "…" }` |

The app reads the response as text first so it can give you a useful error when Google
returns an HTML login page instead of JSON — the usual cause is deploying with
**Who has access** set to anything but *Anyone*.

---

## Security

The sync secret is a **shared secret baked into the client**, which means anyone who
unzips a built APK can read it. Treat it as a speed bump that stops random traffic hitting
your Apps Script endpoint, not as authentication. Your Apps Script should only ever append
to your own sheet, and should never return data you'd mind a stranger reading.

Because of that:

- `www/sync-config.js` is **gitignored** — only `sync-config.example.js` is committed.
- `*.apk` / `*.aab` are **gitignored** — a release build contains your real `sync-config.js`
  inside `assets/public/`, so publishing an APK publishes your secret.
- If a deployment URL and secret ever do leak, rotate them: change `SECRET` in `Code.gs`
  and redeploy to get a fresh `/exec` URL.

---

## What talks to the network

| Destination | When | What goes out |
| --- | --- | --- |
| Your Apps Script `/exec` | Only if you configured sync | Jump and pack rows, your secret |
| `fonts.googleapis.com` | On load | Font request |
| Your Google Sheet | When you tap **Open sheet** | Opens in a browser tab |
| `venmo://` / `venmo.com` | When you tap **Pay packer** | Amount and note, prefilled |
| `tel:` | When you tap **Call DZ** | Dials the number |

Location is read on-device for the landing fix and DZ distances.

---

## Stack

- Single-file HTML/CSS/JS — no framework, no build step, no bundler
- Capacitor 7 (Android), AGP 8.7.2, play-services-location 21.3.0
- Plugins: `app`, `browser`, `filesystem`, `geolocation`, `haptics`, `preferences`, `share`
- Permissions: `ACCESS_FINE_LOCATION`, `ACCESS_COARSE_LOCATION`, `INTERNET`, `VIBRATE`
- Persistence: `localStorage` (`greenlight.v1`), with an offline queue for unsent rows
- Type: `IBM Plex Sans` / `IBM Plex Mono` / `Big Shoulders Display` / `Saira Stencil One`

---

## Repository contents

```
green-light/
├── capacitor.config.json
├── package.json
└── www/
    ├── index.html               the entire app
    └── sync-config.example.js   copy to sync-config.js and fill in
```

Not included: the generated `android/` project (run `npx cap add android`), and the
companion Apps Script `Code.gs`, which lives in your own Google account.

---

© Savion Winston
