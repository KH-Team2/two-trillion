# 기능별 구현 상태 · 코드 연결 구조

- 기준일: 2026-11-08 · 브랜치: `feature/project-base`
- 기준 자료: `db/Schema.sql`(ERD v4.1 내보내기) · 실제 코드. 기능명세서(구글 시트)는 열람하지 못해 기능 ID는 「확인 필요」로 둔다.
- 상태 표기
  - **작성**: 코드 파일이 있음
  - **호출**: 실제 실행 경로에서 호출됨
  - **API 연결**: React가 Spring Boot API를 실제로 호출함
  - **DB 반영**: 실제 Oracle DB에 저장·조회가 확인됨

## 전체 현황

| 기능 | 기능 ID | 백엔드 작성 | 백엔드 테스트 | React → API 연결 | DB 반영 |
|---|---|---|---|---|---|
| 회원가입 | 확인 필요 | 완료 | 통과 (Mock · 12건) | **미연결** (화면은 브라우저 저장소 사용) | **미검증** (Oracle 실행 테스트 없음) |
| 로그인 · 아이디/이메일 중복확인 API · 이메일 인증 · 회원정보 수정 · 탈퇴 | 확인 필요 | 미작성 | - | 미연결 | - |
| 소득 · 카드 업로드 · 예측 · 통계 · 챗봇 · 문의 · 관리자 | 확인 필요 | 미작성 | - | 미연결 | - |

---

## 회원가입 (기능 ID: 확인 필요)

### 6-1. 파일 및 함수 위치표

| 구분 | 파일 경로 | 클래스 / 함수(메서드) | 상태 |
|---|---|---|---|
| React 화면 | `frontend/src/pages/auth/AccountForms.jsx` | `AccountForms` 의 `<form onSubmit>` (L97) · `payload` 생성 (L113-122) · 호출 (L123) | 작성 · 호출됨 |
| React API | `frontend/src/api/account-service.js` | `accountService.signUp` (L382) → `createAccountService().signUp` (L198) → `register` (L90) | 작성 · **localStorage에 저장 (HTTP 호출 없음)** |
| React 설정 | `frontend/src/config/consent-versions.js` | `consentVersions` (L4-6, `VITE_*_VERSION`) | 작성 · 화면 payload에는 미포함 |
| Controller | `backend/src/main/java/com/hoperefund/member/controller/MemberController.java` | `MemberController.signup` (L29-36) | 작성 · 테스트에서 호출 |
| Service | `backend/src/main/java/com/hoperefund/member/service/MemberService.java` | `signup` (L43-86) · `validatePassword` (L89-96) · `validateBirthDate` (L99-109) | 작성 · 테스트에서 호출 |
| DTO (요청) | `backend/src/main/java/com/hoperefund/member/dto/MemberSignupRequestDTO.java` | `MemberSignupRequestDTO` (record) | 작성 |
| DTO (DB 전달) | `backend/src/main/java/com/hoperefund/member/dto/MemberInsertDTO.java` | `MemberInsertDTO` | 작성 |
| DTO (응답) | `backend/src/main/java/com/hoperefund/member/dto/MemberSignupResponseDTO.java` | `MemberSignupResponseDTO` (record) | 작성 |
| Mapper | `backend/src/main/java/com/hoperefund/member/mapper/MemberMapper.java` | `countByLoginId` (L14) · `countByEmail` (L17) · `insertMember` (L20) | 작성 · 테스트는 Mock |
| SQL | `backend/src/main/resources/mapper/member/MemberMapper.xml` | `countByLoginId` (L8-12) · `countByEmail` (L15-19) · `insertMember` (L27-64, `selectKey` L28-30) | 작성 · **Oracle 실행 미검증** |
| 공통 | `backend/src/main/java/com/hoperefund/common/config/PasswordConfig.java` | `passwordEncoder` (BCrypt) | 작성 · 호출 |
| 공통 | `backend/src/main/java/com/hoperefund/common/exception/BusinessException.java` | `BusinessException(status, code, message)` | 작성 · 호출 |
| 공통 | `backend/src/main/java/com/hoperefund/common/exception/GlobalExceptionHandler.java` | `handleValidation` (L21) · `handleInvalidJson` (L34) · `handleBusiness` (L43) · `handleException` (L66) | 작성 · 테스트에서 호출 |
| 테스트 | `backend/src/test/java/com/hoperefund/member/controller/MemberControllerTest.java` | 5건 (`@WebMvcTest` · Service Mock) | 통과 |
| 테스트 | `backend/src/test/java/com/hoperefund/member/service/MemberServiceTest.java` | 7건 (Mapper Mock · 실제 BCrypt) | 통과 |

### 6-2. 데이터 처리 구조표

