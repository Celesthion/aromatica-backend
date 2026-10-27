# Архитектура

## Слои

Все модули устроены одинаково:

```
controller  →  endpoint  →  service  →  repository
```

controller — только HTTP: пути, `@PreAuthorize`, `@Valid`. Авторизация
объявляется только здесь. Ввод проверяет Bean Validation на record-DTO, пустые
суммы и списки нормализует компактный конструктор record. В сервисах остаются
доменные правила вроде «оплата сходится с номиналом».

endpoint собирает сценарий из сервисов, в том числе чужих модулей, и зовёт
маппер: `mapper.toEntity(request)` на входе, `mapper.toDto(entity)` на выходе.
Сам DTO не собирает.

mapper обычно на MapStruct. Если DTO нужны чужие данные (имена, карточки,
фото), маппер — абстрактный класс: сервисы внедрены полями, `toDto`/`toDtos`
грузят данные пачкой на весь список (`TaskMapper`, `CatalogProductMapper`,
`ProductModelMapper`). Ручной `@Component` — там, где MapStruct больше мешает
(`GiftRuleMapper`, `GiftCertificateMapper`, `ReservationMapper`).

service владеет своим репозиторием, чужие данные берёт только через сервисы
их модулей. В обход, через QueryDSL или JPQL-`join`, чужие таблицы тоже не
читаем. Исключение — `inventory.report`: там проекция соединяет таблицы, чтобы
фильтр, сортировка и страницы работали в базе.

repository — Spring Data и QueryDSL.

### Какие бывают сервисы

Доменный сервис (`BrandService`, `DocService`, `PosShiftService`…) работает с
сущностями и доменными командами, про запросы API и мапперы не знает.
`service.create(mapper.toEntity(request))` ставит id, статус и аудит,
проверяет правила и сохраняет. `service.update(id, mapper.toEntity(request))`
читает запись и переносит только разрешённые поля.

Доменная команда — `record` в `<модуль>.model`, когда сценарий не сводится к
одной сущности: `Sale`, `SaleReturn`, `ToShelf`, `SplitWriteOff`,
`ShiftOpening`, `ShiftClosing`, `ConvertOpening`. Маппер заодно держит
совместимость: скидку процентом от старых клиентов `ReceiptLine` уже несёт
суммой.

Use case (`pos.sale.usecase`: `SellUseCase`, `SaleReturnUseCase`,
`ToShelfUseCase`, `VoidReceiptUseCase`, `SplitWriteOffUseCase`) — сценарий
кассы, который трогает склад, деньги, бланки и бронь сразу. Один класс, один
метод `handle(команда)`. Заводим только для сложных сценариев.

SearchService (`BrandSearchService`, `DocSearchService`,
`ShiftSearchService`…) принимает фильтр из пакета `search` и отдаёт `Page`
сущностей. Снаружи это `POST /search` с JSON-фильтром, предикаты собирает
QueryDSL.

QueryService (`StockQueryService`, `PosCatalogQueryService`,
`ShiftSummaryQueryService`, `ReportQueryService`) только читает и сам собирает
проекцию из нескольких источников (остаток, товар, фото, резерв). Ему можно
то, что нельзя остальным сервисам.

`ArchitectureTest` проверяет:

- сервис не лезет в чужой репозиторий
- сервисы и use case не зависят от `*Request` (кроме фильтров поиска), мапперов
  и `dtos`; `*QueryService` исключены явно
- `search*` есть только в `*SearchService`
- endpoint создаёт DTO только через маппер
- `@EventListener` только в `listener`, `@Mapper` только в `mapper`

### Транзакции

Граница на endpoint: каждый метод, который ходит в базу, помечен
`@Transactional` (чтение — `readOnly = true`). Ленивые связи доступны при
сборке DTO, ошибка на любом шаге откатывает всё. `open-in-view` выключен.

Сервисы тоже помечены, на случай вызова из другого модуля, слушателя или
задачи по расписанию. На классе `@Transactional(readOnly = true)`, пишущий
метод ставит `@Transactional` сам (правило `ArchitectureTest`).

Проводки денег при проведении и запись ленты из слушателя помечены
`@Transactional(propagation = MANDATORY)`: без чужой транзакции они падают.

Без транзакции только `AuthEndpointImpl.refresh`. Ротация токена коммитит
погашение сама, иначе 401 откатил бы его и признак кражи потерялся бы.

## События

Модуль не зовёт тех, кому интересно его действие, а публикует событие. Склад
не знает ни про деньги, ни про сертификаты, ни про ленту.

| Событие | Публикует | Слушает |
|---|---|---|
| `DocPostedEvent` / `DocUnpostedEvent` | `PostingService` | `money` (движения денег), `gift` (погашение и возврат бланка) |
| `DocCreatedEvent`, `DocActionEvent` | `DocService`, `DocEventService` | `activity` |
| `ShiftOpenedEvent` / `ShiftClosedEvent` | `PosShiftService` | `activity` |
| `TaskCreatedEvent` / `TaskDoneEvent` | `TaskService` | `activity` |
| `WarehouseArchivingEvent` | `WarehouseService` | `stock` (склад с остатками в архив не уходит) |

