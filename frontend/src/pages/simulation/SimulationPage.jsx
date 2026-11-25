import DeductionDetails from '@/components/common/DeductionDetails.jsx';
import React, { Fragment, useEffect, useState } from 'react';
import { ArrowRight, SlidersHorizontal, Plus, Info } from 'lucide-react';
import { userService, settlementLabel, settlementTone, settlementText } from '@/api/services.js';
import { newPrototypeId } from '@/api/account-service.js';
import {
  DEFAULT_SCENARIO_NAME,
  fetchPrediction,
  fetchPredictions,
} from '@/api/prediction-service.js';
import { money, Btn, Tag } from '@/components/common/site-primitives.jsx';
import TaxGuidePanel from '@/components/guide/TaxGuidePanel.jsx';

// 시뮬레이션 전후 결과를 비교하고 원본과 분리해 저장합니다.
// isDemo: true면 체험용 계산(브라우저) 비교, false면 서버 계산 결과(기준 결과 · 시나리오) 비교

// 변경 전후 차이 표기: 환급이 늘거나 추가 납부가 줄면 「유리」 (부호만 보고 헷갈리지 않게)
function changeText(diff) {
  if (diff === 0) return '변화 없음';
  return `${money(Math.abs(diff))}원 ${diff > 0 ? '유리해졌어요' : '불리해졌어요'}`;
}
// 변경한 조건 표의 숫자 표기: 금액은 쉼표 + 원 · 연도는 쉼표 없이 · 인원 · 개월은 단위
const COUNT_UNIT = { family: '명', children: '명', militaryMonths: '개월' };
function conditionValue(key, value) {
  const n = Number(value);
  if (key === 'taxYear') return `${n}년`;
  if (COUNT_UNIT[key]) return `${n}${COUNT_UNIT[key]}`;
  return `${money(n)}원`;
}

