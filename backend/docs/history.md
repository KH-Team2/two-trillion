# 작업 이력

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
