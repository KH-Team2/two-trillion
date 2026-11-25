package com.hoperefund.chatbot.mapper;

import com.hoperefund.chatbot.dto.GuideInputDTO;
import java.util.List;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface GuideMapper {

    // 로그인 회원이 소유한 계산 결과의 추천 판단값 조회
    GuideInputDTO findGuideInput(
            @Param("memberId") long memberId, @Param("predictionId") long predictionId);

    // 해당 계산 결과에 저장된 추천 JSON 목록 조회
    List<String> findRecommendationContents(
            @Param("memberId") long memberId, @Param("predictionId") long predictionId);

    // 추천 JSON 한 건 저장
    int insertRecommendation(
            @Param("memberId") long memberId,
            @Param("predictionId") long predictionId,
            @Param("content") String content,
            @Param("llmModel") String llmModel);
}
