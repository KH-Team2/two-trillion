# AGENTS.md — 희망환급 FAQ 챗봇·맞춤 절세 가이드

`chatbot/` 담당 AI 코딩 에이전트 작업 규칙입니다. 작업 전 `README.md`와 실제 코드를 읽습니다.

## 1. 프로젝트 목적과 확정된 방향

- FastAPI + Ollama Gemma 3 4B 기반 내부 서버로 **FAQ ID 분류**와 **가이드 설명 생성**을 담당한다.
- FAQ: 모델은 FAQ ID 하나 또는 `null`을 선택하며, 답변은 `tax_terms.json`의 `answer`를 **그대로 반환**한다. FAQ 답변 생성·보완은 금지한다.
- 가이드: 모델은 `tax_guides.json`의 `facts`만 근거로 `description`, `taxBenefit`, `nextStep` 설명을 **생성**한다. 실패하면 `fallback_content`를 사용한다.
- 근거 없는 법률·수치·공제 조건 추가, 공제 유형 혼동, 가입 권유, 세금 절감 보장 금지.
- 실제 세액 계산, 추천 대상 판정 및 Oracle 저장은 담당 서버의 책임이다. 전체 통합 검증 여부를 구현 완료와 혼동하지 않는다.

## 2. 시스템 경계와 책임

```text
FAQ: React → Spring Boot(JWT) → FastAPI /faq* → 규칙 매칭/LLM ID 분류 → tax_terms.json의 고정 답변
가이드: PREDICTION → Spring Boot 추천 판정·기존 저장 확인 → FastAPI /guide/generate
        → tax_guides.json(facts) → Gemma 설명 또는 fallback → Spring Boot RECOMMENDATION 저장 → React
```

- React는 Spring Boot만 호출하며 FastAPI·Ollama에 직접 접근하지 않는다.
- FastAPI는 `X-Chatbot-Token`으로 내부 요청을 인증하고 Oracle·회원 개인정보를 직접 조회하지 않는다.
- Spring Boot는 회원별 계산 결과 접근 권한, 추천 코드 선정, 결과의 재사용·DB 저장을 맡는다.
- 계산 서버(calc)는 LLM 없이 세액을 계산한다. FAQ 챗봇은 대화 이력을 Oracle에 저장하지 않는다.
- 외부 FAQ API는 `/api/faqs`, `/api/faq-categories`, `/api/faqs/{faqId}`, `/api/faq-matches`; 가이드 API는 `POST/GET /api/predictions/{predictionId}/recommendations`이다.

## 3. FAQ 데이터 설계 원칙

FAQ 원본은 **`app/data/tax_terms.json`** 파일에서 관리한다. 현재 구현은 DB나 모델 가중치에서 답변을 가져오지 않는다.

실제 JSON에서 사용하는 주요 필드:

- `id`: 안정적인 FAQ 식별자
- `category`: 메뉴 분류
- `term`: 버튼에 표시할 용어
- `hint`: 해당 용어만 구분할 수 있게 작성한 핵심 정의
- `aliases`: 해당 용어의 동의어·표기 변형 중심(다른 FAQ 개념을 동의어처럼 추가하지 않음). **예외로 의도적으로 통합한 TAX_017은 환급액·추가납부액을 포함**
- `answer`: 사람이 작성·검증한 고정 답변
- `source`: 검증 가능한 공식 출처 및 관련 조항

### 가이드 데이터 설계 원칙

- `app/data/tax_guides.json`의 코드 3종: `HOUSING_SAVINGS`, `PENSION_SAVINGS`, `IRP`.
- `facts.meaning/benefit/tax_benefit/tax_effect/next_step`는 Gemma가 설명을 작성할 때의 **유일한 사실 근거**다.
- `fallback_content`는 현재 `guide_service.py`가 마침표 기준으로 나누므로 **정확히 세 문장**을 유지한다. 순서대로 `description`, `taxBenefit`, `nextStep`에 대응한다.
- 소득공제(`HOUSING_SAVINGS`)와 세액공제(`PENSION_SAVINGS`, `IRP`)를 구분하고, IRP의 개인 추가 납입금과 이전 퇴직급여를 혼동하지 않는다.
- 모델 프롬프트를 불필요하게 늘리기보다 `facts`의 정확성과 이해하기 쉬운 문장을 우선 개선한다.

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

### 맞춤 절세 가이드 생성

