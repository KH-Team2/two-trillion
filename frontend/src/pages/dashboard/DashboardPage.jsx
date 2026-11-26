
import React from 'react';
import Dashboard from '@/pages/dashboard/Dashboard.jsx';
import TaxGuidePanel from '@/components/guide/TaxGuidePanel.jsx';
import { Donut } from '@/components/common/site-primitives.jsx';
// 대시보드에 기존 입력값과 서버 계산 결과를 전달합니다.
// result: 서버 계산 결과 (결과가 없으면 App이 이 화면 대신 「먼저 계산해 주세요」 안내를 띄워요)
// groupStats: 서버가 찾은 내 그룹 통계 · 없으면 통계 스냅샷에서 찾아 보여 줘요
// [수정 11/23] isDemo(체험용 계산) 삭제
//   이유: 체험판 기능 삭제 결정
// [수정 11/26] AI 절세 가이드 페이지 삭제(LLM 절세 가이드는 오른쪽에 있던 기존 챗봇 화면 영역에 띄움)

export default function DashboardPage({
  data,
  transactions,
  go,
  startSim,
  normalInput,
  result,
  groupStats = null,
}) {
  return (
    <Dashboard
      data={data}
      transactions={transactions}
      result={result}
      groupStats={groupStats}
      startSim={startSim}
      normalInput={normalInput}
      go={go}
      chart={(rows) => <Donut transactions={rows} />}
      chat={<TaxGuidePanel predictionId={result?.predictionId} />}
    />
  );
}
