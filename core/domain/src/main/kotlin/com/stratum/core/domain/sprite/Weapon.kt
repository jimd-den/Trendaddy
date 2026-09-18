package com.stratum.core.domain.sprite

/**
 * What a weapon is, for the purposes of drawing and swinging it.
 *
 * Kind rather than free text because three things downstream need to branch on
 * it and none of them can read a description: how it is drawn, where the hand
 * goes on it, and how it moves through a swing. An axe and a bow are not the
 * same object rotated.
 */
enum class WeaponKind(
    val label: String,
    /** How the model is told to draw it. */
    val description: String,
    /**
     * Where the hand sits on the drawn weapon, as a fraction of its box.
     *
     * Every weapon is drawn pointing straight up with its handle at the bottom,
     * so the grip is always low and centred — but not identically. A sword is
     * held just above the pommel; a staff is held a third of the way up; an axe
     * is held low on a long haft.
     *
     * Measured on a generated blade: tip to pommel spanned the drawing, the
     * grip sat about 82% of the way down, and the first guess of 88% put the
     * hand on the pommel itself.
     */
    val gripY: Float,
    /**
     * How long it reads on screen, relative to the character's height.
     *
     * Measured against real art rather than guessed, after the first set came
     * out greatsword-sized on everything: a one-handed blade that reaches
     * two-thirds of a person's height is a two-handed blade. These are the
     * fractions a weapon of each kind actually occupies standing next to a
     * figure.
     */
    val reach: Float,
) {
    SWORD("Sword", "a one-handed sword with a straight or curved blade, a crossguard and a pommel", 0.82f, 0.46f),
    AXE("Axe", "a one-handed axe: a heavy head on a shaft, the head at the top", 0.8f, 0.44f),
    MACE("Mace", "a one-handed mace or club, heaviest at the top", 0.82f, 0.4f),
    DAGGER("Dagger", "a short dagger with a narrow blade", 0.8f, 0.24f),
    SPEAR("Spear", "a long spear: a narrow point on a long straight shaft", 0.68f, 0.82f),
    STAFF("Staff", "a long staff or quarterstaff, the same thickness along its length", 0.6f, 0.8f),
    BOW("Bow", "a bow, the stave curving away from the string, held at its middle", 0.5f, 0.5f),
    SHIELD("Shield", "a shield seen from the front, its face towards the viewer", 0.5f, 0.3f),
    ;

    /** The grip is centred on every kind; only its height along the weapon differs. */
    val gripX: Float get() = 0.5f
}

/**
 * A weapon's art, drawn on its own and owned by nobody.
 *
 * This exists because a weapon drawn into a character is that character's
 * forever. It cannot be dropped, it cannot be swapped for a better one, and it
 * has to be redrawn for every actor that carries one — which in a game about
 * picking up loot is the wrong way round entirely.
 *
 * Holds no pixels, like everything else here: the bytes live under [id].
 */
data class WeaponSprite(
    val id: String,
    val name: String,
    val kind: WeaponKind,
    val width: Int,
    val height: Int,
    /**
     * Where the hand grips it, as a fraction of the drawn box.
     *
     * Taken from the kind by default and overridable, because a generated
     * weapon does not always land where it was asked to. This is the single
     * number that decides whether a sword looks held or looks impaled through
     * the palm, so it is worth being able to nudge.
     */
    val gripX: Float = kind.gripX,
    val gripY: Float = kind.gripY,
    val origin: SpriteOrigin = SpriteOrigin.AI_GENERATED,
)

/** Whether the weapon passes in front of the body this frame or behind it. */
enum class WeaponLayer { BEHIND, IN_FRONT }

/**
 * Where a weapon sits for one frame of one animation.
 *
 * Positions are fractions of the character's frame box rather than pixels, so
 * one rig serves a 64 pixel sheet and a 192 pixel one alike — and survives the
 * sheet being re-packed at another size, which the pose pipeline does routinely.
 *
 * [rotationDegrees] is measured from the weapon as drawn, which is pointing
 * straight up. Positive turns clockwise. So a sword resting at the side is
 * about 150 degrees, and one at the top of a wind-up is about -120.
 */
