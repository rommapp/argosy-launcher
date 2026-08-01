// AUTO-GENERATED. DO NOT EDIT.
// Source: design-system-docs/tokens.json
// Run: node scripts/gen-tokens.mjs

@file:Suppress("unused")

package com.nendo.argosy.ui.theme.generated

import androidx.compose.ui.graphics.Color

object ColorTokens {
    object Scheme {
        object Dark {
            val primary = Color(0xFF40C6D6)
            val secondary = Color(0xFF26A69A)
            val surface = Color(0xFF13141A)
            val surfaceVariant = Color(0xFF1C1E26)
            val surfaceElevated = Color(0xFF262834)
            val background = Color(0xFF050507)
            val onSurface = Color(0xFFE6E8EC)
            val outline = Color(0x1FFFFFFF)
            val outlineVariant = Color(0x0FFFFFFF)
        }
        object Light {
            val primary = Color(0xFF007C91)
            val secondary = Color(0xFF00766C)
            val surface = Color(0xFFF3F4F8)
            val surfaceVariant = Color(0xFFE9EBF0)
            val surfaceElevated = Color(0xFFDEE1E9)
            val background = Color(0xFFFBFBFD)
            val onSurface = Color(0xFF1C1B1F)
            val outline = Color(0x1F000000)
            val outlineVariant = Color(0x0F000000)
        }
        object DebugOverrides {
            object Dark {
                val primary = Color(0xFFFF7043)
            }
            object Light {
                val primary = Color(0xFFC63F17)
            }
        }
    }

    object Semantic {
        object Dark {
            val success = Color(0xFF66BB6A)
            val warning = Color(0xFFFF7043)
            val info = Color(0xFF5C6BC0)
            val progress = Color(0xFF3FD9A8)
        }
        object Light {
            val success = Color(0xFF388E3C)
            val warning = Color(0xFFC63F17)
            val info = Color(0xFF26418F)
            val progress = Color(0xFF189C76)
        }
    }

    object Domain {
        val ratingStar = Color(0xFFFFD700)
        val difficulty = Color(0xFFE53935)
        val trophyAmber = Color(0xFFFFB300)
        val favoriteStar = Color(0xFFFFC107)
        object AchievementTier {
            val hardcore = Color(0xFFFFD700)
            val softcore = Color(0xFFCD7F32)
        }
        object Completion {
            object Playing {
                val dark = Color(0xFF5C6BC0)
                val light = Color(0xFF26418F)
            }
            object Beaten {
                val dark = Color(0xFF66BB6A)
                val light = Color(0xFF388E3C)
            }
            object Completed {
                val dark = Color(0xFFFFB300)
                val light = Color(0xFFC77800)
            }
            object Retired {
                val dark = Color(0xFF9E9E9E)
                val light = Color(0xFF757575)
            }
            object Never {
                val dark = Color(0xFF757575)
                val light = Color(0xFF9E9E9E)
            }
        }
        object SocialBrand {
            val accent = Color(0xFF6366F1)
        }
        object Presence {
            val online = Color(0xFF22C55E)
            val away = Color(0xFFFBBF24)
            val offline = Color(0xFF6B7280)
        }
        object Battery {
            val low = Color(0xFFE53935)
            val charging = Color(0xFF4CAF50)
        }
        object Code {
            val background = Color(0x1A888888)
        }
    }

    val accentPresets: List<AccentPreset> = listOf(
    )
}

data class AccentPreset(val dark: Color, val light: Color)
