/*
 * SPDX-FileCopyrightText: 2024-2026 Anjishnu Nandi <https://github.com/cromaguy>
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package chromahub.rhythm.app.shared.presentation.components.icons

/**
 * Material Symbols icon definitions for the Rhythm app.
 *
 * All icons are represented as [MaterialSymbolIcon] instances using the
 * Material Symbols Outlined variable font. Icons support dynamic axes
 * for fill, weight, grade, and optical size through variable font technology.
 *
 * Usage:
 * ```kotlin
 * Icon(
 *     imageVector = RhythmIcons.Play,
 *     contentDescription = "Play"
 * )
 * ```
 *
 * For filled variants, use the `.filled()` extension:
 * ```kotlin
 * Icon(
 *     imageVector = RhythmIcons.Home.filled(),
 *     contentDescription = "Home"
 * )
 * ```
 */
object RhythmIcons {

    // ═══════════════════════════════════════════════════
    //  Player Controls
    // ═══════════════════════════════════════════════════
    object Player {
        val Play = MaterialSymbolIcon("play_arrow", defaultWeight = 500)
        val PlayCircle = MaterialSymbolIcon("play_circle_filled", defaultWeight = 500)
        val Pause = MaterialSymbolIcon("pause", defaultWeight = 500)
        val SkipNext = MaterialSymbolIcon("skip_next", defaultWeight = 500)
        val SkipPrevious = MaterialSymbolIcon("skip_previous", defaultWeight = 500)
        val Replay10 = MaterialSymbolIcon("replay_10", defaultWeight = 500)
        val Forward10 = MaterialSymbolIcon("forward_10", defaultWeight = 500)
        val Shuffle = MaterialSymbolIcon("shuffle", defaultWeight = 500)
        val Repeat = MaterialSymbolIcon("repeat", defaultWeight = 500)
        val RepeatOne = MaterialSymbolIcon("repeat_one", defaultWeight = 500)
        val Lyrics = MaterialSymbolIcon("lyrics")
        val Stop = MaterialSymbolIcon("stop", filled = true)

        // Volume controls
        val VolumeUp = MaterialSymbolIcon("volume_up")
        val VolumeDown = MaterialSymbolIcon("volume_down")
        val VolumeMute = MaterialSymbolIcon("volume_mute")
        val VolumeOff = MaterialSymbolIcon("volume_off")

        // Additional player controls
        val Queue = MaterialSymbolIcon("queue_music")
        val Equalizer = MaterialSymbolIcon("equalizer")
        val Speed = MaterialSymbolIcon("speed")
        val Timer = MaterialSymbolIcon("timer")
        val FavoriteOutlined = MaterialSymbolIcon("favorite")

    }

    // ═══════════════════════════════════════════════════
    //  Navigation
    // ═══════════════════════════════════════════════════
    object Navigation {
        val Home = MaterialSymbolIcon("home", filled = true, defaultWeight = 500)
        val HomeOutlined = MaterialSymbolIcon("home", defaultWeight = 500)
        val Search = MaterialSymbolIcon("search", filled = true, defaultWeight = 500)
        val SearchOutlined = MaterialSymbolIcon("search", defaultWeight = 500)
        val Library = MaterialSymbolIcon("library_music", filled = true, defaultWeight = 500)
        val LibraryOutlined = MaterialSymbolIcon("library_music", defaultWeight = 500)
        val Settings = MaterialSymbolIcon("settings", filled = true, defaultWeight = 500)
        val SettingsOutlined = MaterialSymbolIcon("settings", defaultWeight = 500)

        // Navigation actions
        val Back = MaterialSymbolIcon("arrow_back", defaultWeight = 500)
        val Forward = MaterialSymbolIcon("arrow_forward", defaultWeight = 500)
        val Close = MaterialSymbolIcon("close", defaultWeight = 500)
        val ExpandMore = MaterialSymbolIcon("expand_more", defaultWeight = 500)
        val ExpandLess = MaterialSymbolIcon("expand_less", defaultWeight = 500)
        val ArrowDropDown = MaterialSymbolIcon("arrow_drop_down", defaultWeight = 500)
        val KeyboardArrowDown = MaterialSymbolIcon("keyboard_arrow_down", defaultWeight = 500)
        val KeyboardArrowUp = MaterialSymbolIcon("keyboard_arrow_up", defaultWeight = 500)
        val ArrowUpward = MaterialSymbolIcon("arrow_upward", defaultWeight = 500)
        val ArrowDownward = MaterialSymbolIcon("arrow_downward", defaultWeight = 500)
    }

