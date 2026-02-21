package com.example.edunet.ui.theme

import androidx.compose.ui.graphics.Color

// ─── Core B&W Palette ─────────────────────────────────────────────────────────
val Black       = Color(0xFF000000)
val White       = Color(0xFFFFFFFF)

// Grays
val Gray950     = Color(0xFF0A0A0A)   // deepest card bg
val Gray900     = Color(0xFF111111)   // page background
val Gray800     = Color(0xFF1A1A1A)   // card surface
val Gray700     = Color(0xFF242424)   // elevated card / input bg
val Gray600     = Color(0xFF333333)   // borders, dividers
val Gray500     = Color(0xFF555555)   // disabled
val Gray400     = Color(0xFF777777)   // placeholder
val Gray300     = Color(0xFF999999)   // secondary text
val Gray200     = Color(0xFFBBBBBB)   // caption text
val Gray100     = Color(0xFFDDDDDD)   // on-surface light
val Gray050     = Color(0xFFF5F5F5)   // almost white

// Semantic aliases (keep naming consistent across screens)
val BgPage      = Gray900
val BgCard      = Gray800
val BgCardAlt   = Gray700
val TextPrimary = White
val TextSecond  = Gray300
val TextHint    = Gray400
val Border      = Gray600
val Divider     = Gray700
val PrimaryAct  = White           // primary interactive colour = white
val OnPrimary   = Black           // text on primary button