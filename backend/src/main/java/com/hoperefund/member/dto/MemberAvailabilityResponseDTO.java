package com.hoperefund.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 아이디 · 이메일 중복확인 응답. */
@Getter
@AllArgsConstructor
public class MemberAvailabilityResponseDTO {

    private final boolean available; // true = 사용 가능
    private final String message; // 화면에 그대로 보여 줄 문구
}
