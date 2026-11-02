package com.nendo.argosy.data.repository

object AppListFilter {

    fun isShown(
        app: InstalledApp,
        hiddenApps: Set<String>,
        visibleSystemApps: Set<String>,
        showHidden: Boolean
    ): Boolean {
        val isExplicitlyHidden = app.packageName in hiddenApps
        val isSystemAppVisible = app.isSystemApp && app.packageName in visibleSystemApps
        val isSystemAppHidden = app.isSystemApp && app.packageName !in visibleSystemApps
        return if (showHidden) {
            isExplicitlyHidden || isSystemAppHidden
        } else {
            (!isExplicitlyHidden && !app.isSystemApp) || isSystemAppVisible
        }
    }

    fun visibleThenHidden(
        apps: List<InstalledApp>,
        hiddenApps: Set<String>,
        visibleSystemApps: Set<String>,
        appOrder: List<String>
    ): List<InstalledApp> {
        val (visible, hidden) = apps.partition {
            isShown(it, hiddenApps, visibleSystemApps, showHidden = false)
        }
        return ordered(visible, appOrder) + ordered(hidden, appOrder)
    }

    fun ordered(apps: List<InstalledApp>, appOrder: List<String>): List<InstalledApp> {
        if (appOrder.isEmpty()) return apps
        val orderMap = appOrder.withIndex().associate { it.value to it.index }
        return apps.sortedWith(
            compareBy(
                { orderMap[it.packageName] ?: Int.MAX_VALUE },
                { it.label.lowercase() }
            )
        )
    }
}
