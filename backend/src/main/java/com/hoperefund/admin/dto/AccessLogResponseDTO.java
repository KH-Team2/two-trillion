package com.hoperefund.admin.dto;

import java.time.LocalDateTime;
import lombok.Getter;
import lombok.Setter;

/**
 * F-21: 관리자 접근 기록 조회 응답 DTO
 * - ERD ACCESS_LOG 테이블 컬럼 기준 매핑
 */
@Getter
@Setter
public class AccessLogResponseDTO {
    private Long accessId; // 접근 번호 (ACCESS_ID)
    private Long memberId; // 회원 번호 (MEMBER_ID)
    private String screen; // 접근 화면 (SCREEN)
    private LocalDateTime accessedAt; // 접근 일시 (ACCESSED_AT)
    private String ipAddress; // 접속 IP 주소 (IP_ADDRESS)
    private String result; // 접근 결과 (RESULT)
}
