package com.stratum.core.domain.sprite

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WeaponPosingTest {

    private fun attack(index: Int, of: Int = 4) =
        assertNotNull(WeaponPosing.anchorFor(AnimationState.ATTACK, index, of))

    @Test
    fun `a swing goes up behind the shoulder and comes down across the front`() {
        val windUp = attack(0)
        val impact = attack(3)

        // Measured from the weapon as drawn, which is pointing straight up.
        assertTrue(windUp.rotationDegrees < 0f, "the wind-up did not take the weapon back")
        assertTrue(impact.rotationDegrees > 0f, "the swing never came down")
        assertEquals(WeaponLayer.BEHIND, windUp.layer)
        assertEquals(WeaponLayer.IN_FRONT, impact.layer)
    }

    @Test
    fun `the swing turns one way the whole way through`() {
        // A blade that reverses mid-arc reads as a mistake rather than as a
        // swing, and is the thing an eased interpolation could quietly do.
        val degrees = (0 until 6).map { attack(it, of = 6).rotationDegrees }
        degrees.zipWithNext { a, b -> assertTrue(b >= a, "the swing reversed: $degrees") }
    }

    @Test
    fun `a swing reads the same whether the attack came back as four frames or six`() {
        assertEquals(attack(0).rotationDegrees, attack(0, of = 6).rotationDegrees, 0.001f)
        assertEquals(attack(3).rotationDegrees, attack(5, of = 6).rotationDegrees, 0.001f)
    }

    @Test
    fun `the weapon crosses to the front once it is past the shoulder`() {
        val layers = (0 until 8).map { attack(it, of = 8).layer }
        // Behind for a while, then in front, and never back again.
        val firstFront = layers.indexOf(WeaponLayer.IN_FRONT)
        assertTrue(firstFront > 0, "the swing started in front of the body")
        assertTrue(
            layers.drop(firstFront).all { it == WeaponLayer.IN_FRONT },
            "the weapon went back behind the body mid-strike: $layers",
        )
    }

    @Test
    fun `a corpse lets go of its weapon`() {
        // A body lying still with a raised sword is the most common way a death
        // animation looks unfinished.
        assertNotNull(WeaponPosing.anchorFor(AnimationState.DIE, 0, 4))
        assertNull(WeaponPosing.anchorFor(AnimationState.DIE, 3, 4))
    }

    @Test
    fun `at rest the weapon hangs at the side and behind the body`() {
        val idle = assertNotNull(WeaponPosing.anchorFor(AnimationState.IDLE, 0, 2))
        assertEquals(WeaponLayer.BEHIND, idle.layer)
        // Tip down and back rather than up: a character standing about with a
        // raised sword reads as permanently mid-attack.
        assertTrue(idle.rotationDegrees > 90f)
    }

    @Test
    fun `a walk carries the weapon without swinging it`() {
        val steps = (0 until 4).map { assertNotNull(WeaponPosing.anchorFor(AnimationState.WALK, it, 4)) }
        assertTrue(steps.all { it.rotationDegrees == steps.first().rotationDegrees })
        // It does move, or the weapon looks pinned to the screen while the
        // character bobs underneath it.
        assertTrue(steps.map { it.yFraction }.distinct().size > 1)
    }

    @Test
    fun `a rig covers every frame of every clip it is built from`() {
        val sheet = SpriteSheet(
            id = "t", name = "T", columns = 4, rows = 2, frameWidth = 96, frameHeight = 96,
            clips = listOf(
                AnimationClip(AnimationState.IDLE, firstFrame = 0, frameCount = 2),
                AnimationClip(AnimationState.ATTACK, firstFrame = 4, frameCount = 4, loops = false),
            ),
        )
        val rig = WeaponPosing.rigFor(sheet)

        assertNotNull(rig.anchorFor(0))
        assertNotNull(rig.anchorFor(1))
        // The attack's frames sit where the clip says they do, not where a
        // second count of the grid would put them.
        assertNotNull(rig.anchorFor(4))
        assertNotNull(rig.anchorFor(7))
        assertNull(rig.anchorFor(2), "a frame with no clip was given an anchor")
    }

    @Test
    fun `a sheet with a death only rigs the frames still holding something`() {
        val sheet = SpriteSheet(
            id = "t", name = "T", columns = 4, rows = 1, frameWidth = 96, frameHeight = 96,
            clips = listOf(AnimationClip(AnimationState.DIE, firstFrame = 0, frameCount = 4)),
        )
        val rig = WeaponPosing.rigFor(sheet)

        assertNotNull(rig.anchorFor(0))
        assertNull(rig.anchorFor(3))
        assertTrue(rig.anchors.size < 4)
    }
}

class WeaponSpriteTest {

    @Test
    fun `the grip sits low on a sword and high on a staff`() {
        // Every weapon is drawn pointing up with its handle at the bottom, so
        // the grip is always low -- but a staff is held a third of the way up
        // and a sword just above the pommel.
        assertTrue(WeaponKind.SWORD.gripY > WeaponKind.STAFF.gripY)
        assertEquals(0.5f, WeaponKind.SWORD.gripX)
    }

    @Test
    fun `reach says how long a weapon reads against the character, not its canvas`() {
        assertTrue(WeaponKind.SPEAR.reach > WeaponKind.DAGGER.reach)
        // Nothing is longer than the character is tall except the polearms.
        assertTrue(WeaponKind.SWORD.reach < 1f)
    }

    @Test
    fun `a weapon takes its grip from its kind and can still be corrected`() {
        val fromKind = WeaponSprite(
            id = "w", name = "Blade", kind = WeaponKind.AXE, width = 100, height = 300,
        )
        assertEquals(WeaponKind.AXE.gripY, fromKind.gripY)

        // A generated weapon does not always land where it was asked to, and
        // this one number is the difference between held and impaled.
        assertEquals(0.6f, fromKind.copy(gripY = 0.6f).gripY)
    }

    @Test
    fun `an empty rig is simply nothing held`() {
        assertTrue(WeaponRig().isEmpty)
        assertNull(WeaponRig().anchorFor(0))
    }
}
