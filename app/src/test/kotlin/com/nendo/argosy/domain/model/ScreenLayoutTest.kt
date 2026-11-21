package com.nendo.argosy.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private const val BUILT_IN = "local:builtin"
private const val SECOND = "local:second"
private const val THIRD = "local:third"

class ScreenLayoutTest {

    private fun layoutOf(vararg pairs: Pair<String, ScreenRole>) = ScreenLayout(pairs.toMap())

    private fun internal(key: String, width: Int, height: Int) =
        ScreenSpec(key = key, widthPx = width, heightPx = height, builtIn = true)

    private fun external(key: String, width: Int = 2560, height: Int = 1440) =
        ScreenSpec(key = key, widthPx = width, heightPx = height, builtIn = false)

    @Test
    fun `the smaller internal panel takes primary`() {
        val layout = ScreenLayout.defaultFor(
            listOf(internal(BUILT_IN, 1920, 1080), internal(SECOND, 1240, 1080))
        )
        assertEquals(SECOND, layout.primaryKey)
        assertEquals(BUILT_IN, layout.presentationKey)
    }

    @Test
    fun `an inverted model takes the larger internal panel as primary`() {
        val layout = ScreenLayout.defaultFor(
            listOf(internal(BUILT_IN, 1920, 1080), internal(SECOND, 1240, 1080)),
            invertInternalOrder = true
        )
        assertEquals(BUILT_IN, layout.primaryKey)
        assertEquals(SECOND, layout.presentationKey)
    }

    @Test
    fun `equal internal panels keep the order they were given`() {
        val layout = ScreenLayout.defaultFor(
            listOf(internal(BUILT_IN, 1080, 1080), internal(SECOND, 1080, 1080))
        )
        assertEquals(SECOND, layout.primaryKey)
        assertEquals(BUILT_IN, layout.presentationKey)
    }

    @Test
    fun `an external screen takes the role left after the internals`() {
        val layout = ScreenLayout.defaultFor(
            listOf(internal(BUILT_IN, 1920, 1080), internal(SECOND, 1240, 1080), external(THIRD))
        )
        assertEquals(SECOND, layout.primaryKey)
        assertEquals(BUILT_IN, layout.presentationKey)
        assertEquals(THIRD, layout.appTargetKey)
    }

    @Test
    fun `a single internal panel hands presentation to the first external`() {
        val layout = ScreenLayout.defaultFor(
            listOf(internal(BUILT_IN, 1080, 2400), external(SECOND), external(THIRD))
        )
        assertEquals(BUILT_IN, layout.primaryKey)
        assertEquals(SECOND, layout.presentationKey)
        assertEquals(THIRD, layout.appTargetKey)
    }

    @Test
    fun `a larger external never outranks an internal panel for primary`() {
        val layout = ScreenLayout.defaultFor(
            listOf(external(THIRD, 3840, 2160), internal(BUILT_IN, 1280, 720))
        )
        assertEquals(BUILT_IN, layout.primaryKey)
        assertEquals(THIRD, layout.presentationKey)
    }

    @Test
    fun `a fourth screen is left off rather than given a role already held`() {
        val layout = ScreenLayout.defaultFor(
            listOf(
                internal(BUILT_IN, 1920, 1080),
                internal(SECOND, 1240, 1080),
                external(THIRD),
                external("local:fourth")
            )
        )
        assertEquals(ScreenRole.OFF, layout.roleFor("local:fourth"))
        assertEquals(THIRD, layout.appTargetKey)
    }

    @Test
    fun `a swap after a role assignment moves the roles rather than restoring the assignment`() {
        val assigned = ScreenLayout.defaultFor(
            listOf(internal(BUILT_IN, 1240, 1080), internal(SECOND, 1920, 1080))
        ).withRole(SECOND, ScreenRole.PRIMARY)
        assertEquals(SECOND, assigned.primaryKey)

        val swapped = assigned.withRole(BUILT_IN, ScreenRole.PRIMARY)

        assertEquals(BUILT_IN, swapped.primaryKey)
        assertEquals(SECOND, swapped.presentationKey)
    }

