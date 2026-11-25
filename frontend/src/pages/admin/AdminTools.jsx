// 관리자 도구 모음입니다. 권한은 서버(/api/admin/** · ROLE=ADMIN)가 검사해요.
// [수정 11/23] 브라우저 시연 도구(CriteriaManagement · AccessLog · Predictions) 삭제 · 이유: 체험판 기능 삭제 결정
//   (prototype-records.js의 브라우저 저장 데이터를 쓰던 가짜 기능) → 관리자 화면에서 「준비 중」 안내
//   서버 API로 연결된 문의 관리(InquiryManagement · F-18)만 남김
import React, { useEffect, useState } from 'react';
import {
  CHECKING_MESSAGE,
  INQUIRY_STATUS_LABEL,
  INQUIRY_TYPE_LABEL,
  fetchAdminInquiries,
  formatDateTime,
  updateInquiryAnswer,
} from '@/api/inquiry-service.js';
// [수정 11/20] F-18 문의 관리: 브라우저 체험 데이터(inquiryService) → 서버 API
//   GET /api/admin/inquiries?status= (전체 목록) · PUT /api/admin/inquiries/{id}/answer ([확인 중] · 답변 등록)
//   이름도 서버 응답에 맞춤: id → inquiryId · category → inquiryType · text → content
export function InquiryManagement() {
  const [rows, setRows] = useState([]),
    [type, setType] = useState(''),
    [status, setStatus] = useState(''),
    [err, setErr] = useState(''),
    // [추가 11/20] 지금 서버에 보내는 중인 문의 번호 (그 문의의 버튼을 잠깐 막아 두 번 저장되지 않게)
    [busyId, setBusyId] = useState(null),
    // [추가 11/20] 숫자를 1 올리면 아래 useEffect가 목록을 다시 불러와요 (저장 뒤 새로 고침용)
    [reload, setReload] = useState(0);

  // [추가 11/20] 상태 필터를 바꾸거나 저장한 뒤 서버에서 목록을 다시 불러와요
  //   상태 필터는 서버(?status=)에서 거르고, 유형 필터는 받아 온 목록을 화면에서 걸러요
  useEffect(() => {
    let ignore = false;
    fetchAdminInquiries(status)
      .then((list) => {
        if (!ignore) setRows(list);
      })
      .catch((e) => {
        // 관리자가 아니면 403 · 서버 꺼짐(OFFLINE) 등 → 서버 문구 그대로
        if (!ignore) {
          setRows([]);
          setErr(e.message);
        }
      });
    return () => {
      ignore = true;
    };
  }, [status, reload]);

  // [수정 11/20] 브라우저 저장(run(fn)) → 서버 저장 · 끝나면 목록을 다시 불러와요
  async function run(inquiryId, form, doneMessage) {
    setBusyId(inquiryId);
    setErr('');
    try {
      await updateInquiryAnswer(inquiryId, form);
      setErr(doneMessage);
      setReload((n) => n + 1);
    } catch (e) {
      setErr(e.message);
      // 다른 관리자가 먼저 답변을 끝낸 경우(409) → 최신 상태를 보여 주려고 다시 불러와요
      if (e.code === 'ALREADY_ANSWERED') setReload((n) => n + 1);
    } finally {
      setBusyId(null);
    }
  }

  const visible = rows.filter((q) => !type || q.inquiryType === type);
  return (
    <>
      <h3>문의 및 답변 관리</h3>
      <div className="form-grid">
        <select aria-label="문의 유형 필터" value={type} onChange={(e) => setType(e.target.value)}>
          <option value="">모든 유형</option>
          {Object.keys(INQUIRY_TYPE_LABEL).map((v) => (
            <option key={v} value={v}>
              {INQUIRY_TYPE_LABEL[v]}
            </option>
          ))}
        </select>
        <select
          aria-label="문의 상태 필터"
          value={status}
          onChange={(e) => setStatus(e.target.value)}
        >
          <option value="">모든 상태</option>
          {Object.keys(INQUIRY_STATUS_LABEL).map((v) => (
            <option key={v} value={v}>
              {INQUIRY_STATUS_LABEL[v]}
            </option>
          ))}
        </select>
      </div>
      {visible.map((q) => {
        const busy = busyId === q.inquiryId;
        return (
          <form
            className="admin-inquiry"
            key={q.inquiryId}
            onSubmit={(e) => {
              e.preventDefault();
              const f = new FormData(e.currentTarget);
              run(
                q.inquiryId,
                { status: 'ANSWERED', answer: f.get('answer') },
                '답변을 저장했어요.',
              );
            }}
          >
            <span className={'tag ' + (q.status === 'ANSWERED' ? 'green' : 'amber')}>
              {INQUIRY_STATUS_LABEL[q.status] || '접수'}
            </span>
            <span className="tag gray">{INQUIRY_TYPE_LABEL[q.inquiryType] || '문의'}</span>
            <h3>{q.title}</h3>
            {/* [추가 11/20] 누가 · 언제 쓴 문의인지 */}
            <p>
              회원 번호 {q.memberId} · 작성 {formatDateTime(q.createdAt)}
            </p>
            <p>{q.content}</p>
            {q.status === 'CHECKING' && <p>문의자 화면 안내: {CHECKING_MESSAGE}</p>}
            {q.status === 'ANSWERED' ? (
              // [수정 11/20] 답변 완료된 문의는 읽기만 · 이유: 서버가 다시 바꾸는 것을 409로 막음 (답변 보호)
              <>
                <p>답변: {q.answer}</p>
                <p>
                  답변 시각 {formatDateTime(q.answeredAt)} · 답변자 번호 {q.answererId}
                </p>
              </>
            ) : (
              <>
                {/* [수정 11/20] 접수(WAITING)일 때만 [확인 중] 버튼 · 이유: 이미 확인 중이면 누를 필요가 없음 */}
                {q.status === 'WAITING' && (
                  <button
                    type="button"
                    className="btn secondary"
                    disabled={busy}
                    onClick={() =>
                      run(q.inquiryId, { status: 'CHECKING' }, '확인 중으로 바꿨어요.')
                    }
                  >
                    확인 중
                  </button>
                )}
                <label className="field">
                  최종 답변
                  <textarea name="answer" required rows={3} />
                </label>
                <button className="btn" disabled={busy}>
                  최종 답변 저장
                </button>
              </>
            )}
          </form>
        );
      })}
      {!visible.length && <p className="empty">접수된 문의가 없습니다.</p>}
      <p role="status">{err}</p>
    </>
  );
}
