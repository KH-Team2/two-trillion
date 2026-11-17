package com.hoperefund.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccessLogResponseDto {
    /* F-21 (접근 기록 및 failCount) */

    private Long accessId; // 접근 기록 고유 ID
    private Long memberId; // 회원 고유 ID
    private String accessedAt; // 접근 시각
    private String ipAddress; // 접속 IP 주소
    private String result; // 접근 결과 (SUCCESS / FAIL 등)
    private Integer failCount; // 로그인 실패 횟수 모니터링
}
