package com.hoperefund.income.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 화면에서 소득·공제 정보를 받을 때 쓰는 DTO
 * - PUT /api/incomes/{taxYear} 의 본문(JSON)
 * - 화면이 넣어야 하는 칸만 둔다 (백엔드 규칙)
 *   · taxYear: 주소 {taxYear}로 받음
 *   · incomeId · memberId · updatedAt · createdAt · dataOrigin: 서버가 정하는 값이라 화면에서 못 바꾸게 뺌
 * - 필수가 아닌 칸은 기본값(0 · "N")을 넣어 둠
 *   → 화면이 그 칸을 아예 안 보내면 기본값이 그대로 저장됨
 *   → null을 직접 보내면 @NotNull에 걸려 400
 */
@Getter // 모든 칸의 getter 자동 생성 (Lombok)
@Setter // 모든 칸의 setter 자동 생성 → 스프링이 JSON 값을 setter로 채워 넣는다
@NoArgsConstructor // 빈 생성자 자동 생성 → 스프링이 이걸로 객체를 먼저 만든다 (이때 기본값이 들어감)
public class IncomeRequestDTO {

    // ▼ 검사 어노테이션: 컨트롤러에서 @Valid를 붙이면 스프링이 저장 전에 자동으로 검사한다.
    //   하나라도 어기면 400 VALIDATION 응답 (common/GlobalExceptionHandler가 처리)
    //   @NotNull         : 비어 있으면(null) 안 됨
    //   @PositiveOrZero  : 0 이상만 (음수 금지 · DB CHECK CK_INCOME_AMOUNT와 같은 규칙)
    //   @Max             : 오타 방지용 상한 (0을 하나 더 붙인 실수 거르기 · 업무 규칙 아님 → 필요하면 숫자만 올리기)
    //                      총급여 · 세액 · 보험료는 10억 (청년 근로소득 범위 밖)
    //                      월세 · 청약 · 연금저축 · IRP는 1억 (법적 연 납입 한도보다 훨씬 위)

    // [수정 11/11] 상한 DB 자릿수(12자리) → 10억 · 이유: 오타까지 거르려고 (팀원 DTO 참고)
    @NotNull @PositiveOrZero @Max(1_000_000_000L)
    private Long totalSalary; // 연간 예상 총급여 (필수 · 기본값 없음)

    @NotNull @PositiveOrZero @Max(1_000_000_000L)
    private Long withheldTax; // 기납부 소득세 (필수 · 기본값 없음)

    // [수정 11/11] @NotNull + 기본값 0 추가 · 이유: 빈 칸을 0으로 바꾸는 일을 SQL(NVL) 대신 여기서 끝내려고
    @NotNull @PositiveOrZero @Max(1_000_000_000L)
    private Long nationalPension = 0L; // 국민연금보험료 (본인 부담)

    @NotNull @PositiveOrZero @Max(1_000_000_000L)
    private Long healthInsurance = 0L; // 건강·고용·노인장기요양보험료 (본인 부담)

    @NotNull @PositiveOrZero @Max(99) // NUMBER(2) → 2자리까지
    private Integer dependents = 0; // 부양가족 수

    @NotNull @PositiveOrZero @Max(99)
    private Integer children = 0; // 20세 이하 자녀 수 (부양가족 수 이하인지는 Service에서 확인)

    @NotNull @PositiveOrZero @Max(100_000_000L)
    private Long monthlyRent = 0L; // 월 월세액

    @NotNull @PositiveOrZero @Max(100_000_000L)
    private Long housingSaving = 0L; // 주택청약 연 납입액

    @NotNull @PositiveOrZero @Max(100_000_000L)
    private Long pensionSaving = 0L; // 연금저축 연 납입액

    @NotNull @PositiveOrZero @Max(100_000_000L)
    private Long irpSaving = 0L; // IRP 연 납입액

    // @Pattern: 글자 모양 검사. "[YN]" = Y 또는 N 한 글자만
    @NotNull
    @Pattern(regexp = "[YN]")
    private String smeReductionYn = "N"; // 중소기업 감면 신청 여부 Y/N
}