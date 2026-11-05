# API 공통 규칙 (11/03 · 이우진 제안 · back 코드 기준)

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
- 로그인 방식(세션 / 토큰)은 Spring Security 담당이 정한다 (미정).
- **개발용 임시**: Security 연결 전까지만 요청 헤더 `X-Member-Id: 회원번호` (화면 `.env`의 `VITE_DEV_MEMBER_ID` · 개발 서버에서만 보냄). 연결되면 헤더를 없앤다.
- 로그인이 필요한데 정보가 없으면 401 `UNAUTHORIZED`.

## 3. GET / POST / PUT / DELETE 쓰는 기준

| 방식 | 언제 | 본문 | 예 |
| --- | --- | --- | --- |
| GET | 조회만 (DB를 바꾸지 않음) | 없음 | `GET /api/incomes/2026` · `GET /api/benefits/2026` |
| POST | 새로 만들기 · 실행해서 결과가 생김 | 필요하면 | `POST /api/calculations/2026` (계산 + 결과 저장) |
| PUT | 한 건 전체 저장 (없으면 새로 · 있으면 덮어쓰기) | **모든 칸** | `PUT /api/incomes/2026` |
| DELETE | 삭제 | 없음 | `DELETE /api/card-uploads/{id}` |

- PUT은 보낸 값으로 전체를 덮어쓴다 → 화면은 GET으로 불러온 값에 고친 칸을 합쳐 모든 칸을 보낸다 (빠진 칸은 0 · N이 됨).
- PATCH(일부만 수정)는 쓰지 않는다.

## 4. 주고받는 데이터 모양

- JSON · 이름은 camelCase (`totalSalary`) — DB 칸(`TOTAL_SALARY`)과 MyBatis가 자동으로 맞춤
- 금액: 원 단위 정수 (소수 없음)
- 날짜: `"2026-11-03"` · 일시: `"2026-11-03T10:20:30"`
- 여부: DB에 저장하는 값은 `"Y"` / `"N"` (예: `smeReductionYn`) · 판정 결과처럼 "모름"이 있으면 `true` / `false` / `null` (예: 혜택 진단 `eligible`)
- 성공하면 결과를 그대로 보낸다 (감싸지 않음) · 목록은 배열

## 5. 오류 응답 모양

모든 오류는 한 가지 모양: `{"error": "코드", "message": "화면에 그대로 보여 줄 문구"}`

| 상태 | error | 언제 |
| --- | --- | --- |
| 400 | `VALIDATION` | 입력값 오류 (빈 칸 · 범위 밖 · 형식 · 업무 규칙 위반) |
| 400 | `NO_CRITERIA` | 기준값이 없는 연도 |
| 400 | `CALC_REJECTED` | 계산 서버가 입력을 거부 |
| 401 | `UNAUTHORIZED` | 로그인 안 함 |
| 403 | `FORBIDDEN` | 권한 없음 · 남의 데이터 (F-09-02 · ACCESS_LOG에 DENIED) — Security 연결 후 |
| 404 | `NOT_FOUND` | 데이터 · 주소 없음 |
| 405 | `METHOD_NOT_ALLOWED` | 주소는 있는데 방식이 틀림 (예: 계산 주소에 GET) |
| 409 | `CONFLICT` | 동시 저장 충돌 |
| 500 | `SERVER_ERROR` | 그 밖의 서버 오류 |
| 503 | `○○_UNAVAILABLE` | 외부 서버 연결 불가 (예: `CALC_UNAVAILABLE`) |

- 오류 문구에 SQL · 스택 트레이스 · DB 주소를 넣지 않는다 (원인은 서버 로그에만).
- Spring은 `GlobalExceptionHandler` 하나에서 이 모양으로 바꾼다 (이 레포 참고).

## 6. 예외: 서버끼리 부르는 내부 API

- 계산 서버(FastAPI)의 `POST /calculate` · `POST /benefit`은 화면에 열지 않는 내부 호출이라 위 주소 규칙의 예외.
- `X-Calc-Token` 헤더로만 받고, Spring 서버만 부른다. 오류 모양(`{"error","message"}`)은 같다.

## 7. 이 레포의 API (명세 예시)

| Method | URI | 설명 |
| --- | --- | --- |
| GET | `/api/incomes/{taxYear}` | 소득 · 공제 정보 조회 (F-02-01) |
| PUT | `/api/incomes/{taxYear}` | 소득 · 공제 정보 저장 (F-02-01) |
| POST | `/api/calculations/{taxYear}` | 예상 세액 계산 + 결과 저장 (F-03-01) |
| GET | `/api/benefits/{taxYear}` | 청년 혜택 자격 진단 · 저장 안 함 (F-03-02) |
