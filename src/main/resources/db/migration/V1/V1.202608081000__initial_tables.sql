-- Таблицы, последовательности и ключи.
--
-- Соглашения схемы:
--   * деньги — bigint в минорных единицах (тыйын), колонки *_minor; количества — numeric(14,3),
--     штуки и миллилитры в одной колонке, единицу задаёт товар;
--   * перечисления — varchar с check по значениям enum из кода;
--   * порядок колонок: ключ, данные, связи, аудит (created_*, updated_*, version), ограничения;
--   * created_by / updated_by — след действия, а не связь: без внешних ключей, сотрудников не
--     удаляют, только архивируют. Ссылки на сотрудника по смыслу (кто открыл смену, кому задача)
--     — с внешними ключами;
--   * timestamp без зоны: магазин работает в одном часовом поясе, время пишет Clock приложения;
--   * version — оптимистичная блокировка (JPA @Version).

create sequence if not exists pos_shift_number;
create sequence if not exists product_article_seq;
create sequence if not exists stock_doc_number_assembly;
create sequence if not exists stock_doc_number_convert;
create sequence if not exists stock_doc_number_disassembly;
create sequence if not exists stock_doc_number_inventory;
create sequence if not exists stock_doc_number_move;
create sequence if not exists stock_doc_number_return;
create sequence if not exists stock_doc_number_return_supply;
create sequence if not exists stock_doc_number_sale;
create sequence if not exists stock_doc_number_supply;
create sequence if not exists stock_doc_number_surplus;
create sequence if not exists stock_doc_number_write_off;

-- Платформа: сотрудники, сессии, настройки, лента действий.

create table if not exists app_user
(
    id            uuid         primary key,
    login         varchar(128) not null unique,
    password_hash varchar(255) not null,
    role          varchar(16)  not null check (role in ('ADMIN', 'SELLER', 'MERCHANDISER', 'SENIOR_SELLER', 'ONLINE_MANAGER')),
    display_name  varchar(255),
    archived      boolean      not null default false,
    created_at    timestamp    not null default now(),
    created_by    uuid,
    updated_at    timestamp,
    updated_by    uuid,
    version       bigint       not null default 0
);

create table if not exists refresh_token
(
    id         uuid        primary key,
    user_id    uuid        not null references app_user (id),
    token_hash varchar(64) not null unique,
    issued_at  timestamp   not null default now(),
    expires_at timestamp   not null,
    revoked_at timestamp
);

create table if not exists app_setting
(
    key        varchar(64)  primary key,
    value      varchar(255) not null,
    updated_at timestamp,
    updated_by uuid
);

create table if not exists activity_log
(
    id          bigserial    primary key,
    at          timestamp    not null default now(),
    section     varchar(16)  not null,
    kind        varchar(16)  not null,
    action      varchar(64)  not null,
    entity_type varchar(32),
    entity_id   uuid,
    title       varchar(255) not null,
    actor_id    uuid         references app_user (id)
);

-- Справочники: бренды, продукты, формы, товары, контрагенты.

create table if not exists brand
(
    id         uuid         primary key,
    name       varchar(255) not null,
    archived   boolean      not null default false,
    created_at timestamp    not null default now(),
    created_by uuid,
    updated_at timestamp,
    updated_by uuid,
    version    bigint       not null default 0
);

create table if not exists product_model
(
    id          uuid         primary key,
    brand       varchar(255),
    name        varchar(512) not null,
    description text,
    gender      varchar(16)  check (gender in ('MALE', 'FEMALE', 'UNISEX')),
    archived    boolean      not null default false,
    created_at  timestamp    not null default now(),
    created_by  uuid,
    updated_at  timestamp,
    updated_by  uuid,
    version     bigint       not null default 0
);

create table if not exists product_model_image
(
    id           uuid        primary key,
    model_id     uuid        not null references product_model (id) on delete cascade,
    content_type varchar(64) not null,
    size_bytes   int         not null check (size_bytes > 0),
    bytes        bytea       not null,
    is_main      boolean     not null default false,
    sort         int         not null default 0,
    created_at   timestamp   not null default now(),
    created_by   uuid
);

create table if not exists product_kind
(
    code          varchar(16) primary key,
    name          varchar(64) not null,
    unit          varchar(8)  not null default 'PCS' check (unit in ('PCS', 'ML')),
    icon          varchar(64),
    color         varchar(16),
    gift_eligible boolean     not null default true,
    system        boolean     not null default false,
    archived      boolean     not null default false,
    created_at    timestamp   not null default now(),
    created_by    uuid,
    updated_at    timestamp,
    updated_by    uuid,
    version       bigint      not null default 0
);

