import React, { useEffect, useState } from 'react';
import { Plus, Search, Send, Mail } from 'lucide-react';
// [수정 11/20] 브라우저 체험 데이터(prototype-records.js의 inquiryService · inquiryLabels) → 서버 API(inquiry-service.js)
//   이유: F-17 문의 작성 · 내 문의 조회를 실제 서버(POST · GET /api/inquiries)에 연결
import {
  INQUIRY_STATUS_LABEL,
  INQUIRY_TYPE_LABEL,
  TITLE_MAX,
  answerText,
  createInquiry,
  fetchInquiries,
  fetchInquiry,
  formatDateTime,
} from '@/api/inquiry-service.js';
import { Btn, Tag, Field } from '@/components/common/site-primitives.jsx';

// FAQ·문의 작성·내 문의 목록을 보여줍니다.
// [수정 11/20] inquiries · setInquiries 를 받지 않음 · 이유: 문의 목록을 이 화면이 서버에서 직접 불러옴
//   ([수정 11/23] App도 더 이상 넘겨주지 않음 · 체험판 삭제 때 App의 inquiries 상태를 지움)
export default function SupportPage({
  search,
  setSearch,
  faq,
  setFaq,
  supportTab,
  setSupportTab,
  notify,
}) {
  // [추가 11/20] 서버에서 받은 내 문의 목록 · 불러오는 중 · 불러오기 실패 문구 · 보내는 중
  // useState: 화면이 기억할 값 (값이 바뀌면 화면을 다시 그림)
  const [rows, setRows] = useState([]);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState('');
  const [sending, setSending] = useState(false);
  // 펼쳐 본 문의의 최신 상세 (문의 번호 → 서버 응답) · 펼칠 때 GET /api/inquiries/{id}로 다시 받아 와요
  const [details, setDetails] = useState({});

  // [추가 11/20] 「문의 내역」 탭을 열 때마다 서버에서 목록을 다시 불러와요
  // useEffect: 화면을 그린 뒤 실행 · [supportTab] 값이 바뀔 때마다 다시 실행
  // ignore: 응답이 오기 전에 다른 탭으로 바꾸면 늦게 온 응답을 버림
  useEffect(() => {
    if (supportTab !== '문의 내역') return undefined;
    let ignore = false;
    setLoading(true);
    setLoadError('');
    setDetails({});
    fetchInquiries()
      .then((list) => {
        if (!ignore) setRows(list);
      })
      .catch((err) => {
        // 로그인 안 함(401) · 서버 꺼짐(OFFLINE) 등 → 서버가 준 문구를 그대로 보여 줘요
        if (!ignore) {
          setRows([]);
          setLoadError(err.message);
        }
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [supportTab]);

  // [추가 11/20] 문의 작성 → 서버에 저장 → 「문의 내역」 탭으로 이동 (탭이 바뀌면 위 useEffect가 목록을 다시 불러옴)
  async function submitInquiry(e) {
    e.preventDefault();
    const f = new FormData(e.currentTarget);
    setSending(true);
    try {
      await createInquiry({
        inquiryType: f.get('inquiryType'),
        title: f.get('title'),
        content: f.get('content'),
      });
      setSupportTab('문의 내역');
      notify('문의가 접수되었어요.');
    } catch (err) {
      notify(err.message);
    } finally {
      setSending(false);
    }
  }

  // [추가 11/20] 문의를 펼칠 때 최신 상태를 서버에서 다시 받아 와요 (그 사이 관리자가 답변했을 수 있어서)
  // 접을 때나 이미 받아 온 문의는 다시 부르지 않아요
  async function loadDetail(e, inquiryId) {
    if (!e.currentTarget.open || details[inquiryId]) return;
    try {
      const one = await fetchInquiry(inquiryId);
      setDetails((prev) => ({ ...prev, [inquiryId]: one }));
    } catch (err) {
      notify(err.message);
    }
  }

  return (
    <>
      <div className="heading">
        <div>
          <div className="eyebrow">WE ARE HERE TO HELP</div>
          <h1>무엇을 도와드릴까요?</h1>
          <p>자주 묻는 질문부터 나의 문의까지, 편하게 확인하세요.</p>
        </div>
        {/* [수정 11/20] 문의 작성 탭에서는 버튼을 숨김 · 이유: 이미 작성 화면이라 누를 필요가 없음 */}
        {supportTab !== '문의 작성' && (
          <Btn onClick={() => setSupportTab('문의 작성')}>
            <Plus size={16} />
            문의하기
          </Btn>
        )}
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
      <section className="card">
        {supportTab === '자주 묻는 질문' ? (
          <>
            <div className="search wide">
              <Search size={18} />
              <input
                placeholder="궁금한 내용을 검색해보세요"
                value={search}
                onChange={(e) => setSearch(e.target.value)}
              />
            </div>
            {/* [수정 11/23] FAQ 답변의 체험용 설명(체험용 계산 · 예시 상호명 목록 · 예시 AI 응답 · 브라우저 저장)을
                지금 서비스에 맞게 고침 · 이유: 체험판 기능 삭제 결정 */}
            {[
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
                'AI가 직접 세금을 계산하나요?',
                '아니요. 세액은 계산 서버가 세법 기준값으로 계산합니다. 화면 오른쪽 아래 FAQ 챗봇은 세금 용어를 설명해 드려요.',
              ],
              [
                '입력한 정보는 어디에 저장되나요?',
                '소득 정보 · 카드 내역 · 계산 결과 · 문의는 희망환급 서버(DB)에 저장됩니다. 업로드한 카드 내역은 파일별로 삭제할 수 있습니다.',
              ],
            ]
              .filter(([q, a]) => (q + a).includes(search))
              .map(([q, a], i) => (
                <div className="faq" key={q}>
                  <button onClick={() => setFaq(faq === i ? null : i)}>
                    <span>Q</span>
                    {q}
                    <Plus size={17} />
                  </button>
                  {faq === i && <p>{a}</p>}
                </div>
              ))}
          </>
        ) : supportTab === '문의 작성' ? (
          // [수정 11/20] 브라우저 저장(inquiryService.create) → 서버 저장(submitInquiry)
          <form onSubmit={submitInquiry}>
            <h3>문의 작성</h3>
            <Field label="문의 유형">
              {/* [수정 11/20] name: category → inquiryType · 이유: 서버 이름(inquiryType)과 맞춤 */}
              <select name="inquiryType" required>
                <option value="">유형을 선택해주세요</option>
                <option value="ERROR">오류</option>
                <option value="QUESTION">문의</option>
                <option value="ETC">기타</option>
              </select>
            </Field>
            <Field label="제목">
              <input
                name="title"
                maxLength={TITLE_MAX}
                required
                placeholder="문의 제목을 입력해주세요"
              />
            </Field>
            <Field label="문의 내용">
              {/* [수정 11/20] name: text → content · 이유: 서버 이름(content)과 맞춤 */}
              <textarea
                rows={6}
                name="content"
                required
                placeholder="주민등록번호, 카드번호 등 민감한 정보는 적지 마세요."
              />
            </Field>
            {/* [수정 11/20] 「(시연)」 삭제 · 보내는 동안 버튼을 막아 두 번 저장되지 않게 */}
            <Btn type="submit" disabled={sending}>
              {sending ? '접수 중…' : '문의 접수'} <Send size={15} />
            </Btn>
          </form>
        ) : loading ? (
          // [추가 11/20] 서버에서 불러오는 중
          <div className="empty">
            <p>문의 내역을 불러오는 중이에요.</p>
          </div>
        ) : loadError ? (
          // [추가 11/20] 불러오기 실패 (예: 로그인이 필요해요 · 서버에 연결할 수 없어요)
          <div className="empty">
            <Mail size={32} />
            <p>{loadError}</p>
          </div>
        ) : rows.length ? (
          // [수정 11/20] 브라우저 데이터 이름 → 서버 응답 이름
          //   id → inquiryId · category → inquiryType · text → content · date → createdAt
          rows.map((row) => {
            const q = details[row.inquiryId] || row; // 펼쳐서 받아 온 최신 상세가 있으면 그것을 씀
            return (
              <details
                className="inquiry"
                key={row.inquiryId}
                onToggle={(e) => loadDetail(e, row.inquiryId)}
              >
                <summary>
                  <Tag tone={q.status === 'ANSWERED' ? 'green' : 'amber'}>
                    {INQUIRY_STATUS_LABEL[q.status] || '접수'}
                  </Tag>
                  <span className="tag gray">{INQUIRY_TYPE_LABEL[q.inquiryType] || '문의'}</span>
                  <b>{q.title}</b>
                  <small>{formatDateTime(q.createdAt)}</small>
                </summary>
                <p>{q.content}</p>
                {q.answeredAt && <small>답변 시각: {formatDateTime(q.answeredAt)}</small>}
                <div className="review">
                  <b>답변</b>
                  {/* 확인 중이면 고정 안내 「확인 후 답변 드리겠습니다.」 (D-22) */}
                  <p>{answerText(q)}</p>
                </div>
              </details>
            );
          })
        ) : (
          <div className="empty">
            <Mail size={32} />
            <p>접수된 문의가 없어요.</p>
            <Btn secondary onClick={() => setSupportTab('문의 작성')}>
              문의 작성하기
            </Btn>
          </div>
        )}
      </section>
    </>
  );
}
