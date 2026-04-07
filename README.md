# Java gRPC KV Service

gRPC-сервис на Java для работы с key-value хранилищем поверх Tarantool

## API

- `put(key, value)` - сохраняет в БД значение для новых ключей и перезаписывает значение для существующих,
- `get(key)` - возвращает значение для указанного ключа,
- `delete(key)` - удаляет значение для указанного ключа,
- `range(key_since, key_to)` - server-streaming диапазона пар `key-value`,
- `count()` - возвращает кол-во записей в БД.

## Стек

- Java 17
- gRPC + Protobuf
- Tarantool 3.2.x
- `io.tarantool:tarantool-client:1.5.0`

## Реализация

- данные хранятся в `space KV`
- `value` поддерживает `null`
- `Count()` реализован через отдельный `KV_META` со счётчиком
- `Range()` читает данные постранично, без загрузки всего диапазона в память

## Структура проекта

- `Application` — точка входа, поднимает gRPC server
- `KvGrpcService` — gRPC-слой
- `KvRepository` — интерфейс хранилища
- `TarantoolKvRepository` — реализация репозитория через Tarantool
- `KvRecord` — внутренняя модель записи
- `tarantool/init.lua` — схема БД, функции Tarantool, пользователь `app`
- `scripts/smoke.sh` — быстрый smoke test
- `scripts/check-delete.sh` — ручная проверка delete
- `BulkDataSeeder` — загрузка большого количества данных
- `GrpcVerificationRunner` — проверка API на загруженном наборе
- `LargeRangeRunner` — проверка большого `range`

## Запуск

### 1. Поднять Tarantool

```bash

docker compose up -d
```

### 2. Запустить gRPC server

```bash

mvn compile exec:java -Dexec.mainClass="vk.grpc.Application" -Dgrpc.port=9091
```

### По умолчанию используются:
- Tarantool host: `localhost`
- Tarantool port: `3303`
- Tarantool user: `app`
- Tarantool password: `app`
- gRPC port: `9091`


## Быстрая проверка

```bash

bash scripts/smoke.sh
```

## Ручная проверка через grpcurl

`Put`:

```bash

grpcurl -plaintext \
  -import-path src/main/proto \
  -proto kv.proto \
  -d '{"key":"k1","value":"AQID"}' \
  localhost:9091 kv.KvService/Put
```

`Get`:

```bash

grpcurl -plaintext -emit-defaults \
  -import-path src/main/proto \
  -proto kv.proto \
  -d '{"key":"k1"}' \
  localhost:9091 kv.KvService/Get
```

`Count`:

```bash

grpcurl -plaintext -emit-defaults \
  -import-path src/main/proto \
  -proto kv.proto \
  -d '{}' \
  localhost:9091 kv.KvService/Count
```

## Проверка на большом объёме данных

Загрузка данных:

```bash

mvn compile exec:java \
  -Dexec.mainClass="vk.grpc.tools.BulkDataSeeder" \
  -Dtotal=500000
```

Проверка API:

```bash

mvn compile exec:java \
  -Dexec.mainClass="vk.grpc.tools.GrpcVerificationRunner" \
  -Dgrpc.port=9091 \
  -DmaxKey=500000
```

Проверка большого `Range`:

```bash

mvn compile exec:java \
  -Dexec.mainClass="vk.grpc.tools.LargeRangeRunner" \
  -Dgrpc.port=9091 \
  -DfromIndex=0 \
  -DtoIndex=499999
```


### Что уже проверено
- корректная работа Put/Get/Delete/Range/Count
- поддержка null в value
- корректная работа Range на больших диапазонах
- корректная работа на большом наборе данных с помощью BulkDataSeeder
