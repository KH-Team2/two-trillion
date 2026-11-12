# 기능별 구현 상태

- 기준일: 2026-11-12 · 브랜치: `feature/member` (팀 브랜치 병합 · 커밋 완료 · **push 안 함**)
- 상태: **작성**(코드 있음) → **테스트**(자동 테스트 통과) → **연결**(React가 API 호출) → **DB**(Oracle 저장 확인)

## 전체 현황

| 기능 | 기능 ID | 작성 | 테스트 | 연결 | DB |
|---|---|---|---|---|---|
| 회원가입 + 중복확인 + 이메일 인증 | F-01-01 | ✅ (메일 실제 발송만 미구현) | ✅ 백 52건 · 프론트 통과 | ✅ 병합 전 브라우저 확인 · ⏳ 병합 후 재확인 | ✅ 병합 전 저장 확인 |
| 로그인 · 로그아웃 (다음 작업) | 확인 필요 | ❌ (방식 결정 대기) | - | ❌ (화면은 localStorage) | - |
| 회원정보 수정 · 탈퇴 | 확인 필요 | ❌ | - | ❌ (화면은 localStorage) | - |
| 소득 · 카드 · 예측 · 통계 · 챗봇 · 문의 · 관리자 | 확인 필요 | ❌ | - | ❌ | - |

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

## 로그인 · 로그아웃 (다음 기능)

### ▶ 먼저 정할 것

- [ ] **로그인 방식: 세션 / JWT** — [api-rules](api-rules.md) 2번에 "Spring Security 담당이 정한다 (미정)". 정하면 `decisions/0004`에 기록
  - 세션: Spring Security + `HttpSession` 쿠키 · Vite 프록시로 같은 주소라 CORS 문제 없음 · 로그아웃은 세션 삭제 · ERD 변경 불필요
  - JWT: 서버 재시작에 강함 · 로그아웃 시 토큰 무효화 · 리프레시 토큰 저장 테이블이 ERD에 없음

### 확인한 현재 상태 (2026-11-12)

- 팀 브랜치(develop · admin · project-base · dashboard)에 Spring Security 설정 · 로그인 API 없음 (`pom.xml`에는 `spring-security-crypto`만)
- 화면 로그인은 `frontend/src/api/account-service.js`의 localStorage 방식 → 서버에 가입한 회원은 아직 로그인 불가
- ERD: `LOGIN_FAIL_COUNT`(5번째 실패 시 10분 잠금 후 0) · `LOCKED_UNTIL` · `STATUS`(ACTIVE/WITHDRAWN) · `ROLE`(USER/ADMIN)
- 응답 규칙: 로그인 필요한데 정보 없으면 401 `UNAUTHORIZED` · 로그아웃 성공은 200 + `data: null`
