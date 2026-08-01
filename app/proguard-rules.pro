# Preserve line numbers and source file names for readable crash reports
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Preserve Signature and Annotations for Retrofit, Room, and Gson reflection
-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod

# ──────────────────────────────
# Gson serialization models
# ──────────────────────────────

# Keep model classes used for Gson serialization in shared settings, playlists, etc.
-keep class chromahub.rhythm.app.shared.data.model.** { *; }

# Keep PlaylistImportExportUtils inner data classes (PlaylistExportData, PlaylistSongEntry)
# used for JSON playlist export/import via Gson reflection
-keep class chromahub.rhythm.app.util.PlaylistImportExportUtils$* { *; }

# Keep GitHub API, Rhythm lyrics API, and other network response models
-keep class chromahub.rhythm.app.network.** { *; }

# Keep ColorExtractor models used for Theme parsing (Gson targets)
-keep class chromahub.rhythm.app.util.ExtractedColors { *; }

# Keep updater local state models (Gson targets)
-keep class chromahub.rhythm.app.shared.presentation.viewmodel.DownloadState { *; }

# Keep streaming models (Gson targets)
-keep class chromahub.rhythm.app.features.streaming.domain.model.** { *; }

# Keep PlaybackEvent inside PlaybackStatsRepository (nested Gson serialization target)
-keep class chromahub.rhythm.app.shared.data.repository.PlaybackStatsRepository$PlaybackEvent { *; }

# ART on Android 16 rejects the R8-optimized form of this oversized Compose
# entry point with a VerifyError when the full player is first composed.
-keep class chromahub.rhythm.app.shared.presentation.screens.player.MaterialPlayerScreenKt { *; }

# Keep classes and fields annotated with @SerializedName to prevent Gson reflection issues
-keep class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# ──────────────────────────────
# Room Database Configuration
# ──────────────────────────────

# Keep database entities and DAOs specifically so Room can generate and link them
-keep class chromahub.rhythm.app.features.local.data.database.entity.** { *; }
-keep class * implements androidx.room.RoomDatabase
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Database class * { *; }

# ──────────────────────────────
# Third-Party Libraries (No native rules in AAR/JAR)
# ──────────────────────────────

# jaudiotagger (metadata extraction relies heavily on reflection and dynamic class loading)
-keep class org.jaudiotagger.** { *; }
-dontwarn org.jaudiotagger.**

# Netty/Ktor (Netty has complex runtime lookups)
-keep class io.netty.** { *; }
-dontwarn io.netty.**
-keep class io.ktor.** { *; }
-dontwarn io.ktor.**

# ──────────────────────────────
# Warnings Suppression
# ──────────────────────────────
-dontwarn java.lang.invoke.**
-dontwarn javax.annotation.**
-dontwarn org.codehaus.mojo.animal_sniffer.**
-dontwarn java.awt.**
-dontwarn javax.sound.**
-dontwarn javax.swing.**
