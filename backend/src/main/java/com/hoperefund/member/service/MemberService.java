package com.hoperefund.member.service;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.member.dto.MemberAvailabilityResponseDTO;
import com.hoperefund.member.dto.MemberInsertDTO;
import com.hoperefund.member.dto.MemberSignupRequestDTO;
import com.hoperefund.member.dto.MemberSignupResponseDTO;
import com.hoperefund.member.mapper.MemberMapper;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.time.ZoneId;
import java.util.Locale;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 회원가입 · 중복확인 (F-01-01).
 * 가입 순서: 아이디 → 이메일 → 만 14세 → 비밀번호 → 이메일 인증 → BCrypt 암호화 → MEMBER 저장
 * 이메일 인증 검사는 설정(member.email-verification.required)으로 켜고 끈다.
 * 메일 발송이 아직 없어 기본은 꺼 둔다 → 인증 없이 가입하고 EMAIL_VERIFIED_YN = 'N'.
 */
@Slf4j
@Service
public class MemberService {

    private static final int MIN_SIGNUP_AGE = 14; // 가입 가능 최소 나이 (만)
    private static final int BCRYPT_MAX_BYTES = 72; // BCrypt 는 72바이트까지만 쓴다
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private static final String EMAIL_VERIFIED = "Y";
    private static final String EMAIL_NOT_VERIFIED = "N";
    private static final String ROLE_USER = "USER"; // MEMBER.ROLE 기본값 (INSERT 에서는 뺀다)
    // TODO: MEMBER.DATA_ORIGIN 허용 값 팀 확인 필요 (화면 입력 데이터 = INPUT 으로 임시 사용)
    private static final String DATA_ORIGIN_INPUT = "INPUT";

    // 중복확인 API 용 형식 검사 (가입 요청은 MemberSignupRequestDTO 의 @Valid 가 같은 규칙으로 검사)
    private static final Pattern LOGIN_ID_PATTERN = Pattern.compile("^[a-z0-9_]{4,50}$");
    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    private final MemberMapper memberMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailVerificationService emailVerificationService;
    private final boolean verificationRequired; // true 면 가입 전에 이메일 인증을 꼭 마쳐야 한다

    // application.yml 의 설정 값(@Value)을 함께 받으므로 생성자를 직접 쓴다
    @Autowired
    public MemberService(
            MemberMapper memberMapper,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService,
            @Value("${member.email-verification.required:false}") boolean verificationRequired) {
        this.memberMapper = memberMapper;
        this.passwordEncoder = passwordEncoder;
        this.emailVerificationService = emailVerificationService;
        this.verificationRequired = verificationRequired;
    }

    // 테스트용: 이메일 인증 검사를 켠 상태로 만든다
    MemberService(
            MemberMapper memberMapper,
            PasswordEncoder passwordEncoder,
            EmailVerificationService emailVerificationService) {
        this(memberMapper, passwordEncoder, emailVerificationService, true);
    }

    @Transactional
    public MemberSignupResponseDTO signup(MemberSignupRequestDTO request) {
        String loginId = normalize(request.getLoginId());
        String email = normalize(request.getEmail());

        // 아이디: 사용 제한 → 중복 (탈퇴 회원 · 가상 회원 · admin 도 모두 중복으로 본다)
        validateLoginIdNotRestricted(loginId);
        if (memberMapper.countByLoginId(loginId) > 0) {
            throw duplicateLoginId();
        }
        if (memberMapper.countByEmail(email) > 0) {
            throw duplicateEmail();
        }
        validateBirthDate(request.getBirthDate());
        validatePassword(request.getPassword(), request.getPasswordConfirm());

        // 화면에서 인증을 마쳤더라도 서버에서 한 번 더 확인한다 (화면 검사는 우회할 수 있으므로)
        if (verificationRequired) {
            String verificationId = request.getVerificationId();
            if (verificationId == null || verificationId.isBlank()) {
                throw badRequest("EMAIL_NOT_VERIFIED", "이메일 인증을 완료해 주세요");
            }
            emailVerificationService.requireVerified(verificationId, email);
        }

        MemberInsertDTO member = new MemberInsertDTO();
        member.setLoginId(loginId);
        member.setEmail(email);
        // 원래 비밀번호는 저장하지 않는다
        member.setPassword(passwordEncoder.encode(request.getPassword()));
        member.setBirthDate(request.getBirthDate());
        member.setGender(request.getGender());
        member.setTermsVersion(request.getTermsVersion().trim());
        member.setPrivacyVersion(request.getPrivacyVersion().trim());
        member.setFinanceVersion(request.getFinanceVersion().trim());
        member.setEmailVerifiedYn(verificationRequired ? EMAIL_VERIFIED : EMAIL_NOT_VERIFIED);
        member.setDataOrigin(DATA_ORIGIN_INPUT);

        try {
            // 이후 member.getMemberId() 에 새 번호가 들어 있다
            memberMapper.insertMember(member);
        } catch (DuplicateKeyException e) {
            // 가입 요청이 동시에 들어와 번호 · 아이디가 겹친 경우
            throw new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_MEMBER", "잠시 후 다시 시도해 주세요");
        } catch (DataAccessException e) {
            // 그 밖의 DB 오류 (필수 칸 누락 · 제약조건 위반 등) → 원인은 서버 로그에만 남긴다
            log.error("회원 저장 실패: {}", e.getClass().getSimpleName(), e);
            throw signupFailed();
        }

        // 같은 인증으로 다시 가입하지 못하게 사용 처리
        if (verificationRequired) {
            emailVerificationService.complete(request.getVerificationId());
        }

        // createdAt: DB 기본값(SYSDATE)과 같은 시점의 서버 시각 · name: 실명을 받지 않아 null
        Long memberId = member.getMemberId();
        LocalDateTime createdAt = LocalDateTime.now(KOREA);
        return new MemberSignupResponseDTO(memberId, loginId, email, null, ROLE_USER, createdAt);
    }

