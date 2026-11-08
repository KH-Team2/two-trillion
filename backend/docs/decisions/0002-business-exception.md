# 0002. 업무 오류는 BusinessException으로 던지고 GlobalExceptionHandler가 응답한다

- 날짜: 2026-11-08 · 상태: 적용
- 관련 코드: `common/exception/BusinessException.java` · `common/exception/GlobalExceptionHandler.java` (`handleBusiness`)

## 결정

- Service는 업무 규칙 위반 시 `new BusinessException(HttpStatus, "오류코드", "화면 문구")`를 던진다.
- `GlobalExceptionHandler.handleBusiness`가 지정된 상태 코드로 `{"error": 코드, "message": 문구}`를 응답한다.
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

## 이유

- 기능마다 Controller에서 오류 응답을 만들면 형식이 달라진다. AGENTS.md의 「오류 응답은 GlobalExceptionHandler 하나로」 규칙을 따른다.

## 남은 확인

- 화면 문구를 기능명세서 「7_오류 메시지」 시트와 대조해야 한다 (시트 열람 불가 · 확인 필요).
