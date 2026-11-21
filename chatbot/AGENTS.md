# AGENTS.md — 희망환급 FAQ Chatbot

이 문서는 `chatbot/`에서 작업하는 AI 코딩 에이전트(Codex 등)를 위한 작업 규칙이다. 작업 전 `README.md`와 함께 읽는다.

## 1. 프로젝트 목적과 확정된 방향

희망환급은 청년 근로자의 연말정산 예상 환급액 계산과 관련 정보를 제공하는 웹 서비스다. 이 디렉터리는 **Python FastAPI 기반 세법 용어 FAQ 챗봇 서버**다.

- 주 기능: 연말정산·세법 용어에 관한 **사전 검증된 고정 FAQ 답변** 제공
- UI: **카테고리·질문 버튼을 기본으로 하고 자유 질문 입력을 보조로 제공**
- 로컬 LLM: **Ollama Gemma 3 (`gemma3:4b`)** 사용
- Gemma 3의 책임: 자유 질문에 대해 등록된 **FAQ ID 하나 또는 `null`**로 분류
- 답변의 유일한 출처: 서버에 저장된 검증된 FAQ 데이터의 `answer` 필드
- **LLM이 세법 답변을 새로 작성·수정·보완하거나 임의의 법령·금액·조건을 생성하지 않는다.**

현재 구현 상태(2026-11-20 수정본): FAQ 데이터, 조회·분류 API, Ollama 연동, Spring Boot 내부 토큰 연동 코드가 존재한다. 전체 자동 테스트 통과 여부는 별도로 검증한다.

## 2. 시스템 경계와 책임

```text
React (버튼 선택 / 자유 질문)
  → Spring Boot (JWT 인증·공통 응답·내부 서비스 호출)
  → Chatbot FastAPI
       ├─ 버튼 선택: FAQ ID 검증 → 저장된 답변 조회
       └─ 자유 질문: 입력 검증 → Gemma 3 FAQ 분류
                              → FAQ ID 검증 → 저장된 답변 조회
  → Spring Boot → React
```

- React는 Spring Boot API를 사용한다. Chatbot FastAPI를 브라우저에 직접 공개하지 않는다.
- Spring Boot는 이 FAQ API의 JWT 인증, 요청 검증, 공통 응답 및 FastAPI 호출을 담당한다. **대화방 소유권 검사·Oracle 대화 기록 저장은 현재 FAQ 구현 범위에 없다.**
- Chatbot FastAPI는 내부 서비스 인증, FAQ 목록·상세 조회, 자유 질문 분류, 고정 답변 반환을 담당한다.
- Calc 서버는 실제 세액 계산을 담당한다. 챗봇은 개인별 세액을 계산하거나 추정하지 않는다.
- Chatbot 서버가 Oracle에 대화 기록을 직접 저장하거나 회원 개인정보를 조회하지 않는다.
- 현재 코드의 외부 API 주소는 `/api/faqs`, `/api/faq-categories`, `/api/faqs/{faqId}`, `/api/faq-matches`; 내부 API는 `/faq` 계열이다. 변경 시 팀 API 명세서와 React·Spring을 함께 수정한다.

## 3. FAQ 데이터 설계 원칙

FAQ 원본은 **`app/data/tax_terms.json`** 파일에서 관리한다. 현재 구현은 DB나 모델 가중치에서 답변을 가져오지 않는다.

실제 JSON에서 사용하는 주요 필드:

- `id`: 안정적인 FAQ 식별자
- `category`: 메뉴 분류
- `term`: 버튼에 표시할 용어
- `hint`: 모델의 분류를 돕는 참고 설명(존재하는 항목에 한함)
- `aliases`: 자유 질문 매칭에 도움이 되는 유사 표현
- `answer`: 사람이 작성·검증한 고정 답변
- `source`: 검증 가능한 공식 출처 및 관련 조항

## 4. 질문 처리 및 답변 통제

### 버튼 선택

1. 클라이언트가 선택한 FAQ ID를 전달한다.
2. 서버가 해당 ID의 존재 여부를 확인한다. (현재 코드에는 별도의 공개·검증 상태 플래그 검사가 없다.)
3. 해당 FAQ의 **저장된 `answer`를 그대로 반환**한다.
4. 버튼 선택 경로에서는 Gemma 3를 호출하지 않는다.

### 자유 질문

