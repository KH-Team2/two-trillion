# 작업 이력

최신 작업이 맨 위에 오도록 기록한다.

### [2026-11-17] 회원번호를 X-Member-Id 헤더 대신 로그인 토큰에서 꺼내기
- **작업 내용**
  - Controller 6개 (`IncomeController` · `CalculationController` · `BenefitController` · `SimulationController` · `PredictionController` · `GroupStatController`): `@RequestHeader("X-Member-Id") long memberId` → `@AuthenticationPrincipal Long memberId` (Service · Mapper 변경 없음)
  - `GlobalExceptionHandler`: 필수 헤더 없음 401 `UNAUTHORIZED` → 400 `VALIDATION` (401은 SecurityConfig가 응답) · `CorsConfig`: 허용 헤더에서 `X-Member-Id` 삭제
  - 테스트: Controller 테스트가 헤더 대신 로그인 정보(SecurityContext)를 넣음 · 헤더 전용 테스트 4개 삭제 → `SecurityConfigTest`에 「토큰 → Controller가 회원번호 받음」 · 「회원 API 7곳 헤더만 보내면 401」 추가
  - 화면: `api-client.js`가 localStorage `hope-refund.accessToken`을 `Authorization: Bearer`로 보냄 · `VITE_DEV_MEMBER_ID` 삭제 · 테스트 2개 수정
  - 문서: [tasks.md](tasks.md) · [api-rules.md](api-rules.md) · frontend `AGENTS.md` · `API_INTEGRATION.md`
- **변경 사유**: 헤더 값만 바꾸면 남의 데이터를 볼 수 있음 (IDOR) · 로그인(Security) 적용으로 헤더만으로는 401이 됨
- **검증 결과**: `./mvnw test` 154건 통과 (157 − 헤더 전용 4 + 1) · 프론트 `npm test` · `npm run build` 통과
  - `npm run format:check`: 이 PC에서 전부터 114개 파일 실패 (CRLF 줄바꿈) · 고친 파일은 통과
  - 미검증: 실제 서버 실행 후 호출 · 로그인 화면 연결 전이라 화면에서는 401

### [2026-11-16] 로그인 · 로그아웃 백엔드 구현 (F-01-02 · JWT)
- **작업 내용**
  - `pom.xml`: `spring-boot-starter-security` · `jjwt` 0.12.6 추가 (`spring-security-crypto`는 starter에 포함되어 뺌)
  - `common/config/SecurityConfig`(신규): 주소별 로그인 규칙 · JWT 필터(안쪽 클래스) · 401 `UNAUTHORIZED` · 403 `FORBIDDEN`을 공통 봉투로 응답
  - `common/service/TokenService`(신규): 토큰 발급 · 검사 · 로그아웃 무효 목록(메모리) · `JWT_SECRET` 32바이트 미만이면 서버 시작 실패
  - `common/mapper/AccessLogMapper`(신규 · XML 포함): 로그인 성공 · 실패 기록 (`LOG_ID` MAX + 1)
  - `member/service/LoginService`(신규) · `member/dto/Login{Request,Response,Member}DTO`(신규)
  - `MemberController`: 주소 앞부분을 `/api/auth`로 바꾸고 `POST /login` · `POST /logout` 추가 (기존 가입 · 중복확인 주소는 그대로)
  - `MemberMapper`: `selectLoginMember` · `increaseLoginFail` · `resetLoginFail` 추가
  - `application.yml` · `.env.example` · README: `JWT_SECRET` · `JWT_EXPIRATION` · `LOGIN_LOCK_ENABLED`
  - `GlobalExceptionHandler` · `EmailVerificationService`: 주석만 수정 (401/403 위치 안내 · 없는 decisions/0004 참조 → tasks.md)
  - 문서: [decisions/0004](decisions/0004-login-jwt.md) 신규 · [tasks.md](tasks.md) 로그인 절 갱신 · [api-rules](api-rules.md) 2번 "미정" → JWT · 오류 코드 표
