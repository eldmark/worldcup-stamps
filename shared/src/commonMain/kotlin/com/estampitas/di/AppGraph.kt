package com.estampitas.di

import com.estampitas.data.EstampitasRepository
import io.github.jan.supabase.SupabaseClient

object AppGraph {
    val client: SupabaseClient by lazy { createEstampitasSupabaseClient() }
    val repository: EstampitasRepository by lazy { EstampitasRepository(client) }
}
