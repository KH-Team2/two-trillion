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
- Java
- Spring Boot
- Python
- FastAPI

### Frontend
- React
- HTML
- CSS
- JavaScript

### Database
- Oracle
- Elasticsearch

### Data / AI
- Python(numpy, pandas, matplotlib/seaborn, scikit-learn 등)
- Ollama(Gemma3) 또는 GPT API

### Collaboration
- Git
- GitHub

## 🌿 Git Branch

- `main` : 최종 버전
- `develop` : 기능 통합 및 테스트
- 작업 브랜치 : 개발 된 기능 업로드
- 개인 브랜치 : 개인 기능 개발 업로드

### 개발 흐름

개인 브랜치 → 팀깃 기능 브랜치 → Pull Request → develop(테스트 및 통합) → Pull Request → main

## 📌 Git 규칙

- `main` 브랜치에 직접 Push하지 않습니다.
- 기능 개발은 별도의 개인 작업 브랜치에서 진행합니다.
- 개발된 기능은 팀깃에 frature/기능명(ex. feature/login)으로 push합니다.
- 커밋 메시지는 구현 및 수정 내용을 간단하게 확인할 수 있도록 작성합니다.
- Pull Request를 통해 코드를 병합합니다.
- main으로의 PR은 2명 이상 승인을 받은 후 Merge합니다.
- Force Push를 금지합니다.
- `.env`, API Key, DB 비밀번호 등 민감정보는 GitHub에 업로드하지 않도록 주의합니다.

## 🚀 주요 기능

- 회원 관리
- 회원 정보 분석 및 관리
- 연말정산 시뮬레이션
- 연말정산 이해 도우미(AI 절세 가이드)
- 고객센터
- 관리자 권한

## 📂 프로젝트 구조

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
 │   ├── mvnw                                 # Maven Wrapper (Unix)
 │   ├── mvnw.cmd                             # Maven Wrapper (Windows)
 │   ├── .mvn/wrapper/
 │   │   └── maven-wrapper.properties         # Wrapper 설정
 │   └── src/
 │       ├── main/
 │       │   ├── java/com/hoperefund/
 │       │   │   ├── HopeRefundBackendApplication.java # Spring Boot 진입점
 │       │   │   └── common/                  # 현재 구현된 공통 영역
 │       │   │       ├── config/
 │       │   │       │   └── CorsConfig.java  # React CORS 설정
 │       │   │       ├── controller/
 │       │   │       │   └── HealthController.java # 상태 확인 API
 │       │   │       ├── dto/
 │       │   │       │   └── ErrorResponseDTO.java # 오류 응답 형식
 │       │   │       └── exception/
 │       │   │           └── GlobalExceptionHandler.java # 공통 예외 처리
 │       │   └── resources/
 │       │       └── application.yml          # 환경변수 기반 서버·DB·내부 서비스 설정
 │       └── test/java/com/hoperefund/
 │           ├── HopeRefundBackendApplicationTests.java
 │           └── common/
 │               ├── config/CorsConfigTest.java
 │               ├── controller/HealthControllerTest.java
 │               └── exception/
 │                   ├── GlobalExceptionHandlerTest.java
 │                   └── TestErrorController.java
 │
 ├── calc/                                    # Python FastAPI · 세액 계산·혜택·카드 처리 서버
 │   ├── .env.example                         # DB·내부 토큰·서버 실행 변수 예시
 │   ├── AGENTS.md                            # 계산 서버 개발 규칙 및 목표 구조
 │   ├── README.md                            # 계산 서버 설치·실행 안내
 │   ├── requirements.txt                     # 서버 의존성
 │   ├── requirements-dev.txt                 # 개발·검증 의존성
 │   ├── pyproject.toml                       # Ruff·pytest 설정
 │   ├── app/
 │   │   ├── __init__.py
 │   │   ├── main.py                           # FastAPI 진입점
 │   │   └── api/
 │   │       ├── __init__.py
 │   │       └── health.py                     # 서버 상태 확인 API
 │   └── tests/
 │       └── test_health.py                   # Health API 테스트
 │
 └── chatbot/                                 # Python FastAPI · Gemma3/RAG 챗봇 서버
     ├── .env.example                         # 내부 토큰·Ollama·서버 변수 예시
     ├── AGENTS.md                            # 챗봇 개발 규칙 및 목표 구조
     ├── README.md                            # 챗봇 서버 설치·실행 안내
     ├── requirements.txt                     # 서버 의존성
     ├── requirements-dev.txt                 # 개발·검증 의존성
     ├── pyproject.toml                       # Ruff·pytest 설정
     ├── app/
     │   ├── __init__.py
     │   ├── main.py                           # FastAPI 진입점
     │   └── api/
     │       ├── __init__.py
     │       └── health.py                     # 서버 상태 확인 API
     └── tests/
         └── test_health.py                   # Health API 테스트

──────────────────────────────────────────────────────────────────
[개발 예정 구조 — AGENTS.md에 명시되었지만 아직 GitHub에 없는 폴더]

backend/src/main/java/com/hoperefund/
├── member/           # MEMBER · PASSWORD_RESET
├── income/           # INCOME
├── expense/          # CARD_UPLOAD · EXPENSE · SPEND_CATEGORY
├── prediction/       # PREDICTION · PREDICTION_INPUT · PREDICTION_DETAIL
├── statistics/       # GROUP_STAT · STAT_FILE
├── chat/             # CHAT_SESSION · CHAT_MESSAGE · RECOMMENDATION
├── inquiry/          # INQUIRY
├── admin/            # DEDUCTION_TYPE · TAX_BRACKET · BENEFIT_CONDITION
└── common/           # 현재 존재, 보안/예외 처리 등의 확장 예정
# 기능별 폴더 하위 계층: controller/ service/ mapper/ dto/
# MyBatis XML: backend/src/main/resources/mapper/ (기능별)

calc/app/
├── api/              # /calculate · /benefit · deps.py
├── schemas/          # 요청·응답 모델
├── db/               # Oracle 읽기 전용 연결
├── repositories/     # DEDUCTION_TYPE · TAX_BRACKET · BENEFIT_CONDITION 조회
├── services/         # bracket.py, tax_calculator.py, tax_rules.py, card_deduction.py, benefit.py
└── card/             # 카드 업로드 파일 정리·분류
calc/database/        # DB 구축·파이프라인 스크립트

chatbot/app/
├── api/              # FastAPI 라우터
├── schemas/          # 요청·응답 모델
├── services/         # 질문 처리·답변 생성
├── rag/              # 문서 로딩·분할·임베딩·검색
├── llm/              # Ollama·Gemma3 연동
└── core/             # 환경설정·내부 인증
chatbot/data/         # RAG 문서·전처리 자료
chatbot/docs/         # 개발 이력·결정·작업 문서

## 🖥 실행 방법

개발 진행 후 작성 예정
