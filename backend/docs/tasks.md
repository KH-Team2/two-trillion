# 남은 작업

## 팀 확인 필요

### expense Controller가 아직 X-Member-Id 헤더로 회원번호를 받음 (2026-11-20 발견 · expense 담당 확인 필요)

- 11/17에 회원번호를 `X-Member-Id` 헤더 대신 로그인 토큰(`@AuthenticationPrincipal Long memberId`)에서 꺼내기로 바꿨고, 화면(`api-client.js`)도 이 헤더를 더 이상 보내지 않는다.
- 그런데 `expense/controller/CardUploadController` · `ExpenseController`는 아직 `@RequestHeader("X-Member-Id") long memberId`로 받는다 (엔드포인트 5개).
- 결과: 실제 화면에서 카드 업로드 · 업로드 이력 · 거래 목록 · 합계 · 업로드 삭제가 헤더 누락으로 항상 400.
  `./mvnw -B verify`도 `CardUploadControllerTest` · `ExpenseControllerTest`의 `회원번호_헤더가_없으면_401`이 실패한다 (401 기대 · 400 응답 · 11/17에 공통 처리기가 헤더 누락을 400으로 바꿈).
- 고칠 방법: 소득 Controller(`IncomeController`)처럼 `@AuthenticationPrincipal Long memberId`로 바꾸고, 테스트도 `X-Member-Id` 헤더 대신 로그인 정보로 요청하게 맞춘다.
- 회원 담당 브랜치(feature/member)에서는 다른 도메인이라 고치지 않고 기록만 남김.

## 회원 (member)

### MEMBER_ID 발급 방식: `MAX + 1` → `SEQ_MEMBER` 전환 검토

- 지금: `MemberMapper.xml`의 `insertMember`가 `SELECT NVL(MAX(MEMBER_ID), 0) + 1 FROM MEMBER`로 번호를 정한 뒤 INSERT 한다.
- 문제: 두 사람이 동시에 가입하면 둘 다 같은 최대값을 읽어 같은 번호로 INSERT → 한 명은 PK 중복으로 실패한다.
  지금은 `DuplicateKeyException`을 잡아 「잠시 후 다시 시도해 주세요」로 안내만 한다 (`MemberService.signup`).
- `SEQ_MEMBER`: Oracle이 번호를 하나씩 나눠 줘서 동시에 가입해도 겹치지 않는다.
  가입이 실패하면 받은 번호가 버려져 번호에 빈칸이 생길 수 있다 (문제 아님). 다른 테이블(INCOME · CARD_UPLOAD · PREDICTION 등)은 이미 시퀀스를 쓴다.
- 확인할 것: `database/01_schema.sql`에는 `SEQ_MEMBER`(START WITH 1)가 있는데 `docs/history.md`에는 "시퀀스가 없어 MAX + 1 임시 사용"으로 적혀 있다.
  공용 DB에서 아래 두 값을 비교한다. 시퀀스 값이 더 작으면 가상 회원 번호와 부딪히므로 DB 담당자에게 시작 번호 조정을 요청한다 (노션 「요청·전달」).

  ```sql
  SELECT MAX(MEMBER_ID) FROM MEMBER;
  SELECT LAST_NUMBER FROM USER_SEQUENCES WHERE SEQUENCE_NAME = 'SEQ_MEMBER';
  ```

- 바꿀 곳: `MemberMapper.xml`의 `selectKey` 한 줄 → `SELECT SEQ_MEMBER.NEXTVAL FROM DUAL`
- 영향: 번호를 만드는 방식만 바뀐다. 로그인(JWT에 MEMBER_ID 넣기) · `@AuthenticationPrincipal` · 이메일 인증은 바꿀 것이 없다.

### 이메일 인증 방식

- ERD에 가입 전 인증 테이블이 없어 인증 진행 상태(번호표 · 인증번호 해시 · 만료 시각 · 실패 횟수)를 서버 메모리에 보관한다 (`EmailVerificationService`).
- 서버를 다시 켜면 진행 중인 인증은 사라진다 (다시 인증하면 됨). 서버를 여러 대 띄우면 동작하지 않는다.
- 메일 발송: `member/client/EmailSender` (MAIL_MODE=log 기본 · smtp 실제 발송) · 2026-11-20 구조 추가
- 남은 작업:
  - 팀 메일 계정(Gmail 앱 비밀번호 등) 정하기 → `.env`에 `MAIL_MODE=smtp` · `MAIL_USERNAME` · `MAIL_PASSWORD`
  - 실제 메일 수신 확인 후 서버 `EMAIL_VERIFICATION_REQUIRED=true` · 화면 `frontend/.env`의 `VITE_EMAIL_VERIFICATION_REQUIRED=true`
  - (선택 · common 수정 필요) 503 `MAIL_UNAVAILABLE`을 화면에도 그 코드로 보내려면 `GlobalExceptionHandler`가 `CALC_UNAVAILABLE`처럼 따로 처리해야 함. 지금은 `SERVER_ERROR` + `errorId`로 나감

### 아이디 찾기 · 비밀번호 재설정 남은 작업 (F-01-03 · 2026-11-21)

- 팀 API 명세에 주소 3개 추가 (`/api/auth/login-id-lookups` · `/api/auth/password-resets` · `/api/auth/password-resets/completion` · `api-rules.md` 7번 표)
- ~~공용 DB에서 확인: `SEQ_PASSWORD_RESET` · `PASSWORD_RESET` 행 · `MEMBER.PASSWORD` 변경~~ → 2026-11-21 확인 완료
- 이메일 인증 조건: `EMAIL_VERIFICATION_REQUIRED=false`(기본)인 동안은 `EMAIL_VERIFIED_YN = 'N'` 회원도 재설정 가능 · 운영에서는 true로 켜야 명세(인증 회원만)와 같아짐
  - 켜기 전에 가입한 회원은 계속 'N' → 켜면 그 회원들은 재설정 불가 (DB 값 변경이 필요하면 DB 담당자와 결정)
