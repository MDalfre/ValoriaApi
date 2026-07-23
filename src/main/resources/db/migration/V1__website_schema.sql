CREATE TABLE website.notice (
    id uuid PRIMARY KEY,
    title varchar(140) NOT NULL,
    body text NOT NULL,
    published_at timestamptz NOT NULL,
    created_by uuid NOT NULL,
    active boolean NOT NULL DEFAULT true
);

CREATE INDEX ix_notice_published_at ON website.notice (published_at DESC);

CREATE TABLE website.mini_game_schedule (
    id uuid PRIMARY KEY,
    name varchar(80) NOT NULL,
    schedule_text varchar(120) NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE website.client_download (
    id uuid PRIMARY KEY,
    label varchar(100) NOT NULL,
    url text NOT NULL,
    version varchar(40) NOT NULL,
    file_size varchar(40),
    checksum_sha256 varchar(64),
    sort_order integer NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true
);

CREATE TABLE website.audit_log (
    id uuid PRIMARY KEY,
    actor_account_id uuid,
    action varchar(80) NOT NULL,
    target text,
    details text,
    remote_address varchar(64),
    created_at timestamptz NOT NULL
);

INSERT INTO website.mini_game_schedule (id, name, schedule_text, sort_order) VALUES
('10000000-0000-0000-0000-000000000001', 'Blood Castle', 'A cada 2 horas, nos minutos 00', 10),
('10000000-0000-0000-0000-000000000002', 'Devil Square', 'A cada 2 horas, nos minutos 30', 20),
('10000000-0000-0000-0000-000000000003', 'Chaos Castle', 'Consulte o anúncio dentro do jogo', 30);

