import assert from 'node:assert/strict';
import { build } from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// 관리자 회원 관리 API 연결 검사 (F-20 · 서버 없이 가짜 fetch로 확인) · [신규 11/23 고은]
const dir = fs.mkdtempSync(path.join(process.cwd(), 'tests/.admin-member-'));
try {
  await build({
    entryPoints: ['src/api/admin-member-service.js'],
    outfile: `${dir}/admin-member.mjs`,
    platform: 'node',
    format: 'esm',
    bundle: true,
    logLevel: 'silent',
  });
  const { memberQuery, fetchMembers, updateMemberRole, MEMBER_PAGE_SIZE } = await import(
    pathToFileURL(`${dir}/admin-member.mjs`)
  );

  // 공통 봉투(ApiResponse) 모양 응답을 돌려주는 가짜 fetch · 보낸 요청은 calls에 남겨요
  const fakeFetch = (status, body, meta, calls) => async (url, init) => {
    calls.push({ url, init });
    return {
      status,
      ok: status < 400,
      json: async () =>
        status < 400
          ? { success: true, data: body, meta, error: null }
          : { success: false, data: null, meta: null, error: body },
    };
  };

  // ── 주소 만들기 · 화면 검사 ──
  assert.equal(memberQuery(), `?page=1&size=${MEMBER_PAGE_SIZE}`, '빈 조건은 빼고 쪽 정보만');
  assert.equal(
    memberQuery({ keyword: ' naver ', role: 'ADMIN', status: 'ACTIVE', page: 2 }),
    `?keyword=naver&role=ADMIN&status=ACTIVE&page=2&size=${MEMBER_PAGE_SIZE}`,
  );
  assert.equal(
    memberQuery({ keyword: 'a&role=ADMIN' }),
    `?keyword=a%26role%3DADMIN&page=1&size=${MEMBER_PAGE_SIZE}`,
    '& = 같은 글자는 주소를 망가뜨리지 않게 바뀌어요',
  );
  assert.throws(() => memberQuery({ role: 'MANAGER' }), /권한/);
  assert.throws(() => memberQuery({ status: 'LOCKED' }), /계정 상태/);
  assert.throws(() => memberQuery({ page: 0 }), /쪽 번호/);
  assert.throws(() => memberQuery({ size: 101 }), /100명/);

  // ── 목록: data + meta ──
  let calls = [];
  const meta = { page: 1, size: 20, total: 3, totalPages: 1 };
  const res = await fetchMembers(
    { role: 'USER' },
    {
      fetchImpl: fakeFetch(
        200,
        [{ memberId: 1002, email: 'te***@naver.com', role: 'USER', status: 'ACTIVE' }],
        meta,
        calls,
      ),
      accessToken: 'admin.token',
    },
  );
  assert.equal(calls[0].url, `/api/admin/members?role=USER&page=1&size=${MEMBER_PAGE_SIZE}`);
  assert.equal(calls[0].init.method, 'GET');
  assert.equal(calls[0].init.headers.Authorization, 'Bearer admin.token');
  assert.equal(res.rows[0].email, 'te***@naver.com');
  assert.deepEqual(res.meta, meta);

  // data · meta가 비어도 화면이 깨지지 않게
  const emptyRes = await fetchMembers({}, { fetchImpl: fakeFetch(200, null, null, []) });
  assert.deepEqual(emptyRes.rows, []);
  assert.equal(emptyRes.meta.totalPages, 1);

  // ── 권한 변경: PUT + 본문 { role } ──
  calls = [];
  await updateMemberRole(1002, 'ADMIN', { fetchImpl: fakeFetch(200, null, null, calls) });
  assert.equal(calls[0].url, '/api/admin/members/1002/role');
  assert.equal(calls[0].init.method, 'PUT');
  assert.deepEqual(JSON.parse(calls[0].init.body), { role: 'ADMIN' });

  // 서버 규칙(409) → 서버 문구 그대로
  await assert.rejects(
    updateMemberRole(1001, 'USER', {
      fetchImpl: fakeFetch(
        409,
        { code: 'SELF_DEMOTE', message: '자기 자신의 관리자 권한은 해제할 수 없어요' },
        null,
        [],
      ),
    }),
    (e) => e.code === 'SELF_DEMOTE' && /자기 자신/.test(e.message),
  );
  // 관리자가 아니면 403
  await assert.rejects(
    fetchMembers(
      {},
      { fetchImpl: fakeFetch(403, { code: 'FORBIDDEN', message: '접근 권한이 없어요' }, null, []) },
    ),
    (e) => e.code === 'FORBIDDEN',
  );
  // 서버 꺼짐 → OFFLINE
  await assert.rejects(
    fetchMembers(
      {},
      {
        fetchImpl: async () => {
          throw new Error('down');
        },
      },
    ),
    (e) => e.code === 'OFFLINE' && /관리자 서버/.test(e.message),
  );
  // 화면 검사에 걸리면 서버를 부르지 않아요
  calls = [];
  assert.throws(
    () => updateMemberRole(0, 'ADMIN', { fetchImpl: fakeFetch(200, null, null, calls) }),
    /회원 번호/,
  );
  assert.throws(
    () => updateMemberRole(5, 'ROOT', { fetchImpl: fakeFetch(200, null, null, calls) }),
    /권한/,
  );
  assert.equal(calls.length, 0);

  console.log(
    'PASS: admin-member-service 검색 주소 · 권한/상태 검사 · data+meta · PUT role · 409 · 403 · OFFLINE',
  );
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
