# 희망환급 (Hope Refund) — Backend

희망환급은 청년 근로자의 연말정산 예상 환급액 계산과 AI 절세 상담을 지원하는 웹 서비스입니다.

본 프로젝트는 **Spring Boot 기반 백엔드 API 서버**로, React 프론트엔드의 요청 처리, Oracle Database 조회·저장, Python 계산 서버 및 AI 챗봇 서버 연동을 담당합니다.

> **현재 상태:** 소득 · 세액 계산 · 혜택 진단 · 대시보드 · 시뮬레이션 · 결과 이력 · 그룹 통계 API 구현 완료 / 로그인 · 회원 · 지출 · 관리자 API 개발 중

## 1. 개발 환경

| 구분 | 기술 |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 4.1.0 |
| Build Tool | Maven Wrapper |
| Web | Spring Web MVC |
| Database | Oracle Database 21c XE |
| Persistence | MyBatis Starter 4.0.1 |
| Testing | JUnit 5, MockMvc |
| Code Quality | Spotless, SpotBugs, Find Security Bugs |
| IDE | Spring Tools for Eclipse (STS) |

개발 규칙, 기능별 패키지 구조 및 코드 작성 기준은 [`AGENTS.md`](AGENTS.md)를 참고합니다.

## 2. 프로젝트 구성

```text
two-trillion/
├── frontend/       # React + Vite
├── backend/        # Spring Boot + MyBatis
├── calc/           # Python 계산 서버
└── chatbot/        # Python AI 챗봇 서버
```

현재 Backend 프로젝트의 주요 구성은 다음과 같습니다.

```text
backend/
├── pom.xml
├── mvnw
├── mvnw.cmd
├── .mvn/
├── README.md
├── AGENTS.md
├── .env.example                                  # 환경변수 이름 안내 (.env는 커밋하지 않음)
├── docs/
│   ├── api-rules.md                              # API 공통 규칙 (주소 모양 · 응답 봉투 · 오류 응답)
│   ├── history.md                                # 작업 이력
│   └── tasks.md                                  # 남은 작업 (세액 계산·진단 파트)
│
└── src/
    ├── main/
    │   ├── java/com/hoperefund/
    │   │   ├── HopeRefundBackendApplication.java
    │   │   ├── common/
    │   │   │   ├── config/
    │   │   │   │   ├── CorsConfig.java
    │   │   │   │   └── SecurityConfig.java           # 로그인 필요 주소 · JWT 필터 · 401/403 응답
    │   │   │   ├── controller/
    │   │   │   │   └── HealthController.java
    │   │   │   ├── service/
    │   │   │   │   └── TokenService.java             # 로그인 토큰(JWT) 발급 · 검사 · 로그아웃 목록
    │   │   │   ├── mapper/
    │   │   │   │   └── AccessLogMapper.java          # 접속 기록(ACCESS_LOG)
    │   │   │   ├── dto/
    │   │   │   │   ├── ApiResponse.java          # 공통 응답 봉투 {success, data, meta, error}
    │   │   │   │   └── PageMeta.java             # 목록 응답의 페이지 정보
    │   │   │   └── exception/
    │   │   │       ├── GlobalExceptionHandler.java   # 모든 오류를 공통 봉투의 error로
    │   │   │       └── *Exception.java           # BadRequest · NotFound · Conflict · NoCriteria · CalcRejected · CalcUnavailable
    │   │   ├── income/                           # 소득 정보 입력·조회 (GET·PUT /api/incomes/{taxYear})
    │   │   │   ├── controller/
    │   │   │   ├── service/
    │   │   │   ├── mapper/
    │   │   │   └── dto/
    │   │   ├── expense/                          # 카드내역 업로드 · 거래내역 조회·삭제 (CARD_UPLOAD · EXPENSE)
    │   │   │   ├── controller/                   # CardUploadController (POST /api/card-uploads) · ExpenseController (업로드 이력 · 거래 목록 · 합계 · 업로드 삭제)
    │   │   │   ├── service/                      # CardUploadService · CardUploadSaveService(저장 트랜잭션) · ExpenseService
    │   │   │   ├── client/                       # CardParseClient (계산 서버 POST /card/parse 호출)
    │   │   │   ├── mapper/                       # CardUploadMapper · ExpenseMapper
    │   │   │   ├── exception/                    # CardUploadException (중복 경고 · 파일 오류 응답)
    │   │   │   └── dto/
    │   │   ├── prediction/                       # 예상 세액 계산 · 청년 혜택 진단
    │   │   │   ├── controller/                   # CalculationController (GET·POST /api/calculations/{taxYear}) · BenefitController (GET /api/benefits/{taxYear}) · SimulationController (POST /api/simulations/{taxYear}) · PredictionController (GET /api/predictions)
    │   │   │   ├── service/                      # CalculationService · BenefitService · PredictionSaveService(결과 저장 트랜잭션)
    │   │   │   ├── client/                       # CalcClient (계산 서버 호출) · HttpCalcClient · MockCalcClient
    │   │   │   ├── mapper/
    │   │   │   └── dto/
    │   │   └── statistics/                       # 그룹 비교 통계 (GET /api/group-stats/{taxYear} · GROUP_STAT 읽기만)
    │   │       ├── controller/                   # GroupStatController
    │   │       ├── service/                      # GroupStatService
    │   │       ├── mapper/                       # GroupStatMapper
    │   │       └── dto/                          # GroupStatDTO (그룹 1행) · GroupStatResponseDTO (응답 전체)
    │   │
    │   └── resources/
    │       ├── application.yml
    │       └── mapper/                           # MyBatis SQL (기능별 폴더)
    │           ├── income/IncomeMapper.xml
    │           ├── expense/CardUploadMapper.xml · ExpenseMapper.xml
    │           ├── prediction/PredictionMapper.xml
    │           └── statistics/GroupStatMapper.xml
    │
    └── test/java/com/hoperefund/
        ├── common/
        ├── income/
        ├── expense/
        ├── prediction/
        └── statistics/
```

