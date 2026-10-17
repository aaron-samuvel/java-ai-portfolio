create table if not exists review_delivery (
    delivery_id varchar(100) primary key,
    repository_name varchar(200) not null,
    pull_request_number integer not null,
    action varchar(40) not null,
    received_at timestamp with time zone not null default current_timestamp,
    status varchar(16) not null default 'PENDING'
);
