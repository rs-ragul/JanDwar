package `in`.jandwar.app.ui.theme

import androidx.compose.ui.graphics.Color

// ── Brand ────────────────────────────────────────────────────────────────────
val BrandIndigo = Color(0xFF1B3A8C)
val BrandIndigoDeep = Color(0xFF102354)
val BrandIndigoLight = Color(0xFF3459B8)
val BrandTeal = Color(0xFF0FA3A3)
val BrandTealLight = Color(0xFF3ECFCF)
val BrandSaffron = Color(0xFFFF8A1F)
val BrandSaffronSoft = Color(0xFFFFB067)

// ── Light surfaces ───────────────────────────────────────────────────────────
val Paper = Color(0xFFF6F8FC)
val SurfaceLight = Color(0xFFFFFFFF)
val SurfaceLightAlt = Color(0xFFEDF1F9)
val Ink = Color(0xFF141B2B)
val Muted = Color(0xFF5B6880)
val BorderLight = Color(0xFFDDE4F0)

// ── Dark surfaces ────────────────────────────────────────────────────────────
val InkDark = Color(0xFF0C111C)
val SurfaceDark = Color(0xFF161D2C)
val SurfaceDarkAlt = Color(0xFF1F2939)
val OnDark = Color(0xFFE7EBF2)
val MutedDark = Color(0xFF9AA7BD)
val BorderDark = Color(0xFF2C3849)

// ── Semantic ─────────────────────────────────────────────────────────────────
val Success = Color(0xFF15A55E)
val SuccessSoft = Color(0xFFDFF6EA)
val SuccessSoftDark = Color(0xFF123829)
val Warning = Color(0xFFD97706)
val WarningSoft = Color(0xFFFFF3E0)
val WarningSoftDark = Color(0xFF3A2A12)
val Error = Color(0xFFDC2626)
val ErrorSoft = Color(0xFFFDE8E8)

// ── Gradients ────────────────────────────────────────────────────────────────
val GradientStart = BrandIndigo
val GradientEnd = BrandTeal

/** Sector accent colours so course cards are visually distinguishable at a glance. */
val SectorColors: Map<String, Color> = mapOf(
    "agriculture" to Color(0xFF15A55E),
    "food_processing" to Color(0xFFE2761B),
    "construction" to Color(0xFF8B5CF6),
    "handloom_textile" to Color(0xFFDB2777),
    "apparel" to Color(0xFFDB2777),
    "electronics_automation" to Color(0xFF0EA5E9),
    "automotive" to Color(0xFF0EA5E9),
    "media_entertainment" to Color(0xFFF43F5E),
    "healthcare" to Color(0xFF14B8A6),
    "tourism_hospitality" to Color(0xFFF59E0B),
    "retail" to Color(0xFF6366F1),
    "logistics" to Color(0xFF64748B)
)

fun sectorColor(sector: String): Color =
    SectorColors[sector.lowercase()] ?: BrandIndigo
