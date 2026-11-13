package com.hoperefund.prediction.mapper;

import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.dto.CardSpendDTO;
import com.hoperefund.prediction.dto.MemberQualificationDTO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 계산 결과 저장 (PREDICTION · PREDICTION_INPUT · PREDICTION_DETAIL) SQL은
 * resources/mapper/prediction/PredictionMapper.xml에 있어요. 값은 모두 #{}로 넣어요 (PreparedStatement의 ? → SQL
 * Injection 방지).
 */
@Mapper
public interface PredictionMapper {

    /** 회원의 무주택 세대주 여부 (Y/N) · 회원이 없으면 null */
    String findHomelessHeadYn(@Param("memberId") long memberId);

    /** 혜택 진단용 회원 자격 정보 (본인 MEMBER_ID만) · 회원이 없으면 null */
    MemberQualificationDTO findMemberQualification(@Param("memberId") long memberId);

    /** 본인 카드 내역을 귀속 연도 안에서 분류 + 결제수단별로 더한 값 (EXPENSE 읽기만) */
    List<CardSpendDTO> findCardSpends(
            @Param("memberId") long memberId, @Param("taxYear") int taxYear);

    /** 공제 항목 번호 찾기: 연도 + 코드 + 총급여 구간 같은 코드가 구간별로 여러 행이라(예: 연금계좌 5,500만 이하/초과) 총급여로 하나를 골라요. */
    List<Long> findTypeIds(
            @Param("taxYear") int taxYear,
            @Param("typeCode") String typeCode,
            @Param("totalSalary") long totalSalary);

    /** 새 예측번호 (SEQ_PREDICTION) */
    long nextPredictionId();

    /**
     * PREDICTION 1행 · SCENARIO_NAME('현재') · BASE_PREDICTION_ID(NULL) · PREDICTED_AT(SYSDATE)은 DB
     * 기본값
     */
    int insertPrediction(
            @Param("predictionId") long predictionId,
            @Param("memberId") long memberId,
            @Param("result") CalculationResponseDTO result);

    /** PREDICTION_INPUT 1행 (계산에 쓴 입력값 그대로 복사) */
    int insertPredictionInput(
            @Param("predictionId") long predictionId,
            @Param("income") IncomeResponseDTO income,
            @Param("homelessHeadYn") String homelessHeadYn);

    /** PREDICTION_DETAIL 1행 (공제 항목 하나) */
    int insertPredictionDetail(
            @Param("predictionId") long predictionId,
            @Param("typeId") long typeId,
            @Param("baseAmount") long baseAmount,
            @Param("deductionAmount") long deductionAmount);
}
