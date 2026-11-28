# 희망환급 (Hope Refund) — FAQ 챗봇 및 맞춤 절세 가이드

Python FastAPI + Ollama Gemma 3 4B 기반 내부 서버입니다. **FAQ는 고정 답변을 검색·반환**하며, **절세 가이드는 검증된 상품 사실을 기반으로 설명을 생성**합니다. 두 기능 모두 세금을 직접 계산하지 않습니다.

- **FAQ:** 카테고리·용어 조회, 단순 질문 규칙 기반 매칭, 자유 질문 Gemma FAQ ID 분류. 답변은 `app/data/tax_terms.json`의 `answer`만 반환합니다.
- **맞춤 절세 가이드:** Spring Boot가 선정한 상품 코드에 대해 `app/data/tax_guides.json`의 `facts`를 바탕으로 Gemma가 상품 설명을 생성합니다. 생성 또는 검증 실패 시 `fallback_content`로 대체합니다.
- **역할 분리:** 추천 조건 판정·회원 데이터 권한 검사·Oracle 저장은 Spring Boot에서, 실제 세액 계산은 `calc/`에서 처리합니다.
- **구현 범위:** FAQ 35개(TAX_001~TAX_035, TAX_017에 환급/추가 납부 설명 포함), 가이드 상품 3종(`HOUSING_SAVINGS`, `PENSION_SAVINGS`, `IRP`).

## 1. 전체 흐름

```text
[FAQ] React → Spring Boot (JWT) → FastAPI /faq*
          ├─ 버튼: ID 조회 → tax_terms.json의 고정 answer
          └─ 자유 질문: 단순 용어 매칭 또는 Gemma 3 ID 분류
                       → ID 검증 → 고정 answer

[가이드] 계산 결과(PREDICTION) → Spring Boot 추천 코드 선정
          → 저장된 RECOMMENDATION 우선 확인
          → 미저장 코드만 FastAPI /guide/generate
          → tax_guides.json의 facts → Gemma 3 설명 생성
          → 검증 실패/오류 시 fallback_content
          → Spring Boot가 RECOMMENDATION 저장·응답 → React 가이드 패널
```

FAQ 분류용 Gemma는 답변을 작성하지 않습니다. 가이드 생성용 Gemma는 제공된 상품 사실만 표현을 바꿔 설명합니다.

## 2. 구현된 파일

```text
chatbot/
├── app/
│   ├── main.py                         # .env 읽기 · 기동 시 토큰 필수 검증
│   ├── api/
│   │   ├── health.py                  # /health (내부 인증 예외)
│   │   ├── deps.py                    # X-Chatbot-Token 검사
│   │   ├── guide.py                   # 절세 가이드 HTTP 요청·응답 처리
│   │   └── faq.py                     # FAQ HTTP 요청·응답 처리
│   ├── repositories/
│   │   ├── faq_repository.py          # tax_terms.json 조회
│   │   └── guide_repository.py        # tax_guides.json 조회  
│   ├── services/
│   │   ├── faq_service.py             # FAQ 조회·자유 질문 처리 업무 흐름
│   │   ├── classifier.py              # 단순 용어 매칭 및 Gemma 3 FAQ ID 분류
│   │   ├── guide_service.py           # Ollama Gemma 3 가이드 생성
│   │   └── privacy_filter.py          # 자유 질문 개인정보 검사
│   └── data/
│       ├── tax_terms.json             # 검증된 고정 FAQ 데이터
│       └── tax_guides.json            # 검증된 Gemma 가이드 생성 자료
├── tests/                             # FAQ·health 등 자동 테스트
├── .env.example                       # 환경변수 예시 (.env는 Git 제외)
├── requirements.txt
├── requirements-dev.txt
├── pyproject.toml
├── README.md
└── AGENTS.md
```


## 3. 설치와 실행 (Windows PowerShell)

