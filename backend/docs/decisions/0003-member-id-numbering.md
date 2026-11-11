# 0003. 회원가입 저장 규칙 (MEMBER_ID 번호 · 서버 지정 값 · 정규화)

- 날짜: 2026-11-08 · 상태: 임시 적용 (확인 필요 항목 있음)
- 관련 코드: `member/service/MemberService.java` · `resources/mapper/member/MemberMapper.xml` (`insertMember`)

## 결정

1. **MEMBER_ID**: `db/Schema.sql`에 시퀀스가 없어 `selectKey`(BEFORE)로 `NVL(MAX(MEMBER_ID), 0) + 1`을 쓴다. 동시 가입으로 PK가 겹치면 `DuplicateKeyException` → 409 `DUPLICATE_MEMBER`("잠시 후 다시 시도해 주세요.").
2. **정규화**: `LOGIN_ID` · `EMAIL`은 `trim` 후 소문자로 저장하고, 중복 확인도 소문자 값으로 한다. 탈퇴 회원도 중복 대상에 포함한다.
3. **서버가 정하는 값** (요청으로 바꿀 수 없음)
   - `TERMS_AGREED_AT` · `PRIVACY_AGREED_AT` · `FINANCE_AGREED_AT` · `UPDATED_AT` = `SYSDATE`
   - `EMAIL_VERIFIED_YN` = `'N'` (이메일 인증 미구현)
   - `DATA_ORIGIN` = `'INPUT'`
4. **화면이 보내는 값**: 동의서 버전(`termsVersion` · `privacyVersion` · `financeVersion`)은 화면의 `VITE_*_VERSION` 값을 받는다 (`TERMS_VERSION` 등은 DB 기본값 없음 · 필수).
5. **INSERT에서 빼는 칸**: 기본값이 있는 `MILITARY_MONTHS` · `HOMELESS_HEAD_YN` · `ROLE` · `STATUS` · `CREATED_AT` · `LOGIN_FAIL_COUNT` (AGENTS.md 규칙).
6. **가입 나이**: 한국 시간(Asia/Seoul) 기준 만 14세 이상.

## 이유

- ERD에 시퀀스·자동 번호가 정의되지 않아 우선 동작 가능한 방식으로 둔다.
- 대소문자만 다른 아이디·이메일 중복 가입을 막는다.
- 권한 · 상태 · 동의 시각을 요청으로 조작하지 못하게 한다.

## 확인 필요

- `MEMBER_ID` 시퀀스(또는 IDENTITY) 생성 여부 → 생기면 `selectKey` 교체 (DB 변경은 노션 「요청·전달」로 요청)
- `MEMBER.DATA_ORIGIN` 허용 값 (`INPUT` 임시)
- 이메일 인증 방식 확정 후 `EMAIL_VERIFIED_YN` 저장 방식
