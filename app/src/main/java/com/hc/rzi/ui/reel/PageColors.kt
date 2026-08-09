package com.hc.rzi.ui.reel

import androidx.compose.ui.graphics.Color

data class PageColors(
    val top: Color,
    val middle: Color,
    val bottom: Color,
    val onBackground: Color,
    val onBackgroundMuted: Color,
)

val lightPalettes = listOf(
    PageColors(
        Color(0xFFDDBDB8),
        Color(0xFFDDBDB8),
        Color(0xFFCEA9A2),
        Color(0xFF6B2E2E),
        Color(0xFF7D3838)
    ),
    PageColors(
        Color(0xFFB8CDB5),
        Color(0xFFB8CDB5),
        Color(0xFFA3BFA0),
        Color(0xFF2E4A30),
        Color(0xFF3D5C3E)
    ),
    PageColors(
        Color(0xFFB0C4DE),
        Color(0xFFB0C4DE),
        Color(0xFF96B0D0),
        Color(0xFF2A4A6B),
        Color(0xFF3A5C7D)
    ),
    PageColors(
        Color(0xFFC4B3D0),
        Color(0xFFC4B3D0),
        Color(0xFFAE98C0),
        Color(0xFF4A3068),
        Color(0xFF5C4078)
    ),
    PageColors(
        Color(0xFFD8D0A8),
        Color(0xFFD8D0A8),
        Color(0xFFCCC290),
        Color(0xFF6B6028),
        Color(0xFF7D7238)
    ),
    PageColors(
        Color(0xFFA8CDD0),
        Color(0xFFA8CDD0),
        Color(0xFF90BCC2),
        Color(0xFF284C50),
        Color(0xFF385E62)
    ),
    PageColors(
        Color(0xFFD4C4A8),
        Color(0xFFD4C4A8),
        Color(0xFFC8B290),
        Color(0xFF6B5028),
        Color(0xFF7D6038)
    ),
    PageColors(
        Color(0xFFB8B0CC),
        Color(0xFFB8B0CC),
        Color(0xFFA498BC),
        Color(0xFF382860),
        Color(0xFF483870)
    ),
)

val darkPalettes = listOf(
    PageColors(
        Color(0xFF4A2828),
        Color(0xFF3E2038),
        Color(0xFF302040),
        Color(0xFFD8B0A8),
        Color(0xFFC8A0A0)
    ),
    PageColors(
        Color(0xFF283E2A),
        Color(0xFF203830),
        Color(0xFF203040),
        Color(0xFFA8C8A5),
        Color(0xFF98B8B0)
    ),
    PageColors(
        Color(0xFF203048),
        Color(0xFF242848),
        Color(0xFF2C2850),
        Color(0xFFB0C0D8),
        Color(0xFFA0B0C8)
    ),
    PageColors(
        Color(0xFF382848),
        Color(0xFF3E2040),
        Color(0xFF482838),
        Color(0xFFC8B0D8),
        Color(0xFFB8A0C8)
    ),
    PageColors(
        Color(0xFF483828),
        Color(0xFF403020),
        Color(0xFF382028),
        Color(0xFFD0B8A0),
        Color(0xFFC0A890)
    ),
    PageColors(
        Color(0xFF203838),
        Color(0xFF203830),
        Color(0xFF283828),
        Color(0xFFA0C0C8),
        Color(0xFF90B0B8)
    ),
    PageColors(
        Color(0xFF303840),
        Color(0xFF283038),
        Color(0xFF202830),
        Color(0xFFD0D8E0),
        Color(0xFFB8C0C8)
    ),
    PageColors(
        Color(0xFF383028),
        Color(0xFF383028),
        Color(0xFF403830),
        Color(0xFFD8D0C8),
        Color(0xFFC0B8B0)
    ),
    PageColors(
        Color(0xFF242848),
        Color(0xFF283050),
        Color(0xFF303858),
        Color(0xFFB0B0D0),
        Color(0xFF9898C0)
    ),
    PageColors(
        Color(0xFF3E2040),
        Color(0xFF482848),
        Color(0xFF502838),
        Color(0xFFD8B0C0),
        Color(0xFFC8A0B0)
    ),
)
