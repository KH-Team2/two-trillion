# AGENTS.md — 희망환급 FAQ Chatbot

이 문서는 `chatbot/`에서 작업하는 AI 코딩 에이전트(Codex 등)를 위한 작업 규칙이다. 작업 전 `README.md`와 함께 읽는다.

## 1. 프로젝트 목적과 확정된 방향

희망환급은 청년 근로자의 연말정산 예상 환급액 계산과 관련 정보를 제공하는 웹 서비스다. 이 디렉터리는 **Python FastAPI 기반 세법 용어 FAQ 챗봇 서버**다.

- 주 기능: 연말정산·세법 용어에 관한 **사전 검증된 고정 FAQ 답변** 제공
- UI: **카테고리·질문 버튼을 기본으로 하고 자유 질문 입력을 보조로 제공**
- 로컬 LLM: **Ollama를 통한 Gemma 3** 사용 예정
- Gemma 3의 책임: 자유 질문의 의미를 파악하여 등록된 **FAQ ID 하나 또는 `UNKNOWN`**으로 분류
- 답변의 유일한 출처: 서버에 저장된 검증된 FAQ 데이터의 `answer` 필드
- **LLM이 세법 답변을 새로 작성·수정·보완하거나 임의의 법령·금액·조건을 생성하지 않는다.**

현재 실제 구현 상태는 FastAPI 기본 서버와 `GET /health`뿐이다. FAQ 데이터, 분류 API, Ollama 연동, Spring Boot 연동은 아직 구현되지 않았다.

## 2. 시스템 경계와 책임

```text
React (버튼 선택 / 자유 질문)
  → Spring Boot (회원 인증·권한·대화 기록·호출 제한)
  → Chatbot FastAPI
       ├─ 버튼 선택: FAQ ID 검증 → 저장된 답변 조회
       └─ 자유 질문: 입력 검증 → Gemma 3 FAQ 분류
                              → FAQ ID 검증 → 저장된 답변 조회
  → Spring Boot → React
```

- React는 Spring Boot API를 사용한다. Chatbot FastAPI를 브라우저에 직접 공개하지 않는다.
- Spring Boot는 회원 인증, 대화방 소유권 확인, 요청 횟수 제한, Oracle DB의 대화 기록 저장·조회를 담당한다.
- Chatbot FastAPI는 내부 서비스 인증, FAQ 목록·상세 조회, 자유 질문 분류, 고정 답변 반환을 담당한다.
- Calc 서버는 실제 세액 계산을 담당한다. 챗봇은 개인별 세액을 계산하거나 추정하지 않는다.
- Chatbot 서버가 Oracle에 대화 기록을 직접 저장하거나 회원 개인정보를 조회하지 않는다.
- API 경로·DTO·인증 헤더는 팀 공식 API 명세서 및 Spring Boot 담당자와 협의하기 전까지 확정하지 않는다.

## 3. FAQ 데이터 설계 원칙

FAQ는 모델 가중치에만 저장하지 않고 **별도 데이터 원본(JSON 또는 DB; 저장 방식 미확정)**으로 관리한다.

권장 논리 필드(실제 스키마 확정 전 예시):

- `id`: 안정적인 FAQ 식별자
- `category`: 메뉴 분류
- `question`: 버튼에 표시할 대표 질문
- `aliases`: 자유 질문 매칭에 도움이 되는 유사 표현
- `answer`: 사람이 작성·검증한 고정 답변
- `source`: 검증 가능한 공식 출처 및 관련 조항

## 4. 질문 처리 및 답변 통제

### 버튼 선택

1. 클라이언트가 선택한 FAQ ID를 전달한다.
2. 서버가 해당 ID의 존재 여부와 공개·검증 상태를 확인한다.
3. 해당 FAQ의 **저장된 `answer`를 그대로 반환**한다.
4. 버튼 선택 경로에서는 Gemma 3를 호출하지 않는다.

### 자유 질문

1. 질문 길이·형식·민감정보 노출 위험을 검사한다. 불필요한 개인정보·개인 세액 데이터는 LLM에 전달하지 않는다.
2. 후보 FAQ를 구성하고 Gemma 3에 **허용된 FAQ ID 중 하나 또는 `UNKNOWN`**만 선택하도록 요청한다.
3. 모델 출력은 신뢰하지 않는다. JSON 형식·허용 ID·FAQ 공개 상태를 서버에서 검증한다.
4. 관련성이 부족하거나 모호하면 `UNKNOWN` 또는 후보 재선택 응답으로 처리한다. 잘못된 FAQ를 억지로 선택하지 않는다.
5. 유효한 ID라면 서버 저장소의 **고정 답변만** 반환한다. 모델의 생성 문장을 최종 답변으로 사용하지 않는다.
6. LLM 오류·시간 초과 시에는 정해진 오류 또는 버튼형 FAQ 안내로 안전하게 복귀한다.

