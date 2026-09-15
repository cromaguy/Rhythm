# Troubleshooting Guide

This guide covers common issues and solutions for Rhythm Music Player.

## 🔍 Quick Diagnostics

Before diving into specific issues, try these general fixes:

1. **Restart the app**: Close completely and reopen
2. **Check permissions**: Settings → Apps → Rhythm → Permissions
3. **Update app**: Ensure you're on the latest version
4. **Clear cache**: Settings → Apps → Rhythm → Storage → Clear cache
5. **Rescan media**: Rhythm Settings → Library & Media → Media Scan

---

## 📁 Permission Issues

### Music Library Not Loading

**Symptoms:**
- Empty library after scanning
- "No songs found" message
- Can't play any music

**Solutions:**
1. **Grant storage permission manually:**
   - Android Settings → Apps → Rhythm → Permissions
   - Enable "Files and media" or "Storage"
   - Restart Rhythm

2. **Check folder access (Android 14+):**
   - You may have granted partial access
   - Re-grant full music folder access

3. **Verify music location:**
   - Ensure music is in standard locations (`/Music`, `/Download`)
   - Check if folders are blacklisted in Rhythm settings

4. **Re-scan media:**
   - Rhythm → Settings → Library & Media → Media Scan
   - Wait for scan to complete
   - Rhythm performs a **full scan** to reliably pick up all changes

### Notification Controls Not Working

**Symptoms:**
- No playback controls in notification
- Notification doesn't appear

**Solutions:**
1. **Grant notification permission:**
   - Settings → Apps → Rhythm → Permissions
   - Enable "Notifications"

2. **Check notification settings:**
   - Ensure notifications aren't blocked
   - Check Do Not Disturb settings

3. **Restart foreground service:**
   - Stop playback completely
   - Close app
   - Reopen and start playing

---

## 🎵 Playback Problems

### Music Won't Play

**Symptoms:**
- Song appears to load but no audio
- Playback immediately stops
- Error message appears

