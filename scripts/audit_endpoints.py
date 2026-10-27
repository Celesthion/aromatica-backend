#!/usr/bin/env python3
"""Аудит «висячих» REST-эндпоинтов: какие контроллеры бэка не зовёт фронт.

У API Aromatica один потребитель — фронт (веб и мобильная касса). Скрипт сверяет
пары (HTTP-метод, путь) контроллеров с вызовами adminFetch во фронте и печатает
эндпоинты без единого вызова. Exit-код 1, если такие нашлись — годится как
проверка в CI (мёртвые API ловятся автоматически).

Использование:
    python3 scripts/audit_endpoints.py --frontend ../aromatica-frontend
    (--backend по умолчанию — src/main/java этого репозитория)
"""
import argparse
import re
import sys
from pathlib import Path

# --- пути ---
ap = argparse.ArgumentParser()
ap.add_argument("--backend", default=str(Path(__file__).resolve().parent.parent / "src/main/java"))
ap.add_argument("--frontend", required=True, help="корень фронта (ищем src/**/*.ts,tsx)")
args = ap.parse_args()

back_root = Path(args.backend)
front_root = Path(args.frontend)
if not back_root.exists():
    sys.exit(f"backend не найден: {back_root}")
front_src = front_root / "src"
if not front_src.exists():
    sys.exit(f"frontend/src не найден: {front_src}")


def static_prefix(path: str) -> str:
    """Статический префикс пути: обрезаем шаблон/квери (${…}, {…}, ?, кавычки)."""
    path = re.split(r"\$\{|[`'\"?]|\{", path)[0]
    return path.rstrip("/")


# --- 1. эндпоинты бэка: (verb, full_path) ---
backend = []
mapping_re = re.compile(r'@(Get|Post|Put|Delete|Query)Mapping(?:\("([^"]*)"\))?')
base_re = re.compile(r'@RequestMapping\("([^"]*)"\)')
for ctrl in back_root.rglob("*Controller.java"):
    text = ctrl.read_text()
    bm = base_re.search(text)
    base = bm.group(1) if bm else ""
    for m in mapping_re.finditer(text):
        verb = m.group(1).upper()
        sub = m.group(2) or ""
        full = (base + sub) if sub else base
        backend.append((verb, full, ctrl.name))

# --- 2. вызовы фронта: (method, static_path) ---
# adminFetch<...>(  '<path>'  [, <HELPER>(...)] )  — генерик и перенос строки допустимы.
call_re = re.compile(
    r"adminFetch\s*(?:<[^(]*>)?\s*\(\s*[`'\"](/api/[^`'\"]*)[`'\"]\s*(?:,\s*([A-Za-z]+))?"
)
HELPER = {"POST": "POST", "PUT": "PUT", "DEL": "DELETE", "QUERY": "QUERY"}
frontend = set()
for f in list(front_src.rglob("*.ts")) + list(front_src.rglob("*.tsx")):
    t = f.read_text()
    for m in call_re.finditer(t):
        path, helper = m.group(1), m.group(2)
        method = HELPER.get(helper, "GET") if helper else "GET"
        frontend.add((method, static_prefix(path)))
    # прямые fetch на auth (обновление сессии и т.п.)
    for m in re.finditer(r"fetch\(`\$\{API_BASE_URL\}(/api/[^`]*)`", t):
        frontend.add(("POST", static_prefix(m.group(1))))

# --- 3. сверка ---
dangling = [
    (v, p, c) for (v, p, c) in backend if (v, static_prefix(p)) not in frontend
]

print(f"эндпоинтов бэка: {len(backend)} | вызовов фронта: {len(frontend)}")
if not dangling:
    print("OK — висячих эндпоинтов нет.")
    sys.exit(0)

print("\nВИСЯЧИЕ (нет вызова с фронта):")
for v, p, c in sorted(dangling, key=lambda x: (x[2], x[1])):
    print(f"  {v:6} {p:45} {c}")
sys.exit(1)
