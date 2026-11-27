// 일반 입력과 시뮬레이션이 함께 사용하는 6단계 폼입니다. 입력은 임시 상태에 두고 완료 시 상위로 전달합니다.
// [수정 11/20 고은] useRef 추가 (아래 formRef에서 씀)
import React, { useState, useEffect, useRef } from 'react';
import {
  ArrowRight,
  ChevronLeft,
  Check,
  Info,
  ShieldCheck,
  SlidersHorizontal,
  Users,
} from 'lucide-react';
import { normalizeTaxData, validateTaxData } from '@/api/services.js';
import { uploadTotals, uploadBlocker } from '@/api/expense-service.js';
import { SCENARIO_NAME_MAX } from '@/api/prediction-service.js';
import { ExpenseUpload } from '@/components/expense/ExpenseUpload.jsx';
const steps = ['기본 정보', '소득 정보', '지출 정보', '부양가족', '주거 정보', '공제·감면'];
function Field({ label, children, hint }) {
  return (
    <label className="field">
      <span>{label}</span>
      {children}
      {hint && <small>{hint}</small>}
    </label>
  );
}
// 무주택 세대주 Y/N을 정할 수 없는 상태 (세대주 「아니요」 또는 「주택 보유」면 N · 「무주택」 + 「예」면 Y · 나머지는 미확정)
function housingUnconfirmed(d) {
  if (d.headOfHousehold === '아니요' || d.housing === '주택 보유') return false;
  return !(d.headOfHousehold === '예' && d.housing === '무주택');
}

