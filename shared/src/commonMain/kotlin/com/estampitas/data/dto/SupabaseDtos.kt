package com.estampitas.data.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class StickerDto(
    val id: Int,
    val code: String,
    val team: String? = null,
    @SerialName("player_name") val playerName: String? = null,
)

@Serializable
data class FamilyMemberDto(
    val id: String,
    @SerialName("user_id") val userId: String,
    @SerialName("family_id") val familyId: String,
)

@Serializable
data class InventoryRowDto(
    val id: String,
    @SerialName("family_id") val familyId: String,
    @SerialName("sticker_id") val stickerId: Int,
    val quantity: Int,
)

@Serializable
data class IncrementStickerRpc(
    @SerialName("p_family_id") val pFamilyId: String,
    @SerialName("p_sticker_id") val pStickerId: Int,
    @SerialName("p_delta") val pDelta: Int,
    @SerialName("p_operation_id") val pOperationId: String,
)

@Serializable
data class JoinFamilyRpc(
    val invite: String,
)
