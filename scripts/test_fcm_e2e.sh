#!/usr/bin/env bash
# scripts/test_fcm_e2e.sh
# ────────────────────────────────────────────────────────────────────────────
# Automated FCM end-to-end test for ElderCare Guardian backend.
#
# Tests the /notify endpoint at each alert level and verifies the response.
# Requires a running backend (local or deployed) and a valid FCM token.
#
# Usage:
#   chmod +x scripts/test_fcm_e2e.sh
#   ./scripts/test_fcm_e2e.sh                         # uses defaults
#   BACKEND_URL=https://your.railway.app ./scripts/test_fcm_e2e.sh
#   FCM_TOKEN=your_token BACKEND_URL=... ./scripts/test_fcm_e2e.sh
#
# Environment variables:
#   BACKEND_URL   Backend base URL (default: http://localhost:3001)
#   FCM_TOKEN     FCM device token for caregiver device (default: test_token)
#   PATIENT_NAME  Patient name to use in alert messages (default: Test Patient)
# ────────────────────────────────────────────────────────────────────────────

set -euo pipefail

BACKEND_URL="${BACKEND_URL:-http://localhost:3001}"
FCM_TOKEN="${FCM_TOKEN:-test_fcm_token_replace_me}"
PATIENT_NAME="${PATIENT_NAME:-Test Patient}"

PASS=0
FAIL=0

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

pass() { echo -e "${GREEN}✅ PASS${NC} $1"; PASS=$((PASS+1)); }
fail() { echo -e "${RED}❌ FAIL${NC} $1"; FAIL=$((FAIL+1)); }
info() { echo -e "${YELLOW}ℹ${NC}  $1"; }

echo "═══════════════════════════════════════════════════"
echo " ElderCare Guardian — FCM E2E Test"
echo " Backend: $BACKEND_URL"
echo "═══════════════════════════════════════════════════"
echo ""

# ── 1. Health check ──────────────────────────────────────────────────────────
info "Test 1: Health check"
HEALTH_RESP=$(curl -sf --max-time 10 "$BACKEND_URL/health" 2>/dev/null || echo "CURL_ERROR")
if echo "$HEALTH_RESP" | grep -q '"status":"ok"'; then
    pass "Health check — $HEALTH_RESP"
else
    fail "Health check failed — got: $HEALTH_RESP"
fi

# ── 2. Missing fields → 400 ──────────────────────────────────────────────────
info "Test 2: Missing required fields → expect 400"
RESP=$(curl -sf --max-time 10 -o /dev/null -w "%{http_code}" \
    -X POST "$BACKEND_URL/notify" \
    -H "Content-Type: application/json" \
    -d '{"token":"tok"}' 2>/dev/null || echo "000")
if [ "$RESP" = "400" ]; then
    pass "Missing fields → 400"
else
    fail "Missing fields → expected 400, got $RESP"
fi

# ── 3. Alert levels ───────────────────────────────────────────────────────────
for LEVEL in Check Warning Emergency; do
    info "Test: Notify level=$LEVEL"
    HTTP_CODE=$(curl -sf --max-time 15 -o /tmp/fcm_resp_$LEVEL.json -w "%{http_code}" \
        -X POST "$BACKEND_URL/notify" \
        -H "Content-Type: application/json" \
        -d "{\"token\":\"$FCM_TOKEN\",\"level\":\"$LEVEL\",\"reason\":\"E2E test — $LEVEL alert\",\"patientName\":\"$PATIENT_NAME\"}" \
        2>/dev/null || echo "000")

    BODY=$(cat /tmp/fcm_resp_$LEVEL.json 2>/dev/null || echo "")

    if [ "$HTTP_CODE" = "200" ]; then
        if echo "$BODY" | grep -q '"success":true'; then
            pass "Level $LEVEL → 200 success=true | $(echo "$BODY" | grep -o '"messageId":"[^"]*"' || echo 'no messageId')"
        elif echo "$BODY" | grep -q '"success":false'; then
            # FCM error (e.g. invalid token) but backend responded correctly
            FCM_ERR=$(echo "$BODY" | grep -o '"error":"[^"]*"' || echo "")
            fail "Level $LEVEL → 200 but success=false | $FCM_ERR"
            info "  → This is expected if FCM_TOKEN is a placeholder. Deploy backend + use real token."
        else
            fail "Level $LEVEL → unexpected body: $BODY"
        fi
    elif [ "$HTTP_CODE" = "429" ]; then
        pass "Level $LEVEL → 429 rate-limited (backend rate-limit working)"
    else
        fail "Level $LEVEL → HTTP $HTTP_CODE | $BODY"
    fi
done

# ── 4. Rate limiting ─────────────────────────────────────────────────────────
info "Test 4: Rate limiting — send same token twice within 10s"
RESP1=$(curl -sf --max-time 10 -o /dev/null -w "%{http_code}" \
    -X POST "$BACKEND_URL/notify" \
    -H "Content-Type: application/json" \
    -d "{\"token\":\"rate_limit_test_token\",\"level\":\"Check\",\"reason\":\"First call\",\"patientName\":\"Test\"}" \
    2>/dev/null || echo "000")
RESP2=$(curl -sf --max-time 10 -o /dev/null -w "%{http_code}" \
    -X POST "$BACKEND_URL/notify" \
    -H "Content-Type: application/json" \
    -d "{\"token\":\"rate_limit_test_token\",\"level\":\"Check\",\"reason\":\"Second call within 10s\",\"patientName\":\"Test\"}" \
    2>/dev/null || echo "000")

if [ "$RESP2" = "429" ]; then
    pass "Rate limiter → second call within 10s returned 429"
elif [ "$RESP1" = "200" ] && [ "$RESP2" = "200" ]; then
    fail "Rate limiter not working — both calls returned 200"
else
    fail "Rate limiter test inconclusive — first=$RESP1, second=$RESP2"
fi

# ── 5. Summary ────────────────────────────────────────────────────────────────
echo ""
echo "═══════════════════════════════════════════════════"
TOTAL=$((PASS+FAIL))
echo " Results: $PASS/$TOTAL passed"
if [ "$FAIL" -gt 0 ]; then
    echo -e " ${RED}$FAIL test(s) failed${NC}"
    echo ""
    echo " Notes:"
    echo "  • If FCM tests show success=false with 'invalid token':"
    echo "    Get a real token from the app: Settings → long-press Backend URL"
    echo "    Or run: adb logcat | grep FcmTokenManager"
    echo "  • If backend is not running locally:"
    echo "    cd apps/backend && npm run dev"
    echo "  • For deployed backend:"
    echo "    BACKEND_URL=https://your.railway.app ./scripts/test_fcm_e2e.sh"
    echo "═══════════════════════════════════════════════════"
    exit 1
else
    echo -e " ${GREEN}All tests passed${NC}"
    echo "═══════════════════════════════════════════════════"
fi
