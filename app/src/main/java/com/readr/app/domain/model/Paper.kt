package com.readr.app.domain.model

/**
 * Paper Studio: the reading surfaces READR prints its books on.
 *
 * Default per the product requirements: WARM_CREAM with the paper grain
 * texture at LOW intensity, selected automatically on first launch.
 */
enum class PaperTheme(val label: String, val bg: Long, val text: Long, val accent: Long, val isDark: Boolean) {
    WARM_CREAM("Warm Cream Paper", 0xFFFAF3E3, 0xFF3A3128, 0xFF7A9B76, false),
    CLASSIC_WHITE("Classic White Paper", 0xFFFEFEFC, 0xFF232320, 0xFF6E8E6A, false),
    VINTAGE("Vintage Paper", 0xFFF2E3C5, 0xFF52432E, 0xFF8B6F52, false),
    RECYCLED("Recycled Paper", 0xFFE9E6D9, 0xFF39382F, 0xFF708060, false),
    PARCHMENT("Parchment", 0xFFF6E7C8, 0xFF5B4632, 0xFF8B6F52, false),
    SEPIA("Sepia Paper", 0xFFEFDFBB, 0xFF5C4423, 0xFF9C6B3F, false),
    SOFT_GRAY("Soft Gray", 0xFFE8E8E4, 0xFF2E2E2C, 0xFF6F7F6B, false),
    DARK("Dark Reading Mode", 0xFF201E1A, 0xFFD8CFBC, 0xFFA9C2A3, true),
}

/** Paper grain intensity drawn behind the reading text. */
enum class TextureIntensity(val label: String, val alpha: Float) {
    LOW("Low", 0.35f),
    MEDIUM("Medium", 0.60f),
    HIGH("High", 0.90f),
}

/** Reading typography families (mapped to system fonts, keeps the APK small). */
enum class ReaderFont(val label: String) {
    SERIF("Serif"),
    SANS("Sans"),
    MONO("Mono"),
    DEFAULT("Default"),
}

enum class AppThemeMode(val label: String) {
    SYSTEM("System"),
    LIGHT("Light"),
    DARK("Dark"),
}

enum class LibraryViewMode { GRID, LIST }

enum class LibrarySort(val label: String) {
    TITLE("Title"),
    ADDED("Recently added"),
    LAST_OPENED("Last opened"),
    FORMAT("Format"),
}

/**
 * Everything that controls how a document is read.
 */
data class ReadingPrefs(
    val paperTheme: PaperTheme = PaperTheme.WARM_CREAM,
    val textureIntensity: TextureIntensity = TextureIntensity.LOW,
    val fontSizeSp: Int = 17,
    val font: ReaderFont = ReaderFont.SERIF,
    val lineSpacing: Float = 1.4f,
    val marginDp: Int = 24,
    val brightnessGestureEnabled: Boolean = true,
    val gestureSensitivity: Float = 1.0f,
    val systemBrightnessMode: Boolean = false,
    /** Warm (orange) tint over the page for evening reading. */
    val warmFilter: Boolean = false,
    /** Eye Comfort custom colours; null = use the paper theme colours. */
    val customBackground: Long? = null,
    val customTextColor: Long? = null,
    val pdfDarkFilter: Boolean = false,
    val pdfFitWidth: Boolean = true,
)

data class AppPrefs(
    val appThemeMode: AppThemeMode = AppThemeMode.SYSTEM,
    val libraryView: LibraryViewMode = LibraryViewMode.GRID,
    val librarySort: LibrarySort = LibrarySort.LAST_OPENED,
)