1. 추천 대상은 Spring Boot가 계산 결과를 바탕으로 판정해 코드 목록으로 전달한다.
2. `guide_repository.py`에서 해당 상품의 `facts`를 확인한다.
3. Gemma 출력은 `description`, `taxBenefit`, `nextStep`만 허용하고 공제 유형·숫자·길이 등을 검증한다.
4. 호출 실패 또는 출력 검증 실패 시 `fallback_content`를 사용한다.
5. Spring Boot가 자체 권한 검사 후 `RECOMMENDATION`에 저장하며, 기존 결과는 재사용한다.
6. `fallback` 발생 여부와 실제 Ollama 호출·통합 검증 여부는 구분해 보고한다.

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
│   ├── api/
│   │   ├── deps.py
│   │   ├── faq.py
│   │   ├── guide.py
│   │   └── health.py
│   ├── data/
│   │   ├── tax_terms.json
│   │   └── tax_guides.json
│   ├── repositories/
│   │   ├── faq_repository.py
│   │   └── guide_repository.py
│   ├── services/
│   │   ├── classifier.py
│   │   ├── faq_service.py
│   │   ├── guide_service.py
│   │   └── privacy_filter.py
│   └── main.py
├── tests/
├── README.md
├── AGENTS.md
├── requirements.txt
├── requirements-dev.txt
└── pyproject.toml
```

빈 디렉터리를 일괄 생성하지 않는다. API 라우터는 검증과 응답을, 서비스는 흐름을, 저장소는 FAQ 조회를, LLM 모듈은 모델 호출을 담당한다. Pydantic 모델을 사용하고 snake_case/PascalCase 명명 규칙 및 Ruff 설정을 따른다. 주석과 사용자 안내 문구는 한글로 작성한다.

## 7. API·환경변수·보안

현재 구현된 FastAPI 내부 API (FAQ와 가이드 모두 `X-Chatbot-Token` 필요):

| 방식 | 경로 | 설명 |
|---|---|---|
| GET | `/health` | 상태 확인, 인증 불필요 |
| GET | `/faq` | FAQ 목록·선택적 `category` 필터 |
| GET | `/faq/categories` | 카테고리 |
| GET | `/faq/{faq_id}` | FAQ 상세 |
| POST | `/faq/ask` | 1~100자 질문 분류, 반환 키는 `faqId` |
| POST | `/guide/generate` | 코드 1~3개에 대한 설명 생성, `items` 반환 |

Spring Boot 외부 FAQ 경로는 `/api/faqs`, `/api/faq-categories`, `/api/faqs/{faqId}`, `/api/faq-matches`이며, 가이드는 `POST/GET /api/predictions/{predictionId}/recommendations`이다. 외부 성공·오류 응답은 Spring의 `{success, data, meta, error}` 공통 봉투다.

현재 `.env.example`과 로컬 `.env`에 필요한 변수 이름은 `CHATBOT_HOST`, `CHATBOT_PORT`, `CHATBOT_API_TOKEN`, `OLLAMA_BASE_URL`, `OLLAMA_MODEL`이다. 모델 태그는 `gemma3:4b`를 사용한다. `main.py`가 `load_dotenv()`를 수행하며 기동 시 토큰 유무를 확인한다. 내부 토큰은 `app/api/deps.py`에서 `X-Chatbot-Token` 헤더와 비교한다.

- 비밀정보·토큰·`.env`·`.venv`·캐시·모델 가중치를 Git에 올리지 않는다.
- FAQ 경로 인증 실패는 401, 유효성 오류는 FastAPI 422(외부 Spring은 400 `VALIDATION`), Ollama 호출 오류는 503을 반환한다. Spring은 챗봇 장애 시 503 `CHATBOT_UNAVAILABLE` + `errorId`를 반환한다.
- 자유 질문 원문, 개인정보, 재무정보를 불필요하게 로그에 남기지 않는다. 현재 분류기의 모델 원본 응답 `print()`는 추후 운영용 로깅 정책에 맞춰 제거 또는 제한해야 한다.
- 내부 FastAPI와 Ollama를 외부 인터넷에 직접 노출하지 않는다. 상세 스택 트레이스는 응답에 포함하지 않는다.

## 8. 작업 방식 및 테스트

큰 변경은 **기존 코드와 팀 API 계약을 먼저 분석하고 변경 계획을 보고**한다. 사용자 승인 없이 코드 수정, 새 파일 생성, 패키지 추가, API 계약 변경을 진행하지 않는다.

작업 순서:

1. 요구사항과 현재 구현 상태 확인
2. FAQ/가이드 데이터·응답 범위·API 책임 설계
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
- **FAQ 최종 답변이 반드시 저장된 `answer`와 일치하는지** 확인
- 가이드: 정상 Gemma 응답·형식/내용 검증 실패·fallback·지원하지 않는 코드·중복 코드 확인
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

- FAQ에 없는 세법 답변을 LLM이 직접 생성하여 사용자에게 제공하지 않는다. 단, 가이드의 허용된 `facts` 기반 설명 생성은 별도 기능이다.
- 자유 질문을 무조건 기존 FAQ 중 하나에 매칭하지 않는다.
- Calc 서버의 세액 계산이나 Spring Boot의 회원·대화 기록 관리를 중복 구현하지 않는다.
- 기존 정상 기능을 임의로 삭제하거나 프로젝트 전체를 재작성하지 않는다.
- 실제 개인정보·인증 토큰을 테스트 데이터나 Git 이력에 넣지 않는다.
- 오류를 숨기기 위해 pytest·Ruff 규칙을 임의로 끄지 않는다.

## 11. 참고 문서

- `README.md`: 실행 방법 및 현재 구현 범위
- `pyproject.toml`: Ruff·pytest 설정
- `.env.example`: 환경변수 항목
- 기능명세서 및 팀 공식 API 명세서(현행 버전 팀 문서로 확인), ERD v4.6
- `../backend/AGENTS.md`, `../calc/AGENTS.md`: 관련 서버 개발 규칙

참고 문서와 실제 코드가 충돌하면 차이를 보고하고, API 계약 변경 시 팀 공식 API 명세서를 함께 최신화한다. 구현되지 않은 대화 저장·신규 분류 정확도 기능은 완료로 표시하지 않는다.
