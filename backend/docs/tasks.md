# 기능별 구현 상태

- 기준일: 2026-11-16 · 브랜치: `feature/member` (develop 최신 병합 `9f11f9b` · **push 안 함** · 로그인 작업은 아직 커밋 안 함)
- 상태: **작성**(코드 있음) → **테스트**(자동 테스트 통과) → **연결**(React가 API 호출) → **DB**(Oracle 저장 확인)

## 전체 현황

| 기능 | 기능 ID | 작성 | 테스트 | 연결 | DB |
|---|---|---|---|---|---|
| 회원가입 + 중복확인 + 이메일 인증 | F-01-01 | ✅ (메일 실제 발송만 미구현) | ✅ 백 52건 · 프론트 통과 | ✅ 병합 전 브라우저 확인 · ⏳ 병합 후 재확인 | ✅ 병합 전 저장 확인 |
| 로그인 · 로그아웃 | F-01-02 | ✅ (JWT · [decisions/0004](decisions/0004-login-jwt.md)) | ✅ 백 79건 (develop 병합 후 전체 157건 통과) | ❌ (화면은 아직 localStorage 가짜 로그인) | ⏳ 실제 DB 미확인 |
| 회원정보 수정 · 탈퇴 | 확인 필요 | ❌ | - | ❌ (화면은 localStorage) | - |
| 소득 · 계산 · 혜택 · 시뮬레이션 · 이력 · 대시보드 · 그룹 통계 (팀원 작업 · develop) | F-02-01 · F-03-01 · F-03-02 · F-04-01 · F-04-02 · F-06-01 · F-06-02 | ✅ (develop에서 받음) | ✅ | ✅ (`X-Member-Id` 개발용 헤더) | ✅ (팀원 확인) |
| 챗봇 · 문의 · 관리자 | 확인 필요 | ❌ | - | ❌ | - |

---

## develop 최신 병합 (2026-11-16)

- `origin/develop` → `feature/member` 병합 (merge 커밋 `9f11f9b`) · 로그인 작업(커밋 전)은 stash로 빼 두었다가 병합 후 다시 적용
- 받은 내용: 소득 · 계산 · 혜택 · 시뮬레이션 · 결과 이력 · 대시보드 · 그룹 통계 API (`income/` · `prediction/` · `statistics/`) · 계산 서버(`calc/`) · DB 스크립트(`database/`) · 화면 API 연동 · `pom.xml` Spotless 자동 적용
- 충돌 해결
  | 파일 | 처리 |
  |---|---|
  | `backend/docs/history.md` | 양쪽 기록 모두 유지 · 날짜순(최신 위)으로 합침 · 오늘 로그인 기록을 맨 위 |
  | `frontend/package.json` | `test` 스크립트에 양쪽 테스트 모두 (`http.test.js` · `member-api.test.js` + `benefit-service.mjs` · `calculation-service.mjs`) |
  | `frontend/vite.config.js` | develop 버전 사용 (같은 `/api` 프록시 · 주소를 `.env`의 `VITE_API_PROXY`로 바꿀 수 있음) |
  | `backend/pom.xml` | 자동 병합 (develop Spotless 설정 + 로그인용 security · jjwt) |
- 검증: 백엔드 `./mvnw test` 157건 통과 · 프론트 `npm test` **아직 안 돌림**
- ⚠ 주의: 로그인 작업이 올라가면 `SecurityConfig`가 `/api/**`에 토큰을 요구 → develop API를 `X-Member-Id`로만 부르면 **실행 시 모두 401** (테스트는 통과해서 못 잡음) → 아래 「회원번호 꺼내기」 전환 필요

---

## 회원번호 꺼내기: `X-Member-Id` → 로그인 토큰(JWT)

### 용어 정리 (헷갈리기 쉬움)

