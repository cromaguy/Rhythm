# Permissions Guide

This guide explains all permissions requested by Rhythm Music Player, why they're needed, and how they're used.

## 🔒 Privacy First

Rhythm is **100% FOSS-compliant** and respects your privacy:
- ✅ No analytics or tracking
- ✅ No personal data collection
- ✅ No internet requirement for core functionality
- ✅ All permissions used solely for app functionality
- ✅ No background data transmission

---

## 📱 Required Permissions

### 📁 Storage Access

**Permissions:**
- `READ_EXTERNAL_STORAGE` (Android ≤12)
- `READ_MEDIA_AUDIO` (Android 13+)

**Why Needed:**
- Scan and index your music files
- Display album artwork
- Read audio metadata (title, artist, album)
- Access playlists from storage

**Where Used:**
- Media library scanning
- Music playback
- Album art display
- Playlist management
- Metadata reading

**Android 14+ Partial Access:**
You can grant access to only selected photos/media instead of full library access.

---

## 🔔 Optional Permissions

### 📢 Notifications

**Permission:** `POST_NOTIFICATIONS` (Android 13+)

**Why Needed:**
- Display playback notification with controls
- Show update availability alerts
- Persistent playback notification

**Where Used:**
- Media notification (play/pause, skip)
- Background playback status
- Update notifications
- Download progress

**Can be disabled:** Yes, but you'll lose playback controls in notifications.

---

### 📶 Internet Access

**Permissions:**
- `INTERNET`
- `ACCESS_NETWORK_STATE`

**Why Needed:**
- Fetch synchronized lyrics from LRCLib
- Download album artwork from Deezer/YouTube Music
- Check for app updates from GitHub
- Access online features

**Where Used:**
- LRCLib lyrics integration
- Online album artwork fetch
- GitHub update checking
- Deezer API integration

**Data Usage:** Minimal (lyrics ~1-5KB, artwork ~50-200KB)

**Can be disabled:** Yes (via firewall), but you'll lose online features.

---

### 🎧 Audio Settings

**Permission:** `MODIFY_AUDIO_SETTINGS`

**Why Needed:**
- Adjust audio routing
- Manage volume levels
- Configure audio focus
- Enable equalizer

**Where Used:**
- Audio focus management (pausing for calls)
- Bluetooth device switching
- Volume control integration
- Equalizer and audio effects

**Can be disabled:** No, required for proper audio playback.

---

### 📱 Bluetooth

**Permissions:**
- `BLUETOOTH` (All versions)
- `BLUETOOTH_ADMIN` (Android ≤11)
- `BLUETOOTH_CONNECT` (Android 12+)
- `BLUETOOTH_SCAN` (Android 12+)

**Why Needed:**
- Detect Bluetooth audio devices
- Auto-play when headphones connect
- Display device name in UI
- Switch between audio outputs

**Where Used:**
- Bluetooth speaker/headphone support
- Auto-resume on device connection
- Audio output device selection
- Wireless playback controls

**Can be disabled:** Yes, if you don't use Bluetooth audio.

---

### 📲 Install Packages

**Permission:** `REQUEST_INSTALL_PACKAGES`

**Why Needed:**
- Install app updates automatically
- In-app update deployment

**Where Used:**
- Automatic update installation
- GitHub release downloads

**Can be disabled:** Yes, you can update manually.

---

### 🏠 Widgets

**Permissions:**
- `BIND_APPWIDGET`
- `UPDATE_APPWIDGET`

**Why Needed:**
- Create home screen widgets
- Update widget content in background

**Where Used:**
- Glance widgets (Music, Cookie, Stats, Lyrics)
- Real-time playback state
- Background widget updates via WorkManager

**Can be disabled:** No, required for widgets to function.

---

### 🎵 Foreground Service

**Permissions:**
- `FOREGROUND_SERVICE`
- `FOREGROUND_SERVICE_MEDIA_PLAYBACK` (Android 14+)

**Why Needed:**
- Continuous background music playback
- Prevent Android from killing playback
- Lock screen controls

**Where Used:**
- Background music playback
- Media notification service
- Lock screen controls

**Can be disabled:** No, required for background playback.

---

## 🚫 Removed Permissions

Rhythm **does NOT** request these permissions (removed for privacy):

