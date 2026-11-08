package com.hoperefund.member.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.member.dto.MemberInsertDTO;
import com.hoperefund.member.dto.MemberSignupRequestDTO;
import com.hoperefund.member.dto.MemberSignupResponseDTO;
import com.hoperefund.member.mapper.MemberMapper;
import java.time.LocalDate;
import java.time.ZoneId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 회원가입 Service 테스트
 * DB 없이 Mapper를 대신해 업무 검증 · 암호화 · 저장 값을 확인한다.
 */
class MemberServiceTest {

    private MemberMapper memberMapper;
    private PasswordEncoder passwordEncoder;
    private MemberService memberService;

    @BeforeEach
    void setUp() {
        memberMapper = mock(MemberMapper.class);
        passwordEncoder = new BCryptPasswordEncoder();
        memberService = new MemberService(memberMapper, passwordEncoder);
    }

    private MemberSignupRequestDTO request(
            String loginId, String password, String confirm, LocalDate birthDate) {
        return new MemberSignupRequestDTO(
                loginId,
                " Hope@Example.com ",
                password,
                confirm,
                birthDate,
                "M",
                true,
                true,
                true,
                "v1.0",
                "v1.0",
                "v1.0");
    }

    private MemberSignupRequestDTO validRequest() {
        return request("Hope_User", "password123", "password123", LocalDate.of(2000, 1, 15));
    }

    // 정상 가입: 소문자 정리 · BCrypt 암호화 · 서버 지정 값 저장
    @Test
    void signupSavesEncryptedMember() {

        given(memberMapper.countByLoginId("hope_user")).willReturn(0);
        given(memberMapper.countByEmail("hope@example.com")).willReturn(0);
        given(memberMapper.insertMember(any()))
                .willAnswer(
                        invocation -> {
                            invocation.<MemberInsertDTO>getArgument(0).setMemberId(7L);
                            return 1;
                        });

        MemberSignupResponseDTO response = memberService.signup(validRequest());

        ArgumentCaptor<MemberInsertDTO> captor = ArgumentCaptor.forClass(MemberInsertDTO.class);
        verify(memberMapper).insertMember(captor.capture());
        MemberInsertDTO saved = captor.getValue();

        assertThat(saved.getLoginId()).isEqualTo("hope_user");
        assertThat(saved.getEmail()).isEqualTo("hope@example.com");
        assertThat(saved.getPassword()).isNotEqualTo("password123").startsWith("$2");
        assertThat(passwordEncoder.matches("password123", saved.getPassword())).isTrue();
        assertThat(saved.getGender()).isEqualTo("M");
        assertThat(saved.getEmailVerifiedYn()).isEqualTo("N");
        assertThat(saved.getDataOrigin()).isEqualTo("INPUT");
        assertThat(response.memberId()).isEqualTo(7L);
        assertThat(response.loginId()).isEqualTo("hope_user");
    }

    // 비밀번호 확인 불일치 → 400
    @Test
    void passwordMismatchThrows() {

        assertThatThrownBy(
                        () ->
                                memberService.signup(
                                        request(
                                                "hope_user",
                                                "password123",
                                                "password999",
                                                LocalDate.of(2000, 1, 15))))
                .isInstanceOf(BusinessException.class)
                .hasMessage("비밀번호 확인이 일치하지 않아요.");

        verify(memberMapper, never()).insertMember(any());
    }

    // 만 14세 미만 → UNDER_AGE
    @Test
    void underAgeThrows() {

        LocalDate thirteen = LocalDate.now(ZoneId.of("Asia/Seoul")).minusYears(13);

        assertThatThrownBy(
                        () ->
                                memberService.signup(
                                        request(
                                                "hope_user",
                                                "password123",
                                                "password123",
                                                thirteen)))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("UNDER_AGE");
    }

    // 탈퇴 회원 형식 아이디 → INVALID_LOGIN_ID
    @Test
    void withdrawnPrefixLoginIdThrows() {

        assertThatThrownBy(
                        () ->
                                memberService.signup(
                                        request(
                                                "withdrawn_abc",
                                                "password123",
                                                "password123",
                                                LocalDate.of(2000, 1, 15))))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("INVALID_LOGIN_ID");
    }

    // 중복 아이디 → DUPLICATE_LOGIN_ID
    @Test
    void duplicateLoginIdThrows() {

        given(memberMapper.countByLoginId("hope_user")).willReturn(1);

        assertThatThrownBy(() -> memberService.signup(validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("DUPLICATE_LOGIN_ID");

        verify(memberMapper, never()).insertMember(any());
    }

    // 중복 이메일 → DUPLICATE_EMAIL
    @Test
    void duplicateEmailThrows() {

        given(memberMapper.countByLoginId("hope_user")).willReturn(0);
        given(memberMapper.countByEmail("hope@example.com")).willReturn(1);

        assertThatThrownBy(() -> memberService.signup(validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("DUPLICATE_EMAIL");
    }

    // 저장 중 키 충돌 → DUPLICATE_MEMBER
    @Test
    void duplicateKeyOnInsertThrows() {

        given(memberMapper.insertMember(any())).willThrow(new DuplicateKeyException("중복"));

        assertThatThrownBy(() -> memberService.signup(validRequest()))
                .isInstanceOf(BusinessException.class)
                .extracting("code")
                .isEqualTo("DUPLICATE_MEMBER");
    }
}
