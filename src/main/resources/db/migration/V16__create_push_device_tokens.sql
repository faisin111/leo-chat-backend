create table push_device_tokens (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    token varchar(4096) not null unique,
    platform varchar(20) not null default 'ANDROID',
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null
);

create index idx_push_device_tokens_user_id on push_device_tokens(user_id);
