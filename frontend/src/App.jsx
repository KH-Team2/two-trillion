import { getDefaultTaxYear } from '@/utils/tax-year.js';
import { uploadTotals } from '@/api/expense-service.js';
import { migrateCalculationHistory } from '@/api/calculation-contract.js';
import { useHashRouting } from '@/routes/useHashRouting.js';
import React from 'react';
import { useState, useEffect, useRef } from 'react';
import { initial, normalizeTaxData, userService, taxService } from '@/api/services.js';
import { fetchLatestCalculation, saveAndCalculate } from '@/api/calculation-service.js';
import { fetchGroupStats } from '@/api/statistics-service.js';
import { canUseDemo } from '@/api/api-client.js';
import { accountService, newPrototypeId } from '@/api/account-service.js';
import { inquiryService, recordAccess } from '@/api/prototype-records.js';
import { CheckCircle2, X } from 'lucide-react';
import AdminPage from '@/pages/admin/AdminPage.jsx';
import SupportPage from '@/pages/support/SupportPage.jsx';
import ProfilePage from '@/pages/profile/ProfilePage.jsx';
import StatisticsPage from '@/pages/statistics/StatisticsPage.jsx';
import BenefitsPage from '@/pages/tax/BenefitsPage.jsx';
import ChatbotPage from '@/pages/chatbot/ChatbotPage.jsx';
import SimulationPage from '@/pages/simulation/SimulationPage.jsx';
import DashboardPage from '@/pages/dashboard/DashboardPage.jsx';
import TaxInputPage from '@/pages/tax/TaxInputPage.jsx';
import Topbar from '@/components/layout/Topbar.jsx';
import Sidebar from '@/components/layout/Sidebar.jsx';
import AuthPage from '@/pages/auth/AuthPage.jsx';
import HomePage from '@/pages/home/HomePage.jsx';
import PublicHeader from '@/components/layout/PublicHeader.jsx';