회원, 소득, 지출, 계산 결과, AI 상담 등 기능별 패키지는 담당자가 개발 과정에서 추가합니다.

최종 패키지 구조 및 역할 구분은 `AGENTS.md`를 기준으로 합니다.

## 3. 프로젝트 가져오기

### 3.1 사전 준비

다음 환경이 필요합니다.

- JDK 17
- STS 또는 Java 개발이 가능한 IDE
- Git
- Oracle DB 접속 정보

Maven은 프로젝트에 포함된 Maven Wrapper를 사용하므로 별도로 설치할 필요가 없습니다.

### 3.2 STS에서 프로젝트 Import

1. GitHub에서 `two-trillion` 저장소를 Clone합니다.
2. STS를 실행합니다.
3. `File → Import → Maven → Existing Maven Projects`를 선택합니다.
4. Root Directory에 `two-trillion/backend` 경로를 지정합니다.
5. `pom.xml`을 선택하고 `Finish`를 누릅니다.
6. 프로젝트 우클릭 → `Maven → Update Project`를 실행합니다.

STS Workspace는 Git 저장소와 별도의 폴더로 지정하는 것을 권장합니다.

## 4. 환경변수 설정

Spring Boot의 공통 설정은 다음 파일에서 관리합니다.

```text
src/main/resources/application.yml
```

실제 비밀번호와 인증 토큰은 소스 코드에 작성하지 않고 환경변수로 전달합니다.

### 4.1 필수 환경변수

| 환경변수 | 설명 |
|---|---|
| `DB_URL` | Oracle JDBC 접속 주소 |
| `DB_USERNAME` | Oracle DB 사용자 계정 |
| `DB_PASSWORD` | Oracle DB 비밀번호 |
| `JWT_SECRET` | 로그인 토큰 서명 비밀키 (32바이트 이상 · 없으면 서버가 켜지지 않음 · `CALC_API_TOKEN`과 다른 값) |