data class WeaponAnchor(
    val xFraction: Float,
    val yFraction: Float,
    val rotationDegrees: Float,
    val scale: Float = 1f,
    /**
     * Which side of the body it passes.
     *
     * The thing that makes an attached weapon read as held rather than as a
     * sticker. A wind-up goes behind the shoulder and a strike comes across the
     * front, and getting that backwards is instantly legible as wrong even to
     * someone who could not say why.
     */
    val layer: WeaponLayer = WeaponLayer.IN_FRONT,
)

/**
 * A weapon's positions across a whole sheet, keyed by frame.
 *
 * Keyed by frame index rather than by state, because that is what the renderer
 * has in its hand: it has already resolved the clip and the elapsed time into a
 * frame number, and asking it to work backwards to a state would be asking it
 * to redo the playback logic.
 */
data class WeaponRig(val anchors: Map<Int, WeaponAnchor> = emptyMap()) {
    fun anchorFor(frame: Int): WeaponAnchor? = anchors[frame]

    val isEmpty: Boolean get() = anchors.isEmpty()
}

/**
 * One character's correction to the authored arcs.
 *
 * The arcs are universal — a wind-up goes back and up behind the shoulder for
 * every character who has ever swung anything — but the *proportions* are not.
 * A stocky figure holds a weapon lower and nearer the centre line than a lanky
 * one, and a sheet packed at a different fill fraction shifts everything again.
 * Measured on a generated warrior, the first authored anchors put the hand a
 * hand's width outside the character entirely.
 *
 * So the shape of the swing stays authored and the character supplies three
 * numbers. That is a far better split than either extreme: authoring twenty-four
 * anchors per character is work nobody will do twice, and deriving them from the
 * art means finding a hand in a drawing.
 */
data class WeaponFit(
    /** Added to every anchor, as a fraction of the frame. */
    val offsetX: Float = 0f,
    val offsetY: Float = 0f,
    /** Multiplies the weapon's reach, for a character drawn larger or smaller in its cell. */
    val scale: Float = 1f,
) {
    fun applyTo(anchor: WeaponAnchor): WeaponAnchor = anchor.copy(
        xFraction = anchor.xFraction + offsetX,
        yFraction = anchor.yFraction + offsetY,
        scale = anchor.scale * scale,
    )

    val isIdentity: Boolean get() = offsetX == 0f && offsetY == 0f && scale == 1f

    companion object {
        val none = WeaponFit()

        /** Wider than this and the weapon is not being corrected, it is being lost. */
        const val MAX_OFFSET = 0.35f
        const val MIN_SCALE = 0.3f
        const val MAX_SCALE = 3f
    }
}

/**
 * Where a weapon goes for each pose the pipeline knows how to ask for.
 *
 * Authored rather than derived, for the same reason the pose instructions are:
 * a swing is a known shape. The wind-up takes the weapon back and up behind the
 * shoulder, the strike brings it down and across the front, the recovery
 * carries it low. Working that out from the frames would mean finding a hand in
 * a drawing, which is a harder problem than this is worth — and it would have
 * to be solved again for every character.
 *
 * The positions are for a figure at a three-quarter isometric angle facing down
 * and to the right, which is the only angle anything here is drawn at. The
 * weapon hand is therefore on the right of the frame and slightly nearer the
 * viewer than the body.
 */
object WeaponPosing {

