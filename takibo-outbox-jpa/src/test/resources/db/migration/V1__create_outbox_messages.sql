create table if not exists outbox_messages (
  id uuid primary key,
  event_type varchar(120) not null,
  aggregate_type varchar(80) not null,
  aggregate_id varchar(120) not null,
  org_id uuid null,
  space_id uuid null,
  payload_json jsonb not null,
  status varchar(20) not null,
  attempts int not null default 0,
  next_run_at timestamptz not null default now(),
  last_error varchar(500) null,
  locked_at timestamptz null,
  locked_by varchar(120) null,
  dedup_key varchar(200) null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

create index if not exists idx_outbox_runnable
  on outbox_messages (status, next_run_at);

create index if not exists idx_outbox_aggregate
  on outbox_messages (aggregate_type, aggregate_id);

create index if not exists idx_outbox_tenant
  on outbox_messages (org_id, space_id);

create unique index if not exists uq_outbox_dedup_key
  on outbox_messages (dedup_key)
  where dedup_key is not null;