    @Test
    fun `promoting the presentation screen demotes the old primary to presentation`() {
        val layout = layoutOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.PRESENTATION)
        val next = layout.withRole(SECOND, ScreenRole.PRIMARY)
        assertEquals(SECOND, next.primaryKey)
        assertEquals(BUILT_IN, next.presentationKey)
    }

    @Test
    fun `demoting the primary to presentation promotes the old presentation`() {
        val layout = layoutOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.PRESENTATION)
        val next = layout.withRole(BUILT_IN, ScreenRole.PRESENTATION)
        assertEquals(SECOND, next.primaryKey)
        assertEquals(BUILT_IN, next.presentationKey)
    }

    @Test
    fun `swapping back restores the arrangement it started from`() {
        val layout = layoutOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.PRESENTATION)
        val round = layout.withRole(SECOND, ScreenRole.PRIMARY).withRole(BUILT_IN, ScreenRole.PRIMARY)
        assertEquals(layout.roles, round.roles)
    }

    /**
     * The swap is only ever between primary and presentation. An app-target screen keeps its role
     * while the other two exchange theirs, which is what makes a media screen a fixed destination
     * rather than something a swap can move out from under a running app.
     */
    @Test
    fun `swapping primary and presentation leaves an app-target screen alone`() {
        val layout = layoutOf(
            BUILT_IN to ScreenRole.PRIMARY,
            SECOND to ScreenRole.PRESENTATION,
            THIRD to ScreenRole.APP_TARGET
        )
        val next = layout.withRole(SECOND, ScreenRole.PRIMARY)
        assertEquals(ScreenRole.APP_TARGET, next.roleFor(THIRD))
        assertEquals(SECOND, next.primaryKey)
        assertEquals(BUILT_IN, next.presentationKey)
    }

    /**
     * A promotion trades roles rather than inventing one: the displaced primary takes the role the
     * promoted screen gave up, so the arrangement keeps one screen per role instead of ending up
     * with two presentation screens and no app target.
     */
    @Test
    fun `promoting an app-target screen hands the old primary the app-target role`() {
        val layout = layoutOf(
            BUILT_IN to ScreenRole.PRIMARY,
            SECOND to ScreenRole.PRESENTATION,
            THIRD to ScreenRole.APP_TARGET
        )
        val next = layout.withRole(THIRD, ScreenRole.PRIMARY)
        assertEquals(THIRD, next.primaryKey)
        assertEquals(SECOND, next.presentationKey)
        assertEquals(BUILT_IN, next.appTargetKey)
    }

    @Test
    fun `promoting a disabled screen hands the old primary the off role`() {
        val layout = layoutOf(
            BUILT_IN to ScreenRole.PRIMARY,
            SECOND to ScreenRole.PRESENTATION,
            THIRD to ScreenRole.OFF
        )
        val next = layout.withRole(THIRD, ScreenRole.PRIMARY)
        assertEquals(THIRD, next.primaryKey)
        assertEquals(SECOND, next.presentationKey)
        assertEquals(ScreenRole.OFF, next.roleFor(BUILT_IN))
    }

    @Test
    fun `three screens keep one role each through a promotion`() {
        val layout = layoutOf(
            BUILT_IN to ScreenRole.PRIMARY,
            SECOND to ScreenRole.PRESENTATION,
            THIRD to ScreenRole.APP_TARGET
        )
        val next = layout.withRole(THIRD, ScreenRole.PRIMARY)
        assertEquals(3, next.roles.size)
        assertEquals(layout.roles.values.toSet(), next.roles.values.toSet())
        assertEquals(3, next.roles.values.toSet().size)
    }

    @Test
    fun `promoting an app-target screen is undone by promoting the screen it displaced`() {
        val layout = layoutOf(
            BUILT_IN to ScreenRole.PRIMARY,
            SECOND to ScreenRole.PRESENTATION,
            THIRD to ScreenRole.APP_TARGET
        )
        val round = layout.withRole(THIRD, ScreenRole.PRIMARY).withRole(BUILT_IN, ScreenRole.PRIMARY)
        assertEquals(layout.roles, round.roles)
    }

    @Test
    fun `claiming app target does not disturb the primary`() {
        val layout = layoutOf(
            BUILT_IN to ScreenRole.PRIMARY,
            SECOND to ScreenRole.PRESENTATION,
            THIRD to ScreenRole.APP_TARGET
        )
        val next = layout.withRole(SECOND, ScreenRole.APP_TARGET)
        assertEquals(BUILT_IN, next.primaryKey)
        assertEquals(SECOND, next.appTargetKey)
    }

    /**
     * Two screens where the second is the app target is a single-display arrangement: nothing holds
     * the presentation role, so the primary screen must host the launcher on its own.
     */
    @Test
    fun `a primary plus an app target is a single-display arrangement`() {
        val layout = layoutOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.APP_TARGET)
        assertTrue(layout.isSingleDisplay)
        assertNull(layout.presentationKey)
    }

    @Test
    fun `a primary and a presentation is not a single-display arrangement`() {
        val layout = layoutOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.PRESENTATION)
        assertTrue(!layout.isSingleDisplay)
    }

    @Test
    fun `roles survive a json round trip`() {
        val layout = layoutOf(
            BUILT_IN to ScreenRole.PRIMARY,
            SECOND to ScreenRole.PRESENTATION,
            THIRD to ScreenRole.OFF
        )
        assertEquals(layout.roles, ScreenLayout.fromJson(layout.toJson()).roles)
    }

    @Test
    fun `the navigation menu pin survives a json round trip`() {
        listOf(
            NavigationMenuPin.Control,
            NavigationMenuPin.Presentation,
            NavigationMenuPin.Panel(SECOND)
        ).forEach { pin ->
            val layout = ScreenLayout(
                mapOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.PRESENTATION),
                pin
            )
            assertEquals(layout, ScreenLayout.fromJson(layout.toJson()))
        }
    }

    @Test
    fun `a stored layout without a pin reads as the control pin`() {
        val layout = ScreenLayout.fromJson("""{"roles":{"$BUILT_IN":"PRIMARY","$SECOND":"PRESENTATION"}}""")
        assertEquals(NavigationMenuPin.Control, layout.navigationMenuPin)
        assertEquals(ScreenRole.PRESENTATION, layout.roleFor(SECOND))
    }

    @Test
    fun `a flat layout written before the pin keeps its roles and reads as the control pin`() {
        val layout = ScreenLayout.fromJson("""{"$BUILT_IN":"PRIMARY","$SECOND":"PRESENTATION"}""")
        assertEquals(
            mapOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.PRESENTATION),
            layout.roles
        )
        assertEquals(NavigationMenuPin.Control, layout.navigationMenuPin)
    }

    @Test
    fun `a role change keeps the navigation menu pin`() {
        val layout = ScreenLayout(
            mapOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.PRESENTATION),
            NavigationMenuPin.Panel(SECOND)
        )
        assertEquals(NavigationMenuPin.Panel(SECOND), layout.withRole(SECOND, ScreenRole.PRIMARY).navigationMenuPin)
    }

    @Test
    fun `an unreadable payload falls back to an empty layout`() {
        assertEquals(emptyMap<String, ScreenRole>(), ScreenLayout.fromJson("not json").roles)
        assertEquals(emptyMap<String, ScreenRole>(), ScreenLayout.fromJson(null).roles)
    }
}

