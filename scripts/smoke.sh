#!/usr/bin/env bash
set -e

HOST="${HOST:-localhost:9091}"
PROTO_DIR="${PROTO_DIR:-src/main/proto}"
KEY="smoke-key"

grpcurl -plaintext -emit-defaults \
  -import-path "$PROTO_DIR" \
  -proto kv.proto \
  -d "{\"key\":\"$KEY\"}" \
  "$HOST" kv.KvService/Delete || true

grpcurl -plaintext -emit-defaults \
  -import-path "$PROTO_DIR" \
  -proto kv.proto \
  -d '{}' \
  "$HOST" kv.KvService/Count

grpcurl -plaintext \
  -import-path "$PROTO_DIR" \
  -proto kv.proto \
  -d "{\"key\":\"$KEY\",\"value\":\"AQID\"}" \
  "$HOST" kv.KvService/Put

grpcurl -plaintext -emit-defaults \
  -import-path "$PROTO_DIR" \
  -proto kv.proto \
  -d "{\"key\":\"$KEY\"}" \
  "$HOST" kv.KvService/Get

grpcurl -plaintext -emit-defaults \
  -import-path "$PROTO_DIR" \
  -proto kv.proto \
  -d "{\"key\":\"$KEY\"}" \
  "$HOST" kv.KvService/Delete

grpcurl -plaintext -emit-defaults \
  -import-path "$PROTO_DIR" \
  -proto kv.proto \
  -d "{\"key\":\"$KEY\"}" \
  "$HOST" kv.KvService/Get