# 0002. 업무 오류는 BusinessException으로 던지고 GlobalExceptionHandler가 응답한다

- 날짜: 2026-11-08 · 상태: 적용 · 수정: 2026-11-11 (공통 봉투 형식으로 변경)
- 관련 코드: `common/exception/BusinessException.java` · `common/exception/GlobalExceptionHandler.java` (`handleBusiness`)

## 결정

- Service는 업무 규칙 위반 시 `new BusinessException(HttpStatus, "오류코드", "화면 문구")`를 던진다.
- `GlobalExceptionHandler.handleBusiness`가 지정된 상태 코드로 공통 봉투를 응답한다 ([api-rules](../api-rules.md) 4·5번).
  - 4xx: `{"success": false, "data": null, "meta": null, "error": {"code": 코드, "message": 문구}}`
  - 5xx: 문구는 숨기고 `{"code": "SERVER_ERROR", "message": "잠시 후 다시 시도해 주세요.", "errorId": "..."}` · 같은 `errorId`와 원래 코드를 서버 로그에 남김
- 로그에는 오류 코드만 `log.warn`으로 남긴다 (아이디 · 이메일 · 생년월일 등 개인정보 제외).
- 상태 코드 기준: 형식·업무 입력 오류 400 · 중복(이미 있는 데이터) 409.

## 회원가입에서 쓰는 오류 코드

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `VALIDATION` | `@Valid` 실패 · JSON 형식 오류 · 비밀번호 확인 불일치 · 72바이트 초과 · 미래 생년월일 |
| 400 | `UNDER_AGE` | 만 14세 미만 |
| 400 | `INVALID_LOGIN_ID` | `withdrawn_`으로 시작하는 아이디 |
| 409 | `DUPLICATE_LOGIN_ID` | 아이디 중복 |
| 409 | `DUPLICATE_EMAIL` | 이메일 중복 |
| 409 | `DUPLICATE_MEMBER` | INSERT 중 키 중복 (동시 가입) |
| 400 | `VERIFICATION_FAILED` · `VERIFICATION_EXPIRED` · `VERIFICATION_LIMIT` | 인증 코드 틀림 · 만료 · 5번 실패 |
| 400 | `EMAIL_NOT_VERIFIED` | 이메일 인증 없이 가입 요청 |
| 500 | `SIGNUP_FAILED` → 응답은 `SERVER_ERROR` | DB 저장 실패 (로그에만 `SIGNUP_FAILED`) |

## 이유

- 기능마다 Controller에서 오류 응답을 만들면 형식이 달라진다. AGENTS.md의 「오류 응답은 GlobalExceptionHandler 하나로」 규칙을 따른다.

## 변경 이력

- 2026-11-11: 팀 공통 응답 봉투(`ApiResponse`) 도입에 맞춰 응답 모양 변경. 병합 때 `handleBusiness`가 빠져 업무 오류가 500으로 나가던 문제를 함께 수정.

## 남은 확인

- 회원 전용 코드(위 표)를 api-rules 오류 표에 올릴지 팀 확인 (화면이 이 코드로 분기함).

- 화면 문구를 기능명세서 「7_오류 메시지」 시트와 대조해야 한다 (시트 열람 불가 · 확인 필요).
