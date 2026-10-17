"""Transforma erros de compilação e falhas de testes em anotações do GitHub Actions.

Uso: python3 .github/scripts/report.py <log> [<log> ...]
As anotações aparecem no resumo da execução e podem ser lidas pela API (check-runs/<id>/annotations).
"""
import glob
import re
import sys
import xml.etree.ElementTree as ET

MAX_ANNOTATIONS = 9
LINES_PER_ANNOTATION = 12


def escape(text: str) -> str:
    return text.replace("%", "%25").replace("\r", "").replace("\n", "%0A")


def emit(kind: str, title: str, lines: list[str]) -> None:
    print(f"::{kind} title={escape(title)}::{escape(chr(10).join(lines))}")


def compile_errors(paths: list[str]) -> list[str]:
    errors = []
    pattern = re.compile(r"^(e: .*|.*error: .*|.*FAILURE: .*|> .*|.*Execution failed.*|\* What went wrong:.*)$")
    for path in paths:
        try:
            with open(path, encoding="utf-8", errors="replace") as handle:
                for line in handle:
                    line = line.rstrip()
                    if pattern.match(line) and "warning" not in line.lower():
                        short = re.sub(r"file:///home/runner/work/[^/]+/[^/]+/", "", line)
                        if short not in errors:
                            errors.append(short)
        except FileNotFoundError:
            pass
    return errors


def test_results() -> tuple[int, int, list[str]]:
    total = failed = 0
    failures = []
    for path in glob.glob("**/build/test-results/**/*.xml", recursive=True):
        try:
            root = ET.parse(path).getroot()
        except ET.ParseError:
            continue
        for case in root.iter("testcase"):
            total += 1
            problem = case.find("failure")
            if problem is None:
                problem = case.find("error")
            if problem is not None:
                failed += 1
                message = (problem.get("message") or problem.text or "").strip().splitlines()
                failures.append(f"{case.get('classname')}.{case.get('name')}: {' | '.join(message[:6])[:900]}")
    return total, failed, failures


def main() -> None:
    errors = compile_errors(sys.argv[1:])
    total, failed, failures = test_results()
    used = 0
    for start in range(0, len(failures), 4):
        if used >= MAX_ANNOTATIONS:
            break
        emit("error", f"Testes com falha ({failed}/{total})", failures[start:start + 4])
        used += 1
    for start in range(0, len(errors), LINES_PER_ANNOTATION):
        if used >= MAX_ANNOTATIONS:
            break
        emit("error", "Erros de build", errors[start:start + LINES_PER_ANNOTATION])
        used += 1
    emit("notice", "Resumo", [f"testes: {total}, falhas: {failed}, linhas de erro de build: {len(errors)}"])


if __name__ == "__main__":
    main()
