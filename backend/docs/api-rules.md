# API 공통 규칙 (11/03 · 이우진 제안 · back 코드 기준 · 11/09 응답 봉투 · 성공 200 통일 팀 합의)

> 개별 주소는 기능 담당자가 정하고, 주소를 지을 때 모두 아래 모양을 따른다.
> 이 back 레포의 API(소득 · 계산 · 혜택 진단)는 이 규칙대로 만들어져 있다 → 예시로 보면 된다.
> 팀에서 다른 안으로 정하면 이 문서와 코드를 맞춰 고친다.

## 1. 주소(URL) 모양

| 규칙 | 예 |
| --- | --- |
| 모두 `/api`로 시작 | `/api/incomes/2026` |
| 자원 이름은 소문자 복수형 명사 · 두 단어는 `-` | `/api/calculations` · `/api/card-uploads` |
| 동사를 주소에 넣지 않음 (동작은 GET/POST로 구분) | ❌ `/api/calculate` → ⭕ `POST /api/calculations/2026` |
| "그 연도 한 건"은 경로에 · 목록 거르기는 `?` 뒤에 | `/api/incomes/2026` · `/api/predictions?year=2026` |
| **본인 데이터 주소에 회원번호를 넣지 않음** | ❌ `/api/members/3/incomes` (숫자만 바꿔 남의 데이터 조회 = IDOR) |
| 권한별로 묶음 | 로그인 전 `/api/auth/**` · 관리자 `/api/admin/**` · 나머지 `/api/**` = 로그인한 회원 |

## 2. 로그인 정보 넘기는 방식

- 화면은 회원번호를 주소 · 본문에 **보내지 않는다**. 서버가 로그인 정보에서 회원을 정한다.
- 로그인 방식: **토큰(JWT)** (11/16 · [decisions/0004](decisions/0004-login-jwt.md)). `POST /api/auth/login`이 준 `accessToken`을 요청 헤더 `Authorization: Bearer <토큰>`으로 보낸다. 유효 2시간 · 리프레시 토큰 없음 (만료되면 다시 로그인).
- Controller는 회원번호를 `@AuthenticationPrincipal Long memberId`로 받는다 (토큰에서 꺼낸 값 · `common/config/SecurityConfig`).
- ~~개발용 임시 `X-Member-Id` 헤더~~ → 2026-11-17 없앰: 소득 · 계산 · 혜택 · 시뮬레이션 · 이력 · 그룹 통계 Controller 모두 `@AuthenticationPrincipal`로 바꿈 (카드 업로드 · 거래 내역은 11/19) · 화면은 `Authorization: Bearer <토큰>`만 보냄 (`X-Member-Id`만 보내면 401)
- 로그인이 필요한데 정보가 없으면 401 `UNAUTHORIZED`.

## 3. GET / POST / PUT / DELETE 쓰는 기준

| 방식 | 언제 | 본문 | 예 |
| --- | --- | --- | --- |
| GET | 조회만 (DB를 바꾸지 않음) | 없음 | `GET /api/incomes/2026` · `GET /api/benefits/2026` |
| POST | 새로 만들기 · 실행해서 결과가 생김 | 필요하면 | `POST /api/calculations/2026` (계산 + 결과 저장) |
| PUT | 한 건 전체 저장 (없으면 새로 · 있으면 덮어쓰기) | **모든 칸** | `PUT /api/incomes/2026` |
| DELETE | 삭제 | 없음 | `DELETE /api/card-uploads/{id}` |

- PUT은 보낸 값으로 전체를 덮어쓴다 → 화면은 GET으로 불러온 값에 고친 칸을 합쳐 모든 칸을 보낸다 (빠진 칸은 0 · N이 됨).
- PATCH(일부만 수정)는 쓰지 않는다. 수정은 모두 PUT (예: 문의 답변 `PUT /api/admin/inquiries/{id}/answer`).
- 성공 응답은 모두 200 (201 · 204 안 씀 · 11/09 팀 합의). 성공 · 실패는 봉투의 `success`로 구분하고, 돌려줄 데이터가 없으면(로그아웃 · 탈퇴) `data`는 `null`. 성공 문구("로그아웃되었습니다." 등)는 화면에서 띄운다.

