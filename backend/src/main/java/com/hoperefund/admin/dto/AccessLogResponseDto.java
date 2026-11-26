package com.hoperefund.admin.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class AccessLogResponseDto {
    /* F-21 (접근 기록 및 failCount) */

    // [수정 11/20] 주석만 수정 · 값은 ACCESS_LOG.LOG_ID (PR #45 리뷰: ACCESS_ID 칸은 없음) · 화면 칸 이름은 그대로 둠
    private Long accessId; // 접근 기록 번호 (ACCESS_LOG.LOG_ID)

    private Long memberId; // 회원 고유 ID (없는 아이디로 로그인 실패한 기록은 null)

    // [추가 11/20] 어느 화면/API 접근인지 · 이유(PR #45 리뷰): SCREEN 칸도 같이 조회하면 좋겠다는 의견
    private String screen; // 접근 화면/API (ACCESS_LOG.SCREEN)

    private String accessedAt; // 접근 시각 (SQL에서 'YYYY-MM-DD HH24:MI:SS' 글자로 바꿔 받음)
    private String ipAddress; // 접속 IP 주소
    private String result; // 접근 결과 (SUCCESS 정상 / FAIL 로그인 실패 등 / DENIED 권한 없음)

    // [수정 11/20] 주석 수정 · 이유(PR #45 리뷰): ACCESS_LOG에는 FAIL_COUNT 칸이 없음
    //   → MEMBER.LOGIN_FAIL_COUNT(그 회원의 지금 연속 실패 횟수)를 LEFT JOIN으로 가져옴 · 회원이 없는 기록은 null
    private Integer failCount; // 회원의 로그인 연속 실패 횟수 (MEMBER.LOGIN_FAIL_COUNT)

    // [추가 11/20] 이유(PR #45 리뷰): 실패 횟수와 함께 잠금 시각(LOCKED_UNTIL)도 MEMBER에 있음 · null이면 잠금 없음
    private String lockedUntil; // 로그인 잠금 해제 시각 (MEMBER.LOCKED_UNTIL)

    // [추가 11/20] 같은 IP의 실패(FAIL) 기록 수 (검색한 기간 안에서)
    //   이유: 기능명세서 F-08-02 ③ 「IP별 실패 횟수 조회」 · 없는 아이디로 여러 번 시도한 IP는 회원 실패 횟수로는 안 보여서
    private Integer ipFailCount;
}
