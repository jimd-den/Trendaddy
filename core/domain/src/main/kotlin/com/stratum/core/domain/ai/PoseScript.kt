package com.stratum.core.domain.ai

import com.stratum.core.domain.sprite.AnimationState
import com.stratum.core.domain.sprite.PoseCell

/**
 * One pose to ask for, as an instruction to an image editor holding the
 * character already.
 *
 * Deliberately says nothing about who the character is. That is the whole
 * point: the identity comes from the reference image, and the prompt changes
 * only the body. A step that re-described the character would invite the model
 * to redraw it, which is exactly the drift this pipeline exists to avoid.
 */
data class PoseStep(
    val state: AnimationState,
    /** Position within its own animation, so a retry knows what it is replacing. */
    val index: Int,
    val instruction: String,
) {
    /** Stable across runs, so a stored pose can be matched to the step that asked for it. */
    val key: String get() = PoseCell.keyOf(state, index)
}

/**
 * The sequence of poses that makes a character animate.
 *
 * The reason this is data rather than a prompt template: animation is a craft
 * with known answers, and they are not things a language model should be
 * improvising per run. A walk cycle is contact, passing, contact, passing --
 * that has been true since before computers, and writing it down once means
 * every character gets a walk that reads as walking rather than as whatever the
 * model associated with the word "walking" that day.
 *
 * Frame counts are small on purpose. Every frame is a separate call to an image
 * model: four frames of a walk is four generations, and a seven-state character
 * at six frames each is forty-two. The counts here are the fewest that read as
 * the motion they name.
 */
data class PoseScript(val steps: List<PoseStep>) {

    val states: List<AnimationState>
        get() = steps.map { it.state }.distinct()

    fun stepsFor(state: AnimationState): List<PoseStep> = steps.filter { it.state == state }

    /** How many frames each state ends up with, which is what the sheet is planned from. */
    fun frameCounts(): Map<AnimationState, Int> =
        states.associateWith { state -> stepsFor(state).size }

    /**
     * What is left to draw, given what is already on disk.
     *
     * The unit of restart. Forty image generations is long enough that
     * something will interrupt it -- a dropped connection, a rate limit, the
     * screen being closed -- and the only acceptable answer to any of those is
     * to carry on from where it stopped.
     */
    fun remaining(done: Set<String>): List<PoseStep> = steps.filterNot { it.key in done }

    fun progress(done: Set<String>): Float {
        if (steps.isEmpty()) return 0f
        return steps.count { it.key in done }.toFloat() / steps.size
    }

    companion object {

        /** The script for a set of states, in the canonical order. */
        fun of(states: Collection<AnimationState>): PoseScript = PoseScript(
            AnimationState.generatedRowOrder
                .filter { it in states }
                .flatMap { state ->
                    posesFor(state).mapIndexed { index, instruction ->
                        PoseStep(state, index, instruction)
                    }
                },
        )

        /** Everything a playable character needs. Forty frames is an evening, not a coffee. */
        fun full(): PoseScript = of(AnimationState.entries)

        /** Idle, walk, attack, death: what an enemy is actually seen doing. */
        fun enemy(): PoseScript = of(
            listOf(
                AnimationState.IDLE,
                AnimationState.WALK,
                AnimationState.ATTACK,
                AnimationState.DIE,
            ),
        )

        /**
         * The poses of one animation, in play order.
         *
         * Each is written as a change of body, not a mood: "left foot forward,
         * heel touching the ground, right arm swung forward" survives being
         * handed to an image editor in a way "walking confidently" does not.
         */
        fun posesFor(state: AnimationState): List<String> = when (state) {
            AnimationState.IDLE -> listOf(
                "standing at rest, weight settled evenly, arms relaxed at the sides, " +
                    "shoulders down",
                "the same standing rest, but mid-breath: chest and shoulders lifted very " +
                    "slightly, head a fraction higher. Everything else identical",
            )
            AnimationState.WALK -> listOf(
                "mid-stride contact: left leg forward with the heel touching the ground, " +
                    "right leg straight back with the toes still down, right arm swung " +
                    "forward and left arm back",
                "passing position: the right leg swinging through directly under the body " +
                    "with the knee bent, standing on the left leg, body at its lowest, arms " +
                    "close to the sides",
                "mid-stride contact the other way: right leg forward with the heel touching " +
                    "the ground, left leg straight back with the toes down, left arm swung " +
                    "forward and right arm back",
                "passing position again: the left leg swinging through under the body with " +
                    "the knee bent, standing on the right leg, body at its lowest, arms close " +
                    "to the sides",
            )
            AnimationState.ATTACK -> listOf(
                "wind-up: weight dropped onto the back foot, torso twisted away from the " +
                    "target, weapon drawn back high behind the shoulder",
                "the swing beginning: torso rotating forward, weapon coming over and down " +
                    "past the shoulder, front foot planting",
                "impact: weight fully forward over the front foot, arms extended, weapon at " +
                    "the far end of its arc where it would strike",
                "recovery: weapon carried low and across the body, shoulders squaring back " +
                    "up, weight returning to centre",
            )
            AnimationState.SPECIAL -> listOf(
                "gathering: crouched slightly, both arms drawn in towards the chest, head " +
                    "down, body coiled",
                "rising: straightening upward, arms sweeping outward and up, head lifting, " +
                    "heels leaving the ground",
                "release: arms thrown wide and forward at full extension, chest open, head " +
                    "back, at the peak of the effort",
                "follow-through: arms falling, body settling back down onto both feet, " +
                    "shoulders dropping",
            )
            AnimationState.HURT -> listOf(
                "taking a hit: head snapped back, chest caved in, both arms flung outward, " +
                    "weight thrown onto the back foot",
                "reeling: doubled further over, one arm across the body, staggering back a " +
                    "step and off balance",
            )
            AnimationState.ROLL -> listOf(
                "crouched low and tucked, chin down, arms wrapped in, about to commit " +
                    "forward",
                "inverted mid-roll, tucked into a ball, rolled over one shoulder with the " +
                    "feet above the head",
                "coming out of the roll, uncurling onto one knee with one hand on the ground",
                "rising out of it, standing back up with the weight forward, ready to move",
            )
            AnimationState.DIE -> listOf(
                "staggering: knees buckling, torso pitching forward, arms loose and falling",
                "going down: one knee on the ground, one hand catching the fall, head hanging",
                "collapsing: fallen onto the side, limbs folding, no longer supporting any " +
                    "weight",
                "lying still on the ground, face down, limbs slack and splayed, completely " +
                    "motionless",
            )
        }
    }
}
