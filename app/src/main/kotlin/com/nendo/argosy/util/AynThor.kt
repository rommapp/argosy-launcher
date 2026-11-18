package com.nendo.argosy.util

import android.os.Build

object AynThor {
    fun matches(model: String? = Build.MODEL): Boolean = model?.contains("thor", ignoreCase = true) == true
}
