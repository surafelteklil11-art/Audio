package com.surafel.audio.pdf

import android.graphics.ColorMatrixColorFilter

object PdfReadingStyle {
    fun filter(style: String): ColorMatrixColorFilter? = when (style) {
        "Invert" -> ColorMatrixColorFilter(floatArrayOf(-1f,0f,0f,0f,255f, 0f,-1f,0f,0f,255f, 0f,0f,-1f,0f,255f, 0f,0f,0f,1f,0f))
        "Paper" -> tint(1f, .94f, .73f)
        "Eye comfort" -> tint(.83f, .95f, .69f)
        else -> null
    }
    private fun tint(r: Float, g: Float, b: Float) = ColorMatrixColorFilter(floatArrayOf(r,0f,0f,0f,0f, 0f,g,0f,0f,0f, 0f,0f,b,0f,0f, 0f,0f,0f,1f,0f))
}
