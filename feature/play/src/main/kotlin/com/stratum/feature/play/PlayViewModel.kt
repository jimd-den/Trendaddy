package com.stratum.feature.play

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stratum.core.domain.content.AssembledContent
import com.stratum.core.domain.session.PlayerState
import com.stratum.core.domain.world.BlockPos
import com.stratum.core.domain.world.World
import com.stratum.core.domain.world.WorldConfig
import com.stratum.core.domain.world.WorldPoint
import com.stratum.engine.world.IsometricProjection
import com.stratum.engine.world.MineResult
import com.stratum.engine.world.PlaceRejection
import com.stratum.engine.world.PlaceResult
import com.stratum.engine.world.WorldSession
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay

/**
 * Drives one play session.
 *
 * The view model owns no game rules. It forwards intent to [WorldSession] and
 * republishes the resulting snapshot, so the rules stay in the pure engine where
 * they can be tested without Android.
 */
class PlayViewModel(
    content: AssembledContent,
    config: WorldConfig,
    heroClassId: String? = null,
) : ViewModel() {

    private val session = WorldSession(content, config, heroClassId)

    private val _state = MutableStateFlow(initialState(content))
    val state: StateFlow<PlayUiState> = _state.asStateFlow()

    val world: World get() = session.world

    private var miningJob: kotlinx.coroutines.Job? = null

    init {
        publish()
    }

    private fun initialState(content: AssembledContent) = PlayUiState(
        player = session.player,
        camera = session.player.position,
        projection = IsometricProjection(),
        palette = content.palette,
        biomeName = session.currentBiome.name,
    )

    fun move(dx: Float, dy: Float) {
        session.move(dx, dy)
        publish()
    }

    fun selectSlot(slot: Int) {
        session.selectSlot(slot)
        publish()
    }

    fun zoom(delta: Float) {
        _state.value = _state.value.let {
            it.copy(projection = it.projection.copy(zoom = (it.projection.zoom + delta).coerceIn(MIN_ZOOM, MAX_ZOOM)))
        }
    }

    /**
     * Mining runs on a coroutine rather than a tap because effort accumulates
     * over time. Starting a new dig cancels the previous one so two blocks can
     * never make progress at once.
     */
    fun beginMining(target: BlockPos) {
        miningJob?.cancel()
        session.cancelMining()
        miningJob = viewModelScope.launch {
            while (isActive) {
                when (val result = session.mine(target, TICK_SECONDS)) {
                    is MineResult.Broken -> {
                        publish(message = "Recovered ${displayName(result.drop)}")
                        return@launch
                    }
                    is MineResult.Rejected -> {
                        publish(message = rejectionMessage(result.reason))
                        return@launch
                    }
                    is MineResult.InProgress -> publish()
                }
                delay(TICK_MILLIS)
            }
        }
    }

    fun stopMining() {
        miningJob?.cancel()
        miningJob = null
        session.cancelMining()
        publish()
    }

    fun place(target: BlockPos) {
        when (val result = session.place(target)) {
            is PlaceResult.Placed -> publish(message = "Placed ${result.block.displayName}")
            is PlaceResult.Rejected -> publish(message = placeRejectionMessage(result.reason))
        }
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null)
    }

    private fun publish(message: String? = null) {
        val snapshot = session.snapshot()
        _state.value = _state.value.copy(
            player = snapshot.player,
            camera = snapshot.player.position,
            biomeName = snapshot.biome.name,
            miningTarget = snapshot.miningTarget,
            miningFraction = snapshot.miningFraction,
            worldRevision = snapshot.worldRevision,
            message = message ?: _state.value.message,
        )
    }

    private fun displayName(blockId: String): String =
        session.content.registry.indexOrNull(blockId)
            ?.let { session.content.registry.typeOf(it).displayName }
            ?: blockId.substringAfter(':').replace('_', ' ')

    private fun rejectionMessage(reason: com.stratum.engine.world.MineRejection) = when (reason) {
        com.stratum.engine.world.MineRejection.NOTHING_THERE -> "Nothing there"
        com.stratum.engine.world.MineRejection.UNBREAKABLE -> "This will not break"
        com.stratum.engine.world.MineRejection.TOOL_TOO_WEAK -> "A stronger tool is needed"
        com.stratum.engine.world.MineRejection.OUT_OF_REACH -> "Too far away"
    }

    private fun placeRejectionMessage(reason: PlaceRejection) = when (reason) {
        PlaceRejection.UNKNOWN_BLOCK -> "Nothing to place"
        PlaceRejection.OCCUPIED -> "Something is already there"
        PlaceRejection.OUT_OF_REACH -> "Too far away"
        PlaceRejection.OUT_OF_BOUNDS -> "Outside the world"
        PlaceRejection.NOTHING_TO_ATTACH_TO -> "Needs something to rest against"
        PlaceRejection.ACTOR_IN_THE_WAY -> "You are standing there"
    }

    override fun onCleared() {
        miningJob?.cancel()
        super.onCleared()
    }

    companion object {
        private const val TICK_SECONDS = 0.05f
        private const val TICK_MILLIS = 50L
        private const val MIN_ZOOM = 0.6f
        private const val MAX_ZOOM = 2.2f

        fun factory(
            content: AssembledContent,
            config: WorldConfig,
            heroClassId: String? = null,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T =
                PlayViewModel(content, config, heroClassId) as T
        }
    }
}

/** Everything the play screen renders, and nothing it does not. */
data class PlayUiState(
    val player: PlayerState,
    val camera: WorldPoint,
    val projection: IsometricProjection,
    val palette: com.stratum.core.domain.content.PackPalette,
    val biomeName: String,
    val miningTarget: BlockPos? = null,
    val miningFraction: Float = 0f,
    val worldRevision: Int = 0,
    val message: String? = null,
)