- 재발송 제한 없음: `POST /api/auth/password-resets`도 아래 「이메일 인증번호 재발송 제한」과 같은 문제 (남의 이메일로 메일 반복 발송) → 같이 처리
- 아이디 찾기는 명세대로 이메일만 맞으면 가린 아이디를 보여 준다 → 이메일로 가입 여부를 알 수 있음 (명세의 「가입 정보를 찾을 수 없어요」 문구가 이를 전제함 · 필요하면 팀에서 재검토)
- 재설정 후 로그인 잠금(`LOCKED_UNTIL`)은 풀지 않는다 (명세에 없음 · 필요하면 `resetLoginFail` 호출 추가)

### 회원정보 · 자격 정보 수정 남은 작업 (F-01-04 · 2026-11-21)

- 팀 API 명세에 주소 3개 추가 (`GET · PUT /api/members/me` · `PUT /api/members/me/password` · `api-rules.md` 7번 표)
- 공용 DB · 브라우저에서 확인: 조회 → 저장 → `MEMBER`의 5칸 · `UPDATED_AT` 변경 · `ROLE` · `STATUS` 그대로 → 다시 계산하면 혜택 진단에 반영
- 자격 정보 입력이 두 곳: 마이페이지(서버 `MEMBER` 저장 · 계산에 쓰임)와 연말정산 입력 5단계(입사일 · 군 복무 · 회사 규모 · 무주택 · 브라우저에만 저장)
  → 두 값이 다를 수 있음 · 입력 화면을 마이페이지 값으로 채울지 / 입력 화면에서 빼고 마이페이지로 안내할지 팀 결정 필요 (tax 화면 담당)
- 비밀번호를 바꿔도 이미 발급된 토큰은 만료(2시간)까지 유효 (로그아웃 목록은 지금 토큰만 · 필요하면 변경 후 다시 로그인 요구)
- 현재 비밀번호 틀림 횟수 제한 없음 (로그인한 토큰이 있어야 부를 수 있음 · 필요하면 로그인 잠금과 같이 처리)

### 회원 탈퇴 남은 작업 (F-01-05 · 2026-11-21)

- 팀 API 명세에 주소 추가 (`POST /api/members/me/withdrawal` · `api-rules.md` 7번 표)
- **30일 지난 회원 식별 불가 처리**: 97_데이터정리.sql 2-2)를 하루 한 번 실행 (명세 v3.8) · 저장소에 파일이 없어 내용 확인 필요
  - DB 담당자가 실행할지 / Spring `@Scheduled`로 매일 돌릴지 팀 결정 필요 (스케줄러로 하면 2-2 SQL 을 받아 `MemberMapper.xml`에 옮김)
- **관리자 탈퇴 (아직 고려하지 않음 · 기록만)**: 지금은 관리자도 탈퇴할 수 있고 탈퇴하면 `ROLE=USER`가 된다.
  마지막 관리자가 탈퇴하면 관리자가 한 명도 남지 않는다 (브라우저 체험은 막고 있음 · 명세에 없음) → 막을지 팀 결정 필요
- **다른 토큰은 만료까지 유효**: 탈퇴 때 무효로 하는 건 지금 쓰는 토큰만. 다른 기기 · 브라우저에서 받은 토큰은 만료(최대 2시간)까지
  `/api/**` 를 부를 수 있다 (JWT 필터가 DB 의 `STATUS`를 보지 않음 · 회원정보 조회 · 수정은 `STATUS='ACTIVE'` 조건이라 404).
  막으려면 `common/` JWT 필터에서 STATUS 확인이 필요 → common 수정이라 승인 필요 (비밀번호 변경의 같은 문제와 같이 처리)
- ~~공용 DB 에서 API 확인: 탈퇴 · 다시 로그인 401 `WITHDRAWN_MEMBER` · 같은 아이디 · 이메일 가입 거절~~ → 2026-11-21 확인 완료 (테스트 회원 1005)
- 남은 확인: `MEMBER` 1005 행의 `WITHDRAWN_AT` · `ROLE` SQL 조회 · 브라우저에서 탈퇴 → 로그인 화면 이동
- 개발 중 테스트 계정: 탈퇴한 아이디 · 이메일은 식별 불가 처리 전까지 다시 쓸 수 없음 → 테스트마다 새 아이디 · 이메일 사용 (Gmail 은 `이름+w1@gmail.com` 처럼 `+` 뒤만 바꾸면 같은 메일함)

### 이메일 인증번호 재발송 제한 (메일 발송을 붙일 때 같이)

- 지금은 `POST /api/auth/email-verifications`를 몇 번이든 부를 수 있다. 메일이 실제로 나가지 않아 문제가 없지만,
  발송을 붙이면 남의 이메일로 메일을 계속 보내게 만들 수 있다 (메일 폭탄 · 발송 계정 차단 위험).
- 제안: 같은 이메일은 60초 안에 다시 보낼 수 없게 막는다 (예: 429 또는 400 · 「잠시 후 다시 요청해 주세요」).
  필요하면 같은 이메일 하루 발송 횟수 제한도 검토한다.
- 기존 인증 상태와 같이 서버 메모리에 「이메일 → 마지막 발송 시각」을 둔다.
