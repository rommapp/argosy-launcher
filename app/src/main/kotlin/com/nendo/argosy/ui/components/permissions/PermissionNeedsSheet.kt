package com.nendo.argosy.ui.components.permissions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.nendo.argosy.R
import com.nendo.argosy.core.input.SoundType
import com.nendo.argosy.data.permissions.PermissionSheetController
import com.nendo.argosy.domain.model.ArgosyPermission
import com.nendo.argosy.domain.model.PermissionAvailability
import com.nendo.argosy.domain.model.PermissionSheetKind
import com.nendo.argosy.domain.model.PermissionSheetRequest
import com.nendo.argosy.domain.model.PermissionSnapshot
import com.nendo.argosy.ui.common.androidNameRes
import com.nendo.argosy.ui.common.icon
import com.nendo.argosy.ui.common.lossRes
import com.nendo.argosy.ui.common.rowState
import com.nendo.argosy.ui.common.titleRes
import com.nendo.argosy.ui.components.PermissionRow
import com.nendo.argosy.ui.input.InputHandler
import com.nendo.argosy.ui.input.InputResult
import com.nendo.argosy.ui.input.ModalInputEffect
import com.nendo.argosy.ui.primitives.ModalActionButton
import com.nendo.argosy.ui.primitives.ModalScaffold
import com.nendo.argosy.ui.theme.Dimens
import com.nendo.argosy.ui.theme.LocalArgosyTheme

internal sealed interface SheetTarget {
    data class Allow(val permission: ArgosyPermission) : SheetTarget
    data object Cancel : SheetTarget
    data object Primary : SheetTarget
}

internal fun sheetTargets(request: PermissionSheetRequest, snapshot: PermissionSnapshot): List<SheetTarget> {
    val allows = request.permissions
        .filter { snapshot.of(it) == PermissionAvailability.MISSING }
        .map { SheetTarget.Allow(it) }
    val cancel = if (request.kind == PermissionSheetKind.SAVE_SYNC) listOf(SheetTarget.Cancel) else emptyList()
    return allows + cancel + SheetTarget.Primary
}

/**
 * Draws the permission sheet [controller] holds and captures input while it shows. Attaches as a
 * host while its lifecycle is started.
 */
@Composable
fun PermissionSheetHost(controller: PermissionSheetController) {
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, controller) {
        var attached = false
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> if (!attached) {
                    controller.attachHost()
                    attached = true
                }
                Lifecycle.Event.ON_STOP -> if (attached) {
                    controller.detachHost()
                    attached = false
                }
                Lifecycle.Event.ON_RESUME -> controller.refresh()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            if (attached) controller.detachHost()
        }
    }

    val request by controller.request.collectAsState()
    val snapshot by controller.snapshot.collectAsState()
    val focusIndex by controller.focusIndex.collectAsState()
    val shown = request
    val targets = remember(shown, snapshot) { shown?.let { sheetTargets(it, snapshot) }.orEmpty() }
    val focused = focusIndex.coerceAtMost((targets.size - 1).coerceAtLeast(0))

    val grant = rememberPermissionGrantLauncher(
        grantFor = controller::grantFor,
        onRootGrant = controller::grantOnRoot,
        onPrivilegedEnable = {},
        onResult = controller::refresh
    )

    val activate: (SheetTarget) -> Unit = { target ->
        when (target) {
            is SheetTarget.Allow -> grant(target.permission)
            SheetTarget.Cancel -> controller.answer(false)
            SheetTarget.Primary -> {
                val firstMissing = shown?.takeIf { it.kind == PermissionSheetKind.SAVE_SYNC }
                    ?.permissions?.firstOrNull { snapshot.of(it) == PermissionAvailability.MISSING }
                if (firstMissing != null) grant(firstMissing) else controller.answer(true)
            }
        }
    }

    val currentTargets by rememberUpdatedState(targets)
    val currentFocused by rememberUpdatedState(focused)
    val currentActivate by rememberUpdatedState(activate)
    val handler = remember(controller) {
        object : InputHandler {
            private fun move(delta: Int): InputResult {
                controller.moveFocus(delta, currentTargets.size)
                return InputResult.HANDLED
            }

            override fun onUp(): InputResult = move(-1)
            override fun onDown(): InputResult = move(1)
            override fun onLeft(): InputResult = move(-1)
            override fun onRight(): InputResult = move(1)

            override fun onConfirm(): InputResult {
                currentTargets.getOrNull(currentFocused)?.let(currentActivate)
                return InputResult.HANDLED
            }

            override fun onBack(): InputResult {
                controller.answer(false)
                return InputResult.handled(SoundType.CLOSE_MODAL)
            }

            override fun onMenu(): InputResult = InputResult.HANDLED
            override fun onSecondaryAction(): InputResult = InputResult.HANDLED
            override fun onContextMenu(): InputResult = InputResult.HANDLED
            override fun onPrevSection(): InputResult = InputResult.HANDLED
            override fun onNextSection(): InputResult = InputResult.HANDLED
            override fun onPrevTrigger(): InputResult = InputResult.HANDLED
            override fun onNextTrigger(): InputResult = InputResult.HANDLED
            override fun onSelect(): InputResult = InputResult.HANDLED
            override fun onLeftStickClick(): InputResult = InputResult.HANDLED
            override fun onRightStickClick(): InputResult = InputResult.HANDLED
            override fun onLongConfirm(): InputResult = InputResult.HANDLED
        }
    }
    ModalInputEffect(active = shown != null, handler = handler)

    ModalScaffold(visible = shown != null, onDismiss = { controller.answer(false) }) {
        if (shown != null) {
            PermissionNeedsSheet(
                request = shown,
                snapshot = snapshot,
                focusedTarget = targets.getOrNull(focused),
                onActivate = { target ->
                    controller.setFocus(targets.indexOf(target))
                    activate(target)
                }
            )
        }
    }
}