| 용어 | 정체 | 하는 일 |
|---|---|---|
| `MEMBER_ID` | ERD에 **있음** · `MEMBER` 기본키 ([01_schema.sql](../../database/01_schema.sql)) | 회원 한 명을 가리키는 번호 |
| MAX + 1 | 가입 때 **번호를 정하는 방법** (시퀀스 없음 · [decisions/0003](decisions/0003-member-id-numbering.md)) | 가입 때 한 번만 사용 · 로그인과 관계없음 |
| `X-Member-Id` | develop의 **개발용 임시 헤더** | 화면이 "나 81번"이라고 보내면 그대로 믿음 → 아무 번호나 넣으면 남의 데이터 조회 가능 · 로그인 전까지만 |

바꿀 것은 "회원번호를 **어디서** 받느냐" 하나: 화면이 보낸 헤더 → 서버가 검증한 토큰

### 흐름

```
① 회원가입  POST /api/auth/members
   아이디 · 비밀번호 · 이메일 · 성별 · 생년월일(만 14세 검사) → MEMBER 1행 저장 (MEMBER_ID = MAX + 1, 예: 81)

② 로그인  POST /api/auth/login {loginId, password}
   LoginService 검사 통과 → TokenService.issue(81, loginId, role)
   토큰 안: sub = 81 · loginId · role · jti(토큰 번호) · 만료 시각 + JWT_SECRET 서명 → 화면은 localStorage에 보관

③ 이후 요청  Authorization: Bearer <토큰>
   → SecurityConfig.JwtAuthenticationFilter: 서명 · 만료 · 로그아웃 여부 검사
       통과 → SecurityContext의 principal = 81L (회원번호) · 권한 ROLE_USER
       실패 → 아무것도 안 넣음 → /api/** 규칙에 걸려 401
   → Controller: @AuthenticationPrincipal Long memberId  ← 여기서 81
   → Service → Mapper: ... WHERE MEMBER_ID = #{memberId}
```

### 성별 · 생년월일 등 회원 정보는?

- **토큰에 넣지 않음** → 회원번호로 `MEMBER`를 다시 조회 (develop에 이미 이 방식: `PredictionMapper.findMemberQualification` · 그룹 통계의 성별 · 연령대)
- 이유: 토큰 내용은 누구나 해독 가능(서명은 위조만 막음) · 회원정보를 고쳐도 이미 발급된 토큰엔 옛 값이 남음
- 만 14세 검사는 가입 때 한 번 · DB에는 `BIRTH_DATE`만 저장

### 바꿀 곳 (팀원 코드 수정 → 팀과 먼저 맞춤 · 아직 안 함)

- [ ] 백엔드 Controller 6개 (`IncomeController` · `CalculationController` · `BenefitController` · `SimulationController` · `PredictionController` · `GroupStatController`)
      `@RequestHeader("X-Member-Id") long memberId` → `@AuthenticationPrincipal Long memberId` (Service · Mapper는 그대로)
- [ ] 프론트 `frontend/src/api/api-client.js`: `X-Member-Id` 대신 `Authorization: Bearer <토큰>` · `VITE_DEV_MEMBER_ID` 제거
- [ ] 위 Controller 테스트: 헤더 대신 로그인 정보를 넣도록 수정
- [ ] 정리: `GlobalExceptionHandler`의 "X-Member-Id 없음" 처리 · `CorsConfig` 허용 헤더의 `X-Member-Id`

---

## F-01-01 회원가입

### ▶ 다음에 할 일 (순서대로)

1. **병합 후 브라우저 재확인** (응답 형식이 바뀌어서 다시 확인)
   - 가입 → 로그인 화면으로 이동 + "회원가입이 완료되었어요"
   - 이미 있는 아이디로 중복확인 → "이미 사용 중인 아이디예요"
   - 실행 설정: 백엔드 `EMAIL_DEV_CODE_ENABLED=true` · `frontend/.env`의 `VITE_*_VERSION=v1.0` (`.env` 커밋 금지)
2. 확인되면 `git push origin feature/member`
3. 팀 공유: 새 API 주소 5개 · 회원 전용 오류 코드 (아래 「남은 확인 사항」)

### 완료한 확인