| 구분 | 내용 |
|---|---|
| React 입력 | 아이디 · 비밀번호 · 비밀번호 확인 · 이메일(+ 인증번호) · 생년월일 · 성별(`남성`/`여성` 버튼) · 필수 동의 3개(이용약관 · 개인정보 · 금융 정보) · 선택 동의(마케팅) |
| API 요청 | `POST /api/members` · `Content-Type: application/json`<br>`{"loginId","email","password","passwordConfirm","birthDate":"yyyy-MM-dd","gender":"M\|F","termsAgreed":true,"privacyAgreed":true,"financeAgreed":true,"termsVersion","privacyVersion","financeVersion"}`<br>※ 현재 React는 이 요청을 보내지 않는다 (미연결) |
| API 응답 | 성공 **201** `{"memberId":1,"loginId":"hope_user","message":"회원가입이 완료되었습니다."}`<br>오류 `{"error":"코드","message":"문구"}`: 400 `VALIDATION`(형식·JSON·비밀번호 불일치·72바이트 초과·미래 생년월일) · 400 `UNDER_AGE` · 400 `INVALID_LOGIN_ID`(`withdrawn_` 시작) · 409 `DUPLICATE_LOGIN_ID` · 409 `DUPLICATE_EMAIL` · 409 `DUPLICATE_MEMBER`(INSERT 중 키 중복) · 500 `SERVER_ERROR` |
| DTO | `MemberSignupRequestDTO`: 요청 수신 · Bean Validation (`toString`에서 비밀번호 제외)<br>`MemberInsertDTO`: 정리된 값 + 서버 지정 값을 Mapper로 전달 · `memberId`는 `selectKey`가 채움<br>`MemberSignupResponseDTO`: 응답 (민감정보 제외) |
| Controller | `MemberController.signup`: `@Valid` 검증 → `memberService.signup` → 201 반환 |
| Service | `MemberService.signup` (`@Transactional`): ① 아이디·이메일 `trim` + 소문자 ② 비밀번호 확인 일치 · UTF-8 72바이트 이하 ③ 만 14세 이상(Asia/Seoul) ④ `withdrawn_` 접두어 차단 ⑤ 아이디 중복 → 이메일 중복 조회 ⑥ BCrypt 암호화 ⑦ `EMAIL_VERIFIED_YN='N'` · `DATA_ORIGIN='INPUT'` 지정 ⑧ INSERT · `DuplicateKeyException` → 409 |
| Mapper | `MemberMapper.countByLoginId(String)` · `countByEmail(String)` · `insertMember(MemberInsertDTO)` |
| SQL | `countByLoginId`: `SELECT COUNT(*) FROM MEMBER WHERE LOGIN_ID = #{loginId}` (탈퇴 회원 포함)<br>`countByEmail`: `SELECT COUNT(*) FROM MEMBER WHERE EMAIL = #{email}` (탈퇴 회원 포함)<br>`insertMember`: `selectKey`(BEFORE) `SELECT NVL(MAX(MEMBER_ID),0)+1 FROM MEMBER` → `INSERT INTO MEMBER (...)` |
| DB 테이블 | `MEMBER` |
| DB 컬럼 (INSERT 대상) | `MEMBER_ID` · `LOGIN_ID` · `EMAIL` · `PASSWORD` · `BIRTH_DATE` · `GENDER` · `UPDATED_AT` · `TERMS_AGREED_AT` · `PRIVACY_AGREED_AT` · `FINANCE_AGREED_AT` · `TERMS_VERSION` · `PRIVACY_VERSION` · `FINANCE_VERSION` · `EMAIL_VERIFIED_YN` · `DATA_ORIGIN` |
| DB 컬럼 (INSERT 제외 · DB 기본값) | `MILITARY_MONTHS`(0) · `HOMELESS_HEAD_YN`('N') · `ROLE`('USER') · `STATUS`('ACTIVE') · `CREATED_AT`(SYSDATE) · `LOGIN_FAIL_COUNT`(0) |
| DB 컬럼 (INSERT 제외 · NULL 허용) | `NAME` · `HIRE_DATE` · `COMPANY_SIZE` · `WITHDRAWN_AT` · `LOCKED_UNTIL` |
| DB 컬럼 (조회 조건) | `LOGIN_ID` · `EMAIL` |

### 6-3. DB 컬럼 연결표

