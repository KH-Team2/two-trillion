package com.hoperefund.consult.service;

import com.hoperefund.common.exception.BusinessException;
import java.security.Principal;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;

/**
 * [추가 11/24 · 이우진 · TT-153] 상담 채팅을 쓰는 사람 (회원번호 + 관리자 여부)
 *
 * <p>REST 는 JWT 필터가, STOMP 는 StompAuthChannelInterceptor 가 같은 모양의 로그인 정보(principal = 회원번호 · 권한 =
 * ROLE_USER / ROLE_ADMIN)를 넣어 두므로 둘 다 여기서 꺼냄. 화면이 보낸 회원번호는 쓰지 않음.
 */
public record ConsultUser(long memberId, boolean admin) {

    static final String ROLE_ADMIN = "ROLE_ADMIN";

    public static ConsultUser from(Principal principal) {
        if (principal instanceof Authentication authentication
                && authentication.getPrincipal() instanceof Long memberId) {
            boolean admin =
                    authentication.getAuthorities().stream()
                            .anyMatch(authority -> ROLE_ADMIN.equals(authority.getAuthority()));
            return new ConsultUser(memberId, admin);
        }
        throw new BusinessException(HttpStatus.UNAUTHORIZED, "UNAUTHORIZED", "로그인이 필요해요");
    }
}
