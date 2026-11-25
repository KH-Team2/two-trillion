package com.hoperefund.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.member.dto.MemberProfileDTO;
import com.hoperefund.member.dto.MemberProfileRequestDTO;
import com.hoperefund.member.dto.MemberProfileResponseDTO;
import com.hoperefund.member.dto.PasswordChangeRequestDTO;
import com.hoperefund.member.mapper.MemberMapper;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 회원정보 · 자격 정보 수정 Service 테스트 (F-01-04)
 * 조회(이메일 가림 · 없음) · 수정(저장 · 없음 · DB 값 제한 위반) · 비밀번호 변경(현재 비밀번호 · 새 비밀번호 규칙)을 확인한다.
 */
class MemberProfileServiceTest {

    private static final long MEMBER_ID = 7L;
    private static final String CURRENT_PASSWORD = "oldPassword1!";
    private static final String NEW_PASSWORD = "newPassword1!";
    private static final LocalDate HIRE_DATE = LocalDate.of(2023, 3, 1);

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    private MemberMapper memberMapper;
    private MemberProfileService service;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        service = new MemberProfileService(memberMapper, passwordEncoder);
        String currentHash = passwordEncoder.encode(CURRENT_PASSWORD);
        given(memberMapper.selectProfile(MEMBER_ID)).willReturn(profileRow());
        given(memberMapper.selectPassword(MEMBER_ID)).willReturn(currentHash);
    }

    private static MemberProfileDTO profileRow() {
        MemberProfileDTO m = new MemberProfileDTO();
        m.setLoginId("test1234");
        m.setEmail("hope@example.com");
        m.setBirthDate(LocalDate.of(1999, 5, 20));
        m.setGender("F");
        m.setMilitaryMonths(0);
        m.setHomelessHeadYn("N");
        m.setUpdatedAt(LocalDateTime.of(2026, 11, 21, 10, 0));
        return m;
    }

    private static MemberProfileRequestDTO request(int militaryMonths) {
        return new MemberProfileRequestDTO("M", militaryMonths, HIRE_DATE, "SME", "Y");
    }

    // 비밀번호 변경 요청 (현재 · 새 · 새 확인)
    private static PasswordChangeRequestDTO change(String current, String next, String confirm) {
        return new PasswordChangeRequestDTO(current, next, confirm);
    }

    // DB 값 제한 위반(ORA-02290) 예외를 만든다 · constraint 는 오류 문장에 찍히는 제약조건 이름
    private static DataIntegrityViolationException checkViolation(String constraint) {
        String oracleMessage = "ORA-02290: check constraint (HOPE." + constraint + ") violated";
        SQLIntegrityConstraintViolationException cause =
                new SQLIntegrityConstraintViolationException(oracleMessage);
        return new DataIntegrityViolationException("check", cause);
    }

    // 오류(e)가 그 상태 · 코드 · 문구의 BusinessException 인지 확인 (예외가 안 났으면 e 는 null → 실패)
    private static void assertError(Throwable e, HttpStatus status, String code, String message) {
        assertThat(e).isInstanceOf(BusinessException.class);
        BusinessException be = (BusinessException) e;
        assertThat(be.getStatus()).isEqualTo(status);
        assertThat(be.getCode()).isEqualTo(code);
        assertThat(be.getMessage()).isEqualTo(message);
    }

    // ---------- 조회 ----------

    // 조회 → 이메일은 앞 2글자만 · 권한 · 상태는 응답에 없음
    @Test
    void getProfileMasksEmail() {
        MemberProfileResponseDTO response = service.getProfile(MEMBER_ID);

        assertThat(response.getLoginId()).isEqualTo("test1234");
        assertThat(response.getMaskedEmail()).isEqualTo("ho***@example.com");
        assertThat(response.getGender()).isEqualTo("F");
        assertThat(response.getHireDate()).isNull();
    }

    // 없거나 탈퇴한 회원 → 404
    @Test
    void getProfileNotFound() {
        given(memberMapper.selectProfile(MEMBER_ID)).willReturn(null);

        Throwable e = catchThrowable(() -> service.getProfile(MEMBER_ID));
        assertError(e, HttpStatus.NOT_FOUND, "NOT_FOUND", "회원 정보를 찾을 수 없어요");
    }

    // 이메일 가리기: 앞이 1글자 · @ 없음 · 빈 값
    @Test
    void maskEmailShortAndInvalid() {
        assertThat(MemberProfileService.maskEmail("a@b.com")).isEqualTo("a***@b.com");
        assertThat(MemberProfileService.maskEmail("abc")).isEqualTo("***");
        assertThat(MemberProfileService.maskEmail(null)).isNull();
    }

    // ---------- 수정 ----------

    // 수정 → 로그인한 회원번호로 UPDATE 후 다시 조회한 값을 돌려줌
    @Test
    void updateProfileSavesAndReturnsProfile() {
        MemberProfileRequestDTO request = request(18);
        given(memberMapper.updateProfile(MEMBER_ID, request)).willReturn(1);

        MemberProfileResponseDTO response = service.updateProfile(MEMBER_ID, request);

        verify(memberMapper).updateProfile(MEMBER_ID, request);
        assertThat(response.getLoginId()).isEqualTo("test1234");
    }

    // 고친 행이 없음(없거나 탈퇴) → 404
    @Test
    void updateProfileNotFound() {
        given(memberMapper.updateProfile(anyLong(), any())).willReturn(0);

        Throwable e = catchThrowable(() -> service.updateProfile(MEMBER_ID, request(0)));
        assertError(e, HttpStatus.NOT_FOUND, "NOT_FOUND", "회원 정보를 찾을 수 없어요");
    }

    // 군 복무 제한 위반(CK_MEMBER_MILITARY) → 400 「0 이상으로 입력해 주세요」
    @Test
    void updateProfileMilitaryConstraintViolation() {
        DataIntegrityViolationException violation = checkViolation("CK_MEMBER_MILITARY");
        given(memberMapper.updateProfile(anyLong(), any())).willThrow(violation);

        Throwable e = catchThrowable(() -> service.updateProfile(MEMBER_ID, request(-1)));
        assertError(e, HttpStatus.BAD_REQUEST, "VALIDATION", "0 이상으로 입력해 주세요");
    }

    // 그 밖의 제한 위반 → 400 일반 문구 (ORA 코드를 화면에 보내지 않음)
    @Test
    void updateProfileOtherConstraintViolation() {
        DataIntegrityViolationException violation = checkViolation("CK_MEMBER_GENDER");
        given(memberMapper.updateProfile(anyLong(), any())).willThrow(violation);

        Throwable e = catchThrowable(() -> service.updateProfile(MEMBER_ID, request(0)));
        assertError(e, HttpStatus.BAD_REQUEST, "VALIDATION", "입력값을 확인해 주세요");
    }

    // ---------- 비밀번호 변경 ----------

    // 비밀번호 변경 → 새 비밀번호를 BCrypt 로 저장 (원문 저장 안 함)
    @Test
    void changePasswordSavesEncodedPassword() {
        PasswordChangeRequestDTO request = change(CURRENT_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);

        service.changePassword(MEMBER_ID, request);

        ArgumentCaptor<String> saved = ArgumentCaptor.forClass(String.class);
        verify(memberMapper).updatePassword(eq(MEMBER_ID), saved.capture());
        assertThat(saved.getValue()).isNotEqualTo(NEW_PASSWORD);
        assertThat(passwordEncoder.matches(NEW_PASSWORD, saved.getValue())).isTrue();
    }

    // 현재 비밀번호 틀림 → 400 CURRENT_PASSWORD_MISMATCH · 저장 안 함
    @Test
    void changePasswordWrongCurrentPassword() {
        PasswordChangeRequestDTO request = change("wrong1234!", NEW_PASSWORD, NEW_PASSWORD);

        Throwable e = catchThrowable(() -> service.changePassword(MEMBER_ID, request));
        String code = "CURRENT_PASSWORD_MISMATCH";
        assertError(e, HttpStatus.BAD_REQUEST, code, "현재 비밀번호가 맞지 않아요");
        verify(memberMapper, never()).updatePassword(anyLong(), anyString());
    }

    // 새 비밀번호 확인 불일치 → 400 · 저장 안 함
    @Test
    void changePasswordConfirmMismatch() {
        PasswordChangeRequestDTO request = change(CURRENT_PASSWORD, NEW_PASSWORD, "different1!");

        Throwable e = catchThrowable(() -> service.changePassword(MEMBER_ID, request));
        assertError(e, HttpStatus.BAD_REQUEST, "VALIDATION", "비밀번호 확인이 일치하지 않아요");
        verify(memberMapper, never()).updatePassword(anyLong(), anyString());
    }

    // 현재와 같은 비밀번호 → 400 · 저장 안 함
    @Test
    void changePasswordSameAsCurrent() {
        PasswordChangeRequestDTO request =
                change(CURRENT_PASSWORD, CURRENT_PASSWORD, CURRENT_PASSWORD);

        Throwable e = catchThrowable(() -> service.changePassword(MEMBER_ID, request));
        String message = "현재 비밀번호와 다른 비밀번호를 입력해 주세요";
        assertError(e, HttpStatus.BAD_REQUEST, "VALIDATION", message);
        verify(memberMapper, never()).updatePassword(anyLong(), anyString());
    }

    // 없거나 탈퇴한 회원 → 404
    @Test
    void changePasswordNotFound() {
        given(memberMapper.selectPassword(MEMBER_ID)).willReturn(null);
        PasswordChangeRequestDTO request = change(CURRENT_PASSWORD, NEW_PASSWORD, NEW_PASSWORD);

        Throwable e = catchThrowable(() -> service.changePassword(MEMBER_ID, request));
        assertError(e, HttpStatus.NOT_FOUND, "NOT_FOUND", "회원 정보를 찾을 수 없어요");
    }
}
