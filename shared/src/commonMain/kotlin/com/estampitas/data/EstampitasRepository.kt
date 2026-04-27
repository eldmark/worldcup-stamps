package com.estampitas.data

import com.estampitas.data.dto.FamilyMemberDto
import com.estampitas.data.dto.IncrementStickerRpc
import com.estampitas.data.dto.InventoryRowDto
import com.estampitas.data.dto.JoinFamilyRpc
import com.estampitas.data.dto.StickerDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.auth
import io.github.jan.supabase.auth.providers.builtin.Email
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.encodeToJsonElement
import kotlinx.serialization.json.jsonObject

class EstampitasRepository(
    private val client: SupabaseClient,
) {
    fun currentUserIdOrNull(): String? =
        client.auth.currentUserOrNull()?.id?.toString()

    suspend fun signIn(email: String, password: String) {
        client.auth.signInWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signUp(email: String, password: String) {
        client.auth.signUpWith(Email) {
            this.email = email
            this.password = password
        }
    }

    suspend fun signOut() {
        client.auth.signOut()
    }

    suspend fun loadMembershipsForCurrentUser(): List<FamilyMemberDto> {
        val uid =
            currentUserIdOrNull()
                ?: return emptyList()
        return client
            .from("family_members")
            .select {
                filter { eq("user_id", uid) }
            }
            .decodeList<FamilyMemberDto>()
    }

    suspend fun joinFamily(inviteKey: String) {
        val params =
            Json.encodeToJsonElement(JoinFamilyRpc(invite = inviteKey.trim())).jsonObject
        client.postgrest.rpc("join_family", params)
    }

    suspend fun loadStickers(): List<StickerDto> =
        client
            .from("stickers")
            .select {
                order("id", Order.ASCENDING)
            }
            .decodeList<StickerDto>()

    suspend fun loadInventoryForFamily(familyId: String): List<InventoryRowDto> =
        client
            .from("inventory")
            .select {
                filter { eq("family_id", familyId) }
            }
            .decodeList<InventoryRowDto>()

    suspend fun applyStickerDelta(
        familyId: String,
        stickerId: Int,
        delta: Int,
        operationId: String,
    ) {
        val params =
            Json.encodeToJsonElement(
                IncrementStickerRpc(
                    pFamilyId = familyId,
                    pStickerId = stickerId,
                    pDelta = delta,
                    pOperationId = operationId,
                ),
            ).jsonObject
        client.postgrest.rpc("increment_sticker", params)
    }
}
