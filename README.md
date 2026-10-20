# TV Remote (Android)

An ultra-light (~29 KB) native Android remote for TVs that run the **WirelessRemote** receiver (UDP port 10030).

## Features
- Neumorphic dark UI with light theme toggle
- D-pad + OK, Vol/Ch rockers (hold to repeat), Power, Mute, Menu, Home, Back, Settings, media keys
- **Input** button: tap for source menu, hold for HDMI 1–4 / AV / Component / VGA / TV list
- **Help (?)** lists every supported key – tap to send
- Keyboard: type text onto the TV
- Auto-discovery (`POSTIP` broadcast) or manual TV IP, saved between launches

## Protocol
UDP to `<tv-ip>:10030`
- Discovery: broadcast `POSTIP` to `255.255.255.255:10030`
- Pair: `<phone-ip>&TVIP=<tv-ip>&<name>`
- Key: `<phone-ip>&key=action;<Android keycode>&`
- Text: `<phone-ip>&key=editText;<char>`

## Build
No Gradle. Needs JDK 17, `android.jar` (API 34), aapt2, d8, zipalign, apksigner. Edit `T=` in `build.sh`, then `bash build.sh` → `TVRemote.apk`.
