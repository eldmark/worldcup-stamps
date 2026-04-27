-- Estampitas Mundial — core schema, idempotent delta RPC, RLS, notify hook
-- Requires: Supabase Postgres (auth schema present)

create extension if not exists "uuid-ossp" with schema extensions;
create extension if not exists pg_net with schema extensions;

-- =========================
-- TABLES
-- =========================

create table public.families (
    id uuid primary key default extensions.uuid_generate_v4(),
    name text not null,
    invite_key text unique not null,
    created_at timestamptz not null default now()
);

create table public.family_members (
    id uuid primary key default extensions.uuid_generate_v4(),
    user_id uuid not null references auth.users (id) on delete cascade,
    family_id uuid not null references public.families (id) on delete cascade,
    created_at timestamptz not null default now(),
    unique (user_id, family_id)
);

create table public.stickers (
    id int primary key,
    code text not null,
    team text,
    player_name text
);

create table public.inventory (
    id uuid primary key default extensions.uuid_generate_v4(),
    family_id uuid not null references public.families (id) on delete cascade,
    sticker_id int not null references public.stickers (id),
    quantity int not null default 0,
    updated_at timestamptz not null default now(),
    unique (family_id, sticker_id)
);

-- Client-supplied UUID on insert = idempotency key for sync queue
create table public.inventory_events (
    id uuid primary key,
    family_id uuid not null references public.families (id) on delete cascade,
    sticker_id int not null references public.stickers (id),
    delta int not null,
    user_id uuid not null references auth.users (id) on delete cascade,
    created_at timestamptz not null default now()
);

-- Single-row config: set inventory_notify_url once from SQL editor (service role) to enable pg_net → Edge Function
create table public.edge_invoke_config (
    id boolean primary key default true,
    inventory_notify_url text,
    constraint edge_invoke_config_singleton check (id)
);

insert into public.edge_invoke_config (id, inventory_notify_url)
values (true, null)
on conflict (id) do nothing;

alter table public.edge_invoke_config enable row level security;
-- Sin políticas: solo roles que bypassan RLS (p. ej. postgres en SQL editor) pueden leer/actualizar la URL.

-- =========================
-- INDEXES
-- =========================

create index idx_inventory_family on public.inventory (family_id);
create index idx_inventory_sticker on public.inventory (sticker_id);
create index idx_events_family on public.inventory_events (family_id);
create index idx_events_sticker on public.inventory_events (sticker_id);
create index idx_events_created on public.inventory_events (created_at);

-- =========================
-- updated_at on inventory
-- =========================

create or replace function public.set_inventory_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger trg_inventory_updated_at
before update on public.inventory
for each row
execute function public.set_inventory_updated_at();

-- =========================
-- NOTIFY → Edge Function (optional URL in edge_invoke_config)
-- =========================

create or replace function public.tg_notify_inventory_event_edge()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
  target_url text;
begin
  select c.inventory_notify_url into target_url
  from public.edge_invoke_config c
  where c.id is true;

  if target_url is null or length(trim(target_url)) = 0 then
    return new;
  end if;

  perform net.http_post(
    url := target_url,
    headers := jsonb_build_object('Content-Type', 'application/json'),
    body := jsonb_build_object(
      'type', 'INSERT',
      'table', 'inventory_events',
      'record', jsonb_build_object(
        'id', new.id,
        'family_id', new.family_id,
        'sticker_id', new.sticker_id,
        'delta', new.delta,
        'user_id', new.user_id,
        'created_at', new.created_at
      )
    )
  );

  return new;
exception
  when others then
    raise warning 'inventory_event notify failed: %', sqlerrm;
    return new;
end;
$$;

create trigger trg_inventory_events_edge_notify
after insert on public.inventory_events
for each row
execute function public.tg_notify_inventory_event_edge();

-- =========================
-- RPC: increment_sticker (delta + idempotent operation id)
-- =========================

