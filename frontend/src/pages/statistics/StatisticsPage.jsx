import React from 'react';
import GroupStats from '@/pages/statistics/GroupStats.jsx';

// 기존 그룹 통계 컴포넌트에 사용자 조건을 전달합니다.
// result: 서버 계산 결과(없으면 체험용 계산) · groupStats: 서버가 찾은 내 그룹 통계(없으면 통계 스냅샷)
export default function StatisticsPage({ data, go, result, groupStats = null }) {
  return <GroupStats data={data} go={go} result={result} groupStats={groupStats} />;
}
