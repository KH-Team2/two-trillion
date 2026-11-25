package com.hoperefund.chatbot.service;

import java.util.List;
import java.util.Map;

/** 맞춤 절세 가이드 서비스 기능 정의. */
public interface GuideService {

    // 입력값을 기준으로 추천 코드 선정
    List<String> recommend(long memberId, long predictionId);

    // 기존 설명 재사용 또는 새 설명 생성·저장
    Map<String, Object> generate(long memberId, long predictionId);

    // DB에 저장된 추천 설명만 조회
    Map<String, Object> getSaved(long memberId, long predictionId);
}
