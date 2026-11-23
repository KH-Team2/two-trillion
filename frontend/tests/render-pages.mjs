import { build } from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';
class Memory {
  map = new Map();
  getItem(k) {
    return this.map.get(k) ?? null;
  }
  setItem(k, v) {
    this.map.set(k, String(v));
  }
  removeItem(k) {
    this.map.delete(k);
  }
  get length() {
    return this.map.size;
  }
  key(i) {
    return [...this.map.keys()][i] ?? null;
  }
}
globalThis.localStorage = new Memory();
globalThis.sessionStorage = new Memory();
const dir = fs.mkdtempSync(path.join(process.cwd(), 'tests/.render-'));
const source = `
import React from 'react';
import { renderToStaticMarkup } from 'react-dom/server';
import { initial } from '@/api/services.js';
import App from '@/App.jsx';
import Auth from '@/pages/auth/AuthPage.jsx';
import Dashboard from '@/pages/dashboard/Dashboard.jsx';
import Input from '@/pages/tax/TaxInfoForm.jsx';
import Benefits from '@/pages/tax/BenefitsPage.jsx';
import Statistics from '@/pages/statistics/GroupStats.jsx';
import Chatbot from '@/pages/chatbot/ChatbotPage.jsx';
import Simulation from '@/pages/simulation/SimulationPage.jsx';
import Profile from '@/pages/profile/ProfilePage.jsx';
import Support from '@/pages/support/SupportPage.jsx';
import Admin from '@/pages/admin/AdminPage.jsx';
const noop = () => {};
const base = {
  data: initial,
  transactions: [],
  histories: [],
  saved: [],
  inquiries: [],
  search: '',
  setSearch: noop,
  go: noop,
  notify: noop,
  startSim: noop,
  normalInput: noop,
  profileTab: '이전 계산 결과',
  setProfileTab: noop,
  onSubmit: noop,
  onCancel: noop,
  faq: 0,
  setFaq: noop,
  supportTab: '자주 묻는 질문',
  setSupportTab: noop,
};
// GET /api/group-stats/{taxYear} 응답 모양 (서버가 찾은 내 그룹 두 개)
const serverGroups = {
  taxYear: 2026,
  ageGroup: {
    statYear: 2024,
    groupLabel: '여성 40대',
    peopleCount: 2037259,
    avgSalary: 41558266,
    avgDecidedTax: 2274940,
    avgRefundTax: 577207,
  },
  salaryGroup: {
    statYear: 2024,
    groupLabel: '4천만 이하',
    peopleCount: 3330978,
    avgSalary: 34782969,
    avgDecidedTax: 394361,
    avgRefundTax: 395272,
  },
};
const apiResult = {
  taxYear: 2026,
  totalSalary: 36000000,
  withheldTax: 1523000,
  taxableIncome: 15375000,
  calculatedTax: 1046250,
  decidedTax: 1100000,
  expectedRefund: 423000,
  details: [
    {
      typeCode: 'CARD_CHECK',
      typeName: '체크카드',
      kind: 'CARD',
      baseAmount: 4000000,
      deductionAmount: 300000,
      sourceNote: '신고안내 근거',
    },
    {
      typeCode: 'CARD_LIMIT',
      typeName: '카드 한도',
      kind: 'CARD_RULE',
      baseAmount: 0,
      deductionAmount: -100000,
      sourceNote: '한도 근거',
    },
    {
      typeCode: 'STANDARD',
      typeName: '표준세액공제',
      kind: 'TAX_CREDIT',
      baseAmount: 0,
      deductionAmount: 0,
      sourceNote: '',
    },
  ],
};
const ledgerRows = [
  {
    id: 1,
    date: '2026-10-01',
    merchant: '가게',
    amount: 12345,
    category: '일반',
    paymentMethod: '신용카드',
    source: 'upload',
    method: '상호명 미매칭 · 일반 추정',
  },
  {
    id: 2,
    date: '2026-10-01',
    merchant: '교통',
    amount: 65000,
    category: '대중교통',
    paymentMethod: '체크카드',
    source: 'upload',
    method: '예시 공공데이터 상호명 대조 일치',
  },
  {
    id: 3,
    date: '2026-10-02',
    merchant: '보험',
    amount: 90000,
    category: '공제 대상 아님',
    paymentMethod: '신용카드',
    source: 'upload',
    method: '예시 공공데이터 상호명 대조 일치',
  },
  {
    id: 4,
    date: '2026-10-03',
    merchant: '가게',
    amount: 12345,
    category: '일반',
    paymentMethod: '신용카드',
    source: 'upload',
    duplicateState: 'pending',
  },
  {
    id: 5,
    date: '2025-10-01',
    merchant: '가게',
    amount: 999999,
    category: '일반',
    paymentMethod: '신용카드',
    source: 'upload',
  },
];
const cases = [
  [
    'dashboard-ledger',
    <Dashboard
      {...base}
      data={{ ...initial, taxYear: 2026 }}
      transactions={ledgerRows}
      result={apiResult}
      chart={() => null}
    />,
  ],
  ['home', <App />],
  ...['login', 'signup', 'reset'].map((page) => [page, <Auth {...base} page={page} />]),
  // [수정 11/23] 체험판 삭제: 체험용 계산 대시보드(dashboard-demo) 삭제 · 화면은 모두 서버 계산 결과(apiResult)로 그림
  ['dashboard-api', <Dashboard {...base} result={apiResult} chart={() => null} />],
  ['input', <Input {...base} mode="normal" />],
  ['simulation-input', <Input {...base} mode="simulation" />],
  ['benefits', <Benefits {...base} />],
  ['statistics', <Statistics {...base} result={apiResult} />],
  // 서버가 찾은 그룹 통계 + 서버 계산 결과로 그리는 경우 (한쪽 그룹을 못 찾은 경우 포함)
  [
    'statistics-api',
    <Statistics {...base} result={apiResult} groupStats={{ ...serverGroups, salaryGroup: null }} />,
  ],
  [
    'dashboard-api-groups',
    <Dashboard
      {...base}
      result={apiResult}
      groupStats={serverGroups}
      chart={() => null}
    />,
  ],
  ['chatbot', <Chatbot />],
  [
    'simulation-result',
    <Simulation
      {...base}
      baseData={initial}
      sim={initial}
      br={{ ...apiResult, expectedRefund: 300000 }}
      sr={apiResult}
      simTx={[]}
    />,
  ],
  [
    'simulation-pay',
    <Simulation
      {...base}
      baseData={{ ...initial, taxYear: 2025, family: 0 }}
      sim={{ ...initial, taxYear: 2024, family: 2 }}
      br={{ ...apiResult, expectedRefund: 446240 }}
      sr={{ ...apiResult, expectedRefund: -12000 }}
      simTx={[]}
    />,
  ],
  ['profile', <Profile {...base} />],
  // [수정 11/17] 서버 로그인 회원: 로그인 응답에 이메일 · 생년월일 · 성별이 없음 (값이 없을 때도 버티는지)
  // → 대시보드 · 통계 · 프로필이 오류(빈 화면) 없이 그려져야 함
  ...[
    ['dashboard', Dashboard],
    ['statistics', Statistics],
    ['profile', Profile],
  ].map(([page, Page]) => [
    page + '-server-login',
    <Page
      {...base}
      data={{ ...initial, name: 'hope_user', email: undefined, birth: undefined, gender: '' }}
      session={{ id: 'member:hope_user', username: 'hope_user', role: 'USER', server: true }}
      result={apiResult}
      chart={() => null}
    />,
  ]),
  ['support', <Support {...base} />],
  ['admin-denied', <Admin {...base} admin={false} />],
  ['admin', <Admin {...base} admin />],
];
// [추가 11/23] 서버 API가 아직 없는 화면은 체험 기능 대신 「준비 중」 안내를 보여야 함
const comingSoon = ['reset', 'chatbot', 'admin'];
for (const [name, node] of cases) {
  const html = renderToStaticMarkup(node);
  if (!html || /NaN|undefined/.test(html)) throw Error(name + ': 렌더링 값 확인 필요');
  // [수정 11/17] 메일 발송 전이라 가입 화면에 이메일 인증(인증번호) UI를 숨김
  if (name === 'signup' && /인증번호|email-verification/.test(html))
    throw Error('가입 화면에 이메일 인증 UI가 보임');
  // [수정 11/17] 가입 · 로그인 화면에서 뺀 문구 (서버 연동 표시 · 검토용 초안 · 시연용 비밀번호 · 비밀번호 찾기 · 빠른 체험)
  if (name === 'signup' && /서버 연동|검토용 초안/.test(html)) throw Error('가입 화면에 뺀 문구가 보임');
  if (name === 'login' && /서버 연동|시연용 비밀번호|잊으셨나요|로그인 없이 바로 체험|사용자 체험/.test(html))
    throw Error('로그인 화면에 뺀 문구가 보임');
  if (
    name === 'simulation-pay' &&
    (!html.includes('amount-refund') ||
      !html.includes('amount-pay') ||
      !html.includes('예상 추가 납부액') ||
      !html.includes('458,240원 불리해졌어요') ||
      !html.includes('2024년') ||
      !html.includes('2명') ||
      html.includes('2,024'))
  )
    throw Error('시뮬레이션 환급/추가 납부 구분 · 변경 조건 표기 실패');
  if (
    name === 'dashboard-api' &&
    (!html.includes('15,375,000') ||
      !html.includes('-100,000') ||
      !html.includes('200,000') ||
      !html.includes('신고안내 근거'))
  )
    throw Error('실제 응답 상세/음수 합계 렌더링 실패');
  if (
    name === 'dashboard-ledger' &&
    (!html.includes('12,345원') ||
      !html.includes('65,000원') ||
      !html.includes('90,000원') ||
      !html.includes('167,345원') ||
      html.includes('999,999원') ||
      !html.includes('소비 유형별 실제 지출 및 분류 결과'))
  )
    throw Error('지출 표의 연도/보류 제외/분류 집계 실패');
  if (comingSoon.includes(name) && !html.includes('준비 중')) throw Error(name + ': 준비 중 안내 누락');
  // [추가 11/23] 체험판 삭제 후 화면에 체험 · 시연 문구가 남지 않음
  if (/체험|시연|DEMO/.test(html)) throw Error(name + ': 체험 · 시연 문구가 남음');
  if (
    name === 'dashboard-api' &&
    (!html.includes('0원') ||
      !html.includes('카드 공제한도 적용에 따른 조정액') ||
      !html.includes('공제 대상 금액') ||
      !html.includes('공제·감면 상세 내역'))
  )
    throw Error('실제 0원/음수/별도 상세 표 표시 실패');
  // 계산 API 결과일 때는 체험·데모 문구가 남지 않음 · 계산 상세 단계에 금액 표시
  if (
    name === 'dashboard-api' &&
    (html.includes('데모값') ||
      html.includes('시연 결과') ||
      html.includes('법정 계산 미연동') ||
      !html.includes('계산값'))
  )
    throw Error('계산 결과 화면에 체험 문구가 남음');
  // [수정 11/19 TT-124] 계산 결과 화면: 공제·감면 상태는 「증빙 확인」 · 서버가 계산하는 항목에 「미연동입니다」 문구가 남지 않음
  if (
    name === 'dashboard-api' &&
    (!html.includes('증빙 확인') ||
      html.includes('미연동입니다') ||
      !html.includes('소비 유형별로'))
  )
    throw Error('계산 결과 화면에 미연동 안내가 남음');
  if (name === 'dashboard-api') {
    const panel = html.split('<section class="card deductions">')[1].split('</section>')[0];
    if (
      (panel.match(/class="deduction-detail"/g) || []).length !== 7 ||
      panel.includes('deduction-response') ||
      !panel.includes('공제율 적용액') ||
      !panel.includes('한도 전') ||
      !panel.includes('최종 소득공제')
    )
      throw Error('v10 공제 영역 구조 복원 실패');
    for (const label of [
      '신용·체크카드·현금영수증',
      '추가 부양가족',
      '월세',
      '사회보험료',
      '주택청약',
      '연금저축·IRP',
      '중소기업 취업 청년 감면',
    ])
      if (!panel.includes(label)) throw Error('v10 항목 누락: ' + label);
  }
  console.log('PASS render:', name);
}
`;
try {
  await build({
    stdin: { contents: source, loader: 'jsx', resolveDir: process.cwd() },
    outfile: `${dir}/pages.mjs`,
    alias: { '@': path.join(process.cwd(), 'src') },
    bundle: true,
    platform: 'node',
    format: 'esm',
    packages: 'external',
    logLevel: 'silent',
  });
  await import(pathToFileURL(`${dir}/pages.mjs`));
  // 유지된 UI 파일까지 구문·import 검사를 수행합니다.
  const walk = (p) =>
    fs
      .readdirSync(p, { withFileTypes: true })
      .flatMap((e) =>
        e.isDirectory()
          ? walk(path.join(p, e.name))
          : /\.(jsx|js)$/.test(e.name)
            ? [path.join(p, e.name)]
            : [],
      );
  const modules = walk('src');
  await build({
    entryPoints: modules,
    outdir: `${dir}/modules`,
    alias: { '@': path.join(process.cwd(), 'src') },
    bundle: true,
    platform: 'browser',
    format: 'esm',
    packages: 'external',
    loader: { '.css': 'empty' },
    logLevel: 'silent',
  });
  console.log('PASS 전체 모듈 번들:', modules.length);
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
