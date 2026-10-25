#!/usr/bin/env bash
# accept.sh — бизнес-приёмка A1–A9 против запущенного приложения.
#
# Запускать на СВЕЖЕМ ./gradlew bootRun: проверки идут по порядку, каждая опирается на состояние
# после предыдущих. Проверяется класс ответа (2xx или 4xx) и поля тела (status, inventoryNumber,
# queuePosition, равенство id между шагами), а не конкретные коды HTTP.
#
# Использование:
#   scripts/accept.sh            A1–A9
#   scripts/accept.sh --a7b      плюс дополнительная проверка A7b (причина «нужно» короче 10 символов
#                                должна быть отклонена; есть только в ветке поломки 3, в зачёт N/9 не идёт)
#   scripts/accept.sh -v         печатать тело каждого ответа
#   BASE_URL=http://host:8080 scripts/accept.sh
#
# Переменные окружения:
#   BASE_URL         адрес приложения (по умолчанию http://localhost:8080)
#   ACCEPT_TIMEOUT   таймаут одного запроса, секунды (по умолчанию 30)
#
# Последняя строка вывода — «N/9». Код возврата: 0 — 9/9, 1 — есть провалы, 2 — сервер недоступен.

set -u

BASE_URL="${BASE_URL:-http://localhost:8080}"
BASE_URL="${BASE_URL%/}"
API="$BASE_URL/api/equipment-requests"
TIMEOUT="${ACCEPT_TIMEOUT:-30}"

RUN_A7B=0
VERBOSE=0
for arg in "$@"; do
  case "$arg" in
    --a7b)      RUN_A7B=1 ;;
    -v|--verbose) VERBOSE=1 ;;
    -h|--help)
      sed -n '2,20p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
      exit 0
      ;;
    *)
      echo "Неизвестный аргумент: $arg (см. --help)" >&2
      exit 2
      ;;
  esac
done

for tool in curl jq; do
  if ! command -v "$tool" >/dev/null 2>&1; then
    echo "Не найден $tool: он нужен для accept.sh (sudo apt install -y $tool / brew install $tool)" >&2
    exit 2
  fi
done

if [ -t 1 ]; then
  C_OK=$'\033[32m'; C_BAD=$'\033[31m'; C_DIM=$'\033[2m'; C_WARN=$'\033[33m'; C_OFF=$'\033[0m'
else
  C_OK=''; C_BAD=''; C_DIM=''; C_WARN=''; C_OFF=''
fi

# ------------------------------------------------------------------ сервер доступен?
curl -s -m 5 -o /dev/null "$BASE_URL/" 2>/dev/null
if [ $? -ne 0 ]; then
  cat >&2 <<EOF
Сервер не отвечает: $BASE_URL
Запустите приложение в отдельном терминале и дождитесь строки «Started EquipmentRequestsApplication»:
    ./gradlew bootRun
Если приложение на другом адресе: BASE_URL=http://host:port scripts/accept.sh
EOF
  exit 2
fi

# ------------------------------------------------------------------ помощники
CODE=""
BODY=""

# post EMAIL TYPE REASON -> CODE, BODY
post() {
  local payload resp rc
  payload="$(jq -n --arg e "$1" --arg t "$2" --arg r "$3" \
    '{employeeEmail: $e, equipmentType: $t, reason: $r}')"
  resp="$(curl -sS -m "$TIMEOUT" -X POST \
    -H 'Content-Type: application/json; charset=utf-8' -H 'Accept: application/json' \
    --data-binary "$payload" -w $'\n%{http_code}' "$API" 2>&1)"
  rc=$?
  if [ "$rc" -ne 0 ]; then
    CODE="000"
    BODY="curl: $resp"
  else
    CODE="${resp##*$'\n'}"
    BODY="${resp%$'\n'*}"
  fi
}

# jget FIELD — значение поля верхнего уровня ответа; пусто, если поля нет или тело не JSON.
jget() {
  printf '%s' "$BODY" | jq -r "(.$1 // empty) | tostring" 2>/dev/null | head -n1
}

# Тело одной строкой, без управляющих символов и не длиннее 160 символов.
snippet() {
  printf '%s' "$BODY" | tr '\r\n\t' '   ' | tr -d '\000-\037' | cut -c1-160
}

STEP=""
STEP_OK=1
PASSED=0
TOTAL=9

step() {
  STEP="$1"
  STEP_OK=1
  printf '\n%s  %s\n' "$1" "$2"
}