    // ═══════════════════════════════════════════════════
    //  Music Items
    // ═══════════════════════════════════════════════════
    object Music {
        val Song = MaterialSymbolIcon("music_note", filled = true)
        val SongOutlined = MaterialSymbolIcon("music_note")
        val Album = MaterialSymbolIcon("album", filled = true)
        val AlbumOutlined = MaterialSymbolIcon("album")
        val Artist = MaterialSymbolIcon("person", filled = true)
        val ArtistOutlined = MaterialSymbolIcon("person")
        val Playlist = MaterialSymbolIcon("playlist_play")
        val PlaylistOutlined = MaterialSymbolIcon("playlist_play")
        val MusicNote = MaterialSymbolIcon("music_note")
        val Audiotrack = MaterialSymbolIcon("audio_file")
        val MusicOff = MaterialSymbolIcon("music_off")
    }

    // ═══════════════════════════════════════════════════
    //  Actions
    // ═══════════════════════════════════════════════════
    object Actions {
        val Favorite = MaterialSymbolIcon("favorite", filled = true)
        val FavoriteOutlined = MaterialSymbolIcon("favorite")
        val Add = MaterialSymbolIcon("add")
        val Remove = MaterialSymbolIcon("remove")
        val Edit = MaterialSymbolIcon("edit")
        val Delete = MaterialSymbolIcon("delete")
        val Check = MaterialSymbolIcon("check")
        val More = MaterialSymbolIcon("more_vert")
        val List = MaterialSymbolIcon("list")
        val Apps = MaterialSymbolIcon("apps")
        val Refresh = MaterialSymbolIcon("refresh")
        val Download = MaterialSymbolIcon("download")
        val Tune = MaterialSymbolIcon("tune")
        val Sort = MaterialSymbolIcon("sort")
        val Update = MaterialSymbolIcon("update")
        val Info = MaterialSymbolIcon("info")
        val Pushpin = MaterialSymbolIcon("push_pin", filled = true)
        val PinOutline = MaterialSymbolIcon("push_pin")
        val ArrowUpward = MaterialSymbolIcon("arrow_upward")
        val Share = MaterialSymbolIcon("share")
        val ContentCopy = MaterialSymbolIcon("content_copy")
        val FilterList = MaterialSymbolIcon("filter_list")
        val SelectAll = MaterialSymbolIcon("select_all")
        val Block = MaterialSymbolIcon("block")
        val SwapVert = MaterialSymbolIcon("swap_vert")
        val DragHandle = MaterialSymbolIcon("drag_handle")
        val Restore = MaterialSymbolIcon("restore")
    }

    // ═══════════════════════════════════════════════════
    //  Devices
    // ═══════════════════════════════════════════════════
    object Devices {
        val Bluetooth = MaterialSymbolIcon("bluetooth", filled = true)
        val BluetoothOutlined = MaterialSymbolIcon("bluetooth")
        val Headphones = MaterialSymbolIcon("headphones", filled = true)
        val HeadphonesOutlined = MaterialSymbolIcon("headphones")
        val Speaker = MaterialSymbolIcon("speaker", filled = true)
        val SpeakerOutlined = MaterialSymbolIcon("speaker")
        val Cast = MaterialSymbolIcon("cast")
        val CastConnected = MaterialSymbolIcon("cast_connected")
        val Location = MaterialSymbolIcon("place", filled = true)
        val LocationOutlined = MaterialSymbolIcon("place")
        val Usb = MaterialSymbolIcon("usb", filled = true)
        val UsbOutlined = MaterialSymbolIcon("usb")
    }

