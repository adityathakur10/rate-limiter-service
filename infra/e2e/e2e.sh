#!/usr/bin/env bash
# End-to-end check of the whole service against a running app (default http://localhost:8080):
# register a tenant, create a rule, hammer /v1/check until 429, edit the rule and see the limit move,
# then the error and no-rule cases and a rule delete. Needs only bash and curl.
#
#   BASE_URL=http://localhost:8080 ./infra/e2e/e2e.sh
#
# Exits non-zero on the first failed expectation. Each run makes its own tenant, so it can be
# repeated against the same database.
set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
HEADERS="$(mktemp)"
trap 'rm -f "$HEADERS"' EXIT
PASSED=0

fail() { echo "FAIL: $*" >&2; exit 1; }
pass() { PASSED=$((PASSED + 1)); echo "  ok  $*"; }
step() { echo; echo "== $*"; }

# json_field <json> <field>: the first string value of "field" (enough for these flat responses)
json_field() { echo "$1" | sed -nE "s/.*\"$2\":\"([^\"]*)\".*/\1/p"; }

# header_value <name>: value of a response header from the last request, empty if absent
header_value() { grep -i "^$1:" "$HEADERS" | head -1 | sed -E 's/^[^:]*:[[:space:]]*//' | tr -d '\r'; }

# call <method> <path> [json-body]: sets STATUS and BODY, and writes the response headers to $HEADERS
call() {
  local method="$1" path="$2" body="${3:-}"
  if [ -n "$body" ]; then
    BODY="$(curl -s -o - -D "$HEADERS" -w '\n%{http_code}' -X "$method" "$BASE_URL$path" \
      -H 'Content-Type: application/json' -d "$body")"
  else
    BODY="$(curl -s -o - -D "$HEADERS" -w '\n%{http_code}' -X "$method" "$BASE_URL$path")"
  fi
  STATUS="${BODY##*$'\n'}"
  BODY="${BODY%$'\n'*}"
}

expect_status() { [ "$STATUS" = "$1" ] || fail "$2: expected HTTP $1, got $STATUS ($BODY)"; pass "$2 -> $1"; }
expect_header() { [ "$(header_value "$1")" = "$2" ] || fail "$3: header $1 expected '$2', got '$(header_value "$1")'"; pass "$3: $1 = $2"; }
expect_no_header() { [ -z "$(header_value "$1")" ] || fail "$2: header $1 should be absent, got '$(header_value "$1")'"; pass "$2: no $1"; }

check() { # check <endpoint> <method> [apiKey]
  call POST /v1/check "{\"tenantId\":\"$TENANT_ID\",\"apiKey\":\"${3:-$API_KEY}\",\"endpoint\":\"$1\",\"method\":\"$2\"}"
}

step "1. register a tenant"
call POST /admin/v1/tenants "{\"name\":\"e2e-$(date +%s)\"}"
expect_status 201 "create tenant"
TENANT_ID="$(json_field "$BODY" id)"
API_KEY="$(json_field "$BODY" apiKey)"
[ -n "$TENANT_ID" ] && [ -n "$API_KEY" ] || fail "tenant response had no id or apiKey: $BODY"

step "2. create a rule: POST /pay, 3 requests per 600 seconds"
call POST /admin/v1/rules "{\"tenantId\":\"$TENANT_ID\",\"endpoint\":\"/pay\",\"method\":\"POST\",\"limit\":3,\"windowSeconds\":600}"
expect_status 201 "create rule"
RULE_ID="$(json_field "$BODY" id)"
[ -n "$RULE_ID" ] || fail "rule response had no id: $BODY"

step "3. three requests are allowed and remaining falls"
for remaining in 2 1 0; do
  check /pay POST
  expect_status 200 "check (remaining should be $remaining)"
  expect_header X-RateLimit-Limit 3 "limit"
  expect_header X-RateLimit-Remaining "$remaining" "remaining"
  expect_no_header X-RateLimit-Status "not degraded"
done

step "4. the fourth is denied"
check /pay POST
expect_status 429 "over the limit"
[ -n "$(header_value Retry-After)" ] || fail "429 had no Retry-After header"
pass "Retry-After = $(header_value Retry-After)"

step "5. raise the limit to 6 through admin: the next check sees it at once"
call PUT "/admin/v1/rules/$RULE_ID" "{\"tenantId\":\"$TENANT_ID\",\"endpoint\":\"/pay\",\"method\":\"POST\",\"limit\":6,\"windowSeconds\":600}"
expect_status 200 "update rule"
check /pay POST
expect_status 200 "check after the edit"
expect_header X-RateLimit-Limit 6 "new limit"

step "6. the 429 point moved: keep going until denied"
ALLOWED_AFTER_EDIT=1
for _ in 1 2 3 4 5 6 7 8; do
  check /pay POST
  [ "$STATUS" = "200" ] && ALLOWED_AFTER_EDIT=$((ALLOWED_AFTER_EDIT + 1)) || break
done
expect_status 429 "denied again at the new limit"
[ "$ALLOWED_AFTER_EDIT" -ge 2 ] || fail "expected more requests allowed after raising the limit, got $ALLOWED_AFTER_EDIT"
pass "$ALLOWED_AFTER_EDIT requests were allowed after the edit (3 before it)"

step "7. a request no rule applies to is allowed without limit headers"
check /orders GET
expect_status 200 "no matching rule"
expect_no_header X-RateLimit-Limit "no limit to report"
expect_no_header X-RateLimit-Status "not degraded"

step "8. authentication"
check /pay POST rl_not_a_real_key
expect_status 401 "wrong api key"

step "9. delete the rule: limiting stops on the next check"
call DELETE "/admin/v1/rules/$RULE_ID"
expect_status 204 "delete rule"
check /pay POST
expect_status 200 "check after the delete"
expect_no_header X-RateLimit-Limit "rule is gone"

echo
echo "All $PASSED checks passed."
