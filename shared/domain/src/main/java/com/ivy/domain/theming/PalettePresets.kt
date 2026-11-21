package com.ivy.domain.theming

/**
 * Starting points for the colour picker. A user can take any of these and then
 * override individual colours; the result is stored as a "custom" palette.
 */
object PalettePresets {

    val Default = Palette.Default

    val Forest = Palette(
        id = "forest",
        name = "Forest",
        primary = ColorHex(0xFF2E7D54),
        secondary = ColorHex(0xFF7CB342),
        income = ColorHex(0xFF43A047),
        expense = ColorHex(0xFFC1553B),
        warning = ColorHex(0xFFD99B2E),
    )

    val Ocean = Palette(
        id = "ocean",
        name = "Ocean",
        primary = ColorHex(0xFF1E6FB8),
        secondary = ColorHex(0xFF00A6A6),
        income = ColorHex(0xFF17A398),
        expense = ColorHex(0xFFE2504F),
        warning = ColorHex(0xFFE8A33D),
    )

    val Slate = Palette(
        id = "slate",
        name = "Slate",
        primary = ColorHex(0xFF55606E),
        secondary = ColorHex(0xFF8A94A6),
        income = ColorHex(0xFF4F8A6B),
        expense = ColorHex(0xFFA85D5D),
        warning = ColorHex(0xFF9C8450),
    )

    val Amber = Palette(
        id = "amber",
        name = "Amber",
        primary = ColorHex(0xFFD98324),
        secondary = ColorHex(0xFFC25E00),
        income = ColorHex(0xFF3F9B52),
        expense = ColorHex(0xFFCB3F3F),
        warning = ColorHex(0xFFE0A526),
    )

    val Rose = Palette(
        id = "rose",
        name = "Rosé",
        primary = ColorHex(0xFFC2457A),
        secondary = ColorHex(0xFFE0749B),
        income = ColorHex(0xFF3E9E85),
        expense = ColorHex(0xFFD4455B),
        warning = ColorHex(0xFFDB9A3E),
    )

    val Mono = Palette(
        id = "mono",
        name = "Mono",
        primary = ColorHex(0xFF3A3A3F),
        secondary = ColorHex(0xFF6E6E76),
        income = ColorHex(0xFF4A4A50),
        expense = ColorHex(0xFF7A7A82),
        warning = ColorHex(0xFF95959D),
    )

    val HighContrast = Palette(
        id = "high_contrast",
        name = "High contrast",
        primary = ColorHex(0xFF0033CC),
        secondary = ColorHex(0xFF006644),
        income = ColorHex(0xFF006633),
        expense = ColorHex(0xFFCC0000),
        warning = ColorHex(0xFF996600),
    )

    val all: List<Palette> = listOf(
        Default,
        Forest,
        Ocean,
        Slate,
        Amber,
        Rose,
        Mono,
        HighContrast,
    )
}