create or replace function public.increment_sticker(
    p_family_id uuid,
    p_sticker_id int,
    p_delta int,
    p_operation_id uuid
)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  inserted int;
begin
  if not exists (
    select 1
    from public.family_members fm
    where fm.family_id = p_family_id
      and fm.user_id = auth.uid()
  ) then
    raise exception 'not a family member';
  end if;

  if p_delta = 0 then
    return;
  end if;

  insert into public.inventory_events (id, family_id, sticker_id, delta, user_id)
  values (p_operation_id, p_family_id, p_sticker_id, p_delta, auth.uid())
  on conflict (id) do nothing;

  get diagnostics inserted = row_count;

  if inserted > 0 then
    insert into public.inventory (family_id, sticker_id, quantity)
    values (p_family_id, p_sticker_id, greatest(0, p_delta))
    on conflict (family_id, sticker_id)
    do update set quantity = greatest(0, public.inventory.quantity + p_delta);
  end if;
end;
$$;

-- Backwards alias without operation id (generates server UUID — prefer client id for offline queue)
create or replace function public.increment_sticker(
    p_family_id uuid,
    p_sticker_id int,
    p_delta int
)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
  perform public.increment_sticker(p_family_id, p_sticker_id, p_delta, extensions.uuid_generate_v4());
end;
$$;

create or replace function public.join_family(invite text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
  fam_id uuid;
begin
  select f.id
  into fam_id
  from public.families f
  where f.invite_key = invite;

  if fam_id is null then
    raise exception 'Invalid invite key';
  end if;

  insert into public.family_members (user_id, family_id)
  values (auth.uid(), fam_id)
  on conflict do nothing;
end;
$$;

-- =========================
-- RLS
-- =========================

alter table public.families enable row level security;
alter table public.family_members enable row level security;
alter table public.stickers enable row level security;
alter table public.inventory enable row level security;
alter table public.inventory_events enable row level security;

-- families: read if member
create policy "families_select_by_membership"
on public.families
for select
to authenticated
using (
  exists (
    select 1
    from public.family_members fm
    where fm.family_id = families.id
      and fm.user_id = auth.uid()
  )
);

-- family_members: see own rows
create policy "family_members_select_own"
on public.family_members
for select
to authenticated
using (user_id = auth.uid());

-- stickers: catalog readable when logged in (seed later)
create policy "stickers_select_authenticated"
on public.stickers
for select
to authenticated
using (true);

-- inventory
create policy "inventory_select_by_membership"
on public.inventory
for select
to authenticated
using (
  exists (
    select 1
    from public.family_members fm
    where fm.family_id = inventory.family_id
      and fm.user_id = auth.uid()
  )
);

create policy "inventory_insert_by_membership"
on public.inventory
for insert
to authenticated
with check (
  exists (
    select 1
    from public.family_members fm
    where fm.family_id = inventory.family_id
      and fm.user_id = auth.uid()
  )
);

create policy "inventory_update_by_membership"
on public.inventory
for update
to authenticated
using (
  exists (
    select 1
    from public.family_members fm
    where fm.family_id = inventory.family_id
      and fm.user_id = auth.uid()
  )
);

-- events: read only; writes go through increment_sticker (security definer)
create policy "inventory_events_select_by_membership"
on public.inventory_events
for select
to authenticated
using (
  exists (
    select 1
    from public.family_members fm
    where fm.family_id = inventory_events.family_id
      and fm.user_id = auth.uid()
  )
);

-- =========================
-- GRANTS
-- =========================

grant usage on schema public to authenticated;
grant select on public.stickers to authenticated;
grant select, insert, update on public.inventory to authenticated;
grant select on public.families to authenticated;
grant select on public.family_members to authenticated;
grant select on public.inventory_events to authenticated;

grant execute on function public.increment_sticker(uuid, int, int, uuid) to authenticated;
grant execute on function public.increment_sticker(uuid, int, int) to authenticated;
grant execute on function public.join_family(text) to authenticated;
