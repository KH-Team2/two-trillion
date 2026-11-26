// 관리자 도구 모음입니다. 권한은 서버(/api/admin/** · ROLE=ADMIN)가 검사해요.
// [수정 11/23] 브라우저 시연 도구(CriteriaManagement · AccessLog · Predictions) 삭제 · 이유: 체험판 기능 삭제 결정
//   (prototype-records.js의 브라우저 저장 데이터를 쓰던 가짜 기능) → 관리자 화면에서 「준비 중」 안내
//   서버 API로 연결된 문의 관리(InquiryManagement · F-18)만 남김
import React, { useEffect, useState } from 'react';
// [수정 11/20] inquiryService · inquiryLabels(브라우저 체험 데이터) 가져오기 삭제
//   이유: F-18 문의 관리를 서버 API(inquiry-service.js)로 바꿈 · 다른 탭(기준값 · 접근 기록 · 예측 결과)은 그대로
// [수정 11/23] accessRecords(브라우저 체험 접근 기록) 가져오기 삭제
//   이유: F-21 접근 기록 탭을 서버 API(access-log-service.js)로 바꿈 · 기준값 · 예측 결과 탭은 그대로
import {
  CHECKING_MESSAGE,
  INQUIRY_STATUS_LABEL,
  INQUIRY_TYPE_LABEL,
  fetchAdminInquiries,
  formatDateTime,
  updateInquiryAnswer,
} from '@/api/inquiry-service.js';
// [추가 11/25 고은] F-21 접근 기록 서버 API
//   이유: develop의 체험판 삭제(#64)와 병합하면서 접근 기록 탭(AccessLog)이 같이 지워져서 되살림
import { ACCESS_RESULT_LABEL, PAGE_SIZE, fetchAccessLogs } from '@/api/access-log-service.js';
// [추가 11/23 고은] F-20 회원 관리 서버 API
import {
  MEMBER_PAGE_SIZE,
  ROLE_LABEL,
  STATUS_LABEL,
  fetchMembers,
  updateMemberRole,
} from '@/api/admin-member-service.js';
// [추가 11/26 고은] F-08-02 ② 예측 결과 조회 서버 API
import {
  KIND_LABEL as PREDICTION_KIND_LABEL,
  PREDICTION_PAGE_SIZE,
  fetchPredictions,
  kindOf,
  refundText,
  wonText,
} from '@/api/admin-prediction-service.js';
// [추가 11/26 고은] F-19 세법 기준값 관리 서버 API
import {
  BRACKET_LABEL,
  CRITERIA_NOTICES,
  KIND_LABEL,
  PAY_LABEL,
  HISTORY_FIELD,
  REQUIRED_FIELDS,
  TARGET,
  TARGET_LABEL,
  copyTaxCriteriaYear,
  fetchCriteriaHistory,
  fetchTaxCriteria,
  formForRevert,
  formFromRow,
  formatAmount,
  idOf,
  missingRequired,
  saveTaxCriteria,
  toRequestBody,
} from '@/api/tax-criteria-service.js';
// [추가 11/26 고은] F-19 화면 전용 모양 (모달 · 작은 버튼 · 필수 표시) · CSS Module이라 다른 화면과 이름이 안 겹쳐요
import styles from './AdminCriteria.module.css';
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

