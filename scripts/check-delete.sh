#!/usr/bin/env bash
set -e

HOST="${HOST:-localhost:9091}"
PROTO_DIR="${PROTO_DIR:-src/main/proto}"
KEY="${KEY:-delete-check-key}"

echo " cleanup before test "
grpcurl -plaintext -emit-defaults \
  -import-path "$PROTO_DIR" \
  -proto kv.proto \
  -d "{\"key\":\"$KEY\"}" \
  "$HOST" kv.KvService/Delete || true

echo
echo " count before put "
grpcurl -plaintext -emit-defaults \
  -import-path "$PROTO_DIR" \
  -proto kv.proto \
  -d '{}' \
  "$HOST" kv.KvService/Count

echo
echo " put "
grpcurl -plaintext \
  -import-path "$PROTO_DIR" \
  -proto kv.proto \
  -d "{\"key\":\"$KEY\",\"value\":\"AQID\"}" \
  "$HOST" kv.KvService/Put

echo
echo " get after put "
grpcurl -plaintext -emit-defaults \
  -import-path "$PROTO_DIR" \
  -proto kv.proto \
  -d "{\"key\":\"$KEY\"}" \
  "$HOST" kv.KvService/Get

echo
echo " count after put "
grpcurl -plaintext -emit-defaults \
  -import-path "$PROTO_DIR" \
  -proto kv.proto \
  -d '{}' \
  "$HOST" kv.KvService/Count

echo
echo " delete "
grpcurl -plaintext -emit-defaults \
  -import-path "$PROTO_DIR" \
  -proto kv.proto \
  -d "{\"key\":\"$KEY\"}" \
  "$HOST" kv.KvService/Delete

echo
echo " get after delete "
grpcurl -plaintext -emit-defaults \
  -import-path "$PROTO_DIR" \
  -proto kv.proto \
  -d "{\"key\":\"$KEY\"}" \
  "$HOST" kv.KvService/Get

echo
echo " count after delete "
grpcurl -plaintext -emit-defaults \
  -import-path "$PROTO_DIR" \
  -proto kv.proto \
  -d '{}' \
  "$HOST" kv.KvService/Count