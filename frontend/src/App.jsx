import { uploadTotals } from '@/api/expense-service.js';
import { migrateCalculationHistory } from '@/api/calculation-contract.js';
import { useHashRouting } from '@/routes/useHashRouting.js';
import React from 'react';
import { useState, useEffect, useRef } from 'react';
import { initial, normalizeTaxData, userService } from '@/api/services.js';
import { fetchLatestCalculation, saveAndCalculate } from '@/api/calculation-service.js';
import { fetchGroupStats } from '@/api/statistics-service.js';
import { AUTH_EXPIRED_EVENT } from '@/api/api-client.js';
import { notificationFromEvent, openNotificationStream } from '@/api/notification-stream.js';
import { fetchNotifications, markNotificationsRead, toAlert } from '@/api/notification-service.js';
import { cardTotalsByMethod, fetchCardTotals, simulate } from '@/api/prediction-service.js';
import { fetchIncome, incomeToInput } from '@/api/income-service.js';
import { accountService, newPrototypeId } from '@/api/account-service.js';
import { CheckCircle2, X } from 'lucide-react';
import AdminPage from '@/pages/admin/AdminPage.jsx';
import SupportPage from '@/pages/support/SupportPage.jsx';
import ProfilePage from '@/pages/profile/ProfilePage.jsx';
import StatisticsPage from '@/pages/statistics/StatisticsPage.jsx';
import BenefitsPage from '@/pages/tax/BenefitsPage.jsx';
import SimulationPage from '@/pages/simulation/SimulationPage.jsx';
import DashboardPage from '@/pages/dashboard/DashboardPage.jsx';
import TaxInputPage from '@/pages/tax/TaxInputPage.jsx';
import Topbar from '@/components/layout/Topbar.jsx';
import Sidebar from '@/components/layout/Sidebar.jsx';
import AuthPage from '@/pages/auth/AuthPage.jsx';
import HomePage from '@/pages/home/HomePage.jsx';
import PublicHeader from '@/components/layout/PublicHeader.jsx';
import FaqLayout from '@/components/chatbot/FaqLayout.jsx';

// 로그인 전에 볼 수 있는 화면 (비회원은 회원가입, 비로그인 회원은 로그인만 · 홈은 소개 화면)
// [추가 11/23] 체험판 삭제 · 이유: 로그인하지 않으면 이 목록 밖의 화면(대시보드 등)은 로그인 화면으로 보내요
const PUBLIC_PAGES = ['home', 'login', 'signup', 'reset'];
// 로그인한 회원이 로그인 · 회원가입 화면 주소로 오면 대시보드로 보내요
const LOGIN_PAGES = ['login', 'signup', 'reset'];

// [추가 11/23] 서버 계산 결과가 없을 때 체험용 계산 대신 보여 주는 안내
// title: 제목 · children: 설명 · action: 버튼 이름 · onAction: 버튼을 누르면 할 일
function NeedResult({ title, children, action, onAction }) {
  return (
    <section className="card empty" role="status">
      <h2>{title}</h2>
      <p>{children}</p>
      <button className="btn" onClick={onAction}>
        {action}
      </button>
    </section>
  );
}

