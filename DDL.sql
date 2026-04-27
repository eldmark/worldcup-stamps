-- =========================
-- EXTENSIONES
-- =========================
create extension if not exists "uuid-ossp";

-- =========================
-- TABLA: families
-- =========================
create table families (
    id uuid primary key default uuid_generate_v4(),
    name text not null,
    invite_key text unique not null,
    created_at timestamp default now()
);

-- =========================
-- TABLA: family_members
-- =========================
create table family_members (
    id uuid primary key default uuid_generate_v4(),
    user_id uuid not null,
    family_id uuid not null references families(id) on delete cascade,
    created_at timestamp default now(),

    unique(user_id, family_id)
);

-- =========================
-- TABLA: stickers
-- =========================
create table stickers (
    id int primary key,
    code text not null,
    team text,
    player_name text
);

-- =========================
-- TABLA: inventory
-- =========================
create table inventory (
    id uuid primary key default uuid_generate_v4(),
    family_id uuid not null references families(id) on delete cascade,
    sticker_id int not null references stickers(id),
    quantity int not null default 0,
    updated_at timestamp default now(),

    unique(family_id, sticker_id)
);

-- =========================
-- TABLA: inventory_events
-- =========================
create table inventory_events (
    id uuid primary key default uuid_generate_v4(),
    family_id uuid not null,
    sticker_id int not null,
    delta int not null,
    user_id uuid not null,
    created_at timestamp default now()
);

-- =========================
-- INDICES
-- =========================
create index idx_inventory_family on inventory(family_id);
create index idx_inventory_sticker on inventory(sticker_id);

create index idx_events_family on inventory_events(family_id);
create index idx_events_sticker on inventory_events(sticker_id);

-- =========================
-- TRIGGER updated_at
-- =========================
create or replace function update_timestamp()
returns trigger as $$
begin
  new.updated_at = now();
  return new;
end;
$$ language plpgsql;

create trigger set_inventory_timestamp
before update on inventory
for each row
execute function update_timestamp();

-- =========================
-- RPC: increment_sticker
-- =========================
create or replace function increment_sticker(
    p_family_id uuid,
    p_sticker_id int,
    p_delta int
)
returns void as $$
begin
    insert into inventory (family_id, sticker_id, quantity)
    values (p_family_id, p_sticker_id, p_delta)
    on conflict (family_id, sticker_id)
    do update set quantity = inventory.quantity + p_delta;

    insert into inventory_events (family_id, sticker_id, delta, user_id)
    values (p_family_id, p_sticker_id, p_delta, auth.uid());
end;
$$ language plpgsql security definer;

-- =========================
-- RPC: join_family
-- =========================
create or replace function join_family(invite text)
returns void as $$
declare
    fam_id uuid;
begin
    select id into fam_id
    from families
    where invite_key = invite;

    if fam_id is null then
        raise exception 'Invalid invite key';
    end if;

    insert into family_members (user_id, family_id)
    values (auth.uid(), fam_id)
    on conflict do nothing;
end;
$$ language plpgsql security definer;

-- =========================
-- ENABLE RLS
-- =========================
alter table families enable row level security;
alter table family_members enable row level security;
alter table inventory enable row level security;
alter table inventory_events enable row level security;

-- =========================
-- POLICIES: inventory
-- =========================
create policy "select inventory by family"
on inventory
for select
using (
    exists (
        select 1 from family_members fm
        where fm.family_id = inventory.family_id
        and fm.user_id = auth.uid()
    )
);

create policy "insert inventory by family"
on inventory
for insert
with check (
    exists (
        select 1 from family_members fm
        where fm.family_id = inventory.family_id
        and fm.user_id = auth.uid()
    )
);

create policy "update inventory by family"
on inventory
for update
using (
    exists (
        select 1 from family_members fm
        where fm.family_id = inventory.family_id
        and fm.user_id = auth.uid()
    )
);

-- =========================
-- POLICIES: inventory_events
-- =========================
create policy "select events by family"
on inventory_events
for select
using (
    exists (
        select 1 from family_members fm
        where fm.family_id = inventory_events.family_id
        and fm.user_id = auth.uid()
    )
);

-- =========================
-- POLICIES: family_members
-- =========================
create policy "select own memberships"
on family_members
for select
using (user_id = auth.uid());

-- =========================
-- POLICIES: families
-- =========================
create policy "select families by membership"
on families
for select
using (
    exists (
        select 1 from family_members fm
        where fm.family_id = families.id
        and fm.user_id = auth.uid()
    )
);