- **변경 사유**: F-01-02 구현. 리프레시 토큰 없음 · 로그아웃 메모리 목록 · 화면 localStorage 보관안 확정 (11/16). 새 파일은 README · AGENTS.md 폴더 구조 안에서 최소로 만듦
- **검증 결과**: `./mvnw test` 79건 통과 (신규 `SecurityConfigTest` 6 · `TokenServiceTest` 6 · `LoginServiceTest` 12 · `MemberControllerTest` 로그인 3건 추가)
- **미검증**: 실제 Oracle DB에서 로그인 · 잠금 · ACCESS_LOG 저장 (SQL은 Mapper 대역으로만 테스트) · 화면 연결 안 함

### [2026-11-16] 결과 이력 조회 API (F-04-02)
- **작업 내용**:
  - `GET /api/predictions?year=2026`: 본인의 그 연도 결과 전부(현재 · 시나리오) 최신순 · 없으면 빈 목록(오류 아님)
  - `GET /api/predictions/{predictionId}`: 하나 다시 열기 (대시보드와 같은 모양 · 항목별 내역 포함) · 없거나 남의 것이면 404
  - 새 `PredictionSummaryDTO` · `PredictionController` · 매퍼 `findPredictions` · `findPrediction` · `CalculationService.getHistory()` · `getOne()`
- **변경 사유**:
  - 기능명세서 F-04-02 · 주소는 팀 규칙(목록 거르기는 `?` 뒤 · 그 한 건은 경로)
- **검증 결과**:
  - 테스트 3개 추가 · curl: 회원 81 목록 3건 · 6번 열기 200 · 82번이 6번 열기 404

### [2026-11-15] 조건 변경 시뮬레이션 API (F-04-01)
- **작업 내용**:
  - `POST /api/simulations/{taxYear}`: 바꾼 입력값(소득 칸 전부 · 무주택 여부 · 카드 분류별 금액)으로 세액 계산(F-03-01)을 다시 하고 `PREDICTION`에 시나리오 이름(`SCENARIO_NAME`) · 기준 결과 번호(`BASE_PREDICTION_ID`)를 채워 저장
  - 기준 결과는 본인의 같은 연도 결과만 (`countOwnPrediction`) · 아니면 404 (403이 아니라 존재 여부를 숨김) · 자녀 수 > 부양가족 수는 400
  - `PredictionSaveService.saveScenario()` 추가 (결과 1행 INSERT만 다르고 입력값 · 상세 저장은 기존과 같은 함수 · 같은 트랜잭션)
  - 요청 검사: `SimulationRequestDTO`(`income` · `cardSpends`에 `@Valid`) · `CardSpendDTO`에 분류 1 ~ 5 · 결제수단 · 금액 검사 (DB 합계는 `@Valid`를 거치지 않아 기존 계산에 영향 없음)
- **변경 사유**:
  - 기능명세서 F-04-01 · 원래 소득(`INCOME`)과 카드 내역(`EXPENSE`)은 읽지도 고치지도 않음 (입력값은 결과마다 `PREDICTION_INPUT`에만)
- **검증 결과**:
  - 테스트 4개 추가 · curl mock 모드 200 · 404 · 400
  - 학원 PC에서 계산 서버(http) + 저장: 회원 81 기준 결과 3번 → 시나리오 6번 · `PREDICTION_INPUT` 월세 60만 · `INCOME` 월세는 그대로

### [2026-11-13] 그룹 비교 통계 조회 API (F-06-02)
- **작업 내용**:
  - `GET /api/group-stats/{taxYear}`: 본인의 성별·연령대 그룹과 총급여 구간 그룹의 국세통계 평균(인원 · 1인당 총급여 · 결정세액 · 환급세액) · 못 찾은 그룹은 그 칸만 null(200)
  - 새 패키지 `statistics` (`GROUP_STAT` 읽기만)
  - SQL 규칙: 통계 연도는 귀속 연도 이하 최근(지금 2024) · 나이 = 귀속 연도 − 태어난 해 · `AGE_MIN` · `AGE_MAX` NULL이면 제한 없음 · 급여는 `SALARY_MIN` 초과 ~ `SALARY_MAX` 이하이고 `SALARY_MIN`에는 「NULL이면 통과」를 붙이지 않음 (「1억 초과(결정세액 없는 자)」 행이 누구에게나 걸리는 것 방지)