1. 질문 길이·형식·민감정보 노출 위험을 검사한다. 불필요한 개인정보·개인 세액 데이터는 LLM에 전달하지 않는다.
2. `match_simple_question()`으로 단순 용어 질문을 우선 처리하고, 나머지는 Gemma 3에 **허용된 FAQ ID 중 하나 또는 JSON `null`**을 선택하도록 요청한다.
3. 모델 출력은 신뢰하지 않는다. JSON 형식과 허용된 FAQ ID 여부를 서버에서 검증한다.
4. 분류에 실패하거나 모델이 `null`을 선택하면 매칭 실패(`matched: false`)로 처리한다. 관련성이 낮은 질문을 억지로 매칭하지 않는다.
5. 유효한 ID라면 서버 저장소의 **고정 답변만** 반환한다. 모델의 생성 문장을 최종 답변으로 사용하지 않는다.
6. LLM 오류·시간 초과 시에는 정해진 오류 또는 버튼형 FAQ 안내로 안전하게 복귀한다.

- 모델이 출력한 자체 `confidence` 값만으로 정확성을 보장하지 않는다. 분류 성능과 오분류·거절률을 별도로 평가한다.
- FAQ 수가 많아지면 키워드 검색 등 후보 추출 단계를 **성능 측정 후** 추가할 수 있다. 검색 기술은 사전 승인 없이 도입하지 않는다.
- 프롬프트만으로 답변 범위 제한이 보장된다고 주장하지 않는다.

## 5. 기술 스택과 실행 명령

현재 사용: Python 3.11, FastAPI, Uvicorn, Pydantic, python-dotenv, Ollama, Gemma 3 (`gemma3:4b`), pytest, Ruff. 모델 추가 학습·LoRA/QLoRA·PyTorch는 구현에 필요하지 않다.

Windows CMD, `chatbot/` 기준:

```cmd
py -3.11 -m venv .venv
.venv\Scripts\activate
python -m pip install -r requirements-dev.txt
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8001
python -m pytest -v
python -m ruff check .
python -m ruff format --check .
```

코드 자동 정리: `python -m ruff format .`

## 6. 폴더 및 코드 구성

현재 구현된 핵심 구조:

```text
chatbot/
├── app/
│   ├── main.py              # load_dotenv · 토큰 기동 검사
│   ├── api/
│   │   ├── deps.py         # 내부 인증
│   │   ├── faq.py          # FAQ 조회·질문 분류
│   │   └── health.py       # /health
│   ├── data/tax_terms.json # FAQ 원본
│   └── services/classifier.py # 단순 매칭 · Ollama 분류
├── tests/                  # FAQ·health·벤치마크 (인증 테스트 보완 필요)
├── .env.example
├── requirements.txt
├── requirements-dev.txt
├── pyproject.toml
├── README.md
└── AGENTS.md
```

빈 디렉터리를 일괄 생성하지 않는다. API 라우터는 검증과 응답을, 서비스는 흐름을, 저장소는 FAQ 조회를, LLM 모듈은 모델 호출을 담당한다. Pydantic 모델을 사용하고 snake_case/PascalCase 명명 규칙 및 Ruff 설정을 따른다. 주석과 사용자 안내 문구는 한글로 작성한다.

## 7. API·환경변수·보안

현재 구현된 FastAPI 내부 API (FAQ 계열은 `X-Chatbot-Token` 필요):

| 방식 | 경로 | 설명 |
|---|---|---|
| GET | `/health` | 상태 확인, 인증 불필요 |
| GET | `/faq` | FAQ 목록·선택적 `category` 필터 |
| GET | `/faq/categories` | 카테고리 |
| GET | `/faq/{faq_id}` | FAQ 상세 |
| POST | `/faq/ask` | 1~100자 질문 분류, 반환 키는 `faqId` |

Spring Boot 외부 경로는 `/api/faqs`, `/api/faq-categories`, `/api/faqs/{faqId}`, `/api/faq-matches`이다. 외부 성공·오류 응답은 Spring의 `{success, data, meta, error}` 공통 봉투다.

현재 `.env.example`과 로컬 `.env`에 필요한 변수 이름은 `CHATBOT_HOST`, `CHATBOT_PORT`, `CHATBOT_API_TOKEN`, `OLLAMA_BASE_URL`, `OLLAMA_MODEL`이다. 모델 태그는 `gemma3:4b`를 사용한다. `main.py`가 `load_dotenv()`를 수행하며 기동 시 토큰 유무를 확인한다. 내부 토큰은 `app/api/deps.py`에서 `X-Chatbot-Token` 헤더와 비교한다.

- 비밀정보·토큰·`.env`·`.venv`·캐시·모델 가중치를 Git에 올리지 않는다.
- FAQ 경로 인증 실패는 401, 유효성 오류는 FastAPI 422(외부 Spring은 400 `VALIDATION`), Ollama 호출 오류는 503을 반환한다. Spring은 챗봇 장애 시 503 `CHATBOT_UNAVAILABLE` + `errorId`를 반환한다.
- 자유 질문 원문, 개인정보, 재무정보를 불필요하게 로그에 남기지 않는다. 현재 분류기의 모델 원본 응답 `print()`는 추후 운영용 로깅 정책에 맞춰 제거 또는 제한해야 한다.
- 내부 FastAPI와 Ollama를 외부 인터넷에 직접 노출하지 않는다. 상세 스택 트레이스는 응답에 포함하지 않는다.

