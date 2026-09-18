package com.stratum.feature.forge

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.stratum.core.domain.ai.GeneratedImage
import com.stratum.core.domain.ai.GenerationObserver
import com.stratum.core.domain.ai.WeaponRequest
import com.stratum.core.domain.sprite.WeaponKind
import com.stratum.core.domain.sprite.WeaponSprite
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Draws weapons nobody owns yet.
 *
 * Short, because a weapon is a single drawing with no animation of its own —
 * the swing lives in the rig, not in the art. That asymmetry is the point of
 * the whole mechanism: one generation buys a sword that every character in the
 * game can pick up, swing and drop, instead of one sword welded to one hero.
 */
class WeaponForgeViewModel(
    private val drawWeapon: suspend (WeaponRequest, GenerationObserver) -> Result<GeneratedImage>,
    /** Keys, trims and stores it; answers null when there was nothing on the canvas. */
    private val storeWeapon: (WeaponRequest, ByteArray) -> WeaponSprite?,
    private val loadWeapons: () -> List<WeaponSprite>,
    private val deleteWeapon: (String) -> Unit,
    private val isProviderConfigured: () -> Boolean,
) : ViewModel() {

    private val _state = MutableStateFlow(
        WeaponForgeUiState(
            weapons = loadWeapons(),
            providerConfigured = isProviderConfigured(),
        ),
    )
    val state: StateFlow<WeaponForgeUiState> = _state.asStateFlow()

    fun refresh() {
        _state.value = _state.value.copy(
            weapons = loadWeapons(),
            providerConfigured = isProviderConfigured(),
        )
    }

    fun updateSubject(subject: String) {
        _state.value = _state.value.copy(subject = subject)
    }

    fun selectKind(kind: WeaponKind) {
        _state.value = _state.value.copy(kind = kind)
    }

    fun updateStyle(style: String) {
        _state.value = _state.value.copy(style = style)
    }

    fun generate() {
        val current = _state.value
        val subject = current.subject.trim()
        if (subject.isEmpty()) {
            _state.value = current.copy(error = "Describe the weapon first.")
            return
        }
        if (!isProviderConfigured()) {
            _state.value = current.copy(error = "Add a provider API key in settings first.")
            return
        }

        val request = WeaponRequest(
            subject = subject,
            kind = current.kind,
            styleDirection = current.style,
        )
        _state.value = current.copy(busy = true, error = null, message = null, lastDrawn = null)

        viewModelScope.launch {
            val result = drawWeapon(request, GenerationObserver.None)
            _state.value = result.fold(
                onSuccess = { image ->
                    val stored = storeWeapon(request, image.bytes)
                    if (stored == null) {
                        _state.value.copy(
                            busy = false,
                            // The specific failure: everything keyed away, so
                            // there is no weapon left. Worth saying, because a
                            // silently absent weapon is indistinguishable from
                            // a bug in the renderer.
                            error = "Nothing was left after the background was removed. The " +
                                "model probably drew the weapon in the background colour.",
                        )
                    } else {
                        _state.value.copy(
                            busy = false,
                            weapons = loadWeapons(),
                            lastDrawn = stored,
                            message = "${stored.name} drawn. Any character can carry it.",
                        )
                    }
                },
                onFailure = { cause ->
                    _state.value.copy(busy = false, error = cause.message ?: "Generation failed.")
                },
            )
        }
    }

    fun delete(weaponId: String) {
        deleteWeapon(weaponId)
        _state.value = _state.value.copy(
            weapons = loadWeapons(),
            lastDrawn = _state.value.lastDrawn?.takeIf { it.id != weaponId },
        )
    }

    fun dismissMessage() {
        _state.value = _state.value.copy(message = null, error = null)
    }

    companion object {
        fun factory(
            drawWeapon: suspend (WeaponRequest, GenerationObserver) -> Result<GeneratedImage>,
            storeWeapon: (WeaponRequest, ByteArray) -> WeaponSprite?,
            loadWeapons: () -> List<WeaponSprite>,
            deleteWeapon: (String) -> Unit,
            isProviderConfigured: () -> Boolean,
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T = WeaponForgeViewModel(
                drawWeapon, storeWeapon, loadWeapons, deleteWeapon, isProviderConfigured,
            ) as T
        }
    }
}

data class WeaponForgeUiState(
    val subject: String = "",
    val kind: WeaponKind = WeaponKind.SWORD,
    val style: String = "",
    val busy: Boolean = false,
    val weapons: List<WeaponSprite> = emptyList(),
    val lastDrawn: WeaponSprite? = null,
    val equippedId: String? = null,
    val providerConfigured: Boolean = false,
    val message: String? = null,
    val error: String? = null,
)