export default function SimulationPage({
  setMode,
  simTx,
  simUpload,
  histories,
  saved,
  setSaved,
  setFormSeed,
  go,
  notify,
  sim,
  baseData,
  br,
  sr,
  isDemo = true,
  taxYear,
  basePredictionId = null,
}) {
  return (
    <>
      <div className="heading">
        <div>
          <div className="eyebrow">SIMULATION RESULT</div>
          <h1>조건을 바꾸니, 이렇게 달라졌어요</h1>
          <p>원본 정보는 그대로 유지하고, 변경한 조건의 결과를 비교해요.</p>
        </div>
        <Btn
          secondary
          onClick={() => {
            setMode('simulation');
            setFormSeed((s) => s + 1);
            go('input');
          }}
        >
          다시 조건 수정 <SlidersHorizontal size={16} />
        </Btn>
      </div>
      {isDemo ? (
        <div className="notice amber">
          <Info size={16} />
          체험용 계산 결과입니다. 실제 절세 효과나 법정 계산을 의미하지 않습니다.
        </div>
      ) : (
        <div className="notice">
          <Info size={16} />
          기존 결과와 변경 후 결과 모두 계산 서버 결과예요. 원본 소득 · 지출 정보는 바뀌지 않아요.
        </div>
      )}
      {/* 시뮬레이션 결과와 맞춤 절세 가이드를 좌우로 배치 */}
      <div className="dashboard-grid simulation-guide-grid">
        <div className="dashboard-left">
          <div className="comparison">
            <section className="card">
              <small>기존 {settlementLabel(br.expectedRefund)}</small>
              <h2 className={settlementTone(br.expectedRefund)}>
                {money(Math.abs(br.expectedRefund))}
                <span>원</span>
              </h2>
              <Tag tone="gray">{isDemo ? '원본 저장 정보' : '기존 계산 결과'}</Tag>
            </section>
            <ArrowRight size={22} />
            <section className="card after">
              <small>변경 후 {settlementLabel(sr.expectedRefund)}</small>
              <h2 className={settlementTone(sr.expectedRefund)}>
                {money(Math.abs(sr.expectedRefund))}
                <span>원</span>
              </h2>
              <Tag>{changeText(sr.expectedRefund - br.expectedRefund)}</Tag>
            </section>
          </div>
          <section className="card">
            <h3>변경 후 계산 상세{isDemo ? ' · 체험용' : ''}</h3>
            <div className="review">
              <div>
                과세표준{' '}
                <b>
                  {sr.taxableIncome === null
                    ? isDemo
                      ? '계산 API 연동 후 제공'
                      : '제공되지 않음'
                    : `${money(sr.taxableIncome)}원`}
                </b>
              </div>
              <div>
                산출세액 <b>{money(sr.calculatedTax)}원</b>
              </div>
            </div>
            <DeductionDetails details={sr.details} calculationConnected={!isDemo} />
          </section>
          <section className="card">
            <h3>변경한 조건</h3>
            <table>
              <thead>
                <tr>
                  <th>항목</th>
                  <th>기존</th>
                  <th>변경</th>
                </tr>
              </thead>
              <tbody>
                {[...new Set([...Object.keys(baseData), ...Object.keys(sim)])]
                  .filter(
                    (k) =>
                      ![
                        'consent',
                        'rentPeriod',
                        'socialInsurance',
                        'legacyAnnualRent',
                        'legacyRentNeedsReview',
                        'legacyInsuranceNeedsReview',
                      ].includes(k) && JSON.stringify(baseData[k]) !== JSON.stringify(sim[k]),
                  )
                  .map((k) => (
                    <tr key={k}>
                      <td>
                        {{
                          nationalPension: '국민연금',
                          healthEmploymentInsurance: '건강·고용보험료',
                          taxYear: '귀속 연도',
                          housingSaving: '주택청약',
                          pensionSaving: '연금저축',
                          irp: 'IRP',
                          simulationSpend: '소비 유형별 가상 추가액',
                          scenarioName: '시나리오명',
                          salary: '총급여',
                          paid: '기납부세액',
                          credit: '신용카드',
                          debit: '체크카드',
                          cash: '현금영수증',
                          family: '부양가족',
                          rent: '월세 월 납부액',
                          children: '20세 이하 자녀 수',
                          militaryMonths: '군 복무 개월 수',
                          companySize: '회사 규모',
                          headOfHousehold: '무주택 세대주',
                          rentPeriod: '월세 입력 단위',
                          sme: '감면 자격',
                          name: '이름',
                          birth: '생년월일',
                          gender: '성별',
                          housing: '주택 상태',
                          employed: '취업일',
                          email: '이메일',
                        }[k] || k}
                      </td>
                      <td>
                        {typeof baseData[k] === 'number'
                          ? conditionValue(k, baseData[k])
                          : baseData[k] == null
                            ? '없음'
                            : typeof baseData[k] === 'object'
                              ? '기존 가상 소비 ' + (baseData[k]?.length || 0) + '건'
                              : String(baseData[k])}
                      </td>
                      <td>
                        {typeof sim[k] === 'number'
                          ? conditionValue(k, sim[k])
                          : typeof sim[k] === 'object'
                            ? Array.isArray(sim[k])
                              ? sim[k]
                                  .map(
                                    (row) =>
                                      `${{ GENERAL: '일반', TRANSIT: '대중교통', MARKET: '전통시장', CULTURE: '문화체육' }[row.category] || row.category} / ${{ CREDIT: '신용카드', CHECK: '체크카드', CASH: '현금영수증' }[row.payment] || row.payment} ${money(row.amount)}원`,
                                  )
                                  .join(', ')
                              : '변경 정보'
                            : String(sim[k])}
                      </td>
                    </tr>
                  ))}
              </tbody>
            </table>
            {JSON.stringify({
              ...baseData,
              consent: false,
            }) ===
              JSON.stringify({
                ...sim,
                consent: false,
              }) && <p className="empty">변경한 조건이 없습니다.</p>}
            <div className="review">
              <div>
                변경 후 결정세액 <b>{money(sr.decidedTax)}원</b>
              </div>
              <div>
                변경 후 기납부세액 <b>{money(sr.withheldTax)}원</b>
              </div>
            </div>
            <div className="actions">
              {/* 서버 결과는 시뮬레이션 API가 시나리오로 저장해요 → 브라우저 저장 버튼은 체험용일 때만 */}
              {isDemo ? (
                <Btn
                  onClick={() => {
                    const next = [
                      {
                        id: newPrototypeId(),
                        scenarioName: sim.scenarioName || DEFAULT_SCENARIO_NAME,
                        baselineData: structuredClone(baseData),
                        basePredictionId: histories[0]?.id || null,
                        date: new Date().toLocaleDateString('ko-KR'),
                        data: sim,
                        transactions: simTx,
                        upload: simUpload,
                        ...sr,
                      },
                      ...saved,
                    ];
                    setSaved(next);
                    userService.save('saved', next);
                    notify('시나리오를 이 브라우저에 별도로 저장했습니다. 원본은 유지됩니다.');
                  }}
                >
                  시뮬레이션 저장 <Plus size={16} />
                </Btn>
              ) : (
                <p className="muted">
                  {Number.isInteger(sr.predictionId)
                    ? '이 결과는 시나리오로 저장됐어요. 아래 결과 이력에서 다시 열 수 있어요.'
                    : '서버 저장 설정이 꺼져 있어 이 결과는 저장되지 않았어요.'}
                </p>
              )}
              <Btn secondary onClick={() => go('dashboard')}>
                기존 결과로 돌아가기
              </Btn>
            </div>
          </section>
          {!isDemo && (
            <PredictionHistory
              taxYear={taxYear}
              basePredictionId={basePredictionId}
              latestId={sr.predictionId}
            />
          )}
        </div>

        <aside className="dash-ai" aria-label="맞춤 절세 가이드">
          <TaxGuidePanel predictionId={sr.predictionId} />
        </aside>
      </div>
    </>
  );
}

