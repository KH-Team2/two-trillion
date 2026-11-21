package com.hoperefund.member.service;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.member.client.EmailSender;
import com.hoperefund.member.dto.LoginIdLookupResponseDTO;
import com.hoperefund.member.dto.PasswordResetCompleteRequestDTO;
import com.hoperefund.member.dto.PasswordResetDTO;
import com.hoperefund.member.dto.PasswordResetMemberDTO;
import com.hoperefund.member.dto.PasswordResetResponseDTO;
import com.hoperefund.member.mapper.MemberMapper;
import com.hoperefund.member.mapper.PasswordResetMapper;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 아이디 찾기 · 비밀번호 재설정 (F-01-03).
 *
 * <pre>
 * 아이디 찾기: 이메일 → 대상 회원 확인 → 일부를 가린 아이디 (예: te******)
 * 재설정: ① 이메일 → ② 6자리 코드 생성 · 메일 발송 · BCrypt 해시로 PASSWORD_RESET 저장 (10분 유효)
 *        → ③ 코드 확인 (틀릴 때마다 FAIL_COUNT + 1 · 5번이면 무효) → ④ 새 비밀번호 BCrypt 저장 → ⑤ USED_YN = 'Y'
 * </pre>
 *
 * 대상 회원: 탈퇴하지 않은 회원. 가입 이메일 인증을 켰으면(member.email-verification.required) EMAIL_VERIFIED_YN = 'Y' 인 회원만.
 * 비밀번호는 BCrypt 로 저장해 원래 값을 알 수 없어서 「찾기」는 없고 「재설정」만 있다.
 */
@Slf4j
@Service
public class PasswordResetService {

    static final int CODE_VALID_MINUTES = 10; // 인증 코드 유효 시간 (분)
    static final int MAX_FAIL_COUNT = 5; // 이 횟수만큼 틀리면 그 코드는 무효
    static final int VISIBLE_LOGIN_ID_CHARS = 2; // 아이디 찾기에서 보여 줄 앞글자 수

    static final String NOT_FOUND_MESSAGE = "가입 정보를 찾을 수 없어요";
    static final String RESEND_MESSAGE = "인증 코드를 다시 받아 주세요";
    static final String CODE_FAILED = "인증 코드가 맞지 않아요";

    private static final String STATUS_WITHDRAWN = "WITHDRAWN";
    private static final String EMAIL_VERIFIED = "Y";

    private final MemberMapper memberMapper;
    private final PasswordResetMapper passwordResetMapper;
    private final PasswordEncoder passwordEncoder;
    private final EmailSender emailSender; // 메일 발송 (MAIL_MODE: log = 로그만 · smtp = 실제 발송)
    private final Clock clock;
    private final boolean verificationRequired; // true 면 이메일 인증한 회원(EMAIL_VERIFIED_YN = 'Y')만
    private final boolean devCodeEnabled; // true 면 발송 응답에 코드를 담는다 (개발용 · 가입 인증과 같은 설정)
    private final SecureRandom random = new SecureRandom(); // 추측할 수 없는 난수 (Random 은 예측 가능)

    @Autowired
    public PasswordResetService(
            MemberMapper memberMapper,
            PasswordResetMapper passwordResetMapper,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            @Value("${member.email-verification.required:false}") boolean verificationRequired,
            @Value("${member.email-verification.dev-code-enabled:false}") boolean devCodeEnabled) {
        this(
                memberMapper,
                passwordResetMapper,
                passwordEncoder,
                emailSender,
                Clock.systemUTC(),
                verificationRequired,
                devCodeEnabled);
    }

    // 테스트용: 시각(Clock)을 정해 응답의 만료 시각을 확인할 수 있게 한다
    PasswordResetService(
            MemberMapper memberMapper,
            PasswordResetMapper passwordResetMapper,
            PasswordEncoder passwordEncoder,
            EmailSender emailSender,
            Clock clock,
            boolean verificationRequired,
            boolean devCodeEnabled) {
        this.memberMapper = memberMapper;
        this.passwordResetMapper = passwordResetMapper;
        this.passwordEncoder = passwordEncoder;
        this.emailSender = emailSender;
        this.clock = clock;
        this.verificationRequired = verificationRequired;
        this.devCodeEnabled = devCodeEnabled;
    }

    // 아이디 찾기: 일부를 가린 아이디를 돌려준다
    public LoginIdLookupResponseDTO findLoginId(String rawEmail) {
        PasswordResetMemberDTO member = findTarget(rawEmail);
        return new LoginIdLookupResponseDTO(maskLoginId(member.getLoginId()));
    }