**Solutions:**
1. **Check audio format:**
   - See [Audio Formats Guide](https://github.com/cromaguy/Rhythm/wiki/Audio-Formats)
   - Unsupported formats won't play
   - Convert to FLAC or MP3

2. **Verify file integrity:**
   - Try playing in another app (VLC)
   - Re-download if corrupted

3. **Check audio output:**
   - Ensure volume isn't muted
   - Try different output device
   - Disconnect/reconnect Bluetooth

4. **Clear audio cache:**
   - Settings → Apps → Rhythm → Storage
   - Clear cache (not data)

### Audio Stuttering/Glitches

**Symptoms:**
- Crackling or popping sounds
- Intermittent pauses
- Audio cuts out

**Solutions:**
1. **Reduce background processes:**
   - Close other apps
   - Disable battery optimization for Rhythm

2. **Check storage space:**
   - Ensure sufficient free space (500MB+)
   - Clear cache if needed

3. **Disable audio effects:**
   - Turn off equalizer temporarily
   - Disable bass boost/virtualizer

4. **Try different audio buffer:**
   - Some devices need larger buffers
   - Check device manufacturer settings

### Gapless Playback Not Working

**Symptoms:**
- Brief silence between tracks
- Album tracks don't flow seamlessly

**Solutions:**
1. **Check format compatibility:**
   - Gapless works best with FLAC, MP3, AAC
   - Some formats don't support gapless

2. **Verify album metadata:**
   - Ensure tracks are tagged as same album
   - Check track numbers are sequential

3. **Update ExoPlayer:**
   - Update to latest Rhythm version
   - ExoPlayer improvements in newer releases

---

## 📊 Metadata & Artwork Issues

### Album Art Not Showing

**Symptoms:**
- Generic placeholder instead of album art
- Some albums show art, others don't

**Solutions:**
1. **Check embedded artwork:**
   - Use MP3Tag or similar to verify art is embedded
   - Embed artwork if missing

2. **Enable online artwork:**
   - Rhythm → Settings → Notifications & Services → API Management
   - Enable "Fetch album artwork from Deezer"

3. **Clear image cache:**
   - Settings → Apps → Rhythm → Storage → Clear cache
   - Rescan media library

4. **Check file permissions:**
   - Ensure Rhythm has audio access permission granted
   - Rescan media library in Settings

### Incorrect Metadata

**Symptoms:**
- Wrong song title, artist, or album
- Missing information
- Can't edit metadata

**Solutions:**
1. **Edit metadata in Rhythm:**
   - Long-press song → Song info → Edit
   - Update title, artist, album, etc.
   - Requires storage write permission

2. **Use desktop metadata editor:**
   - **Windows/Mac:** [Mp3tag](https://www.mp3tag.de/)
   - **Cross-platform:** [MusicBrainz Picard](https://picard.musicbrainz.org/)
   - Re-scan library after editing

3. **Check file encoding:**
   - Ensure UTF-8 encoding for tags
   - Special characters may cause issues

4. **Batch edit:**
   - Use MusicBrainz Picard for auto-tagging
   - Match files to online database

### Can't Edit Metadata

**Symptoms:**
- Edit option greyed out
- Changes don't save
- "Permission denied" error

**Solutions:**
1. **Grant write permission:**
   - Android Settings → Apps → Rhythm → Permissions
   - Ensure storage write access granted

2. **Check file location:**
   - Files on SD card may require special permissions
   - Try moving to internal storage

3. **Verify file permissions:**
   - Some files may be read-only
   - Check file properties in file manager

---

## 🎤 Lyrics Issues

### Lyrics Not Loading

**Symptoms:**
- "No lyrics found" message
- Lyrics screen is blank

**Solutions:**
1. **Enable lyrics sources:**
   - Rhythm → Settings → Audio & Effects → Lyrics
   - Enable LRCLib and embedded lyrics

2. **Check internet connection:**
   - LRCLib requires internet
   - Verify connection is active

3. **Try manual search:**
   - Tap "Search lyrics" button
   - Enter artist and title manually

4. **Check embedded lyrics:**
   - Use MP3Tag to verify lyrics are embedded
   - Embed manually if needed

### Lyrics Out of Sync

**Symptoms:**
- Lyrics appear too early/late
- Highlighting doesn't match audio

**Solutions:**
1. **Adjust sync offset:**
   - Tap lyrics settings icon
   - Use +/- buttons to adjust timing
   - Save offset for this track

2. **Try different source:**
   - Switch between LRCLib and embedded
   - Some sources have better timing

3. **Edit .lrc file:**
   - Use LRC editor to fix timestamps
   - Save corrected version

---

## 🔄 Update & Installation Issues

### Update Won't Install

**Symptoms:**
- "App not installed" error
- Installation blocked
- "Package conflicts" error

**Solutions:**
1. **Check signature match:**
   - Updates from different sources have different signatures
   - Uninstall and clean install if switching sources

2. **Enable unknown sources:**
   - Settings → Security → Install unknown apps
   - Enable for your browser/file manager

3. **Clear download cache:**
   - Delete old APK files
   - Re-download update

4. **Sufficient storage:**
   - Ensure 100MB+ free space
   - Clear unnecessary files

### Auto-Update Not Working

**Symptoms:**
- No update notifications
- Update check fails

**Solutions:**
1. **Check update settings:**
   - Rhythm → Settings → Updates & Info → Updates
   - Enable automatic update checking

2. **Verify internet access:**
   - GitHub access required
   - Check firewall/VPN settings

3. **Manual check:**
   - Settings → Updates & Info → About → Check for Updates
   - Download manually if needed

---

## 📱 Widget Issues

### Widget Not Updating

**Symptoms:**
- Widget shows old song
- Play/pause button doesn't work
- Widget appears frozen

**Solutions:**
1. **Restart widget:**
   - Remove widget from home screen
   - Re-add widget

2. **Check battery optimization:**
   - Settings → Apps → Rhythm → Battery
   - Set to "Unrestricted" or "Not optimized"

3. **Verify widget permissions:**
   - Widget requires notification access
   - Grant in app permissions

4. **Update WorkManager:**
   - Update to latest Rhythm version
   - Widget updates improved in recent releases

### Widget Shows Wrong Size

**Symptoms:**
- Widget appears stretched or cropped
- Controls cut off

**Solutions:**
1. **Resize widget:**
   - Long-press widget → Resize handles
   - Drag to correct size

2. **Try different layout:**
   - Remove and re-add widget
   - Choose appropriate size (2x1 to 5x5)

3. **Launcher compatibility:**
   - Some launchers have widget sizing issues
   - Try a different launcher

---

## 🎧 Bluetooth & Audio Output

### Auto-Play on Bluetooth Connect Not Working

**Symptoms:**
- Music doesn't resume when connecting Bluetooth
- No auto-play behavior

**Solutions:**
1. **Enable auto-resume:**
   - Rhythm → Settings → Queue & Playback → Playback
   - Enable "Resume on Device Reconnect"

2. **Grant Bluetooth permission:**
   - Settings → Apps → Rhythm → Permissions
   - Enable Bluetooth access

3. **Check device pairing:**
   - Ensure Bluetooth device is properly paired
   - Reconnect if needed

### Audio Routing Issues

**Symptoms:**
- Sound plays from wrong speaker
- Can't switch to Bluetooth
- Audio output stuck

**Solutions:**
1. **Select output via the system volume panel:**
   - Open the Android volume panel and tap the output icon
   - Choose the Bluetooth device

2. **Restart Bluetooth:**
   - Turn Bluetooth off and on
   - Reconnect device

3. **Clear audio cache:**
   - Restart Rhythm
   - Disconnect all Bluetooth devices
   - Reconnect desired device

### USB DAC Support & Android Popups

**Symptoms:**
- USB DAC is connected but no Android USB permission dialog (e.g., "Allow Rhythm to access [USB Device]?") appears.

**Explanation & Verification:**
1. **Silent Native Routing:** Rhythm routes audio to USB DACs natively using standard Android OS audio routing and Android 14's native `setPreferredMixerAttributes` API (App mode). Since this relies on official system-level APIs instead of custom user-space USB drivers, **no USB permission popup is expected or required.** The DAC receives high-resolution playback silently.
2. **Verify Playback:** You can verify the DAC is active by checking your DAC's hardware LED color/indicator or ensuring "DAC / USB Audio" settings are configured under **Experimental Features** in Settings.

---

## 💾 Backup & Restore

### Backup Fails

**Symptoms:**
- "Backup failed" error
- No backup file created

**Solutions:**
1. **Check storage permissions:**
   - Ensure write access granted
   - Verify destination folder exists

2. **Sufficient storage:**
   - Ensure adequate free space
   - Backups can be several MB

3. **Try different location:**
   - Use internal storage instead of SD card
   - Some folders may have restrictions

### Restore Doesn't Work

**Symptoms:**
- Playlists don't appear after restore
- Settings not restored
- "Invalid backup file" error

**Solutions:**
1. **Verify backup file:**
   - Ensure file isn't corrupted
   - Check file size is reasonable

2. **Compatible version:**
   - Restore from backup created by similar Rhythm version
   - Major version differences may cause issues

3. **Manual restore:**
   - Export playlists as M3U/PLS
   - Import one by one

---

## 🔧 Advanced Troubleshooting

### Complete App Reset

If nothing else works:

1. **Backup important data:**
   - Export all playlists
   - Save settings screenshots

2. **Clear app data:**
   - Settings → Apps → Rhythm → Storage
   - Clear cache → Clear data

3. **Reinstall app:**
   - Uninstall Rhythm completely
   - Download fresh APK
   - Install and set up again

⚠️ **Warning:** This erases all app data, playlists, and settings!

### Logcat Debug (Advanced)

For developers or persistent issues:

```bash
# Capture Rhythm logs
adb logcat | grep "chromahub.rhythm"

# Save to file
adb logcat -d > rhythm_log.txt

# Filter for errors only
adb logcat *:E | grep "chromahub.rhythm"
```

Share logs in [GitHub Issues](https://github.com/cromaguy/Rhythm/issues) for debugging.

---

## 🆘 Still Need Help?

### Community Support
- **[Telegram Group](https://t.me/RhythmSupport)**: Live community help
- **[Discord Server](https://discord.gg/XjPyUYPQYc)**: Community chat
- **[GitHub Discussions](https://github.com/cromaguy/Rhythm/discussions)**: Q&A forum

### Bug Reports
- **[GitHub Issues](https://github.com/cromaguy/Rhythm/issues)**: Report bugs

### Include This Information
When asking for help, provide:
- Android version (e.g., Android 14)
- Device model (e.g., Pixel 7)
- Rhythm version (Settings → Updates & Info → About)
- Detailed description of issue
- Steps to reproduce
- Screenshots/logs if possible

---

**Pro tip:** Check [FAQ](https://github.com/cromaguy/Rhythm/wiki/FAQ) for quick answers to common questions!
