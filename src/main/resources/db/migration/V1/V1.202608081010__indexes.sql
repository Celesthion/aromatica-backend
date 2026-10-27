-- Индексы. Имена: idx_<таблица>_<что> для обычных, uq_<таблица>_<что> для уникальных.
-- Частичные уникальные индексы держат бизнес-правила, которые не выразить ограничением.

-- Платформа.

-- отзыв сессий пользователя трогает только живые токены
create index if not exists idx_refresh_token_user_active on refresh_token (user_id) where revoked_at is null;

create index if not exists idx_activity_log_actor_at on activity_log (actor_id, at desc, id desc);
create index if not exists idx_activity_log_actor_section_at on activity_log (actor_id, section, at desc, id desc);

-- Справочники.

-- имя бренда уникально без учёта регистра, архивные тоже занимают имя
create unique index if not exists uq_brand_name on brand (lower(name));

create index if not exists idx_product_model_brand on product_model (brand);
create unique index if not exists uq_product_model_brand_name on product_model (lower(coalesce(brand, '')), lower(name));

create index if not exists idx_product_model_image_model on product_model_image (model_id, sort);
-- у продукта одно основное фото
create unique index if not exists uq_product_model_image_main on product_model_image (model_id) where is_main;

create unique index if not exists uq_product_kind_name on product_kind (lower(name));

create index if not exists idx_product_model on product (model_id);
create index if not exists idx_product_kind on product (kind) where not archived;
create index if not exists idx_product_name on product (lower(name));
create unique index if not exists uq_product_article on product (article) where article is not null;
-- вариант товара: продукт, форма, объём и отличие; отличие без учёта регистра и пробелов
-- по краям, пустое отличие равно его отсутствию. Архивный вариант можно завести заново
create unique index if not exists uq_product_variant
    on product (model_id, kind, coalesce(volume_ml, -1), lower(coalesce(trim(label), '')))
    where not archived;
-- готовое с одинаковым составом — один товар
create unique index if not exists uq_product_composition on product (composition_key)
    where composition_key is not null and not archived;

create index if not exists idx_product_component_component on product_component (component_id);

-- телефон покупателя уникален, пустой не считается
create unique index if not exists uq_counterparty_phone on counterparty (phone) where phone is not null and phone <> '';
create index if not exists idx_counterparty_archived_name on counterparty (archived, name);
create index if not exists idx_counterparty_name on counterparty (lower(name));

-- Склад.

-- склад по умолчанию один
create unique index if not exists uq_warehouse_default on warehouse (is_default) where is_default;
-- у магазина одна действующая полка
create unique index if not exists uq_warehouse_shelf_per_store on warehouse (parent_id) where kind = 'SHELF' and not archived;

-- счёт по умолчанию — один на тип (наличные, банк)
create unique index if not exists uq_money_account_default on money_account (type) where is_default and not archived;

create unique index if not exists uq_pos_shift_number on pos_shift (number);
-- на складе одна открытая смена
create unique index if not exists uq_pos_shift_open on pos_shift (warehouse_id) where status = 'OPEN';
create index if not exists idx_pos_shift_opened_by_status on pos_shift (opened_by, status);
create index if not exists idx_pos_shift_closed_by on pos_shift (closed_by);
create index if not exists idx_pos_shift_opened_at on pos_shift (opened_at desc);
create index if not exists idx_pos_shift_status_opened_at on pos_shift (status, opened_at desc);

-- журнал документов: фильтр по типу и статусу, свежие сверху
create index if not exists idx_stock_doc_type_status_moment on stock_doc (doc_type, status, moment desc, number desc);
create index if not exists idx_stock_doc_status_moment on stock_doc (status, moment desc, number desc);
create index if not exists idx_stock_doc_moment on stock_doc (moment);
-- поиск по номеру без типа
create index if not exists idx_stock_doc_number on stock_doc (number);
create index if not exists idx_stock_doc_counterparty on stock_doc (counterparty_id);
create index if not exists idx_stock_doc_shift on stock_doc (shift_id);
create index if not exists idx_stock_doc_source on stock_doc (source_doc_id);
create index if not exists idx_stock_doc_certificate on stock_doc (certificate_id) where certificate_id is not null;
create index if not exists idx_stock_doc_created_by on stock_doc (created_by);
-- статистика продавца: его проведённые продажи, сумма берётся прямо из индекса
create index if not exists idx_stock_doc_seller_sales on stock_doc (created_by, moment) include (sum_minor)
    where doc_type = 'SALE' and status = 'POSTED';