create table if not exists product
(
    id              uuid          primary key,
    model_id        uuid          references product_model (id),
    kind            varchar(16)   not null default 'GOODS' references product_kind (code),
    label           varchar(256),
    volume_ml       numeric(14,3) check (volume_ml > 0),
    name            varchar(512),
    article         varchar(16),
    description     text,
    unit            varchar(8)    not null default 'PCS' check (unit in ('PCS', 'ML')),
    price_minor     bigint        check (price_minor >= 0),
    composition_key varchar(1024),
    archived        boolean       not null default false,
    created_at      timestamp     not null default now(),
    created_by      uuid,
    updated_at      timestamp,
    updated_by      uuid,
    version         bigint        not null default 0
);

create table if not exists product_component
(
    id           uuid          primary key,
    product_id   uuid          not null references product (id) on delete cascade,
    component_id uuid          not null references product (id),
    qty          numeric(14,3) not null check (qty > 0),
    line_no      int           not null,
    unique (product_id, component_id)
);

create table if not exists counterparty
(
    id         uuid         primary key,
    name       varchar(512) not null,
    type       varchar(16)  not null check (type in ('SUPPLIER', 'CUSTOMER')),
    phone      varchar(64),
    gender     varchar(16)  check (gender in ('MALE', 'FEMALE')),
    birth_date date,
    note       text,
    archived   boolean      not null default false,
    created_at timestamp    not null default now(),
    created_by uuid,
    updated_at timestamp,
    updated_by uuid,
    version    bigint       not null default 0
);

-- Акции: правила подарков.

create table if not exists gift_rule
(
    id         uuid         primary key,
    name       varchar(255) not null,
    step_minor bigint       check (step_minor > 0),
    max_gifts  int          check (max_gifts >= 1),
    active     boolean      not null default true,
    created_at timestamp    not null default now(),
    created_by uuid,
    updated_at timestamp,
    updated_by uuid,
    version    bigint       not null default 0
);

create table if not exists gift_rule_condition
(
    rule_id       uuid          not null references gift_rule (id) on delete cascade,
    sort          int           not null,
    group_no      int           not null default 0,
    type          varchar(16)   not null check (type in ('SUM', 'KIND', 'PRODUCT')),
    min_sum_minor bigint        check (min_sum_minor > 0),
    kind          varchar(16)   references product_kind (code),
    min_volume_ml numeric(14,3) check (min_volume_ml > 0),
    product_id    uuid          references product (id),
    primary key (rule_id, sort)
);

create table if not exists gift_rule_item
(
    rule_id    uuid          not null references gift_rule (id) on delete cascade,
    sort       int           not null,
    product_id uuid          references product (id),
    kind       varchar(16)   references product_kind (code),
    qty        numeric(14,3) not null check (qty > 0),
    primary key (rule_id, sort),
    -- подарок — либо конкретный товар, либо «любой товар формы»
    constraint gift_rule_item_target_check check ((product_id is not null) <> (kind is not null))
);

-- Склад: склады и полки, кто к каким допущен, документы, журнал и балансы остатков.

create table if not exists warehouse
(
    id         uuid         primary key,
    name       varchar(255) not null,
    kind       varchar(16)  not null default 'STORE' check (kind in ('STORE', 'SHELF')),
    parent_id  uuid         references warehouse (id),
    address    varchar(500),
    is_default boolean      not null default false,
    archived   boolean      not null default false,
    created_at timestamp    not null default now(),
    created_by uuid,
    updated_at timestamp,
    updated_by uuid,
    version    bigint       not null default 0
);

create table if not exists user_warehouse
(
    user_id      uuid not null references app_user (id) on delete cascade,
    warehouse_id uuid not null references warehouse (id),
    primary key (user_id, warehouse_id)
);

create table if not exists money_account
(
    id         uuid         primary key,
    name       varchar(255) not null,
    type       varchar(8)   not null check (type in ('CASH', 'BANK')),
    is_default boolean      not null default false,
    archived   boolean      not null default false,
    created_at timestamp    not null default now(),
    created_by uuid,
    updated_at timestamp,
    updated_by uuid,
    version    bigint       not null default 0
);

create table if not exists pos_shift
(
    id                  uuid       primary key,
    number              bigint     not null,
    warehouse_id        uuid       not null references warehouse (id),
    status              varchar(8) not null check (status in ('OPEN', 'CLOSED')),
    opened_at           timestamp  not null default now(),
    opened_by           uuid       not null references app_user (id),
    opening_cash_minor  bigint     not null default 0 check (opening_cash_minor >= 0),
    closed_at           timestamp,
    closed_by           uuid       references app_user (id),
    expected_cash_minor bigint,
    counted_cash_minor  bigint     check (counted_cash_minor >= 0),
    comment             text
);

