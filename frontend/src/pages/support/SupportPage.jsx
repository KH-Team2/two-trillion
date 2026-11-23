import React from 'react';
import { Plus, Search } from 'lucide-react';
import ComingSoon from '@/components/common/ComingSoon.jsx';

// 자주 묻는 질문 · 문의 화면입니다.
// [수정 11/23] 브라우저에만 저장하던 문의 작성 · 내 문의 목록(inquiryService) 삭제 → 「준비 중」 안내
//   이유: 체험판 기능 삭제 결정 · 문의 API(F-07-01)가 develop에 들어오면 두 탭에 실제 화면을 연결해요
//   FAQ 답변도 체험용 설명(체험용 계산 · 예시 상호명 목록 · 예시 AI 응답)을 지금 서비스에 맞게 고쳤어요
const FAQS = [
  [
    '예상 환급액은 실제 금액과 같은가요?',
    '입력한 값을 기준으로 서버가 계산한 예상 금액입니다. 실제 신고 결과와 다를 수 있으며, 실제 신고 전에는 증빙과 국세청 기준을 확인해 주세요.',
  ],
  [
    '시뮬레이션을 하면 원래 정보가 바뀌나요?',
    '아니요. 저장된 정보의 복사본으로 조건을 바꿔 계산합니다. 원본은 그대로이며, 시뮬레이션 결과는 시나리오로 따로 저장됩니다.',
  ],
  [
    '카드 내역은 어떻게 분류하나요?',
    '공공데이터 상호명 목록과 가맹점명을 대조해 일반 · 대중교통 · 전통시장 · 문화체육 · 공제 대상 아님으로 분류합니다. 목록에 없는 가맹점은 일반으로 분류됩니다. 머신러닝은 사용하지 않습니다.',
  ],
  [
    '입력한 정보는 어디에 저장되나요?',
    '소득 정보 · 카드 내역 · 계산 결과는 희망환급 서버(DB)에 저장됩니다. 업로드한 카드 내역은 파일별로 삭제할 수 있습니다.',
  ],
];
export default function SupportPage({ search, setSearch, faq, setFaq, supportTab, setSupportTab }) {
  return (
    <>
      <div className="heading">
        <div>
          <div className="eyebrow">WE ARE HERE TO HELP</div>
          <h1>무엇을 도와드릴까요?</h1>
          <p>자주 묻는 질문부터 나의 문의까지, 편하게 확인하세요.</p>
        </div>
      </div>
      <div className="tabs">
        {['자주 묻는 질문', '문의 내역', '문의 작성'].map((t) => (
          <button
            key={t}
            className={supportTab === t ? 'active' : ''}
            onClick={() => setSupportTab(t)}
          >
            {t}
          </button>
        ))}
      </div>
      {supportTab === '자주 묻는 질문' ? (
        <section className="card">
          <div className="search wide">
            <Search size={18} />
            <input
              placeholder="궁금한 내용을 검색해보세요"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
            />
          </div>
          {FAQS.filter(([q, a]) => (q + a).includes(search)).map(([q, a], i) => (
            <div className="faq" key={q}>
              <button onClick={() => setFaq(faq === i ? null : i)}>
                <span>Q</span>
                {q}
                <Plus size={17} />
              </button>
              {faq === i && <p>{a}</p>}
            </div>
          ))}
        </section>
      ) : (
        <ComingSoon title={supportTab}>
          문의 작성 · 내 문의 확인은 서버 연결 작업 중이에요.
        </ComingSoon>
      )}
    </>
  );
}
