package com.hoperefund.prediction.dto;

/**
 * 항목별 공제 내역 1행 (DTO: 계층 사이에 데이터를 실어 나르는 객체) - 칸 이름은 노션 「동석님 전달」의 details[] 이름 그대로 써요. 화면(React)이 이
 * 이름으로 값을 꺼내요. - 금액은 원 단위 정수(Long)예요. 카드 한도 행은 음수일 수 있어요.
 */
public class DeductionDetailDTO {

    private String typeCode; // 공제 코드 (DEDUCTION_TYPE.TYPE_CODE)
    private String typeName; // 화면에 보일 이름
    private String kind; // INCOME_DEDUCTION · TAX_CREDIT · TAX_REDUCTION · CARD · CARD_RULE
    private Long baseAmount; // 대상 금액
    private Long deductionAmount; // 공제액
    private String sourceNote; // 근거 (국세청 신고안내 쪽수)

    // 기본 생성자: JSON으로 바꿀 때(직렬화) 라이브러리가 사용해요
    public DeductionDetailDTO() {}

    public DeductionDetailDTO(
            String typeCode,
            String typeName,
            String kind,
            Long baseAmount,
            Long deductionAmount,
            String sourceNote) {
        this.typeCode = typeCode;
        this.typeName = typeName;
        this.kind = kind;
        this.baseAmount = baseAmount;
        this.deductionAmount = deductionAmount;
        this.sourceNote = sourceNote;
    }

    public String getTypeCode() {
        return typeCode;
    }

    public void setTypeCode(String typeCode) {
        this.typeCode = typeCode;
    }

    public String getTypeName() {
        return typeName;
    }

    public void setTypeName(String typeName) {
        this.typeName = typeName;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public Long getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(Long baseAmount) {
        this.baseAmount = baseAmount;
    }

    public Long getDeductionAmount() {
        return deductionAmount;
    }

    public void setDeductionAmount(Long deductionAmount) {
        this.deductionAmount = deductionAmount;
    }

    public String getSourceNote() {
        return sourceNote;
    }

    public void setSourceNote(String sourceNote) {
        this.sourceNote = sourceNote;
    }
}
