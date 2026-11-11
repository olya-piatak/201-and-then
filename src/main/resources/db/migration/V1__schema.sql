-- One row per seat that is taken. The primary key is what stops two people
-- from buying the same seat; nothing in Java does.
create table seat_holds (
    show_id    text   not null,
    seat       text   not null,
    booking_id bigint,
    primary key (show_id, seat)
);

create table bookings (
    id              bigserial   primary key,
    show_id         text        not null,
    seat            text        not null,
    customer        text        not null,
    idempotency_key text        unique,
    created_at      timestamptz not null default now()
);

-- Written in the same transaction as the booking. The relay publishes rows
-- from here, so a booking and its event either both exist or neither does.
create table outbox (
    id           bigserial   primary key,
    event_id     uuid        not null unique,
    booking_id   bigint      not null,
    type         text        not null,
    payload      text        not null,
    created_at   timestamptz not null default now(),
    published_at timestamptz
);

-- Kafka delivers at least once. This table is how the ticket desk remembers
-- which events it has already handled.
create table processed_events (
    event_id     uuid        primary key,
    processed_at timestamptz not null default now()
);

create table tickets (
    id         bigserial   primary key,
    booking_id bigint      not null,
    event_id   uuid        not null,
    issued_at  timestamptz not null default now()
);
