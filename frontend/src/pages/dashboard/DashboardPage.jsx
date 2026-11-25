
import React from 'react';
import Dashboard from '@/pages/dashboard/Dashboard.jsx';
import TaxGuidePanel from '@/components/guide/TaxGuidePanel.jsx';
import { Donut } from '@/components/common/site-primitives.jsx';

// 대시보드 구성 및 계산 결과별 절세 가이드 연결
export default function DashboardPage({
  data,
  transactions,
  go,
  startSim,
  normalInput,
  result,
  isDemo = true,
  groupStats = null,
}) {
  return (
    <Dashboard
      data={data}
      transactions={transactions}
      result={result}
      isDemo={isDemo}
      groupStats={groupStats}
      startSim={startSim}
      normalInput={normalInput}
      go={go}
      chart={(rows) => <Donut transactions={rows} />}
      chat={
        <TaxGuidePanel predictionId={result?.predictionId} />
      }
    />
  );
}