- **변경 사유**:
  - 화면이 브라우저 입력값과 화면 안 통계 파일로 그룹을 찾고 있었음 → 서버에 저장된 회원 정보 · 소득으로 찾도록
- **검증 결과**:
  - 테스트 6개 추가 (Service 3 · Controller 3)
  - 학원 DB: 회원 81 → 여성 40대 · 4천만 이하 · 82 → 여성 50대 · 2천만 이하 · 없는 회원 → 200에 둘 다 null
  - 화면 스냅샷과 평균이 조금 다름: 나누는 인원 칸이 다름 (DB는 「과세대상근로소득」 인원 · 스냅샷은 「급여총계」 인원) · DB는 고칠 것 없음

### [2026-11-13] 로그인 방식 토큰(JWT)으로 결정 · Security 401/403 공지 정리
- **작업 내용** (문서만 수정 · 코드 변경 없음)
  - [tasks.md](tasks.md) 「로그인 · 로그아웃」: 로그인 방식을 토큰(JWT)으로 정함 · 세부안(리프레시 토큰 안 씀 · 로그아웃은 서버 메모리 무효 목록 · localStorage + Bearer 헤더)은 승인 대기로 적음
  - 처리 흐름(로그인 → 필터 토큰 검사 → 401/403 → 로그아웃) 추가
  - 팀 공지의 `SecurityConfig` 401(`authenticationEntryPoint`) · 403(`accessDeniedHandler`) 응답 정리 · 공지 예시 JSON이 팀 봉투와 달라 봉투 모양으로 맞춰야 함을 적음
  - 계산 서버 `X-Calc-Token`(서버끼리 인증)과 로그인 토큰(JWT)의 차이 정리
- **확인한 내용**
  - 프로젝트에 `@EnableWebSecurity` · `SecurityFilterChain` 없음 → `SecurityConfig` 새로 만들어야 함 (`common/` 수정 · 라이브러리 추가 승인 필요)
  - 기능명세서 원본은 저장소에 없음 → 로그인 기능 ID · 요구사항은 시트 확인 필요
- **변경 사유**: 로그인 · 로그아웃 구현 전 방식 결정과 팀 공지 내용 기록

### [2026-11-12] 저장된 계산 결과 조회 API (F-06-01 결과 대시보드)
- **작업 내용**:
  - `GET /api/calculations/{taxYear}`: 본인의 그 연도 계산 결과 중 가장 최근 1건 + 항목별 공제 내역 · 없으면 404 `NOT_FOUND`
  - 응답은 계산(POST)과 같은 `CalculationResponseDTO` · 시뮬레이션 결과(`BASE_PREDICTION_ID`가 있는 행)는 빼고 기준 결과만
  - `prediction` 패키지에 둠 (PREDICTION · PREDICTION_INPUT · PREDICTION_DETAIL의 주인 · 시뮬레이션도 같은 조회를 쓸 수 있게)
- **변경 사유**:
  - 전에는 계산 직후 응답과 브라우저 저장에만 결과가 있어서, 다른 브라우저로 들어오거나 브라우저 저장이 지워지면 대시보드가 저장된 결과를 못 보여 줌
- **검증 결과**:
  - 테스트 4개 추가 (Service 2 · Controller 2) · 「계산은 POST만 받는다」 → 「GET과 POST만 받는다」(PUT → 405)
  - curl: 회원 81 → 200 · 82 → 404

### [2026-11-12] 소득 API 보완 (PR #7과 비교 반영)
- **작업 내용**:
  - `IncomeRequestDTO`: 모든 입력 검사에 한글 문구(message) 추가
  - `IncomeResponseDTO` · `IncomeMapper.xml`: 응답에 `incomeId` · `taxYear` 추가
  - `IncomeServiceImpl`: 2000~2100 밖 연도는 DB 조회 없이 바로 400 `NO_CRITERIA`
  - `IncomeControllerTest`: 테스트 메서드 이름 영문화(@DisplayName에 한글 설명) · 한글 문구 · incomeId/taxYear 확인 추가
  - `IncomeServiceImplTest`: 범위 밖 연도 테스트 추가
  - develop 최신 README · .gitignore 반영 (README의 소득 항목은 develop에 이미 있어 따로 수정하지 않음)
