package com.nendo.argosy.data.model

data class SetupAccess(
    val allFiles: Boolean = false,
    val usageAccess: Boolean = false,
    val usageAccessGrantable: Boolean = true,
    val notifications: Boolean = false,
    val dualScreenDevice: Boolean = false,
    val defaultHome: Boolean = false
)
