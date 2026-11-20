# Universal Remote (Android)

An Android app that turns your phone into a remote for most TVs, over **Wi-Fi** or **infrared**.

| TV type | How it talks to the TV | Notes |
|---|---|---|
| **Vestel-based** (Polaroid, Toshiba, JVC, Hitachi, Bush, Luxor, Finlux, Telefunken, Techwood…) | HTTP on port `56789` (`/apps/SmartCenter`) | Turn on *Virtual Remote* in the TV settings if it doesn't respond |
| **Roku TV** (TCL, Hisense, Sharp…) / Roku sticks | Roku ECP, port `8060` | Set *Control by mobile apps* to Enabled/Permissive |
| **Samsung** Tizen (≈2016+) | WebSocket on `8002` (TLS) or `8001` | Accept the prompt on the TV the first time |
| **LG** webOS (≈2014+) | WebSocket on `3001` (TLS) or `3000` | Accept the prompt on the TV the first time |
| **Sony Bravia** | IRCC over HTTP | Turn on IP control and set a Pre-Shared Key |
| **Any TV with an IR remote** | Phone's IR blaster | Only on phones that have an IR emitter |

## Features

- **Scan** your Wi-Fi network to find TVs automatically, or add one by IP address.
- A full remote: power, source, mute, D-pad and OK, back, home, menu, exit, volume and channel (hold to repeat), number pad, play controls, colour keys, and Netflix and YouTube buttons.
- **Code explorer**: send any code and see what the TV does, step through numeric codes with − and +, and assign a working code to any button. Use this to fix a button that doesn't work on your model.
- Wake-on-LAN, if you enter the TV's MAC address.
- Opens straight to the last TV you used.

## Testing on the Polaroid P32FPA0119A

This TV was made by Vestel for ASDA (2019, Freeview Play).

1. Connect the TV to your Wi-Fi. Find its IP address under **Settings › Network**.
2. If the TV has a **Virtual Remote** option (often under **Settings › System › More**), turn it on.
3. In the app, tap **Scan**. The TV should appear as *Vestel-based TV*. If it doesn't, tap **Add manually**, pick *Vestel-based TV*, and enter the IP.
4. Try **Vol +/−**, **Mute**, **Ch +/−** and the numbers first, because those codes are confirmed.
5. If a button does nothing (arrows, OK, Home and so on), open **Codes** (the spanner icon). Use **−/+** to step through codes from `1000` upwards and watch the TV. When one works, assign it and tap **Save**.

## Installing

Every push builds an APK with GitHub Actions:

- **Releases** page → the newest *Build N* → download `universal-remote.apk` on your phone and open it. You'll need to allow *Install unknown apps* for your browser.
- Or open **Actions** → the latest run → *universal-remote-apk* artifact.

Builds are signed with the same key every time, so new versions install over old ones.

## Building locally

Requires JDK 17 and the Android SDK (API 35).

```bash
./gradlew assembleDebug
# app/build/outputs/apk/debug/app-debug.apk
```

## Not supported yet

- Android TV / Google TV (needs the TLS-paired Remote v2 protocol)
- Hisense VIDAA (MQTT with certificates), Philips JointSpace, Panasonic Viera
- Learning IR codes (Android's IR API can only send, not receive)