class ScreenLayoutsTest {

    /**
     * The set key identifies which screens are attached, not the order the system happened to
     * enumerate them in, so unplugging and replugging the same panel returns to the same profile.
     */
    @Test
    fun `the set key ignores enumeration order`() {
        assertEquals(
            ScreenLayouts.setKeyOf(listOf(BUILT_IN, SECOND)),
            ScreenLayouts.setKeyOf(listOf(SECOND, BUILT_IN))
        )
    }

    @Test
    fun `a different set of screens is a different profile`() {
        val pair = ScreenLayouts.setKeyOf(listOf(BUILT_IN, SECOND))
        val trio = ScreenLayouts.setKeyOf(listOf(BUILT_IN, SECOND, THIRD))
        val single = ScreenLayouts.setKeyOf(listOf(BUILT_IN))
        assertEquals(3, setOf(pair, trio, single).size)
    }

    @Test
    fun `storing a layout leaves the other sets untouched`() {
        val single = ScreenLayouts.setKeyOf(listOf(BUILT_IN))
        val pair = ScreenLayouts.setKeyOf(listOf(BUILT_IN, SECOND))
        val singleLayout = ScreenLayout(mapOf(BUILT_IN to ScreenRole.PRIMARY))
        val pairLayout = ScreenLayout(
            mapOf(BUILT_IN to ScreenRole.PRESENTATION, SECOND to ScreenRole.PRIMARY)
        )

        val stored = ScreenLayouts().with(single, singleLayout).with(pair, pairLayout)

        assertEquals(singleLayout.roles, stored.layoutFor(single)?.roles)
        assertEquals(pairLayout.roles, stored.layoutFor(pair)?.roles)
    }

    @Test
    fun `an unseen set of screens has no stored layout`() {
        val stored = ScreenLayouts().with(
            ScreenLayouts.setKeyOf(listOf(BUILT_IN)),
            ScreenLayout(mapOf(BUILT_IN to ScreenRole.PRIMARY))
        )
        assertNull(stored.layoutFor(ScreenLayouts.setKeyOf(listOf(BUILT_IN, SECOND))))
    }

    @Test
    fun `profiles survive a json round trip`() {
        val key = ScreenLayouts.setKeyOf(listOf(BUILT_IN, SECOND))
        val stored = ScreenLayouts().with(
            key,
            ScreenLayout(mapOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.PRESENTATION))
        )
        val restored = ScreenLayouts.fromJson(stored.toJson())
        assertEquals(stored.layoutFor(key)?.roles, restored.layoutFor(key)?.roles)
    }

    @Test
    fun `each profile keeps its own navigation menu pin through a json round trip`() {
        val pair = ScreenLayouts.setKeyOf(listOf(BUILT_IN, SECOND))
        val trio = ScreenLayouts.setKeyOf(listOf(BUILT_IN, SECOND, THIRD))
        val stored = ScreenLayouts()
            .with(
                pair,
                ScreenLayout(
                    mapOf(BUILT_IN to ScreenRole.PRIMARY, SECOND to ScreenRole.PRESENTATION),
                    NavigationMenuPin.Presentation
                )
            )
            .with(
                trio,
                ScreenLayout(
                    mapOf(
                        BUILT_IN to ScreenRole.PRIMARY,
                        SECOND to ScreenRole.PRESENTATION,
                        THIRD to ScreenRole.APP_TARGET
                    ),
                    NavigationMenuPin.Panel(THIRD)
                )
            )
        val restored = ScreenLayouts.fromJson(stored.toJson())
        assertEquals(NavigationMenuPin.Presentation, restored.layoutFor(pair)?.navigationMenuPin)
        assertEquals(NavigationMenuPin.Panel(THIRD), restored.layoutFor(trio)?.navigationMenuPin)
    }
}