create unique index if not exists uq_stock_doc_line_no on stock_doc_line (doc_id, line_no);
create index if not exists idx_stock_doc_line_product on stock_doc_line (product_id);
create index if not exists idx_stock_doc_line_bundle on stock_doc_line (bundle_id) where bundle_id is not null;

create index if not exists idx_doc_event_doc on doc_event (doc_id, at);
create index if not exists idx_doc_event_actor on doc_event (actor_id, at desc);

create index if not exists idx_stock_movement_doc on stock_movement (doc_id);
-- история позиции: свежие движения сверху
create index if not exists idx_stock_movement_product_moment on stock_movement (product_id, warehouse_id, moment);
-- пересчёт баланса при распроведении идёт по журналу в порядке проведения (id)
create index if not exists idx_stock_movement_product_id on stock_movement (product_id, warehouse_id, id);

create index if not exists idx_stock_balance_warehouse on stock_balance (warehouse_id);

-- Деньги.

create index if not exists idx_money_movement_account on money_movement (account_id, moment);
create index if not exists idx_money_movement_doc on money_movement (doc_id);
create index if not exists idx_money_movement_moment on money_movement (moment desc, id desc);

-- Сертификаты.

create index if not exists idx_gift_certificate_status on gift_certificate (status);
create index if not exists idx_gift_certificate_buyer on gift_certificate (buyer_id);
create index if not exists idx_gift_certificate_sold_shift on gift_certificate (sold_shift_id);

create index if not exists idx_gift_certificate_use_certificate on gift_certificate_use (certificate_id);
create index if not exists idx_gift_certificate_use_doc on gift_certificate_use (doc_id);

-- Касса.

create index if not exists idx_pos_draft_shift on pos_draft (shift_id);

-- задача по расписанию ищет действующие брони с прошедшим сроком
create index if not exists idx_reservation_active_expires on reservation (expires_at) where status = 'ACTIVE';
create index if not exists idx_reservation_warehouse_status on reservation (warehouse_id, status);
create index if not exists idx_reservation_counterparty on reservation (counterparty_id) where counterparty_id is not null;

create index if not exists idx_reservation_line_reservation on reservation_line (reservation_id);
create index if not exists idx_reservation_line_product on reservation_line (product_id);

-- Задачи.

create index if not exists idx_task_assignee on task (assignee_id, status);
create index if not exists idx_task_created_by on task (created_by);

create index if not exists idx_task_item_task on task_item (task_id);

-- Поиск подстрокой (lower(x) like '%q%') по триграммному индексу вместо полного прохода:
-- контрагенты по имени и телефону, бланки по коду, товары по имени и артикулу.
-- Выражения совпадают с тем, что генерирует QueryDSL containsIgnoreCase: lower(колонка).
-- Если расширение на сервере недоступно, миграция не падает: поиск просто останется без индекса.
do $$
begin
    create extension if not exists pg_trgm;
exception when others then
    raise notice 'pg_trgm недоступен, поиск подстрокой остаётся без индекса: %', sqlerrm;
end $$;

do $$
begin
    if exists (select 1 from pg_extension where extname = 'pg_trgm') then
        create index if not exists idx_counterparty_name_trgm on counterparty using gin (lower(name) gin_trgm_ops);
        create index if not exists idx_counterparty_phone_trgm on counterparty using gin (lower(phone) gin_trgm_ops);
        create index if not exists idx_gift_certificate_code_trgm on gift_certificate using gin (lower(code) gin_trgm_ops);
        create index if not exists idx_product_name_trgm on product using gin (lower(name) gin_trgm_ops);
        create index if not exists idx_product_article_trgm on product using gin (lower(article) gin_trgm_ops);
    end if;
end $$;
