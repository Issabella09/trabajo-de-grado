package com.trabajogrado.asistente

import android.graphics.drawable.Drawable

data class AppInfo(
    val packageName: String,
    val nombre: String,
    var activada: Boolean,
    val icono: Drawable?
)