create table if not exists stock_doc
(
    id                uuid        primary key,
    doc_type          varchar(16) not null check (doc_type in ('SUPPLY', 'SALE', 'WRITE_OFF', 'SURPLUS', 'INVENTORY', 'MOVE', 'CONVERT', 'RETURN', 'RETURN_SUPPLY', 'ASSEMBLY', 'DISASSEMBLY')),
    number            bigint      not null,
    status            varchar(16) not null default 'DRAFT' check (status in ('DRAFT', 'POSTED', 'CANCELLED')),
    moment            timestamp   not null,
    warehouse_id      uuid        not null references warehouse (id),
    warehouse_to_id   uuid        references warehouse (id),
    counterparty_id   uuid        references counterparty (id),
    shift_id          uuid        references pos_shift (id),
    source_doc_id     uuid        references stock_doc (id),
    sum_minor         bigint      not null default 0 check (sum_minor >= 0),
    cash_minor        bigint      not null default 0 check (cash_minor >= 0),
    no_cash_minor     bigint      not null default 0 check (no_cash_minor >= 0),
    certificate_minor bigint      not null default 0 check (certificate_minor >= 0),
    certificate_id    uuid,
    pay_account_id    uuid        references money_account (id),
    write_off_reason  varchar(32) check (write_off_reason in ('BOTTLE_EMPTY')),
    comment           text,
    posted_at         timestamp,
    created_at        timestamp   not null default now(),
    created_by        uuid,
    updated_at        timestamp,
    updated_by        uuid,
    version           bigint      not null default 0,
    unique (doc_type, number)
);

create table if not exists stock_doc_line
(
    id             uuid          primary key,
    doc_id         uuid          not null references stock_doc (id) on delete cascade,
    line_no        int           not null,
    product_id     uuid          not null references product (id),
    warehouse_id   uuid          references warehouse (id),
    qty            numeric(14,3) not null check (qty >= 0),
    price_minor    bigint        not null default 0 check (price_minor >= 0),
    discount_minor bigint        not null default 0 check (discount_minor >= 0),
    discount_pct   numeric(5,2)  not null default 0 check (discount_pct between 0 and 100),
    cost_minor     bigint        check (cost_minor >= 0),
    is_gift        boolean       not null default false,
    dilution_pct   numeric(5,2)  check (dilution_pct between 30 and 100),
    bundle_id      uuid
);

create table if not exists doc_event
(
    id       bigserial   primary key,
    doc_id   uuid        not null references stock_doc (id),
    action   varchar(16) not null check (action in ('POST', 'UNPOST', 'CANCEL', 'VOID', 'PAY')),
    at       timestamp   not null default now(),
    actor_id uuid        references app_user (id)
);

create table if not exists stock_movement
(
    id           bigserial     primary key,
    doc_id       uuid          not null references stock_doc (id),
    line_id      uuid          not null references stock_doc_line (id),
    product_id   uuid          not null references product (id),
    warehouse_id uuid          not null references warehouse (id),
    qty_delta    numeric(14,3) not null,
    cost_minor   bigint        not null check (cost_minor >= 0),
    moment       timestamp     not null
);

create table if not exists stock_balance
(
    product_id     uuid          not null references product (id),
    warehouse_id   uuid          not null references warehouse (id),
    qty            numeric(14,3) not null default 0,
    avg_cost_minor bigint        not null default 0 check (avg_cost_minor >= 0),
    updated_at     timestamp     not null default now(),
    primary key (product_id, warehouse_id)
);

-- Деньги: движения по счетам.

create table if not exists money_movement
(
    id           bigserial primary key,
    account_id   uuid      not null references money_account (id),
    doc_id       uuid      references stock_doc (id),
    amount_minor bigint    not null,
    moment       timestamp not null,
    comment      text,
    created_at   timestamp not null default now(),
    created_by   uuid
);

-- Подарочные сертификаты: бланки и их погашения.

create table if not exists gift_certificate
(
    id                 uuid        primary key,
    code               varchar(64) not null unique,
    nominal_minor      bigint      not null check (nominal_minor > 0),
    balance_minor      bigint      not null,
    status             varchar(16) not null check (status in ('NEW', 'ACTIVE', 'USED', 'BLOCKED')),
    sold_at            timestamp,
    sold_shift_id      uuid        references pos_shift (id),
    sold_by            uuid        references app_user (id),
    sold_cash_minor    bigint      not null default 0 check (sold_cash_minor >= 0),
    sold_no_cash_minor bigint      not null default 0 check (sold_no_cash_minor >= 0),
    buyer_id           uuid        references counterparty (id),
    comment            text,
    created_at         timestamp   not null default now(),
    created_by         uuid,
    updated_at         timestamp,
    updated_by         uuid,
    version            bigint      not null default 0,
    constraint gift_certificate_balance_check check (balance_minor between 0 and nominal_minor)
);

