package com.estampitas.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.benasher44.uuid.uuid4
import com.estampitas.data.EstampitasRepository
import com.estampitas.data.dto.StickerDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.status.SessionStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class EstampitasViewModel(
    private val client: SupabaseClient,
    private val repo: EstampitasRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(EstampitasUiState())
    val ui: StateFlow<EstampitasUiState> = _ui.asStateFlow()

    init {
        viewModelScope.launch {
            client.auth.sessionStatus.collect { status ->
                when (status) {
                    SessionStatus.Initializing -> {
                        _ui.update { it.copy(authInitializing = true) }
                    }
                    is SessionStatus.Authenticated -> {
                        _ui.update {
                            it.copy(
                                authInitializing = false,
                                isAuthenticated = true,
                                errorMessage = null,
                            )
                        }
                        refreshMembershipAndInventory()
                    }
                    is SessionStatus.NotAuthenticated -> {
                        _ui.update {
                            it.copy(
                                authInitializing = false,
                                isAuthenticated = false,
                                familyId = null,
                                stickers = emptyList(),
                            )
                        }
                    }
                    is SessionStatus.RefreshFailure -> {
                        _ui.update {
                            it.copy(
                                authInitializing = false,
                                errorMessage = status.toString(),
                            )
                        }
                    }
                }
            }
        }
    }

    fun clearError() {
        _ui.update { it.copy(errorMessage = null) }
    }

    fun setEmail(value: String) {
        _ui.update { it.copy(email = value) }
    }

    fun setPassword(value: String) {
        _ui.update { it.copy(password = value) }
    }

    fun setInviteKey(value: String) {
        _ui.update { it.copy(inviteKey = value) }
    }

    fun signIn() {
        val email = _ui.value.email.trim()
        val password = _ui.value.password
        if (email.isBlank() || password.isBlank()) {
            _ui.update { it.copy(errorMessage = "Email y contraseña son obligatorios") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repo.signIn(email, password)
            } catch (e: Throwable) {
                _ui.update { it.copy(errorMessage = e.message ?: "Error al iniciar sesión") }
            } finally {
                _ui.update { it.copy(isLoading = false) }
            }
        }
    }

    fun signUp() {
        val email = _ui.value.email.trim()
        val password = _ui.value.password
        if (email.isBlank() || password.isBlank()) {
            _ui.update { it.copy(errorMessage = "Email y contraseña son obligatorios") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repo.signUp(email, password)
            } catch (e: Throwable) {
                _ui.update { it.copy(errorMessage = e.message ?: "Error al registrarse") }
            } finally {
                _ui.update { it.copy(isLoading = false) }
            }
        }
    }

    fun signOut() {
        viewModelScope.launch {
            try {
                repo.signOut()
            } catch (_: Throwable) {
            }
        }
    }

    fun joinFamily() {
        val key = _ui.value.inviteKey.trim()
        if (key.isBlank()) {
            _ui.update { it.copy(errorMessage = "Introduce la clave de familia") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                repo.joinFamily(key)
                refreshMembershipAndInventory()
            } catch (e: Throwable) {
                _ui.update { it.copy(errorMessage = e.message ?: "No se pudo unir a la familia") }
            } finally {
                _ui.update { it.copy(isLoading = false) }
            }
        }
    }

    fun refresh() {
        val fid = _ui.value.familyId ?: return
        viewModelScope.launch {
            _ui.update { it.copy(isRefreshing = true) }
            try {
                loadInventory(fid)
            } finally {
                _ui.update { it.copy(isRefreshing = false) }
            }
        }
    }

    fun applyDelta(stickerId: Int, delta: Int) {
        if (delta == 0) return
        val fid = _ui.value.familyId ?: return
        viewModelScope.launch {
            try {
                val opId = uuid4().toString()
                repo.applyStickerDelta(fid, stickerId, delta, opId)
                loadInventory(fid)
            } catch (e: Throwable) {
                _ui.update { it.copy(errorMessage = e.message ?: "Error al sincronizar") }
            }
        }
    }

    private suspend fun refreshMembershipAndInventory() {
        try {
            val members = repo.loadMembershipsForCurrentUser()
            val fid = members.firstOrNull()?.familyId
            _ui.update { it.copy(familyId = fid) }
            if (fid != null) {
                loadInventory(fid)
            } else {
                _ui.update { it.copy(stickers = emptyList()) }
            }
        } catch (e: Throwable) {
            _ui.update { it.copy(errorMessage = e.message) }
        }
    }

    private suspend fun loadInventory(familyId: String) {
        _ui.update { it.copy(isLoading = true, errorMessage = null) }
        try {
            val stickers: List<StickerDto> = repo.loadStickers()
            val inv = repo.loadInventoryForFamily(familyId).associateBy { it.stickerId }
            val lines =
                stickers.map { s ->
                    StickerLineUi(
                        id = s.id,
                        code = s.code,
                        team = s.team,
                        playerName = s.playerName,
                        quantity = inv[s.id]?.quantity ?: 0,
                    )
                }
            _ui.update { it.copy(stickers = lines, isLoading = false) }
        } catch (e: Throwable) {
            _ui.update {
                it.copy(
                    isLoading = false,
                    errorMessage = e.message ?: "Error al cargar datos",
                )
            }
        }
    }
}
