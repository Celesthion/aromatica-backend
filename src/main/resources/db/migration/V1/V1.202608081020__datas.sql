-- Начальные данные: администратор, настройки, счета, склад с полкой и формы товара.

insert into app_user (id, login, password_hash, role, display_name, archived, created_at, created_by, updated_at, updated_by)
values
    ('78fdc2f3-e07f-4f37-ae16-cc7759306052', 'admin', '$2y$10$jPTVteL3O62lZA7/pTaC1.105u2Wun36bHv92k4.pkRr7ixTEWrxm', 'ADMIN', 'Администратор', false, now(), null, null, null);

insert into app_setting (key, value, updated_at, updated_by)
values
    ('reservation.default_days', '3', null, null),
    ('reservation.max_days', '14', null, null);

insert into money_account (id, name, type, is_default, archived, created_at, created_by, updated_at, updated_by)
values
    ('30c00d22-363e-470f-b31e-177896ecc67b', 'Наличные', 'CASH', true, false, now(), null, null, null),
    ('e3f559db-502a-4e2a-98c6-cc71b8a7d4f8', 'Банк / перевод', 'BANK', false, false, now(), null, null, null);

insert into warehouse (id, name, is_default, archived, address, created_at, created_by, updated_at, updated_by, kind, parent_id)
values
    ('1a3c4232-e7e9-4f85-a1be-a14116cc08d2', 'Магазин', true, false, null, now(), null, null, null, 'STORE', null),
    ('a477147a-99a0-4d26-aeeb-ecda8c9856bc', 'Полка · Магазин', false, false, null, now(), null, null, null, 'SHELF', '1a3c4232-e7e9-4f85-a1be-a14116cc08d2');

insert into product_kind (code, name, unit, system, archived, created_at, gift_eligible, created_by, updated_at, updated_by, icon, color)
values
    ('BOTTLE', 'Флакон', 'PCS', true, false, now(), true, null, null, null, 'FlaskRound', '#8091ad'),
    ('TESTER', 'Тестер', 'PCS', true, false, now(), true, null, null, null, 'TestTube', '#9b8ec9'),
    ('SPLIT', 'Распив', 'ML', true, false, now(), true, null, null, null, 'Droplet', '#c39a63'),
    ('BULK', 'Разлив', 'ML', true, false, now(), true, null, null, null, 'Droplets', '#c39a63'),
    ('ATOMIZER', 'Парфюмерный атомайзер', 'PCS', true, false, now(), true, null, null, null, 'SprayCan', '#78a892'),
    ('PACKAGING', 'Упаковка', 'PCS', true, false, now(), true, null, null, null, 'Package', '#78a892'),
    ('GOODS', 'Товар', 'PCS', true, false, now(), true, null, null, null, 'Tag', '#9aa3b2'),
    ('BASE', 'Парфюмерная вода', 'ML', true, false, now(), false, null, null, null, 'Beaker', '#8091ad'),
    ('KIT', 'Комплект', 'PCS', true, false, now(), false, null, null, null, 'Boxes', '#c39a63'),
    ('READY', 'Готовое', 'PCS', true, false, now(), false, null, null, null, 'PackageCheck', '#78a892');
