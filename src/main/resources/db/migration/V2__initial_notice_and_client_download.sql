INSERT INTO website.notice (id, title, body, published_at, created_by)
VALUES (
    '20000000-0000-0000-0000-000000000001',
    'Comece sua jornada com 15 dias VIP',
    'Toda nova conta recebe 15 dias de acesso VIP gratuito. Crie sua conta, conheça os servidores Gold e aproveite os benefícios desde o primeiro login.',
    now(),
    '00000000-0000-0000-0000-000000000000'
);

INSERT INTO website.client_download (
    id,
    label,
    url,
    version,
    file_size,
    checksum_sha256,
    sort_order
)
VALUES (
    '30000000-0000-0000-0000-000000000001',
    'Client completo Valoria',
    'https://github.com/MDalfre/ValoriaDocker/releases/download/client-v0.1.0/ValoriaClient-0.1.0.zip',
    '0.1.0',
    '445 MB',
    '846bfd2b38822ec02e87d3a0806141bed415c71d6e19f45d30cd39c7ad771ea8',
    10
);
