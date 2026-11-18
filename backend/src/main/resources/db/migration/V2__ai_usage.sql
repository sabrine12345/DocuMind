-- AI usage accounting (tokens and costs) per workspace.
-- Rows are written by the AI integration layer once enabled; the platform
-- itself never fabricates usage data.

create table ai_usage_events (
    id uuid not null,
    workspace_id uuid not null,
    document_id uuid,
    provider varchar(60) not null,
    model varchar(80) not null,
    operation varchar(60) not null,
    prompt_tokens integer not null,
    completion_tokens integer not null,
    total_tokens integer not null,
    cost_amount numeric(12, 6) not null,
    currency varchar(8) not null,
    occurred_at timestamp not null,
    created_by uuid,
    created_at timestamp not null,
    updated_at timestamp not null,
    constraint pk_ai_usage_events primary key (id),
    constraint fk_ai_usage_workspace foreign key (workspace_id) references workspaces (id) on delete cascade,
    constraint fk_ai_usage_document foreign key (document_id) references documents (id) on delete set null
);

create index idx_ai_usage_workspace_time on ai_usage_events (workspace_id, occurred_at);
