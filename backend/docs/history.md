# 작업 이력

최신 작업이 맨 위에 오도록 기록한다.

### [2026-11-26] 관리자 세법 기준값 관리 (F-19 · F-08-01 · TT-156 · TT-157)
- **작업 내용** (`feature/admin`의 홍은님 F-19 코드를 바탕으로 고침)
  - `GET /api/admin/tax-criteria?taxYear=`: 연도별 공제 항목 · 세율/공제 구간 · 혜택 조건 + 연도 목록(`years`) · 마지막 수정(날짜 · 관리자 번호) · 카드 분류 이름
  - `PUT /api/admin/tax-criteria`: 값 칸만 수정 (연도 · 코드 · 종류 · 분류 · 결제수단 · 구간 종류는 그대로 · 계산 서버가 TAX_YEAR + TYPE_CODE로 찾아서) · `UPDATED_BY = 관리자 번호` · `UPDATED_AT = SYSDATE` 같이 갱신 → 이력은 v4.4 트리거가 `CRITERIA_HISTORY`에 자동 기록 (PR #57 안내 · 이력 INSERT 코드 없음)
  - `POST /api/admin/tax-criteria`(같은 코드로 구간 추가) · `POST /api/admin/tax-criteria/years`(연도 통째로 복사) · `GET /api/admin/tax-criteria/history`(한 행 이력)
  - 원래 코드 오류 수정: INSERT에 번호 빠짐(ORA-01400) → `SEQ_*.NEXTVAL` · 없는 칸 `HOMELESS_HOUSEHOLD_YN` → `HOMELESS_HEAD_REQ_YN` · 응답 Item에 setter 없어 MyBatis가 못 담음 → `@Setter` · `IllegalArgumentException`(500) → 공통 예외
  - DB 규칙(구간 끝 > 시작 · 최소 나이 ≤ 최대 나이 · 카드 행 분류 필수)을 먼저 검사해 400 · 값 제한 위반(ORA-02290) 400 「입력값을 확인해 주세요」 · 중복(ORA-00001) 409 · 근거 필수
  - 수정 · 추가 · 연도 복사를 `ACCESS_LOG`에 기록 (명세 ④)
  - 화면: `tax-criteria-service.js` 신규 · `AdminTools.jsx` `CriteriaManagement`(연도 선택 · 세 표 · 수정 · 구간 추가 · 연도 추가 · 이력) · `AdminPage.jsx` 「공제 기준값」 탭 연결
  - 테스트: `AdminTaxCriteriaServiceTest` 10건 · `tests/tax-criteria-service.mjs`
- **변경 사유**: 명세 F-08-01 · DB 담당 안내(PR #57: UPDATE에 `UPDATED_BY` · `UPDATED_AT`을 같이 넣으면 트리거가 이력을 남김)
- **리뷰에서 정할 것**: 근거(`sourceNote`)를 꼭 받을지 · 혜택 조건도 행 추가를 열어 둘지(화면에서는 막아 둠) · 이력에 관리자 아이디를 보일지
- **검증 결과**
  - `mvnw verify` 344건 통과 · `npm test` 123건 통과 · Prettier 통과
  - Talend: 기준값 조회 200 · 없는 연도 400 · 값 수정 200 · 수정 후 CRITERIA_HISTORY에 바뀐 칸 · 관리자 번호 기록 · 같은 값 재수정 시 이력 안 늘어남 · 구간 끝 ≤ 시작 400 · 근거 비움 400 · 없는 번호 404 · 이력 조회 200 · 연도 복사 200 · 같은 연도 다시 복사 409 · 일반 회원 403 · ACCESS_LOG 수정/복사 기록
  - 화면: 연도 선택 · 공제 항목/구간/혜택 표 · 수정 · 구간 추가 · 연도 추가 · 이력 · 되돌리기 · 모달 · 필수 칸 표시

### [2026-11-26] ERD v4.5 · 챗봇 대화 테이블 CHAT_SESSION · CHAT_MESSAGE 삭제 (TT-186)
- **작업 내용**:
  - `database/01_schema.sql`: `CHAT_SESSION` · `CHAT_MESSAGE` 테이블 · 인덱스 3개 · 시퀀스 2개 · COMMENT 삭제 (테이블 24 → 22 · 칸 237 → 226 · CHECK 54 → 53 · 인덱스 30 → 27 · 시퀀스 22 → 20)
  - `database/00_초기화.sql`: 두 테이블 · 시퀀스 삭제 줄은 그대로 두고 주석만 추가 (v4.4 이하로 만든 계정을 비울 때 필요)
  - `database/README.md`: v4.5 안내 추가 · 테이블 수 · zip 이름 · ERD 버전 표기
- **변경 사유**: 팀 노션 DB 변경 요청(김동석 · 11/26) · 절세 가이드는 `RECOMMENDATION`만 쓰고 두 테이블을 쓰는 코드가 없음 (팀 깃 전체 브랜치에서 확인)
- **검증 결과**: 백엔드 · 계산 서버 · 화면 코드 변경 없음 · 다른 테이블이 두 테이블을 가리키는 FK 없음 · 이미 만든 계정(공용 DB)은 DB 담당이 `75_v4.4에서_v4.5로.sql`로 적용

### [2026-11-25] 구조도에 알림 · 상담 채팅 추가 · ERD 버전 표기 v4.5 (TT-185)
- **작업 내용**:
  - `README.md`(루트) 구조도: 백엔드 `notification/` · `consult/` 패키지 · 매퍼 XML · 테스트 폴더 추가 · 화면 `api/`에 계산 · 혜택 진단 · 시뮬레이션 · 통계 · 알림 · 상담 호출 파일 9개 · `pages/support/ConsultChat.jsx` · 화면 테스트 5개 추가
  - `backend/README.md` 구조도: `notification/` · `consult/` 패키지 · 매퍼 XML · 테스트 폴더 추가
  - `backend/AGENTS.md` 폴더 구조: `notification/` · `consult/` 두 줄 추가
  - ERD 버전 표기 v4.2 → v4.4: `backend/README.md` · `backend/AGENTS.md` · `calc/README.md`
  - [추가 11/26] ERD 버전 표기 v4.4 → v4.5 (PR #66 · 챗봇 대화 테이블 2개 삭제 · 공용 DB는 11/26 v4.5 적용): 같은 3개 파일 · 루트 `README.md` 구조도 `01_schema.sql` 테이블 21개 → 22개
  - 기능명세서 v1.14 → v1.15 (새 구글 시트 주소): `backend/README.md` · `calc/README.md` 표기 · `backend/README.md` · `backend/AGENTS.md` · `calc/AGENTS.md` 링크
  - `frontend/docs/API_INTEGRATION.md`: 실시간 알림 · 관리자 상담 채팅 연결 흐름 추가 · 「아직 연결 안 된 부분」에서 해결된 카드 사용액 2건 정리
- **변경 사유**: PR #46 · #51 · #58로 들어간 알림 · 상담 채팅과 ERD v4.3 · v4.4가 구조도 · 버전 표기에 빠져 있었음
- **검증 결과**: 문서만 바꿈 (코드 변경 없음) · 폴더 · 파일 이름은 develop(e19fb07)의 실제 경로와 대조

### [2026-11-25] API 규칙 문서 7번 표에 알림 · 상담 API 추가 (TT-184)
- **작업 내용**: `backend/docs/api-rules.md` 7번 표에 알림 3개(`/api/notifications/stream` · `/api/notifications` · `/api/notifications/read`) · 상담 3개(`/api/consult/rooms` · `/api/consult/rooms/{roomId}/messages` · `/api/admin/consult/rooms`) 행 추가 · 표 아래에 WebSocket(`/ws` · `/sub` · `/pub` · `/user/queue/errors`) 주소 규칙 한 줄
- **변경 사유**: 계산 · 회원 API는 표에 있는데 PR #51 · #58로 들어간 알림 · 상담 API가 빠져 있었음
- **검증 결과**: 문서만 바꿈 (코드 변경 없음) · 내용은 develop의 `NotificationController` · `ConsultController` · `ConsultService` · `WebSocketConfig`와 대조


### [2026-11-25] 허용 화면 주소(CORS · WebSocket)를 설정 파일로 분리 (TT-182 · PR #59)
- **작업 내용**:
  - `application.yml`: `cors.allowed-origins: ${ALLOWED_ORIGINS:http://localhost:5173}` 추가
  - `common/config/CorsConfig` · `consult/config/WebSocketConfig`: 코드에 고정한 `http://localhost:5173` → 설정값 (쉼표로 여러 개)
  - `CorsConfigAllowedOriginsTest` 3개: 설정에 넣은 주소 허용 · 쉼표 뒤 두 번째 주소 허용(공백 무시) · 없는 주소 거절
  - `backend/README.md` 4.2 추가 환경변수에 `ALLOWED_ORIGINS` · `frontend/vite.config.js` 주석
- **변경 사유**: 허용 주소가 코드에 고정돼 있어 배포하면 운영 주소에서 API · 알림 · 채팅이 막힘 · 개발은 기본값 그대로, 배포는 환경변수만 바꾸면 되게 (강사님 확인 · 팀 공유)
- **검증 결과**: `mvnw.cmd -B clean verify` 248개 통과 · 기존 `CorsConfigTest` · `SecurityConfigTest` 그대로 통과


### [2026-11-25] 관리자 상담 채팅 (WebSocket · STOMP) · 종 알림 (TT-153 · TT-154 · PR #58)
- **작업 내용**:
  - `pom.xml`: `spring-boot-starter-websocket` 추가
  - `consult/config/WebSocketConfig`: 연결 `/ws` · 보내기 `/pub/...` → `@MessageMapping` 컨트롤러 · 받기 `/sub/...` → 구독자 모두 · 나에게만 `/user/queue/...` · Spring 기본 브로커(서버 메모리)
  - `consult/config/StompAuthChannelInterceptor`: CONNECT 헤더 토큰 확인 → 이 연결의 사용자 = 회원번호 · SUBSCRIBE `/sub/consult/{roomId}`는 방 주인 회원 · 관리자만 · SEND는 `/pub/consult/{숫자}`만
  - `consult/controller/ConsultController`: `POST /api/consult/rooms`(내 방 · 있으면 그 방) · `GET /api/consult/rooms/{roomId}/messages?beforeId=`(30개씩) · `GET /api/admin/consult/rooms`(최근 대화 순 최대 100개 · 아이디 일부 가림 `test****`)
  - `consult/controller/ConsultMessageController`: SEND → 저장 → `/sub/consult/{roomId}` 방송 · 업무 오류는 `/user/queue/errors`로 보낸 사람에게만
  - `consult/service/ConsultService` · `ConsultUser` · `consult/mapper/ConsultMapper`(+ XML) · `consult/dto` 4개: 회원당 방 1개(UNIQUE 충돌이면 먼저 만든 방) · 메시지 1~1000자 · 남의 방 404
  - `consult/service/ConsultNotifier`: 받는 사람이 그 방을 구독 중이 아니면 종 알림(CHAT) · 회원 → 활동 중인 관리자 전원 · 관리자 → 방 주인 · 문구에 메시지 내용 없음
  - `notification/service/NotificationService` · `NotificationMapper`: `TYPE_CHAT` · `notifyMemberUnlessUnread`(안 읽은 CHAT 알림이 있으면 안 만듦) · `countUnreadByType` · SSE 연결 회원당 최대 5개(넘으면 가장 오래된 연결 닫음)
  - 화면: `ConsultChat.jsx`(+ CSS) · `consult-socket.js`(`@stomp/stompjs` 7.3.0 · 끊기면 5초 뒤 다시 연결) · `consult-service.js` · `SupportPage.jsx` 「상담 채팅」 탭 · `vite.config.js` `/ws` 프록시 · `tests/consult-chat.mjs`
  - 리뷰 반영: SEND 주소 제한 · SSE 연결 5개 제한(`4fa053a`) · 구독 거절 문구 「내 채팅방이 아니에요」(`5a6cbf8`)
  - **알려진 한계**:
    - 서버 메모리 안 기본 브로커 → 서버 1대 기준 (SSE 알림과 같음 · 여러 대면 외부 브로커 필요)
    - 토큰은 CONNECT 때만 확인 → 연결 중 토큰이 만료돼도 그 연결은 유지
    - 메시지 보내기 횟수 제한 없음
    - 관리자 방 목록은 최근 대화 순 100개까지 → 오래 조용한 방을 찾는 페이지 넘기기 · 검색은 없음 (대화 중인 방은 새 메시지가 오면 맨 위로 · 알림도 감)
    - 허용 화면 주소(Origin)가 `localhost:5173`으로 고정 → 설정 파일 분리는 PR #59 (TT-182)
    - 사진 · 파일 첨부 없음 (민감 정보 사진 보관 책임 · CONTENT 1000자 칸)
- **변경 사유**: 2차 미션(실시간 알림 · 상담 채팅) · 회원이 고객센터에서 관리자와 1:1 실시간 상담 · 양쪽이 주고받아서 SSE(서버 → 화면 한 방향) 대신 WebSocket + STOMP · DB는 PR #56의 `CONSULT_ROOM` · `CONSULT_MESSAGE` 그대로 (DB 변경 없음)
- **검증 결과**:
  - `mvnw.cmd -B clean verify` 245개 통과 · `npm test` 통과
  - 실사용(공용 DB · 회원 test1234 ↔ 관리자 admin): 실시간 주고받기 · 방을 안 보고 있을 때 종 알림 · 안 읽은 알림 중복 안 쌓임 · 방을 보고 있으면 알림 없음 · 관리자 답장 시 회원 알림
  - 
### [2026-11-24] 재설정 코드 발송 횟수 제한 · 입사일 미래 날짜 막기 (리뷰 의견 반영)
- **작업 내용**:
  - `PasswordResetService.sendCode`: 최근 60분 동안 받은 코드가 5개 이상이면 429 `RESET_SEND_LIMIT` 「인증 코드를 너무 많이 요청했어요. 1시간 뒤에 다시 시도해 주세요」 · 메일 · 저장 안 함
  - `PasswordResetMapper`(+ XML): `countRecentResets` 추가 (기존 `PASSWORD_RESET.CREATED_AT` · 인덱스 `IX_PASSWORD_RESET_MEMBER` 사용 · 테이블 변경 없음)
  - `MemberProfileRequestDTO.hireDate`: `@PastOrPresent` → 오늘 이후 날짜면 400 `VALIDATION` 「입사일은 오늘 이후로 입력할 수 없어요」 (오늘은 허용)
  - 화면: `member-api.js`에 `todayDate()`(지역 시각 기준 오늘) · `profileFormError`에 같은 검사 추가 · `ProfileDetails.jsx` 입사일 칸에 `max` 추가
  - 테스트: `PasswordResetServiceTest`(이미 4번 받았으면 5번째 발송 · 이미 5번이면 429) · `MemberProfileControllerTest`(내일 400 · 오늘 200) · `member-api.test.js`
- **변경 사유**:
  - 코드 1개당 5번만 틀릴 수 있어도 새 코드를 끝없이 받으면 계속 맞혀 볼 수 있었음 (메일도 무제한 발송)
  - 입사일에 아직 오지 않은 날짜가 저장돼 계산 서버 혜택 판정에 그대로 쓰일 수 있었음
- **검증 결과**:
  - `mvnw spotless:check` 통과 · `mvnw -B verify` 통과 (feature/member 266건 · feature/backend-common 277건) (`CHATBOT_API_TOKEN` · `CALC_API_TOKEN`에 임시 값을 넣고 실행 · 값이 없으면 `contextLoads` 1건 실패 — 수정 전에도 같음)
  - 화면 `npm test` 통과
  - 미검증: 실제 Oracle DB에서 `countRecentResets` 쿼리 실행 (Mock 테스트만)

### [2026-11-24] IP 기준 로그인 제한 (D-15 · D-16 · F-09-01)
- **작업 내용**
  - D-15: IP 차단 목록(관리자가 IP 차단) 기능은 만들지 않음 → `BLOCKED_IP` 테이블 추가 없음
  - D-16: 한 IP가 1분에 50번 이상 로그인 실패 → 12시간 동안 그 IP 로그인 429 `LOGIN_IP_BLOCKED`
  - `AccessLogMapper.countIpBlockTriggers`: ACCESS_LOG 실패 기록을 `COUNT(*) OVER (RANGE 1분)`으로 세서 최근 12시간 안에 50번이 된 적 있는지 확인 (인덱스 `IX_ACCESS_LOG_IP`)
  - `LoginService`: 회원 조회 전에 IP 확인 · 막힌 동안의 시도는 `DENIED` 기록(FAIL로 남기면 다시 세어져 차단이 늘어나서 · 회원 잠금처럼 처음 막힌 때부터 정확히 12시간) · IP 없음 · 확인 중 DB 오류면 막지 않음
  - 테스트: `LoginServiceTest` 3건 추가
- **변경 사유**: 기능명세서 결정 필요 항목 D-15(IP 차단 기능 없음) · D-16(1분 50번 → 12시간) 확정
- **검증 결과**: `mvnw verify` 325건 통과 · SCREEN 조건 추가 후 Oracle에서 `countIpBlockTriggers` 실행 확인 · SQL로 로그인 실패 기록 50건을 넣은 뒤 Talend 로그인 시도 → 429 `LOGIN_IP_BLOCKED` · `DENIED` 기록 · 차단 판단 숫자 그대로(차단 시간 안 늘어남) · 회원 실패 횟수 그대로 · 실패 시각을 13시간 전으로 바꾸면 정상 로그인 확인

### [2026-11-23] 관리자 회원 관리 · 권한 부여/해제 (F-20 · F-01-06 · F-08-02 ① · TT-156 · TT-157)
- **작업 내용** (`feature/admin-2`의 홍은님 코드를 바탕으로 고침)
  - `GET /api/admin/members`: 이메일 일부를 가린 목록 + 계정 상태 · 권한 · 가입일 · 검색(이메일 일부 · 권한 · 상태) · page 1부터 · size 1~100 · 응답 `ApiResponse.ok(목록, PageMeta)` · 조회한 것도 `ACCESS_LOG`에 기록
  - `PUT /api/admin/members/{id}/role` 본문 `{"role":"ADMIN"|"USER"}`: 관리자 번호를 `@AuthenticationPrincipal`(토큰)에서 · 성공하면 `ACCESS_LOG`에 `/api/admin/members/{id}/role` 기록
  - 거절(409): 자기 자신 해제 `SELF_DEMOTE`(D-06) · 성별 없는 관리자 → 일반 회원 `GENDER_REQUIRED`(10/28) · 이미 그 권한 `SAME_ROLE` · 탈퇴 회원 `WITHDRAWN_MEMBER` · 없는 회원 404 · role 값이 틀리면 400
  - 이름(NAME) 마스킹 · 검색 삭제 (v3.5부터 이름 안 받음) · 이메일 마스킹은 @ 앞이 2글자 이하여도 가려지게
  - 화면: `admin-member-service.js` 신규 · `AdminTools.jsx`에 서버 연결 `MemberManagement` 추가 · `AdminPage.jsx`가 그쪽을 쓰도록 (11/25: develop 체험판 삭제(#64) 뒤 「준비 중」이던 회원 관리 탭을 서버 API로 연결)
  - 테스트: `AdminMemberServiceTest` 9건 · `tests/admin-member-service.mjs`
- **변경 사유**: 원래 코드는 `@RequestAttribute("currentAdminId")`를 넣어 주는 곳이 없어 권한 변경이 항상 400 · 실패 응답이 팀 공통 모양이 아님 · 명세의 「계정 상태 · 권한 표시」 「변경 작업 기록」이 빠져 있었음
- **리뷰에서 정할 것**: 탈퇴 회원 권한 변경 막기(명세에 없음) · 같은 권한 요청을 409로 할지 · 목록에 아이디(LOGIN_ID)를 보일지
- **검증 결과**: `mvnw verify` 334건 통과 · `npm test` · Prettier 통과 · Talend로 목록 200 · 이메일 마스킹 · role/size 값 오류 400 · 일반 회원 403 · 권한 부여 200 · 같은 권한 409(SAME_ROLE) · 자기 자신 해제 409(SELF_DEMOTE) · 없는 회원 404 · ACCESS_LOG 조회/변경 기록 확인 · 화면 검색 · 권한 부여/해제 · 쪽 이동 확인 · 탈퇴 회원은 상태 「탈퇴」 표시 · 권한 버튼 꺼짐 확인 (develop 탈퇴 기능 #60 병합 후)

### [2026-11-23] 관리자 접근 기록 조회 리뷰 반영 · 화면 연결 (F-21 · F-08-02 ③ · TT-156 · TT-157)
- **작업 내용**
  - `GET /api/admin/access-logs`: 날짜를 `LocalDate`로 받기(형식 틀리면 400) · page 1부터(`@Min(1)`) · size `@Min(1) @Max(100)` · 응답 `ApiResponse.ok(목록, PageMeta)`
  - `AdminAccessLogMapper.xml`: `LEFT JOIN MEMBER`(회원 없는 실패 기록도 보이게) · `COUNT(...) OVER (PARTITION BY IP)`로 같은 IP 실패 수 · 목록 · 개수가 검색 조건 `<sql>` 공유
  - `AdminAccessLogService.recordView`: 관리자가 목록을 본 것도 `ACCESS_LOG`에 기록 (번호 겹치면 한 번 더 · 실패해도 화면은 그대로 · `LoginService`와 같은 규칙)
  - 화면: `access-log-service.js` 신규 · `AdminTools.jsx` 접근 기록 탭을 체험 데이터 → 서버 API · `api-client.js`에 `withMeta` 옵션(기본 꺼짐)
  - 테스트: `AdminAccessLogServiceTest` 6건 · `tests/access-log-service.mjs`
- **변경 사유**: PR #45 리뷰 4개(size 최대값 · 날짜 형식 · 전체 개수 · 회원 없는 기록) · 명세 F-08-02 비고 「관리자가 목록을 조회한 것도 ACCESS_LOG에 기록」
- **검증 결과**: `mvnw verify` 213건 통과 · `npm test` · Prettier 통과 · Talend로 접근 기록 조회 200 확인 · 없는 아이디 실패 기록(memberId null) 표시 · 같은 IP 실패 수 · 연속 실패 4회 표시 · 접근 기록 탭을 열 때마다 /api/admin/access-logs 기록 추가 확인 · (11/25) develop 체험판 삭제(#64) 병합 후 접근 기록 탭 다시 연결

### [2026-11-23] README · .env.example 환경변수 정리 (필수 토큰 · 이메일 인증 설정)
- **작업 내용**:
  - `backend/README.md`: `CALC_API_TOKEN` · `CHATBOT_API_TOKEN`을 4.2 추가 환경변수 → 4.1 필수 환경변수로 이동 · 4.3 STS 등록 목록과 5.2 CMD 실행 안내를 필수 6개 기준으로 수정
  - `backend/README.md`: 4.2 추가 환경변수 표에 `EMAIL_DEV_CODE_ENABLED` · `EMAIL_VERIFICATION_REQUIRED` 추가 (둘 다 기본 `false`)
  - `backend/.env.example`: 「필수」 · 「기본값 있음(바꿀 때만)」 두 묶음으로 정리 · `CALC_API_TOKEN=` · `CHATBOT_API_TOKEN=`을 주석 없이 필수 묶음으로 이동 (예전에는 「기본값이 있어서 바꿀 때만」 아래에 주석으로 있었음) · 선택 묶음에 이메일 인증 설정 2개 추가
- **변경 사유**:
  - `FaqClient`(챗봇)는 항상 만들어지고 `CHATBOT_API_TOKEN`이 비면 서버 시작 때 예외 → 기본값이 없는 필수 값인데 「추가 · 기본값 있음」으로 안내돼 있었음
  - `CardParseClient`(카드 업로드)는 `CALC_MODE`와 관계없이 항상 `X-Calc-Token`으로 계산 서버를 호출 → 비어 있으면 업로드가 실패 · `CALC_MODE=http`면 `HttpCalcClient`가 서버 시작을 막음
  - `application.yml`에는 이메일 인증 설정 2개(`member.email-verification.dev-code-enabled` · `required`)가 있는데 README 4.2 · `.env.example`에 빠져 있었음 (리뷰 의견 반영)
- **검증 결과**:
  - 문서 수정만 (코드 변경 없음) · `application.yml` · `FaqClient` · `HttpCalcClient` · `CardParseClient`의 토큰 처리와 문구가 맞는지 대조
  - 이메일 인증 설정 이름 · 기본값이 `application.yml`(`EMAIL_DEV_CODE_ENABLED:false` · `EMAIL_VERIFICATION_REQUIRED:false`)과 같은지 대조

### [2026-11-23] 업로드 삭제 성공 응답 204 → 200 (팀 API 규칙)
- **작업 내용**
  - `ExpenseController.deleteUpload`: `ResponseEntity.noContent()`(204 · 본문 없음) → `ApiResponse.ok(null)`(200 · `{success: true, data: null}`)
  - `ExpenseControllerTest`: 「삭제 성공이면 204 · 본문 없음」 → 「200 · `success: true` · `data: null`」
  - 화면: `card-upload-service.js` · `ExpenseUpload.jsx` 주석만 200으로 · `api-client.js`의 204 처리는 혹시 모를 204 대비로 그대로 둠 (동작 변화 없음 · 화면은 `data`(null)를 그대로 받음)
  - `api-rules.md` 7번 표: 204 → 200 `data: null`
  - `card-upload-service.js` · `ExpenseUpload.jsx`: Prettier 줄바꿈 정리 (기존 한글 문장 줄 나눔 위치만 · 동작 변화 없음)
- **변경 사유**: `api-rules.md` 3번 「성공 응답은 모두 200 (201 · 204 안 씀 · 11/09 팀 합의)」과 어긋났음
- **검증 결과**: `mvnw clean verify` 195건 통과 · 화면에서 업로드 삭제 → Network `DELETE /api/card-uploads/{id}` 200 · 응답 `{success: true, data: null}` · 목록에서 사라짐 확인

### [2026-11-23] 알림 저장 · 목록 · 읽음 · 놓친 알림 다시 보내기 (TT-158 · TT-159 · PR #51)
- **작업 내용**:
  - `notification/mapper/NotificationMapper`(+ XML) · `dto/NotificationDTO` · `NotificationListDTO`: 저장 · 목록(최근 20개 + 안 읽은 수) · 읽음(`READ_YN` · `READ_AT` 같이) · 놓친 알림(`Last-Event-ID` 뒤 최대 50개)
  - `NotificationService`: 저장 → 번호 받기 → 그 번호로 SSE 전송 · 마감 안내는 활동 회원 전원 `INSERT … SELECT` 한 문장
  - `NotificationController`: `GET /api/notifications` · `PUT /api/notifications/read` · `/stream`에 `Last-Event-ID`
  - `CalculationService` · `DeadlineNotificationScheduler`: 새 저장 방식으로 호출 · `CalculationNotificationDTO` 삭제
  - 화면: `notification-service.js`(목록 · 읽음) · `notification-stream.js`(`Last-Event-ID`로 다시 연결) · `App.jsx`(목록 먼저 → 연결 · 배지는 서버 안 읽은 수) · `Topbar.jsx`(종 열면 읽음)
- **변경 사유**: PR #46 알림은 서버 메모리에만 있어서 접속 안 한 동안 생긴 알림이 사라지고 새로고침하면 종 목록이 비워짐 → PR #47 `NOTIFICATION` 테이블에 먼저 저장
- **검증 결과**: `mvnw clean verify` 206개 통과 · `npm test` 통과(`notification-stream.mjs` 11개) · 마감 안내 저장 SQL 활동 회원 1,004명 → 1,004행 확인 후 ROLLBACK

### [2026-11-22] 세금 용어 FAQ 챗봇 연동 및 구조 정리
- **작업 내용**
  - React FAQ 챗봇을 Spring Boot API와 연결
  - Spring Boot에 FAQ 목록·카테고리·상세·자유 질문 매칭 API 추가
  - Spring Boot에서 Python FastAPI 챗봇 서버를 호출하는 `FaqClient` 추가
  - Spring Boot → FastAPI 내부 통신에 `X-Chatbot-Token` 인증 적용
  - FastAPI에 `tax_terms.json` 기반 FAQ 목록·카테고리·상세 조회 구현
  - 자유 질문은 단순 용어 우선 매칭 후 Gemma 3를 이용해 등록된 FAQ ID로 분류하도록 구현
  - Gemma 3가 답변을 직접 생성하지 않고 분류된 FAQ의 고정 답변을 반환하도록 구성
  - 자유 질문이 LLM으로 전달되기 전에 개인정보 및 숫자 포함 여부 검사
  - 챗봇 서버 연결 또는 AI 분류 실패 시 Spring 공통 예외 처리의 `CHATBOT_UNAVAILABLE` 503 응답 적용
  - FastAPI의 API·서비스·FAQ 데이터 조회 책임을 분리
  - React FAQ API 호출을 공통 `apiRequest()` 및 `/api/...` 상대경로 방식으로 통일
- **변경 사유**
  - 세금 용어를 버튼 또는 자유 질문으로 조회할 수 있도록 FAQ 챗봇을 실제 백엔드와 연결
  - LLM이 세법 답변을 임의 생성하지 않고 검증된 FAQ만 반환하도록 제한
  - React가 Python 서버를 직접 호출하지 않도록 팀의 React → Spring Boot → Python 서버 구조와 인증 규칙을 적용
  - 챗봇 코드의 API 처리·업무 흐름·데이터 조회 책임을 분리해 유지보수성을 개선
- **검증 결과**
  - FAQ 카테고리 선택 → 용어 선택 → 상세 답변 조회 정상 동작 확인
  - 자유 질문 → FAQ 분류 → 고정 FAQ 답변 반환 정상 동작 확인

### [2026-11-21] 회원 탈퇴 (F-01-05)
- **작업 내용**:
  - API `POST /api/members/me/withdrawal` (`MemberProfileController` · 로그인 필요 · 팀 명세에 없는 제안 주소) · 본문 `{password}` → `null`
    - D-05: 비밀번호를 다시 확인한다 (토큰을 도용당해도 비밀번호를 모르면 탈퇴시킬 수 없게) · 틀림 400 `CURRENT_PASSWORD_MISMATCH` 「비밀번호가 맞지 않아요」 · 비움 400 `VALIDATION` · 없거나 이미 탈퇴 404
    - DELETE 가 아닌 이유: 행을 지우지 않고 상태만 바꾸고(30일 유예), 비밀번호를 본문으로 받아야 해서
  - `member/service/MemberWithdrawalService` (새 파일): 비밀번호 확인(BCrypt · 72바이트 초과는 틀림으로) → `withdrawMember` → 지금 토큰 로그아웃(`TokenService.logout`) · `@Transactional` · 로그는 회원번호만
  - `MemberMapper.xml` `withdrawMember`: `STATUS='WITHDRAWN'` · `WITHDRAWN_AT=SYSDATE` · `ROLE='USER'` 를 한 문장으로 (따로 바꾸면 `CK_MEMBER_WITHDRAWN` ORA-02290) · `AND STATUS='ACTIVE'` (두 번 눌러도 탈퇴일 그대로) · 아이디 · 이메일은 유지 (D-21 30일 유예)
  - `member/dto/MemberWithdrawalRequestDTO` (새 파일): `password` 하나 · `@ToString(exclude = "password")`
  - 화면: `member-api.js` `withdrawMe()` · `account-service.js` 서버 로그인 회원이면 서버 탈퇴 후 저장된 토큰 삭제 (체험 계정은 브라우저 그대로) · `AccountForms.jsx` `WithdrawAccount` 안내 문구를 서버 / 체험으로 나눔 · `ProfilePage.jsx` 완료 알림 「탈퇴했어요. 30일 동안은 같은 아이디·이메일로 다시 가입할 수 없어요.」
  - 이미 되어 있어 고치지 않은 부분: 탈퇴 회원 로그인 401 `WITHDRAWN_MEMBER` (`LoginService`) · 유예기간 중 같은 아이디 · 이메일 가입 막기 (중복확인이 탈퇴 회원도 셈) · 회원정보 조회 · 수정은 `STATUS='ACTIVE'` 만
  - 이번에 안 한 것: 30일 지난 회원 식별 불가 처리 (97_데이터정리.sql 2-2 · 하루 한 번 · 저장소에 파일 없음) · 관리자 탈퇴 제한 (`tasks.md`)
- **변경 사유**: 기능명세서 F-01-05 · D-05(비밀번호 재확인) · D-21(30일 유예) · 참고 저장소 sjlimiei26/final_project_sample(`@AuthenticationPrincipal` · `ApiResponse`) · secure-coding-lab-student(본인 조건을 SQL 에 · 식별자만 로그)
- **검증 결과**: `./mvnw spotless:check` 통과 · `./mvnw -B verify` 261건 중 259건 통과 (실패 2건은 기존 expense `회원번호_헤더가_없으면_401` · 변경 전과 같음) · 새 테스트: `MemberWithdrawalServiceTest` 6건 · `MemberProfileControllerTest` 탈퇴 4건 · 화면 `npm run format:check` · `npm test` · `npm run build` 통과
  - 공용 DB 로 API 확인 (2026-11-21 · 테스트 회원 1005 `wdtest_1121210430` · 서버를 8081 로 따로 띄우고 `dev-code-enabled=true` 실행 옵션으로 이메일 인증): 가입 · 로그인 → 틀린 비밀번호 400 `CURRENT_PASSWORD_MISMATCH` · 빈 비밀번호 400 `VALIDATION` → 맞는 비밀번호 200 (ORA-02290 없음) → 같은 토큰 401 → 다시 로그인 401 `WITHDRAWN_MEMBER` → 중복확인 아이디 · 이메일 둘 다 사용 중 → 같은 아이디 재가입 409 `DUPLICATE_LOGIN_ID` · 같은 이메일 409 `DUPLICATE_EMAIL` · 서버 로그는 `memberId=1005` 만
  - 미검증: `MEMBER` 칸을 SQL 로 직접 조회 (`WITHDRAWN_AT` · `ROLE`) · 브라우저에서 탈퇴 → 로그인 화면 이동 흐름

### [2026-11-21] 회원(member) 코드 줄바꿈 정리 (선생님 샘플 기준)
- **작업 내용**:
  - 원인: Spotless(google-java-format AOSP)는 100자를 넘는 줄을 자동으로 꺾는다 → 긴 `throw new BusinessException(...)` · 람다 안의 람다 · 긴 요청 체인이 3 ~ 5단계로 들쭉날쭉하게 꺾여 있었음
  - 방법 (final_project_sample/back 과 같은 방식 · 처음부터 100자를 넘지 않게 씀): 오류는 `badRequest(code, message)` · `unauthorized(...)` · `signupFailed()` 같은 메서드로 뺌 · 긴 식은 변수로 나눔 · 메일 본문은 텍스트 블록(`"""`) · 응답 DTO 는 `from()` 으로 만듦(`MemberProfileResponseDTO`) · 인증 기록 만료 판단은 `Verification.isExpired()` 로
  - 테스트: 오류 확인을 `catchThrowable(...)` + `assertError(...)` 두 줄로 · 로그인은 `login()` · `loginError()` 도우미 · Controller 는 `postJson(주소, 본문)` · `putJson(...)` 도우미 · 긴 요청 DTO 는 setter 로
  - 범위: `member/` main · test 만 (common · 다른 도메인은 건드리지 않음) · API · 응답 · 동작은 바뀌지 않음
  - 그대로 둔 모양: 생성자의 긴 매개변수 목록(한 줄에 하나씩) · `변수 =` 다음 줄 한 줄로 끝나는 대입 (formatter 가 일정하게 만드는 모양)
- **변경 사유**: 푸시된 코드의 줄바꿈이 읽기 어렵다는 사용자 피드백 · 참고 저장소: sjlimiei26/final_project_sample · 5_Framework · secure-coding-lab-student
- **검증 결과**: `./mvnw spotless:check` 통과 · `./mvnw -B verify` 251건 중 249건 통과 (실패 2건은 기존 expense `회원번호_헤더가_없으면_401` · 변경 전과 같음) · member 테스트 107건 통과 · member 폴더에 24칸 이상 들여 쓴 줄 0개 (JSON 텍스트 블록 제외)
  - 남은 곳 (이번에 안 고침): 다른 도메인(income · expense · prediction · statistics) 코드
- **추가 (사용자 요청)**: `common/config/SecurityConfig` 도 같은 방식으로 정리
  - 설정을 샘플처럼 ① 기본 기능 끄기 → ② 세션 안 씀 → ③ 주소 규칙 → ④ 401 · 403 응답 → ⑤ 토큰 필터 순서로 · 람다 안에 길게 늘어놓던 부분은 `stateless()` · `authorizeRules()` · `errorHandlers()` 메서드로 뺌 (주소 규칙은 한 줄에 하나)
  - 401 · 403 응답: `writeUnauthorized()` · `writeForbidden()` · `JsonMapper` 는 메서드 매개변수 대신 생성자로 받음 (`@RequiredArgsConstructor`)
  - JWT 필터: 로그인 정보 넣는 부분을 `login(TokenInfo)` 로 뺌
  - 주소 규칙 · 순서 · 응답 코드 · 문구는 그대로 · `SecurityConfigTest` 7건 통과 · 전체 251건 중 249건 통과 (기존 expense 2건만 실패 · 변경 전과 같음)

### [2026-11-21] 회원정보 · 자격 정보 수정 · 비밀번호 변경 (F-01-04)
- **작업 내용**:
  - API 3개 (`member/controller/MemberProfileController` · 로그인 필요 · 회원번호는 토큰에서 · 팀 명세에 없는 제안 주소)
    - `GET /api/members/me`: 아이디 · 가린 이메일(`ho***@example.com` · 서버에서 가림) · 생년월일 · 성별 · 군 복무 개월 · 입사일 · 회사 규모 · 무주택 세대주 · 수정일 (권한 · 상태 · 비밀번호는 응답에 없음)
    - `PUT /api/members/me`: 성별(M·F 필수) · 군 복무(0 ~ 999) · 입사일(비우면 NULL) · 회사 규모(SME·MID·LARGE · 비우면 NULL) · 무주택(Y·N 필수) 전부 저장 · `UPDATED_AT = SYSDATE` · `ROLE` · `STATUS` · 아이디 · 이메일(D-04) · 생년월일은 SQL `SET`에 없음 · 저장 후 다시 조회해 응답
    - `PUT /api/members/me/password`: 현재 비밀번호 BCrypt 확인(틀리면 400 `CURRENT_PASSWORD_MISMATCH`) → 새 비밀번호 규칙(회원가입과 같은 `MemberService.validatePassword` · 현재와 같으면 400) → BCrypt 저장
  - 군 복무 음수: DTO 검사로 400 「0 이상으로 입력해 주세요」 · DB 값 제한 위반(ORA-02290 `CK_MEMBER_MILITARY`)도 같은 문구로 바꿈 (ORA 코드는 화면에 보내지 않음)
  - 모든 SQL에 `MEMBER_ID = 로그인 회원` + `STATUS = 'ACTIVE'` 조건 · 없거나 탈퇴면 404
  - `MemberProfileService` · DTO 4개 (`MemberProfileDTO`(Mapper 1행 · 이메일 원문) · `MemberProfileResponseDTO` · `MemberProfileRequestDTO` · `PasswordChangeRequestDTO`) · `MemberMapper`에 `selectProfile` · `updateProfile` · `selectPassword` 추가
  - 화면: `frontend/src/api/member-api.js`에 `getMyProfile` · `updateMyProfile` · `changeMyPassword` · `profileFormError` · `toProfileRequest` · `passwordChangeFormError` 추가 · `ProfileDetails.jsx`에 서버 회원용 `ServerProfileDetails` · `ServerPasswordChange` 추가 · `ProfilePage.jsx`의 「개인정보」 · 「비밀번호 변경」 탭은 서버 로그인 회원이면 서버 폼 (빠른 체험은 기존 시연 폼 그대로 · 라우팅 그대로)
  - 테스트: `MemberProfileServiceTest` 12개 · `MemberProfileControllerTest` 9개 · `member-api.test.js` · `render-pages.mjs`(서버 회원 개인정보 · 비밀번호 탭 2개)
- **변경 사유**: 기능명세서 F-01-04. 세액 계산(F-03-01) · 혜택 진단(F-03-02)이 `MEMBER`의 군 복무 · 입사일 · 회사 규모 · 무주택을 읽는데 이 값을 저장하는 곳이 없어 모든 회원이 기본값으로 계산되고 있었음. 비밀번호 변경은 토큰만 탈취돼도 바꾸지 못하게 현재 비밀번호를 확인하는 별도 주소로 둠 (사용자 결정)
- **검증 결과**: `./mvnw spotless:check` 통과 · `./mvnw -B verify` 251건 중 249건 통과 (실패 2건은 기존 expense `회원번호_헤더가_없으면_401` · tasks.md 「팀 확인 필요」) · 프론트 `npm test` · `npm run build` 통과 · 고친 파일 prettier 통과
  - 미검증: 실제 Oracle DB에서 UPDATE · ORA-02290 문구 확인 · 브라우저에서 조회 → 저장 → 다시 계산 흐름 · 토큰 없이 부르면 401 (`/api/**` 규칙이라 같을 것으로 보지만 `SecurityConfigTest`는 common이라 추가 안 함)

### [2026-11-21] 아이디 찾기 · 비밀번호 재설정 (F-01-03)
- **작업 내용**:
  - API 3개 (`member/controller/PasswordResetController` · 팀 명세에 없는 제안 주소 · 이메일이 접속 기록에 남지 않게 모두 POST 본문)
    - `POST /api/auth/login-id-lookups`: 이메일 → 앞 2글자만 보이는 아이디 (`te******` · 마스킹은 서버에서)
    - `POST /api/auth/password-resets`: 6자리 코드 생성 → 메일 발송 → BCrypt 해시로 `PASSWORD_RESET` 저장 (`SEQ_PASSWORD_RESET` · 만료 `SYSDATE + 10분`)
    - `POST /api/auth/password-resets/completion`: 가장 최근 코드를 `FOR UPDATE`로 잠그고 확인 → 틀리면 `FAIL_COUNT + 1`(5번째에 무효) → 맞으면 `USED_YN = 'Y'` + 새 비밀번호 BCrypt 저장 (한 트랜잭션 · 코드 틀림 예외에서는 되돌리지 않음 `noRollbackFor`)
  - 대상 회원: 탈퇴 제외 · `EMAIL_VERIFICATION_REQUIRED=true`면 `EMAIL_VERIFIED_YN = 'Y'`만 (끈 동안은 'N'도 허용 · 사용자 결정) · 아이디 찾기도 같은 조건
  - `PasswordResetService` · `PasswordResetMapper`(.java · .xml) · DTO 6개 (`MemberEmailRequestDTO` · `LoginIdLookupResponseDTO` · `PasswordResetResponseDTO` · `PasswordResetCompleteRequestDTO` · `PasswordResetMemberDTO` · `PasswordResetDTO`) 추가
  - `MemberMapper`: `selectResetMember`(이메일로 조회) · `updatePassword` 추가 · `MemberService.validatePassword`를 패키지 공개로 (재설정도 같은 비밀번호 규칙)
  - `EmailSender.sendPasswordResetCode` 추가 (`LogEmailSender` · `SmtpEmailSender` · 메일 1통 보내는 부분을 `send()`로 묶음)
  - 화면: `frontend/src/api/member-api.js`에 `findLoginId` · `sendPasswordResetCode` · `completePasswordReset` · `passwordResetFormError` 추가 · `AccountForms.jsx`의 기존 `reset` 화면(「미연동」 안내)을 「아이디 찾기 / 비밀번호 재설정」 두 탭 서버 연결 카드로 교체 · 로그인 화면에 「아이디 · 비밀번호를 잊으셨나요?」 표시 (`SHOW_PASSWORD_RESET` 삭제) · 라우팅은 그대로
  - 테스트: `PasswordResetServiceTest` 18개 · `PasswordResetControllerTest` 8개 · `SmtpEmailSenderTest` 1개 · `member-api.test.js` · `render-pages.mjs`(로그인 링크 · 재설정 화면)
- **변경 사유**: 기능명세서 F-01-03 (DB v2.3 · v2.5의 `PASSWORD_RESET`은 준비돼 있었음 · 계획서에 없어 포함 여부 D-03 후순위)
- **검증 결과**: `./mvnw spotless:check` 통과 · `./mvnw -B verify` 230건 중 228건 통과 (실패 2건은 기존 expense `회원번호_헤더가_없으면_401` · tasks.md 「팀 확인 필요」) · 프론트 `npm test` · `npm run build` 통과 · 고친 파일 prettier 통과 (전체 `format:check`는 기존 CRLF 문제로 실패)
  - 사용자 확인 (2026-11-21): 화면에서 아이디 찾기(앞 2글자 + 마스킹) · 인증 코드 발송 → 입력 → 새 비밀번호 → 「비밀번호를 바꿨어요」 → 로그인하러 가기 · 공용 DB에서 `SEQ_PASSWORD_RESET` · `PASSWORD_RESET` 저장 값(코드 해시 · 10분 · `USED_YN`) · `MEMBER.PASSWORD` · `UPDATED_AT` 변경 확인
  - 미검증: 예외 흐름을 실제 DB에서 따로 확인한 기록 없음 (5번 틀림 · 10분 만료 · 사용한 코드 재사용 · 테스트로만 확인)

### [2026-11-21] 이메일 인증 화면 스위치를 환경변수로 · 메일 발송 안내 문구 수정 (F-01-01)
- **작업 내용**:
  - `frontend/src/api/member-api.js`: `EMAIL_VERIFICATION_REQUIRED`를 코드 고정값(false) 대신 `VITE_EMAIL_VERIFICATION_REQUIRED=true`일 때만 켜지게 변경 (기본 꺼짐)
  - `frontend/src/pages/auth/AccountForms.jsx`: 서버 회원가입 인증 안내 「메일 발송 기능은 준비 중…아래에 표시됩니다」 · 「(메일 발송 기능은 아직 연결되지 않았어요)」 삭제 → 메일 발송 안내로 변경 (빠른 체험 문구는 그대로)
  - `frontend/.env.example` · `frontend/README.md` 환경변수 표에 `VITE_EMAIL_VERIFICATION_REQUIRED` 추가 · 백엔드 문서의 「member-api.js를 true로」 안내를 새 방식으로 수정
- **변경 사유**: 메일 발송이 붙어 실제로 메일이 나감 · 스위치를 코드에 두면 켜서 커밋할 때 메일 계정이 없는 팀원 화면에도 인증이 강제됨
- **검증 결과**: `npm test` · `npm run build` 통과 · 고친 파일 prettier 통과 (전체 `format:check`는 기존 CRLF 문제로 실패) · 로컬에서 Gmail 인증번호 수신 → 가입 → `EMAIL_VERIFIED_YN = 'Y'` 확인 (사용자 확인)

### [2026-11-20] 회원가입 동의서 버전 표시 · 초안 안내 문구 삭제 (F-01-01)
- **작업 내용**: `frontend/src/pages/auth/AccountForms.jsx` 서버 회원가입 체크박스에 동의서 버전 표시 (예: `[필수] 서비스 이용약관 동의 (v1.0)` · `VITE_*_VERSION` 값) · 펼치기 문구 「동의문 초안 보기 · 내용·버전 확인 필요」 → 「동의문 보기」 (빠른 체험 화면은 그대로)
- **변경 사유**: 기능명세서 F-01-01 「약관·개인정보 동의(버전 표시)」 · 동의서 버전 1.0 확정
- **검증 결과**: `npm test` · `npm run build` 통과 · 미검증: 동의문 본문의 「확정 전」 표현은 팀 결정 사항이라 그대로 둠

### [2026-11-20] 이메일 인증번호 메일 발송 구조 추가 (EmailSender · F-01-01)
- **작업 내용**:
  - `pom.xml`: `spring-boot-starter-mail` 추가
  - `member/client/EmailSender`(인터페이스) · `LogEmailSender`(MAIL_MODE=log · 기본값 · 로그만) · `SmtpEmailSender`(MAIL_MODE=smtp · 실제 발송 · 실패 시 503 `MAIL_UNAVAILABLE`) 추가 → `CalcClient` · `MockCalcClient` · `HttpCalcClient`와 같은 구조
  - `EmailVerificationService.send`: 메일을 먼저 보내고, 보낸 뒤에만 인증 기록 저장 (발송 실패 시 쓸 수 없는 기록을 남기지 않음)
  - `application.yml`: `spring.mail`(MAIL_HOST · MAIL_PORT · MAIL_USERNAME · MAIL_PASSWORD · 연결 3초 · 읽기/쓰기 5초) · `member.mail.mode` · `member.mail.from` 추가
  - `.env.example` · README 4.2 표에 메일 환경변수 추가
  - 테스트: `SmtpEmailSenderTest` 3개 추가 · `EmailVerificationServiceTest` 3개 추가
- **변경 사유**: 이메일 인증에서 실제 메일 발송만 비어 있었음 (인증번호 생성 · 확인 · 가입 전 확인은 구현돼 있었음). SMTP 계정 확정 전에도 개발이 멈추지 않게 log/smtp를 설정으로 고름
- **검증 결과**: `./mvnw spotless:check` 통과 · member 테스트 모두 통과 · `./mvnw -B verify`는 expense 테스트 2개 실패
  - 실패: `CardUploadControllerTest` · `ExpenseControllerTest`의 `회원번호_헤더가_없으면_401` (401 기대 · 400 응답) → 이번 변경 전 코드에서도 똑같이 실패 (기존 문제 · expense 담당 확인 필요)
  - 참고: 503은 공통 처리기(`GlobalExceptionHandler`)가 화면에 `SERVER_ERROR` 「잠시 후 다시 시도해 주세요.」 + `errorId`로 바꿔 보냄 (`MAIL_UNAVAILABLE`은 서버 로그에서 확인 · common 수정 안 함)
  - 미검증: 실제 SMTP 발송(메일 계정 미정) · 화면에서 인증 흐름 (EMAIL_VERIFICATION_REQUIRED는 아직 false)

### [2026-11-20] SSE 실시간 알림 · 공제 마감 안내 (TT-150 · TT-151 · TT-152 · PR #46)
- **작업 내용**:
  - `notification/controller/NotificationController`: `GET /api/notifications/stream` (SSE 구독)
  - `notification/service/NotificationService`: 회원별 연결 목록 · 연결 직후 첫 이벤트 · 25초 하트비트 · 계산 완료 알림
  - `DeadlineNotificationScheduler` · `NotificationSchedulingConfig`: 12월 매일 09:00(서울) 확인 → D-30 · D-7 · D-1에만 마감 안내
  - `CalculationService`: 계산 · 저장 뒤 알림 (금액 빼고 연도 · 결과 번호만)
  - `common/config/SecurityConfig`: `dispatcherTypeMatchers(DispatcherType.ASYNC).permitAll()` 한 줄 (조장님 확인 후)
  - 화면: `notification-stream.js`(fetch로 SSE 읽기 · Bearer 헤더 · 60초 무응답이면 다시 연결 · 401이면 중단) · `App.jsx`(토스트) · `Topbar.jsx`(종 목록 · 안 읽은 배지) · `globals.css`
- **변경 사유**: 2차 미션 실시간 알림 · 알림 문구에 금융 정보(금액) 넣지 않음 · ASYNC 허용은 SSE 연결 종료 때 JWT 필터가 다시 안 봐서 생기는 Access Denied 로그 때문 (첫 요청 검사는 그대로) · 화면은 브라우저 기본 EventSource가 Authorization 헤더를 못 붙여서 fetch로 직접 읽음
- **검증 결과**: 로그인 → 알림 연결(200 · `text/event-stream`) → 계산 → calculation 이벤트 수신 확인 · `DeadlineNotificationSchedulerTest`로 D-30 · D-7 · D-1만 보내는지 확인

### [2026-11-20] 정보 입력: 단계 버튼에도 입력칸 검사 · 소수 입력 안내 문구 (TT-132)
- **작업 내용**: `frontend/src/pages/tax/TaxInfoForm.jsx`
  - 위쪽 단계 버튼으로 앞 단계로 넘어갈 때 `formRef.current.reportValidity()`로 지금 단계 입력칸 검사 (틀리면 말풍선 · 이동 안 함 · 뒤로 갈 때는 검사 안 함)
  - 숫자 칸에 소수를 넣으면 브라우저 기본 문구 대신 「소수는 입력할 수 없어요.」 (`onInvalid` + `validity.stepMismatch` → `setCustomValidity`) · 다시 입력하면 문구 지움
  - (11/23) 기본 정보 → 소득 정보로 갈 때는 전체 검사(`validateTaxData`)를 건너뜀: 소득 칸을 비우고 뒤로 왔다가 다시 가려 하면 「금액은 0 이상으로 입력해주세요.」로 막히던 문제 · 소득 정보를 지나갈 때(1 → 2 이상)는 지금처럼 전체 검사
    · 「다음 단계」 버튼(`onSubmit`)도 마지막 단계 전에는 `move`에 검사를 맡겨서 위쪽 단계 버튼과 같은 규칙으로 맞춤 (`onSubmit`이 먼저 전체 검사를 해서 「다음 단계」로는 계속 막혔음)
- **변경 사유**: 단계 버튼은 `<form>` 밖에 있어서 빈칸 · 소수가 있어도 검사 없이 넘어감 · 기본 문구 「가장 근접한 유효 값 2개는…」가 어려움
- **검증 결과**: `npx prettier --check` 통과 · `npm test` · `npm run build` 통과 · 브라우저에서 총급여에 소수 입력 → 「소수는 입력할 수 없어요.」 · 「다음 단계」와 위쪽 단계 버튼 모두 빈칸(「이 입력란을 작성하세요」) · 귀속 연도 0(「값은 1900 이상이어야 합니다」)에서 막힘 확인 · 소득 칸 비움 → 기본 정보 → 다시 소득 정보(위쪽 단계 버튼 · 「다음 단계」) 이동 확인

### [2026-11-19] 시뮬레이션 카드 사용액을 서버 합계 기준으로 (TT-118 · PR #43)
- **작업 내용**: 화면 `prediction-service.js` · `App.jsx` · `TaxInfoForm.jsx` · `TaxInputPage.jsx` · 카드 사용액 = 서버 합계(`GET /api/expense-totals?year=`) + 가상 추가액 · 합계를 못 받으면 계산 안 함 · 백엔드 변경 없음
- **변경 사유**: 시뮬레이션이 브라우저 값으로 카드 사용액을 셈 → 서버 계산과 달라질 수 있었음
- **검증 결과**: `npm test` · `npm run build` 통과 · PR #41 · #42와 합쳐도 충돌 없이 통과

### [2026-11-19] 카드 업로드: 미지원 형식 파일은 목록에 넣지 않고 alert (TT-148)
- **작업 내용**: `frontend/src/components/expense/ExpenseUpload.jsx`
  - 확장자가 `.csv` · `.xlsx` · `.xls`가 아니면 서버로 보내지 않음 · 서버가 `UNSUPPORTED_FORMAT`(읽을 수 없는 양식)으로 답한 파일도 목록에 넣지 않음
  - 다 끝난 뒤 `alert` 한 번: 「지원하지 않는 형식입니다.」 + 파일 이름
  - 서버 검사(`CardUploadServiceImpl` 확장자 · 계산 서버 양식)는 그대로
- **변경 사유**: pdf 등도 파일 목록에 「처리 실패」로 올라가 업로드된 것처럼 보였음 (DB에는 저장 안 됨)
- **검증 결과**: 브라우저에서 pdf 업로드 → alert · 목록에 안 생김 확인 · `npm test` · `npm run build` 통과

### [2026-11-19] 정수 칸에 소수가 오면 400으로 거부 (TT-131)
- **작업 내용**: `backend/src/main/resources/application.yml`에 `spring.jackson.deserialization.accept-float-as-int: false`
- **변경 사유**: 기본 설정은 정수 칸(금액 · 인원)에 `1.5`가 오면 소수점을 버리고 `1`로 저장함 → 잘못된 값이 조용히 들어감
- **주의**: 팀 전체 API에 적용됨 · 화면에서 정수 칸에 소수를 보내면 400 `VALIDATION`
- **검증 결과**: `mvnw clean verify` 195건 통과 (서버 전체를 띄우는 `contextLoads` 포함 → 설정 이름 오류 없음) · Talend API Tester로 `PUT /api/incomes/2026`에 `totalSalary: 36000000.5` → 400 `VALIDATION` 「요청 데이터 형식을 확인해 주세요.」 확인
  - 테스트 코드는 추가하지 않음 (직접 호출로 확인)

### [2026-11-19] 카드 업로드 · 거래 내역 API: 회원번호를 로그인 토큰에서 받기 (TT-149)
- **작업 내용**
  - `CardUploadController` · `ExpenseController`: `@RequestHeader("X-Member-Id")` → `@AuthenticationPrincipal Long memberId` (11/17 소득 · 계산 API와 같은 방식)
  - 테스트: `CardUploadControllerTest` · `ExpenseControllerTest`는 헤더 대신 `loginAs(1L)` · 「헤더 없으면 401」 테스트 삭제 · `SecurityConfigTest`의 「토큰 없으면 401」 목록에 업로드 · 거래 목록 · 합계 · 삭제 추가
- **변경 사유**: 화면이 로그인 토큰만 보내게 바뀌어(11/17) 업로드 · 거래 API가 헤더 없음으로 실패 · 헤더 방식은 숫자만 바꾸면 남의 데이터 접근 가능
- **검증 결과**: `mvnw clean verify` 195건 통과 · 로그인 후 화면에서 CSV 업로드 · 업로드 목록 · 삭제 동작 확인
  - 참고: 처음 `verify`에서 `contextLoads` 1건 실패 → 다른 브랜치(feature/admin)에서 빌드한 `target/classes/mapper/admin/*.xml`이 남아 있던 것 · `mvnw clean verify`로 해결 (코드 문제 아님)
  
### [2026-11-18] 계산 결과 환급 · 추가 납부 색 · 표기 구분 (TT-126 · PR #42)
- **작업 내용**: 화면 `services.js` · `DeductionDetails.jsx` · `Dashboard.jsx` · `SimulationPage.jsx` · `globals.css` · 환급 파랑 · 추가 납부 빨강 + 글자 표기 · 결과 이력 「환급 ○원 / 추가 납부 ○원」 · 변경 조건 표 연도 · 명 · 개월 · 원
- **변경 사유**: 11/18 중간 시연 피드백
- **검증 결과**: `npm test` · `npm run build` 통과

### [2026-11-18] 무주택 세대주 「확인 필요」면 다음 단계 막기 (TT-125 · PR #41)
- **작업 내용**: 화면 `TaxInfoForm.jsx` · 5단계(주거 정보)에서 무주택 세대주 미확인이면 다음 단계 · 단계 번호 건너뛰기 막음 · 정보 입력 · 시뮬레이션 공통
- **변경 사유**: 11/18 중간 시연 피드백 · 11/19 고은님 의견으로 정보 입력에도 적용
- **검증 결과**: 정보 입력 · 시뮬레이션에서 각각 막힘 확인 · `npm test` · `npm run build` 통과

### [2026-11-18] 계산 결과 화면 안내 문구 정리 (TT-124 · PR #40)
- **작업 내용**: 화면 `Dashboard.jsx` · `SimulationPage.jsx` · `dashboard-model.js` · 「확인 필요」 → 「증빙 확인」 등 서버 연결 상태에 맞게 · `render-pages.mjs` 테스트 추가
- **변경 사유**: 서버에 연결됐는데 「미연동 · 시연용」 문구가 남아 있었음
- **검증 결과**: PR 본문에 따로 적은 검증 기록 없음

### [2026-11-18] 다른 PC 접속 때 POST · PUT · DELETE 403 해결 (TT-123 · PR #39)
- **작업 내용**: 화면 `vite.config.js` 개발 프록시에서 Origin 헤더 제거
- **변경 사유**: 팀 서버처럼 다른 PC에서 `IP:5173`으로 접속하면 백엔드 CORS가 `localhost:5173`만 허용해서 403
- **검증 결과**: PR 본문에 따로 적은 검증 기록 없음

### [2026-11-17] 세액 계산 · 시뮬레이션의 중소기업 감면 확인을 한 메서드로 (TT-117 · PR #32)
- **작업 내용**: `CalculationService`의 `calculate()` · `simulate()`에 똑같이 있던 감면 확인 블록을 `applySmeEligibility()` 하나로 · 감면 신청(Y)인데 진단이 「해당」이 아니면 N (기존과 같음)
- **변경 사유**: PR #22 · #27에서 같은 블록이 복사돼 두 곳에 있었음 · 시뮬레이션에도 계산과 같은 로그(회원번호 · 연도만)가 남게
- **검증 결과**: `mvnw.cmd -B clean verify` 91개 통과 · 계산 결과 · API 응답 · 테스트 코드 변경 없음

### [2026-11-17] 계산 서버 토큰 필수 통일 · 환경변수 문서 맞춤 (TT-116 · PR #31)
- **작업 내용**:
  - 계산 서버 `calc/app/main.py`: 토큰(`X-Calc-Token`)이 비어 있으면 서버가 시작되지 않음 · `calc/tests/test_api.py` 2개 추가(토큰 없으면 시작 안 함 · 있으면 정상)
  - `backend/.env.example` · `backend/README.md` · `calc/.env.example` · `calc/README.md` · 루트 `README.md`: 토큰 · 환경변수 · 언어 버전 설명을 실제와 맞춤
- **변경 사유**: 강사님 피드백 · 예전엔 토큰이 비면 계산 서버는 켜지고 요청만 막아서 설정 실수를 늦게 알게 됨 · README와 `.env.example` 설명이 실제와 달랐음
- **검증 결과**: 계산 서버 pytest 65개 · ruff check · format 통과

### [2026-11-17] 로그인 성공 알림 문구에서 「시연」 삭제
- **작업 내용**: `frontend/src/pages/auth/AuthPage.jsx` 서버 로그인 성공 알림 「이 브라우저의 시연 계정으로 로그인했습니다.」 → 「로그인했습니다.」 (빠른 체험 문구는 그대로)
- **변경 사유**: 실제 서버 계정으로 로그인하는데 「시연 · 이 브라우저의」로 보여 사용자가 가짜 로그인으로 오해할 수 있었음
- **검증 결과**: `npm test` · `npm run build` 통과 · `npm run format:check`는 이 파일과 무관한 기존 문제(CRLF 줄바꿈 · 기존 8개 파일)로 실패 → tasks.md 「팀 확인 필요」에 기록 (`prettier --check --end-of-line auto`로 이 파일은 통과)
  - 미검증: 브라우저에서 알림 문구 확인

### [2026-11-17] 회원(로그인 · 회원가입) 코드 스타일 정리 (선생님 샘플 기준)
- **작업 내용**
  - 범위: `member/` 전체 + 이 브랜치에서 만든 공통 파일(`TokenService` · `AccessLogMapper` · `BusinessException` · `PasswordConfig` · `SecurityConfig` 주석 1곳). 다른 도메인 · develop에 있던 공통 파일은 건드리지 않음
  - 주석: 클래스 `/** 역할 2~3줄 */` · 메서드 `// 방식 주소 — 설명` · 줄 끝은 "왜"만 · 말투 "~한다" · `[수정 11/17]` 이력 주석은 이유만 남기고 삭제
  - Lombok: Controller 생성자 → `@RequiredArgsConstructor` · `LoggerFactory` → `@Slf4j` · `BusinessException` getter → `@Getter` (설정 값 `@Value` · 테스트용 생성자가 있는 Service는 생성자 그대로)
  - DTO: record 8개 → 클래스 (요청 `@Getter @Setter @NoArgsConstructor @AllArgsConstructor` · 응답 `@Getter @AllArgsConstructor`) · 비밀번호 · 인증번호는 `@ToString(exclude)`로 로그에서 제외 (`LoginRequestDTO`는 원래 record 기본 toString에 비밀번호가 찍혔음)
  - 테스트: 응답 값 꺼내는 코드만 `x()` → `getX()` (`MemberServiceTest` · `LoginServiceTest` · `EmailVerificationServiceTest`)
  - `TokenService`의 `IssuedToken` · `TokenInfo`는 record 그대로 (DTO가 아닌 내부 값 · `SecurityConfig`가 사용)
- **변경 사유**: 초보자도 읽기 쉽게 선생님 샘플(final_project_sample/back)과 같은 주석 · 코드 모양으로 맞춤. API 주소 · 요청 · 응답 JSON · 동작은 바뀌지 않음
- **검증 결과**: `./mvnw spotless:check` 통과 · `./mvnw -B verify` 155건 통과
  - 미검증: 실제 서버 실행 후 화면에서 가입 · 로그인 (JSON 칸 이름은 테스트로만 확인)

### [2026-11-17] 이메일 인증 끄기 (설정 스위치 · 화면 UI 숨김)
- **작업 내용**
  - 백엔드: `application.yml`에 `member.email-verification.required`(`EMAIL_VERIFICATION_REQUIRED`, 기본 false) · `MemberService`가 켰을 때만 인증 확인 · 끄면 `EMAIL_VERIFIED_YN = 'N'` · `MemberSignupRequestDTO.verificationId`의 `@NotBlank` 제거 (빈 값 검사는 Service가 설정에 따라)
  - 화면: `member-api.js`에 `EMAIL_VERIFICATION_REQUIRED = false` · `AccountForms.jsx` 인증 UI를 이 값으로 숨김 (코드는 그대로) · `signupFormError`는 끈 동안 인증 확인 생략 · 요청의 `verificationId`는 null
  - 테스트: `MemberServiceTest` 2개 추가(켰을 때 빈 번호표 400 · 껐을 때 인증 없이 가입 N) · `MemberControllerTest` 번호표 누락 400 삭제(Service로 옮김) · `member-api.test.js` · `render-pages.mjs`(가입 화면에 인증 UI 없음)
- **변경 사유**: 실제 메일 발송이 없어 형식만 맞는 이메일이면 누구나 인증되던 상태 → 메일 발송이 붙을 때까지 꺼 둠
- **검증 결과**: `./mvnw test` 155건 통과 · 프론트 `npm test` · `npm run build` 통과
  - 미검증: 브라우저에서 인증 없이 가입 → 로그인

### [2026-11-17] 로그인 · 로그아웃 화면 연결 (F-01-02)
- **작업 내용**
  - `frontend/src/api/member-api.js`: `login`(POST /api/auth/login) · `logout`(POST /api/auth/logout) 추가
  - `account-service.js`의 `accountService`: 로그인 · 로그아웃 · `current()`를 서버 로그인으로 교체 (토큰 `hope-refund.accessToken` · 회원 정보 `hope-refund.loginMember` · 만료 시각 지나면 삭제) · 체험용 `createAccountService`는 그대로
  - `api-client.js`: `bearerHeader` · `clearLogin` · `expireLogin` (토큰을 보냈는데 401 → 삭제 + 알림) · `http.js`: `auth: true` 요청만 토큰 전송
  - `App.jsx`: 만료 알림 → 로그인 화면 + 안내 · `AccountForms.jsx`: 칸 이름 「아이디」 · 서버 연동 표시 · 「최초 관리자 초기 설정(시연)」 버튼 숨김 · `Sidebar.jsx`: 「브라우저 시연」 표시는 빠른 체험만
  - 테스트: `http.test.js`(토큰 헤더 · 로그인 실패 401은 토큰 유지 · 401 만료 삭제) · `member-api.test.js`(로그인 · 로그아웃 주소 · 본문)
  - 버그 수정: 서버 로그인 직후 대시보드가 빈 화면 → 로그인 응답에 생년월일 · 이메일이 없어 `yearOf(date.trim)` · `groupComparison(d.birth.slice)` · 프로필 `email.replace`가 오류 · 값이 없어도 버티게 수정 + `App`은 빈 값('')으로 채움 · `render-pages.mjs`에 서버 로그인 회원 화면 3개 추가 (수정 전 실패 · 수정 후 통과 확인)
- **변경 사유**: 로그인이 브라우저 가짜 계정이라 서버로 가입한 회원이 로그인할 수 없었고, 토큰이 없어 회원 API가 모두 401
- **검증 결과**: 프론트 `npm test` · `npm run build` 통과 · 고친 파일 `prettier --check` 통과 (전체 `format:check`는 이 PC의 CRLF 줄바꿈으로 전부터 실패)
  - 미검증: 실제 서버 · 브라우저에서 로그인 → 계산 → 로그아웃 ([tasks.md](tasks.md) 「브라우저 확인 순서」)

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

### [2026-11-16] 시뮬레이션 화면 서버 연결 · 결과 이력 (TT-105 · PR #30)
- **작업 내용**: 화면 `prediction-service.js`(새 파일) · `income-service.js` · `SimulationPage.jsx` · `App.jsx` · 가상 입력 → `POST /api/simulations/{taxYear}` · 결과 이력 `GET /api/predictions` · 열기는 누른 줄 아래 펼침 · 출발값은 서버 소득
- **변경 사유**: PR #27(시뮬레이션 · 결과 이력 API)의 화면 연결 · 11/17 사용자 흐름 테스트에서 시크릿 창 출발값 0 문제 발견 → 서버 소득으로
- **검증 결과**: 백엔드 91개 · 계산 서버 pytest 63개 · 화면 `npm test` · `npm run build` 통과 · 사용자 흐름 테스트 8개 (`frontend/docs/VERIFICATION.md`)

### [2026-11-16] 화면 API 연동: 소득 입력 · 계산 · 혜택 진단 (PR #24)
- **작업 내용**: 화면 `api-client.js`(봉투 응답을 `apiRequest()`에서 한 번에) · `income-service.js` · `calculation-service.js` · `benefit-service.js` · 대시보드 · 혜택 화면 · `vite.config.js` `/api` 프록시 · 테스트 3개
- **변경 사유**: 소득 저장 → 계산 → 대시보드 · 혜택 진단을 서버 API로 · 서버 연결 실패 때 체험용 계산은 개발 서버에서만
- **검증 결과**: `npm test` · `npm run build` 통과

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
### [2026-11-16] 거래내역 조회·삭제 API (F-02-04 · TT-111)
- **작업 내용**:
  - `GET /api/card-uploads`: 본인의 업로드 이력 (번호 · 파일 이름 · 건수 · 업로드일 · 최신순)
  - `GET /api/expenses?year=` 또는 `?uploadId=`: 본인의 거래 목록 + 분류 근거 (`MERCHANT_LIST` LEFT JOIN → 일치 방식 `matchType` · 목록 상호명 `matchedName` · 출처 `sourceName` · 일반 거래는 근거가 null) · 둘 다 없으면 400
  - `GET /api/expense-totals?year=`: 분류 + 결제수단별 합계 (`categoryId` · `payMethod` · `totalAmount` · `count` · 취소 음수 포함)
  - `DELETE /api/card-uploads/{uploadId}`: 본인 업로드 삭제 → 204 · 거래(`EXPENSE`)는 `ON DELETE CASCADE`로 같이 삭제
  - 새 `ExpenseController` · `ExpenseService(Impl)` · `ExpenseMapper`(+ XML) · DTO 3개(`UploadHistoryDTO` · `ExpenseItemDTO` · `ExpenseTotalDTO`) · 업로드(POST)는 기존 `CardUploadController` 그대로
- **변경 사유**:
  - 기능명세서 F-02-04 · 남의 업로드 번호는 403이 아니라 404 (팀 규칙 · `GlobalExceptionHandler` · 없는 것과 같은 문구로 존재 여부를 숨김)
  - 합계를 분류만이 아니라 결제수단까지 나눈 이유: 계산 서버 `POST /calculate`의 `cardSpends`가 `{categoryId, payMethod, amount}` 단위 (결제수단마다 공제율이 다름) → 계산 요청에 그대로 옮겨 쓸 수 있게
  - 연도는 2000 ~ 2100만 받음 (오타 연도로 날짜를 만들다 500이 나는 것을 400으로 막음)
- **검증 결과**:
  - 테스트 20개 추가 (Service 10 · Controller 10)
  - `mvnw spotless:check` · `mvnw -B verify` 통과
  - curl (학원 DB · 회원 1): 업로드 이력 200 · `uploadId=1` 거래 137건 200(분류 근거 표시 확인) · 합계 2026 200(10줄 · 건수 합 137) · 남의 업로드(`uploadId` 2 · 10) 조회 404 · 남의 업로드 삭제 404
  - 본인 업로드 삭제 (2026-11-17 · 화면에서 올린 업로드 101번): `DELETE` 204 → `CARD_UPLOAD` · `EXPENSE` 둘 다 0건 (CASCADE 확인)

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

### [2026-11-13] 예상 세액 계산 + 결과 저장 · 청년 혜택 진단 API (F-03-01 · F-03-02 · TT-73 · TT-74 · PR #22)
- **작업 내용**:
  - `prediction` 패키지: `CalculationController`(`POST /api/calculations/{taxYear}`) · `BenefitController`(`GET /api/benefits/{taxYear}`)
  - `CalculationService` · `BenefitService` · `PredictionSaveService`(PREDICTION · PREDICTION_INPUT · PREDICTION_DETAIL 저장)
  - `client/HttpCalcClient`(계산 서버 호출 · `X-Calc-Token`) · `MockCalcClient`(`CALC_MODE=mock` · 계산 서버 없이 · 가짜 결과는 저장 안 함)
  - `PredictionMapper`(+ XML) · dto 9개 · 테스트 6개 파일
- **변경 사유**: 세액 계산 · 진단 파트(③) · 계산은 Python 계산 서버, 저장은 Spring (10/30 결정)
- **검증 결과**: `clean verify` 74개 통과 (계산 서버는 가짜로 대체 · 두 서버를 붙인 실제 계산 확인은 이 PR에서는 안 함)

### [2026-11-13] 계산 서버(FastAPI) 세액 계산 · 카드 소득공제 · 혜택 진단 (F-03-01 · F-03-02 · PR #21)
- **작업 내용**: 계산 서버 `POST /calculate`(예상 세액) · `POST /benefit`(청년 혜택 진단) · `services/`(tax_calculator · card_deduction · bracket · benefit · tax_rules) · `repositories/oracle_criteria.py`(DB 기준값 읽기 · 읽기 전용 계정 · 5분 캐시) · `X-Calc-Token` 검사 · 테스트
- **변경 사유**: 세율 · 공제율 · 한도를 코드가 아니라 DB 기준값에서 읽음 (기준이 바뀌면 행만 수정) · 닫은 PR #15에서 계산 서버 부분만 나눔
- **검증 결과**: pytest 63개 · ruff 통과

### [2026-11-13] 계산 서버 카드 파일 해석 · 지출 분류 (F-02-02 · F-02-03 · TT-109 · TT-110)
- **작업 내용** (계산 서버 `calc/` · 백엔드가 호출):
  - `POST /card/parse`: 백엔드가 보낸 카드사 · 홈택스 파일(csv · xlsx · xls)을 공통 4칸(사용일 · 가맹점명 · 금액 · 결제수단)으로 바꾸고 행마다 분류 결과(`categoryCode` · `listId`)를 붙여 돌려줌 · 저장은 하지 않음 · `X-Calc-Token` 검사
  - 파일 오류는 400 `{"error", "message", "errorRows"}` (`UNSUPPORTED_FORMAT` · `PAY_METHOD_REQUIRED` · `ROW_LIMIT` · `VALIDATION`) · `main.py`에 처리기 추가
  - `app/card/merchant_repo.py`: `MERCHANT_LIST` · `SPEND_CATEGORY`에서 사용 중(`USE_YN='Y'`)인 목록을 읽어 분류기(`MerchantMatcher`)를 만듦 · 첫 요청 때 한 번만 읽고 재사용 (목록을 고치면 계산 서버 재시작)
  - `scripts/compare_card_classify.py`: 파이썬 분류 결과와 SQL(`04_분류쿼리.sql`) 결과를 건마다 비교하는 검증 도구 (읽기만)
- **변경 사유**:
  - 기능명세서 F-02-02 · F-02-03 · 파일 해석과 분류를 계산 서버 한 곳에서 하기로 함 (백엔드는 결과를 받아 저장만)
- **검증 결과**:
  - 가상 카드 내역 10,453건: 파이썬 분류 = SQL 분류 (전부 일치 · 목록 EXACT 79,727개 이름 · CONTAINS 697개)
  - pytest 130개 · `ruff check` 통과 (카드 API 7 · 목록 읽기 5 추가)
  - 배포 시 필요: 계산 서버 읽기 계정에 `MERCHANT_LIST` · `SPEND_CATEGORY` SELECT 권한 + 동의어 (`database/README.md`) · 로컬은 테이블 주인 계정이라 해당 없음

### [2026-11-13] 그룹 비교 통계 화면 연결 (F-06-02 · PR #26)
- **작업 내용**: 화면 `statistics-service.js` · `group-model` · 대시보드 · 통계 화면 · 테스트 (API는 바로 아래 11/13 「그룹 비교 통계 조회 API」)
- **검증 결과**: 테스트 6개 추가 · 학원 DB로 회원 81 · 82 · 없는 회원 확인

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
### [2026-11-12] 카드내역 업로드 저장 API (F-02-02 · TT-109)
- **작업 내용**:
  - `POST /api/card-uploads` (multipart: `file` · 선택 `payMethod` · `allowDuplicate`): 파일 확인(빈 파일 · 확장자 csv · xlsx · xls · 결제수단) → 계산 서버 `POST /card/parse` 호출 → 중복 확인 → 저장 → 분류별 건수 응답
  - 저장: 업로드 1행(`CARD_UPLOAD`) + 지출 N행(`EXPENSE`) + 분류 반영을 한 트랜잭션으로 (`CardUploadSaveService`) · 계산 서버 호출은 트랜잭션 밖
  - 중복: 같은 사용일 · 가맹점 · 금액 · 결제수단이 이미 있으면 409 `DUPLICATE_WARNING`(겹친 건수) · 사용자가 「계속」하면 `allowDuplicate=true`로 다시 보내 저장
  - 사용구분 칸이 없는 파일은 400 `PAY_METHOD_REQUIRED` → 화면에서 신용/체크를 골라 다시 보냄
  - 계산 서버 연결 실패 · 토큰 오류는 503 (`CardParseClient`) · 원본 파일과 카드번호는 저장하지 않음 (기능명세서 S-02)
  - 새 패키지 `expense` (controller · service · client · mapper · dto · exception)
- **변경 사유**:
  - 기능명세서 F-02-02 · 파일 해석과 분류는 계산 서버에서 하고 백엔드는 저장 · 중복 경고만
- **검증 결과**:
  - 테스트 22개 추가 (Service 11 · 저장 4 · Controller 7 · 계산 서버는 가짜 응답)
  - 실제 연결 업로드 (2026-11-17 · 화면 TT-112): `card_a.xlsx` → 업로드 101번 · 14건 저장 · 계산 서버 토큰이 비면 503 + 오류 번호 확인
  - 미검증: 실제 홈택스 파일 형식(칸 이름은 추측값)

### [2026-11-12] 저장된 계산 결과로 대시보드 표시 (F-06-01 · PR #25)
- **작업 내용**: 화면 `calculation-service.js` · `App.jsx` (API는 바로 아래 11/12 「저장된 계산 결과 조회 API」)
- **검증 결과**: 테스트 4개 추가 · curl 회원 81 → 200 · 82 → 404

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

### [2026-11-10] 공통 응답 봉투(ApiResponse) · 오류 응답 통일 (TT-80 · TT-90 · PR #6)
- **작업 내용**:
  - `common/dto/ApiResponse` · `PageMeta` 추가 · `ErrorResponseDTO` 삭제 (봉투로 대체)
  - `common/exception`: BadRequest · NotFound · Conflict · NoCriteria · CalcRejected · CalcUnavailable 예외
  - `GlobalExceptionHandler`: 400 · 401 · 404 · 405 · 409 · 413 · 415 · 500 · 503을 한 모양으로 · 5xx만 `errorId`를 응답과 로그에 · `ResponseStatusException`도 같은 봉투(11/11)
  - `docs/api-rules.md` (주소 모양 · 성공 200 통일 · PUT만 · 오류 코드 표) · `.env.example` · `application.yml`
- **변경 사유**: 모든 응답을 `{success, data, meta, error}`로 통일 (팀 합의 11/09) · 모든 API가 쓰는 공통 작업이라 먼저 올림
- **검증 결과**: 이 브랜치 단독 `backend clean verify` 통과 (Spring Boot 4.1.0 · 11/11)

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