## 8. 작업 방식 및 테스트

큰 변경은 **기존 코드와 팀 API 계약을 먼저 분석하고 변경 계획을 보고**한다. 사용자 승인 없이 코드 수정, 새 파일 생성, 패키지 추가, API 계약 변경을 진행하지 않는다.

작업 순서:

1. 요구사항과 현재 구현 상태 확인
2. FAQ 데이터·응답 범위·API 책임 설계
3. 변경 계획 및 신규 파일 필요성 보고
4. 승인된 범위만 구현
5. pytest·Ruff 검사 및 실제 동작 확인
6. Spring Boot 통합 영향 확인
7. 수정 파일·검증 결과·미완료 항목 보고

필수 테스트:

- 기존 `/health` 정상 동작
- 버튼 ID의 정상 조회·존재하지 않는 ID 처리 (미검증 FAQ 별도 차단은 현재 코드에 없음)
- 자유 질문의 정상 분류·`null` 미매칭·모호한 질문·잘못된 ID·비정상 JSON·100자 제한
- Ollama 미실행·연결 실패·시간 초과
- **최종 답변이 반드시 저장된 FAQ `answer`와 일치하는지** 확인
- LLM 호출 없이 실행 가능한 단위 테스트 우선 작성
- 내부 인증 구현 후 정상·누락·잘못된 토큰 검증
- 실제 테스트하지 않은 항목은 `미검증`으로 명시

완료 조건:

```cmd
python -m ruff check .
python -m ruff format --check .
python -m pytest -v
```

## 9. Git 및 문서 관리

- 기능 개발은 각 담당자의 개인 브랜치에서 진행한다.
- 개발이 완료된 기능은 해당 `feature/기능명` 브랜치에 반영한다.
- 기능명세서 1행 완료 시 `develop` 브랜치로 PR하여 통합한다.
- 2개 이상의 행이 연결되어 개별 검증이 불가능한 경우 하나의 PR로 통합하고, 그 이유를 PR 본문에 작성한다.
- `feature/기능명` → `develop`: PR 작성자 외 팀원 2인 승인
- `develop` → `main`: 형상관리자·조장 승인
- `develop`은 기능 통합 및 최종 검증을 위한 브랜치로 사용한다.
- `main`은 최종 안정 버전만 반영하는 브랜치로 사용한다.
- 공통 브랜치나 다른 담당자의 개인·기능 브랜치에 직접 커밋하지 않는다.
- 커밋은 의미 있는 기능 구현·수정 단위로 한다.
- 커밋 메시지는 종류: `무엇을 했는지` 형식으로 작성하고 설명은 한글로 작성한다.
- `커밋 종류`는 feat, fix, chore, docs, test, refactor, ci 등을 사용한다.
- 사용자 확인 없이 git push, 브랜치 삭제, 강제 푸시(git push -f)를 수행하지 않는다.

## 10. 금지 사항

- FAQ에 없는 세법 답변을 LLM이 직접 생성하여 사용자에게 제공하지 않는다.
- 자유 질문을 무조건 기존 FAQ 중 하나에 매칭하지 않는다.
- Calc 서버의 세액 계산이나 Spring Boot의 회원·대화 기록 관리를 중복 구현하지 않는다.
- 기존 정상 기능을 임의로 삭제하거나 프로젝트 전체를 재작성하지 않는다.
- 실제 개인정보·인증 토큰을 테스트 데이터나 Git 이력에 넣지 않는다.
- 오류를 숨기기 위해 pytest·Ruff 규칙을 임의로 끄지 않는다.

## 11. 참고 문서

- `README.md`: 실행 방법 및 현재 상태 (기존 `tests/test_faq.py`는 토큰 인증 반영 필요)
- `pyproject.toml`: Ruff·pytest 설정
- `.env.example`: 환경변수 항목
- 팀 기능명세서 v1.13, ERD v4.1, 팀 공식 API 명세서
- `backend/AGENTS.md`, `calc/AGENTS.md`: 관련 서버 개발 규칙

참고 문서와 실제 코드가 충돌하면 차이를 보고하고, API 계약 변경 시 팀 공식 API 명세서를 함께 최신화한다. 구현되지 않은 대화 저장·신규 분류 정확도 기능은 완료로 표시하지 않는다.