// 공통 상태와 기존 이벤트 처리 흐름을 유지하는 앱 조정자입니다.
export default function App() {
  // 페이지·입력·거래·세션 상태는 원본처럼 최상위에서 관리합니다.
  const [page, setPage] = useState('home');
  const [draftTaxYear, setDraftTaxYear] = useState(null);
  const [data, setData] = useState(initial);
  const [transactions, setTransactions] = useState([]);
  const [simulation, setSimulation] = useState(null);
  const [compareBase, setCompareBase] = useState(null);
  const [mode, setMode] = useState('normal');
  const [toast, setToast] = useState('');
  const [mobile, setMobile] = useState(false);
  const [session, setSession] = useState(null);
  const admin = session?.role === 'ADMIN' && accountService.current()?.role === 'ADMIN';
  const [upload, setUpload] = useState(null);
  const [simTx, setSimTx] = useState([]);
  const [simUpload, setSimUpload] = useState(null);
  const [histories, setHistories] = useState([]);
  const [saved, setSaved] = useState([]);
  const [inquiries, setInquiries] = useState([]);
  const [notification, setNotification] = useState(false);
  const [profileTab, setProfileTab] = useState('개인정보');
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('전체');
  const [faq, setFaq] = useState(0);
  const [supportTab, setSupportTab] = useState('자주 묻는 질문');
  const [statsFilter, setStatsFilter] = useState('연령대');
  const [adminTab, setAdminTab] = useState('문의 관리');
  const [criteria, setCriteria] = useState(String(getDefaultTaxYear()));
  const [publicList, setPublicList] = useState(
    '서울교통공사,대중교통\n광장시장,전통시장\n교보문고,문화체육',
  );
  const [authError, setAuthError] = useState('');
  const [formSeed, setFormSeed] = useState(0);
  // 서버(계산 API) 계산 결과 · 없으면 대시보드는 체험용 계산을 보여 줘요
  const [calcResult, setCalcResult] = useState(null);
  const calculating = useRef(false); // 계산 버튼 연타로 같은 요청이 여러 번 가는 것 방지
  // 서버(그룹 통계 API)가 찾은 내 그룹 · 없으면 화면은 통계 스냅샷에서 찾아 보여 줘요
  const [groupStats, setGroupStats] = useState(null);
  useEffect(() => setDraftTaxYear(null), [page, formSeed]);
  function refreshSession() {
    setSession(accountService.current());
  }
  // 다른 탭의 계정 변경을 감지하고 현재 세션을 동기화합니다.
  useEffect(() => {
    refreshSession();
    const sync = () => refreshSession();
    window.addEventListener('storage', sync);
    return () => window.removeEventListener('storage', sync);
  }, []);
  // 로그인한 계정에 해당하는 입력·거래·이력을 불러옵니다.
  useEffect(() => {
    const fallback = {
      ...initial,
      ...(session
        ? {
            name: session.username || '사용자',
            email: session.email,
            birth: session.birth,
            gender: session.gender || '',
          }
        : {}),
    };
    const loadedRows = userService
      .load('transactions', [])
      .filter((row) => row.source === 'upload');
    const loadedInput = normalizeTaxData(userService.load('data', fallback));
    const loaded = { ...loadedInput, ...uploadTotals(loadedRows, loadedInput.taxYear) };
    setData(
      session
        ? {
            ...loaded,
            name: session.username || '사용자',
            email: session.email,
            birth: session.birth,
            gender: session.gender || '',
          }
        : loaded,
    );
    setTransactions(
      userService
        .load('transactions', [])
        .filter((t) => t.source === 'upload')
        .map((t) => ({
          ...t,
          category: t.category === '공제 제외' ? '공제 대상 아님' : t.category,
        })),
    );
    setUpload(userService.load('upload', null));
    const loadedHistories = userService.load('histories', []).map(migrateCalculationHistory);
    setHistories(loadedHistories);
    // 마지막 계산이 서버 계산이고 같은 귀속 연도면 그 결과를 이어서 보여 줘요
    const latest = loadedHistories[0];
    setCalcResult(latest?.source === 'API' && latest.taxYear === loaded.taxYear ? latest : null);
    setSaved(userService.load('saved', []).map(migrateCalculationHistory));
    inquiryService.migrate(userService.load('inquiries', []));
    setInquiries(session ? inquiryService.list() : []);
    setSimulation(null);
    setCompareBase(null);
    setSimTx([]);
    setSimUpload(null);
    setMode('normal');
    setCriteria(userService.load('admin-criteria', String(getDefaultTaxYear())));
    setPublicList(
      userService.load(
        'admin-merchants',
        '서울교통공사,대중교통\n광장시장,전통시장\n교보문고,문화체육',
      ),
    );
  }, [session?.id]);
  useHashRouting({ setPage, setMode, setFormSeed, setMobile });
  // 대시보드에 들어왔는데 서버 계산 결과가 화면에 없으면, DB에 저장된 최근 결과를 불러와요
  // (다른 브라우저에서 계산했거나 브라우저 저장이 지워졌어도 결과가 보이게)
  // 계산한 적이 없거나(NOT_FOUND) 서버에 연결할 수 없으면 지금 화면을 그대로 둬요
  useEffect(() => {
    if (page !== 'dashboard') return;
    if (calcResult && calcResult.taxYear === data.taxYear) return;
    let ignore = false;
    fetchLatestCalculation(data.taxYear)
      .then((latest) => {
        if (!ignore) setCalcResult(latest);
      })
      .catch(() => {});
    return () => {
      ignore = true;
    };
  }, [page, data.taxYear, calcResult]);
  // 대시보드 · 통계 화면에 들어오면 내가 속한 그룹의 국세통계 평균을 서버에서 불러와요 (F-06-02)
  // 계산을 새로 하면 저장된 총급여가 바뀔 수 있어서 다시 불러와요 (calcResult)
  // 서버에 연결할 수 없으면 비워 두고, 화면은 통계 스냅샷에서 찾아 보여 줘요
  useEffect(() => {
    if (page !== 'dashboard' && page !== 'stats') return;
    let ignore = false;
    fetchGroupStats(data.taxYear)
      .then((found) => {
        if (!ignore) setGroupStats(found);
      })
      .catch(() => {
        if (!ignore) setGroupStats(null);
      });
    return () => {
      ignore = true;
    };
  }, [page, data.taxYear, calcResult]);
  // 일시 안내 메시지를 원본과 같은 시간 후 닫습니다.
  useEffect(() => {
    if (toast) {
      const t = setTimeout(() => setToast(''), 3500);
      return () => clearTimeout(t);
    }
  }, [toast]);
  // 원본의 #dashboard 등 해시 주소를 그대로 사용합니다.
  function go(p) {
    if (p === 'expenses') {
      normalInput();
      return;
    }
    if (p === 'admin') {
      try {
        recordAccess(
          'ADMIN_VIEW',
          accountService.current()?.role === 'ADMIN' ? 'SUCCESS' : 'DENIED',
        );
      } catch {}
    }
    if (p === 'support' && accountService.current()) setInquiries(inquiryService.list());
    setPage(p);
    location.hash = p;
    setMobile(false);
    window.scrollTo(0, 0);
  }
  function notify(t) {
    setToast(t);
  }
  // 실제 입력을 복사해 가상 조건을 만들므로 원본 소득·지출을 덮어쓰지 않습니다.
  function startSim() {
    setCompareBase(structuredClone(data));
    setSimTx(transactions.map((t) => ({ ...t })));
    setSimUpload(upload ? { ...upload } : null);
    setSimulation({ ...data });
    setMode('simulation');
    setFormSeed((s) => s + 1);
    go('input');
  }
  function normalInput() {
    setMode('normal');
    setFormSeed((s) => s + 1);
    go('input');
  }
  // 일반 계산: 소득을 서버에 저장(PUT)하고 계산 API(POST)로 계산해요.
  // 서버에 연결할 수 없을 때 개발 서버에서만 기존 체험용 계산으로 보여 줘요 (배포본은 연결 실패 안내만).
  // 시뮬레이션은 아직 체험용 계산이에요.
  async function calculate(input, tx, meta) {
    const d = { ...input, ...uploadTotals(tx, input.taxYear) };
    recordAccess(mode === 'simulation' ? 'SIMULATION_CALCULATE' : 'CALCULATE');
    if (mode === 'simulation') {
      setSimulation(d);
      setSimTx(tx);
      setSimUpload(meta);
      go('compare');
    } else {
      if (calculating.current) return;
      calculating.current = true;
      let server = null;
      try {
        server = await saveAndCalculate(d);
      } catch (error) {
        // 입력 오류 · 기준값 없음 등은 입력 화면에 그대로 머물러요 (입력값 유지)
        // 연결 실패도 배포본에서는 체험용 계산으로 넘어가지 않고 안내만 띄워요
        if (!canUseDemo(error)) {
          notify(error.message);
          return;
        }
      } finally {
        calculating.current = false;
      }
      setCalcResult(server);
      setTransactions(tx);
      setUpload(meta);
      userService.save('transactions', tx);
      userService.save('upload', meta);
      setData(d);
      userService.save('data', d);
      const next = [
        {
          id: newPrototypeId(),
          date: new Date().toLocaleDateString('ko-KR'),
          data: d,
          ...(server ?? taxService.calculate(d)),
          source: server ? 'API' : 'DEMO',
        },
        ...histories,
      ];
      setHistories(next);
      userService.save('histories', next);
      go('dashboard');
      notify(
        server
          ? '입력 정보를 서버에 저장하고 계산을 완료했어요.'
          : '계산 서버에 연결할 수 없어 예시 계산으로 보여 줘요.',
      );
    }
  }
  function saveTx(t) {
    setTransactions(t);
    userService.save('transactions', t);
  }
  const serverResult = calcResult && calcResult.taxYear === data.taxYear ? calcResult : null;
  // 다른 귀속 연도로 받아 둔 그룹 통계는 쓰지 않아요
  const serverGroups = groupStats && groupStats.taxYear === data.taxYear ? groupStats : null;
  const r = serverResult ?? taxService.calculate(data);
  const sim = simulation || data;
  const baseData = compareBase || data;
  const br = taxService.calculate(baseData);
  const sr = taxService.calculate(sim);
  const publicPage = ['home', 'login', 'signup', 'reset'].includes(page);
  return (
    <>
      {publicPage ? (
        <>
          <PublicHeader go={go} />
          {page === 'home' ? (
            <HomePage go={go} startSim={startSim} normalInput={normalInput} />
          ) : (
            <AuthPage page={page} setSession={setSession} go={go} notify={notify} />
          )}
        </>
      ) : (
        <div className="app">
          <Sidebar
            page={page}
            data={data}
            mobile={mobile}
            session={session}
            setSession={setSession}
            admin={admin}
            go={go}
            notify={notify}
            normalInput={normalInput}
          />
          {mobile && <div className="scrim" onClick={() => setMobile(false)} />}
          <div className="app-main">
            <Topbar
              taxYear={
                page === 'input'
                  ? (draftTaxYear ?? (mode === 'simulation' ? sim.taxYear : data.taxYear))
                  : page === 'compare'
                    ? sim.taxYear
                    : data.taxYear
              }
              page={page}
              data={page === 'input' && mode === 'simulation' ? sim : data}
              mobile={mobile}
              setMobile={setMobile}
              notification={notification}
              setNotification={setNotification}
              go={go}
            />
            <main className="content">
              <div className="prototype-notice">
                {session && accountService.isDemo()
                  ? '빠른 체험 모드 · 가입한 회원 계정과 분리된 데이터입니다. '
                  : ''}
                브라우저 시연 · 서버 인증·권한 검증·계정 잠금·탈퇴 API는 미연동입니다. 실제
                개인·금융정보를 입력하지 마세요.
              </div>
              {page === 'input' ? (
                <TaxInputPage
                  data={data}
                  transactions={transactions}
                  mode={mode}
                  session={session}
                  upload={upload}
                  simTx={simTx}
                  simUpload={simUpload}
                  onTaxYearChange={setDraftTaxYear}
                  formSeed={formSeed}
                  go={go}
                  calculate={calculate}
                  sim={sim}
                />
              ) : (
                <>
                  {page === 'dashboard' && (
                    <DashboardPage
                      data={data}
                      result={serverResult ?? undefined}
                      isDemo={!serverResult}
                      groupStats={serverGroups}
                      transactions={transactions}
                      go={go}
                      startSim={startSim}
                      normalInput={normalInput}
                    />
                  )}
                  {page === 'compare' && (
                    <SimulationPage
                      setMode={setMode}
                      simTx={simTx}
                      simUpload={simUpload}
                      histories={histories}
                      saved={saved}
                      setSaved={setSaved}
                      setFormSeed={setFormSeed}
                      go={go}
                      notify={notify}
                      sim={sim}
                      baseData={baseData}
                      br={br}
                      sr={sr}
                    />
                  )}
                  {page === 'guide' && (
                    <ChatbotPage data={data} go={go} startSim={startSim} r={r} />
                  )}
                  {page === 'benefits' && <BenefitsPage data={data} normalInput={normalInput} />}
                  {page === 'stats' && (
                    <StatisticsPage
                      data={data}
                      result={serverResult ?? undefined}
                      groupStats={serverGroups}
                      go={go}
                    />
                  )}
                  {page === 'profile' && (
                    <ProfilePage
                      data={data}
                      setData={setData}
                      setTransactions={setTransactions}
                      setSimulation={setSimulation}
                      setCompareBase={setCompareBase}
                      session={session}
                      setSession={setSession}
                      setUpload={setUpload}
                      setSimTx={setSimTx}
                      setSimUpload={setSimUpload}
                      histories={histories}
                      setHistories={setHistories}
                      saved={saved}
                      setSaved={setSaved}
                      setInquiries={setInquiries}
                      profileTab={profileTab}
                      setProfileTab={setProfileTab}
                      go={go}
                      notify={notify}
                      startSim={startSim}
                      normalInput={normalInput}
                    />
                  )}
                  {page === 'support' && (
                    <SupportPage
                      inquiries={inquiries}
                      setInquiries={setInquiries}
                      search={search}
                      setSearch={setSearch}
                      faq={faq}
                      setFaq={setFaq}
                      supportTab={supportTab}
                      setSupportTab={setSupportTab}
                      notify={notify}
                    />
                  )}
                  {page === 'admin' && (
                    <AdminPage
                      admin={admin}
                      adminTab={adminTab}
                      setAdminTab={setAdminTab}
                      publicList={publicList}
                      setPublicList={setPublicList}
                      refreshSession={refreshSession}
                      go={go}
                      notify={notify}
                    />
                  )}
                </>
              )}
            </main>
          </div>
        </div>
      )}
      {toast && (
        <div className="toast" role="status">
          <CheckCircle2 size={18} />
          {toast}
          <button onClick={() => setToast('')} aria-label="안내 닫기">
            <X size={14} />
          </button>
        </div>
      )}
    </>
  );
}