line_ok()  { printf '      %sok%s   %s\n' "$C_OK" "$C_OFF" "$*"; }
line_bad() { printf '      %sFAIL%s %s\n' "$C_BAD" "$C_OFF" "$*"; STEP_OK=0; }
line_req() {
  printf '      %s-> %s %s%s\n' "$C_DIM" "$1" "$2" "$C_OFF"
  [ "$VERBOSE" = 1 ] && printf '      %s   %s%s\n' "$C_DIM" "$(snippet)" "$C_OFF"
}

# Вспомогательный вывод «что пришло» для провалов.
got() { printf 'получено HTTP %s, тело: %s' "$CODE" "$(snippet)"; }
# Для проверок полей: тело показываем только при 2xx (иначе оно уже показано проверкой класса).
got_2xx() {
  case "$CODE" in
    2[0-9][0-9]) got ;;
    *)           printf 'HTTP %s' "$CODE" ;;
  esac
}

expect_class() { # 2|4 — класс HTTP-кода
  case "$CODE" in
    "$1"[0-9][0-9]) line_ok "HTTP ${CODE}: класс ${1}xx" ;;
    *) line_bad "ожидался класс ${1}xx; $(got)" ;;
  esac
}

expect_eq() { # FIELD EXPECTED
  local v
  v="$(jget "$1")"
  if [ "$v" = "$2" ]; then
    line_ok "$1 = $v"
  else
    line_bad "$1: ожидалось «$2», в ответе «${v:-<нет поля>}»; $(got_2xx)"
  fi
}

expect_prefix() { # FIELD PREFIX
  local v
  v="$(jget "$1")"
  case "$v" in
    "$2"*) line_ok "$1 = $v (префикс $2)" ;;
    *)     line_bad "$1: ожидался префикс «$2», в ответе «${v:-<нет поля>}»; $(got_2xx)" ;;
  esac
}

expect_id_same() { # NAME EXPECTED_ID
  local v
  v="$(jget id)"
  if [ -z "$2" ]; then
    line_bad "id: не с чем сравнить, на предыдущем шаге ($1) id не получен"
  elif [ "$v" = "$2" ]; then
    line_ok "id = $v (тот же, что в $1)"
  else
    line_bad "id: ожидался тот же, что в $1 («$2»), в ответе «${v:-<нет поля>}»; $(got_2xx)"
  fi
}

expect_id_present() {
  local v
  v="$(jget id)"
  if [ -n "$v" ]; then
    line_ok "id = $v"
  else
    line_bad "в ответе нет id; $(got_2xx)"
  fi
}

step_end() {
  if [ "$STEP_OK" = 1 ]; then
    printf '      %sPASS%s %s\n' "$C_OK" "$C_OFF" "$STEP"
    PASSED=$((PASSED + 1))
  else
    printf '      %sFAIL%s %s\n' "$C_BAD" "$C_OFF" "$STEP"
  fi
}

# ------------------------------------------------------------------ данные стенда
IVAN="ivan.petrov@company.com"
MARIA="maria.sidorova@company.com"
ALEKSEY="aleksey.ivanov@company.com"
OLGA="olga.smirnova@company.com"
PETR="petr.kuznetsov@company.com"

ID_IVAN=""
ID_ALEKSEY=""
HINT_STALE=0

printf 'Приёмка A1–A9 против %s\n' "$API"

# ------------------------------------------------------------------ A1
step A1 "Иван подаёт заявку на ноутбук"
post "$IVAN" LAPTOP "Нужен ноутбук для работы"
line_req "POST $IVAN LAPTOP" "$CODE"
expect_class 2
expect_eq status RESERVED
expect_prefix inventoryNumber LAP-
expect_id_present
ID_IVAN="$(jget id)"
if [ "$STEP_OK" = 0 ]; then
  case "$CODE" in
    2*) HINT_STALE=1 ;;
  esac
fi
# На свежей базе свободны только LAP-001 и LAP-002 (LAP-003 выдан в сиде).
case "$(jget inventoryNumber)" in
  ''|LAP-001|LAP-002) ;;
  *) HINT_STALE=1 ;;
esac
step_end

# ------------------------------------------------------------------ A2
step A2 "Иван подаёт ту же заявку повторно"
post "$IVAN" LAPTOP "Нужен ноутбук для работы"
line_req "POST $IVAN LAPTOP (повтор)" "$CODE"
expect_class 2
expect_id_same A1 "$ID_IVAN"
step_end

