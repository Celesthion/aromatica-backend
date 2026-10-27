# Aromatica — бэкенд

Бэкенд для парфюмерного магазина: склад, касса, подарочные сертификаты, акции, сотрудники.
Фронт и мобильная касса — в `aromatica-frontend`.

Java 21, Spring Boot 4, PostgreSQL, Flyway, Spring Security + JWT, MapStruct, QueryDSL.

## Как запустить

Нужен Postgres с базой `aromatica` (по умолчанию `localhost:5432`, `postgres/postgres`).
Таблицы создаст Flyway при старте.

Ещё нужен секрет для JWT. Можно задать через `APP_AUTH_JWT_SECRET` или положить
в `config/application.yaml` (он в .gitignore):

```yaml
app:
  auth:
    jwt-secret: любая длинная строка
```

```bash
./mvnw spring-boot:run
```

Логин по умолчанию — `admin/admin`.

Swagger выключен, включить можно через `API_DOCS_ENABLED=true`, потом
`/swagger-ui.html`.

## Структура

```
com.aromatica
├── config
├── security
├── common
└── modules
    ├── masterdata   каталог, контрагенты, настройки
    ├── inventory    склад и документы
    ├── finance      деньги и сертификаты
    ├── pos          касса
    └── workspace    задачи
```

В каждом модуле controller → endpoint → service → repository. Остатки и деньги
меняются только через проведение документов. Зависимости между модулями
проверяются ArchUnit-тестом.

Подробнее про устройство — в [docs](docs/ARCHITECTURE.md).

## Тесты

```bash
./mvnw test
```

База не нужна.

## Docker

```bash
docker build -t aromatica-backend .
```

Переменные: `SPRING_DATASOURCE_URL`, `SPRING_DATASOURCE_USERNAME`,
`SPRING_DATASOURCE_PASSWORD`, `APP_AUTH_JWT_SECRET`, `PORT`.