- 2026-11-11 (병합 전): 브라우저 가입 → Oracle `MEMBER` 저장 쿼리 확인 → 로그인 화면 이동
- 2026-11-11 (병합 후): 백엔드 `./mvnw test` 52건 · 프론트 `npm test` · `npm run build` 통과

### API (제안 주소 · 팀 API 명세에 추가 필요)

| 기능 | 요청 | 성공 응답 |
|---|---|---|
| 아이디 중복확인 | `GET /api/auth/members/availability?loginId=` | 200 `{available, message}` |
| 이메일 중복확인 | `GET /api/auth/members/availability?email=` | 200 `{available, message}` |
| 인증번호 발송 | `POST /api/auth/email-verifications` `{email}` | 200 `{verificationId, expiresAt, devCode}` |
| 인증번호 확인 | `POST /api/auth/email-verifications/{verificationId}/confirmation` `{code}` | 200 `{verified, message}` |
| 회원가입 | `POST /api/auth/members` (기존 필드 + `verificationId`) | 200 `{memberId, loginId, email, name(null), role, createdAt}` |

- 응답은 [api-rules](api-rules.md)의 공통 봉투: 위 표의 성공 값은 `data` 안에 들어감 (`{"success": true, "data": {...}}`) · 성공은 모두 200
- 오류 응답은 `{"success": false, "error": {"code": "코드", "message": "문구"}}` · 500은 `SERVER_ERROR` + `errorId` (원인 문구 숨김)
- 주요 오류 코드: `VALIDATION`(400) · `INVALID_LOGIN_ID`(400) · `UNDER_AGE`(400) · `DUPLICATE_LOGIN_ID` · `DUPLICATE_EMAIL` · `DUPLICATE_MEMBER`(409) · `VERIFICATION_FAILED` · `VERIFICATION_EXPIRED` · `VERIFICATION_LIMIT` · `EMAIL_NOT_VERIFIED` (화면이 코드로 분기 · api-rules 표에 없는 회원 전용 코드 → 팀 공유 필요)
- DB 저장 실패는 500 `SERVER_ERROR` + `errorId` (서버 로그에는 `SIGNUP_FAILED`로 남음)

### 처리 흐름

```
React AccountForms.jsx → member-api.js → http.js (fetch, Vite 프록시 /api → :8080)
  → MemberController / EmailVerificationController
  → MemberService.signup: withdrawn_ 차단 → 아이디 중복 → 이메일 중복 → 만 14세 → 비밀번호 → 이메일 인증 확인
  → MemberMapper.insertMember (MEMBER INSERT) → 인증 기록 삭제 → 200 ApiResponse
```

### 파일 위치

| 구분 | 파일 |
|---|---|
| Controller | `member/controller/MemberController.java` · `EmailVerificationController.java` (신규) |
| Service | `member/service/MemberService.java` · `EmailVerificationService.java` (신규) |
| DTO | `member/dto/MemberSignup{Request,Response}DTO` · `EmailVerification{Request,ConfirmRequest,Response}DTO` · `MemberAvailabilityResponseDTO` |
| Mapper / SQL | `member/mapper/MemberMapper.java` · `resources/mapper/member/MemberMapper.xml` (변경 없음) |
| 공통 | `common/exception/GlobalExceptionHandler.java` (`BusinessException` → 공통 봉투 · 5xx는 `SERVER_ERROR` + `errorId`) · `application.yml` (`member.email-verification.dev-code-enabled`) |
| 테스트 | `MemberControllerTest`(12) · `MemberServiceTest`(12) · `EmailVerificationControllerTest`(5) · `EmailVerificationServiceTest`(8) · `GlobalExceptionHandlerTest`(업무 오류 2건 포함) |
| React | `frontend/src/api/http.js` (봉투의 `data`만 꺼내 반환 · 실패는 `error.code`/`message`로 ApiError) · `member-api.js` (신규 · 테스트 포함) · `pages/auth/AccountForms.jsx` · `vite.config.js` (프록시) |

### 이메일 인증 방식 (임시 결정)