    // ①② 재설정 인증 코드 발송. 새 코드를 받으면 예전 코드는 쓰지 않는다 (가장 최근 코드만 확인)
    public PasswordResetResponseDTO sendCode(String rawEmail) {
        String email = normalize(rawEmail);
        PasswordResetMemberDTO member = findTarget(email);

        String code = String.format("%06d", random.nextInt(1_000_000)); // 000000 ~ 999999

        // 메일을 먼저 보낸다. 못 보내면 예외(503)가 나가고, 받지 못한 코드는 저장하지 않는다
        emailSender.sendPasswordResetCode(email, code);

        // 코드 원문은 저장하지 않고 BCrypt 해시만 저장한다
        PasswordResetDTO reset = new PasswordResetDTO();
        reset.setMemberId(member.getMemberId());
        reset.setCodeHash(passwordEncoder.encode(code));
        reset.setValidMinutes(CODE_VALID_MINUTES);
        passwordResetMapper.insertReset(reset);
        // 코드 · 이메일은 로그에 남기지 않는다
        log.info("비밀번호 재설정 코드 발급: resetId={}", reset.getResetId());

        Instant expiresAt = clock.instant().plus(Duration.ofMinutes(CODE_VALID_MINUTES));
        String devCode = devCodeEnabled ? code : null; // 개발용 설정일 때만 응답에 담는다
        return new PasswordResetResponseDTO(expiresAt, devCode);
    }

    /**
     * ③④⑤ 코드 확인 → 새 비밀번호 저장 → 사용 처리.
     * 코드가 틀려 던지는 BusinessException 에서는 되돌리지 않는다 (틀린 횟수 + 1 을 저장해야 하므로).
     * DB 오류 같은 그 밖의 예외는 모두 되돌린다.
     */
    @Transactional(noRollbackFor = BusinessException.class)
    public void complete(PasswordResetCompleteRequestDTO request) {
        // 비밀번호 규칙부터 본다 → 확인 칸 오타로 코드 기회를 쓰지 않게
        MemberService.validatePassword(request.getNewPassword(), request.getNewPasswordConfirm());

        PasswordResetMemberDTO member = findTarget(request.getEmail());

        Long resetId = passwordResetMapper.selectLatestResetId(member.getMemberId());
        if (resetId == null) {
            throw resendRequired("RESET_CODE_EXPIRED");
        }
        // 행을 잠그고 읽는다 → 같은 코드로 동시에 여러 번 확인해도 틀린 횟수가 빠지지 않는다
        PasswordResetDTO reset = passwordResetMapper.selectResetForUpdate(resetId);
        boolean unusable = reset == null || reset.isUsed() || reset.isExpired();
        if (unusable || reset.getFailCount() >= MAX_FAIL_COUNT) {
            throw resendRequired("RESET_CODE_EXPIRED");
        }

        if (!passwordEncoder.matches(request.getCode(), reset.getCodeHash())) {
            passwordResetMapper.increaseFailCount(resetId);
            // 6자리 숫자는 경우의 수가 적어서, 계속 넣어 보며 맞히지 못하게 횟수를 제한한다
            if (reset.getFailCount() + 1 >= MAX_FAIL_COUNT) {
                throw resendRequired("RESET_CODE_LIMIT");
            }
            throw new BusinessException(HttpStatus.BAD_REQUEST, "RESET_CODE_FAILED", CODE_FAILED);
        }

        passwordResetMapper.markUsed(resetId);
        // 원래 비밀번호는 저장하지 않는다
        String newPasswordHash = passwordEncoder.encode(request.getNewPassword());
        memberMapper.updatePassword(member.getMemberId(), newPasswordHash);
        log.info("비밀번호 재설정 완료: memberId={}", member.getMemberId());
    }

    // 아이디 앞 2글자만 보이고 나머지는 * (글자 수는 그대로) · 예: test1234 → te******
    static String maskLoginId(String loginId) {
        int visible = Math.min(VISIBLE_LOGIN_ID_CHARS, loginId.length());
        return loginId.substring(0, visible) + "*".repeat(loginId.length() - visible);
    }

    // 이메일로 대상 회원 찾기. 없음 · 탈퇴 · (인증을 켰을 때) 미인증은 모두 같은 404
    private PasswordResetMemberDTO findTarget(String rawEmail) {
        PasswordResetMemberDTO member = memberMapper.selectResetMember(normalize(rawEmail));
        if (member == null
                || STATUS_WITHDRAWN.equals(member.getStatus())
                || (verificationRequired && !EMAIL_VERIFIED.equals(member.getEmailVerifiedYn()))) {
            throw new BusinessException(HttpStatus.NOT_FOUND, "NOT_FOUND", NOT_FOUND_MESSAGE);
        }
        return member;
    }

    private static BusinessException resendRequired(String code) {
        return new BusinessException(HttpStatus.BAD_REQUEST, code, RESEND_MESSAGE);
    }

    // 앞뒤 공백을 지우고 소문자로 (가입할 때도 소문자로 저장한다)
    private static String normalize(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }
}
