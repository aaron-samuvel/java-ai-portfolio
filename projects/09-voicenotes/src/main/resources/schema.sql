create table if not exists transcript_job (
    id varchar(36) primary key,
    status varchar(16) not null,
    created_at timestamp with time zone not null,
    updated_at timestamp with time zone not null,
    result_json clob,
    error_message varchar(500)
);
