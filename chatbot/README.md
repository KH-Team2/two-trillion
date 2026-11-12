# 희망환급 (Hope Refund) — Chatbot

희망환급 프로젝트의 **Python FastAPI 기반 AI 챗봇 서버**입니다.

연말정산 및 청년 세제 혜택에 관한 질문에 답변하고, RAG(Retrieval-Augmented Generation)를 이용해 관련 참고 자료를 검색하여 AI 답변을 생성하도록 설계합니다.

> **현재 상태:** FastAPI 공통 초기 프로젝트 구성 완료 / Gemma3·RAG·Spring Boot 연동 개발 예정

## 1. 개발 환경

| 구분 | 기술 |
|---|---|
| Language | Python 3.11.9 |
| Framework | FastAPI |
| ASGI Server | Uvicorn |
| LLM | LangChain ChatOllama + Gemma3 |
| PDF Processing | PyMuPDF |
| RAG | PDF 전처리·청킹 구현 / Elasticsearch 검색·답변 연동 예정 |
| Dependency Management | pip, requirements.txt |
| Testing | pytest |
| Code Quality | Ruff |
| IDE | VS Code 권장 |

세부 개발 규칙, RAG 처리 방식 및 서버 간 역할 구분은 [`AGENTS.md`](AGENTS.md)를 참고합니다.

## 2. 프로젝트 구성

희망환급은 다음과 같은 구조로 개발합니다.

```text
two-trillion/
├── frontend/        # React + Vite
├── backend/         # Spring Boot + MyBatis
├── calc/            # Python 계산 서버
└── chatbot/         # Python AI 챗봇 서버
```

현재 Chatbot 초기 프로젝트의 주요 구조는 다음과 같습니다.

```text
chatbot/
├── .env.example
├── README.md
├── AGENTS.md
├── requirements.txt
├── requirements-dev.txt
chatbot/
├── .env.example
├── README.md
├── AGENTS.md
├── requirements.txt
├── requirements-dev.txt
├── requirements-preprocess.txt
├── pyproject.toml
│
├── app/
│   ├── main.py
│   ├── api/                 # Health·상담·추천 내부 API
│   ├── core/                # 환경설정·인증·오류 처리
│   ├── llm/                 # Ollama·Gemma3 연동
│   ├── rag/                 # PDF 추출·전처리·청킹
│   ├── schemas/             # 요청·응답 DTO
│   └── services/            # 상담·추천·생성 서비스
│
├── scripts/
│   └── preprocess_documents.py
│
├── data/
│   └── processed/           # PDF 전처리 결과
│
├── docs/                    # 설계·API 계약·개발 문서
└── tests/                   # 단위·API·RAG 전처리 테스트
```

현재 내부 상담·추천 API, 요청·응답 스키마, Ollama·Gemma3 연결, PDF 전처리·청킹 모듈까지 구현되어 있습니다. 향후 Elasticsearch 기반 문서 검색과 검색 결과를 활용한 RAG 답변 생성을 연동할 예정입니다.

최종 개발 목표 구조는 `AGENTS.md`를 참고합니다.

## 3. 개발환경 준비

### 3.1 사전 준비

- Python 3.11.9
- Git
- VS Code 또는 Python 개발이 가능한 IDE

AI 답변 생성 기능을 사용하려면 로컬 환경에 Ollama와 gemma3:4b 모델을 준비해야 합니다.

### 3.2 프로젝트 가져오기

GitHub에서 공통 초기 프로젝트 브랜치를 Clone합니다.

```bash
git clone -b feature/project-base https://github.com/KH-Team2/two-trillion.git
cd two-trillion/chatbot
```

이미 저장소를 Clone한 경우에는 현재 브랜치와 작업 상태를 먼저 확인합니다.

### 3.3 Python 가상환경 생성

Windows CMD에서 실행합니다.

```cmd
py -3.11 -m venv .venv
```

### 3.4 가상환경 활성화

```cmd
.venv\Scripts\activate
```

정상적으로 활성화되면 CMD 앞에 `(.venv)`가 표시됩니다.

가상환경은 개발자별로 생성하며 GitHub에는 업로드하지 않습니다.

### 3.5 패키지 설치

```cmd
python -m pip install -r requirements-dev.txt
```

- `requirements.txt`: 서버 실행에 필요한 패키지
- `requirements-dev.txt`: 개발 및 테스트 도구를 포함한 패키지

Ollama·Gemma3 연동에 필요한 의존성은 requirements.txt에서 관리합니다. 
PDF 전처리용 의존성은 requirements-preprocess.txt에서 별도로 관리합니다. 
초기 RAG 검색은 Elasticsearch BM25를 사용할 예정이며, 임베딩 및 벡터·하이브리드 검색은 검색 품질 검증 후 별도로 결정합니다.

## 4. 환경변수 설정

환경변수 예시는 다음 파일에 있습니다.

```text
chatbot/.env.example
```

| 환경변수 | 설명 | 초기 기본값 |
|---|---|---|
| `CHATBOT_HOST` | FastAPI 실행 주소 | `127.0.0.1` |
| `CHATBOT_PORT` | FastAPI 실행 포트 | `8001` |
| `CHATBOT_API_TOKEN` | Spring Boot와 챗봇 서버 간 내부 인증 토큰 | 없음 |
| `OLLAMA_BASE_URL` | Ollama 서버 주소 | `http://localhost:11434` |
| `OLLAMA_MODEL` | Ollama에서 사용할 LLM | `gemma3` |