    // ═══════════════════════════════════════════════════
    //  System & Status
    // ═══════════════════════════════════════════════════
    object System {
        val Warning = MaterialSymbolIcon("warning", filled = true)
        val BugReport = MaterialSymbolIcon("bug_report", filled = true)
        val Security = MaterialSymbolIcon("security", filled = true)
        val AccessTime = MaterialSymbolIcon("schedule", filled = true)
        val Notifications = MaterialSymbolIcon("notifications")
        val NotificationsOff = MaterialSymbolIcon("notifications_off")
        val Visibility = MaterialSymbolIcon("visibility")
        val VisibilityOff = MaterialSymbolIcon("visibility_off")
        val DarkMode = MaterialSymbolIcon("dark_mode", filled = true)
        val LightMode = MaterialSymbolIcon("light_mode", filled = true)
        val Language = MaterialSymbolIcon("language")
        val Public = MaterialSymbolIcon("public", filled = true)
        val Code = MaterialSymbolIcon("code")
        val ExitToApp = MaterialSymbolIcon("exit_to_app")
        val Storage = MaterialSymbolIcon("storage")
        val CloudUpload = MaterialSymbolIcon("cloud_upload")
        val CloudDownload = MaterialSymbolIcon("cloud_download")
    }

    // ═══════════════════════════════════════════════════
    //  Content & Media
    // ═══════════════════════════════════════════════════
    object Content {
        val Palette = MaterialSymbolIcon("palette", filled = true)
        val WavingHand = MaterialSymbolIcon("waving_hand", filled = true)
        val GridView = MaterialSymbolIcon("grid_view", filled = true)
        val FormatListNumbered = MaterialSymbolIcon("format_list_numbered", filled = true)
        val SortByAlpha = MaterialSymbolIcon("sort_by_alpha", filled = true)
        val Folder = MaterialSymbolIcon("folder", filled = true)
        val FolderOpen = MaterialSymbolIcon("folder_open")
        val InsertDriveFile = MaterialSymbolIcon("description")
        val Image = MaterialSymbolIcon("image")
        val Category = MaterialSymbolIcon("category")
        val DateRange = MaterialSymbolIcon("date_range")
        val AutoAwesome = MaterialSymbolIcon("auto_awesome")
        val TrendingUp = MaterialSymbolIcon("trending_up")
        val BarChart = MaterialSymbolIcon("bar_chart")
        val CalendarMonth = MaterialSymbolIcon("calendar_month")
    }

    // ═══════════════════════════════════════════════════
    //  Connectivity
    // ═══════════════════════════════════════════════════
    object Connectivity {
        val Wifi = MaterialSymbolIcon("wifi")
        val WifiOff = MaterialSymbolIcon("wifi_off")
        val Telegram = MaterialSymbolIcon("chat") // Using chat as Telegram substitute
        val Link = MaterialSymbolIcon("link")
        val OpenInNew = MaterialSymbolIcon("open_in_new")
    }

    // ═══════════════════════════════════════════════════
    //  Switch / Toggle
    // ═══════════════════════════════════════════════════
    object Toggle {
        val CheckCircle = MaterialSymbolIcon("check_circle", filled = true)
        val RadioButtonChecked = MaterialSymbolIcon("radio_button_checked")
        val RadioButtonUnchecked = MaterialSymbolIcon("radio_button_unchecked")
        val ToggleOn = MaterialSymbolIcon("toggle_on")
        val ToggleOff = MaterialSymbolIcon("toggle_off")
    }

    // ═══════════════════════════════════════════════════
    //  Legacy flat aliases (backward compatibility)
    //  Preserves existing `RhythmIcons.Play` call sites
    // ═══════════════════════════════════════════════════

    // Player controls
    val Play = Player.Play
    val PlayCircle = Player.PlayCircle
    val Pause = Player.Pause
    val SkipNext = Player.SkipNext
    val SkipPrevious = Player.SkipPrevious
    val Replay10 = Player.Replay10
    val Forward10 = Player.Forward10
    val Shuffle = Player.Shuffle
    val Repeat = Player.Repeat
    val RepeatOne = Player.RepeatOne
    val VolumeUp = Player.VolumeUp
    val VolumeDown = Player.VolumeDown
    val VolumeMute = Player.VolumeMute
    val VolumeOff = Player.VolumeOff
    val Stop = Player.Stop

    // Navigation
    val Home = Navigation.HomeOutlined
    val HomeFilled = Navigation.Home
    val Search = Navigation.SearchOutlined
    val SearchFilled = Navigation.Search
    val Library = Navigation.LibraryOutlined
    val Settings = Navigation.SettingsOutlined
    val SettingsFilled = Navigation.Settings
    val Back = Navigation.Back
    val Forward = Navigation.Forward
    val Close = Navigation.Close
    val ArrowRight = Navigation.Forward
    val ExpandMore = Navigation.ExpandMore
    val ExpandLess = Navigation.ExpandLess
    val ArrowDropDown = Navigation.ArrowDropDown
    val KeyboardArrowDown = Navigation.KeyboardArrowDown
    val ArrowUpward = Navigation.ArrowUpward
    val ArrowDownward = Navigation.ArrowDownward

