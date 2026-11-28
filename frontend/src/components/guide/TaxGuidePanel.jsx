import React, { useEffect, useState } from 'react';
import { Sparkles, ChevronDown, House, PiggyBank, Wallet, Info } from 'lucide-react';
import { fetchTaxGuides } from '@/api/guide-service.js';

// 추천 조건은 Spring Boot가 판단하며,
// 프론트엔드는 해당 조건을 설명하는 문구만 표시
const GUIDE_INFO = {
  HOUSING_SAVINGS: {
    icon: House,
    type: '소득공제',
    reason:
      '총급여가 7,000만 원 이하이고 무주택 세대주에 해당하며, 주택청약 납입액이 0원으로 입력되어 안내합니다.',
  },
  PENSION_SAVINGS: {
    icon: PiggyBank,
    type: '세액공제',
    reason:
      '연금저축 납입액이 0원으로 입력되어, 노후 준비와 세액공제 혜택을 검토할 항목으로 안내합니다.',
  },
  IRP: {
    icon: Wallet,
    type: '세액공제',
    reason:
      'IRP 납입액이 0원으로 입력되어, 노후 준비와 세액공제 혜택을 검토할 항목으로 안내합니다.',
  },
};

export default function TaxGuidePanel({ predictionId }) {
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState('');
  const [opened, setOpened] = useState(null);
  const [retry, setRetry] = useState(0);

  const validId = Number.isInteger(predictionId) && predictionId > 0;

  useEffect(() => {
    if (!validId) {
      return;
    }

    let active = true;

    setLoading(true);
    setItems([]);
    setError('');
    setOpened(null);

    fetchTaxGuides(predictionId)
      .then((result) => {
        if (!active) return;

        setItems(result);
        setOpened(result[0]?.code ?? null);
      })
      .catch((err) => {
        if (!active) return;
        setError(err.message);
      })
      .finally(() => {
        if (active) setLoading(false);
      });

    return () => {
      active = false;
    };
  }, [predictionId, validId, retry]);

  // 저장된 실제 계산 결과가 없으면 표시하지 않음
  if (!validId) return null;

  // 추천이 0개이고 오류도 없다면 오른쪽 패널을 숨김
  if (!loading && !error && items.length === 0) {
    return null;
  }

  return (
    <section className="card tax-guide">
      {/* 기존 챗봇의 제목 스타일 재사용 */}
      <div className="chat-title">
        <span className="iconbox">
          <Sparkles size={20} />
        </span>

        <div className="tax-guide-title">
          <h3>맞춤 절세 가이드</h3>
          <p>입력하신 정보를 바탕으로 확인할 항목을 안내합니다.</p>
        </div>

        {!loading && items.length > 0 && <span className="tag">추천 {items.length}개</span>}
      </div>

      {loading && (
        <p className="tax-guide-status" role="status">
          맞춤 절세 가이드를 불러오는 중입니다...
        </p>
      )}

      {error && (
        <div className="tax-guide-status" role="alert">
          <p>{error}</p>
          <button type="button" onClick={() => setRetry((value) => value + 1)}>
            다시 시도
          </button>
        </div>
      )}

      {!loading && !error && (
        <>
          <div className="tax-guide-list">
            {items.map((item) => {
              const info = GUIDE_INFO[item.code];
              const Icon = info?.icon ?? Sparkles;
              const isOpen = opened === item.code;

              return (
                <article className="tax-guide-item" key={item.code}>
                  <button
                    type="button"
                    className="tax-guide-toggle"
                    aria-expanded={isOpen}
                    onClick={() => setOpened(isOpen ? null : item.code)}
                  >
                    <span className="iconbox">
                      <Icon size={18} />
                    </span>

                    <span className="tax-guide-name">
                      <strong>{item.name}</strong>
                      <small className="tag">{info?.type}</small>
                    </span>

                    <ChevronDown size={16} className={isOpen ? 'is-open' : ''} />
                  </button>

                  {isOpen && (
                    <div className="tax-guide-body">
                      <div>
                        <h4>왜 추천하나요?</h4>
                        <p>{info?.reason}</p>
                      </div>

                      <div>
                        <h4>어떤 상품인가요?</h4>
                        <p>{item.content?.description}</p>
                      </div>

                      <div>
                        <h4>연말정산에서는 어떤 혜택이 있나요?</h4>
                        <p>{item.content?.taxBenefit}</p>
                      </div>

                      <div>
                        <h4>무엇을 확인하면 좋을까요?</h4>
                        <p>{item.content?.nextStep}</p>
                      </div>
                    </div>
                  )}
                </article>
              );
            })}
          </div>

          <p className="tax-guide-notice">
            <Info size={14} />
            실제 공제 여부와 절세 효과는 귀속 연도, 개인별 요건 및 납입 내역에 따라 달라질 수
            있습니다.
          </p>
        </>
      )}
    </section>
  );
}