# ------------------------------------------------------------------ A3
step A3 "Мария, затем Алексей подают заявки на ноутбук"
post "$MARIA" LAPTOP "Нужен ноутбук для проекта"
line_req "POST $MARIA LAPTOP" "$CODE"
expect_class 2
expect_eq status RESERVED
post "$ALEKSEY" LAPTOP "Нужен ноутбук для разработки"
line_req "POST $ALEKSEY LAPTOP" "$CODE"
expect_class 2
expect_eq status QUEUED
expect_eq queuePosition 1
ID_ALEKSEY="$(jget id)"
step_end

# ------------------------------------------------------------------ A4
step A4 "Ольга подаёт заявку на ноутбук"
post "$OLGA" LAPTOP "Нужен ноутбук для аналитики"
line_req "POST $OLGA LAPTOP" "$CODE"
expect_class 2
expect_eq status QUEUED
expect_eq queuePosition 2
step_end

# ------------------------------------------------------------------ A5
step A5 "Ольга подаёт заявку на монитор"
post "$OLGA" MONITOR "Нужен второй монитор"
line_req "POST $OLGA MONITOR" "$CODE"
expect_class 2
expect_eq status QUEUED
expect_eq queuePosition 1
step_end

# ------------------------------------------------------------------ A6
step A6 "Уволенный Пётр; неизвестный email"
post "$PETR" LAPTOP "Нужен ноутбук для работы"
line_req "POST $PETR LAPTOP (уволен)" "$CODE"
expect_class 4
post "nobody@company.com" LAPTOP "Нужен ноутбук для работы"
line_req "POST nobody@company.com LAPTOP (неизвестный)" "$CODE"
expect_class 4
step_end

# ------------------------------------------------------------------ A7
step A7 "Пустая причина; тип PRINTER"
post "$IVAN" HEADSET ""
line_req "POST $IVAN HEADSET, reason=\"\"" "$CODE"
expect_class 4
post "$IVAN" PRINTER "Нужен принтер для печати"
line_req "POST $IVAN PRINTER" "$CODE"
expect_class 4
step_end

# ------------------------------------------------------------------ A7b (необязательная)
A7B_RESULT=""
if [ "$RUN_A7B" = 1 ]; then
  step A7b "Причина «нужно» (короче 10 символов) отклоняется; только ветка поломки 3, в N/9 не входит"
  post "$MARIA" HEADSET "нужно"
  line_req "POST $MARIA HEADSET, reason=\"нужно\"" "$CODE"
  expect_class 4
  if [ "$STEP_OK" = 1 ]; then
    printf '      %sPASS%s A7b\n' "$C_OK" "$C_OFF"
    A7B_RESULT="PASS"
  else
    printf '      %sFAIL%s A7b\n' "$C_BAD" "$C_OFF"
    A7B_RESULT="FAIL"
  fi
fi

# ------------------------------------------------------------------ A8
step A8 "Иван подаёт заявку на ноутбук, email в другом регистре и с пробелами"
post " IVAN.Petrov@Company.com " LAPTOP "Нужен ноутбук для работы"
line_req "POST \" IVAN.Petrov@Company.com \" LAPTOP" "$CODE"
expect_class 2
expect_id_same A1 "$ID_IVAN"
step_end

# ------------------------------------------------------------------ A9
step A9 "Алексей повторно подаёт заявку на ноутбук с другой причиной"
post "$ALEKSEY" LAPTOP "Совсем другая причина заявки"
line_req "POST $ALEKSEY LAPTOP (другая причина)" "$CODE"
expect_class 2
expect_id_same A3 "$ID_ALEKSEY"
expect_eq queuePosition 1
step_end

# ------------------------------------------------------------------ итог
printf '\n'
if [ "$HINT_STALE" = 1 ]; then
  printf '%sA1 вернул не «зарезервирован LAP-001/LAP-002»: перезапустите приложение, база не свежая%s\n' "$C_WARN" "$C_OFF"
  printf '%s(остановите bootRun, запустите снова и повторите accept.sh).%s\n' "$C_WARN" "$C_OFF"
elif [ "$PASSED" -lt "$TOTAL" ]; then
  printf '%sЕсли провалы выглядят странно, сначала перезапустите приложение (база должна быть свежей) и повторите.%s\n' "$C_WARN" "$C_OFF"
fi
[ -n "$A7B_RESULT" ] && printf 'A7b (дополнительно): %s\n' "$A7B_RESULT"

printf '%s/%s\n' "$PASSED" "$TOTAL"
[ "$PASSED" -eq "$TOTAL" ] && exit 0
exit 1