// 공통 상태와 기존 이벤트 처리 흐름을 유지하는 앱 조정자입니다.
// [수정 11/23] 체험판 기능 삭제 · 이유: 팀 결정(체험판 기능 자체를 삭제)
//   - 로그인하지 않으면 홈 · 로그인 · 회원가입만 볼 수 있어요 (PUBLIC_PAGES)
//   - 서버 연결 실패 때 보여 주던 체험용 계산(taxService · canUseDemo) 삭제 → 오류 안내 후 입력 화면에 머물러요
//   - 서버 계산 결과가 없으면 대시보드 · 통계 · 비교 화면 대신 「먼저 계산해 주세요」 안내(NeedResult)
//   - 브라우저에만 저장하던 문의 · 관리자 · 시나리오 저장(inquiryService · recordAccess · saved 등) 삭제
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
  // [수정 11/23] 처음 화면을 그릴 때부터 로그인 상태를 알아야 로그인 검사가 맞게 돼요 (전에는 null로 시작)
  const [session, setSession] = useState(() => accountService.current());
  const admin = session?.role === 'ADMIN';
  const [upload, setUpload] = useState(null);
  const [simTx, setSimTx] = useState([]);
  // 시뮬레이션 출발 카드 사용액 = 서버 합계 {rows, byMethod} (TT-118)
  // undefined: 서버 기준 결과 없음 · null: 불러오기 실패 → 계산할 때 다시 불러옴
  const [simCard, setSimCard] = useState(undefined);
  const [simUpload, setSimUpload] = useState(null);
  const [histories, setHistories] = useState([]);
  const [notification, setNotification] = useState(false);
  // 서버 알림 목록 (TT-151 · 최근 20개) · 상단 종 아이콘에서 보여 줘요
  // [수정 11/23 · TT-159] 로그인하면 서버에 저장된 목록을 불러와서, 새로고침해도 유지돼요
  const [alerts, setAlerts] = useState([]);
  // 목록을 불러올 때 서버가 준 안 읽은 개수 (목록 20개 밖의 안 읽은 알림까지 포함 · 열면 0)
  const [baseUnread, setBaseUnread] = useState(0);
  const [profileTab, setProfileTab] = useState('개인정보');
  const [search, setSearch] = useState('');
  const [filter, setFilter] = useState('전체');
  const [faq, setFaq] = useState(0);
  const [supportTab, setSupportTab] = useState('자주 묻는 질문');
  const [statsFilter, setStatsFilter] = useState('연령대');
  const [formSeed, setFormSeed] = useState(0);
  // 서버(계산 API) 계산 결과 · 없으면 대시보드는 「먼저 계산해 주세요」 안내를 보여 줘요
  const [calcResult, setCalcResult] = useState(null);
  // 서버(시뮬레이션 API) 계산 결과 · 없으면 비교 화면은 안내를 보여 줘요
  const [simResult, setSimResult] = useState(null);
  // 시뮬레이션의 기준 결과 (시뮬레이션을 시작할 때의 서버 계산 결과)
  const [baseResult, setBaseResult] = useState(null);
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
    // 로그인 토큰이 만료 · 무효(서버 401) → 로그인 화면으로 (api-client.js expireLogin)
    const expired = () => {
      setSession(null);
      setPage('login');
      location.hash = 'login';
      setToast('로그인이 만료되었어요. 다시 로그인해 주세요.');
    };
    window.addEventListener(AUTH_EXPIRED_EVENT, expired);
    return () => {
      window.removeEventListener('storage', sync);
      window.removeEventListener(AUTH_EXPIRED_EVENT, expired);
    };
  }, []);
  // 서버 알림(SSE · TT-151): 로그인한 동안 연결해 두고, 계산 완료 · 마감 안내 알림이 오면 토스트로 보여 줘요
  // 다른 탭에서 계산해도 이 탭에 알림이 와요 · 로그아웃 · 계정 바뀜 · 화면 닫힘이면 연결을 끊어요
  // [수정 11/23 · 이우진 · TT-159] 저장된 목록을 먼저 불러오고, 그 마지막 번호부터 연결 → 그 사이 알림도 놓치지 않음
  useEffect(() => {
    setAlerts([]); // 계정이 바뀌면 이전 계정 알림은 지움
    setBaseUnread(0);
    if (!session?.server) return undefined; // 로그인 전에는 연결하지 않음
    let alive = true;
    let close = null;
    // 같은 번호가 두 번 오면(목록 · 다시 받은 알림) 하나만
    const addAlert = (a) =>
      setAlerts((prev) => (prev.some((p) => p.id === a.id) ? prev : [a, ...prev].slice(0, 20)));
    const open = (lastEventId) => {
      if (!alive) return;
      close = openNotificationStream({
        lastEventId,
        onEvent: (event, data) => {
          if (event !== 'notification') return;
          const alert = notificationFromEvent(data);
          if (!alert) return;
          setToast(alert.message);
          addAlert({ ...alert, fresh: true }); // 목록을 불러온 뒤 새로 온 알림 (배지에 더함)
        },
      });
    };
    fetchNotifications()
      .then((list) => {
        if (!alive) return;
        const items = (list?.items ?? []).map(toAlert).filter((a) => a.message);
        setAlerts(items);
        setBaseUnread(Number.isInteger(list?.unreadCount) ? list.unreadCount : 0);
        const ids = items.map((a) => a.id).filter(Number.isInteger);
        open(ids.length ? Math.max(...ids) : undefined);
      })
      .catch(() => open(undefined)); // 목록을 못 불러와도 실시간 알림은 받음
    return () => {
      alive = false;
      close?.();
    };
  }, [session?.id, session?.server]);
  // 로그인한 계정에 해당하는 입력·거래·이력을 불러옵니다.
  useEffect(() => {
    const fallback = {
      ...initial,
      ...(session
        ? {
            name: session.username || '사용자',
            // 서버 로그인 회원은 이메일 · 생년월일 · 성별이 없음 → 빈 값 (undefined로 두면 화면 계산에서 오류)
            email: session.email || '',
            birth: session.birth || '',
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
            // 서버 로그인 회원은 이메일 · 생년월일 · 성별이 없음 → 빈 값 (undefined로 두면 화면 계산에서 오류)
            email: session.email || '',
            birth: session.birth || '',
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
    setSimulation(null);
    setCompareBase(null);
    setSimResult(null);
    setBaseResult(null);
    setSimTx([]);
    setSimUpload(null);
    setMode('normal');
  }, [session?.id]);
  useHashRouting({ setPage, setMode, setFormSeed, setMobile });
  // 대시보드 · 통계 화면에 들어왔는데 서버 계산 결과가 화면에 없으면, DB에 저장된 최근 결과를 불러와요
  // (다른 브라우저에서 계산했거나 브라우저 저장이 지워졌어도 결과가 보이게)
  // 계산한 적이 없거나(NOT_FOUND) 서버에 연결할 수 없으면 지금 화면을 그대로 둬요
  useEffect(() => {
    if (page !== 'dashboard' && page !== 'stats') return;
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
  // 계산은 입력 화면에서 하고 끝나면 대시보드로 넘어오므로, 화면이 바뀔 때 다시 불러오면 새 총급여가 반영돼요
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
  }, [page, data.taxYear]);
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
    setPage(p);
    location.hash = p;
    setMobile(false);
    window.scrollTo(0, 0);
  }
  function notify(t) {
    setToast(t);
  }
  // 실제 입력을 복사해 가상 조건을 만들므로 원본 소득·지출을 덮어쓰지 않습니다.
  // 출발값은 서버에 저장된 소득(기준 결과를 계산한 값)으로 맞춰요. 브라우저 입력값은 다른 브라우저 ·
  // 시크릿 창 · 저장 삭제 때 비어 있어서, 바꾸지 않은 칸까지 기준과 달라지는 것을 막아요.
  // 소득이 없거나(NOT_FOUND) 서버에 연결할 수 없으면 브라우저 입력값 그대로 시작해요.
  async function startSim() {
    let start = data;
    try {
      start = { ...data, ...incomeToInput(await fetchIncome(data.taxYear)) };
    } catch {
      // 서버 소득이 없으면 브라우저 입력값으로 시작
    }
    // 카드 사용액도 기준 계산과 같은 서버 합계에서 출발 (브라우저 거래 목록은 다른 PC · 새 브라우저에서 비어 있음)
    // 서버 기준 결과가 있을 때만
    let card;
    if (calcResult && calcResult.taxYear === data.taxYear) {
      try {
        const rows = await fetchCardTotals(data.taxYear);
        card = { rows, byMethod: cardTotalsByMethod(rows) };
        start = { ...start, ...card.byMethod };
      } catch {
        card = null; // 계산할 때 다시 불러와요 (그때도 실패하면 계산하지 않음)
      }
    }
    setSimCard(card);
    setCompareBase(structuredClone(start));
    // 기준 결과 = 지금 대시보드에 보이는 서버 계산 결과 (없으면 null → 계산할 때 안내)
    setBaseResult(calcResult && calcResult.taxYear === data.taxYear ? calcResult : null);
    setSimResult(null);
    setSimTx(transactions.map((t) => ({ ...t })));
    setSimUpload(upload ? { ...upload } : null);
    setSimulation({ ...start });
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
  // 서버에 연결할 수 없거나 오류가 나면 안내만 띄우고 입력 화면에 머물러요 (입력값 유지).
  // 시뮬레이션: 바꾼 조건 전부를 시뮬레이션 API(POST)로 보내 다시 계산해요. 원본 소득 · 지출은 덮어쓰지 않아요.
  async function calculate(input, tx, meta) {
    const d = { ...input, ...uploadTotals(tx, input.taxYear) };
    if (mode === 'simulation') {
      if (calculating.current) return;
      let server;
      // 기준 결과가 없으면(서버 계산 전 · 저장 꺼짐) 서버 시뮬레이션을 할 수 없어요 → 안내만
      const baseId = baseResult?.predictionId;
      if (!Number.isInteger(baseId) || baseResult.taxYear !== d.taxYear) {
        notify(
          baseResult && baseResult.taxYear !== d.taxYear
            ? '귀속 연도는 기존 결과와 같아야 비교할 수 있어요.'
            : '먼저 기존 정보로 계산해 주세요. 비교할 기준 결과가 없어요.',
        );
        return;
      }
      calculating.current = true;
      try {
        let card = simCard;
        if (!card) {
          const rows = await fetchCardTotals(d.taxYear);
          card = { rows, byMethod: cardTotalsByMethod(rows) };
          setSimCard(card);
        }
        Object.assign(d, card.byMethod); // 비교 화면 합계도 서버 기준
        server = await simulate(d, card.rows, baseId);
      } catch (error) {
        // 입력 오류 · 기준 결과 없음 · 서버 연결 실패는 입력 화면에 그대로 머물러요 (입력값 유지)
        notify(error.message);
        return;
      } finally {
        calculating.current = false;
      }
      setSimResult(server);
      setSimulation(d);
      setSimTx(tx);
      setSimUpload(meta);
      go('compare');
      notify(
        server.predictionId
          ? '바꾼 조건으로 계산하고 시나리오로 저장했어요. 원본 정보는 그대로예요.'
          : '바꾼 조건으로 계산했어요. (서버 저장 설정이 꺼져 있어 시나리오는 저장되지 않았어요)',
      );
    } else {
      if (calculating.current) return;
      calculating.current = true;
      let server;
      try {
        server = await saveAndCalculate(d);
      } catch (error) {
        // 입력 오류 · 기준값 없음 · 서버 연결 실패는 입력 화면에 그대로 머물러요 (입력값 유지)
        notify(error.message);
        return;
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
          ...server,
          source: 'API',
        },
        ...histories,
      ];
      setHistories(next);
      userService.save('histories', next);
      go('dashboard');
      notify('입력 정보를 서버에 저장하고 계산을 완료했어요.');
    }
  }
  function saveTx(t) {
    setTransactions(t);
    userService.save('transactions', t);
  }
  const serverResult = calcResult && calcResult.taxYear === data.taxYear ? calcResult : null;
  // 다른 귀속 연도로 받아 둔 그룹 통계는 쓰지 않아요
  const serverGroups = groupStats && groupStats.taxYear === data.taxYear ? groupStats : null;
  const sim = simulation || data;
  const baseData = compareBase || data;
  // 비교 화면: 서버 시뮬레이션 결과와 기준 결과가 둘 다 있을 때만 보여 줘요
  const serverCompare = Boolean(simResult && baseResult);
  // 로그인 검사 [추가 11/23]: 로그인 안 했으면 공개 화면 밖은 로그인 화면 · 로그인했으면 로그인 · 가입 화면은 대시보드
  const shownPage = !session
    ? PUBLIC_PAGES.includes(page)
      ? page
      : 'login'
    : LOGIN_PAGES.includes(page)
      ? 'dashboard'
      : page;
  useEffect(() => {
    if (shownPage === page) return;
    setPage(shownPage);
    location.hash = shownPage;
  }, [shownPage, page]);
  const publicPage = PUBLIC_PAGES.includes(shownPage);
  return (
    <FaqLayout sessionId={session?.id ?? null} isAuthenticated={Boolean(session) && !publicPage}>
      {publicPage ? (
        <>
          <PublicHeader go={go} />
          {shownPage === 'home' ? (
            <HomePage go={go} />
          ) : (
            <AuthPage page={shownPage} setSession={setSession} go={go} notify={notify} />
          )}
        </>
      ) : (
        <div className="app">
          <Sidebar
            page={page}
            data={data}
            mobile={mobile}
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
              alerts={alerts}
              unreadCount={baseUnread + alerts.filter((a) => a.fresh && !a.read).length}
              onReadAlerts={() => {
                setAlerts((prev) => prev.map((a) => ({ ...a, read: true })));
                setBaseUnread(0);
                markNotificationsRead().catch(() => {}); // 실패해도 화면은 읽음 · 다음 목록 불러올 때 다시 안 읽음으로 보일 수 있음
              }}
              setNotification={setNotification}
              go={go}
            />
            <main className="content">
              {page === 'input' ? (
                <TaxInputPage
                  data={data}
                  transactions={transactions}
                  mode={mode}
                  session={session}
                  upload={upload}
                  simTx={simTx}
                  simUpload={simUpload}
                  simCardTotals={simCard?.byMethod ?? null}
                  simCardMissing={simCard === null}
                  onTaxYearChange={setDraftTaxYear}
                  formSeed={formSeed}
                  go={go}
                  calculate={calculate}
                  sim={sim}
                />
              ) : (
                <>
                  {/* [수정 11/23] 대시보드 · 통계 · 비교: 서버 계산 결과가 없으면 체험용 계산 대신 안내 (NeedResult) */}
                  {(page === 'dashboard' || page === 'stats') && !serverResult && (
                    <NeedResult
                      title="아직 계산 결과가 없어요"
                      action="정보 입력하고 계산하기"
                      onAction={normalInput}
                    >
                      소득과 카드 내역을 입력하고 계산하면 결과를 볼 수 있어요. 이미 계산했다면 잠시
                      후 결과를 불러와요.
                    </NeedResult>
                  )}
                  {page === 'dashboard' && serverResult && (
                    <DashboardPage
                      data={data}
                      result={serverResult}
                      groupStats={serverGroups}
                      transactions={transactions}
                      go={go}
                      startSim={startSim}
                      normalInput={normalInput}
                    />
                  )}
                  {page === 'compare' &&
                    (serverCompare ? (
                      <SimulationPage
                        setMode={setMode}
                        setFormSeed={setFormSeed}
                        go={go}
                        sim={sim}
                        baseData={baseData}
                        br={baseResult}
                        sr={simResult}
                        taxYear={sim.taxYear}
                        basePredictionId={baseResult.predictionId}
                      />
                    ) : (
                      <NeedResult
                        title="비교할 결과가 없어요"
                        action="조건 변경 시뮬레이션 시작"
                        onAction={startSim}
                      >
                        조건을 바꿔 계산하면 기존 결과와 비교해 볼 수 있어요.
                      </NeedResult>
                    ))}
                  {page === 'benefits' && <BenefitsPage data={data} normalInput={normalInput} />}
                  {page === 'stats' && serverResult && (
                    <StatisticsPage
                      data={data}
                      result={serverResult}
                      groupStats={serverGroups}
                      go={go}
                    />
                  )}
                  {page === 'profile' && (
                    <ProfilePage
                      data={data}
                      setData={setData}
                      session={session}
                      setSession={setSession}
                      histories={histories}
                      profileTab={profileTab}
                      setProfileTab={setProfileTab}
                      go={go}
                      notify={notify}
                      normalInput={normalInput}
                    />
                  )}
                  {page === 'support' && (
                    <SupportPage
                      search={search}
                      setSearch={setSearch}
                      faq={faq}
                      setFaq={setFaq}
                      supportTab={supportTab}
                      setSupportTab={setSupportTab}
                      notify={notify}
                    />
                  )}
                  {page === 'admin' && <AdminPage admin={admin} go={go} />}
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
    </FaqLayout>
  );
}