    // Music items
    val Song = Music.SongOutlined
    val SongFilled = Music.Song
    val Album = Music.AlbumOutlined
    val AlbumFilled = Music.Album
    val Artist = Music.ArtistOutlined
    val ArtistFilled = Music.Artist
    val Playlist = Music.PlaylistOutlined
    val PlaylistFilled = Music.Playlist
    val MusicNote = Music.MusicNote
    val MusicOff = Music.MusicOff

    // Mood & moments
    val Energy = Player.Equalizer
    val Relax = Devices.HeadphonesOutlined
    val Focus = Player.Timer

    // System
    val Notifications = System.Notifications

    // Actions
    val Favorite = Actions.FavoriteOutlined
    val FavoriteFilled = Actions.Favorite
    val Add = Actions.Add
    val Remove = Actions.Remove
    val Edit = Actions.Edit
    val Delete = Actions.Delete
    val AddToPlaylist = MaterialSymbolIcon("playlist_add")
    val AddToQueue = MaterialSymbolIcon("add_to_queue")
    val More = Actions.More
    val Queue = Player.Queue
    val Check = Actions.Check
    val Download = Actions.Download
    val List = Actions.List
    val AppsGrid = Actions.Apps
    val Refresh = Actions.Refresh
    val Telegram = Connectivity.Telegram
    val Equalizer = Player.Equalizer
    val Pushpin = Actions.Pushpin
    val PinOutline = Actions.PinOutline
    val Tune = Actions.Tune
    val Share = Actions.Share
    val Block = Actions.Block
    val ContentCopy = Actions.ContentCopy
    val Info = Actions.Info
    val Sort = Actions.Sort

    // System (flat)
    val Warning = System.Warning
    val BugReport = System.BugReport
    val Security = System.Security
    val AccessTime = System.AccessTime
    val DarkMode = System.DarkMode
    val Public = System.Public
    val ExitToApp = System.ExitToApp
    val Language = System.Language
    val Visibility = System.Visibility
    val VisibilityOff = System.VisibilityOff
    val CloudUpload = System.CloudUpload
    val CloudDownload = System.CloudDownload
    val Code = System.Code

    // Content (flat)
    val Palette = Content.Palette
    val WavingHand = Content.WavingHand
    val GridView = Content.GridView
    val FormatListNumbered = Content.FormatListNumbered
    val SortByAlpha = Content.SortByAlpha
    val Folder = Content.Folder
    val FolderOpen = Content.FolderOpen
    val InsertDriveFile = Content.InsertDriveFile
    val Image = Content.Image
    val Category = Content.Category
    val DateRange = Content.DateRange
    val AutoAwesome = Content.AutoAwesome
    val TrendingUp = Content.TrendingUp
    val BarChart = Content.BarChart
    val CalendarMonth = Content.CalendarMonth

    // Location
    val Location = Devices.LocationOutlined
    val LocationFilled = Devices.Location

    // Audio devices
    val Bluetooth = Devices.BluetoothOutlined
    val BluetoothFilled = Devices.Bluetooth
    val Headphones = Devices.HeadphonesOutlined
    val HeadphonesFilled = Devices.Headphones
    val Speaker = Devices.SpeakerOutlined
    val SpeakerFilled = Devices.Speaker
    val Cast = Devices.Cast
    val CastConnected = Devices.CastConnected

    // Toggle
    val CheckCircle = Toggle.CheckCircle
    val RadioButtonChecked = Toggle.RadioButtonChecked
    val RadioButtonUnchecked = Toggle.RadioButtonUnchecked

    // Additional icons used across the app
    val Update = Actions.Update
    val SystemUpdate = MaterialSymbolIcon("system_update", filled = true)
    val Restore = Actions.Restore
    val FilterList = Actions.FilterList
    val SwapVert = Actions.SwapVert
    val DragHandle = Actions.DragHandle
    val SelectAll = Actions.SelectAll
    val OpenInNew = Connectivity.OpenInNew
    val Link = Connectivity.Link
    val Storage = System.Storage
}
