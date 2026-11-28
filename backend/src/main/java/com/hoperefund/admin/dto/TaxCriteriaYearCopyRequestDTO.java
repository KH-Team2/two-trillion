package com.hoperefund.admin.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * [신규 11/26 고은] F-19 새 귀속 연도 추가 요청 (예: 2026 기준값을 복사해 2027 만들기)
 *
 * <p>원래는 TaxCriteriaRequestDTO의 sourceYear로 같이 처리했는데, 「행 하나 추가」와 「연도 통째로 복사」가
 * 한 요청에 섞여 헷갈려서 따로 나눴어요. 복사한 뒤 바뀐 값만 화면에서 고치면 돼요.
 */
@Getter
@Setter
@NoArgsConstructor
public class TaxCriteriaYearCopyRequestDTO {

    @NotNull(message = "복사할 연도를 입력해 주세요")
    @Min(2000)
    @Max(2100)
    private Integer sourceYear; // 복사해 올 연도 (이미 있는 연도)

    @NotNull(message = "새 연도를 입력해 주세요")
    @Min(2000)
    @Max(2100)
    private Integer targetYear; // 새로 만들 연도 (아직 없는 연도)
}
