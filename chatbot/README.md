# 희망환급 (Hope Refund) — FAQ Chatbot

희망환급의 **Python FastAPI 기반 연말정산·세법 용어 FAQ 챗봇 서버**입니다.

사용자는 카테고리·질문 **버튼**을 눌러 FAQ를 조회하거나, **자유 질문**을 입력할 수 있습니다. 자유 질문은 로컬 **Gemma 3**가 등록된 FAQ 항목으로 분류하고, 실제 답변은 사람이 작성·검증한 **고정 FAQ 데이터**에서 조회합니다. 모델이 세법 답변을 직접 생성하지 않습니다.

> **현재 상태:** FastAPI 초기 프로젝트와 `/health`만 구현 완료. FAQ 조회, Gemma 3 분류, Spring Boot 연동은 개발 예정입니다.

## 1. 개발 방향

| 구분 | 계획 |
|---|---|
| 핵심 기능 | 연말정산·세법 용어 FAQ 안내 |
| 질문 방식 | 버튼 선택 기본 + 자유 입력 보조 |
| 답변 방식 | 사전 작성·검증한 고정 답변 조회 |
| 로컬 LLM | Ollama + Gemma 3 (미연동) |
| LLM 역할 | 자유 질문 → FAQ ID 또는 `UNKNOWN` 분류 |
| 백엔드 | Python 3.11.9, FastAPI, Uvicorn, Pydantic |
| 테스트·품질 | pytest, Ruff |

모델을 추가 학습하더라도 **답변의 원본은 검증된 FAQ 데이터**로 유지합니다.

## 2. 전체 시스템 구성

```text
React Frontend
  ├─ FAQ 카테고리·질문 버튼
  └─ 자유 질문 입력
        ↓
Spring Boot Backend
  ├─ 회원 인증·대화방 소유권·요청 제한
  └─ Oracle DB 대화 기록 저장·조회
        ↓ (내부 HTTP; 아직 미연동)
Python FastAPI Chatbot
  ├─ 버튼: FAQ ID 확인 → 고정 답변 조회
  └─ 자유 질문: Gemma 3 FAQ 분류 → ID 확인 → 고정 답변 조회
        ↓
Spring Boot → React (검증된 FAQ 답변)
```

별도 Calc 서버는 연말정산 계산을 담당합니다. 챗봇은 사용자별 세액을 계산하거나 세법상 확정 판단을 내리지 않습니다.

프로젝트의 상위 구조:

```text
two-trillion/
├── frontend/   # React + Vite
├── backend/    # Spring Boot + MyBatis
├── calc/       # Python 계산 서버
└── chatbot/    # Python FAQ 챗봇 서버
```

## 3. 현재 파일 구조

```text
chatbot/
├── .env.example
├── AGENTS.md
├── README.md
├── requirements.txt
├── requirements-dev.txt
├── pyproject.toml
├── app/
│   ├── __init__.py
│   ├── main.py
│   └── api/
│       ├── __init__.py
│       └── health.py
└── tests/
    └── test_health.py
```

FAQ 저장소, FAQ 조회 라우터, Gemma 3 분류 모듈, 추가 테스트는 아직 존재하지 않으며 기능 개발 단계에서 생성할 예정입니다.

## 4. 개발환경 준비 (Windows CMD)

### 4.1 사전 준비

- Python 3.11.9
- Git, VS Code 등 개발 도구
- Ollama 및 Gemma 3는 자유 질문 분류 구현 단계에서 별도로 설치

이미 `C:\workspace\two-trillion\chatbot` 폴더가 있다면 다시 Clone할 필요가 없습니다. 해당 폴더에서 진행합니다.

### 4.2 가상환경 및 패키지

```cmd
py -3.11 -m venv .venv
.venv\Scripts\activate
python -m pip install -r requirements-dev.txt
```

- `requirements.txt`: 서버 실행 의존성
- `requirements-dev.txt`: 개발·테스트 의존성
- `.venv`는 개인별 로컬 환경으로 Git에 올리지 않습니다.

### 4.3 환경변수

`.env.example`의 현재 항목:

| 변수 | 용도 | 예시 또는 상태 |
|---|---|---|
| `CHATBOT_HOST` | FastAPI 주소 | `127.0.0.1` |
| `CHATBOT_PORT` | FastAPI 포트 | `8001` |
| `CHATBOT_API_TOKEN` | Spring Boot ↔ Chatbot 내부 인증 | 실제 값 미설정 |
| `OLLAMA_BASE_URL` | Ollama 주소 | `http://localhost:11434` |
| `OLLAMA_MODEL` | 로컬 LLM 태그 | `gemma3` (실제 태그 확인 필요) |

현재 환경변수 자동 로딩과 내부 토큰 검증은 미구현입니다. 비밀정보와 `.env` 파일은 Git에 올리지 않습니다.

## 5. FastAPI 실행

`chatbot/`에서 가상환경을 활성화한 뒤 실행합니다.

```cmd
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8001
```

- Health: `http://127.0.0.1:8001/health`
- API 문서: `http://127.0.0.1:8001/docs`
- 종료: `Ctrl + C`

현재 `GET /health`의 정상 응답:

```json
{"status":"OK"}
```

이 Health 응답은 FastAPI의 기본 동작만 확인합니다. Ollama 또는 FAQ 기능의 정상 동작을 의미하지 않습니다.

## 6. FAQ 동작 예시 (설계안, 미구현)

### 버튼 선택

```text
'과세표준이란?' 버튼 클릭
  → FAQ ID 전달
  → 서버에서 ID·검증 상태 확인
  → 저장된 고정 답변 반환
```

### 자유 질문

```text
'세율을 적용하는 기준 금액이 뭐야?' 입력
  → 입력 검증
  → Gemma 3가 FAQ ID 또는 UNKNOWN 분류
  → 서버가 ID 유효성·매칭 적합성 확인
  → 저장된 고정 답변 반환 또는 재선택 안내
```

**Gemma 3가 생성한 문장은 세법 답변으로 사용하지 않습니다.** 모호하거나 목록 밖의 질문을 억지로 기존 FAQ에 연결하지 않습니다.

### FAQ 데이터 구조 예시

```json
{
  "id": "TAX_001",
  "category": "연말정산 기본 용어",
  "question": "과세표준이란 무엇인가요?",
  "aliases": ["과세표준 뜻", "세율을 적용하는 기준 금액"],
  "answer": "검증 후 등록할 고정 답변",
  "source": "검증 후 기입할 공식 출처",
}
```

## 7. 테스트 및 코드 검사

```cmd
python -m pytest -v
python -m ruff check .
python -m ruff format --check .
```

필요 시 자동 정리:

```cmd
python -m ruff format .
```

## 8. 개발 예정 순서

1. FAQ 범위·카테고리 및 검증 절차 확정
2. 초기 FAQ 데이터(예: 30~50개) 작성·출처·귀속연도 검증
3. FAQ 카테고리·목록·상세 조회 API 구현
4. Ollama 및 Gemma 3 로컬 실행 환경 확인
5. 자유 질문 → FAQ ID 분류 기능 구현
6. 잘못된 매칭·거절·오류 상황 평가
7. Spring Boot API·인증·대화 기록 통합

초기부터 FAQ를 대량 생산하거나 모델을 학습시키는 대신, 작은 검증 데이터셋으로 기능과 정확도를 확인한 후 확장합니다.

## 9. 관련 문서 및 협업 규칙

- [`AGENTS.md`](AGENTS.md): 코딩 에이전트 규칙, 답변 통제 및 테스트 기준
- `.env.example`: 환경변수 안내
- `requirements.txt`, `requirements-dev.txt`: 의존성
- `pyproject.toml`: Ruff·pytest 설정
- 팀 기능명세서 v1.13, ERD v4.1, 팀 공식 API 명세서

API 경로·요청·응답 필드와 내부 인증 방식은 팀 공식 API 명세 및 Spring Boot 담당자와 협의 후 확정합니다. 이 문서는 **새 챗봇의 목표 설계**를 설명하며, 미구현 기능을 구현 완료로 표시하지 않습니다.