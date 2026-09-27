CREATE TABLE users (
	id BIGSERIAL PRIMARY KEY,
	email VARCHAR(255) NOT NULL UNIQUE,
	password_hash VARCHAR(255) NOT NULL,
	role VARCHAR(20) NOT NULL CHECK (role IN ('USER', 'ADMIN')),
	created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE events (
	id BIGSERIAL PRIMARY KEY,
	name VARCHAR(200) NOT NULL,
	description TEXT,
	venue VARCHAR(200) NOT NULL,
	starts_at TIMESTAMPTZ NOT NULL,
	capacity INTEGER NOT NULL CHECK (capacity > 0),
	-- Safety net against overselling: the atomic UPDATE can never push this below zero
	available_seats INTEGER NOT NULL CHECK (available_seats >= 0),
	price_cents INTEGER NOT NULL CHECK (price_cents >= 0),
	created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
	CHECK (available_seats <= capacity)
);

CREATE INDEX idx_events_starts_at ON events (starts_at);

CREATE TABLE bookings (
	id BIGSERIAL PRIMARY KEY,
	user_id BIGINT NOT NULL REFERENCES users (id),
	event_id BIGINT NOT NULL REFERENCES events (id),
	quantity INTEGER NOT NULL CHECK (quantity > 0),
	status VARCHAR(20) NOT NULL CHECK (status IN ('CONFIRMED', 'CANCELLED')),
	created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_bookings_user_id ON bookings (user_id);
CREATE INDEX idx_bookings_event_id ON bookings (event_id);
