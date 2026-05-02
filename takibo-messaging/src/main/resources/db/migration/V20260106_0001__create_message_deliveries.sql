-- Takibo Messaging - message deliveries (PostgreSQL)
-- Single table intentionally: durable tracking, idempotence, retries, correlation.

create table if not exists message_deliveries (
    id uuid not null,
    org_id uuid null,
    space_id uuid null,

    message_type varchar(120) not null,
    channel varchar(30) not null,

    recipient_type varchar(30) not null,
    recipient_value varchar(320) not null,
    recipient_key varchar(200) not null,

    from_address varchar(320) null,
    subject varchar(255) null,
    body text null,
    payload_json jsonb null,

    status varchar(20) not null,
    attempts int not null,
    next_run_at timestamptz not null,
    last_error varchar(500) null,

    locked_at timestamptz null,
    locked_by varchar(120) null,

    dedup_key varchar(220) not null,

    correlation_outbox_id uuid null,
    trace_id varchar(64) null,

    created_at timestamptz not null,
    updated_at timestamptz not null,

    constraint pk_message_deliveries primary key (id)
);

create unique index if not exists uq_message_deliveries_dedup_key on message_deliveries(dedup_key);

create index if not exists ix_message_deliveries_runnable
    on message_deliveries(status, next_run_at);

create index if not exists ix_message_deliveries_locked
    on message_deliveries(locked_at);

create index if not exists ix_message_deliveries_org_space
    on message_deliveries(org_id, space_id);

create index if not exists ix_message_deliveries_corr_outbox
    on message_deliveries(correlation_outbox_id);
