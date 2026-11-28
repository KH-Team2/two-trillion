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
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 로그인 · 로그아웃 (F-01-02 · decisions/0004).
 *
 * <pre>
 * ⓪ IP 제한 확인 ── 이 IP가 1분에 50번 넘게 실패한 지 12시간 안 됨 → 429 LOGIN_IP_BLOCKED · DENIED 기록   [추가 11/24 고은 · D-16]
 * ① 아이디로 회원 조회 ── 없음 → 401 LOGIN_FAILED
 * ② 잠금 확인 (잠금 켰을 때) → 401 LOGIN_LOCKED   (비밀번호 비교 전 — 잠금 중에 비밀번호를 맞혀 보지 못하게)
 * ③ 비밀번호 비교 ── 틀림 → 실패 횟수 + 1 (5번째면 10분 잠금) → 401 LOGIN_FAILED
 * ④ 탈퇴 확인 → 401 WITHDRAWN_MEMBER          (비밀번호가 맞은 사람에게만 — 아이디 존재 여부를 알 수 없게)
 * ⑤ 성공 → 실패 횟수 초기화 · 토큰 발급
 * 모든 결과를 ACCESS_LOG 에 SUCCESS / FAIL 로 남긴다. (⓪ IP 차단으로 거절한 시도만 DENIED)
 * </pre>
 *
 * @Transactional 을 붙이지 않는다: 실패하면 예외를 던지는데, 트랜잭션 안이면 실패 기록(ACCESS_LOG · 실패 횟수)까지 취소되기 때문.
 */
@Slf4j
@Service
public class LoginService {

    static final int MAX_FAIL_COUNT = 5; // 연속 실패 허용 횟수 (5번째 실패 시 잠금)
    static final int LOCK_MINUTES = 10; // 잠금 시간 (분)
    // [추가 11/24 고은] D-16 IP 기준 제한 숫자 (F-09-01): 한 IP가 1분에 50번 실패하면 12시간 막기
    static final int IP_FAIL_LIMIT = 50; // 이 횟수 이상 실패하면
    static final int IP_FAIL_WINDOW_MINUTES = 1; // 이 시간(분) 안에
    static final int IP_BLOCK_HOURS = 12; // 이 시간(시간) 동안 막는다
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
        // [추가 11/24 고은] ⓪ IP 제한 (D-16)
        // 이유: 아이디를 바꿔 가며 계속 시도하면 회원별 잠금(5번/10분)으로는 못 막아서, 접속 IP 기준으로 한 번 더 막는다
        //   · 회원 조회 전에 확인 → 막힌 IP 에는 아이디가 있는지 없는지도 알려 주지 않음
        //   · 막힌 동안의 시도는 DENIED 로 남긴다 (명세 F-01-02 「모든 시도는 IP와 함께 기록」)
        //     → 차단 판단은 FAIL 만 세므로, 막힌 동안 계속 시도해도 차단 시간이 늘어나지 않음
        //       (회원 5회 잠금이 잠금 중 시도로 잠금 시간을 늘리지 않는 것과 같은 규칙 · 처음 막힌 때부터 정확히 12시간)
        if (isIpBlocked(ipAddress)) {
            // [추가 11/24 고은] D-16 막힌 IP 의 로그인 시도 기록용 (FAIL 로 남기면 차단 판단에 다시 세어져 차단이 늘어나서)
            writeAccessLog(null, ipAddress, AccessLogService.DENIED);
            throw new BusinessException(
                    HttpStatus.TOO_MANY_REQUESTS,
                    "LOGIN_IP_BLOCKED",
                    "로그인 실패가 너무 많아요. 12시간 뒤 다시 시도해 주세요");
        }

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

    // [추가 11/24 고은] D-16 이 IP가 지금 막혀 있는지 (ACCESS_LOG 의 실패 기록으로 판단 · 따로 차단 목록 테이블 없음 D-15)
    // IP 를 모르면(null) 셀 수 없으니 막지 않는다.
    // 확인 중 DB 오류가 나면 서버 로그만 남기고 막지 않는다 (ACCESS_LOG 기록 실패와 같은 규칙: 기록 문제로 정상 로그인을 막지 않음)
    private boolean isIpBlocked(String ipAddress) {
        if (ipAddress == null) {
            return false;
        }
        try {
            return accessLogService.countIpBlockTriggers(
                            ipAddress, IP_FAIL_LIMIT, IP_FAIL_WINDOW_MINUTES, IP_BLOCK_HOURS)
                    > 0;
        } catch (DataAccessException e) {
            log.error("IP 제한 확인 실패 (막지 않고 진행)", e);
            return false;
        }
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