사전 준비: Python 3.11, Ollama, Gemma 3 모델. 프로젝트의 `chatbot/`에서 다음을 실행합니다.

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt
```
Ollama 공식 사이트에서 Windows용 프로그램을 설치합니다.
Ollama가 실행되지 않았다면 다른 터미널에서 다음을 실행합니다. 이미 서비스로 실행 중이라면 중복 실행하지 않습니다.

```powershell
ollama list
ollama pull gemma3:4b
ollama serve
```

`chatbot/.env.example`을 참고해 **`chatbot/.env`를 로컬에 직접 생성**합니다. 비밀값은 예시 문서나 Git에 넣지 않습니다.

```dotenv
CHATBOT_HOST=127.0.0.1
CHATBOT_PORT=8001
CHATBOT_API_TOKEN=<로컬에서 생성한 임의의 비밀값>
OLLAMA_BASE_URL=http://localhost:11434
OLLAMA_MODEL=gemma3:4b
```

`CHATBOT_API_TOKEN`은 Spring Boot의 `backend/.env`에 설정한 값과 동일해야 합니다. `.env`에 실제 비밀값을 넣은 다음 챗봇 서버를 실행합니다.

```powershell
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8001
```

상태 확인: <http://127.0.0.1:8001/health> · 종료: `Ctrl+C`. `/docs`, `/redoc`, `/openapi.json`은 `app/main.py`에서 비활성화되어 있습니다. `.env`가 바뀌면 서버를 완전히 종료 후 재실행합니다. `CHATBOT_API_TOKEN`이 설정되지 않으면 서버가 기동되지 않습니다.

## 4. 외부(Spring Boot) 및 내부(FastAPI) API

| Spring Boot 외부 API (JWT 필수) | FastAPI 내부 API (`X-Chatbot-Token` 필수) | 기능 |
|---|---|---|
| `GET /api/faqs` | `GET /faq` | FAQ 목록 (선택적 `category` 필터) |
| `GET /api/faq-categories` | `GET /faq/categories` | 카테고리 목록 |
| `GET /api/faqs/{faqId}` | `GET /faq/{faq_id}` | FAQ 상세 |
| `POST /api/faq-matches` | `POST /faq/ask` | 자유 질문 FAQ 분류 (`question`: 1~100자) |
| `POST /api/predictions/{predictionId}/recommendations` | `POST /guide/generate` | 추천 코드에 대한 설명 생성·DB 저장 (Spring이 기존 저장 결과 재사용) |
| `GET /api/predictions/{predictionId}/recommendations` | 해당 내부 호출 없음 | Spring DB에 저장된 추천 설명만 조회 |

FastAPI 내부 응답에는 Spring 공통 봉투를 씌우지 않습니다. Spring Boot가 외부 요청을 `{success, data, meta, error}`로 감싸서 반환합니다.

분류 성공시 FastAPI의 `POST /faq/ask` 응답 예시 (ID는 설명용):

```json
{
  "matched": true,
  "faqId": "TAX_001",
  "faq": { "id": "TAX_001", "term": "연말정산", "answer": "등록된 답변 내용" }
}
```

미매칭 시 `{"matched":false,"faqId":null,"faq":null}`. Ollama는 `classifier.py` 내부에서 `faq_id` 키를 사용해 응답하지만, **사용자에게 전달되는 API의 JSON 필드명은 `faqId`**입니다. FAQ 목록은 상세 답변이 아니라 `id`, `category`, `term`, `aliases`만 반환합니다.

### 절세 가이드 응답·데이터 기준

- 요청: `POST /guide/generate`, 본문 `{"codes":["HOUSING_SAVINGS","PENSION_SAVINGS"]}` (1~3개, 중복·미지원 코드는 거부).
- 응답: `{"items":[{"code":"HOUSING_SAVINGS","name":"주택청약종합저축","content":{"description":"...","taxBenefit":"...","nextStep":"..."},"source":"gemma"}]}`. `source`는 `gemma` 또는 `fallback`입니다.
- `facts`: `meaning`, `benefit`, `tax_benefit`, `tax_effect`, `next_step`으로 구성된 생성 근거입니다. `fallback_content`는 Gemma 실패/검증 실패 시 쓰며 **마침표로 구분되는 정확히 3문장**이어야 합니다.
- 생성 결과는 정해진 3개 필드만 허용하며 숫자 포함, 공제 유형 혼동, 과도한 길이 등을 검증합니다. 설명은 실제 세액 계산이나 세금 혜택 보장을 대체하지 않습니다.
- Spring의 `POST`는 미저장 설명만 생성하고 트랜잭션 밖에서 LLM을 호출합니다. 생성 후 `PREDICTION` 행 잠금·재조회로 중복 저장을 방지하도록 구현되어 있습니다. 실제 Oracle 동시성 통합 테스트는 별도입니다.

## 5. 인증·오류·보안

- 외부 API: Spring Security JWT 인증 (`Authorization: Bearer <accessToken>`).
- 내부 FAQ 및 가이드 API: `X-Chatbot-Token`; 누락·오류 시 FastAPI에서 401. `/health`는 내부 토큰 예외.
- 질문: 최대 100자. 잘못된 입력은 Spring에서 400 `VALIDATION`, FastAPI Pydantic 유효성 검사는 422.
- FAQ 없음: 외부 404 `NOT_FOUND`. 챗봇 연결 실패 또는 Gemma 분류 장애: 외부 503 `CHATBOT_UNAVAILABLE` 및 `errorId` (Spring 공통 처리기).
- 내부 URL·토큰·개인정보·금액·질문 원문을 불필요하게 기록하지 않습니다. 오류 상세 스택은 사용자에게 노출하지 않습니다.
- `chatbot/.env`, `backend/.env`, `.venv`, `__pycache__`는 버전 관리 대상이 아닙니다.
- **개인정보 차단:** `ko-pii` 및 숫자 포함 여부를 검사하여 자유 질문을 차단합니다. 차단 시 HTTP 400 `PERSONAL_INFO_DETECTED`를 반환하며, Ollama에 질문을 전달하지 않습니다. 오탐·미탐 가능성이 있습니다.

## 6. 테스트

```powershell
python -m pytest -v
python -m ruff check .
python -m ruff format --check .
```

## 7. 관련 문서

- [`AGENTS.md`](AGENTS.md): 챗봇 관련 코드/보안/테스트 규칙
- `../backend/README.md`, `../frontend/README.md`: Spring 및 React 연동 안내
- 팀 기능명세서 및 API 명세서: 외부 API 계약 확인
