# AGENTS.md — Estampitas Mundial (Kotlin Multiplatform + Supabase)

## 1. Objetivo del proyecto
Aplicación mobile multiplataforma (Android/iOS) desarrollada con Kotlin Multiplatform usando arquitectura MVVM.

Funcionalidad principal:
- Inventario **compartido por familia**.
- Acceso mediante **family key**.
- Sincronización en tiempo real con Supabase.
- Soporte **offline-first (cola de sincronización con operaciones delta)**.
- Gestión automática de estampitas repetidas.
- Notificaciones push reales.

---

## 2. Principio crítico de sincronización

**NUNCA sincronizar estado (quantity)**  
**SIEMPRE sincronizar operaciones (delta)**

Ejemplo:
- Acción: +1 estampita
- Se guarda: delta = +1
- El servidor acumula

---

## 3. Arquitectura General

### Patrón
MVVM (Model - View - ViewModel)

### Capas

1. UI (Compose Multiplatform)
2. ViewModel (StateFlow)
3. Domain (UseCases)
4. Data
   - Remote (Supabase)
   - Local (SQLDelight)
   - Sync Engine

---

## 4. Stack Tecnológico

- Kotlin Multiplatform
- Compose Multiplatform
- Ktor Client
- Supabase (Auth + Postgres + Realtime + Edge Functions)
- SQLDelight (cache offline)
- Coroutines + Flow

---

## 5. Modelo de Datos

### families
- id (UUID)
- name
- invite_key (UNIQUE)

### family_members
- id (UUID)
- user_id (UUID)
- family_id (UUID)

### stickers
- id (Int)
- code (String)
- team (String)
- player_name (String)

### inventory
- id (UUID)
- family_id (UUID)
- sticker_id (Int)
- quantity (Int)
- updated_at (timestamp)

### inventory_events (auditoría + sync robusto)
- id (UUID)
- family_id (UUID)
- sticker_id (Int)
- delta (Int)
- user_id (UUID)
- created_at (timestamp)

---

## 6. Estados (DERIVADOS)

- quantity == 0 → faltante
- quantity == 1 → normal
- quantity > 1 → duplicada

---

## 7. Offline-first (DETALLADO)

### Tabla local: pending_operations

- id
- sticker_id
- delta
- created_at

### Flujo

1. Usuario hace acción
2. Se actualiza UI local
3. Se guarda operación
4. Sync engine ejecuta RPC
5. Se limpia cola

---

## 8. RPC CRÍTICO

increment_sticker:
- recibe delta
- suma en DB
- registra evento

---

## 9. UX

- Tap → +1
- Long press → +1 (rápido para repetidas)
- Swipe down → -1

---

## 10. Notificaciones

Trigger:
- insert en inventory_events

Flujo:
1. Evento DB
2. Edge Function
3. Push (Firebase/APNs)

---

## 11. Autenticación

- Supabase Auth

Join:
- invite_key → RPC join_family

---

## 12. Seguridad (RLS)

- Validar siempre membership
- Basado en auth.uid()

---

## 13. Sync Engine

Responsabilidades:
- Ejecutar cola
- Retry
- Manejo de errores
- Evitar duplicados

Estrategia:
- FIFO queue
- Idempotencia

---

## 14. Estructura

shared/
 ├── data/
 │    ├── local/
 │    ├── remote/
 │    ├── repository/
 │    └── sync/
 ├── domain/
 └── presentation/

---

## 15. Agentes

### Backend Agent
- Schema SQL
- RPC
- RLS

### Sync Agent
- Offline queue
- Delta operations

### Mobile Agent
- MVVM
- StateFlow

### UI Agent
- Compose UI
- Dark/Light

### Realtime Agent
- Subscripciones

### Notification Agent
- Push system

---

## 16. Flujo

Login → Join Family →
Carga →
Acción local →
Guardar delta →
Sync →
Realtime →
Push

---

## 17. Escalabilidad

- 1000+ stickers
- Indexing
- Queries optimizadas

---

FIN