// [수정 11/23] F-21 접근 기록: 브라우저 체험 데이터(accessRecords) → 서버 API (GET /api/admin/access-logs)
// [추가 11/25 고은] develop의 체험판 삭제(#64) 병합 때 함께 지워진 컴포넌트를 되살림 (서버 API 버전이라 체험판 아님)
//   기간 · IP로 검색 · 한 쪽에 20건 · 이전/다음 쪽 · 회원 연속 실패 횟수 · 잠금 시각 · IP별 실패 수 (기능명세서 F-08-02 ③)
export function AccessLog() {
  // 화면에 적는 중인 검색 조건(form) vs 실제로 검색한 조건(filters)
  //   → [검색]을 눌렀을 때만 filters를 바꿔서, 글자를 칠 때마다 서버를 부르지 않게
  const empty = { startDate: '', endDate: '', ipAddress: '' };
  const [form, setForm] = useState(empty),
    [filters, setFilters] = useState(empty),
    [page, setPage] = useState(1),
    [rows, setRows] = useState([]),
    [meta, setMeta] = useState(null),
    [err, setErr] = useState(''),
    [loading, setLoading] = useState(false);

  // 검색 조건이나 쪽 번호가 바뀔 때마다 서버에서 다시 불러와요
  // ignore: 응답이 오기 전에 다른 쪽을 누르면, 늦게 온 예전 응답은 버려요
  useEffect(() => {
    let ignore = false;
    setLoading(true);
    setErr('');
    fetchAccessLogs({ ...filters, page, size: PAGE_SIZE })
      .then((res) => {
        if (!ignore) {
          setRows(res.rows);
          setMeta(res.meta);
        }
      })
      .catch((e) => {
        // 관리자가 아니면 403 · 서버 꺼짐(OFFLINE) 등 → 서버 문구 그대로
        // 날짜 모양 · 시작일 > 종료일 등 화면 검사에 걸려도 서버에 보내지 않고 여기로 와요
        if (!ignore) {
          setRows([]);
          setMeta(null);
          setErr(e.message);
        }
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [filters, page]);

  const totalPages = Math.max(meta?.totalPages || 0, 1);
  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));
  return (
    <>
      <h3>주요 동작 접근 기록</h3>
      <p>
        로그인 · 관리자 조회 등 주요 동작을 누가 · 언제 · 어디서(IP) 했는지 보여 줘요. 이 화면을 연
        것도 기록돼요.
      </p>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault();
          setFilters({ ...form });
          setPage(1);
        }}
      >
        <label className="field">
          시작일
          <input type="date" value={form.startDate} onChange={set('startDate')} />
        </label>
        <label className="field">
          종료일
          <input type="date" value={form.endDate} onChange={set('endDate')} />
        </label>
        <label className="field">
          IP (일부만 써도 돼요)
          <input
            value={form.ipAddress}
            onChange={set('ipAddress')}
            maxLength={45}
            placeholder="예: 127.0.0"
          />
        </label>
        <button className="btn" disabled={loading}>
          검색
        </button>
      </form>
      <p>
        전체 {meta?.total ?? 0}건 · {page} / {totalPages}쪽
      </p>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              {[
                '번호',
                '회원 번호',
                '화면 · API',
                '시각',
                'IP',
                '결과',
                '연속 실패',
                '잠금 해제 시각',
                '같은 IP 실패',
              ].map((v) => (
                <th key={v}>{v}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <tr key={r.accessId}>
                <td>{r.accessId}</td>
                {/* 없는 아이디로 로그인에 실패한 기록은 회원 번호가 비어 있어요 */}
                <td>{r.memberId ?? '없음'}</td>
                <td>{r.screen}</td>
                <td>{r.accessedAt}</td>
                <td>{r.ipAddress || '-'}</td>
                <td>{ACCESS_RESULT_LABEL[r.result] || r.result}</td>
                <td>{r.failCount ?? '-'}</td>
                <td>{r.lockedUntil || '-'}</td>
                <td>{r.ipFailCount ?? 0}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!loading && !rows.length && !err && <p className="empty">기록이 없습니다.</p>}
      <div className="form-grid">
        <button
          type="button"
          className="btn secondary"
          disabled={loading || page <= 1}
          onClick={() => setPage((n) => n - 1)}
        >
          이전 쪽
        </button>
        <button
          type="button"
          className="btn secondary"
          disabled={loading || page >= totalPages}
          onClick={() => setPage((n) => n + 1)}
        >
          다음 쪽
        </button>
      </div>
      <p role="status">{err}</p>
    </>
  );
}

// [추가 11/23 고은] F-20 회원 관리 · 관리자 권한 부여/해제 (서버 API: GET /api/admin/members · PUT …/{id}/role)
//   이유: 원래 회원 관리 탭(AccountForms.jsx의 MemberManagement)은 이 브라우저에 저장된 체험 회원만 바꾸는 시연이었어요
//   → 다른 팀원 파일(AccountForms.jsx)은 건드리지 않고, 관리자 화면 모음인 이 파일에 서버 연결 버전을 새로 만들었어요
//   onChange: 권한을 바꾼 뒤 부르는 함수 (AdminPage가 로그인 정보를 다시 읽게 넘겨줘요)
export function MemberManagement({ onChange }) {
  // 화면에 적는 중인 검색 조건(form) vs 실제로 검색한 조건(filters) · 접근 기록 탭과 같은 방식
  const empty = { keyword: '', role: '', status: '' };
  const [form, setForm] = useState(empty),
    [filters, setFilters] = useState(empty),
    [page, setPage] = useState(1),
    [rows, setRows] = useState([]),
    [meta, setMeta] = useState(null),
    [message, setMessage] = useState(''),
    [loading, setLoading] = useState(false),
    // reload: 권한을 바꾼 뒤 숫자를 올려서 목록을 다시 불러오게 하는 신호
    [reload, setReload] = useState(0);

  useEffect(() => {
    let ignore = false; // 늦게 도착한 예전 응답은 버려요
    setLoading(true);
    fetchMembers({ ...filters, page, size: MEMBER_PAGE_SIZE })
      .then((res) => {
        if (!ignore) {
          setRows(res.rows);
          setMeta(res.meta);
        }
      })
      .catch((e) => {
        if (!ignore) {
          setRows([]);
          setMeta(null);
          setMessage(e.message); // 403(관리자 아님) · 서버 꺼짐 등 → 서버 문구 그대로
        }
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [filters, page, reload]);

  async function changeRole(member) {
    const nextRole = member.role === 'ADMIN' ? 'USER' : 'ADMIN';
    const action = nextRole === 'ADMIN' ? '부여' : '해제';
    if (
      !window.confirm(`${member.memberId}번 회원(${member.email})의 관리자 권한을 ${action}할까요?`)
    )
      return;
    setLoading(true);
    try {
      await updateMemberRole(member.memberId, nextRole);
      setMessage(`${member.memberId}번 회원의 관리자 권한을 ${action}했어요.`);
      setReload((n) => n + 1);
      onChange?.();
    } catch (e) {
      // 409: 자기 자신 해제 · 성별 없는 관리자 · 이미 그 권한 · 탈퇴 회원 → 서버 문구 그대로
      setMessage(e.message);
      setLoading(false);
    }
  }

  const totalPages = Math.max(meta?.totalPages || 0, 1);
  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));
  return (
    <>
      <h3>회원 관리</h3>
      <p>
        이메일 일부를 가린 회원 목록이에요. 권한을 바꾸면 기록이 남아요. 자기 자신의 관리자 권한은
        해제할 수 없어요.
      </p>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault();
          setMessage('');
          setFilters({ ...form });
          setPage(1);
        }}
      >
        <label className="field">
          이메일 (일부만 써도 돼요)
          <input value={form.keyword} onChange={set('keyword')} maxLength={100} />
        </label>
        <label className="field">
          권한
          <select value={form.role} onChange={set('role')}>
            <option value="">전체</option>
            <option value="USER">{ROLE_LABEL.USER}</option>
            <option value="ADMIN">{ROLE_LABEL.ADMIN}</option>
          </select>
        </label>
        <label className="field">
          계정 상태
          <select value={form.status} onChange={set('status')}>
            <option value="">전체</option>
            <option value="ACTIVE">{STATUS_LABEL.ACTIVE}</option>
            <option value="WITHDRAWN">{STATUS_LABEL.WITHDRAWN}</option>
          </select>
        </label>
        <button className="btn" disabled={loading}>
          검색
        </button>
      </form>
      <p>
        전체 {meta?.total ?? 0}명 · {page} / {totalPages}쪽
      </p>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              {['회원 번호', '이메일', '가입일', '계정 상태', '현재 권한', '권한 관리'].map((v) => (
                <th key={v}>{v}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((m) => (
              <tr key={m.memberId}>
                <td>{m.memberId}</td>
                <td>{m.email}</td>
                <td>{m.createdAt || '-'}</td>
                <td>{STATUS_LABEL[m.status] || m.status}</td>
                <td>
                  <span className={'tag ' + (m.role === 'ADMIN' ? 'blue' : 'gray')}>
                    {ROLE_LABEL[m.role] || m.role}
                  </span>
                </td>
                <td>
                  {/* 탈퇴 회원은 서버도 막지만(409), 화면에서도 버튼을 꺼 둬요 */}
                  <button
                    type="button"
                    className="text-btn"
                    disabled={loading || m.status === 'WITHDRAWN'}
                    onClick={() => changeRole(m)}
                  >
                    {m.role === 'ADMIN' ? '관리자 권한 해제' : '관리자 권한 부여'}
                  </button>
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      {!loading && !rows.length && !message && <p className="empty">검색 결과가 없습니다.</p>}
      <div className="form-grid">
        <button
          type="button"
          className="btn secondary"
          disabled={loading || page <= 1}
          onClick={() => setPage((n) => n - 1)}
        >
          이전 쪽
        </button>
        <button
          type="button"
          className="btn secondary"
          disabled={loading || page >= totalPages}
          onClick={() => setPage((n) => n + 1)}
        >
          다음 쪽
        </button>
      </div>
      <p role="status">{message}</p>
    </>
  );
}

// [추가 11/26 고은] F-08-02 ② 예측 결과 조회 (서버 API: GET /api/admin/predictions)
//   전체 회원의 예상 세액 계산 결과를 이메일 일부 · 귀속 연도 · 종류(현재 결과/시나리오)로 찾고 쪽으로 나눠 봐요
//   이유: develop 체험판 삭제(#64) 뒤 「준비 중」이던 「예측 결과」 탭을 서버에 연결 · 회원 관리 탭과 같은 모양
//   보기만 하는 화면이라 수정 · 삭제 버튼은 없어요
export function PredictionResults() {
  // 화면에 적는 중인 검색 조건(form) vs 실제로 검색한 조건(filters)
  //   이유: 글자를 칠 때마다 서버에 요청하지 않고, 「검색」을 눌렀을 때만 요청하려고
  const empty = { keyword: '', taxYear: '', kind: '' };
  const [form, setForm] = useState(empty),
    [filters, setFilters] = useState(empty),
    [page, setPage] = useState(1),
    [rows, setRows] = useState([]),
    [meta, setMeta] = useState(null),
    [message, setMessage] = useState(''),
    [loading, setLoading] = useState(false);

  // filters나 page가 바뀌면 서버에서 다시 읽어요
  useEffect(() => {
    let ignore = false; // 늦게 도착한 예전 응답은 버려요 (검색을 빠르게 여러 번 눌렀을 때)
    setLoading(true);
    fetchPredictions({ ...filters, page, size: PREDICTION_PAGE_SIZE })
      .then((res) => {
        if (!ignore) {
          setRows(res.rows);
          setMeta(res.meta);
        }
      })
      .catch((e) => {
        if (!ignore) {
          setRows([]);
          setMeta(null);
          setMessage(e.message); // 연도 형식 · 403(관리자 아님) · 서버 꺼짐 등 → 문구 그대로
        }
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [filters, page]);

  const totalPages = Math.max(meta?.totalPages || 0, 1);
  const set = (key) => (e) => setForm((f) => ({ ...f, [key]: e.target.value }));
  return (
    <>
      <h3>예측 결과</h3>
      <p>전체 회원의 계산 결과예요. 이메일은 일부만 보이고, 볼 때마다 기록이 남아요.</p>
      <form
        className="form-grid"
        onSubmit={(e) => {
          e.preventDefault();
          setMessage('');
          setFilters({ ...form });
          setPage(1);
        }}
      >
        <label className="field">
          이메일 (일부만 써도 돼요)
          <input value={form.keyword} onChange={set('keyword')} maxLength={100} />
        </label>
        <label className="field">
          귀속 연도
          <input
            type="number"
            inputMode="numeric"
            placeholder="전체"
            min={2000}
            max={2100}
            value={form.taxYear}
            onChange={set('taxYear')}
          />
        </label>
        <label className="field">
          종류
          <select value={form.kind} onChange={set('kind')}>
            <option value="">전체</option>
            <option value="CURRENT">{PREDICTION_KIND_LABEL.CURRENT}</option>
            <option value="SCENARIO">{PREDICTION_KIND_LABEL.SCENARIO}</option>
          </select>
        </label>
        <button className="btn" disabled={loading}>
          검색
        </button>
      </form>
      <p>
        전체 {meta?.total ?? 0}건 · {page} / {totalPages}쪽
      </p>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              {[
                '예측 번호',
                '계산 일시',
                '회원',
                '귀속 연도',
                '종류',
                '총급여',
                '결정세액',
                '예상 환급액',
              ].map((v) => (
                <th key={v}>{v}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => {
              const kind = kindOf(r);
              // 시나리오면 이름과 기준 결과 번호도 붙여요 (기준 결과가 지워졌으면 번호 없음)
              const base = r.basePredictionId ? ` (기준 ${r.basePredictionId}번)` : '';
              const scenarioText = kind === 'SCENARIO' ? ` ${r.scenarioName}${base}` : '';
              return (
                <tr key={r.predictionId}>
                  <td>{r.predictionId}</td>
                  <td>{r.predictedAt || '-'}</td>
                  <td>{`${r.memberId}번 · ${r.email}`}</td>
                  <td>{r.taxYear}</td>
                  <td>
                    <span className={'tag ' + (kind === 'CURRENT' ? 'blue' : 'gray')}>
                      {PREDICTION_KIND_LABEL[kind]}
                    </span>
                    {scenarioText}
                  </td>
                  <td>{wonText(r.totalSalary)}</td>
                  <td>{wonText(r.decidedTax)}</td>
                  <td>{refundText(r.expectedRefund)}</td>
                </tr>
              );
            })}
          </tbody>
        </table>
      </div>
      {!loading && !rows.length && !message && <p className="empty">검색 결과가 없습니다.</p>}
      <div className="form-grid">
        <button
          type="button"
          className="btn secondary"
          disabled={loading || page <= 1}
          onClick={() => setPage((n) => n - 1)}
        >
          이전 쪽
        </button>
        <button
          type="button"
          className="btn secondary"
          disabled={loading || page >= totalPages}
          onClick={() => setPage((n) => n + 1)}
        >
          다음 쪽
        </button>
      </div>
      <p role="status">{message}</p>
    </>
  );
}

// [추가 11/26 고은] F-19 세법 기준값 관리 (서버 API: /api/admin/tax-criteria)
//   연도 고르기 · 세 가지 기준값 표 · 한 행 수정 · 같은 코드로 구간 추가 · 새 연도 추가(복사) · 변경 이력 · 이력으로 되돌리기
//   이유: develop 체험판 삭제(#64) 뒤 「준비 중」이던 「공제 기준값」 탭을 서버에 연결 (명세 F-08-01)
//   [수정 11/26 고은] 수정 · 이력을 표 아래가 아니라 모달(화면 가운데 띄우는 창)로 · 관리 버튼을 한 줄 작은 버튼으로
//     이유: 표 아래에 폼이 열리면 아래로 스크롤해야 보여서 · 버튼이 화면 너비에 따라 줄바꿈되던 것 고정
export function CriteriaManagement() {
  const [year, setYear] = useState(''), // 고른 연도 (''면 서버가 가장 최근 연도)
    [data, setData] = useState(null), // 서버 응답 (연도 목록 + 세 표)
    [target, setTarget] = useState(TARGET.DEDUCTION_TYPE), // 지금 보는 표
    [editing, setEditing] = useState(null), // { row, isAdd, changedKey } · 수정 모달을 연 행
    [form, setForm] = useState({}),
    [invalid, setInvalid] = useState([]), // 비운 필수 칸 (빨간 테두리)
    [formError, setFormError] = useState(''), // 모달 안에 보일 오류
    [history, setHistory] = useState(null), // { row, items } · 이력 모달을 연 행
    [copy, setCopy] = useState({ source: '', target: '' }),
    [message, setMessage] = useState(''),
    [loading, setLoading] = useState(false),
    [reload, setReload] = useState(0); // 저장 뒤 숫자를 올려 표를 다시 읽는 신호

  useEffect(() => {
    let ignore = false; // 늦게 온 예전 응답은 버림
    setLoading(true);
    fetchTaxCriteria(year)
      .then((res) => {
        if (ignore) return;
        setData(res);
        if (!year && res.taxYear) setYear(String(res.taxYear));
      })
      .catch((e) => !ignore && setMessage(e.message))
      .finally(() => !ignore && setLoading(false));
    return () => {
      ignore = true;
    };
  }, [year, reload]);

  // Esc 키로 열린 모달 닫기 (keydown = 키를 누를 때 생기는 이벤트)
  useEffect(() => {
    if (!editing && !history) return undefined;
    const onKey = (e) => {
      if (e.key === 'Escape') closeModals();
    };
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [editing, history]);

  const listOf = (t) =>
    t === TARGET.DEDUCTION_TYPE
      ? data?.deductionTypes
      : t === TARGET.TAX_BRACKET
        ? data?.taxBrackets
        : data?.benefitConditions;
  const rows = listOf(target) ?? [];
  const required = REQUIRED_FIELDS[target];

  function closeModals() {
    setEditing(null);
    setHistory(null);
    setInvalid([]);
    setFormError('');
  }

  function openForm(row, isAdd, preset = null) {
    closeModals();
    const f = preset ? preset.form : formFromRow(target, row);
    // 구간 추가는 새 구간을 적어야 해서 구간 · 근거 칸을 비워 둠 (나머지 값은 바탕 행 그대로)
    if (isAdd && target === TARGET.DEDUCTION_TYPE)
      Object.assign(f, { salaryMin: '', salaryMax: '', sourceNote: '' });
    if (isAdd && target === TARGET.TAX_BRACKET)
      Object.assign(f, { lowerAmount: '', upperAmount: '', baseAmount: '', sourceNote: '' });
    setForm(f);
    setEditing({ row, isAdd, changedKey: preset?.key ?? null });
  }

  async function save(e) {
    e.preventDefault();
    // 필수 칸이 비었으면 서버에 보내지 않고 빨간 테두리로 표시
    const missing = missingRequired(target, form);
    setInvalid(missing);
    if (missing.length) {
      setFormError('빨간 * 표시 칸을 채워 주세요');
      document.getElementById(`criteria-${missing[0]}`)?.focus(); // 첫 빈칸으로 커서 이동
      return;
    }
    try {
      const body = toRequestBody(target, form, editing.row, editing.isAdd);
      setLoading(true);
      await saveTaxCriteria(body);
      setMessage(
        editing.isAdd
          ? '행을 추가했어요. 최대 5분 뒤부터 계산에 반영돼요.'
          : '저장했어요. 최대 5분 뒤부터 계산에 반영돼요.',
      );
      closeModals();
      setReload((n) => n + 1);
    } catch (err) {
      setFormError(err.message); // 400 입력값 · 404 없는 행 · 409 중복 → 서버 문구 그대로 (모달 안에)
      setLoading(false);
    }
  }

  async function openHistory(row) {
    closeModals();
    setMessage('');
    try {
      setHistory({ row, items: await fetchCriteriaHistory(target, idOf(target, row)) });
    } catch (err) {
      setMessage(err.message);
    }
  }

  // 이력 한 줄의 「이전 값」으로 되돌리기 → 수정 모달을 그 값으로 채워 열기 (저장은 관리자가 확인 후)
  function revert(h) {
    const preset = formForRevert(target, history.row, h);
    if (preset) openForm(history.row, false, preset);
  }

  async function copyYear(e) {
    e.preventDefault();
    setMessage('');
    if (!window.confirm(`${copy.source}년 기준값을 복사해서 ${copy.target}년을 만들까요?`)) return;
    try {
      setLoading(true);
      await copyTaxCriteriaYear(copy.source, copy.target);
      setMessage(`${copy.target}년을 추가했어요. 바뀐 값만 고쳐 주세요.`);
      setCopy({ source: '', target: '' });
      setYear(String(copy.target));
      setReload((n) => n + 1);
    } catch (err) {
      setMessage(err.message);
      setLoading(false);
    }
  }

  const set = (key) => (e) => {
    setForm((f) => ({ ...f, [key]: e.target.value }));
    setInvalid((list) => list.filter((k) => k !== key)); // 고치면 빨간 테두리 해제
  };
  // 입력칸 하나 (필수면 빨간 *, 비웠으면 빨간 테두리, 되돌리기로 바뀐 칸은 노란 배경)
  const fieldClass = (key) =>
    [invalid.includes(key) && styles.invalid, editing?.changedKey === key && styles.changed]
      .filter(Boolean)
      .join(' ');
  const labelText = (key, label) => (
    <>
      {label}
      {required.includes(key) && (
        <span className={styles.required} aria-label="필수">
          *
        </span>
      )}
    </>
  );
  const field = (key, label, extra = {}) => (
    <label key={key} className={extra.wide ? styles.wide : undefined}>
      {labelText(key, label)}
      <input
        id={`criteria-${key}`}
        className={fieldClass(key)}
        value={form[key] ?? ''}
        onChange={set(key)}
        aria-invalid={invalid.includes(key)}
        inputMode={extra.inputMode}
        maxLength={extra.maxLength}
        placeholder={extra.placeholder}
      />
    </label>
  );
  const selectField = (key, label, options) => (
    <label key={key}>
      {labelText(key, label)}
      <select
        id={`criteria-${key}`}
        className={fieldClass(key)}
        value={form[key] ?? ''}
        onChange={set(key)}
      >
        {options.map(([value, text]) => (
          <option key={value} value={value}>
            {text}
          </option>
        ))}
      </select>
    </label>
  );
  const updated = (r) => `${r.updatedAt || '-'} · ${r.updatedBy ? `${r.updatedBy}번` : '스크립트'}`;
  const small = `btn secondary ${styles.small}`;
  const actions = (r, canAdd) => (
    <td className={styles.actionsCell}>
      <div className={styles.actions}>
        <button
          type="button"
          className={small}
          disabled={loading}
          onClick={() => openForm(r, false)}
        >
          수정
        </button>
        {canAdd && (
          <button
            type="button"
            className={small}
            disabled={loading}
            onClick={() => openForm(r, true)}
          >
            구간 추가
          </button>
        )}
        <button type="button" className={small} onClick={() => openHistory(r)}>
          이력
        </button>
      </div>
    </td>
  );
  const rowLabel = (r) => r?.typeCode || BRACKET_LABEL[r?.bracketType] || r?.benefitCode || '';

  return (
    <>
      <h3>세법 기준값 관리</h3>
      <ul>
        {CRITERIA_NOTICES.map((t) => (
          <li key={t}>{t}</li>
        ))}
      </ul>
      <div className="form-grid">
        <label className="field">
          귀속 연도
          <select
            value={year}
            onChange={(e) => {
              closeModals();
              setYear(e.target.value);
            }}
          >
            {(data?.years ?? []).map((y) => (
              <option key={y} value={y}>
                {y}년
              </option>
            ))}
          </select>
        </label>
      </div>
      <form className="form-grid" onSubmit={copyYear}>
        <label className="field">
          새 연도 추가: 복사할 연도
          <select
            value={copy.source}
            onChange={(e) => setCopy((c) => ({ ...c, source: e.target.value }))}
          >
            <option value="">선택</option>
            {(data?.years ?? []).map((y) => (
              <option key={y} value={y}>
                {y}년
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          새 연도
          <input
            inputMode="numeric"
            value={copy.target}
            onChange={(e) => setCopy((c) => ({ ...c, target: e.target.value }))}
            placeholder="예: 2027"
          />
        </label>
        <button className="btn secondary" disabled={loading || !copy.source || !copy.target}>
          연도 추가
        </button>
      </form>
      <div className="tabs">
        {Object.values(TARGET).map((t) => (
          <button
            type="button"
            key={t}
            className={target === t ? 'active' : ''}
            onClick={() => {
              setTarget(t);
              closeModals();
            }}
          >
            {TARGET_LABEL[t]} ({listOf(t)?.length ?? 0})
          </button>
        ))}
      </div>
      <div className="table-scroll">
        <table>
          {target === TARGET.DEDUCTION_TYPE && (
            <>
              <thead>
                <tr>
                  {[
                    '코드',
                    '항목',
                    '종류',
                    '공제율(%)',
                    '한도',
                    '총급여 구간(초과~이하)',
                    '카드 분류 · 결제',
                    '근거',
                    '마지막 수정',
                    '관리',
                  ].map((h) => (
                    <th key={h}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.typeId}>
                    <td>{r.typeCode}</td>
                    <td>{r.typeName}</td>
                    <td>{KIND_LABEL[r.deductionKind] || r.deductionKind}</td>
                    <td>{r.rate}</td>
                    <td>{formatAmount(r.limitAmount)}</td>
                    <td>
                      {formatAmount(r.salaryMin, '0')} ~ {formatAmount(r.salaryMax, '상한 없음')}
                    </td>
                    <td>
                      {[r.categoryName, PAY_LABEL[r.payMethod]].filter(Boolean).join(' · ') || '-'}
                    </td>
                    <td>{r.sourceNote || '-'}</td>
                    <td>{updated(r)}</td>
                    {actions(r, true)}
                  </tr>
                ))}
              </tbody>
            </>
          )}
          {target === TARGET.TAX_BRACKET && (
            <>
              <thead>
                <tr>
                  {[
                    '구간 종류',
                    '구간 시작(초과)',
                    '구간 끝(이하)',
                    '비율(%)',
                    '누적액',
                    '근거',
                    '마지막 수정',
                    '관리',
                  ].map((h) => (
                    <th key={h}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.bracketId}>
                    <td>{BRACKET_LABEL[r.bracketType] || r.bracketType}</td>
                    <td>{formatAmount(r.lowerAmount)}</td>
                    <td>{formatAmount(r.upperAmount, '상한 없음')}</td>
                    <td>{r.rate}</td>
                    <td>{formatAmount(r.baseAmount)}</td>
                    <td>{r.sourceNote || '-'}</td>
                    <td>{updated(r)}</td>
                    {actions(r, true)}
                  </tr>
                ))}
              </tbody>
            </>
          )}
          {target === TARGET.BENEFIT_CONDITION && (
            <>
              <thead>
                <tr>
                  {[
                    '코드',
                    '혜택',
                    '나이',
                    '군 복무 인정',
                    '회사 규모',
                    '총급여 조건',
                    '무주택 세대주',
                    '감면율(%)',
                    '한도',
                    '기간(년)',
                    '근거',
                    '마지막 수정',
                    '관리',
                  ].map((h) => (
                    <th key={h}>{h}</th>
                  ))}
                </tr>
              </thead>
              <tbody>
                {rows.map((r) => (
                  <tr key={r.benefitId}>
                    <td>{r.benefitCode}</td>
                    <td>{r.benefitName}</td>
                    <td>
                      {r.minAge ?? '-'} ~ {r.maxAge ?? '-'}
                    </td>
                    <td>{r.maxMilitaryMonths ?? '-'}</td>
                    <td>{r.companySize || '-'}</td>
                    <td>{formatAmount(r.salaryLimit)}</td>
                    <td>{r.homelessHeadReqYn === 'Y' ? '필요' : '-'}</td>
                    <td>{r.reductionRate ?? '-'}</td>
                    <td>{formatAmount(r.limitAmount)}</td>
                    <td>{r.periodYears ?? '-'}</td>
                    <td>{r.sourceNote || '-'}</td>
                    <td>{updated(r)}</td>
                    {actions(r, false)}
                  </tr>
                ))}
              </tbody>
            </>
          )}
        </table>
      </div>
      {!loading && data && !rows.length && <p className="empty">이 연도에는 기준값이 없습니다.</p>}
      <p role="status">{message}</p>

      {/* 수정 · 구간 추가 모달 · 바깥(반투명 배경)을 누르거나 Esc 키로 닫힘 */}
      {editing && (
        <div
          className={styles.overlay}
          onClick={(e) => e.target === e.currentTarget && closeModals()}
        >
          <form
            className={styles.modal}
            role="dialog"
            aria-modal="true"
            aria-labelledby="criteria-modal-title"
            onSubmit={save}
            noValidate
          >
            <div className={styles.modalHead}>
              <div>
                <h3 id="criteria-modal-title">
                  {editing.isAdd ? '구간 추가' : editing.changedKey ? '이력으로 되돌리기' : '수정'}{' '}
                  · {rowLabel(editing.row)}
                </h3>
                <p>
                  {editing.row.taxYear}년 · {TARGET_LABEL[target]}
                  {editing.changedKey &&
                    ' · 노란 칸이 이전 값으로 바뀌었어요. 확인하고 저장하면 반영돼요.'}
                </p>
              </div>
              <button
                type="button"
                className={styles.close}
                aria-label="닫기"
                onClick={closeModals}
              >
                ×
              </button>
            </div>
            {target === TARGET.DEDUCTION_TYPE && (
              <p className={styles.fixed}>
                고정 값(수정 불가):{' '}
                {KIND_LABEL[editing.row.deductionKind] || editing.row.deductionKind}
                {editing.row.categoryName && ` · ${editing.row.categoryName}`}
                {editing.row.payMethod && ` · ${PAY_LABEL[editing.row.payMethod]}`}
              </p>
            )}
            <div className={styles.grid}>
              {target === TARGET.DEDUCTION_TYPE && [
                field('name', '항목명', { maxLength: 50 }),
                field('rate', '공제율(%)', { inputMode: 'decimal' }),
                field('salaryMin', '총급여 구간 시작(초과)', { inputMode: 'numeric' }),
                field('salaryMax', '총급여 구간 끝(이하)', {
                  inputMode: 'numeric',
                  placeholder: '비우면 상한 없음',
                }),
                field('limitAmount', '한도', { inputMode: 'numeric', placeholder: '비우면 없음' }),
              ]}
              {target === TARGET.TAX_BRACKET && [
                field('lowerAmount', '구간 시작(초과)', { inputMode: 'numeric' }),
                field('upperAmount', '구간 끝(이하)', {
                  inputMode: 'numeric',
                  placeholder: '비우면 상한 없음',
                }),
                field('rate', '비율(%)', { inputMode: 'decimal' }),
                field('baseAmount', '누적액', { inputMode: 'numeric' }),
              ]}
              {target === TARGET.BENEFIT_CONDITION && [
                field('name', '혜택명', { maxLength: 33 }),
                field('minAge', '최소 나이', { inputMode: 'numeric' }),
                field('maxAge', '최대 나이', { inputMode: 'numeric' }),
                field('maxMilitaryMonths', '군 복무 인정 최대 개월', { inputMode: 'numeric' }),
                selectField('companySize', '회사 규모', [
                  ['', '조건 없음'],
                  ['SME', '중소기업(SME)'],
                  ['MID', '중견기업(MID)'],
                  ['LARGE', '대기업(LARGE)'],
                ]),
                field('salaryLimit', '총급여 조건', {
                  inputMode: 'numeric',
                  placeholder: '비우면 없음',
                }),
                selectField('homelessHeadReqYn', '무주택 세대주', [
                  ['N', '필요 없음'],
                  ['Y', '필요'],
                ]),
                field('rate', '감면율(%)', { inputMode: 'decimal', placeholder: '비우면 없음' }),
                field('limitAmount', '한도', { inputMode: 'numeric', placeholder: '비우면 없음' }),
                field('periodYears', '적용 기간(년)', { inputMode: 'numeric' }),
              ]}
              {field('sourceNote', '근거 (법 조문 · 신고안내 쪽수)', {
                maxLength: 66,
                wide: true,
                placeholder: '예: 조특법 §126의2 · 신고안내 132p',
              })}
            </div>
            {formError && (
              <p className={styles.error} role="alert">
                {formError}
              </p>
            )}
            <div className={styles.foot}>
              <button type="button" className="btn secondary" onClick={closeModals}>
                취소
              </button>
              <button className="btn" disabled={loading}>
                {editing.isAdd ? '추가 완료' : '수정 완료'}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* 변경 이력 모달 */}
      {history && (
        <div
          className={styles.overlay}
          onClick={(e) => e.target === e.currentTarget && closeModals()}
        >
          <div
            className={styles.modal}
            role="dialog"
            aria-modal="true"
            aria-labelledby="criteria-history-title"
          >
            <div className={styles.modalHead}>
              <div>
                <h3 id="criteria-history-title">변경 이력 · {rowLabel(history.row)}</h3>
                <p>
                  최신순 · 최대 100줄 · 「되돌리기」를 누르면 그 줄의 이전 값으로 수정 창이 열려요
                </p>
              </div>
              <button
                type="button"
                className={styles.close}
                aria-label="닫기"
                onClick={closeModals}
              >
                ×
              </button>
            </div>
            <div className="table-scroll">
              <table className={styles.historyTable}>
                <thead>
                  <tr>
                    {['바꾼 시각', '칸', '이전 값', '새 값', '바꾼 관리자', ''].map((h) => (
                      <th key={h || 'action'}>{h}</th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {history.items.map((h) => (
                    <tr key={h.historyId}>
                      <td>{h.changedAt}</td>
                      <td>{h.columnName}</td>
                      <td>{h.oldValue ?? '(비어 있음)'}</td>
                      <td>{h.newValue ?? '(비움)'}</td>
                      <td>{h.changedBy ? `${h.changedBy}번` : '스크립트'}</td>
                      <td className={styles.actionsCell}>
                        {HISTORY_FIELD[target]?.[h.columnName] && (
                          <button type="button" className={small} onClick={() => revert(h)}>
                            되돌리기
                          </button>
                        )}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            {!history.items.length && <p className="empty">아직 바뀐 적이 없어요.</p>}
            <div className={styles.foot}>
              <button type="button" className="btn secondary" onClick={closeModals}>
                닫기
              </button>
            </div>
          </div>
        </div>
      )}
    </>
  );
}
