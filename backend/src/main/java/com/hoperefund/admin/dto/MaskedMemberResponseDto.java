package com.hoperefund.admin.dto;

import lombok.Getter;
import lombok.Setter;

/**
 * F-20: 회원 목록 조회 및 권한 검증용 응답 DTO
 *       - ERD MEMBER 테이블 기준 매핑 (MEMBER_ID, NAME, EMAIL, ROLE, GENDER, UPDATED_AT)
 *       - 목록 조회 시 NAME, EMAIL은 SQL 정규식으로 마스킹 처리
 */
@Getter
@Setter
public class MaskedMemberResponseDto {
    private Long memberId; // 회원 번호 (MEMBER.MEMBER_ID)
    private String name; // 회원 이름 - 목록 조회 시 마스킹 처리됨 (MEMBER.NAME)
    private String email; // 이메일 - 목록 조회 시 마스킹 처리됨 (MEMBER.EMAIL)
    private String role; // 권한 구분 - USER / ADMIN 등 (MEMBER.ROLE)
    private String gender; // 성별 - M / F / null, 일반 회원 전환 시 필수 확인 (MEMBER.GENDER)
    private String updatedAt; // 수정일시 (MEMBER.UPDATED_AT)
}
