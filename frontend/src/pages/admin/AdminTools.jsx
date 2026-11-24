import { getDefaultTaxYear } from '@/utils/tax-year.js';
// 기존 관리자 도구 모음입니다. 화면 검사는 체험용이며 운영 권한은 서버에서 검사해야 합니다.
import { newPrototypeId } from '@/api/account-service.js';
import React, { useEffect, useState } from 'react';
// [수정 11/20] inquiryService · inquiryLabels(브라우저 체험 데이터) 가져오기 삭제
//   이유: F-18 문의 관리를 서버 API(inquiry-service.js)로 바꿈 · 다른 탭(기준값 · 접근 기록 · 예측 결과)은 그대로
// [수정 11/23] accessRecords(브라우저 체험 접근 기록) 가져오기 삭제
//   이유: F-21 접근 기록 탭을 서버 API(access-log-service.js)로 바꿈 · 기준값 · 예측 결과 탭은 그대로
import { criteriaRecords, saveCriterion, predictionRecords } from '@/api/prototype-records.js';
import { ACCESS_RESULT_LABEL, PAGE_SIZE, fetchAccessLogs } from '@/api/access-log-service.js';
// [추가 11/23 고은] F-20 회원 관리 서버 API
import {
  MEMBER_PAGE_SIZE,
  ROLE_LABEL,
  STATUS_LABEL,
  fetchMembers,
  updateMemberRole,
} from '@/api/admin-member-service.js';
import {
  CHECKING_MESSAGE,
  INQUIRY_STATUS_LABEL,
  INQUIRY_TYPE_LABEL,
  fetchAdminInquiries,
  formatDateTime,
  updateInquiryAnswer,
} from '@/api/inquiry-service.js';
const blank = {
  id: '',
  TAX_YEAR: getDefaultTaxYear(),
  TYPE_CODE: '',
  SALARY_MIN: 0,
  SALARY_MAX: null,
  RATE: 0,
  LIMIT_AMOUNT: 0,
  SOURCE_NOTE: '',
  UPDATED_AT: '',
  UPDATED_BY: '',
};
export function CriteriaManagement() {
  const [rows, setRows] = useState(() => criteriaRecords()),
    [d, setD] = useState(blank),
    [year, setYear] = useState(String(getDefaultTaxYear())),
    [err, setErr] = useState('');
  return (
    <>
      <h3>연도·총급여 구간별 공제 기준값</h3>
      <p className="notice amber">
        브라우저 관리 시연 · 입력한 기준값은 실제 계산 엔진과 미연동입니다. 검증된 운영 세법 기준이
        아닙니다.
      </p>
      <label className="field">
        조회 연도
        <input type="number" value={year} onChange={(e) => setYear(e.target.value)} />
      </label>
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              {['연도', '항목', '총급여 구간(원)', '비율·한도', '최종 수정', '작업'].map((s) => (
                <th key={s}>{s}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows
              .filter((r) => String(r.TAX_YEAR) === year)
              .map((r) => (
                <tr key={r.id}>
                  <td>{r.TAX_YEAR}</td>
                  <td>{r.TYPE_CODE}</td>
                  <td>
                    {r.SALARY_MIN.toLocaleString()} 초과 ~{' '}
                    {r.SALARY_MAX?.toLocaleString() || '상한 없음'} 이하
                  </td>
                  <td>
                    {r.RATE}% / {r.LIMIT_AMOUNT.toLocaleString()}원
                  </td>
                  <td>
                    {r.UPDATED_AT} · {r.UPDATED_BY}
                  </td>
                  <td>
                    <button type="button" className="text-btn" onClick={() => setD(r)}>
                      수정
                    </button>
                  </td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>
      {!rows.length && <p>등록된 기준값이 없습니다. 임의의 공식 기준값은 생성하지 않습니다.</p>}
      <form
        onSubmit={(e) => {
          e.preventDefault();
          try {
            saveCriterion({ ...d, id: d.id || newPrototypeId() });
            setRows(criteriaRecords());
            setD(blank);
            setErr('브라우저에 저장했습니다. 계산 엔진에는 반영되지 않습니다.');
          } catch (e) {
            setErr(e.message);
          }
        }}
      >
        <h4>{d.id ? '기준값 수정' : '기준값 행 추가'}</h4>
        <div className="form-grid">
          {[
            'TAX_YEAR',
            'TYPE_CODE',
            'SALARY_MIN',
            'SALARY_MAX',
            'RATE',
            'LIMIT_AMOUNT',
            'SOURCE_NOTE',
          ].map((k) => (
            <label className="field" key={k}>
              <span>
                {
                  {
                    TAX_YEAR: '귀속연도',
                    TYPE_CODE: '항목 코드',
                    SALARY_MIN: '총급여 시작 (초과)',
                    SALARY_MAX: '총급여 끝 (이하 · 빈칸은 상한 없음)',
                    RATE: '비율 (%)',
                    LIMIT_AMOUNT: '한도 (원)',
                    SOURCE_NOTE: '근거 · 조문/신고안내 쪽수',
                  }[k]
                }
              </span>
              <input
                type={['TYPE_CODE', 'SOURCE_NOTE'].includes(k) ? 'text' : 'number'}
                required={k !== 'SALARY_MAX'}
                min="0"
                value={d[k] ?? ''}
                onChange={(e) =>
                  setD({
                    ...d,
                    [k]: ['TYPE_CODE', 'SOURCE_NOTE'].includes(k)
                      ? e.target.value
                      : k === 'SALARY_MAX' && e.target.value === ''
                        ? null
                        : Number(e.target.value),
                  })
                }
              />
            </label>
          ))}
        </div>
        <button className="btn">저장 (시연)</button>
        <button type="button" className="btn secondary" onClick={() => setD(blank)}>
          새 행 입력
        </button>
        <p role="status">{err}</p>
      </form>
    </>
  );
}
// [수정 11/23] F-21 접근 기록: 브라우저 체험 데이터(accessRecords) → 서버 API (GET /api/admin/access-logs)
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

export function Predictions() {
  const [rows] = useState(() => predictionRecords()),
    [search, setSearch] = useState('');
  return (
    <>
      <h3>예측 결과 조회 · 브라우저 시연</h3>
      <input
        aria-label="예측 결과 검색"
        placeholder="계정 ID 또는 시나리오명"
        value={search}
        onChange={(e) => setSearch(e.target.value)}
      />
      <div className="table-scroll">
        <table>
          <thead>
            <tr>
              {['계정 ID', '종류', '계산일 / 시나리오', '결정세액', '환급 차액'].map((v) => (
                <th key={v}>{v}</th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows
              .filter((r) => (r.memberId + (r.scenarioName || '')).includes(search))
              .map((r, i) => (
                <tr key={i}>
                  <td>{r.memberId}</td>
                  <td>{r.kind}</td>
                  <td>
                    {r.date} {r.scenarioName}
                  </td>
                  <td>{r.decidedTax?.toLocaleString()}원</td>
                  <td>{r.expectedRefund?.toLocaleString()}원</td>
                </tr>
              ))}
          </tbody>
        </table>
      </div>
      <p>
        현재 브라우저에 보관된 체험 결과만 조회합니다. 실제 전체 회원 결과 DB 조회는 미연동입니다.
      </p>
    </>
  );
}
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