Oracle 접속 주소는 팀 DB 담당자가 제공한 정보를 사용합니다.

### 4.2 추가 환경변수

| 환경변수 | 설명 | 기본값 |
|---|---|---|
| `SERVER_PORT` | Spring Boot 실행 포트 | `8080` |
| `CALC_MODE` | `mock`: 가짜 계산(세액 0 · 화면 연결용) · `http`: Python 계산 서버 호출 (카드 업로드는 모드와 관계없이 항상 계산 서버 `POST /card/parse`를 호출) | `mock` |
| `CALC_BASE_URL` | Python 계산 서버 주소 | `http://localhost:8000` |
| `CALC_API_TOKEN` | 계산 서버 내부 인증 토큰 (`calc/.env`와 같은 값) · `CALC_MODE=http`면 필수 (비우면 서버가 시작되지 않음 · 계산 서버도 같은 규칙) | 없음 |
| `SAVE_ENABLED` | 계산 결과를 DB에 저장할지 (`CALC_MODE=http`일 때만 실제 저장) | `false` |
| `CHATBOT_BASE_URL` | Python 챗봇 서버 주소 | `http://localhost:8001` |
| `CHATBOT_API_TOKEN` | 챗봇 서버 내부 인증 토큰 | 없음 |
| `JWT_EXPIRATION` | 로그인 유지 시간 (지나면 다시 로그인) | `2h` |
| `LOGIN_LOCK_ENABLED` | 5번 틀리면 10분 로그인 잠금 | `true` |
| `MAIL_MODE` | 인증번호 메일 발송 방식 · `log`: 메일을 보내지 않고 로그만 · `smtp`: 실제 발송 (`CALC_MODE`와 같은 구조 · `member/client/EmailSender`) | `log` |
| `MAIL_USERNAME` · `MAIL_PASSWORD` | 메일 계정 · Gmail은 「앱 비밀번호」 · `MAIL_MODE=smtp`일 때 필요 | 없음 |
| `MAIL_FROM` | 보내는 주소 (`MAIL_MODE=smtp`인데 이것과 `MAIL_USERNAME`이 모두 비면 서버가 시작되지 않음) | `MAIL_USERNAME` |
| `MAIL_HOST` · `MAIL_PORT` | 메일 서버 주소 · 포트 | `smtp.gmail.com` · `587` |
| `EMAIL_VERIFICATION_REQUIRED` | 가입 때 이메일 인증 필수 (켜면 화면 `frontend/.env`의 `VITE_EMAIL_VERIFICATION_REQUIRED=true`도 같이) | `false` |
| `EMAIL_DEV_CODE_ENABLED` | 인증번호를 발송 API 응답(`devCode`)에 담음 · 개발용 · 운영 금지 | `false` |

환경변수 대신 `backend/.env` 파일로 넣어도 됩니다 (`.env.example` 참고 · `.env`는 커밋하지 않음). 둘 다 있으면 환경변수가 우선입니다.

Python Chatbot 서버와의 실제 통신은 아직 구현되지 않았습니다.

챗봇 서버의 내부 인증 세부 방식은 실제 연동 구현 시 확정합니다.

### 4.3 STS 환경변수 등록 방법

1. `Run → Run Configurations...`를 선택합니다.
2. `Spring Boot App`에서 Backend 프로젝트를 선택합니다.
3. `Environment` 탭으로 이동합니다.
4. `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`를 추가합니다.
5. `Apply → Run`을 선택합니다.

> **주의:** DB 비밀번호, API 토큰 등 민감정보는 GitHub에 업로드하거나 화면 캡처에 노출하지 않습니다.

## 5. 서버 실행

### 5.1 STS에서 실행

`HopeRefundBackendApplication.java`를 우클릭한 후 다음을 선택합니다.

```text
Run As → Spring Boot App
```

### 5.2 CMD에서 실행

`backend` 디렉터리에서 실행합니다.

