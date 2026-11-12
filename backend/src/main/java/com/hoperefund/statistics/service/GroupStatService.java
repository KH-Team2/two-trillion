package com.hoperefund.statistics.service;

import com.hoperefund.statistics.dto.GroupStatResponseDTO;
import com.hoperefund.statistics.mapper.GroupStatMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 그룹 비교 통계 (F-06-02 · 「나와 비슷한 사람들은」) · 본인 성별·연령대 그룹과 총급여 구간 그룹의 국세통계 평균을 찾아요. 읽기만 해요.
 */
@Service
public class GroupStatService {

    private final GroupStatMapper groupStatMapper;

    public GroupStatService(GroupStatMapper groupStatMapper) {
        this.groupStatMapper = groupStatMapper;
    }

    /** 본인이 속한 그룹 두 개 · 못 찾은 그룹은 null로 둬요 (성별 미입력 · 소득 미저장은 오류가 아님) */
    @Transactional(readOnly = true)
    public GroupStatResponseDTO getMyGroups(long memberId, int taxYear) {
        return new GroupStatResponseDTO(
                taxYear,
                groupStatMapper.findAgeGroup(memberId, taxYear),
                groupStatMapper.findSalaryGroup(memberId, taxYear));
    }
}