Событие — `record` в `<модуль>.event` с сущностью целиком. Слушатель —
`@Component` с `@EventListener` в `<модуль>.listener`.

Слушатели синхронные, в транзакции публикующего, без `@Async` и
`@TransactionalEventListener`. Упал слушатель — откатилось и исходное действие,
так что «товар списан, а бланк не погашен» не бывает.

Публикующий о слушателях не знает. Сценарий, которому нужны данные нескольких
модулей (продажа бланка в смене), живёт в endpoint своего модуля (`pos`).

## Модули

Верхний уровень: `config`, `security`, `common` и `modules`. Зависимости идут
только вниз, `config` собирает приложение. Циклов нет ни между уровнями, ни
внутри (`ArchitectureTest`, `layeredArchitecture`):

```
common ← security ← masterdata ← inventory ← finance ← pos ← workspace ← config
```

| Область | Модуль | Что внутри |
|---|---|---|
| `config` | — | `SecurityConfig`, OpenAPI, QueryDSL, аудит JPA, часы, расписание |
| `common` | — | аудит, ошибки, поиск и страницы, базовые репозитории, web, утилиты |
| `security` | `auth` | вход, JWT (access и refresh), сотрудники и роли, защита от перебора |
| `modules.masterdata` | `catalog` | бренды, продукты (product model), формы (product kind), товары (SKU), правила подарков |
| | `party` | поставщики и покупатели |
| | `settings` | потолок скидки, сроки брони, разбавление |
| `modules.inventory` | `warehouse` | склады и полки |
| | `stock` | остатки и движения. Пишет сюда только проведение через `StockLedger` |
| | `doc` | документы (приёмка, продажа, возврат, вскрытие…) и проведение |
| | `convert` | вскрытие флакона в распив |
| | `report` | экран остатков, история движений позиции, отчёты продаж |
| `modules.finance` | `money` | счета и движения денег, долги поставщикам |
| | `gift` | подарочные сертификаты ([CERTIFICATES.md](CERTIFICATES.md)) |
| `modules.pos` | `shift` | смены, сводка, журнал |
| | `sale` | чек, возврат, «на полку», сторно, списание хвоста, вскрытие, продажа бланка |
| | `storefront` | витрина кассы. Доступный остаток берёт через `AvailableStock`, его реализует `reservation` |
| | `pricing` | потолок скидки продавца, проверка заработанных подарков |
| | `draft`, `reservation`, `customer` | отложенные чеки, брони, покупатели |
| `modules.workspace` | `task` | задачи и чек-листы |
| | `activity` | лента действий, только слушает события |

Про кассу — [POS.md](POS.md).

## Общие правила

- Схему меняет только Flyway, `ddl-auto=validate`.
- Аудит — наследованием entity от `CreatedAudit` или `Auditable`.
- Деньги в minor units (тыйынах). Сумма строки — `CostMath.lineSumMinor`,
  одно округление HALF_UP в конце. На фронте такая же формула.
- Себестоимость списывается по средней (avg cost) из `stock_balance`.
- Всё, что растёт, отдаётся страницами, `Paging` режет страницу до 200.
- Роль пользователя в сервисах — `CurrentUser` (`security.auth.authentication`),
  `@PreAuthorize` остаётся на контроллерах.

Остатки и деньги меняются только проведением документа (`PostingService`).
Чек, возврат, вскрытие, приёмка — документы разных типов с общим проведением и
отменой, деньги и сертификаты идут следом через события. `PostingService`
берёт блокировки в глобальном порядке ключей, пишет движения пачкой и
публикует событие. Правила типа — в стратегии `DocPosting` (пакет
`inventory.doc.posting`, по одной на тип). Реестр `DocPostings` на старте
требует стратегию для каждого `DocType`, без неё приложение не запустится.

Ошибки приходят как `{"error": "…"}` (`ApiExceptionHandler`): `NoSuchElement`
→ 404, `IllegalArgument` → 400, `IllegalState` → 409, `AccessDenied` → 403,
пропущенный параметр → 400 с его именем, остальное → 500 без деталей.

Ограничения частоты в памяти инстанса. Вход (`LoginRateLimiter`) считается по
паре логин+IP и отдельно по логину, IP — последний хоп `X-Forwarded-For`.
Проверка кода сертификата (`RequestRateLimiter`) — 30 в минуту на кассира.

## Модель товара

Три уровня, подробнее в [PRODUCT-NAMING.md](PRODUCT-NAMING.md).

Бренд — плоский справочник. У модели хранится строкой, переименование
переносится каскадом. Бренд необязателен, у упаковки его нет.

Продукт (`product_model`) группирует товары на витрине («Creed — Aventus»),
остатков у него нет.

Товар (`product`, SKU) — продукт в конкретной форме: форма (`product_kind`,
справочник с иконкой и цветом), необязательное отличие (`label`) и объём.
Полное имя собирается из частей при сохранении и хранится готовым.

SKU уникален по `(model_id, kind, volume_ml, label)`.
