# two-trillion

# 희망환급
> 청년 대상 연말정산 예측 및 AI 절세 전략 추천 웹서비스

## 📌 프로젝트 소개

"쉽게 알아보는 연말정산 시뮬레이터"

연말정산 쉽게 이해하기 도우미
현재 상황에 따른 연말정산 예상 결과를 제공한다.
적용되었거나 추가 적용 가능한 혜택 정보를 제공한다.

절세 시뮬레이션 제공
상황 변동 시의 연말정산 예상 결과와 결과별 대시보드를 제공한다.
비슷한 그룹 내의 결과 통계를 제공한다.

## 📅 프로젝트 기간

2026.10.12 ~ 2026.12.09

## 👥 팀

**팀명: 희망연봉2조**

| 역할 | 담당 |
| --- | --- |
| 조장 | 이고은 |
| 형상관리 | 김동석 |
| DB 관리 | 이우진 |
| 이슈 관리 | 이윤경 |
| 문서 관리 | 김홍은 |

## 🛠 기술 스택

### Backend
- Java 17
- Spring Boot 4.1.0
- MyBatis
- Python 3.11
- FastAPI

### Frontend
- React
- JavaScript (JSX)
- HTML5
- CSS3

### Database
- Oracle Database 21c

### Data / AI
- Python
- Ollama (Gemma 3 4B)
- LangChain
- Elasticsearch

### Collaboration
- Git
- GitHub

## 🌿 Git Branch

- `main` : 최종 버전
- `develop` : 기능 통합 및 테스트
- 작업 브랜치 : 개발 된 기능 업로드
- 개인 브랜치 : 개인 기능 개발 업로드

### 개발 흐름

개인 브랜치 → 팀깃 기능 브랜치 → Pull Request(2인 승인) → develop(테스트 및 통합) → Pull Request(조장, 형상 승인) → main

## 📌 Git 규칙

- 기능 개발은 각 담당자의 개인 브랜치에서 진행한다.
- 개발이 완료된 기능은 해당 `feature/기능명` 브랜치에 반영한다.
- 기능명세서 상 1행 완료 시 `develop` 브랜치로 PR하여 통합한다.
- 기능명세서 상 2개 이상의 행이어야만 실행 오류가 없는 경우 합쳐서 `develop` 브랜치로 PR하여 통합한다. (이유를 PR 본문에 작성)
- `develop`은 기능 통합 및 최종 검증을 위한 브랜치로 사용한다.
- `main`은 최종 안정 버전만 반영하는 브랜치로 사용한다.
- 공통 브랜치나 다른 담당자의 개인·기능 브랜치에 직접 커밋하지 않는다.
- 커밋은 의미 있는 기능 구현·수정 단위로 한다.
- 커밋 메시지는 종류: `무엇을 했는지` 형식으로 작성하고 설명은 한글로 작성한다.
- `커밋 종류`는 feat, fix, chore, docs, test, refactor, ci 등을 사용한다.
- 사용자 확인 없이 git push, 브랜치 삭제, 강제 푸시(git push -f)를 수행하지 않는다.

## 🚀 주요 기능

- 회원 관리
- 회원 정보 분석 및 관리
- 연말정산 시뮬레이션
- 연말정산 이해 도우미(AI 절세 가이드)
- 고객센터
- 관리자 권한

## 📂 프로젝트 구조

