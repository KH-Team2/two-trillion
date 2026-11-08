# 작업 이력

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