export default function TaxInfoForm({
  mode,
  data,
  transactions,
  upload,
  spendTotals = null, // 시뮬레이션 출발 카드 합계 {credit, debit, cash} (서버 · 없으면 업로드 거래로 계산)
  spendTotalsMissing = false, // 서버 카드 합계를 못 불러옴 (계산할 때 다시 시도)
  onSubmit,
  onCancel,
  onTaxYearChange,
}) {
  // 편집 중인 값은 임시 상태입니다. 완료할 때만 App의 저장/계산 처리에 전달합니다.
  const [d, setD] = useState(() => normalizeTaxData(data)),
    [step, setStep] = useState(0),
    [err, setErr] = useState('');
  // [추가 11/20 고은] 입력 폼(<form>)을 가리키는 손잡이
  // 이유: 위쪽 단계 버튼은 폼 바깥에 있어서, 눌러도 브라우저의 「이 입력란을 작성하세요」 검사가 돌지 않음
  //   → 단계 버튼을 누를 때 이 손잡이로 폼을 찾아 검사를 직접 시킴
  // useRef: 화면이 다시 그려져도 바뀌지 않는 상자 · .current에 실제 <form> 요소가 들어감
  const formRef = useRef(null);
  const [tx, setTx] = useState(() => structuredClone(transactions)),
    [uploadMeta, setUploadMeta] = useState(() => structuredClone(upload));
  // 파일 추가·삭제 또는 귀속 연도 변경 시 업로드 거래에서만 실제 지출 합계를 갱신합니다.
  // 시뮬레이션은 서버 카드 합계(spendTotals)를 보여 줘요 (기준 계산과 같은 값 · TT-118)
  useEffect(() => {
    setD((current) => ({ ...current, ...(spendTotals ?? uploadTotals(tx, current.taxYear)) }));
  }, [tx, d.taxYear, spendTotals]);
  const familyError =
    (d.children ?? 0) > d.family
      ? '20세 이하 자녀 수는 추가 부양가족 수(본인 제외)보다 많을 수 없습니다.'
      : '';
  const move = (next) => {
    if (next > step) {
      // [추가 11/20 고은] 앞으로 넘어갈 때 지금 단계 입력칸을 브라우저 규칙(필수 · 정수 · 0 이상)으로 검사
      // reportValidity(): 틀린 칸이 있으면 그 칸에 말풍선을 띄우고 false를 돌려줌 → 넘어가지 않음
      // formRef.current가 아직 없으면(null) 검사를 건너뜀 (오류 방지)
      if (formRef.current && !formRef.current.reportValidity()) return;
      // [수정 11/23 고은] 기본 정보(0) → 소득 정보(1)로 갈 때는 전체 검사(validateTaxData)를 건너뜀
      // 이유: 전체 검사는 아직 안 지나간 소득 정보 칸까지 봐서, 소득 칸을 비우고 뒤로 왔다가
      //   다시 소득 정보로 가려 하면 「금액은 0 이상으로 입력해주세요」로 막혔음 (그 칸을 채우러 가는 길인데 막힘)
      //   → 귀속 연도 같은 기본 정보 칸은 바로 위 reportValidity가 검사하고,
      //     소득 정보 칸은 소득 정보를 지나갈 때(1 → 2 이상) 지금처럼 전체 검사로 막음
      const toIncomeOnly = step === 0 && next === 1;
      const error = toIncomeOnly
        ? ''
        : validateTaxData(d) || (step >= 2 && mode !== 'simulation' ? uploadBlocker(tx) : '');
      if (error) {
        setErr(error);
        return;
      }
      if (step === 1 && (!Number.isFinite(d.salary) || !Number.isFinite(d.paid))) {
        setErr('총급여·기납부 소득세는 필수입니다.');
        return;
      }
      // 주거 정보(5단계)를 넘어갈 때 무주택 세대주 여부가 정해져야 함 (정보 입력 · 시뮬레이션 공통)
      // 단계 번호를 눌러 건너뛰어도 같이 막음 · 기준은 prediction-service homelessHeadYn과 같음
      if (step <= 4 && next > 4 && housingUnconfirmed(d)) {
        setErr('주택 보유 상태와 무주택 세대주 여부를 선택해주세요.');
        if (step !== 4) setStep(4);
        return;
      }
    }
    setErr('');
    setStep(next);
  };
  const f = (key, label, type = 'number', hint = '', unit = '원') => (
    <Field label={label} hint={hint}>
      <input
        required
        type={type}
        min={type === 'number' ? 0 : undefined}
        step={type === 'number' ? 1 : undefined}
        readOnly={
          ['name', 'birth', 'email'].includes(String(key)) ||
          (mode === 'simulation' && key === 'employed')
        }
        value={typeof d[key] === 'number' && !Number.isFinite(d[key]) ? '' : String(d[key] ?? '')}
        // [추가 11/20 고은] 소수를 넣었을 때 브라우저 기본 문구 대신 우리 문구로 안내
        // 이유: 기본 문구 「유효한 값을 입력해 주세요. 가장 근접한 유효 값 2개는…」가 어렵고 길어서
        // onInvalid: 입력값이 규칙(step=1 → 정수만)에 안 맞을 때 브라우저가 부르는 함수
        // validity.stepMismatch: 「step 간격에 안 맞음」 = 정수 칸에 소수를 넣었다는 뜻
        // setCustomValidity(문구): 브라우저 말풍선에 띄울 문구를 바꿈
        onInvalid={(e) => {
          if (e.target.validity.stepMismatch)
            e.target.setCustomValidity('소수는 입력할 수 없어요.');
        }}
        onChange={(e) => {
          // [추가 11/20 고은] 다시 입력하면 바꿔 둔 문구를 지움
          // 이유: 지우지 않으면 정수로 고쳐도 브라우저가 계속 「틀린 값」으로 보고 넘어가지 못함
          e.target.setCustomValidity('');
          setD({
            ...d,
            ...(key === 'rent' ? { legacyRentNeedsReview: false } : {}),
            ...(['nationalPension', 'healthEmploymentInsurance'].includes(key)
              ? { legacyInsuranceNeedsReview: false }
              : {}),
            [key]:
              type === 'number'
                ? e.target.value === ''
                  ? NaN
                  : Number(e.target.value)
                : e.target.value,
          });
        }}
      />
      {type === 'number' && <span className="input-unit">{unit}</span>}
    </Field>
  );
  return (
    <>
      <div className="heading">
        <div>
          <div className="eyebrow">MY TAX INFORMATION</div>
          <h1>{mode === 'simulation' ? '조건 변경 시뮬레이션' : '연말정산 정보 입력'}</h1>
          <p>나의 정보를 하나씩 채우고, 환급의 가능성을 확인해보세요.</p>
        </div>
        <span className="tag blue">{step + 1} / 6 단계</span>
      </div>
      {mode === 'simulation' && (
        <div className="notice amber">
          <SlidersHorizontal size={18} />
          시뮬레이션 모드 · 변경한 값은 실제 저장정보에 반영되지 않습니다.
        </div>
      )}
      <div className="stepper">
        {steps.map((s, i) => (
          <button
            type="button"
            key={s}
            onClick={() => move(i)}
            className={i === step ? 'current' : i < step ? 'done' : ''}
          >
            <span>{i < step ? <Check size={16} /> : i + 1}</span>
            {s}
          </button>
        ))}
      </div>
      <form
        // [추가 11/20 고은] 위 formRef가 이 폼을 가리키게 연결
        ref={formRef}
        className="card form-card"
        onSubmit={(e) => {
          e.preventDefault();
          // [수정 11/23 고은] 마지막 단계 전에는 「다음 단계」 검사를 move 함수에 맡김
          // 이유: 여기서 전체 검사(validateTaxData)를 먼저 하면, move에서 「기본 정보 → 소득 정보는 전체 검사 건너뛰기」로
          //   고친 것이 「다음 단계」 버튼에는 적용되지 않아 같은 곳에서 또 막혔음
          //   → 위쪽 단계 버튼과 「다음 단계」 버튼이 같은 규칙(move)으로 검사하도록 맞춤
          if (step < 5) {
            move(step + 1);
            return;
          }
          const error =
            validateTaxData(d) || (step >= 2 && mode !== 'simulation' ? uploadBlocker(tx) : '');
          if (error) {
            setErr(error);
            return;
          }
          setErr('');
          // [수정 11/23] 「시연용 예시 결과」 문구 → 예상 결과 안내 · 이유: 체험판 기능 삭제 결정 (계산은 서버가 해요)
          if (!d.consent) setErr('예상 결과 안내를 확인하고 체크해 주세요.');
          else {
            try {
              onSubmit(d, tx, uploadMeta);
            } catch (error) {
              setErr(error.message || '계산 요청을 확인해주세요.');
            }
          }
        }}
      >
        <div className="section-number">STEP 0{step + 1}</div>
        <h2>{steps[step]}를 확인해주세요</h2>
        <p className="muted">
          {mode === 'simulation'
            ? '저장된 정보가 자동으로 입력되어 있어요. 원하는 항목만 바꿔보세요.'
            : '입력한 정보는 계산할 때 희망환급 서버에 저장됩니다.'}
        </p>
        <div className="form-grid">
          {step === 0 && (
            <>
              <p className="full muted">
                실명은 수집하지 않습니다. 아이디·생년월일·이메일은 조회 전용입니다. 가입 성별 변경은
                마이페이지에서 진행해주세요.
              </p>
              <Field
                label="귀속 연도"
                hint="기본 연도는 서비스 정책에 따라 1~3월 전년도, 4~12월 해당 연도로 선택합니다."
              >
                <input
                  type="number"
                  required
                  min="1900"
                  max="9999"
                  value={d.taxYear}
                  onChange={(e) => {
                    const year = Number(e.target.value);
                    setD({ ...d, taxYear: year });
                    onTaxYearChange?.(year);
                  }}
                />
              </Field>
              {f('birth', '생년월일', 'date')}
              <Field label="성별">
                <select
                  disabled
                  value={d.gender}
                  onChange={(e) => setD({ ...d, gender: e.target.value })}
                >
                  <option value="">선택해주세요</option>
                  <option>여성</option>
                  <option>남성</option>
                </select>
              </Field>
              {f('email', '이메일', 'email')}
            </>
          )}
          {step === 1 && (
            <>
              {f('salary', '연간 총급여', 'number', '비과세 소득을 제외한 연간 급여')}
              {f(
                'paid',
                '연간 기납부 소득세',
                'number',
                '지방소득세·4대 보험료가 아니라 급여에서 미리 납부한 소득세입니다.',
              )}
              {d.legacyInsuranceNeedsReview && (
                <p className="notice amber full">
                  기존 사회보험료 합계는 임의로 나누지 않았습니다. 두 항목을 확인해 다시
                  입력해주세요.
                </p>
              )}
              {f(
                'nationalPension',
                '국민연금 연간 납부액',
                'number',
                '급여명세서 등에 표시된 국민연금 본인 납부액입니다.',
              )}
              {f(
                'healthEmploymentInsurance',
                '건강보험료·고용보험료 연간 납부액',
                'number',
                '본인 납부액을 국민연금과 구분하여 입력합니다.',
              )}
            </>
          )}
          {step === 2 && (
            <>
              <div className="review full">
                <h3>{d.taxYear}년 업로드 거래 집계</h3>
                <div>
                  신용카드 <b>{d.credit.toLocaleString()}원</b>
                </div>
                <div>
                  체크카드 <b>{d.debit.toLocaleString()}원</b>
                </div>
                <div>
                  현금영수증 <b>{d.cash.toLocaleString()}원</b>
                </div>
                {spendTotals ? (
                  <p>서버에 저장된 카드 사용 내역 합계예요. 기존 계산 결과와 같은 값이에요.</p>
                ) : spendTotalsMissing ? (
                  <p>
                    서버 카드 사용 내역을 불러오지 못해 이 브라우저의 거래로 보여 줘요. 계산할 때
                    서버 합계를 다시 불러와요.
                  </p>
                ) : (
                  <p>
                    다른 연도 거래{' '}
                    {tx.filter((row) => Number(row.date.slice(0, 4)) !== d.taxYear).length}건은
                    보존하며 이번 계산에서만 제외합니다.
                  </p>
                )}
              </div>
              <p className="full muted">
                {mode === 'simulation'
                  ? '실제 지출은 유지됩니다. 아래에 소비 유형·결제수단별 가상 추가 금액을 입력하세요.'
                  : '실제 사용금액은 CSV·XLSX 업로드로만 입력합니다. 선택 연도의 집계 포함 거래를 자동 합산합니다.'}
              </p>
              {mode === 'simulation' ? (
                <div className="inline-note full">
                  <h3>소비 유형·결제수단별 가상 추가 사용액</h3>
                  <p>
                    업로드 없이 원본 지출에 가상 금액을 추가합니다. 원본 거래는 변경되지 않습니다.
                    계산 서버에 연결되면 기존 계산 결과와 서버 계산으로 비교해요.
                  </p>
                  {(d.simulationSpend || []).map((row, i) => (
                    <div className="form-grid" key={i}>
                      <select
                        aria-label={`가상 소비 유형 ${i + 1}`}
                        value={row.category}
                        onChange={(e) =>
                          setD({
                            ...d,
                            simulationSpend: d.simulationSpend.map((r, j) =>
                              j === i ? { ...r, category: e.target.value } : r,
                            ),
                          })
                        }
                      >
                        {[
                          ['GENERAL', '일반'],
                          ['TRANSIT', '대중교통'],
                          ['MARKET', '전통시장'],
                          ['CULTURE', '문화체육'],
                        ].map(([v, l]) => (
                          <option key={v} value={v}>
                            {l}
                          </option>
                        ))}
                      </select>
                      <select
                        aria-label={`가상 결제수단 ${i + 1}`}
                        value={row.payment}
                        onChange={(e) =>
                          setD({
                            ...d,
                            simulationSpend: d.simulationSpend.map((r, j) =>
                              j === i ? { ...r, payment: e.target.value } : r,
                            ),
                          })
                        }
                      >
                        {[
                          ['CREDIT', '신용카드'],
                          ['CHECK', '체크카드'],
                          ['CASH', '현금영수증'],
                        ].map(([v, l]) => (
                          <option key={v} value={v}>
                            {l}
                          </option>
                        ))}
                      </select>
                      <input
                        aria-label={`가상 추가 금액 ${i + 1}`}
                        type="number"
                        min="0"
                        required
                        value={row.amount}
                        onChange={(e) =>
                          setD({
                            ...d,
                            simulationSpend: d.simulationSpend.map((r, j) =>
                              j === i ? { ...r, amount: Number(e.target.value) } : r,
                            ),
                          })
                        }
                      />
                      <button
                        type="button"
                        className="text-btn"
                        onClick={() =>
                          setD({
                            ...d,
                            simulationSpend: d.simulationSpend.filter((_, j) => j !== i),
                          })
                        }
                      >
                        가상 항목 삭제
                      </button>
                    </div>
                  ))}
                  <button
                    type="button"
                    className="btn secondary"
                    onClick={() =>
                      setD({
                        ...d,
                        simulationSpend: [
                          ...(d.simulationSpend || []),
                          { category: 'GENERAL', payment: 'CREDIT', amount: 0 },
                        ],
                      })
                    }
                  >
                    가상 소비 추가
                  </button>
                </div>
              ) : (
                <ExpenseUpload
                  rows={tx}
                  meta={uploadMeta}
                  onChange={(rows, meta) => {
                    setErr('');
                    setTx(rows);
                    setUploadMeta(meta);
                    setD((current) => ({ ...current, ...uploadTotals(rows, current.taxYear) }));
                  }}
                />
              )}
            </>
          )}
          {step === 3 && (
            <>
              <Field label="추가 부양가족 수 (본인 제외)" hint="소득·연령·중복 공제 요건 별도 확인">
                <input
                  required
                  type="number"
                  min="0"
                  max="20"
                  step="1"
                  value={d.family}
                  onChange={(e) => setD({ ...d, family: Number(e.target.value) })}
                />
              </Field>
              <Field label="20세 이하 자녀 수" hint="추가 부양가족 중 해당 자녀 수를 입력해주세요.">
                <input
                  required
                  type="number"
                  min="0"
                  max="20"
                  step="1"
                  value={d.children ?? 0}
                  onChange={(e) => setD({ ...d, children: Number(e.target.value) })}
                />
              </Field>
              {familyError && (
                <p className="error full" role="alert">
                  {familyError}
                </p>
              )}
              <div className="inline-note">
                <Users />
                <b>기본공제 요건을 확인해주세요</b>
                <p>
                  실제 공제 가능 여부는 가족별 연령·소득·중복 공제를 확인해야 합니다. 인적공제는
                  입력한 인원으로 서버가 계산합니다.
                </p>
              </div>
            </>
          )}
          {step === 4 && (
            <>
              <Field label="주택 보유 상태">
                <select value={d.housing} onChange={(e) => setD({ ...d, housing: e.target.value })}>
                  <option>무주택</option>
                  <option>주택 보유</option>
                  <option>확인 필요</option>
                </select>
              </Field>
              <Field label="무주택 세대주 여부">
                <select
                  required
                  value={d.headOfHousehold}
                  onChange={(e) => setD({ ...d, headOfHousehold: e.target.value })}
                >
                  <option value="확인 필요">확인 필요</option>
                  <option>예</option>
                  <option>아니요</option>
                </select>
                <small>무주택이면서 세대주인 경우 예를 선택해주세요.</small>
              </Field>
              {f(
                'rent',
                '월세 월 납부액 (원)',
                'number',
                '한 달에 납부하는 월세입니다. 계산할 때 월 납부액 × 12개월로 환산합니다. 실제 납부 기간·계약·증빙은 본인 확인이 필요합니다.',
              )}
              {d.legacyRentNeedsReview && (
                <p className="notice amber full">
                  과거 연간 월세 {d.legacyAnnualRent?.toLocaleString()}원은 보존했습니다. 월
                  납부액을 확인하여 다시 입력해주세요.
                </p>
              )}
              {f('housingSaving', '주택청약 연간 납입액')}
              <p>연간 환산 월세: {(d.rent * 12).toLocaleString()}원</p>
            </>
          )}
          {step === 5 && (
            <>
              <Field label="중소기업 취업자 감면 신청 여부">
                <select
                  value={{ 해당: '예', 미해당: '아니요' }[d.sme] || d.sme}
                  onChange={(e) => setD({ ...d, sme: e.target.value })}
                >
                  <option>확인 필요</option>
                  <option value="예">예</option>
                  <option value="아니요">아니요</option>
                </select>
              </Field>
              {f('pensionSaving', '연금저축 연간 납입액')}
              {f('irp', 'IRP 연간 납입액')}
              {f('employed', '입사일', 'date')}
              <Field label="군 복무 개월 수">
                <input
                  required
                  type="number"
                  min="0"
                  max="120"
                  step="1"
                  disabled={mode === 'simulation'}
                  value={d.militaryMonths ?? 0}
                  onChange={(e) => setD({ ...d, militaryMonths: Number(e.target.value) })}
                />
                <small>복무 이력이 없으면 0개월을 입력해주세요.</small>
              </Field>
              <Field label="회사 규모">
                <select
                  disabled={mode === 'simulation'}
                  required
                  value={d.companySize}
                  onChange={(e) => setD({ ...d, companySize: e.target.value })}
                >
                  <option value="확인 필요">확인 필요</option>
                  <option>중소기업</option>
                  <option>중견기업</option>
                  <option>대기업</option>
                </select>
              </Field>
              <div className="notice amber full">
                <Info size={16} />
                입사일·병역·기업 규모·무주택 세대주 정보는 자격 검토용으로 구분합니다.
                입사일·병역·기업 규모는 시나리오 저장 컬럼이 없어 시뮬레이션에서 변경할 수 없습니다.
                법정 자격 판정·계산은 아직 연결되지 않아 이 입력만으로 공제·감면을 확정하지
                않습니다.
              </div>
              <div className="review full">
                {mode === 'simulation' && (
                  <Field label="시나리오 이름">
                    {/* [추가 11/27 · TT-192] 16자까지만 입력 (PREDICTION.SCENARIO_NAME) · 넘으면 계산 때 알림만 잠깐 떠서 놓치기 쉬웠음 */}
                    <input
                      maxLength={SCENARIO_NAME_MAX}
                      value={d.scenarioName || ''}
                      onChange={(e) => setD({ ...d, scenarioName: e.target.value })}
                      placeholder="예: 연금 추가 납입"
                    />
                    <small>
                      {SCENARIO_NAME_MAX}자까지 입력할 수 있어요. ({(d.scenarioName || '').length}/
                      {SCENARIO_NAME_MAX})
                    </small>
                  </Field>
                )}
                <p>보험료·청약·연금은 서버 계산에서 공제율·한도를 적용해 반영됩니다.</p>
                <h3>계산 전 마지막 확인</h3>
                <div>
                  총급여 <b>{d.salary.toLocaleString()}원</b>
                </div>
                <div>
                  기납부세액 <b>{d.paid.toLocaleString()}원</b>
                </div>
                <div>
                  카드·현금 사용 합계 <b>{(d.credit + d.debit + d.cash).toLocaleString()}원</b>
                </div>
                <div>
                  추가 가족 / 20세 이하 자녀{' '}
                  <b>
                    {d.family}명 / {d.children ?? 0}명
                  </b>
                </div>
                <label className="checkline">
                  <input
                    type="checkbox"
                    required
                    checked={d.consent}
                    onChange={(e) => setD({ ...d, consent: e.target.checked })}
                  />
                  입력값 기준 예상 결과이며 실제 신고 결과와 다를 수 있음을 확인했습니다.
                </label>
              </div>
            </>
          )}
        </div>
        {err && (
          <p className="error" role="alert">
            {err}
          </p>
        )}
        <div className="form-bottom">
          <button
            className="btn secondary"
            type="button"
            onClick={() => (step ? move(step - 1) : onCancel())}
          >
            <ChevronLeft size={16} />
            {step ? '이전 단계' : '취소'}
          </button>
          <span>입력 정보는 이 브라우저에서 보관됩니다</span>
          <button className="btn" type="submit">
            {step === 5
              ? mode === 'simulation'
                ? '변경 조건으로 계산하기'
                : '정보 저장하고 계산하기'
              : '다음 단계'}
            <ArrowRight size={16} />
          </button>
        </div>
      </form>
      <div className="privacy">
        <ShieldCheck size={16} />
        주민등록번호와 카드번호 전체는 수집하지 않습니다.
      </div>
    </>
  );
}
