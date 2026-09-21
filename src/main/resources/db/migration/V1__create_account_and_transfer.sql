create table account (
     id uuid primary key,
     owner_name text not null,
     balance bigint not null,
     currency text not null,
     created_at timestamptz not null default now(),
     updated_at timestamptz not null default now(),
     version bigint not null default 0
);

create table transfer (
    id uuid primary key,
    source_account_id uuid not null,
    target_account_id uuid not null,
    amount bigint not null,
    status text not null,
    created_at timestamptz not null default now()
);

create table idempotency_key (
    key text primary key,
    transfer_id uuid not null,
    created_at timestamptz not null default now()
);

create table outbox (
    id uuid primary key,
    aggregate_id uuid not null,
    topic text not null,
    message_key text not null,
    payload jsonb not null,
    headers jsonb,
    created_at timestamptz not null default now(),
    published_at timestamptz,
    attempts int not null default 0
);