```cmd
mvnw spring-boot:run
```

CMD에서도 DB 환경변수가 설정되어 있어야 합니다. STS에 등록한 실행 환경변수는 CMD에 자동으로 전달되지 않습니다.

### 5.3 정상 실행 확인

서버 실행 후 다음 주소로 접속합니다.

```http
GET http://localhost:8080/api/health
```

정상 응답:

```text
OK
```

해당 API는 Spring Boot의 기본 HTTP 응답 확인용입니다. Oracle DB나 Python 서버 전체의 상태까지 검사하지는 않습니다.

## 6. 테스트 및 빌드

모든 명령어는 `backend` 폴더에서 실행합니다.

### JUnit 테스트

```cmd
mvnw test
```

### 코드 형식 자동 정리

```cmd
mvnw spotless:apply
```

### 코드 형식 검사

```cmd
mvnw spotless:check
```

### SpotBugs 정적 분석

```cmd
mvnw compile spotbugs:spotbugs
```

### 전체 빌드 검증

```cmd
mvnw -B verify
```

공통 초기 프로젝트에서 다음 검증을 완료했습니다.

- Health API 테스트 1개 통과
- CORS 테스트 2개 통과
- 공통 예외 처리 테스트 3개 통과
- Spotless 검사 성공
- SpotBugs 정적 분석 실행 성공
- Maven `verify` 성공

정적 분석에서 발견된 경고의 개별 검토는 별도 작업으로 진행합니다.

최종 작업 완료 조건은 `AGENTS.md`를 따릅니다.

## 7. 현재 구현 상태

### 구현 완료

- Spring Boot 기본 프로젝트 및 Maven 환경
- Oracle DB 연결 설정 및 실행 확인
- MyBatis 기본 설정
- 환경변수 기반 공통 설정
- React 개발 서버 CORS 설정
- Health API
- 공통 오류 응답 DTO
- 공통 예외 처리 (입력 오류 · 기준값 없음 · 계산 서버 연결 불가 · 동시 저장 충돌 · 404 · 405 · 500)
- 기능 API: 소득 입력·조회 · 카드내역 업로드 · 거래내역 조회·삭제 · 예상 세액 계산 + 결과 저장 · 저장된 계산 결과 조회 · 청년 혜택 진단 · 조건 변경 시뮬레이션 · 결과 이력 조회 · 그룹 비교 통계 (주소 · 설명은 [`docs/api-rules.md`](docs/api-rules.md) 7번 표)
- Python 계산 서버 HTTP 통신 (`CalcClient` · `X-Calc-Token` · 연결·응답 제한 시간 · mock 전환)
- JUnit 자동 테스트 및 코드 품질 검사 환경

### 추후 구현 예정

- Spring Security 인증·권한 처리
- 회원·관리자 API
- Python AI 챗봇 서버 HTTP 통신
- 공통 오류 처리 확장
- API 및 DB 통합 테스트

## 8. 관련 문서

| 문서 | 용도 |
|---|---|
| `AGENTS.md` | 개발 규칙, 패키지 구조, API·DB·보안·협업 규칙 |
| `docs/api-rules.md` | API 주소 및 요청·응답 공통 규칙 |
| `docs/setup/` | 개발환경 및 도구 설정 절차 |
| `docs/history.md` | 작업 변경 이력 |
| `docs/decisions/` | 주요 결정 사항 |
| `docs/tasks.md` | 작업 현황 및 예정 작업 |

기능 요구사항은 [희망환급 기능명세서 v1.14](https://docs.google.com/spreadsheets/d/1qW5xjOhPqrWkCn9KLUUbylKfsFJCacfOAgBiXm27v9w/edit)를 참고합니다.

ERD는 팀에서 공유한 v4.2를 기준으로 합니다.
https://www.erdcloud.com/d/mL4eEnZLQ3QjgbWF4

일부 개발 문서는 초기 프로젝트 공유 이후 추가될 예정입니다.