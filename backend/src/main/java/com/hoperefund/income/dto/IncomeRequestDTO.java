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

    // [수정 11/12] 모든 검사에 message(화면 문구) 추가 · 이유: 문구가 없으면 "널이어서는 안됩니다" 같은
    //   기본 문장이 화면에 그대로 나감 (공통 처리기가 이 message를 그대로 띄움) · 문구는 팀원 PR #7과 같게

    // [수정 11/11] 상한 DB 자릿수(12자리) → 10억 · 이유: 오타까지 거르려고 (팀원 DTO 참고)
    @NotNull(message = "총급여를 입력해 주세요")
    @PositiveOrZero(message = "총급여는 0원 이상이어야 해요")
    @Max(value = 1_000_000_000L, message = "총급여가 너무 커요")
    private Long totalSalary; // 연간 예상 총급여 (필수 · 기본값 없음)

    @NotNull(message = "기납부세액을 입력해 주세요")
    @PositiveOrZero(message = "기납부세액은 0원 이상이어야 해요")
    @Max(value = 1_000_000_000L, message = "기납부세액이 너무 커요")
    private Long withheldTax; // 기납부 소득세 (필수 · 기본값 없음)

    // [수정 11/11] @NotNull + 기본값 0 추가 · 이유: 빈 칸을 0으로 바꾸는 일을 SQL(NVL) 대신 여기서 끝내려고
    @NotNull(message = "국민연금보험료를 입력해 주세요")
    @PositiveOrZero(message = "국민연금보험료는 0원 이상이어야 해요")
    @Max(value = 1_000_000_000L, message = "금액이 너무 커요")
    private Long nationalPension = 0L; // 국민연금보험료 (본인 부담)

    @NotNull(message = "건강·고용보험료를 입력해 주세요")
    @PositiveOrZero(message = "건강·고용보험료는 0원 이상이어야 해요")
    @Max(value = 1_000_000_000L, message = "금액이 너무 커요")
    private Long healthInsurance = 0L; // 건강·고용·노인장기요양보험료 (본인 부담)

    @NotNull(message = "부양가족 수를 입력해 주세요")
    @PositiveOrZero(message = "부양가족 수는 0명 이상이어야 해요")
    @Max(value = 99, message = "부양가족 수가 너무 커요") // NUMBER(2) → 2자리까지
    private Integer dependents = 0; // 부양가족 수

    @NotNull(message = "자녀 수를 입력해 주세요")
    @PositiveOrZero(message = "자녀 수는 0명 이상이어야 해요")
    @Max(value = 99, message = "자녀 수가 너무 커요")
    private Integer children = 0; // 20세 이하 자녀 수 (부양가족 수 이하인지는 Service에서 확인)

    @NotNull(message = "월세를 입력해 주세요")
    @PositiveOrZero(message = "월세는 0원 이상이어야 해요")
    @Max(value = 100_000_000L, message = "금액이 너무 커요")
    private Long monthlyRent = 0L; // 월 월세액

    @NotNull(message = "주택청약 납입액을 입력해 주세요")
    @PositiveOrZero(message = "주택청약 납입액은 0원 이상이어야 해요")
    @Max(value = 100_000_000L, message = "금액이 너무 커요")
    private Long housingSaving = 0L; // 주택청약 연 납입액

    @NotNull(message = "연금저축 납입액을 입력해 주세요")
    @PositiveOrZero(message = "연금저축 납입액은 0원 이상이어야 해요")
    @Max(value = 100_000_000L, message = "금액이 너무 커요")
    private Long pensionSaving = 0L; // 연금저축 연 납입액

    @NotNull(message = "IRP 납입액을 입력해 주세요")
    @PositiveOrZero(message = "IRP 납입액은 0원 이상이어야 해요")
    @Max(value = 100_000_000L, message = "금액이 너무 커요")
    private Long irpSaving = 0L; // IRP 연 납입액

    // @Pattern: 글자 모양 검사. "[YN]" = Y 또는 N 한 글자만
    @NotNull(message = "중소기업 감면 신청 여부를 입력해 주세요")
    @Pattern(regexp = "[YN]", message = "중소기업 감면 신청 여부는 Y 또는 N이에요")
    private String smeReductionYn = "N"; // 중소기업 감면 신청 여부 Y/N
}
