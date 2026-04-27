package com.estampitas.presentation

data class EstampitasUiState(
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val errorMessage: String? = null,
    val authInitializing: Boolean = true,
    val isAuthenticated: Boolean = false,
    val email: String = "",
    val password: String = "",
    val inviteKey: String = "",
    val familyId: String? = null,
    val stickers: List<StickerLineUi> = emptyList(),
)

data class StickerLineUi(
    val id: Int,
    val code: String,
    val team: String?,
    val playerName: String?,
    val quantity: Int,
) {
    val bucket: StickerQuantityBucket =
        when {
            quantity <= 0 -> StickerQuantityBucket.Faltante
            quantity == 1 -> StickerQuantityBucket.Normal
            else -> StickerQuantityBucket.Duplicada
        }
}

enum class StickerQuantityBucket {
    Faltante,
    Normal,
    Duplicada,
}
