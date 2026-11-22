package com.hoperefund.member.service;

import com.hoperefund.common.exception.BusinessException;
import com.hoperefund.common.service.AccessLogService;
import com.hoperefund.common.service.TokenService;
import com.hoperefund.common.service.TokenService.IssuedToken;
import com.hoperefund.common.service.TokenService.TokenInfo;
import com.hoperefund.member.dto.LoginMemberDTO;
import com.hoperefund.member.dto.LoginRequestDTO;
import com.hoperefund.member.dto.LoginResponseDTO;
import com.hoperefund.member.mapper.MemberMapper;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Locale;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 로그인 · 로그아웃 (F-01-02 · decisions/0004).
 *
 * <pre>
 * ① 아이디로 회원 조회 ── 없음 → 401 LOGIN_FAILED
 * ② 잠금 확인 (잠금 켰을 때) → 401 LOGIN_LOCKED   (비밀번호 비교 전 — 잠금 중에 비밀번호를 맞혀 보지 못하게)
 * ③ 비밀번호 비교 ── 틀림 → 실패 횟수 + 1 (5번째면 10분 잠금) → 401 LOGIN_FAILED
 * ④ 탈퇴 확인 → 401 WITHDRAWN_MEMBER          (비밀번호가 맞은 사람에게만 — 아이디 존재 여부를 알 수 없게)
 * ⑤ 성공 → 실패 횟수 초기화 · 토큰 발급
 * 모든 결과를 ACCESS_LOG 에 SUCCESS / FAIL 로 남긴다.
 * </pre>
 *
 * @Transactional 을 붙이지 않는다: 실패하면 예외를 던지는데, 트랜잭션 안이면 실패 기록(ACCESS_LOG · 실패 횟수)까지 취소되기 때문.
 */
@Service
public class LoginService {

    static final int MAX_FAIL_COUNT = 5; // 연속 실패 허용 횟수 (5번째 실패 시 잠금)
    static final int LOCK_MINUTES = 10; // 잠금 시간 (분)
    static final String LOGIN_SCREEN = "/api/auth/login"; // ACCESS_LOG.SCREEN 에 남길 값

    private static final String STATUS_WITHDRAWN = "WITHDRAWN";
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");

    private final MemberMapper memberMapper;
    private final AccessLogService accessLogService;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final boolean lockEnabled;

    // 없는 아이디일 때도 비밀번호 비교를 한 번 해서 응답 시간을 맞춘다 → 응답 속도로 아이디 존재를 알 수 없게
    private final String dummyPasswordHash;

    // 설정 값(@Value)을 받고 가짜 암호문도 만들어야 해서 생성자를 직접 쓴다
    public LoginService(
            MemberMapper memberMapper,
            AccessLogService accessLogService,
            PasswordEncoder passwordEncoder,
            TokenService tokenService,
            @Value("${member.login-lock.enabled:true}") boolean lockEnabled) {
        this.memberMapper = memberMapper;
        this.accessLogService = accessLogService;
        this.passwordEncoder = passwordEncoder;
        this.tokenService = tokenService;
        this.lockEnabled = lockEnabled;
        this.dummyPasswordHash = passwordEncoder.encode("hope-refund-dummy-password");
    }

    public LoginResponseDTO login(LoginRequestDTO request, String ipAddress) {
        // 가입할 때처럼 소문자로 바꿔서 찾는다
        String loginId = request.getLoginId().trim().toLowerCase(Locale.ROOT);
        LoginMemberDTO member = memberMapper.selectLoginMember(loginId);

        // ① 없는 아이디
        if (member == null) {
            passwordMatches(request.getPassword(), dummyPasswordHash);
            writeAccessLog(null, ipAddress, AccessLogService.FAIL);
            throw loginFailed();
        }
        long memberId = member.getMemberId();

        // ② 잠금 중
        if (lockEnabled && member.isLocked()) {
            writeAccessLog(memberId, ipAddress, AccessLogService.FAIL);
            throw unauthorized("LOGIN_LOCKED", "10분 뒤 다시 시도해 주세요");
        }

        // ③ 비밀번호 틀림
        if (!passwordMatches(request.getPassword(), member.getPassword())) {
            if (lockEnabled) {
                memberMapper.increaseLoginFail(memberId, MAX_FAIL_COUNT, LOCK_MINUTES);
            }
            writeAccessLog(memberId, ipAddress, AccessLogService.FAIL);
            throw loginFailed();
        }

        // ④ 탈퇴 회원
        if (STATUS_WITHDRAWN.equals(member.getStatus())) {
            writeAccessLog(memberId, ipAddress, AccessLogService.FAIL);
            throw unauthorized("WITHDRAWN_MEMBER", "탈퇴한 계정이에요");
        }

        // ⑤ 성공 (실패 기록이 있을 때만 초기화 → 필요 없는 UPDATE 를 줄인다)
        if (member.getLoginFailCount() != null && member.getLoginFailCount() > 0) {
            memberMapper.resetLoginFail(memberId);
        }
        IssuedToken token = tokenService.issue(memberId, member.getLoginId(), member.getRole());
        writeAccessLog(memberId, ipAddress, AccessLogService.SUCCESS);

        LocalDateTime expiresAt = LocalDateTime.ofInstant(token.expiresAt(), KOREA);
        String tokenType = TokenService.TOKEN_TYPE;
        String role = member.getRole();
        return new LoginResponseDTO(token.token(), tokenType, expiresAt, member.getLoginId(), role);
    }

    // 로그아웃: 그 토큰을 무효로 만든다 → 다음 요청부터 401
    public void logout(TokenInfo tokenInfo) {
        tokenService.logout(tokenInfo);
    }

    // BCrypt 는 72바이트가 넘는 비밀번호에 오류를 내므로, 그 경우도 "틀린 비밀번호"로 처리한다
    private boolean passwordMatches(String rawPassword, String passwordHash) {
        try {
            return passwordEncoder.matches(rawPassword, passwordHash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    // ACCESS_LOG 기록 (F-09-01). 저장에 실패해도 AccessLogService 가 서버 로그만 남긴다
    // → 기록 실패 때문에 로그인을 막지 않는다
    private void writeAccessLog(Long memberId, String ipAddress, String result) {
        accessLogService.record(memberId, LOGIN_SCREEN, ipAddress, result);
    }

    // 아이디가 없을 때와 비밀번호가 틀렸을 때 같은 문구 → 어떤 아이디가 있는지 알려 주지 않기 위해
    private static BusinessException loginFailed() {
        return unauthorized("LOGIN_FAILED", "아이디 또는 비밀번호가 맞지 않아요");
    }

    private static BusinessException unauthorized(String code, String message) {
        return new BusinessException(HttpStatus.UNAUTHORIZED, code, message);
    }
}