create table if not exists gift_certificate_use
(
    id             bigint    generated by default as identity primary key,
    certificate_id uuid      not null references gift_certificate (id),
    doc_id         uuid      not null references stock_doc (id),
    -- погашение минусом, возврат на бланк (сторно, распроведение) плюсом; сумма по чеку — чистое погашение
    amount_minor   bigint    not null check (amount_minor <> 0),
    at             timestamp not null default now(),
    created_by     uuid
);

-- Касса: отложенные чеки и брони.

create table if not exists pos_draft
(
    id         uuid         primary key,
    shift_id   uuid         not null references pos_shift (id) on delete cascade,
    sum_minor  bigint       not null check (sum_minor >= 0),
    positions  int          not null check (positions >= 0),
    preview    varchar(512),
    payload    jsonb        not null,
    created_at timestamp    not null default now(),
    created_by uuid
);

create table if not exists reservation
(
    id              uuid         primary key,
    warehouse_id    uuid         not null references warehouse (id),
    counterparty_id uuid         references counterparty (id),
    customer_name   varchar(255) not null,
    phone           varchar(64),
    comment         text,
    status          varchar(16)  not null default 'ACTIVE' check (status in ('ACTIVE', 'SOLD', 'CANCELLED', 'EXPIRED')),
    expires_at      timestamp    not null,
    sold_doc_id     uuid         references stock_doc (id),
    created_at      timestamp    not null default now(),
    created_by      uuid,
    updated_at      timestamp,
    updated_by      uuid,
    version         bigint       not null default 0
);

create table if not exists reservation_line
(
    id             uuid          primary key,
    reservation_id uuid          not null references reservation (id) on delete cascade,
    line_no        int           not null,
    product_id     uuid          not null references product (id),
    qty            numeric(14,3) not null check (qty > 0),
    price_minor    bigint        not null check (price_minor >= 0)
);

-- Задачи сотрудников.

create table if not exists task
(
    id          uuid         primary key,
    title       varchar(500) not null,
    description text,
    assignee_id uuid         references app_user (id),
    status      varchar(8)   not null default 'TODO' check (status in ('TODO', 'DONE')),
    due_date    date,
    done_at     timestamp,
    created_at  timestamp    not null default now(),
    created_by  uuid,
    updated_at  timestamp,
    updated_by  uuid,
    version     bigint       not null default 0
);

create table if not exists task_item
(
    id       uuid         primary key,
    task_id  uuid         not null references task (id) on delete cascade,
    position int          not null default 0 check (position >= 0),
    text     varchar(500) not null,
    done     boolean      not null default false
);

-- Связь на таблицу, объявленную ниже ссылающейся: в самой таблице её не записать.
alter table stock_doc add constraint stock_doc_certificate_id_fkey foreign key (certificate_id) references gift_certificate (id);

-- Ядро учёта.
comment on table stock_doc is 'Складской документ: приёмка, продажа, возврат, списание, перемещение, вскрытие, сборка. Склад и деньги меняются только его проведением.';
comment on table stock_doc_line is 'Строка документа. cost_minor — себестоимость единицы, фиксируется при проведении; у возврата — входные данные из исходного чека.';
comment on table stock_movement is 'Журнал остатков: одно движение на товар и склад. Порядок id — порядок проведения, по нему пересчитывается средняя себестоимость.';
comment on table stock_balance is 'Текущий остаток и средняя себестоимость товара на складе — свёртка stock_movement, обновляется проведением под блокировкой строки.';
comment on table pos_shift is 'Кассовая смена. expected_cash_minor фиксируется при закрытии: размен + наличные продажи − наличные возвраты + наличные за сертификаты.';
comment on table gift_certificate is 'Подарочный сертификат — денежное обязательство магазина: продажа не выручка, выручка появляется при погашении чеком.';
comment on table money_movement is 'Движение по денежному счёту; остаток счёта — сумма движений, отдельно не хранится.';
comment on column stock_doc_line.bundle_id is 'Метка строк одной вещи в чеке (масло, вода, атомайзер одного флакона): возврат кладёт на полку вещь целиком.';
comment on column product.composition_key is 'Состав готового в каноническом виде: одинаковый состав — один товар.';