- **변경 사유**:
  - 검사 문구가 없으면 기본 문장("널이어서는 안됩니다" 등)이 화면에 그대로 나감
  - API 명세서 소득 행의 응답 칸(incomeId · taxYear)과 맞춤
  - 같은 기능의 팀원 PR #7과 비교해 빠진 부분 반영
- **검증 결과**:
  - `mvnw spotless:check` · `mvnw -B verify` 통과
  - Thunder Client로 실제 DB 확인: PUT 200 · GET 200 · 2024 GET 400 `NO_CRITERIA`
  - 화면(PR #10 화면 코드 · npm run dev)에서 「계산」 → `PUT /api/incomes/2026` 200 확인 (뒤이은 계산 API는 이 브랜치에 없어 404 · 정상)

### [2026-11-12] 소득 입력·수정 API (F-02-01)
- **작업 내용**:
  - `GET /api/incomes/{taxYear}` 소득 조회 · `PUT /api/incomes/{taxYear}` 소득 저장 추가
  - `income/` controller · service · mapper · dto, `mapper/income/IncomeMapper.xml`, 컨트롤러 · 서비스 테스트
  - 저장은 MERGE (그 연도 행이 있으면 UPDATE, 없으면 INSERT)
  - 검증: 총급여 · 기납부세액 필수, 모든 금액 0 이상, 20세 이하 자녀 수 ≤ 부양가족 수
  - 연도 검사: DB 기준값(`DEDUCTION_TYPE`)이 있는 연도만 받음 (없으면 400 `NO_CRITERIA`)
- **변경 사유**:
  - 기능명세서 F-02-01 구현
  - 연도 검사를 코드 고정값(2025 · 2026) → DB 기준값으로 변경: 관리자가 새 연도 기준값을 넣으면 코드 수정 없이 열리게 (팀원 리뷰 반영)
- **검증 결과**:
  - `mvnw spotless:check` · `mvnw -B verify` 통과
  - Thunder Client로 실제 DB 확인: PUT 200 · GET 200

### [2026-11-12] 회원가입 남은 확인 사항 정리 · 로그인 준비
- **작업 내용** (문서만 수정 · 코드 변경 없음)
  - [tasks.md](tasks.md) 「남은 확인 사항」 정리: "새 API 주소 5개"와 "회원 전용 오류 코드"를 팀 API 명세 추가 한 항목으로 묶음
  - 약관 동의 버전을 임시가 아닌 정한 값 `v1.0`으로 확정 → 「정한 값」으로 옮김 (마케팅 동의 제거도 함께)
  - "서버 가입 회원 로그인 불가" 항목 삭제 → 다음 기능(로그인 · 로그아웃)으로 이어감
- **확인한 내용**
  - 약관 동의 버전은 `MEMBER`에 컬럼이 있어 저장됨: `TERMS_VERSION` · `PRIVACY_VERSION` · `FINANCE_VERSION` (`VARCHAR2(10)` · NOT NULL · 기본값 없음) + 동의 시각 `*_AGREED_AT`
  - 컬럼이 없는 값은 저장 불가 (INSERT 시 `ORA-00904`) → 마케팅 동의는 ERD에 칸이 없어 화면에서 제거한 것
  - 로그인 방식(세션 / JWT)은 api-rules에 "미정" · 팀 브랜치(develop · admin · project-base · dashboard)에도 Spring Security 설정 · 로그인 API 없음
  - ERD 로그인 관련 컬럼: `LOGIN_FAIL_COUNT`(5번째 실패 시 10분 잠금) · `LOCKED_UNTIL` · 토큰 저장 테이블은 없음
- **변경 사유**: 회원가입 마무리 · 다음 기능(로그인 · 로그아웃) 시작 전 정리

### [2026-11-11] 팀 브랜치 병합 · 응답 형식을 공통 규칙(api-rules)에 맞춤
- **작업 내용**
  - `feature/member-signup` → 팀 `feature/member` 병합 (팀 공통 예외 처리기 · `ApiResponse` 봉투 유지)
  - 병합 때 빠진 `BusinessException` 처리를 `GlobalExceptionHandler`에 다시 추가 (봉투 형식 · 5xx는 `SERVER_ERROR` + `errorId`)
  - `MemberController` · `EmailVerificationController`: 성공 201 → 200 · `ApiResponse.ok(...)`로 감쌈
  - `frontend/src/api/http.js`: 봉투에서 `data`를 꺼내 반환 · 오류는 `error.code` / `error.message` 사용
  - 테스트: 회원 Controller 테스트를 새 형식으로 수정 · `TestErrorController`에 업무 오류 주소 복구 · 5xx 업무 오류 테스트 1건 추가
- **변경 사유**: 병합 후 중복 아이디 등 업무 오류가 500으로 나가는 문제(테스트 13건 실패) · [api-rules.md](api-rules.md)(성공 200 · 공통 봉투) 미반영
- **검증 결과**: `./mvnw test` 52건 통과 · `npm test` · `npm run build` 통과
  - 병합 전 브라우저 가입 · Oracle 저장 · 로그인 화면 이동 확인 (2026-11-11)
  - 미검증: 병합 후 브라우저 재확인 · 실제 메일 발송(미구현)

### [2026-11-10] F-01-01 회원가입 React 연결 · 중복확인 · 이메일 인증
- **작업 내용**: 중복확인 API · 이메일 인증 API(서버 메모리 · 메일 미발송) 추가, 가입 주소 `/api/auth/members`로 변경, 응답 필드 변경, React 화면을 실제 API에 연결
- **변경 사유**: F-01-01 지침 (React → Spring Boot → Oracle 끝까지 연결)
- **검증 결과**: `./mvnw -B verify` → BUILD SUCCESS · Tests 45 · `npm test` · `npm run build` 통과
  - 미검증(당시): 서버 실행 후 API 호출 · 브라우저 가입 · Oracle 저장 → 2026-11-11 확인

### [2026-11-08] 회원가입 API 구현 · 공통 업무 오류 처리 추가
- **작업 내용**
  - 추가: `member/controller/MemberController.java` · `member/service/MemberService.java` · `member/mapper/MemberMapper.java` · `member/dto/MemberSignupRequestDTO.java` · `member/dto/MemberSignupResponseDTO.java` · `member/dto/MemberInsertDTO.java` · `resources/mapper/member/MemberMapper.xml`
  - 추가: `common/config/PasswordConfig.java`(BCrypt `PasswordEncoder` 빈) · `common/exception/BusinessException.java`(상태 코드 · 오류 코드 · 문구)
  - 수정: `common/exception/GlobalExceptionHandler.java` — `BusinessException` 처리(`handleBusiness`) 추가
  - 수정: `pom.xml` — `spring-security-crypto` 추가 (암호화 기능만 · 인증 필터 미사용)
  - 수정: `README.md` — ERD 링크 추가
  - 추가: `db/Schema.sql`(ERD v4.1 내보내기) · `docs/` 문서
  - 테스트: `member/controller/MemberControllerTest.java`(5건) · `member/service/MemberServiceTest.java`(7건) 추가 · `GlobalExceptionHandlerTest`에 업무 오류 테스트 1건 · `TestErrorController`에 `/api/test-errors/business` 추가
- **주요 변경 사항**
  - `POST /api/members` → 201 `{memberId, loginId, message}` · 오류는 `{"error","message"}`
  - 아이디·이메일 소문자 저장 · 비밀번호 BCrypt 저장 · 만 14세 이상 · `withdrawn_` 아이디 차단 · 중복 시 409
  - `MEMBER_ID`는 시퀀스가 없어 `MAX + 1` 임시 사용
- **변경 사유**: 회원가입 기능의 API · DB 저장 구현, 서비스 업무 오류를 공통 오류 형식으로 응답
- **검증 결과**
  - `./mvnw spotless:check` → 통과
  - `./mvnw -B verify` → BUILD SUCCESS · Tests run: 20, Failures: 0, Errors: 0
  - 미검증: Oracle 실제 INSERT(테스트는 Mapper Mock) · React 연동(화면은 아직 localStorage 사용)