@Composable
private fun PermissionNeedsSheet(
    request: PermissionSheetRequest,
    snapshot: PermissionSnapshot,
    focusedTarget: SheetTarget?,
    onActivate: (SheetTarget) -> Unit
) {
    val theme = LocalArgosyTheme.current
    val isSync = request.kind == PermissionSheetKind.SAVE_SYNC
    val firstMissing = request.permissions.firstOrNull { snapshot.of(it) == PermissionAvailability.MISSING }
    Column(modifier = Modifier.padding(Dimens.spacingLg)) {
        Text(
            text = stringResource(
                if (isSync) R.string.permissions_sheet_sync_title else R.string.permissions_sheet_launch_title
            ),
            style = MaterialTheme.typography.titleMedium,
            color = theme.textPrimary
        )
        Spacer(Modifier.height(Dimens.spacingXs))
        Text(
            text = stringResource(
                if (isSync) R.string.permissions_sheet_sync_body else R.string.permissions_sheet_launch_body
            ),
            style = MaterialTheme.typography.bodyMedium,
            color = theme.textDim
        )
        Spacer(Modifier.height(Dimens.spacingMd))
        LazyColumn(
            modifier = Modifier.weight(1f, fill = false),
            verticalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
        ) {
            items(request.permissions, key = { it.token }) { permission ->
                PermissionSheetRow(
                    permission = permission,
                    availability = snapshot.of(permission),
                    showLoss = !isSync,
                    allowFocused = focusedTarget == SheetTarget.Allow(permission),
                    onAllow = { onActivate(SheetTarget.Allow(permission)) }
                )
            }
        }
        Spacer(Modifier.height(Dimens.spacingMd))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm, Alignment.End)
        ) {
            if (isSync) {
                ModalActionButton(
                    label = stringResource(R.string.permissions_sheet_sync_cancel),
                    tint = theme.focusAccent,
                    restLabelColor = theme.textPrimary,
                    focused = focusedTarget == SheetTarget.Cancel,
                    onClick = { onActivate(SheetTarget.Cancel) }
                )
            }
            ModalActionButton(
                label = stringResource(primaryLabelRes(isSync, firstMissing)),
                tint = theme.focusAccent,
                restLabelColor = theme.textPrimary,
                focused = focusedTarget == SheetTarget.Primary,
                onClick = { onActivate(SheetTarget.Primary) }
            )
        }
    }
}

private fun primaryLabelRes(isSync: Boolean, firstMissing: ArgosyPermission?): Int = when {
    !isSync && firstMissing != null -> R.string.permissions_sheet_launch_continue
    !isSync -> R.string.permissions_sheet_launch_play
    firstMissing == ArgosyPermission.ALL_FILES_ACCESS -> R.string.permissions_sheet_sync_allow_all_files
    firstMissing == ArgosyPermission.USAGE_ACCESS -> R.string.permissions_sheet_sync_allow_usage
    firstMissing == ArgosyPermission.NOTIFICATIONS -> R.string.permissions_sheet_sync_allow_notifications
    else -> R.string.permissions_sheet_sync_turn_on
}

@Composable
private fun PermissionSheetRow(
    permission: ArgosyPermission,
    availability: PermissionAvailability,
    showLoss: Boolean,
    allowFocused: Boolean,
    onAllow: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.spacingSm)
    ) {
        Column(modifier = Modifier.weight(1f)) {
            PermissionRow(
                icon = permission.icon,
                title = stringResource(permission.titleRes),
                androidName = permission.androidNameRes?.let { stringResource(it) }.orEmpty(),
                state = availability.rowState,
                isFocused = false,
                onClick = null,
                dense = true
            )
            val loss = permission.lossRes
            if (showLoss && loss != null) {
                Text(
                    text = stringResource(loss),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalArgosyTheme.current.textDim,
                    modifier = Modifier.padding(horizontal = Dimens.spacingMd)
                )
            }
        }
        if (availability == PermissionAvailability.MISSING) {
            ModalActionButton(
                label = stringResource(R.string.permissions_sheet_row_allow),
                tint = LocalArgosyTheme.current.focusAccent,
                restLabelColor = LocalArgosyTheme.current.textPrimary,
                focused = allowFocused,
                onClick = onAllow
            )
        }
    }
}
