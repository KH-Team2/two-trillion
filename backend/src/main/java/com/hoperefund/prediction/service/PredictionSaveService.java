package com.hoperefund.prediction.service;

import com.hoperefund.income.dto.IncomeResponseDTO;
import com.hoperefund.prediction.dto.CalculationResponseDTO;
import com.hoperefund.prediction.dto.DeductionDetailDTO;
import com.hoperefund.prediction.mapper.PredictionMapper;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 계산 결과 저장 (PREDICTION · PREDICTION_INPUT · PREDICTION_DETAIL)
 *
 * <p>저장만 따로 둔 이유: 계산 서버 호출(최대 10초)을 트랜잭션 밖에서 하려고요. 계산이 끝난 뒤 저장할 때만 DB 연결을 잠깐 잡아요 → 계산 서버가 느려도 DB
 * 연결이 모자라지 않아요. (같은 클래스 안에서 부르면 @Transactional이 적용되지 않아서 Bean을 나눴어요)
 */
@Service
public class PredictionSaveService {

    private final PredictionMapper predictionMapper;

    public PredictionSaveService(PredictionMapper predictionMapper) {
        this.predictionMapper = predictionMapper;
    }

    /**
     * @Transactional: 이 메서드 안의 INSERT가 하나라도 실패하면 전부 취소(rollback)돼요. → PREDICTION만 저장되고 상세가 빠지는 "반쪽
     * 결과"가 남지 않아요.
     */
    @Transactional
    public long save(
            long memberId,
            int taxYear,
            IncomeResponseDTO income,
            String homelessHeadYn,
            CalculationResponseDTO result) {
        // 계산 결과의 연도가 요청 연도와 다르면 저장하지 않아요 (기준값 연도 = 계산 연도 규칙)
        if (result.getTaxYear() == null || result.getTaxYear() != taxYear) {
            throw new IllegalStateException(
                    "계산 결과 연도가 요청 연도와 달라요: " + result.getTaxYear() + " / " + taxYear);
        }
        if (result.getDetails() == null) {
            throw new IllegalStateException("계산 결과에 항목별 내역이 없어요");
        }

        long predictionId = predictionMapper.nextPredictionId();
        predictionMapper.insertPrediction(predictionId, memberId, result);
        predictionMapper.insertPredictionInput(predictionId, income, homelessHeadYn);

        for (DeductionDetailDTO detail : result.getDetails()) {
            long typeId = findTypeId(taxYear, detail.getTypeCode(), income.getTotalSalary());
            predictionMapper.insertPredictionDetail(
                    predictionId, typeId, detail.getBaseAmount(), detail.getDeductionAmount());
        }
        return predictionId;
    }

    /** 연도 + 코드 + 총급여 구간으로 정확히 1개를 찾아요. 0개나 여러 개면 기준값이 잘못된 거라 저장을 멈춰요. */
    private long findTypeId(int taxYear, String typeCode, long totalSalary) {
        List<Long> typeIds = predictionMapper.findTypeIds(taxYear, typeCode, totalSalary);
        if (typeIds.size() != 1) {
            throw new IllegalStateException(
                    "기준값 항목을 하나로 찾지 못했어요: "
                            + taxYear
                            + " "
                            + typeCode
                            + " (찾은 개수 "
                            + typeIds.size()
                            + ")");
        }
        return typeIds.get(0);
    }
}