| React 데이터 (현재 화면 이름) | DTO 필드 (요청 → INSERT) | SQL 파라미터 | DB 컬럼 |
|---|---|---|---|
| 아이디 `username` | `loginId` → `loginId` (trim · 소문자) | `#{loginId}` | `MEMBER.LOGIN_ID` |
| 이메일 `email` | `email` → `email` (trim · 소문자) | `#{email}` | `MEMBER.EMAIL` |
| 비밀번호 `password` | `password` → `password` (BCrypt 암호문) | `#{password}` | `MEMBER.PASSWORD` |
| 비밀번호 확인 `confirm` | `passwordConfirm` (비교만 · 저장 안 함) | - | - |
| 생년월일 `birth` | `birthDate` → `birthDate` | `#{birthDate}` | `MEMBER.BIRTH_DATE` |
| 성별 `gender` (`남성`/`여성`) | `gender` (`M`/`F`) → `gender` | `#{gender}` | `MEMBER.GENDER` |
| 이용약관 동의 `terms` | `termsAgreed` (검증만) | `SYSDATE` | `MEMBER.TERMS_AGREED_AT` |
| 개인정보 동의 `privacy` | `privacyAgreed` (검증만) | `SYSDATE` | `MEMBER.PRIVACY_AGREED_AT` |
| 금융 정보 저장 동의 `financial` | `financeAgreed` (검증만) | `SYSDATE` | `MEMBER.FINANCE_AGREED_AT` |
| 약관 버전 `consentVersions.TERMS_VERSION` (payload 미포함) | `termsVersion` → `termsVersion` (trim) | `#{termsVersion}` | `MEMBER.TERMS_VERSION` |
| 개인정보 버전 `consentVersions.PRIVACY_VERSION` (payload 미포함) | `privacyVersion` → `privacyVersion` (trim) | `#{privacyVersion}` | `MEMBER.PRIVACY_VERSION` |
| 금융 동의 버전 `consentVersions.FINANCE_VERSION` (payload 미포함) | `financeVersion` → `financeVersion` (trim) | `#{financeVersion}` | `MEMBER.FINANCE_VERSION` |
| 이메일 인증 `verificationToken` | 받지 않음 · 서버 고정값 `"N"` → `emailVerifiedYn` | `#{emailVerifiedYn}` | `MEMBER.EMAIL_VERIFIED_YN` |
| 마케팅 동의 `marketing` | 받지 않음 | - | 확인 불가 (ERD에 컬럼 없음) |
| (서버 지정) | `"INPUT"` → `dataOrigin` | `#{dataOrigin}` | `MEMBER.DATA_ORIGIN` (허용 값 확인 필요) |
| (서버 지정) | `selectKey` → `memberId` → 응답 `memberId` | `#{memberId}` | `MEMBER.MEMBER_ID` |
| (서버 지정) | - | `SYSDATE` | `MEMBER.UPDATED_AT` |

### 구현 상태

- [x] 회원가입 API · Service · Mapper · SQL 작성
- [x] 공통 업무 오류 처리(`BusinessException`) · BCrypt 설정
- [x] 단위·슬라이스 테스트 12건 통과 (Mapper는 Mock)
- [ ] **Oracle 실제 INSERT 미검증** (`DB_URL` 연결 후 `insertMember` 실행 확인 필요)
- [ ] **React 미연결**: `account-service.js`의 `register`가 localStorage에 저장

### 남은 작업

1. React 연동 시 요청 이름·값 맞추기 (현재 화면과 다름)
   - `username`→`loginId` · `birth`→`birthDate` · `confirm`→`passwordConfirm` · `terms/privacy/financial`→`termsAgreed/privacyAgreed/financeAgreed`
   - 성별 `남성/여성` → `M/F` 변환
   - `consentVersions`의 `termsVersion` · `privacyVersion` · `financeVersion`을 payload에 포함 (빈 문자열이면 서버 400)
2. 이메일 인증 API 구현 후 `EMAIL_VERIFIED_YN`을 인증 결과로 저장 (`MemberService` L30 TODO)
3. `MEMBER.DATA_ORIGIN` 허용 값 팀 확인 (`MemberService` L32 TODO · 임시 `INPUT`)
4. `MEMBER_ID` 시퀀스 확보 후 `selectKey`의 `MAX + 1` 교체 (`MemberMapper.xml` L25 TODO) — [decisions/0003](decisions/0003-member-id-numbering.md)
5. 아이디·이메일 중복확인 API(화면의 `checkUsername` · `checkEmail` 대체) 필요 여부 확인
6. `MEMBER.NAME`: 화면은 아이디를 이름으로 쓰고 서버는 저장하지 않음 (NULL) — 기능명세서 기준 확인 필요
7. 마케팅 동의: 화면에는 있으나 ERD에 컬럼 없음 — 확인 필요
8. `docs/api-rules.md`가 저장소에 없음 — AGENTS.md가 참조하므로 추가 필요 여부 확인
9. `db/Schema.sql`은 ERDCloud 내보내기라 백틱(`` ` ``)·`COMMENT` 구문이 있어 Oracle에서 그대로 실행되지 않음 — 실제 DDL 확인 필요