- 실제 메일은 **보내지 않음** (메일 라이브러리 미추가). 개발 중에는 `EMAIL_DEV_CODE_ENABLED=true`일 때만 응답 `devCode`로 인증번호를 받음 (기본값 false → 운영에서 노출 안 됨)
- 인증 기록은 **서버 메모리**에 저장: 6자리 · 5분 유효 · 5번 틀리면 무효 · 인증 후 30분 안에 가입 · 코드는 BCrypt로 저장
- 서버를 재시작하면 인증 기록이 사라짐 → 인증부터 다시 해야 함
- ERD에 가입 전 인증 테이블이 없어서 이렇게 결정함 (DB 변경 금지)

### 남은 확인 사항 (팀 확인 필요)

- [ ] 메일 발송 방식 (SMTP 계정 · `EmailVerificationService.send`의 TODO 위치에 연결) — **회원가입에서 남은 유일한 구현 작업**
- [ ] 팀 API 명세에 새 API 주소 5개 + 회원 전용 오류 코드(`DUPLICATE_LOGIN_ID` · `UNDER_AGE` 등, 위 「API」 표) 함께 추가
- [ ] `MEMBER.DATA_ORIGIN` 허용 값 (임시 `INPUT`) · `GENDER` 제약조건(M/F) 실제 DB 확인
- [ ] `MEMBER_ID` 시퀀스 생기면 `MAX + 1` 교체 — [decisions/0003](decisions/0003-member-id-numbering.md)

### 정한 값

- 약관 동의 버전: `v1.0` — 가입할 때 회원이 동의한 약관(이용약관 · 개인정보 · 금융정보)의 버전을 `MEMBER`에 함께 저장함. 지금 약관이 첫 판이라 `v1.0`, 약관 내용이 바뀌면 버전을 올림 (`frontend/.env`의 `VITE_*_VERSION`)
  - 저장 컬럼: `TERMS_VERSION` · `PRIVACY_VERSION` · `FINANCE_VERSION` (NOT NULL · 기본값 없음 → `.env` 값이 비면 가입 실패) + 동의 시각 `*_AGREED_AT`
- 마케팅 동의: ERD에 칸이 없어 화면에서 제거 (2026-11-11) — 컬럼이 없는 값은 저장할 수 없음

---

## F-01-02 로그인 · 로그아웃

### ▶ 다음에 할 일 (순서대로)

1. **`backend/.env`에 `JWT_SECRET` 추가** (32바이트 이상 · 없으면 서버가 안 켜짐 · `.env.example` 참고)
2. **실제 DB 확인** (서버 실행 후 Postman 등으로 확인)
   - 로그인 성공 → `ACCESS_LOG`에 `SUCCESS` 1줄
   - 비밀번호 5번 틀림 → `MEMBER.LOCKED_UNTIL` = 지금 + 10분 · `LOGIN_FAIL_COUNT` = 0 → 6번째는 `LOGIN_LOCKED`
   - 토큰 없이 `GET /api/incomes/2026` → 401 `UNAUTHORIZED` · 로그아웃 후 같은 토큰 → 401
3. **화면 연결** (별도 승인 후)
   - `http.js`: localStorage 토큰을 `Authorization: Bearer` 헤더로 붙임 · 401이면 토큰 지우고 로그인 화면으로 (단, **로그인 요청은 제외**: 로그인 실패도 401)
   - `member-api.js`: `login` · `logout` 추가
   - `account-service.js` 가짜 로그인 → 로그인 응답의 `loginId` · `role`로 교체 (App · Sidebar · 관리자 · 프로필이 사용)
4. **develop API 회원번호 전환** (develop 코드는 2026-11-16 이 브랜치에 병합됨): 위 「회원번호 꺼내기」의 「바꿀 곳」 체크리스트
   - 전환 전에 로그인 작업을 올리면 `/api/**`가 모두 로그인 필요 → `X-Member-Id`만 보내는 화면은 401
5. 팀 공유: 주소 2개 · 오류 코드 3개(`LOGIN_FAILED` · `WITHDRAWN_MEMBER` · `LOGIN_LOCKED`) · `JWT_SECRET` 환경변수

