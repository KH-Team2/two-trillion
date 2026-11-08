package com.hoperefund.member.controller;

import com.hoperefund.member.dto.MemberSignupRequestDTO;
import com.hoperefund.member.dto.MemberSignupResponseDTO;
import com.hoperefund.member.service.MemberService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 API Controller
 * 요청 수신 · 형식 검증(@Valid) · 응답만 담당하고 처리는 MemberService에 맡긴다.
 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    // 회원가입: 성공 시 201
    @PostMapping
    public ResponseEntity<MemberSignupResponseDTO> signup(
            @Valid @RequestBody MemberSignupRequestDTO request) {

        MemberSignupResponseDTO response = memberService.signup(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