❌ `MANAGE_EXTERNAL_STORAGE` - Broad file access (not needed for music)
❌ `ACCESS_MEDIA_LOCATION` - GPS coordinates in photos (not needed)
❌ `RECORD_AUDIO` - Microphone access (never needed)
❌ `CAMERA` - Camera access (never needed)
❌ `LOCATION` - GPS/location (never needed)
❌ `CONTACTS` - Contact list (never needed)
❌ `PHONE` - Phone calls/SMS (never needed)

---

## ⚙️ Managing Permissions

### Granting Permissions

#### During First Launch
1. Open Rhythm
2. Follow onboarding wizard
3. Grant requested permissions when prompted

#### Manual Grant (Android Settings)
1. Open **Settings** → **Apps**
2. Find and tap **Rhythm Music**
3. Tap **Permissions**
4. Enable required permissions

#### Android 14+ Partial Media Access
1. When prompted for storage, select **"Select photos and videos"**
2. Choose your music folder(s)
3. Tap **Allow** for selected items

### Revoking Permissions

To revoke a permission:
1. **Settings** → **Apps** → **Rhythm Music** → **Permissions**
2. Tap the permission to revoke
3. Select **Don't allow**

**Impact:**
- **Storage**: App won't play music
- **Notifications**: No playback controls
- **Internet**: No online features
- **Bluetooth**: No wireless audio

### Permission Reset

If permissions are messed up:
1. **Settings** → **Apps** → **Rhythm Music**
2. Tap **Storage & cache** → **Clear data**
3. Reopen app and grant permissions again

⚠️ **Warning:** This will reset all app settings and playlists. Backup first!

---

## 🔍 Permission Audit

### How to Verify What Rhythm Accesses

#### Using Android Permission Manager
1. **Settings** → **Privacy** → **Permission manager**
2. Tap each permission type (Files, Notifications, etc.)
3. Find **Rhythm Music** in the list
4. Verify granted permissions

#### Using ADB (Advanced)
```bash
# List all permissions for Rhythm
adb shell dumpsys package chromahub.rhythm.app | grep permission

# Check runtime permissions
adb shell pm list permissions -g
```

#### Using Apps
- **[App Manager](https://github.com/MuntashirAkon/AppManager)**: Open-source permission auditor
- **[Exodus Privacy](https://reports.exodus-privacy.eu.org/)**: Check app for trackers

---

## 🛡️ Privacy Best Practices

### Minimize Permissions
1. Grant only essential permissions (Storage, Audio Settings)
2. Deny optional permissions if you don't need features:
   - Internet → No online lyrics/artwork
   - Bluetooth → No wireless audio auto-play
   - Notifications → No playback controls in shade

### Use Firewall (Advanced)
Block internet access if you want offline-only:
- **[NetGuard](https://netguard.me/)**: No-root firewall
- **[AFWall+](https://github.com/ukanth/afwall)**: Requires root
- **[RethinkDNS](https://rethinkdns.com/)**: DNS + Firewall

### Scoped Storage (Android 11+)
Rhythm uses **Scoped Storage** on Android 11+:
- Access limited to music files only
- No access to documents, photos, or other file types
- Enhanced privacy and security

---

## ❓ FAQ

### Why does Rhythm need internet access?
For optional online features: lyrics (LRCLib), artwork (Deezer), and updates (GitHub). The app works fully offline without internet.

### Can I use Rhythm without granting notifications?
Yes, but you won't see playback controls in the notification shade. Player screen still works normally.

### Does Rhythm access my photos?
No. Rhythm does not request or require photo permissions. Custom artwork selection uses the secure system Android Photo Picker, granting access only to the specific image you pick.

### Why Bluetooth permissions?
To detect when you connect Bluetooth headphones/speakers and auto-resume playback. Optional feature.

### Is my listening data sent anywhere?
**No.** Rhythm stores all data locally on your device. Nothing is uploaded or shared.

### Can I audit the code?
**Yes!** Rhythm is fully open-source: [GitHub Repository](https://github.com/cromaguy/Rhythm)

---

## 🔗 Related Documentation

- [Installation Guide](https://github.com/cromaguy/Rhythm/wiki/Installation-Guide)
- [Getting Started](https://github.com/cromaguy/Rhythm/wiki/Getting-Started)
- [Troubleshooting](https://github.com/cromaguy/Rhythm/wiki/Troubleshooting)
- [Security Policy](https://github.com/cromaguy/Rhythm/blob/main/docs/SECURITY.md)

---

**Still have questions?** Ask in our [Telegram Community](https://t.me/RhythmSupport) or [Discord Server](https://discord.gg/XjPyUYPQYc).
