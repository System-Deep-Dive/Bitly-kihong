#!/usr/bin/env python3
"""
Step 1 Phase별 테스트 데이터셋 생성 스크립트

각 Phase에서 동일한 조건으로 테스트하기 위해 Hot/Warm/Cold 분포를 반영한
테스트 데이터셋을 생성합니다.

사용법:
    python generate-dataset.py [--count COUNT] [--output OUTPUT]

옵션:
    --count: 생성할 URL 개수 (기본값: 1000)
    --output: 출력 파일 경로 (기본값: step1-dataset.json)
"""

import argparse
import json
import random
import requests
import sys
from typing import List, Dict
from datetime import datetime

BASE_URL = "http://localhost:8080"
API_ENDPOINT = f"{BASE_URL}/urls"


def create_short_url(original_url: str) -> Dict:
    """단축 URL을 생성합니다."""
    try:
        payload = {"originalUrl": original_url, "alias": None, "expirationDate": None}
        response = requests.post(
            API_ENDPOINT,
            json=payload,
            headers={"Content-Type": "application/json"},
            timeout=10,
        )

        if response.status_code == 201:
            data = response.json()
            return {
                "shortCode": data.get("shortCode"),
                "originalUrl": original_url,
                "shortUrl": data.get("shortUrl"),
            }
        else:
            print(
                f"Warning: Failed to create URL: {response.status_code} - {response.text}"
            )
            return None
    except Exception as e:
        print(f"Error creating URL {original_url}: {e}")
        return None


def generate_test_urls(count: int) -> List[Dict]:
    """테스트용 URL을 생성합니다."""
    urls = []

    print(f"Creating {count} test URLs...")

    for i in range(count):
        # 다양한 도메인과 경로로 URL 생성
        domain = random.choice(
            [
                "https://example.com",
                "https://example.org",
                "https://example.net",
            ]
        )
        path = f"/test/{i}/{random.randint(1000, 9999)}"
        original_url = f"{domain}{path}"

        url_data = create_short_url(original_url)
        if url_data:
            urls.append(url_data)

        # 진행 상황 표시
        if (i + 1) % 100 == 0:
            print(f"  Created {i + 1}/{count} URLs...")

    print(f"Successfully created {len(urls)} URLs")
    return urls


def categorize_urls(urls: List[Dict], total_count: int) -> Dict:
    """URL을 Hot/Warm/Cold로 분류합니다.

    - Hot: 상위 1% (10개) - 전체 트래픽의 50%
    - Warm: 상위 1%~10% (90개) - 전체 트래픽의 30%
    - Cold: 나머지 90% (900개) - 전체 트래픽의 20%
    """
    if not urls:
        return {"hot": [], "warm": [], "cold": [], "invalid": []}

    # Hot: 상위 1%
    hot_count = max(1, int(total_count * 0.01))
    # Warm: 상위 10% 중 Hot을 제외한 부분 (즉, 1%~10%)
    warm_total_count = max(1, int(total_count * 0.10))  # 전체 10%

    # Cold: 나머지 (90%)
    cold_count = len(urls) - warm_total_count

    # 분류: 순서대로 Hot → Warm → Cold
    hot_urls = urls[:hot_count]  # 처음 1%
    warm_urls = urls[hot_count:warm_total_count]  # 1% 다음부터 10%까지
    cold_urls = urls[warm_total_count:]  # 10% 이후

    # Invalid URLs: 존재하지 않는 코드 (404 테스트용)
    invalid_count = max(1, int(total_count * 0.02))
    invalid_urls = [f"INVALID{i:06d}" for i in range(invalid_count)]

    return {
        "hot": hot_urls,
        "warm": warm_urls,
        "cold": cold_urls,
        "invalid": invalid_urls,
    }


def main():
    parser = argparse.ArgumentParser(
        description="Generate test dataset for Phase testing"
    )
    parser.add_argument(
        "--count",
        type=int,
        default=1000,
        help="Number of URLs to create (default: 1000)",
    )
    parser.add_argument(
        "--output",
        type=str,
        default="step1-dataset.json",
        help="Output file path (default: step1-dataset.json)",
    )

    args = parser.parse_args()

    # API 서버 확인
    try:
        response = requests.get(f"{BASE_URL}/admin/health", timeout=5)
        if response.status_code != 200:
            print(f"Error: API server is not available at {BASE_URL}")
            print("Please start the application first.")
            sys.exit(1)
    except requests.exceptions.RequestException as e:
        print(f"Error: Cannot connect to API server at {BASE_URL}")
        print("Please start the application first.")
        sys.exit(1)

    print("=" * 60)
    print("Step 1 Test Dataset Generator")
    print("=" * 60)
    print(f"Target: {BASE_URL}")
    print(f"Count: {args.count} URLs")
    print(f"Output: {args.output}")
    print("=" * 60)
    print()

    # URL 생성
    urls = generate_test_urls(args.count)

    if not urls:
        print("Error: No URLs were created. Please check the API server.")
        sys.exit(1)

    # 분류
    print("\nCategorizing URLs...")
    categorized = categorize_urls(urls, args.count)

    print(f"  Hot URLs: {len(categorized['hot'])} (1% of total)")
    print(f"  Warm URLs: {len(categorized['warm'])} (10% of total)")
    print(f"  Cold URLs: {len(categorized['cold'])} (90% of total)")
    print(f"  Invalid URLs: {len(categorized['invalid'])} (404 test)")

    # 메타데이터 추가
    dataset = {
        "metadata": {
            "createdAt": datetime.now().isoformat(),
            "totalUrls": len(urls),
            "distribution": {
                "hot": len(categorized["hot"]),
                "warm": len(categorized["warm"]),
                "cold": len(categorized["cold"]),
                "invalid": len(categorized["invalid"]),
            },
            "description": "Step 1 Phase별 테스트용 데이터셋 (Hot/Warm/Cold 분포)",
        },
        "data": categorized,
    }

    # 파일 저장
    output_path = args.output
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(dataset, f, indent=2, ensure_ascii=False)

    print(f"\nDataset saved to: {output_path}")
    print("\n" + "=" * 60)
    print("Dataset generation completed!")
    print("=" * 60)
    print("\nNext steps:")
    print("1. Use this dataset in k6 test scenarios")
    print("2. Ensure all Phase tests use the same dataset")
    print(f"3. Dataset file: {output_path}")


if __name__ == "__main__":
    main()
