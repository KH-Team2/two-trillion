package com.hoperefund.member.service;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.member.dto.MemberInsertDTO;
import com.hoperefund.member.dto.MemberSignupRequestDTO;
import com.hoperefund.member.dto.MemberSignupResponseDTO;
import com.hoperefund.member.mapper.MemberMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 처리 Service
 * 입력값 업무 검증 → 중복 확인 → 비밀번호 BCrypt 암호화 → MEMBER 저장
 */
@Service
public class MemberService {

    private static final int MIN_SIGNUP_AGE = 14; // 가입 가능 최소 나이 (만)
    private static final int BCRYPT_MAX_BYTES = 72; // BCrypt 입력 한도
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    // TODO: 이메일 인증 구현 후 인증 결과로 Y 저장
    private static final String EMAIL_NOT_VERIFIED = "N";
    // TODO: MEMBER.DATA_ORIGIN 허용 값 팀 확인 필요 (화면 입력 데이터 = INPUT으로 임시 사용)
    private static final String DATA_ORIGIN_INPUT = "INPUT";

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;

    public MemberService(MemberMapper memberMapper, PasswordEncoder passwordEncoder) {
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public MemberSignupResponseDTO signup(MemberSignupRequestDTO request) {

        String loginId = request.loginId().trim().toLowerCase(Locale.ROOT);
        String email = request.email().trim().toLowerCase(Locale.ROOT);

        validatePassword(request.password(), request.passwordConfirm());
        validateBirthDate(request.birthDate());

        // 탈퇴 회원 아이디 형식과 겹치지 않게 막음
        if (loginId.startsWith("withdrawn_")) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "INVALID_LOGIN_ID", "사용할 수 없는 아이디예요.");
        }
        if (memberMapper.countByLoginId(loginId) > 0) {
            throw new BusinessException(
                    HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "이미 사용 중인 아이디입니다.");
        }
        if (memberMapper.countByEmail(email) > 0) {
            throw new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 사용 중인 이메일입니다.");
        }

        MemberInsertDTO member = new MemberInsertDTO();
        member.setLoginId(loginId);
        member.setEmail(email);
        member.setPassword(passwordEncoder.encode(request.password())); // BCrypt 암호문만 저장
        member.setBirthDate(request.birthDate());
        member.setGender(request.gender());
        member.setTermsVersion(request.termsVersion().trim());
        member.setPrivacyVersion(request.privacyVersion().trim());
        member.setFinanceVersion(request.financeVersion().trim());
        member.setEmailVerifiedYn(EMAIL_NOT_VERIFIED);
        member.setDataOrigin(DATA_ORIGIN_INPUT);

        try {
            memberMapper.insertMember(member);
        } catch (DuplicateKeyException e) {
            // 동시에 가입 요청이 들어와 번호·아이디가 겹친 경우
            throw new BusinessException(
                    HttpStatus.CONFLICT, "DUPLICATE_MEMBER", "잠시 후 다시 시도해 주세요.");
        }

        return new MemberSignupResponseDTO(member.getMemberId(), loginId, "회원가입이 완료되었습니다.");
    }

    // 비밀번호 확인 일치 · BCrypt 바이트 한도 검사
    private void validatePassword(String password, String passwordConfirm) {
        if (!password.equals(passwordConfirm)) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION", "비밀번호 확인이 일치하지 않아요.");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BusinessException(HttpStatus.BAD_REQUEST, "VALIDATION", "비밀번호가 너무 길어요.");
        }
    }

    // 만 14세 이상만 가입 (한국 시간 기준)
    private void validateBirthDate(LocalDate birthDate) {
        LocalDate today = LocalDate.now(KOREA);
        if (birthDate.isAfter(today)) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "VALIDATION", "미래의 생년월일은 입력할 수 없습니다.");
        }
        if (Period.between(birthDate, today).getYears() < MIN_SIGNUP_AGE) {
            throw new BusinessException(
                    HttpStatus.BAD_REQUEST, "UNDER_AGE", "본 서비스는 만 14세 이상부터 회원가입할 수 있습니다.");
        }
    }
}