## 4. 주고받는 데이터 모양

- JSON · 이름은 camelCase (`totalSalary`) — DB 칸(`TOTAL_SALARY`)과 MyBatis가 자동으로 맞춤
- 금액: 원 단위 정수 (소수 없음 · 정수 칸에 소수를 보내면 400 `VALIDATION` · 11/19 `application.yml` `accept-float-as-int: false`)
- 날짜: `"2026-11-03"` · 일시: `"2026-11-03T10:20:30"`
- 여부: DB에 저장하는 값은 `"Y"` / `"N"` (예: `smeReductionYn`) · 판정 결과처럼 "모름"이 있으면 `true` / `false` / `null` (예: 혜택 진단 `eligible`)
- 응답은 성공 · 실패 모두 **공통 봉투**로 감싼다 (11/09 팀 합의 · 선생님 샘플과 같은 모양 · `common/dto/ApiResponse`)

```json
성공(단건) {"success": true,  "data": {...}, "meta": null, "error": null}
성공(목록) {"success": true,  "data": [...], "meta": {"page": 1, "size": 20, "total": 135, "totalPages": 7}, "error": null}
실패       {"success": false, "data": null,  "meta": null, "error": {"code": "NOT_FOUND", "message": "..."}}
```

- Controller: `return ApiResponse.ok(결과);` · 목록은 `ApiResponse.ok(목록, PageMeta.of(page, size, total))` (페이지는 1부터)
- 화면: 성공이면 `data`를 꺼내 쓰고, 실패면 `error.message`를 띄운다 (`frontend/src/api/api-client.js`가 대신 해 줌)

## 5. 오류 응답 모양

모든 오류는 봉투의 `error`에 한 가지 모양: `{"code": "코드", "message": "화면에 그대로 보여 줄 문구"}` (+ 500 · 503에만 `"errorId"`)
서버 쪽 오류(500 · 503)에만 추적 번호 `errorId`가 더 붙는다 → 같은 번호가 서버 로그에 `[errorId=...]`로 남아 원인을 바로 찾는다.

```json
{"success": false, "data": null, "meta": null,
 "error": {"code": "SERVER_ERROR", "message": "잠시 후 다시 시도해 주세요.", "errorId": "a3f9c2d1"}}
```

| 상태 | error.code | 언제 |
| --- | --- | --- |
| 400 | `VALIDATION` | 입력값 오류 (빈 칸 · 범위 밖 · 형식 · 업무 규칙 위반 · 필수 쿼리 값 누락 · 업로드 파일 누락) |
| 400 | `NO_CRITERIA` | 기준값이 없는 연도 |
| 400 | `CALC_REJECTED` | 계산 서버가 입력을 거부 |
| 401 | `UNAUTHORIZED` | 로그인 안 함 (토큰 없음 · 위조 · 만료 · 로그아웃됨) |
| 401 | `LOGIN_FAILED` · `WITHDRAWN_MEMBER` · `LOGIN_LOCKED` | 로그인 실패 (`POST /api/auth/login`만 · 화면은 이 401에서 로그인 화면으로 보내지 않는다) |
| 403 | `FORBIDDEN` | 권한 없음 (예: 일반 회원이 관리자 API 호출) · ACCESS_LOG `DENIED` 기록은 아직 안 함 (IP 차단 429만 `DENIED` 기록) |
| 429 | `LOGIN_IP_BLOCKED` | 한 IP가 1분에 로그인 50번 이상 실패 → 12시간 로그인 막음 (`POST /api/auth/login`만 · D-16 · 따로 차단 목록 없이 ACCESS_LOG로 판단 · D-15 · 막힌 동안의 시도는 `DENIED`로 기록해 차단 시간이 늘지 않음) |
| 404 | `NOT_FOUND` | 데이터 · 주소 없음 · **남의 데이터도 404** (없는 것과 같은 응답 · F-09-02) |
| 405 | `METHOD_NOT_ALLOWED` | 주소는 있는데 방식이 틀림 (예: 계산 주소에 GET) |
| 409 | `CONFLICT` | 동시 저장 충돌 |
| 413 | `PAYLOAD_TOO_LARGE` | 업로드 파일이 허용 크기를 넘음 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 본문 형식이 JSON이 아님 (예: `text/plain`으로 PUT) |
| 500 | `SERVER_ERROR` | 그 밖의 서버 오류 · `errorId` 있음 |
| 503 | `○○_UNAVAILABLE` | 외부 서버 연결 불가 (예: `CALC_UNAVAILABLE`) · `errorId` 있음 |

