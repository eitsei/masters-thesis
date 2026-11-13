#!/usr/bin/env bash
# ==============================================================================
# Helper script to trigger Enterprise Closed-Corpus Ingestion into pgvector
# ==============================================================================
set -euo pipefail

URL="${RAG_APP_URL:-http://localhost:8080}"
MODE="${1:-both}"
CLEAR="${2:-false}"

echo "========================================================================"
echo " Triggering Enterprise Closed-Corpus Ingestion"
echo " Endpoint:        ${URL}/api/ingest/enterprise-closed"
echo " Mode:            $MODE (both | original | s2b)"
echo " Clear Database:  $CLEAR"
echo " Target Tables:   vector_store_enterprise_closed"
echo "                  vector_store_enterprise_closed_large"
echo "                  vector_store_enterprise_closed_small"
echo "========================================================================"

response=$(curl -s -w "\n%{http_code}" -X POST "${URL}/api/ingest/enterprise-closed?mode=${MODE}&clearDatabase=${CLEAR}" \
  -H "Content-Type: application/json")

http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | sed '$d')

if [ "$http_code" -eq 200 ]; then
  echo -e "\n✓ Ingestion successful (HTTP $http_code):"
  echo "$body" | python3 -m json.tool 2>/dev/null || echo "$body"
else
  echo -e "\n✗ Ingestion failed (HTTP $http_code):"
  echo "$body"
  exit 1
fi
