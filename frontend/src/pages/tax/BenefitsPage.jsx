import { benefitChecks } from '@/utils/benefit-model.js';
import { benefitStatus, fetchBenefits, PREPARE_DOCS } from '@/api/benefit-service.js';
import { getTaxYear } from '@/utils/tax-year.js';
import React, { useEffect, useState } from 'react';
import { ArrowRight, FilePenLine, Info, CheckCircle2, Building2, Users } from 'lucide-react';
import { Btn, Tag } from '@/components/common/site-primitives.jsx';

// 청년 혜택 자격 진단 화면 (S-05 · F-03-02)
// 서버(GET /api/benefits/{taxYear})가 판정한 결과를 그대로 보여 줘요.
// 서버에 연결할 수 없을 때만 입력값 기준 체험 판정(benefitChecks)을 보여 줘요.
export default function BenefitsPage({ data, normalInput, loadBenefits = fetchBenefits }) {
  const taxYear = getTaxYear(data);
  const [state, setState] = useState({ status: 'loading' });

  useEffect(() => {
    let alive = true; // 화면을 떠난 뒤 늦게 온 응답은 버려요
    setState({ status: 'loading' });
    loadBenefits(taxYear)
      .then((result) => alive && setState({ status: 'ok', results: result.results }))
      .catch(
        (error) =>
          alive && setState({ status: error.code === 'OFFLINE' ? 'offline' : 'error', error }),
      );
    return () => {
      alive = false;
    };
  }, [taxYear, loadBenefits]);

  return (
    <>
      <div className="heading">
        <div>
          <div className="eyebrow">YOUNG & ELIGIBLE</div>
          <h1>청년의 가능성, 혜택으로 이어지도록</h1>
          <p>{taxYear}년 귀속 기준으로 나에게 맞는 혜택과 부족한 조건을 살펴보세요.</p>
        </div>
        <Btn secondary onClick={normalInput}>
          자격 정보 수정 <FilePenLine size={16} />
        </Btn>
      </div>
      <div className="benefit-hero">
        <div>
          <Tag tone="green">청년 특화 혜택</Tag>
          <h2>중소기업 취업자 소득세 감면</h2>
          <p>
            취업 당시 나이(군 복무 기간 제외)·회사 규모·취업 후 기간을 함께 확인해요.
            <br />
            입사일이나 회사 규모가 비어 있으면 판정할 수 없어요.
          </p>
        </div>
        <Building2 size={100} strokeWidth={1} />
      </div>
      <section className="card">
        <div className="card-heading">
          <h3>나의 자격 정보</h3>
          <Tag tone="amber">증빙 확인 필요</Tag>
        </div>
        {state.status === 'ok' && (
          <p className="muted">
            아래 값은 이 브라우저에 입력한 값이에요. 혜택 판정은 서버에 저장된 회원 정보로 해요.
          </p>
        )}
        {[
          ['생년월일', '취업 당시 나이 계산에 써요', data.birth || '입력 필요'],
          ['입사일', '취업 당시 나이 · 감면 기간(5년) 계산', data.employed || '입력 필요'],
          [
            '군 복무 개월 수',
            '최대 6년까지 나이에서 빼요',
            String(data.militaryMonths ?? 0) + '개월',
          ],
          ['회사 규모', '중소기업 재직자만 감면 대상', data.companySize || '입력 필요'],
        ].map(([t, d, v]) => (
          <div className="benefit-row" key={t}>
            <span className="iconbox">
              <CheckCircle2 size={19} />
            </span>
            <div>
              <h3>{t}</h3>
              <p>{d}</p>
            </div>
            <span>{v}</span>
          </div>
        ))}
        <div className="notice">
          <Info size={16} />
          {state.status === 'ok'
            ? '서버가 저장된 자격 정보로 판정했어요. 실제 적용은 증빙 서류를 내고 회사(원천징수의무자)가 확인해요.'
            : state.status === 'loading'
              ? '혜택 진단 결과를 불러오는 중이에요.'
              : state.status === 'error'
                ? state.error.message
                : "자격판정 서버에 연결할 수 없어 입력값 기준으로만 보여 줘요. '해당'은 자가 선택이며 실제 자격 승인을 의미하지 않습니다."}
        </div>
        {state.status === 'error' && state.error.code === 'NOT_FOUND' && (
          <Btn secondary onClick={normalInput}>
            소득 정보 입력하기 <ArrowRight size={14} />
          </Btn>
        )}
      </section>
      <div className="two-col">
        {state.status === 'ok' &&
          state.results.map((item) => {
            const status = benefitStatus(item.eligible);
            const docs = PREPARE_DOCS[item.benefitCode];
            return (
              <section className="card" key={item.benefitCode}>
                <h3>{item.benefitName}</h3>
                <Tag tone={status.tone}>{status.label}</Tag>
                {item.reasons.length > 0 ? (
                  item.reasons.map((reason) => <p key={reason}>{reason}</p>)
                ) : (
                  <p>입력한 정보로는 조건을 모두 충족해요.</p>
                )}
                {item.sourceNote && <p className="muted">근거: {item.sourceNote}</p>}
                {docs && item.eligible !== false && (
                  <div className="muted">
                    <p>준비 서류 ({docs.note})</p>
                    <ul>
                      {docs.items.map((doc) => (
                        <li key={doc}>{doc}</li>
                      ))}
                    </ul>
                  </div>
                )}
              </section>
            );
          })}
        {state.status === 'offline' &&
          benefitChecks(data).map((item) => (
            <section className="card" key={item.name}>
              <h3>{item.name}</h3>
              <Tag tone={item.status === '비해당' ? 'gray' : 'amber'}>
                {item.status} · 입력 기준
              </Tag>
              <p>{item.reason}</p>
              <p className="muted">필요한 정보·조건: {item.requirements}</p>
              <p className="muted">자격 진단 서버 미연결. 최종 혜택 판정이 아닙니다.</p>
            </section>
          ))}
        <section className="card">
          <Users className="blue" />
          <h3>부양가족 기본공제</h3>
          <p className="muted">
            추가 입력 가족 {data.family}명 · 소득·연령·중복공제 요건을 확인하세요.
          </p>
          <Btn secondary onClick={normalInput}>
            내 정보 확인하기 <ArrowRight size={14} />
          </Btn>
        </section>
      </div>
    </>
  );
}