Ollama 연결 및 내부 API 인증 설정은 OS 환경변수를 통해 읽습니다. 
CHATBOT_API_TOKEN이 설정되지 않은 경우 내부 상담·추천 POST 요청은 503 AUTH_NOT_CONFIGURED로 차단되며 /health는 유지됩니다. 
.env.example은 설정 항목 안내용이며 파일을 복사하는 것만으로 자동 로딩되지는 않습니다.

**주의:** 비밀번호 및 인증 토큰은 GitHub에 업로드하지 않습니다. 실제 값을 설정할 경우 `.gitignore`에서 제외되는 로컬 환경변수 파일 또는 실행 환경 설정을 사용합니다.

## 5. FastAPI 서버 실행

가상환경이 활성화된 상태에서 실행합니다.

```cmd
python -m uvicorn app.main:app --reload --host 127.0.0.1 --port 8001
```

정상 실행 후 브라우저에서 다음 주소를 확인합니다.

**Health API**

```text
http://127.0.0.1:8001/health
```

정상 응답:

```json
{
  "status": "OK"
}
```

**FastAPI 자동 API 문서**

```text
http://127.0.0.1:8001/docs
```

현재 Health API는 FastAPI 서버의 기본 HTTP 응답을 확인합니다. Ollama 모델이나 RAG 검색 시스템의 정상 상태까지 검사하는 기능은 아닙니다.

서버 종료는 `Ctrl + C`를 사용합니다.

## 6. 테스트 및 코드 검사

모든 명령은 `chatbot` 디렉터리에서 실행합니다.

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

### Ruff 코드 자동 정리

```cmd
python -m ruff format .
```

현재 초기 프로젝트에서 다음 사항을 확인했습니다.

- Python 3.11.9 환경 실행
- FastAPI 서버 정상 실행
- `/health` API 응답 확인
- pytest Health 테스트 1개 통과
- Ruff 코드 검사 통과

Ruff 형식 검사 최종 통과 여부는 자동 정리 후 다시 확인해야 합니다.

## 7. 전체 시스템에서의 역할

```text
React Frontend
      │
      │ 사용자 질문
      ▼
Spring Boot Backend
      │
      ├── 회원 인증 및 권한 확인
      ├── 대화방 소유권 확인
      ├── Oracle DB 대화 기록 조회·저장
      │
      │ 내부 HTTP 요청
      ▼
Python FastAPI Chatbot
      │
      ├── 질문 처리
      ├── RAG 참고 자료 검색
      └── Ollama / Gemma3 답변 생성
```

Frontend는 Spring Boot API를 호출합니다.

Spring Boot는 회원 인증, 대화 기록 관리 및 DB 저장을 담당합니다.

Python Chatbot은 전달받은 질문과 필요한 문맥을 기반으로 참고 자료를 검색하고 AI 답변을 생성합니다.

Chatbot 내부 상담·추천 API와 인증은 구현되어 있으나, 
Spring Boot와의 실제 서버 간 HTTP 연동 및 Elasticsearch 기반 RAG 검색 연동은 아직 완료되지 않았습니다.

## 8. 현재 구현 상태

### 구성 완료

- Python 3.11.9 기반 FastAPI 애플리케이션
- `/health` API
- LangChain ChatOllama 기반 `gemma3:4b` 연결
- 내부 상담 API `POST /internal/chat-responses`
- 내부 추천 API `POST /internal/recommendations`
- `X-Chatbot-Token` 기반 내부 인증
- 요청·응답 DTO 및 오류 처리
- PDF 8개 오프라인 텍스트 추출 및 전처리
- 법령·안내서·개정세법 구조별 청킹
- 전처리 결과 재현성·무결성 검증
- pytest 테스트
- Ruff 코드 검사 및 포맷 설정

### 추후 구현 및 검증 예정

- 전처리 결과 수동 검수
- Elasticsearch BM25 색인 및 검색
- 검색 결과와 Gemma3를 연결한 RAG 답변 생성
- Spring Boot와 내부 HTTP 연동
- 법령 출처 및 적용 귀속연도 검증
- AI 답변 품질 및 예외 상황 검증
- 필요 시 임베딩·벡터·하이브리드 검색 검토

## 9. 관련 문서

| 문서 | 용도 |
|---|---|
| `AGENTS.md` | AI 챗봇 개발 규칙 및 아키텍처 |
| `.env.example` | 환경변수 예시 |
| `requirements.txt` | 서버 실행 의존성 |
| `requirements-dev.txt` | 개발 및 테스트 의존성 |
| `pyproject.toml` | Ruff·pytest 설정 |

기능 구현은 팀의 [희망환급 기능명세서 v1.13](https://docs.google.com/spreadsheets/d/1qW5xjOhPqrWkCn9KLUUbylKfsFJCacfOAgBiXm27v9w/edit)을 기준으로 합니다.

실제 API 주소와 요청·응답 데이터 구조는 팀 공식 API 명세서 및 Spring Boot 담당 영역과 협의하여 확정합니다.