- 오류 문구에 SQL · 스택 트레이스 · DB 주소 · DB 오류 코드(ORA-…)를 넣지 않는다 (원인은 서버 로그에만).
- `errorId`는 사용자 쪽 오류(400 · 401 · 403 · 404 · 405 · 409 · 413 · 415)엔 붙이지 않는다 — 문구만으로 고칠 수 있어서.
- Spring은 `GlobalExceptionHandler` 하나에서 이 모양으로 바꾼다 (이 레포 참고). Service는 예외를 던지기만 하면 된다.
- 상태 코드를 직접 정해 던지는 `ResponseStatusException`도 같은 봉투로 나간다 (정한 상태 그대로 · 코드 이름은 위 표 · 표에 없는 4xx는 `REQUEST_ERROR`). 5xx로 던지면 문구는 숨기고 `SERVER_ERROR` + `errorId`.

## 6. 예외: 서버끼리 부르는 내부 API

- 계산 서버(FastAPI)의 `POST /calculate` · `POST /benefit`은 화면에 열지 않는 내부 호출이라 위 주소 규칙의 예외.
- `X-Calc-Token` 헤더로만 받고, Spring 서버만 부른다. 내부 API라 봉투로 감싸지 않고 결과 그대로 · 오류는 `{"error","message"}` (Spring이 받아서 봉투로 바꿔 화면에 보냄).

## 7. 이 레포의 API (명세 예시)

