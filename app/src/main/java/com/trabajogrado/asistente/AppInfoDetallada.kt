package com.trabajogrado.asistente

import android.graphics.drawable.Drawable

data class AppInfoDetallada(
    val nombre: String,
    val packageName: String,
    val icon: Drawable,
    var activada: Boolean
)