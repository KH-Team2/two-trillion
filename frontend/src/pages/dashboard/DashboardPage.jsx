import React from 'react';
import Dashboard from '@/pages/dashboard/Dashboard.jsx';
import ComingSoon from '@/components/common/ComingSoon.jsx';
import { Donut } from '@/components/common/site-primitives.jsx';

// 대시보드에 기존 입력값과 서버 계산 결과를 전달합니다.
// result: 서버 계산 결과 (결과가 없으면 App이 이 화면 대신 「먼저 계산해 주세요」 안내를 띄워요)
// groupStats: 서버가 찾은 내 그룹 통계 · 없으면 통계 스냅샷에서 찾아 보여 줘요
// [수정 11/23] isDemo(체험용 계산) 삭제 · 상담 카드(ChatPanel 예시 답변) → 「준비 중」 안내
//   이유: 체험판 기능 삭제 결정 · 상담 카드의 답변은 서버 없이 미리 써 둔 예시 문장이었음 (AI 챗봇 API 연결 전)
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
      chat={<ComingSoon title="AI 절세 가이드">AI 상담 기능은 서버 연결 작업 중이에요.</ComingSoon>}
    />
  );
}
