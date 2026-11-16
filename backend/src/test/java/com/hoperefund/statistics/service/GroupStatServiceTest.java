package com.hoperefund.statistics.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.hoperefund.statistics.dto.GroupStatDTO;
import com.hoperefund.statistics.dto.GroupStatResponseDTO;
import com.hoperefund.statistics.mapper.GroupStatMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** DB 없이 그룹 비교 통계 흐름만 확인해요 (가짜 매퍼). 그룹을 찾는 조건(SQL)은 실제 DB로 따로 확인했어요. */
class GroupStatServiceTest {

    /** 넣어 둔 그룹을 그대로 돌려주는 가짜 GroupStatMapper (없으면 null) */
    static class FakeGroupStatMapper implements GroupStatMapper {
        GroupStatDTO ageGroup;
        GroupStatDTO salaryGroup;
        long receivedMemberId;
        int receivedTaxYear;

        @Override
        public GroupStatDTO findAgeGroup(long memberId, int taxYear) {
            receivedMemberId = memberId;
            receivedTaxYear = taxYear;
            return ageGroup;
        }

        @Override
        public GroupStatDTO findSalaryGroup(long memberId, int taxYear) {
            return salaryGroup;
        }
    }

    private FakeGroupStatMapper mapper;
    private GroupStatService service;

    @BeforeEach
    void setUp() {
        mapper = new FakeGroupStatMapper();
        service = new GroupStatService(mapper);
    }

    private GroupStatDTO group(String label, long avgDecidedTax) {
        GroupStatDTO group = new GroupStatDTO();
        group.setStatYear(2024);
        group.setGroupLabel(label);
        group.setAvgDecidedTax(avgDecidedTax);
        return group;
    }

    @Test
    @DisplayName("두 그룹을 찾으면 귀속 연도와 함께 돌려준다")
    void returnsBothGroupsWithTaxYear() {
        // Given
        mapper.ageGroup = group("여성 40대", 2_274_940L);
        mapper.salaryGroup = group("4천만 이하", 394_361L);
        // When
        GroupStatResponseDTO response = service.getMyGroups(81L, 2026);
        // Then
        assertEquals(2026, response.getTaxYear());
        assertEquals("여성 40대", response.getAgeGroup().getGroupLabel());
        assertEquals(394_361L, response.getSalaryGroup().getAvgDecidedTax());
        assertEquals(81L, mapper.receivedMemberId);
        assertEquals(2026, mapper.receivedTaxYear);
    }

    @Test
    @DisplayName("소득이 없어 급여 구간을 못 찾으면 그 칸만 null이다")
    void salaryGroupIsNullWhenIncomeMissing() {
        // Given: 성별·연령대 그룹만 있음
        mapper.ageGroup = group("여성 40대", 2_274_940L);
        // When
        GroupStatResponseDTO response = service.getMyGroups(81L, 2026);
        // Then
        assertEquals("여성 40대", response.getAgeGroup().getGroupLabel());
        assertNull(response.getSalaryGroup());
    }

    @Test
    @DisplayName("두 그룹을 모두 못 찾아도 오류 없이 null로 돌려준다")
    void returnsNullsWhenNoGroupFound() {
        // When
        GroupStatResponseDTO response = service.getMyGroups(999_999L, 2026);
        // Then
        assertEquals(2026, response.getTaxYear());
        assertNull(response.getAgeGroup());
        assertNull(response.getSalaryGroup());
    }
}