| Method | URI | 설명 |
| --- | --- | --- |
| GET | `/api/incomes/{taxYear}` | 소득 · 공제 정보 조회 (F-02-01) |
| PUT | `/api/incomes/{taxYear}` | 소득 · 공제 정보 저장 (F-02-01) |
| POST | `/api/card-uploads` | 카드 · 홈택스 파일 업로드 · 계산 서버에서 해석 · 분류 후 저장 · 중복이면 409 · 결제수단 없으면 400 (F-02-02 · F-02-03) |
| GET | `/api/card-uploads` | 내 업로드 이력 · 최신순 (F-02-04) |
| DELETE | `/api/card-uploads/{uploadId}` | 업로드 삭제 · 거래도 같이 삭제 · 200 `data: null` · 없거나 남의 것이면 404 (F-02-04) |
| GET | `/api/expenses?year=` 또는 `?uploadId=` | 거래 목록 + 분류 근거 · 남의 업로드면 404 (F-02-04) |
| GET | `/api/expense-totals?year=` | 분류 + 결제수단별 합계 · 계산 서버 cardSpends 단위 (F-02-04) |
| POST | `/api/calculations/{taxYear}` | 예상 세액 계산 + 결과 저장 (F-03-01) |
| GET | `/api/calculations/{taxYear}` | 저장된 계산 결과 조회 · 가장 최근 1건 · 없으면 404 (F-06-01) |
| GET | `/api/benefits/{taxYear}` | 청년 혜택 자격 진단 · 저장 안 함 (F-03-02) |
| POST | `/api/simulations/{taxYear}` | 조건 변경 시뮬레이션 · 바꾼 입력값 전부를 받아 다시 계산하고 시나리오로 저장 · 남의 기준 결과면 404 (F-04-01) |
| GET | `/api/predictions?year={taxYear}` | 결과 이력 목록 · 현재와 시나리오 함께 · 최신순 · 없으면 빈 목록 (F-04-02) |
| GET | `/api/predictions/{predictionId}` | 결과 하나 다시 열기 · 항목별 내역 포함 · 없거나 남의 것이면 404 (F-04-02) |
| GET | `/api/group-stats/{taxYear}` | 내 성별·연령대 그룹과 총급여 구간 그룹의 통계 평균 · 못 찾은 그룹은 null (F-06-02) |
| GET | `/api/notifications/stream` | 실시간 알림 연결 (SSE · `text/event-stream`) · 이벤트 `notification` · 다시 연결할 때 `Last-Event-ID` 헤더로 놓친 알림만 다시 받음 · 회원당 연결 5개까지 (넘으면 가장 오래된 연결을 닫음) (F-09-03) |
| GET | `/api/notifications` | 내 알림 목록 · `{unreadCount, items}` · 안 읽은 개수 + 최근 20개 · 새것부터 (F-09-03) |
| PUT | `/api/notifications/read` | 안 읽은 알림 모두 읽음 · 본문 없음 → `null` · `READ_YN='Y'` · `READ_AT=SYSDATE` (F-09-03) |
| POST | `/api/consult/rooms` | 상담 요청 · 본문 없음 → `{roomId}` · 내 방이 있으면 그 방, 없으면 새로 만듦 (회원당 1개) · 관리자는 403 (F-07-03) |
| GET | `/api/consult/rooms/{roomId}/messages?beforeId=` | 지난 메시지 30개 · 오래된 순 · `beforeId`보다 작은 번호만 (없으면 가장 최근 30개) · 남의 방이면 404 (관리자는 모든 방) (F-07-03) |
| GET | `/api/admin/consult/rooms` | 관리자 상담 방 목록 · 최근 메시지 순 최대 100개 · 탈퇴 회원 방 제외 · 아이디 앞 절반만 보임 (예: `test****`) · `ADMIN`만 (F-07-03) |
| POST | `/api/auth/login-id-lookups` | 아이디 찾기 · 본문 `{email}` → `{maskedLoginId}`(예: `te******`) · 가입 정보 없음 · 탈퇴 · (인증을 켰을 때) 미인증이면 404 (F-01-03 · 제안 주소) |
| POST | `/api/auth/password-resets` | 비밀번호 재설정 인증 코드 메일 발송 · 본문 `{email}` → `{expiresAt, devCode}` · 10분 유효 · 대상 회원이 아니면 404 (F-01-03 · 제안 주소) |
| POST | `/api/auth/password-resets/completion` | 코드 확인 + 새 비밀번호 저장 · 본문 `{email, code, newPassword, newPasswordConfirm}` → `null` · 틀림 400 `RESET_CODE_FAILED` · 만료 · 5번 틀림 400 `RESET_CODE_EXPIRED` / `RESET_CODE_LIMIT` (F-01-03 · 제안 주소) |
| GET | `/api/members/me` | 내 회원정보 · `{loginId, maskedEmail, birthDate, gender, militaryMonths, hireDate, companySize, homelessHeadYn, updatedAt}` · 이메일은 가린 값만 · 탈퇴면 404 (F-01-04 · 제안 주소) |
| PUT | `/api/members/me` | 회원정보 · 자격 정보 저장 · 본문 `{gender, militaryMonths, hireDate, companySize, homelessHeadYn}` (모든 칸) → 저장된 회원정보 · 군 복무 음수 400 「0 이상으로 입력해 주세요」 · `ROLE` · `STATUS` 안 바뀜 (F-01-04 · 제안 주소) |
| PUT | `/api/members/me/password` | 비밀번호 변경 · 본문 `{currentPassword, newPassword, newPasswordConfirm}` → `null` · 현재 비밀번호 틀림 400 `CURRENT_PASSWORD_MISMATCH` (F-01-04 · 제안 주소) |
| POST | `/api/members/me/withdrawal` | 회원 탈퇴 · 본문 `{password}` (D-05 재확인) → `null` · `STATUS=WITHDRAWN` · `WITHDRAWN_AT=SYSDATE` · `ROLE=USER` · 아이디 · 이메일은 30일 유지 · 지금 토큰 무효 · 비밀번호 틀림 400 `CURRENT_PASSWORD_MISMATCH` · 이미 탈퇴 404 (F-01-05 · 제안 주소) |
| GET | `/api/admin/tax-criteria?taxYear=` | 관리자 전용 연도별 세법 기준값 (공제 항목 · 세율/공제 구간 · 혜택 조건) + 기준값이 있는 연도 목록 `years` · 연도를 비우면 가장 최근 연도 (F-08-01) |
| PUT | `/api/admin/tax-criteria` | 한 행의 값 수정 · 본문 `targetType`(DEDUCTION_TYPE/TAX_BRACKET/BENEFIT_CONDITION) + `id` + 값 · 근거 `sourceNote` 필수 · 연도 · 코드 · 종류는 안 바뀜 · `UPDATED_BY`(관리자) · `UPDATED_AT` 갱신 → 트리거가 `CRITERIA_HISTORY`에 이력 · 400 값 오류 · 404 없는 행 · 409 `DUPLICATE_CRITERIA` · 기록 남김 |
| POST | `/api/admin/tax-criteria` | 같은 코드로 행 하나 추가 (구간 추가) · 본문에 `taxYear` · `code` 포함 · 409 `DUPLICATE_CRITERIA` · 기록 남김 |
| POST | `/api/admin/tax-criteria/years` | 새 연도 추가 · 본문 `{"sourceYear":2026,"targetYear":2027}` · 세 테이블 통째로 복사 · 복사할 연도 없음 404 · 새 연도 이미 있음 409 `YEAR_ALREADY_EXISTS` · 기록 남김 |
| GET | `/api/admin/tax-criteria/history?targetType=&id=` | 한 행의 변경 이력 (`CRITERIA_HISTORY` · 최신순 최대 100줄) |
| GET | `/api/admin/members?keyword=&role=&status=&page=&size=` | 관리자 전용 회원 목록 · 이메일 일부 가림 · 계정 상태 · 권한 · 가입일 · `meta`에 쪽 정보 · 조회한 것도 기록 (F-08-02 ①) |
| PUT | `/api/admin/members/{memberId}/role` | 관리자 권한 부여 · 해제 · 본문 `{"role":"ADMIN"\|"USER"}` · 200 `data: null` · 자기 자신 해제 · 성별 없는 관리자 해제 · 같은 권한 · 탈퇴 회원이면 409 · 없으면 404 · 기록 남김 (F-01-06) |
| GET | `/api/admin/access-logs?startDate=&endDate=&ipAddress=&page=&size=` | 관리자 전용 접근 기록 목록 · 최신순 · `meta`에 쪽 정보 · 같은 IP 실패 수 포함 · 날짜 형식 · page(1~) · size(1~100) 틀리면 400 · 조회한 것도 기록 (F-08-02 ③) |
| GET | `/api/faqs` | FAQ 목록 조회 |
| GET | `/api/faq-categories` | FAQ 카테고리 목록 조회 |
| GET | `/api/faqs/{faqId}` | FAQ 상세 조회 |
| POST | `/api/faq-matches` | 자유 질문을 등록된 FAQ에 매칭 |

상담 채팅 실시간 주고받기는 REST가 아니라 WebSocket(STOMP)이에요 (F-07-03).
연결 `/ws` (CONNECT 헤더 `Authorization: Bearer {accessToken}`) · 받기 `SUBSCRIBE /sub/consult/{roomId}` (방 주인 회원 · 관리자만) · 보내기 `SEND /pub/consult/{roomId}` 본문 `{content}` (1~1000자) · 오류는 보낸 사람에게만 `/user/queue/errors`