    /**
     * The anchor for one pose, or null when the hand should be empty.
     *
     * Null rather than a hidden anchor, so a death that has dropped its weapon
     * simply has none for those frames rather than one drawn at zero scale.
     */
    fun anchorFor(state: AnimationState, index: Int, frameCount: Int): WeaponAnchor? {
        val progress = if (frameCount <= 1) 0f else index.toFloat() / (frameCount - 1)
        return when (state) {
            // At rest: hanging at the side, tip down and back.
            AnimationState.IDLE -> rest(bob = progress * 0.004f)

            // Carried, rising and falling a little with the stride.
            AnimationState.WALK -> rest(bob = 0.012f * kotlin.math.sin(progress * TWO_PI))

            AnimationState.ATTACK -> swing(progress)

            // A skill is held out in front in both hands rather than swung.
            AnimationState.SPECIAL -> WeaponAnchor(
                xFraction = 0.5f,
                yFraction = 0.4f - 0.06f * kotlin.math.sin(progress * PI_F),
                rotationDegrees = -20f + 30f * progress,
                scale = 1f + 0.08f * kotlin.math.sin(progress * PI_F),
                layer = WeaponLayer.IN_FRONT,
            )

            // Flung wide by the hit, but not let go of.
            AnimationState.HURT -> WeaponAnchor(
                xFraction = 0.64f + 0.04f * progress,
                yFraction = 0.55f,
                rotationDegrees = 170f + 25f * progress,
                layer = WeaponLayer.BEHIND,
            )

            // Tucked in tight against the body through the roll.
            AnimationState.ROLL -> WeaponAnchor(
                xFraction = 0.52f,
                yFraction = 0.55f,
                rotationDegrees = 150f,
                scale = 0.95f,
                layer = WeaponLayer.BEHIND,
            )

            // Held while falling, dropped once down. A corpse still gripping a
            // raised sword is the single most common way a death animation
            // looks unfinished.
            AnimationState.DIE -> if (progress < DROP_AT) {
                WeaponAnchor(
                    xFraction = 0.6f,
                    yFraction = 0.56f + 0.1f * progress,
                    rotationDegrees = 150f + 40f * progress,
                    layer = WeaponLayer.BEHIND,
                )
            } else {
                null
            }
        }
    }

    /** Every anchor for a laid-out sheet, ready to hang on it. */
    fun rigFor(sheet: SpriteSheet, fit: WeaponFit = WeaponFit.none): WeaponRig = WeaponRig(
        buildMap {
            for (clip in sheet.clips) {
                for (step in 0 until clip.frameCount) {
                    val anchor = anchorFor(clip.state, step, clip.frameCount) ?: continue
                    put(clip.firstFrame + step, fit.applyTo(anchor))
                }
            }
        },
    )

    /**
     * Hanging at the side, which is where a weapon spends most of its life.
     *
     * Nearer the body than the first attempt. Hands sit closer to the centre
     * line than they look like they do: measured on a generated figure the
     * fist was at 0.47 across, where this had been placing the weapon at 0.68 —
     * a hand's width outside the character entirely.
     */
    private fun rest(bob: Float) = WeaponAnchor(
        xFraction = 0.6f,
        yFraction = 0.54f + bob,
        rotationDegrees = 155f,
        layer = WeaponLayer.BEHIND,
    )

    /**
     * The arc of a swing, in four beats.
     *
     * Interpolated rather than tabulated so it reads the same whether the
     * attack came back as four frames or as six. The shape is what matters: up
     * and behind, then down and across, then low in front.
     */
    private fun swing(progress: Float): WeaponAnchor {
        val degrees = lerp(WIND_UP_DEGREES, FOLLOW_THROUGH_DEGREES, ease(progress))
        // Crosses to the front of the body once it is past the shoulder, which
        // is the moment the swing stops being a wind-up and starts being a hit.
        val layer = if (degrees < CROSSES_AT) WeaponLayer.BEHIND else WeaponLayer.IN_FRONT
        return WeaponAnchor(
            // Hands stay near the centre line through a swing; they do not
            // travel nearly as far across the body as they appear to.
            xFraction = lerp(0.55f, 0.44f, progress),
            yFraction = lerp(0.24f, 0.5f, ease(progress)),
            rotationDegrees = degrees,
            scale = 1f + 0.1f * kotlin.math.sin(progress * PI_F),
            layer = layer,
        )
    }

    /**
     * Slow at the top, fast through the middle.
     *
     * A swing drawn at a constant rate reads as a machine. The weight of a
     * blade is entirely in how long it hangs at the top of the wind-up and how
     * fast it crosses the bottom.
     */
    private fun ease(t: Float): Float = t * t * (3f - 2f * t)

    private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t

    /** Behind the shoulder at the top of the wind-up. */
    private const val WIND_UP_DEGREES = -130f

    /** Low across the body at the end of the follow-through. */
    private const val FOLLOW_THROUGH_DEGREES = 110f

    /** Past this the weapon is in front of the body rather than behind it. */
    private const val CROSSES_AT = -20f

    /** How far into a death the weapon leaves the hand. */
    private const val DROP_AT = 0.5f

    private const val PI_F = 3.1415927f
    private const val TWO_PI = 2f * PI_F
}
