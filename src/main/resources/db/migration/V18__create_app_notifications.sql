create table app_notifications (
    id uuid primary key default gen_random_uuid(),
    user_id uuid not null references users(id) on delete cascade,
    type varchar(40) not null,
    title varchar(200) not null,
    body varchar(1000) not null,
    reference_id uuid,
    conversation_id uuid,
    created_at timestamp with time zone not null,
    read_at timestamp with time zone
);

create index idx_app_notifications_user_created
    on app_notifications(user_id, created_at desc);
create index idx_app_notifications_user_unread
    on app_notifications(user_id, read_at);