### API

| 기능 | 요청 | 성공 응답 (`data`) |
|---|---|---|
| 로그인 | `POST /api/auth/login` `{loginId, password}` | 200 `{accessToken, tokenType: "Bearer", expiresAt, loginId, role}` |
| 로그아웃 | `POST /api/auth/logout` + `Authorization: Bearer <토큰>` | 200 `null` |

- login · logout은 동사지만 알아보기 쉬워서 그대로 씀 (api-rules 1번 "주소에 동사 금지"의 예외 → 팀 공유 시 함께 알림)
- 회원번호는 응답에 넣지 않음 (api-rules 2번 · 서버가 토큰에서 꺼냄)

| 상태 | error.code | 언제 | 문구 (명세서 그대로) |
|---|---|---|---|
| 400 | `VALIDATION` | 아이디 · 비밀번호 빈 칸 | 아이디를 입력해 주세요 / 비밀번호를 입력해 주세요 |
| 401 | `LOGIN_FAILED` | 없는 아이디 · 틀린 비밀번호 (둘 다 같은 응답) | 아이디 또는 비밀번호가 맞지 않아요 |
| 401 | `WITHDRAWN_MEMBER` | 탈퇴 회원 (비밀번호가 맞을 때만) | 탈퇴한 계정이에요 |
| 401 | `LOGIN_LOCKED` | 잠금 중 (잠금 켰을 때) | 10분 뒤 다시 시도해 주세요 |
| 401 | `UNAUTHORIZED` | 로그인 필요한 주소에 토큰 없음 · 위조 · 만료 · 로그아웃됨 | 로그인이 필요해요 |
| 403 | `FORBIDDEN` | 일반 회원(USER)이 `/api/admin/**` | 접근 권한이 없어요 |

### 처리 흐름

```
1) 로그인    POST /api/auth/login → MemberController.login → LoginService.login (IP = request.getRemoteAddr())
   ① 아이디로 회원 조회 (소문자) ── 없음 → 401 LOGIN_FAILED + FAIL 기록 (가짜 비밀번호 비교로 응답 시간도 같게)
   ② 잠금 확인 (LOCKED_UNTIL > SYSDATE) → 401 LOGIN_LOCKED + FAIL   ← 비밀번호 비교 전
   ③ BCrypt 비교 ── 틀림 → increaseLoginFail (한 문장 UPDATE · 5번째면 10분 잠금 · 횟수 0) → 401 LOGIN_FAILED + FAIL
   ④ 탈퇴 확인 → 401 WITHDRAWN_MEMBER + FAIL                       ← 비밀번호가 맞은 사람에게만
   ⑤ 성공 → 실패 횟수 0 (실패한 적 있을 때만) · TokenService.issue → SUCCESS 기록 → 200
2) 이후 요청  Authorization: Bearer <토큰> → SecurityConfig.JwtAuthenticationFilter → TokenService.verify
              ├ 토큰 없음 · 위조 · 만료 · 로그아웃됨 → 401 UNAUTHORIZED (authenticationEntryPoint · 공통 봉투)
              ├ USER가 /api/admin/** → 403 FORBIDDEN (accessDeniedHandler · 공통 봉투)
              └ 통과 → Controller에서 @AuthenticationPrincipal Long memberId
3) 로그아웃  POST /api/auth/logout → 토큰 번호를 만료 시각까지 무효 목록에 → 다음 요청부터 401
```

- 주소 규칙 순서 (SecurityConfig · 위에서부터 처음 맞는 규칙): `OPTIONS` 허용 → `/api/auth/logout` 로그인 필요 → `/api/auth/**` · `/api/health` 허용 → `/api/admin/**` ADMIN → `/api/**` 로그인 필요 → 나머지 허용
- 필터 단계 오류(401 · 403)는 `GlobalExceptionHandler`가 잡지 못해서 `SecurityConfig`가 같은 봉투로 직접 응답함

