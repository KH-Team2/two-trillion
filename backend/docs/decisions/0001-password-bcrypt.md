# 0001. 비밀번호는 BCrypt로 암호화하고 spring-security-crypto만 사용한다

- 날짜: 2026-11-08 · 상태: 적용
- 관련 코드: `common/config/PasswordConfig.java` · `member/service/MemberService.java` · `pom.xml`

## 결정

- 비밀번호는 `BCryptPasswordEncoder`로 암호화한 값만 `MEMBER.PASSWORD`에 저장한다.
- 의존성은 `spring-security-crypto`만 추가한다. `spring-boot-starter-security`(인증 필터)는 쓰지 않는다.
- 암호화기는 `PasswordConfig`의 `PasswordEncoder` 빈 하나를 회원가입 · 로그인 · 비밀번호 변경에서 같이 쓴다.
- 비밀번호 길이: 8자 이상 · UTF-8 기준 72바이트 이하 (BCrypt 입력 한도). 72바이트 초과는 400 `VALIDATION`.
- `MemberSignupRequestDTO.toString()`에서 비밀번호를 빼 로그에 남지 않게 한다.

## 이유

- 보안 스타터를 넣으면 모든 API에 기본 인증이 걸려 아직 없는 로그인 구조와 충돌한다. 암호화만 필요하므로 최소 의존성으로 한다.
- BCrypt는 72바이트 이후를 무시하므로, 그보다 긴 비밀번호는 다른 비밀번호와 같게 처리될 수 있어 막는다.

## 남은 확인

- 로그인 방식(JWT · 세션)이 정해지면 인증 필터 도입 여부를 다시 결정한다.
