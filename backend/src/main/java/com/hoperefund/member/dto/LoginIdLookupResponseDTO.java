package com.hoperefund.member.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 아이디 찾기 응답 (F-01-03). 아이디 원문은 보내지 않고 일부를 가린 값만 보낸다. */
@Getter
@AllArgsConstructor
public class LoginIdLookupResponseDTO {

    private final String maskedLoginId; // 예: te******
}
