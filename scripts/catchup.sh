#!/usr/bin/env bash
# catchup.sh N — догнать группу: перейти на чекпоинт N.
#
#   scripts/catchup.sh 1     spec.md после clarify (отстали к 20:30)
#   scripts/catchup.sh 1r    то же + эталонный research.md (plan не укладывается по времени)
#   scripts/catchup.sh 2     + plan, research, data-model, contracts, tasks, отчёт analyze (отстали к 21:10)
#   scripts/catchup.sh 3     код по всем задачам (отстали к 21:40)
#
# Скрипт создаёт локальную ветку catchup-cpN из origin/lab-sdd/checkpoint-N и записывает
# .specify/feature.json с папкой фичи из чекпоинта (specs/NNN-…): Spec Kit 1.2.x находит фичу
# по этому файлу, а не по имени ветки, и файл в git не хранится (он в .specify/.gitignore).
#
# Если в рабочей копии есть изменения, они прячутся в git stash (вместе с новыми файлами) под
# именем «catchup-N»; вернуть их можно командой git stash list / git stash pop.

set -u

usage() {
  sed -n '2,15p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

if [ "$#" -ne 1 ]; then
  usage >&2
  exit 2
fi

case "$1" in
  -h|--help) usage; exit 0 ;;
  1|1r|2|3)  N="$1" ;;
  *)
    echo "Неверный номер чекпоинта: «$1». Допустимо: 1, 1r, 2, 3." >&2
    exit 2
    ;;
esac

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT" || exit 2

REMOTE_REF="origin/lab-sdd/checkpoint-$N"
BRANCH="catchup-cp$N"

echo "Обновляю origin..."
if ! git fetch origin; then
  echo "Не удалось выполнить git fetch origin (нет сети?). Продолжаю с тем, что уже есть локально." >&2
fi

if ! git rev-parse --verify --quiet "$REMOTE_REF^{commit}" >/dev/null; then
  echo "Нет ветки $REMOTE_REF. Проверьте сеть и повторите: git fetch origin" >&2
  exit 1
fi

if git show-ref --verify --quiet "refs/heads/$BRANCH"; then
  echo "Ветка $BRANCH уже существует." >&2
  echo "Переключиться на неё: git switch $BRANCH" >&2
  echo "Начать заново:        git switch lab-sdd/start && git branch -D $BRANCH && scripts/catchup.sh $N" >&2
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "В рабочей копии есть незакоммиченные изменения: прячу их в stash «catchup-$N»."
  if ! git stash push -u -m "catchup-$N"; then
    echo "Не удалось выполнить git stash push. Закоммитьте или уберите изменения вручную и повторите." >&2
    exit 1
  fi
  echo "Вернуть их позже: git stash list, затем git stash pop (возможны конфликты с чекпоинтом)."
fi

if ! git switch -c "$BRANCH" "$REMOTE_REF"; then
  echo "Не удалось создать ветку $BRANCH из $REMOTE_REF." >&2
  exit 1
fi

FEATURE_DIR="$(find specs -mindepth 1 -maxdepth 1 -type d -name '[0-9][0-9][0-9]-*' 2>/dev/null | sort | tail -n 1)"
if [ -z "$FEATURE_DIR" ]; then
  echo "В чекпоинте нет папки specs/NNN-…: .specify/feature.json не записан." >&2
  exit 1
fi
printf '{"feature_directory": "%s"}\n' "$FEATURE_DIR" > .specify/feature.json

echo
echo "Готово: вы на ветке $BRANCH (чекпоинт $N), фича — $FEATURE_DIR."
echo "Дальше продолжайте с нужного этапа; проверка: scripts/check.sh"