// 결과 이력 (F-04-02) · 현재(기준 결과)와 시나리오 목록 · 하나를 골라 항목별 내역을 다시 열어요
// 서버 결과로 비교할 때만 보여 줘요 (체험용 비교에는 서버 이력이 없음)
function PredictionHistory({ taxYear, basePredictionId, latestId }) {
  const [rows, setRows] = useState(null); // null = 불러오는 중
  const [error, setError] = useState('');
  // 열어 본 결과 { row, result } · 누른 줄 바로 아래에 펼쳐요
  // (표 아래 멀리 나오면 누른 뒤 변화가 안 보였음 · 11/17 사용자 흐름 테스트)
  const [opened, setOpened] = useState(null);
  const [opening, setOpening] = useState(null);
  // 새 시나리오가 저장되면(latestId가 바뀌면) 목록을 다시 불러와요
  useEffect(() => {
    let ignore = false;
    setRows(null);
    setError('');
    fetchPredictions(taxYear)
      .then((found) => {
        if (!ignore) setRows(found);
      })
      .catch((e) => {
        if (!ignore) {
          setRows([]);
          setError(e.message);
        }
      });
    return () => {
      ignore = true;
    };
  }, [taxYear, latestId]);
  async function open(row) {
    // 이미 펼친 줄을 다시 누르면 접어요
    if (opened?.row.predictionId === row.predictionId) {
      setOpened(null);
      return;
    }
    setOpening(row.predictionId);
    setError('');
    try {
      setOpened({ row, result: await fetchPrediction(row.predictionId) });
    } catch (e) {
      setError(e.message);
    } finally {
      setOpening(null);
    }
  }
  return (
    <section className="card">
      <h3>결과 이력</h3>
      {error && <p className="empty">{error}</p>}
      {rows === null ? (
        <p className="empty">결과 이력을 불러오는 중이에요.</p>
      ) : rows.length === 0 ? (
        !error && <p className="empty">저장된 결과가 없어요.</p>
      ) : (
        <table>
          <thead>
            <tr>
              <th>이름</th>
              <th>예상 환급 · 추가 납부</th>
              <th>계산일</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => (
              <Fragment key={row.predictionId}>
                <tr>
                  <td>
                    {row.scenarioName || '현재'}
                    {row.predictionId === basePredictionId && ' · 기준'}
                    {row.predictionId === latestId && ' · 방금 계산'}
                  </td>
                  <td className={settlementTone(row.expectedRefund)}>
                    {settlementText(row.expectedRefund)}
                  </td>
                  <td>{row.predictedAt.slice(0, 10)}</td>
                  <td>
                    <button
                      type="button"
                      className="text-btn"
                      disabled={opening !== null}
                      onClick={() => open(row)}
                    >
                      {opening === row.predictionId
                        ? '여는 중'
                        : opened?.row.predictionId === row.predictionId
                          ? '닫기'
                          : '열기'}
                    </button>
                  </td>
                </tr>
                {opened?.row.predictionId === row.predictionId && (
                  <tr>
                    <td colSpan={4}>
                      <div className="review">
                        <div>
                          {settlementLabel(opened.result.expectedRefund)}{' '}
                          <b className={settlementTone(opened.result.expectedRefund)}>
                            {money(Math.abs(opened.result.expectedRefund))}원
                          </b>
                        </div>
                        <div>
                          결정세액 <b>{money(opened.result.decidedTax)}원</b>
                        </div>
                        <DeductionDetails details={opened.result.details} calculationConnected />
                      </div>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
      )}
    </section>
  );
}
