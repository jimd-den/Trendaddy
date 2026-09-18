package com.stratum.core.domain.sprite

/** Where the pose for a frame comes from. */
enum class PoseGuideMode(val label: String) {
    /**
     * Nothing but the written instruction.
     *
     * Kept because it is the only mode that works with a provider that accepts
     * one input image, and because it is what every pose set generated before
     * guides existed used — a set half-drawn under the old mode must stay
     * resumable under it.
     */
    NONE("Words only"),

    /** The skeletons authored in [MocapPoses]. */
    BUILT_IN("Built-in skeleton"),

    /**
     * Poses imported from an OpenPose library.
     *
     * A pose library is a far larger and better-observed body of work than
     * anything authored here: real anatomy, taken from photographs, in
     * quantity. What it cannot know is this game's camera or its frame counts,
     * which is why imports are normalised and why the built-in set stays.
     */
    IMPORTED("Imported OpenPose"),
}

/** How a guide is drawn. */
enum class PoseGuideStyle(val label: String) {
    /**
     * Flat black lines on white.
     *
     * Legible to a chat image model, and unmistakably a diagram rather than
     * art — which is what stops a model returning a tidied-up stick figure.
     */
    DIAGRAM("Plain diagram"),

    /**
     * The canonical OpenPose rendering: coloured limbs on black.
     *
     * Worth having even though nothing here runs ControlNet. Every model
     * trained alongside an OpenPose preprocessor has seen this exact palette,
     * so it is a stronger signal to some of them than a plain diagram — and it
     * makes a pose authored here droppable into that ecosystem unchanged.
     */
    OPENPOSE("OpenPose"),
}

/**
 * The pose for a frame, from whichever source is in use.
 *
 * One object rather than a branch at each call site, because three things read
 * it — the guide handed to the model, the weapon rig, and the editor preview —
 * and the whole value of a skeleton is that all three agree. A rig built from
 * the built-in poses while the art was drawn against an imported one would put
 * the sword where the character's hand is not, which is precisely the bug the
 * skeleton was introduced to remove.
 */
data class PoseGuides(
    val mode: PoseGuideMode = PoseGuideMode.BUILT_IN,
    val style: PoseGuideStyle = PoseGuideStyle.DIAGRAM,
    /** Imported poses, keyed as [PoseCell.keyOf] keys them. */
    val imported: Map<String, Pose> = emptyMap(),
    val skeleton: Skeleton = Skeleton(),
) {

    /**
     * The pose to draw and to rig against, or null when there is none.
     *
     * An imported pose that is missing for one frame falls back to the built-in
     * one rather than to nothing. Filling a library set frame by frame is
     * normal — a person finds a good wind-up and a good impact and has nothing
     * for the recovery — and a gap that silently disabled the guide would make
     * a partly-imported set worse than either pure source.
     */
    fun poseFor(state: AnimationState, index: Int, frameCount: Int): Pose? = when (mode) {
        PoseGuideMode.NONE -> null
        PoseGuideMode.BUILT_IN -> builtIn(state, index, frameCount)
        PoseGuideMode.IMPORTED ->
            imported[PoseCell.keyOf(state, index)] ?: builtIn(state, index, frameCount)
    }

    /**
     * The pose the weapon is rigged against.
     *
     * Falls back to the built-in skeleton even in [PoseGuideMode.NONE], because
     * a character generated from prose alone still has hands and still has to
     * hold things. Losing the guide loses pose fidelity; it should not lose the
     * weapon.
     */
    fun riggingPoseFor(state: AnimationState, index: Int, frameCount: Int): Pose =
        poseFor(state, index, frameCount) ?: builtIn(state, index, frameCount)

    /** Which frames of a script an import has actually supplied. */
    fun importedKeys(): Set<String> = imported.keys

    fun withImported(key: String, pose: Pose): PoseGuides =
        copy(imported = imported + (key to pose), mode = PoseGuideMode.IMPORTED)

    fun withoutImported(key: String): PoseGuides = copy(imported = imported - key)

    private fun builtIn(state: AnimationState, index: Int, frameCount: Int): Pose =
        skeleton.pose(MocapPoses.poseFor(state, index, frameCount))
}
