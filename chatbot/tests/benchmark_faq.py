"""실제 Ollama를 호출하는 FAQ 분류 벤치마크. pytest와 별도로 실행."""

import argparse
import csv
import json
import sys
import time
from pathlib import Path

# backend-python 디렉터리에서 실행: python tests/benchmark_faq.py --terms app/data/tax_terms.json
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.services.classifier import classify_question

ORDERS = ("original", "reverse", "random42")


def load_terms(path):
    data = json.loads(Path(path).read_text(encoding="utf-8-sig"))
    if isinstance(data, list):
        return data
    if isinstance(data, dict):
        for key in ("terms", "items", "faqs"):
            if isinstance(data.get(key), list):
                return data[key]
    raise ValueError(
        "FAQ JSON은 배열 또는 terms/items/faqs 배열을 가진 객체여야 합니다."
    )


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--terms", required=True, help="실제 서비스가 사용하는 FAQ JSON 파일 경로"
    )
    parser.add_argument(
        "--cases", default=str(Path(__file__).with_name("faq_test_cases.json"))
    )
    parser.add_argument("--output", default="faq_benchmark_results.csv")
    parser.add_argument("--orders", nargs="+", choices=ORDERS, default=list(ORDERS))
    args = parser.parse_args()
    terms = load_terms(args.terms)
    cases = json.loads(Path(args.cases).read_text(encoding="utf-8-sig"))
    results = []
    for order in args.orders:
        correct = 0
        print(f"\n=== {order} / {len(cases)}문항 ===", flush=True)
        for index, case in enumerate(cases, 1):
            start = time.perf_counter()
            try:
                actual = classify_question(
                    case["question"], terms, candidate_order=order
                )
                error = ""
            except Exception as exc:
                actual = None
                error = f"{type(exc).__name__}: {exc}"
            elapsed = round(time.perf_counter() - start, 3)
            ok = not error and actual == case["expected"]
            correct += ok
            results.append(
                {
                    "order": order,
                    "number": case["number"],
                    "question": case["question"],
                    "expected": case["expected"] or "null",
                    "actual": actual or "null",
                    "correct": int(ok),
                    "seconds": elapsed,
                    "error": error,
                }
            )
            print(
                f"[{index:02d}/{len(cases)}] {'OK' if ok else 'FAIL'} | expected={case['expected']} actual={actual} | {elapsed:.1f}s",
                flush=True,
            )
            # 중간 중단에도 결과 보존
            with open(args.output, "w", encoding="utf-8-sig", newline="") as f:
                writer = csv.DictWriter(f, fieldnames=list(results[0]))
                writer.writeheader()
                writer.writerows(results)
        print(
            f"{order}: {correct}/{len(cases)} = {correct / len(cases):.1%}", flush=True
        )
    print(f"\n결과 저장: {Path(args.output).resolve()}")


if __name__ == "__main__":
    main()
