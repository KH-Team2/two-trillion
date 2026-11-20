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

  console.log('PASS: inquiry-service 문의 작성 · 목록 · 상세 · 입력 검사 · 401 · 404 · OFFLINE');
} finally {
  fs.rmSync(dir, { recursive: true, force: true });
}