- 모델이 출력한 자체 `confidence` 값만으로 정확성을 보장하지 않는다. 분류 성능과 오분류·거절률을 별도로 평가한다.
- FAQ 수가 많아지면 키워드 검색 등 후보 추출 단계를 **성능 측정 후** 추가할 수 있다. 검색 기술은 사전 승인 없이 도입하지 않는다.
- 프롬프트만으로 답변 범위 제한이 보장된다고 주장하지 않는다.

## 5. 기술 스택과 실행 명령

현재 사용: Python 3.11.9, FastAPI, Uvicorn, Pydantic, pytest, Ruff.

예정: Ollama, Gemma 3. 모델 태그와 실행 하드웨어는 설치·검증 후 확정한다. LoRA/QLoRA, PyTorch, 학습 라이브러리는 **현재 필수 의존성이 아니다.**

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

현재 구현된 구조:

```text
chatbot/
├── app/
│   ├── __init__.py
│   ├── main.py
│   └── api/
│       ├── __init__.py
│       └── health.py
├── tests/test_health.py
├── .env.example
├── requirements.txt
├── requirements-dev.txt
├── pyproject.toml
├── README.md
└── AGENTS.md
```

기능 구현 시 필요에 따라 추가할 수 있는 모듈(아직 존재하지 않음):

```text
app/api/          # FAQ 조회·자유 질문 라우터
app/schemas/      # Pydantic 요청·응답
app/services/     # FAQ 조회 및 분류 흐름
app/llm/          # Ollama·Gemma 3 분류 호출
app/core/         # 설정·내부 인증
app/repositories/ # FAQ 저장소 접근
data/             # 검증된 FAQ 원본 및 평가 자료
tests/            # 단위·통합 테스트
docs/             # 결정·작업 이력(필요 시)
```

빈 디렉터리를 일괄 생성하지 않는다. API 라우터는 검증과 응답을, 서비스는 흐름을, 저장소는 FAQ 조회를, LLM 모듈은 모델 호출을 담당한다. Pydantic 모델을 사용하고 snake_case/PascalCase 명명 규칙 및 Ruff 설정을 따른다. 주석과 사용자 안내 문구는 한글로 작성한다.

## 7. API·환경변수·보안

현재 구현된 API:

```http
GET /health
```

현재 응답:

```json
{"status":"OK"}
```

FAQ 카테고리·질문 목록·상세 조회 및 자유 질문 분류 API는 **미구현·미확정**이다. 임의로 팀 공통 API 계약을 확정하지 않는다.

현재 `.env.example`의 설정 항목: `CHATBOT_HOST`, `CHATBOT_PORT`, `CHATBOT_API_TOKEN`, `OLLAMA_BASE_URL`, `OLLAMA_MODEL`.

- `.env.example`은 안내용이며 실제 자동 로딩·내부 인증은 아직 구현되지 않았다.
- 내부 공유 토큰 인증의 헤더명·검증 방식은 연동 전에 확정한다. 인증 구현 전에는 인증이 완료됐다고 표시하지 않는다.
- 비밀정보·토큰·`.env`·`.venv`·캐시·모델 가중치를 Git에 올리지 않는다.
- 자유 질문, 대화 기록, 개인정보, 민감한 재무정보를 불필요하게 로그에 기록하지 않는다.
- 내부 FastAPI와 Ollama를 인증 없이 외부 인터넷에 노출하지 않는다.
- 사용자 입력·모델 출력·외부 자료는 신뢰할 수 없는 데이터로 취급한다.
- 서버 오류와 모델 오류를 구분하며 내부 스택 트레이스를 사용자에게 노출하지 않는다.

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
- 버튼 ID의 정상 조회·존재하지 않는 ID·미검증 FAQ 차단
- 자유 질문의 정상 분류·`UNKNOWN`·모호한 질문·잘못된 ID·비정상 JSON
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

- `README.md`: 실행 방법 및 현재 상태
- `pyproject.toml`: Ruff·pytest 설정
- `.env.example`: 환경변수 항목
- 팀 기능명세서 v1.13, ERD v4.1, 팀 공식 API 명세서
- `backend/AGENTS.md`, `calc/AGENTS.md`: 관련 서버 개발 규칙

참고 문서와 현재 합의된 챗봇 방향이 충돌하면 임의로 해석하지 않고 차이를 보고한다. 본 문서의 새 FAQ 아키텍처는 챗봇의 개발 방향이며 팀 공식 API 계약을 대체하지 않는다.
