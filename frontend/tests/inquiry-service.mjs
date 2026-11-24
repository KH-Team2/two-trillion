import assert from 'node:assert/strict';
import { build } from 'esbuild';
import fs from 'node:fs';
import path from 'node:path';
import { pathToFileURL } from 'node:url';

// 고객센터 문의 API 연결 검사 (F-17 · 서버 없이 가짜 fetch로 확인) · [신규 11/20]
const dir = fs.mkdtempSync(path.join(process.cwd(), 'tests/.inquiry-'));
try {
  await build({
    entryPoints: ['src/api/inquiry-service.js'],
    outfile: `${dir}/inquiry.mjs`,
    platform: 'node',
    format: 'esm',
    bundle: true,
    logLevel: 'silent',
  });
  const {
    inquiryRequest,
    createInquiry,
    fetchInquiries,
    fetchInquiry,
    answerText,
    formatDateTime,
    CHECKING_MESSAGE,
    fetchAdminInquiries,
    answerRequest,
    updateInquiryAnswer,
  } = await import(pathToFileURL(`${dir}/inquiry.mjs`));

  // back 공통 봉투(ApiResponse) 모양 응답을 돌려주는 가짜 fetch · 보낸 요청은 calls에 남겨요
  const fakeFetch = (status, body, calls) => async (url, init) => {
    calls.push({ url, init });
    return {
      status,
      ok: status < 400,
      json: async () =>
        status < 400
          ? { success: true, data: body, meta: null, error: null }
          : { success: false, data: null, meta: null, error: body },
    };
  };

  // ── 입력 검사 ──
  assert.deepEqual(
    inquiryRequest({ inquiryType: 'QUESTION', title: '  환급 질문 ', content: ' 내용 ' }),
    { inquiryType: 'QUESTION', title: '환급 질문', content: '내용' },
    '앞뒤 띄어쓰기를 지우고 서버 이름으로 바꿔요',
  );
  assert.throws(() => inquiryRequest({ inquiryType: '', title: 'a', content: 'b' }), /유형/);
  assert.throws(() => inquiryRequest({ inquiryType: 'HACK', title: 'a', content: 'b' }), /유형/);
  assert.throws(() => inquiryRequest({ inquiryType: 'ETC', title: '   ', content: 'b' }), /제목/);
  assert.throws(
    () => inquiryRequest({ inquiryType: 'ETC', title: '가'.repeat(67), content: 'b' }),
    /66자/,
  );
  assert.doesNotThrow(() =>
    inquiryRequest({ inquiryType: 'ETC', title: '가'.repeat(66), content: 'b' }),
  );
  assert.throws(() => inquiryRequest({ inquiryType: 'ERROR', title: 'a', content: ' ' }), /내용/);

  // ── 작성: POST /api/inquiries · JSON 본문 · 회원번호를 보내지 않음 ──
  let calls = [];
  await createInquiry(
    { inquiryType: 'ERROR', title: '업로드 오류', content: '파일이 안 올라가요' },
    { fetchImpl: fakeFetch(200, null, calls), accessToken: 'test.token' },
  );
  assert.equal(calls[0].url, '/api/inquiries');
  assert.equal(calls[0].init.method, 'POST');
  assert.equal(calls[0].init.headers.Authorization, 'Bearer test.token');
  const sent = JSON.parse(calls[0].init.body);
  assert.deepEqual(sent, {
    inquiryType: 'ERROR',
    title: '업로드 오류',
    content: '파일이 안 올라가요',
  });
  assert.equal('memberId' in sent, false, '회원번호는 토큰에서 정해요');

  // 입력이 틀리면 서버에 보내지 않아요
  calls = [];
  assert.throws(
    () =>
      createInquiry(
        { inquiryType: 'ETC', title: '', content: 'b' },
        { fetchImpl: fakeFetch(200, null, calls) },
      ),
    /제목/,
  );
  assert.equal(calls.length, 0);

  // ── 목록: GET /api/inquiries ──
  calls = [];
  const list = await fetchInquiries({
    fetchImpl: fakeFetch(200, [{ inquiryId: 3, status: 'WAITING' }], calls),
  });
  assert.equal(calls[0].url, '/api/inquiries');
  assert.equal(calls[0].init.method, 'GET');
  assert.equal(list.length, 1);
  assert.deepEqual(
    await fetchInquiries({ fetchImpl: fakeFetch(200, null, []) }),
    [],
    'null이면 빈 목록',
  );

  // 로그인 안 함 → 서버 문구 그대로
  await assert.rejects(
    fetchInquiries({
      fetchImpl: fakeFetch(401, { code: 'UNAUTHORIZED', message: '로그인이 필요해요' }, []),
      accessToken: '',
    }),
    (e) => e.code === 'UNAUTHORIZED' && e.message === '로그인이 필요해요',
  );
  // 서버 꺼짐 → OFFLINE
  await assert.rejects(
    fetchInquiries({
      fetchImpl: async () => {
        throw new Error('down');
      },
    }),
    (e) => e.code === 'OFFLINE' && /고객센터 서버/.test(e.message),
  );

  // ── 상세: GET /api/inquiries/{id} · 남의 문의는 404 ──
  calls = [];
  const one = await fetchInquiry(7, { fetchImpl: fakeFetch(200, { inquiryId: 7 }, calls) });
  assert.equal(calls[0].url, '/api/inquiries/7');
  assert.equal(one.inquiryId, 7);
  await assert.rejects(
    fetchInquiry(8, {
      fetchImpl: fakeFetch(404, { code: 'NOT_FOUND', message: '문의를 찾을 수 없어요' }, []),
    }),
    (e) => e.code === 'NOT_FOUND',
  );
  assert.throws(() => fetchInquiry('1/../admin'), /문의 번호/, '이상한 번호는 주소에 넣지 않아요');
  assert.throws(() => fetchInquiry(0), /문의 번호/);

  // ── 답변 문구 · 날짜 ──
  assert.equal(answerText({ status: 'CHECKING', answer: null }), CHECKING_MESSAGE);
  assert.equal(answerText({ status: 'ANSWERED', answer: '답변입니다' }), '답변입니다');
  assert.match(answerText({ status: 'WAITING' }), /기다리고/);
  assert.equal(formatDateTime(null), '');
  assert.equal(formatDateTime('잘못된 값'), '');
  assert.match(formatDateTime('2026-11-20T17:30:00'), /2026/);

  // ── [추가 11/20] F-18 관리자: 전체 목록 · 확인 중 · 답변 등록 ──
  calls = [];
  await fetchAdminInquiries('', { fetchImpl: fakeFetch(200, [], calls) });
  await fetchAdminInquiries('WAITING', { fetchImpl: fakeFetch(200, [], calls) });
  await fetchAdminInquiries('HACK', { fetchImpl: fakeFetch(200, [], calls) });
  assert.deepEqual(
    calls.map((c) => c.url),
    ['/api/admin/inquiries', '/api/admin/inquiries?status=WAITING', '/api/admin/inquiries'],
    '상태가 셋 중 하나일 때만 ?status= 를 붙여요',
  );
  // 관리자가 아니면 403 → 서버 문구 그대로
  await assert.rejects(
    fetchAdminInquiries('', {
      fetchImpl: fakeFetch(403, { code: 'FORBIDDEN', message: '권한이 없어요' }, []),
    }),
    (e) => e.code === 'FORBIDDEN',
  );

  assert.deepEqual(answerRequest({ status: 'CHECKING', answer: '무시' }), { status: 'CHECKING' });
  assert.deepEqual(answerRequest({ status: 'ANSWERED', answer: ' 답변 ' }), {
    status: 'ANSWERED',
    answer: '답변',
  });
  assert.throws(() => answerRequest({ status: 'ANSWERED', answer: '  ' }), /답변을 입력/);
  assert.throws(() => answerRequest({ status: 'WAITING' }), /상태/);

  calls = [];
  await updateInquiryAnswer(
    5,
    { status: 'ANSWERED', answer: '처리했어요' },
    {
      fetchImpl: fakeFetch(200, null, calls),
    },
  );
  assert.equal(calls[0].url, '/api/admin/inquiries/5/answer');
  assert.equal(calls[0].init.method, 'PUT');
  const answerBody = JSON.parse(calls[0].init.body);
  assert.deepEqual(answerBody, { status: 'ANSWERED', answer: '처리했어요' });
  assert.equal('adminId' in answerBody, false, '답변자는 토큰에서 정해요');
  // 이미 답변 완료 → 409 코드 그대로 (화면이 목록을 다시 불러옴)
  await assert.rejects(
    updateInquiryAnswer(
      5,
      { status: 'CHECKING' },
      {
        fetchImpl: fakeFetch(
          409,
          { code: 'ALREADY_ANSWERED', message: '이미 답변이 완료된 문의예요' },
          [],
        ),
      },
    ),
    (e) => e.code === 'ALREADY_ANSWERED',
  );
  assert.throws(() => updateInquiryAnswer('x', { status: 'CHECKING' }), /문의 번호/);

  console.log(
    'PASS: inquiry-service 문의 작성 · 목록 · 상세 · 입력 검사 · 401 · 404 · OFFLINE · 관리자 목록 · 확인 중 · 답변 · 403 · 409',
  );
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
