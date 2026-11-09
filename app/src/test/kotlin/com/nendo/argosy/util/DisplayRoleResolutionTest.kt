package com.nendo.argosy.util

import com.nendo.argosy.data.preferences.DisplayRoleOverride
import com.nendo.argosy.domain.model.ScreenLayout
import com.nendo.argosy.domain.model.ScreenRole
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveRoleDisplayIds
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.resolveSecondaryDisplayId
import com.nendo.argosy.util.DisplayAffinityHelper.Companion.rolesSwappedFor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

private const val BUILT_IN = 0
private const val LOWER = 4
private const val MONITOR = 7

private val TOP = AttachedScreen("top", BUILT_IN, 1, 1920, 1080, builtIn = true)
private val BOTTOM = AttachedScreen("bottom", LOWER, 2, 1240, 1080, builtIn = true)
private val MONITOR_SCREEN = AttachedScreen("monitor", MONITOR, 3, 3840, 2160, builtIn = false)

/**
 * The Thor with a monitor attached: the layout gives PRIMARY to the lower panel and PRESENTATION
 * to the built-in, and the monitor holds neither.
 */
class DisplayRoleResolutionTest {

    private val attached = setOf(BUILT_IN, LOWER, MONITOR)

    @Test
    fun `the companion display comes from the layout rather than list position`() {
        val resolved = resolveSecondaryDisplayId(
            roleDisplayIds = LOWER to BUILT_IN,
            attachedIds = attached,
            positionalFallback = MONITOR
        )

        assertEquals(LOWER, resolved)
    }

    @Test
    fun `list position is used only while no layout has been applied`() {
        val resolved = resolveSecondaryDisplayId(
            roleDisplayIds = null,
            attachedIds = attached,
            positionalFallback = LOWER
        )

        assertEquals(LOWER, resolved)
    }

    @Test
    fun `a role holder that has been unplugged falls back to position`() {
        val resolved = resolveSecondaryDisplayId(
            roleDisplayIds = MONITOR to BUILT_IN,
            attachedIds = setOf(BUILT_IN, LOWER),
            positionalFallback = LOWER
        )

        assertEquals(LOWER, resolved)
    }

    @Test
    fun `a layout holding both roles on the default display has no companion display`() {
        val resolved = resolveSecondaryDisplayId(
            roleDisplayIds = BUILT_IN to BUILT_IN,
            attachedIds = setOf(BUILT_IN),
            positionalFallback = null
        )

        assertNull(resolved)
    }

    @Test
    fun `roles answer the layout, input first`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = LOWER to BUILT_IN,
            attachedIds = attached,
            secondaryDisplayId = LOWER,
            rolesSwapped = false,
            dualScreenActive = true
        )

        assertEquals(LOWER to BUILT_IN, resolved)
    }

    @Test
    fun `a swap exchanges the two role displays`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = LOWER to BUILT_IN,
            attachedIds = attached,
            secondaryDisplayId = LOWER,
            rolesSwapped = true,
            dualScreenActive = true
        )

        assertEquals(BUILT_IN to LOWER, resolved)
    }

    @Test
    fun `a layout with home on the default display resolves home there once swapped`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = BUILT_IN to LOWER,
            attachedIds = attached,
            secondaryDisplayId = LOWER,
            rolesSwapped = true,
            dualScreenActive = true
        )

        assertEquals(BUILT_IN to LOWER, resolved)
    }

    @Test
    fun `a layout with home on the default display resolves home on the companion when unswapped`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = BUILT_IN to LOWER,
            attachedIds = attached,
            secondaryDisplayId = LOWER,
            rolesSwapped = false,
            dualScreenActive = true
        )

        assertEquals(LOWER to BUILT_IN, resolved)
    }

    @Test
    fun `the monitor never takes a role it was not given`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = LOWER to BUILT_IN,
            attachedIds = attached,
            secondaryDisplayId = LOWER,
            rolesSwapped = false,
            dualScreenActive = true
        )

        assertEquals(setOf(LOWER, BUILT_IN), setOf(resolved!!.first, resolved.second))
    }

    @Test
    fun `without a layout the roles pair the default display with the positional secondary`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = null,
            attachedIds = attached,
            secondaryDisplayId = LOWER,
            rolesSwapped = false,
            dualScreenActive = true
        )

        assertEquals(LOWER to BUILT_IN, resolved)
    }

    @Test
    fun `a layout giving the default display primary resolves swapped`() {
        val layout = ScreenLayout(mapOf(TOP.key to ScreenRole.PRIMARY, BOTTOM.key to ScreenRole.PRESENTATION))

        assertEquals(true, rolesSwappedFor(layout, listOf(TOP, BOTTOM)))
    }

    @Test
    fun `a layout giving the lower panel primary resolves unswapped`() {
        val layout = ScreenLayout(mapOf(TOP.key to ScreenRole.PRESENTATION, BOTTOM.key to ScreenRole.PRIMARY))

        assertEquals(false, rolesSwappedFor(layout, listOf(TOP, BOTTOM)))
    }

    @Test
    fun `a layout whose primary screen is not attached resolves nothing`() {
        val layout = ScreenLayout(mapOf(MONITOR_SCREEN.key to ScreenRole.PRIMARY, TOP.key to ScreenRole.PRESENTATION))

        assertNull(rolesSwappedFor(layout, listOf(TOP, BOTTOM)))
    }

    @Test
    fun `an auto override stays auto whichever way the layout points`() {
        assertEquals(DisplayRoleOverride.AUTO, DisplayRoleOverride.AUTO.followingLayout(rolesSwapped = true))
        assertEquals(DisplayRoleOverride.AUTO, DisplayRoleOverride.AUTO.followingLayout(rolesSwapped = false))
    }

    @Test
    fun `an explicit override follows the layout in both directions`() {
        assertEquals(DisplayRoleOverride.SWAPPED, DisplayRoleOverride.STANDARD.followingLayout(rolesSwapped = true))
        assertEquals(DisplayRoleOverride.STANDARD, DisplayRoleOverride.SWAPPED.followingLayout(rolesSwapped = false))
        assertEquals(DisplayRoleOverride.SWAPPED, DisplayRoleOverride.SWAPPED.followingLayout(rolesSwapped = true))
        assertEquals(DisplayRoleOverride.STANDARD, DisplayRoleOverride.STANDARD.followingLayout(rolesSwapped = false))
    }

    @Test
    fun `a single display has no roles to hold`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = null,
            attachedIds = setOf(BUILT_IN),
            secondaryDisplayId = null,
            rolesSwapped = false,
            dualScreenActive = true
        )

        assertNull(resolved)
    }

    @Test
    fun `dual screen off holds no roles whichever screen the layout made primary`() {
        listOf(LOWER to BUILT_IN, BUILT_IN to LOWER).forEach { layout ->
            listOf(false, true).forEach { swapped ->
                val resolved = resolveRoleDisplayIds(
                    roleDisplayIds = layout,
                    attachedIds = attached,
                    secondaryDisplayId = LOWER,
                    rolesSwapped = swapped,
                    dualScreenActive = false
                )

                assertNull(resolved)
            }
        }
    }

    @Test
    fun `dual screen off holds no roles without a stored layout`() {
        val resolved = resolveRoleDisplayIds(
            roleDisplayIds = null,
            attachedIds = attached,
            secondaryDisplayId = LOWER,
            rolesSwapped = false,
            dualScreenActive = false
        )

        assertNull(resolved)
    }
}
