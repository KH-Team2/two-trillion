# 희망환급 (Hope Refund) — FAQ Chatbot

희망환급의 **Python FastAPI 기반 연말정산·세법 용어 FAQ 챗봇 서버**입니다.

사용자는 카테고리·질문 **버튼**을 눌러 FAQ를 조회하거나, **자유 질문**을 입력할 수 있습니다. 자유 질문은 로컬 **Gemma 3**가 등록된 FAQ 항목으로 분류하고, 실제 답변은 사람이 작성·검증한 **고정 FAQ 데이터**에서 조회합니다. 모델이 세법 답변을 직접 생성하지 않습니다.

> **현재 상태:** FAQ 목록·카테고리·상세·자유 질문 분류, Spring Boot 연동, `X-Chatbot-Token` 내부 인증 코드 구현.

## 1. 전체 흐름

```text
React (FAQ 버튼 / 자유 질문)
  └─ Spring Boot :8080 (JWT 인증 · 공통 API 응답)
       └─ X-Chatbot-Token → FastAPI :8001
            ├─ FAQ 목록·카테고리·상세 → tax_terms.json
            └─ 자유 질문 → 단순 용어 우선 매칭 / Ollama :11434 (Gemma 3) → FAQ ID 검증 → tax_terms.json
       ← FAQ 데이터 / 매칭 결과
  ← 고정 FAQ 답변
```

## 2. 구현된 파일

```text
chatbot/
├── app/
│   ├── main.py                 # .env 읽기 · 기동 시 토큰 필수 검증
│   ├── api/
│   │   ├── health.py          # /health (인증 예외)
│   │   ├── deps.py            # X-Chatbot-Token 검사
│   │   └── faq.py             # FAQ 목록·카테고리·상세·분류 API
│   ├── data/tax_terms.json    # 고정 FAQ 데이터
│   └── services/classifier.py # 단순 용어 매칭 및 Ollama Gemma 3 분류
├── tests/                      # 테스트 · 벤치마크 (일부 갱신 필요)
├── .env.example               # 환경변수 예시 (.env는 Git 제외)
├── requirements.txt
├── requirements-dev.txt
├── pyproject.toml
├── README.md
└── AGENTS.md
```


## 3. 설치와 실행 (Windows PowerShell)

사전 준비: Python 3.11, Ollama, 설치된 `gemma3:4b` 모델. 프로젝트의 `chatbot/`에서 다음을 실행합니다.

```powershell
py -3.11 -m venv .venv
.\.venv\Scripts\Activate.ps1
python -m pip install -r requirements-dev.txt
```

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

상태 확인: <http://127.0.0.1:8001/health> · OpenAPI: <http://127.0.0.1:8001/docs> · 종료: `Ctrl+C`. `.env`가 바뀌면 서버를 완전히 종료 후 재실행합니다. `CHATBOT_API_TOKEN`이 설정되지 않으면 서버가 기동되지 않습니다.

## 4. 외부(Spring Boot) 및 내부(FastAPI) API

| Spring Boot 외부 API (JWT 필수) | FastAPI 내부 API (`X-Chatbot-Token` 필수) | 기능 |
|---|---|---|
| `GET /api/faqs` | `GET /faq` | FAQ 목록 (선택적 `category` 필터) |
| `GET /api/faq-categories` | `GET /faq/categories` | 카테고리 목록 |
| `GET /api/faqs/{faqId}` | `GET /faq/{faq_id}` | FAQ 상세 |
| `POST /api/faq-matches` | `POST /faq/ask` | 자유 질문 FAQ 분류 (`question`: 1~100자) |

FastAPI 내부 응답에는 Spring 공통 봉투를 씌우지 않습니다. Spring Boot가 외부 요청을 `{success, data, meta, error}`로 감싸서 반환합니다.

분류 성공시 FastAPI의 `POST /faq/ask` 응답 예시 (ID는 설명용):

```json
{
  "matched": true,
  "faqId": "TAX_001",
  "faq": { "id": "TAX_001", "term": "총급여액", "answer": "등록된 답변 내용" }
}
```

미매칭 시 `{"matched":false,"faqId":null,"faq":null}`. Ollama는 `classifier.py` 내부에서 `faq_id` 키를 사용해 응답하지만, **사용자에게 전달되는 API의 JSON 필드명은 `faqId`**입니다. FAQ 목록은 상세 답변이 아니라 `id`, `category`, `term`, `aliases`만 반환합니다.

## 5. 인증·오류·보안

- 외부 API: Spring Security JWT 인증 (`Authorization: Bearer <accessToken>`).
- 내부 API: `X-Chatbot-Token`; 누락·오류 시 FastAPI에서 401. `/health`는 내부 토큰 예외.
- 질문: 최대 100자. 잘못된 입력은 Spring에서 400 `VALIDATION`, FastAPI Pydantic 유효성 검사는 422.
- FAQ 없음: 외부 404 `NOT_FOUND`. 챗봇 연결 실패 또는 Gemma 분류 장애: 외부 503 `CHATBOT_UNAVAILABLE` 및 `errorId` (Spring 공통 처리기).
- 내부 URL·토큰·개인정보·금액·질문 원문을 불필요하게 기록하지 않습니다. 오류 상세 스택은 사용자에게 노출하지 않습니다.
- `chatbot/.env`, `backend/.env`, `.venv`, `__pycache__`는 버전 관리 대상이 아닙니다.

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