```text
 two-trillion/
 ├── .gitignore                               # 4개 프로젝트 공통 Git 제외 설정
 ├── README.md                                # 저장소 최상위 안내
 │
 ├── frontend/                                # React + Vite · 화면/UI · 체험 기능
 │   ├── .env.example                         # 약관·개인정보·금융정보 동의 버전 변수 예시
 │   ├── .gitignore                           # 프론트엔드 개별 Git 제외 설정
 │   ├── .prettierignore                      # Prettier 검사 제외 파일
 │   ├── .prettierrc.json                     # Prettier 코드 형식 설정
 │   ├── .openai/
 │   │   └── hosting.json                     # 기존 배포 설정
 │   ├── AGENTS.md                            # 프론트 개발 규칙
 │   ├── README.md                            # 프론트 설치·실행·상태 안내
 │   ├── index.html                           # Vite HTML 진입점
 │   ├── jsconfig.json                        # JS 프로젝트 설정
 │   ├── package.json                         # npm 패키지·실행 명령
 │   ├── package-lock.json                    # npm 의존성 잠금 파일
 │   ├── postcss.config.js                    # PostCSS 설정
 │   ├── vite.config.js                       # Vite 설정
 │   ├── public/
 │   │   └── favicon.svg                      # 파비콘
 │   ├── scripts/
 │   │   └── refresh-group-stat.mjs           # 그룹 통계 자료 갱신 스크립트
 │   ├── docs/                                # 기존 구현·검증·추출 관련 기록
 │   │   ├── API_INTEGRATION.md               # API 연동 참고
 │   │   ├── EXPORT_VERIFICATION.md           # 원본 추출 검증
 │   │   ├── FEEDBACK_REPORT.md               # 수정 사항 및 보류 항목
 │   │   ├── PROJECT_STRUCTURE.md             # 기존 프로젝트 구조 설명
 │   │   ├── SOURCE_MAP.md                    # 원본 소스 구성 안내
 │   │   ├── VERIFICATION.md                  # 검증 기록
 │   │   ├── export-provenance.json           # 추출 출처 메타데이터
 │   │   ├── source-provenance.json           # 원본 출처 메타데이터
 │   │   └── insurance-input.jpg              # 보험료 입력 관련 참고 이미지
 │   ├── src/
 │   │   ├── main.jsx                         # React 진입점
 │   │   ├── App.jsx                          # 앱 최상위 컴포넌트·상태 연결
 │   │   ├── api/                             # 브라우저 체험 서비스·API 연동 경계
 │   │   │   ├── account-service.js           # 회원 관련 체험 서비스
 │   │   │   ├── calculation-contract.js      # 계산 응답 계약 관련 로직
 │   │   │   ├── erd-contract.js              # ERD 관련 데이터 계약
 │   │   │   ├── expense-service.js           # 지출·업로드 관련 서비스
 │   │   │   ├── prototype-records.js         # 체험용 기록 관리
 │   │   │   └── services.js                  # 체험 데이터·서비스
 │   │   ├── components/
 │   │   │   ├── common/                      # 공통 표현 컴포넌트
 │   │   │   │   ├── DeductionDetails.jsx     # 공제·감면 상세 표시
 │   │   │   │   └── site-primitives.jsx      # 공통 사이트 UI 요소
 │   │   │   ├── expense/
 │   │   │   │   └── ExpenseUpload.jsx        # 카드·홈택스 CSV/XLSX 업로드 UI
 │   │   │   ├── layout/
 │   │   │   │   ├── PublicHeader.jsx         # 비로그인 화면 헤더
 │   │   │   │   ├── Sidebar.jsx              # 사이드바
 │   │   │   │   └── Topbar.jsx               # 상단바
 │   │   │   └── ui/                          # 재사용 UI 컴포넌트 다수
 │   │   │       # button.jsx, input.jsx, dialog.jsx, chart.jsx, table.jsx 등
 │   │   ├── pages/                           # 실제 화면 구성
 │   │   │   ├── admin/
 │   │   │   │   ├── AdminPage.jsx            # 관리자 화면
 │   │   │   │   └── AdminTools.jsx           # 관리자 기능 UI
 │   │   │   ├── auth/
 │   │   │   │   ├── AccountForms.jsx         # 회원 관련 입력 폼
 │   │   │   │   └── AuthPage.jsx             # 로그인·가입 등 인증 화면
 │   │   │   ├── chatbot/
 │   │   │   │   ├── ChatPanel.jsx            # AI 상담 패널
 │   │   │   │   └── ChatbotPage.jsx          # AI 상담 페이지
 │   │   │   ├── dashboard/
 │   │   │   │   ├── Dashboard.jsx            # 대시보드 UI
 │   │   │   │   └── DashboardPage.jsx        # 대시보드 페이지
 │   │   │   ├── home/
 │   │   │   │   └── HomePage.jsx             # 시작 화면
 │   │   │   ├── profile/
 │   │   │   │   ├── ProfileDetails.jsx       # 마이페이지 상세
 │   │   │   │   └── ProfilePage.jsx          # 마이페이지
 │   │   │   ├── simulation/
 │   │   │   │   └── SimulationPage.jsx       # 조건 변경 시뮬레이션
 │   │   │   ├── statistics/
 │   │   │   │   ├── GroupStats.jsx           # 그룹 통계 시각화
 │   │   │   │   └── StatisticsPage.jsx       # 그룹 통계 페이지
 │   │   │   ├── support/
 │   │   │   │   └── SupportPage.jsx          # 고객센터
 │   │   │   └── tax/
 │   │   │       ├── BenefitsPage.jsx         # 청년 혜택 화면
 │   │   │       ├── TaxInfoForm.jsx          # 연말정산 입력 폼
 │   │   │       └── TaxInputPage.jsx         # 연말정산 입력 페이지
 │   │   ├── config/
 │   │   │   └── consent-versions.js          # 동의 버전 설정
 │   │   ├── data/statistics/                 # 2024년 그룹 통계 정적 자료
 │   │   │   ├── gender-age-2024.csv
 │   │   │   ├── gender-age-2024.xlsx
 │   │   │   ├── group-stat-2024.json
 │   │   │   ├── group-stat-metadata.json
 │   │   │   ├── salary-2024.csv
 │   │   │   ├── salary-2024.xlsx
 │   │   │   └── stat-file.json
 │   │   ├── hooks/
 │   │   │   └── use-mobile.js                # 모바일 화면 대응 훅
 │   │   ├── routes/
 │   │   │   └── useHashRouting.js            # 기존 해시 라우팅
 │   │   ├── styles/
 │   │   │   └── globals.css                  # 전역 스타일
 │   │   └── utils/
 │   │       ├── benefit-model.js             # 혜택 관련 보조 로직
 │   │       ├── cn.js                        # 클래스명 처리 유틸
 │   │       ├── dashboard-model.js           # 대시보드 모델
 │   │       ├── group-model.js               # 그룹 통계 모델
 │   │       └── tax-year.js                  # 귀속 연도 유틸
 │   └── tests/
 │       ├── feedback-contract.mjs            # 응답 계약 관련 테스트
 │       ├── prototype-flows.mjs              # 체험 기능 흐름 테스트
 │       └── render-pages.mjs                 # 화면 렌더링 테스트
 │
 ├── backend/                                 # Spring Boot · MyBatis · Oracle 연동
 │   ├── .gitattributes                       # 파일 속성/줄바꿈 설정
 │   ├── .gitignore                           # 백엔드 개별 제외 규칙
 │   ├── AGENTS.md                            # 백엔드 개발 규칙
 │   ├── README.md                            # 실행·DB 환경변수·검증 안내
 │   ├── pom.xml                              # Maven 의존성·빌드 설정
 │   ├── .mvn/
 │   ├── .env.example                         # 환경변수 이름 안내 (.env는 커밋하지 않음)
 │   ├── docs/
 │   │    ├── api-rules.md                    # API 공통 규칙 (주소 모양 · 응답 봉투 · 오류 응답)
 │   │    ├── history.md                      # 작업 이력
 │   │    └── tasks.md                        # 남은 작업 (세액 계산·진단 파트)
 │   │   
 │   └── src/
 │       ├── main/
 │       │   ├── java/com/hoperefund/
 │       │   │   ├── HopeRefundBackendApplication.java
 │       │   │   │
 │       │   │   ├── common/                    # 공통 설정·응답·예외 처리
 │       │   │   │   ├── config/
 │       │   │   │   │    └── CorsConfig.java
 │       │   │   │   ├── controller/
 │       │   │   │   │    └── HealthController.java
 │       │   │   │   ├── dto/
 │       │   │   │   │    ├── ApiResponse.java  # 공통 응답 봉투 {success, data, meta, error}
 │       │   │   │   │    └── PageMeta.java     # 목록 응답의 페이지 정보
 │       │   │   │   └── exception/
 │       │   │   │        ├── GlobalExceptionHandler.java   # 모든 오류를 공통 봉투의 error로
 │       │   │   │        └── *Exception.java               # BadRequest · NotFound · Conflict · NoCriteria · CalcRejected · CalcUnavailable
 │       │   │   │
 │       │   │   ├── income/                    # 소득 정보 입력·조회 (GET·PUT /api/incomes/{taxYear})
 │       │   │   │   ├── controller/
 │       │   │   │   ├── service/
 │       │   │   │   ├── mapper/
 │       │   │   │   └── dto/
 │       │   │   │
 │       │   │   └── prediction/                # 예상 세액 계산·청년 혜택 진단
 │       │   │       ├── controller/            # CalculationController (POST /api/calculations/{taxYear}) · BenefitController (GET /api/benefits/{taxYear})
 │       │   │       ├── service/               # CalculationService · BenefitService · PredictionSaveService(결과 저장 트랜잭션)
 │       │   │       ├── client/                # Python 계산 서버 연동
 │       │   │       ├── mapper/                # CalcClient (계산 서버 호출) · HttpCalcClient · MockCalcClient
 │       │   │       └── dto/
 │       │   │
 │       │   └── resources/
 │       │       ├── application.yml            # Spring Boot 공통 설정
 │       │       └── mapper/                    # MyBatis SQL (기능별 폴더)
 │       │           ├── income/IncomeMapper.xml
 │       │           └── prediction/PredictionMapper.xml
 │       │
 │       └── test/java/com/hoperefund/
 │           ├── common/
 │           ├── income/
 │           └── prediction/
 │
 ├── calc/                                    # Python FastAPI · 세액 계산·혜택·카드 처리 서버
 │   ├── .env.example                         # DB·내부 토큰·서버 실행 변수 예시
 │   ├── AGENTS.md                            # 계산 서버 개발 규칙
 │   ├── README.md                            # 계산 서버 설치·실행 안내
 │   ├── requirements.txt                     # 서버 의존성
 │   ├── requirements-dev.txt                 # 개발·검증 의존성
 │   ├── pyproject.toml                       # Ruff·pytest 설정
 │   │
 │   ├── app/
 │   │   ├── __init__.py
 │   │   ├── main.py                          # FastAPI 진입점
 │   │   │
 │   │   ├── api/                             # API 엔드포인트
 │   │   │   ├── __init__.py
 │   │   │   ├── benefit.py                   # 청년 혜택 API
 │   │   │   ├── calculate.py                 # 연말정산 계산 API
 │   │   │   ├── deps.py                      # API 공통 의존성
 │   │   │   └── health.py                    # 서버 상태 확인 API
 │   │   │
 │   │   ├── db/                              # Oracle DB 연결
 │   │   │   ├── __init__.py
 │   │   │   └── oracle.py                    # Oracle 연결 처리
 │   │   │
 │   │   ├── repositories/                    # 계산 기준 데이터 조회
 │   │   │   ├── __init__.py
 │   │   │   ├── criteria.py                  # 기준 데이터 조회 인터페이스
 │   │   │   └── oracle_criteria.py           # Oracle 기준 데이터 조회
 │   │   │
 │   │   ├── schemas/                         # API 요청·응답 모델
 │   │   │   ├── __init__.py
 │   │   │   ├── benefit.py                   # 혜택 요청·응답 모델
 │   │   │   ├── calculation.py               # 계산 요청·응답 모델
 │   │   │   └── common.py                    # 공통 데이터 모델
 │   │   │
 │   │   └── services/                        # 연말정산 계산 비즈니스 로직
 │   │       ├── __init__.py
 │   │       ├── benefit.py                    # 청년 혜택 판정
 │   │       ├── bracket.py                    # 과세표준·세율 구간 처리
 │   │       ├── card_deduction.py             # 카드 사용액 소득공제 계산
 │   │       ├── errors.py                     # 계산 서버 오류 정의
 │   │       ├── models.py                     # 계산 내부 모델
 │   │       ├── tax_calculator.py             # 연말정산 세액 계산
 │   │       └── tax_rules.py                  # 세액 계산 규칙
 │   │
 │   ├── database/                            # 계산용 DB 구축·기준 데이터
 │   │   ├── 00_초기화.sql
 │   │   ├── 01_schema.sql
 │   │   ├── 02_기준데이터.sql
 │   │   ├── 03_데이터적재.sql
 │   │   ├── README.md
 │   │   └── cp949/                           # CP949 인코딩 SQL
 │   │       ├── 00_초기화.sql
 │   │       ├── 01_schema.sql
 │   │       ├── 02_기준데이터.sql
 │   │       └── 03_데이터적재.sql
 │   │
 │   └── tests/                               # 계산 서버 테스트
 │       ├── __init__.py
 │       ├── criteria_data.py                  # 테스트용 기준 데이터
 │       ├── test_api.py                       # 계산 API 테스트
 │       ├── test_benefit.py                   # 청년 혜택 테스트
 │       ├── test_bracket.py                   # 세율 구간 테스트
 │       ├── test_card_deduction.py            # 카드 공제 테스트
 │       ├── test_health.py                    # Health API 테스트
 │       ├── test_oracle_criteria.py           # Oracle 기준 데이터 조회 테스트
 │       ├── test_tax_calculator.py            # 세액 계산 테스트
 │       └── test_tax_rules.py                 # 세액 계산 규칙 테스트
 │ 
 ├── chatbot/                                 # Python FastAPI · Ollama 기반 AI 챗봇 서버
 │   ├── .env.example                         # Ollama·내부 API 인증 등 환경 변수 예시
 │   ├── AGENTS.md                            # 챗봇 서버 개발 규칙
 │   ├── README.md                            # 챗봇 서버 설치·실행 안내
 │   ├── requirements.txt                     # 서버 의존성
 │   ├── requirements-dev.txt                 # 개발·검증 의존성
 │   ├── pyproject.toml                       # Ruff·pytest 설정
 │   │
 │   ├── app/
 │   │   ├── __init__.py
 │   │   ├── main.py                          # FastAPI 진입점
 │   │   │
 │   │   ├── api/                             # 챗봇 API 엔드포인트
 │   │   │   ├── __init__.py
 │   │   │   ├── chat.py                      # 채팅 API
 │   │   │   ├── dependencies.py              # API 공통 의존성
 │   │   │   ├── health.py                    # 서버 상태 확인 API
 │   │   │   └── recommendations.py           # 추천 API
 │   │   │
 │   │   ├── core/                            # 서버 공통 설정·보안·오류 처리
 │   │   │   ├── __init__.py
 │   │   │   ├── config.py                    # 환경 설정
 │   │   │   ├── errors.py                    # 챗봇 서버 오류 정의
 │   │   │   └── security.py                  # 내부 API 인증 처리
 │   │   │
 │   │   ├── data
 │   │   │   └── raw/                         # RAG 원본 PDF
 │   │   │    
 │   │   ├── llm/                             # LLM 연동
 │   │   │   ├── __init__.py
 │   │   │   └── ollama.py                    # Ollama 호출 처리
 │   │   │
 │   │   ├── schemas/                         # API 요청·응답 모델
 │   │   │   ├── __init__.py
 │   │   │   ├── chat.py                      # 채팅 요청·응답 모델
 │   │   │   ├── common.py                    # 공통 응답 모델
 │   │   │   └── recommendation.py            # 추천 요청·응답 모델
 │   │   │
 │   │   └── services/                        # 챗봇 비즈니스 로직
 │   │       ├── __init__.py
 │   │       ├── chat_service.py               # 채팅 처리
 │   │       ├── generation.py                 # 답변 생성 처리
 │   │       └── recommendation_service.py     # 추천 처리
 │   │
 │   ├── docs/                                # 챗봇 설계·개발 문서
 │   │
 │   ├── prompts/                             # AI 개발 작업용 프롬프트
 │   │   ├── 01_최초분석요청.md
 │   │   ├── 02_첫구현요청.md
 │   │   └── 03_작업이어하기.md
 │   │
 │   └── tests/                               # 챗봇 서버 테스트
 │       ├── test_generation.py                # 답변 생성 테스트
 │       ├── test_health.py                    # Health API 테스트
 │       ├── test_internal_api.py              # 내부 API 테스트
 │       └── test_ollama.py                    # Ollama 연동 테스트
──────────────────────────────────────────────────────────────────
[개발 예정 구조 — AGENTS.md에 명시되었지만 아직 GitHub에 없는 폴더]

backend/src/main/java/com/hoperefund/
├── member/           # MEMBER · PASSWORD_RESET
├── expense/          # CARD_UPLOAD · EXPENSE · SPEND_CATEGORY
├── statistics/       # GROUP_STAT · STAT_FILE
├── chat/             # CHAT_SESSION · CHAT_MESSAGE · RECOMMENDATION
├── inquiry/          # INQUIRY
├── admin/            # DEDUCTION_TYPE · TAX_BRACKET · BENEFIT_CONDITION
└── common/           # 현재 존재, 보안/예외 처리 등의 확장 예정
# 기능별 폴더 하위 계층: controller/ service/ mapper/ dto/
# MyBatis XML: backend/src/main/resources/mapper/ (기능별)
```
## 🖥 실행 방법

개발 진행 후 작성 예정