    // 아이디 중복확인: 형식 오류 · 사용 제한은 400, 중복이면 available = false
    public MemberAvailabilityResponseDTO checkLoginId(String rawLoginId) {
        String loginId = normalize(rawLoginId);
        if (!LOGIN_ID_PATTERN.matcher(loginId).matches()) {
            throw badRequest("VALIDATION", "아이디는 영문·숫자·밑줄 4~50자로 입력해 주세요");
        }
        validateLoginIdNotRestricted(loginId);

        if (memberMapper.countByLoginId(loginId) > 0) {
            return new MemberAvailabilityResponseDTO(false, "이미 사용 중인 아이디예요");
        }
        return new MemberAvailabilityResponseDTO(true, "사용 가능한 아이디예요");
    }

    // 이메일 중복확인: 형식 오류는 400, 중복이면 available = false
    public MemberAvailabilityResponseDTO checkEmail(String rawEmail) {
        String email = normalize(rawEmail);
        if (email.length() > 100 || !EMAIL_PATTERN.matcher(email).matches()) {
            throw badRequest("VALIDATION", "이메일 형식을 확인해 주세요");
        }

        if (memberMapper.countByEmail(email) > 0) {
            return new MemberAvailabilityResponseDTO(false, "이미 가입된 이메일이에요");
        }
        return new MemberAvailabilityResponseDTO(true, "사용 가능한 이메일이에요");
    }

    // 앞뒤 공백을 지우고 소문자로 → "Hope" 와 "hope" 를 같은 아이디로 본다
    private static String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    // withdrawn_ 은 탈퇴 회원 아이디에 붙이는 앞글자라 새 가입에는 쓸 수 없다
    private static void validateLoginIdNotRestricted(String loginId) {
        if (loginId.startsWith("withdrawn_")) {
            throw badRequest("INVALID_LOGIN_ID", "사용할 수 없는 아이디예요");
        }
    }

    private static BusinessException badRequest(String code, String message) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, message);
    }

    private static BusinessException signupFailed() {
        HttpStatus status = HttpStatus.INTERNAL_SERVER_ERROR; // 500 → 화면에는 공통 문구 + errorId
        return new BusinessException(status, "SIGNUP_FAILED", "가입 처리 중 오류가 발생했습니다");
    }

    private static BusinessException duplicateLoginId() {
        return new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_LOGIN_ID", "이미 사용 중인 아이디예요");
    }

    private static BusinessException duplicateEmail() {
        return new BusinessException(HttpStatus.CONFLICT, "DUPLICATE_EMAIL", "이미 가입된 이메일이에요");
    }

    // 비밀번호 확인 일치 · BCrypt 바이트 한도 검사 (한글은 1글자 3바이트라 글자 수가 아니라 바이트로 센다)
    // 비밀번호 재설정(PasswordResetService)도 같은 규칙을 쓴다
    static void validatePassword(String password, String passwordConfirm) {
        if (!password.equals(passwordConfirm)) {
            throw badRequest("VALIDATION", "비밀번호 확인이 일치하지 않아요");
        }
        if (password.getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw badRequest("VALIDATION", "비밀번호가 너무 길어요");
        }
    }

    // 만 14세 이상만 가입 (한국 시간 기준)
    private static void validateBirthDate(LocalDate birthDate) {
        LocalDate today = LocalDate.now(KOREA);
        if (birthDate.isAfter(today)) {
            throw badRequest("VALIDATION", "생년월일을 확인해 주세요");
        }
        if (Period.between(birthDate, today).getYears() < MIN_SIGNUP_AGE) {
            throw badRequest("UNDER_AGE", "만 14세 이상만 가입할 수 있어요");
        }
    }
}