### ACCESS_LOG 기록

- `LOG_ID` 시퀀스가 없어 `INSERT ... SELECT NVL(MAX(LOG_ID), 0) + 1 FROM ACCESS_LOG` 한 문장 ([decisions/0003](decisions/0003-member-id-numbering.md)과 같은 방식) → 시퀀스가 생기면 교체
- 넣는 값: `MEMBER_ID`(없는 아이디면 null) · `SCREEN` = `/api/auth/login` · `IP_ADDRESS` · `RESULT` = `SUCCESS` / `FAIL` · `ACCESSED_AT`은 기본값
- 번호가 겹치면 한 번 더 시도 · 그래도 실패하면 서버 로그만 (기록 실패로 로그인을 막지 않음)
- 개발 중 IP는 Vite 프록시를 거쳐 `127.0.0.1`(또는 `0:0:0:0:0:0:0:1`) — 정상
- 로그아웃 · 403(`DENIED`) 기록은 아직 안 함 (명세서는 로그인 성공 · 실패만)

### 파일 위치

| 구분 | 파일 |
|---|---|
| 보안 설정 | `common/config/SecurityConfig.java` (주소 규칙 · JWT 필터(안쪽 클래스) · 401/403 응답) |
| 토큰 | `common/service/TokenService.java` (발급 · 검사 · 로그아웃 목록) |
| 접속 기록 | `common/mapper/AccessLogMapper.java` · `resources/mapper/common/AccessLogMapper.xml` |
| Controller | `member/controller/MemberController.java` (주소 앞부분을 `/api/auth`로 바꾸고 login · logout 추가 · 기존 주소 그대로) |
| Service | `member/service/LoginService.java` |
| DTO | `member/dto/LoginRequestDTO` · `LoginResponseDTO` · `LoginMemberDTO`(조회용) |
| Mapper / SQL | `member/mapper/MemberMapper.java` · `MemberMapper.xml` (`selectLoginMember` · `increaseLoginFail` · `resetLoginFail` 추가) |
| 설정 | `application.yml` (`jwt.secret` · `jwt.expiration` · `member.login-lock.enabled`) · `.env.example` · `pom.xml` (`spring-boot-starter-security` · `jjwt` 0.12.6) |
| 테스트 | `SecurityConfigTest`(6) · `TokenServiceTest`(6) · `LoginServiceTest`(12) · `MemberControllerTest`(로그인 3건 추가 → 15) |

### 정한 것

- 로그인 방식 · 리프레시 토큰 없음 · 로그아웃 메모리 목록 · 화면 localStorage 보관: [decisions/0004](decisions/0004-login-jwt.md)
- 명세서 D-02(인증 전 로그인 허용 여부)는 고려하지 않음 — 이메일 인증을 해야만 가입되므로 가입한 회원은 모두 인증한 상태 (2026-11-14)

### 남은 확인 사항

- [ ] 탈퇴 기능 설계 때: 탈퇴 시 `LOGIN_ID`를 `withdrawn_…`으로 바꾸면 원래 아이디로는 조회되지 않아 `WITHDRAWN_MEMBER` 대신 `LOGIN_FAILED`가 나옴 → 함께 정함 · 탈퇴 API에서 지금 토큰도 로그아웃 처리
- [ ] `ACCESS_LOG.LOG_ID` 시퀀스 생성 여부

### 로그인 토큰과 헷갈리지 말 것: 계산 서버 토큰 (`X-Calc-Token`)

| | 계산 서버 토큰 (`X-Calc-Token`) | 로그인 토큰 (JWT) |
|---|---|---|
| 누가 보냄 | Spring 서버 → 계산 서버 | 화면(React) → Spring 서버 |
| 값 | `.env`의 `CALC_API_TOKEN` 하나 (고정) | 회원이 로그인할 때마다 새로 발급 |
| 목적 | 아무나 계산 서버를 못 부르게 | 어느 회원인지 알아내기 |

- `JWT_SECRET`은 `CALC_API_TOKEN`과 다른 값으로 둠
