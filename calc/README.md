# 희망환급 (Hope Refund) — Calc

희망환급 프로젝트의 **Python FastAPI 계산 서버**입니다.

연말정산 예상 세액 계산, 청년 혜택 진단, 카드·홈택스 파일 처리 및 지출 분류를 담당합니다.

> **현재 상태:** 예상 세액 계산 · 청년 혜택 진단 API 구현 완료 / 카드·홈택스 파일 처리 · 지출 분류 개발 예정

## 1. 개발 환경

| 구분 | 기술 |
|---|---|
| Language | Python 3.11.9 |
| Framework | FastAPI |
| ASGI Server | Uvicorn |
| Database | Oracle Database 21c XE |
| Dependency Management | pip, requirements.txt |
| Testing | pytest |
| Code Quality | Ruff |
| IDE | VS Code 권장 |

세부 개발 규칙과 최종 디렉터리 구조는 `AGENTS.md`를 참고합니다.

## 2. 프로젝트 구조

```text
calc/
├── AGENTS.md
├── README.md
├── .env.example
├── requirements.txt
├── requirements-dev.txt
├── pyproject.toml
├── app/
│   ├── main.py            # FastAPI 시작 · 오류 응답 통일 ({"error", "message"})
│   ├── api/               # health · calculate(POST /calculate) · benefit(POST /benefit) · deps(X-Calc-Token 검사)
│   ├── schemas/           # 요청·응답 모양 (pydantic)
│   ├── services/          # 세액 계산 · 카드 소득공제 · 구간 계산 · 혜택 진단 (순수 계산 · DB 안 봄)
│   ├── repositories/      # 기준값 읽기 (Oracle · 5분 캐시)
│   └── db/                # Oracle 연결 (읽기 전용 계정)
├── database/              # DB 작업 파일 (스키마 · 기준값 · 가상데이터 SQL · 읽기 계정 안내)
└── tests/                 # pytest (기준값은 가짜 데이터로 바꿔 끼움 · DB 없이 실행)
```

카드·홈택스 파일 처리, 지출 분류는 담당자가 개발하면서 추가합니다.

## 3. 개발환경 준비

### 3.1 프로젝트 폴더 이동

Windows CMD에서 실행합니다.

```cmd
cd /d C:\workspace\two-trillion\calc
```

### 3.2 Python 가상환경 생성

```cmd
py -3.11 -m venv .venv
```

### 3.3 가상환경 활성화

```cmd
.venv\Scripts\activate
```

정상적으로 활성화되면 CMD 앞에 `(.venv)`가 표시됩니다.

### 3.4 패키지 설치

```cmd
python -m pip install -r requirements-dev.txt
```

`requirements.txt`는 서버 실행에 필요한 패키지를, `requirements-dev.txt`는 개발·테스트 도구까지 포함합니다.

## 4. 환경변수 설정

`.env.example`에 필요한 환경변수 이름이 정의되어 있습니다.

| 환경변수 | 설명 |
|---|---|
| `DB_USERNAME` | Oracle DB 계정 — 계산 서버 전용 **읽기 계정** (`database/README.md`) |
| `DB_PASSWORD` | Oracle DB 비밀번호 |
| `DB_DSN` | Oracle 접속 주소 `주소:포트/서비스이름` (python-oracledb는 JDBC 주소를 쓰지 않음) |
| `CALC_API_TOKEN` | Spring Boot와 Calc 서버 간 인증 토큰 — `backend/.env`와 같은 값 · **비우면 모든 계산 요청을 막음** |
| `CALC_HOST` | FastAPI 실행 주소 |
| `CALC_PORT` | FastAPI 실행 포트 |

`.env.example`을 복사해 `calc/.env`로 만들면 서버 시작 시 자동으로 읽습니다 (python-dotenv).

실제 비밀번호와 토큰은 GitHub에 올리지 않습니다.

## 5. 서버 실행

가상환경을 활성화한 상태에서 실행합니다.

```cmd
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8000
```

정상 실행 후 다음 주소에 접속합니다.

**Health API**

```text
http://127.0.0.1:8000/health
```

정상 응답:

```json
{
  "status": "OK"
}
```

**계산 API (Spring 서버가 호출 · `X-Calc-Token` 헤더 필요)**

```text
POST http://127.0.0.1:8000/calculate   예상 세액 계산
POST http://127.0.0.1:8000/benefit     청년 혜택 자격 진단
```

FastAPI 자동 문서(`/docs`)는 내부 서버라 꺼 두었습니다. 요청·응답 모양은 `app/schemas/`를 참고합니다.

서버 종료는 `Ctrl + C`로 진행합니다.

## 6. 테스트 및 코드 검사

모든 명령은 `calc` 디렉터리에서 실행합니다.

### pytest

```cmd
python -m pytest -v
```

### Ruff 코드 검사

```cmd
python -m ruff check .
```

### Ruff 형식 검사

```cmd
python -m ruff format --check .
```

### Ruff 자동 정리

```cmd
python -m ruff format .
```

pytest 60개(health 1 + 계산·진단 59) 및 Ruff lint·format 통과를 확인했습니다. 테스트는 기준값을 가짜 데이터로 바꿔 끼워 DB 없이 실행됩니다.

## 7. 현재 구현 상태

**구현 및 검증 완료**

- Python 3.11.9 가상환경 · FastAPI · Health API · Ruff lint/formatter
- `POST /calculate` 예상 세액 계산 (근로소득공제 · 인적공제 · 연금·보험 · 카드 소득공제 · 세율 구간 · 근로소득세액공제 · 표준/특별세액공제 자동 비교 · 월세·주택청약·연금계좌 · 자녀 · 중소기업 취업자 감면)
- `POST /benefit` 청년 혜택 자격 진단 (해당 · 비해당 · 정보 부족)
- Oracle DB 기준값 읽기 전용 조회 (`DEDUCTION_TYPE` · `TAX_BRACKET` · `BENEFIT_CONDITION` · 5분 캐시)
- `X-Calc-Token` 인증 검증 (토큰 미설정 시 모든 요청 차단)
- pytest 테스트 60개
- Spring Boot(`backend/`) 연동 — `HttpCalcClient`로 호출 확인

**추후 개발 예정**

- 카드·홈택스 파일 파싱
- 지출 자동 분류

## 8. 참고 문서

- `AGENTS.md`: Calc 서버 개발 규칙 및 담당 영역
- `database/README.md`: DB 작업 파일 실행 순서 · 계산 서버 읽기 계정
- 희망환급 기능명세서 v1.13
- ERD v4.1
- 팀 API 공통 규칙

각 기능의 실제 요청·응답 규격은 공식 API 명세서를 기준으로 구현합니다.