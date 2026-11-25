// CSV·XLSX를 읽고 업로드 단위로 거래를 묶습니다. 중복 후보는 사용자 확인 전 집계에서 보류합니다.
// [수정 11/17 고은] useEffect 추가 · 이유: 화면이 열릴 때 서버 업로드 이력을 한 번 불러오려고 (TT-115)
import React, { useState, useId, useEffect } from 'react';
// [수정 11/17 고은] parseUploadFile(브라우저에서 파일 읽기) 대신 서버 업로드 API를 씀
// 이유: 브라우저에서만 읽으면 DB(EXPENSE)에 저장되지 않아 세액 계산에 카드 내역이 빠짐
// [수정 11/17 고은] loadServerUploads · deleteUpload 추가 (TT-115 업로드 이력 · 삭제)
import {
  uploadCardFile,
  fetchUploadExpenses,
  toUploadRow,
  loadServerUploads,
  deleteUpload,
} from '@/api/card-upload-service.js';
import {
  payCode,
  transactionExamples,
  uploadFiles,
  makeMeta,
  flagDuplicates,
  isCounted,
  removeUpload,
} from '@/api/expense-service.js';
// [수정 11/23 고은] recordAccess(브라우저 접근 기록) 삭제 · 이유: 체험판 기능 삭제 결정 · 접근 기록은 서버(ACCESS_LOG)가 남겨요
export function ExpenseUpload({ rows, meta, onChange }) {
  const [error, setError] = useState(''),
    [busy, setBusy] = useState(false),
    [source, setSource] = useState('card'),
    [fallback, setFallback] = useState(''),
    // [수정 11/17 고은] 결제수단 선택 창 상태 (TT-113)
    // null이면 창 닫힘 · {fileName, answer}면 창 열림 (answer: 사용자가 고른 값을 업로드 쪽에 돌려주는 함수)
    [payAsk, setPayAsk] = useState(null);
  const files = uploadFiles(meta, rows);

  // ───────── [추가 11/17 고은] TT-115: 화면이 열릴 때 서버 업로드 이력 불러오기 ─────────
  // 이유: 브라우저에 남은 목록은 다른 PC · 다른 브라우저에서 올린 것과 다를 수 있고,
  //       서버에서 지운 업로드가 화면에 남아 있을 수도 있음 → 서버(DB) 내용을 기준으로 다시 맞춤
  // useEffect(함수, []): 화면이 처음 나타날 때 한 번만 실행 (빈 배열 [] = 다시 실행할 조건 없음)
  useEffect(() => {
    let stop = false; // 화면이 닫힌 뒤에 응답이 오면 무시하려는 표시
    setBusy(true);
    loadServerUploads()
      .then(({ rows: serverRows, files: serverFiles }) => {
        if (stop) return;
        // 서버 거래 · 파일로 통째로 바꿈
        // [수정 11/23 고은] 서버에 저장되지 않은 거래(예전 브라우저 체험 데이터)를 남기던 부분 삭제
        //   이유: 체험판 기능 삭제 결정 → 화면에는 서버에 저장된 업로드만 보여 줘요
        onChange(serverRows, makeMeta(serverFiles));
      })
      .catch((err) => {
        if (stop) return;
        // [수정 11/23 고은] 서버가 꺼져 있을 때(OFFLINE) 조용히 넘어가던 것 → 안내 표시
        //   이유: 체험판 삭제로 서버 없이 쓰는 화면이 없어짐 (로그인 만료(401)는 api-client가 로그인 화면으로 보냄)
        setError(`업로드 이력을 불러오지 못했어요. ${err.message}`);
      })
      .finally(() => {
        if (!stop) setBusy(false);
      });
    // 정리 함수: 화면이 사라질 때 실행 → 늦게 온 응답을 무시하게 표시
    return () => {
      stop = true;
    };
    // 처음 한 번만 불러오면 되므로 rows · onChange가 바뀌어도 다시 실행하지 않음
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // ───────── [추가 11/17 고은] TT-113: 결제수단 선택 · 중복 경고 후 다시 보내기 ─────────

  /**
   * 결제수단 선택 창을 열고, 사용자가 버튼을 누를 때까지 기다림
   *
   * Promise(약속): 「나중에 값이 정해지면 알려 줄게」라는 상자.
   * resolve를 화면 상태(payAsk.answer)에 넣어 두었다가, 버튼을 누르면 그 함수를 불러서 값을 돌려줌
   * → 업로드 코드는 await askPayMethod(...)에서 버튼이 눌릴 때까지 멈춰 있다가 고른 값을 받음
   *
   * @returns 'CREDIT' | 'CHECK' | null(취소)
   */
  const askPayMethod = (fileName) =>
    new Promise((resolve) => {
      setPayAsk({
        fileName,
        answer: (value) => {
          setPayAsk(null); // 창 닫기
          resolve(value); // 기다리던 업로드 코드에 고른 값 전달
        },
      });
    });

  /** 사용자가 취소했다는 표시용 오류 (일반 실패와 구분해서 「처리 실패」로 남기지 않으려고) */
  const cancelled = () => Object.assign(new Error('업로드를 취소했어요.'), { code: 'CANCELLED' });

  /**
   * 파일 1개를 올리고, 서버가 「결제수단이 필요해요(400)」나 「중복이에요(409)」라고 하면
   * 사용자에게 물어본 뒤 같은 파일을 다시 보냄
   *
   * - PAY_METHOD_REQUIRED: 파일에 사용구분 칸이 없음 → 신용/체크 선택 창 → 고른 값으로 다시 보냄
   * - DUPLICATE_WARNING: 이미 올린 결제와 같은 건이 있음 → 확인 창 → 「확인」이면 allowDuplicate=true로 다시 보냄
   *   (같은 날 · 같은 가게 · 같은 금액 결제는 실제로 두 번 있을 수 있어서 막지 않고 묻기만 함)
   * - 그 밖의 오류 · 같은 오류가 또 오면: 그대로 던져서 아래 catch에서 안내
   *
   * @returns {saved: 서버 업로드 응답, payMethod: 실제로 보낸 결제수단 코드}
   */
  const uploadWithChoices = async (file) => {
    let payMethod = source === 'hometax' ? undefined : payCode(fallback);
    let allowDuplicate = false;
    // 최대 3번: 처음 보내기 → (결제수단 고른 뒤) → (중복 확인 뒤) · 무한 반복 방지
    for (let attempt = 0; attempt < 3; attempt++) {
      try {
        const saved = await uploadCardFile(file, { payMethod, allowDuplicate });
        return { saved, payMethod };
      } catch (err) {
        if (err.code === 'PAY_METHOD_REQUIRED' && !payMethod) {
          const picked = await askPayMethod(file.name);
          if (!picked) throw cancelled();
          payMethod = picked;
          continue; // 고른 결제수단으로 다시 보내기
        }
        if (err.code === 'DUPLICATE_WARNING' && !allowDuplicate) {
          const ok = window.confirm(
            `${file.name}\n${err.message}\n\n같은 날 · 같은 가게 · 같은 금액 결제가 실제로 두 번 있었다면 「확인」을 눌러 그대로 올려 주세요.\n이미 올린 파일이면 「취소」를 눌러 주세요.`,
          );
          if (!ok) throw cancelled();
          allowDuplicate = true;
          continue; // 「그래도 올리기」로 다시 보내기
        }
        throw err;
      }
    }
    throw cancelled();
  };
  const changeMethod = (id, value) => {
    const others = rows.filter((r) => (r.uploadId || 'legacy') !== id);
    const selected = rows
      .filter((r) => (r.uploadId || 'legacy') === id)
      .map((r) => ({
        ...r,
        paymentMethod:
          r.paymentMethod === '확인 필요' || !r.paymentMethod ? value : r.paymentMethod,
        PAY_METHOD: payCode(
          r.paymentMethod === '확인 필요' || !r.paymentMethod ? value : r.paymentMethod,
        ),
      }));
    onChange(
      [...others, ...flagDuplicates(others, selected)],
      makeMeta(files.map((f) => (f.id === id ? { ...f, paymentMethod: value } : f))),
    );
  };
  const resolve = (id, include) => {
    if (
      include &&
      !window.confirm(
        '중복 가능 거래를 별개의 정상 거래로 확인하고 집계에 포함하시겠습니까? 같은 거래를 두 번 포함하지 않도록 확인해주세요.',
      )
    )
      return;
    onChange(
      rows.map((r) =>
        (r.uploadId || 'legacy') === id && r.duplicateState === 'pending'
          ? { ...r, duplicateState: include ? 'included' : 'excluded' }
          : r,
      ),
      meta,
    );
  };
  return (
    <div className="inline-note upload-inline">
      <h3>카드·현금영수증 거래내역 업로드</h3>
      {/* [수정 11/17 고은] 안내 문구를 서버 처리 기준으로 바꿈 · 이유: 이제 실제 목록으로 분류하고 DB에 저장함 */}
      <p>
        지원: CSV(UTF-8·한글 인코딩) · XLSX · 홈택스 현금영수증 XLS · 첫 행에 거래일자/승인일시,
        가맹점명, 이용금액 칸이 있어야 합니다. 사용구분(신용/체크) 칸은 없어도 됩니다. 파일당
        10,000건까지 올릴 수 있고, 암호가 걸린 파일은 읽을 수 없습니다.
      </p>
      <p>
        파일 업로드 → 서버에서 가맹점명을 공공데이터 목록(약 8만 곳)과 대조해 자동 분류 → 거래내역
        저장 → 세액 계산에 반영. 원본 파일과 카드번호는 저장하지 않습니다. 머신러닝은 사용하지
        않습니다.
      </p>
      <div className="form-grid">
        <label className="field">
          <span>업로드 파일 출처</span>
          <select value={source} onChange={(e) => setSource(e.target.value)}>
            <option value="card">카드사 이용내역</option>
            <option value="hometax">홈택스 현금영수증 사용내역</option>
          </select>
        </label>
        {source === 'card' ? (
          <label className="field">
            <span>파일에 결제수단이 없는 경우</span>
            <select value={fallback} onChange={(e) => setFallback(e.target.value)}>
              <option value="">선택해주세요</option>
              <option>신용카드</option>
              <option>체크카드</option>
            </select>
            <small>파일에 유효한 결제수단이 있으면 해당 값을 우선합니다.</small>
          </label>
        ) : (
          <p>홈택스 출처의 모든 거래는 현금영수증으로 처리합니다.</p>
        )}
      </div>
      <div className="actions">
        <label className="btn">
          {/* [수정 11/17 고은] xls 추가 · 이유: 홈택스 현금영수증은 xls로만 내려받을 수 있음 (서버가 xls도 읽음) */}
          {busy ? '파일 업로드 중…' : 'CSV·XLSX·XLS 파일 추가 (여러 개 가능)'}
          <input
            aria-label="거래내역 CSV·XLSX·XLS 파일 추가"
            type="file"
            multiple
            accept=".csv,.xlsx,.xls,text/csv,application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,application/vnd.ms-excel"
            disabled={busy}
            onChange={async (e) => {
              const input = e.target,
                selected = Array.from(input.files || []);
              if (!selected.length) return;
              setError('');
              setBusy(true);
              let nextRows = [...rows],
                nextFiles = [...files];
              const messages = [];
              // [추가 11/19 고은] 지원하지 않는 형식 파일 이름을 모아 두는 곳
              // 이유: 전에는 pdf 등도 파일 목록에 「처리 실패」로 올라가서 업로드된 것처럼 보였음
              //   → 이제 목록에 넣지 않고, 다 끝난 뒤 alert 창으로 한 번만 알려 줌
              const rejected = [];
              for (const file of selected) {
                // [추가 11/19 고은] 확장자(파일 이름 끝 .csv 등) 검사 → 서버에 보내지 않고 건너뜀
                // /\.(csv|xlsx|xls)$/i : 이름이 .csv · .xlsx · .xls로 끝나는지 (i = 대소문자 구분 안 함)
                // continue : 이 파일은 여기서 끝내고 다음 파일로 넘어감
                if (!/\.(csv|xlsx|xls)$/i.test(file.name)) {
                  rejected.push(file.name);
                  continue;
                }
                const id = Array.from(crypto.getRandomValues(new Uint8Array(16)))
                    .map((n) => n.toString(16).padStart(2, '0'))
                    .join(''),
                  at = new Date().toISOString();
                try {
                  // [수정 11/17 고은] 브라우저 해석 → 서버 업로드 (TT-112)
                  // 1) 파일을 서버로 보냄 · 결제수단은 카드사 파일에서 사용자가 골랐을 때만 보냄
                  //    (홈택스 파일은 서버가 현금영수증으로 정함)
                  // [수정 11/17 고은] uploadCardFile 직접 호출 → uploadWithChoices (TT-113)
                  // 이유: 결제수단이 필요하거나 중복이면 사용자에게 묻고 같은 파일을 다시 보내려고
                  const { saved, payMethod } = await uploadWithChoices(file);
                  // 2) 저장된 거래 + 분류 근거를 다시 받아 기존 화면 모양으로 바꿈
                  //    (중복 확인은 서버가 이미 했으므로 화면에서 다시 하지 않음)
                  const incoming = (await fetchUploadExpenses(saved.uploadId)).map((item) =>
                    toUploadRow(item, saved),
                  );
                  nextRows.push(...incoming);
                  nextFiles.push({
                    id: String(saved.uploadId), // 서버 업로드 번호를 파일 id로 씀
                    name: file.name,
                    count: incoming.length,
                    at: saved.uploadedAt || at,
                    source,
                    // [수정 11/17 고은] fallback → 실제로 보낸 결제수단 · 이유: 선택 창에서 고른 값이 반영되게
                    paymentMethod:
                      source === 'hometax'
                        ? '현금영수증'
                        : payMethod === 'CHECK'
                          ? '체크카드'
                          : payMethod === 'CREDIT'
                            ? '신용카드'
                            : fallback,
                    status: '업로드 완료',
                    serverSaved: true,
                  });
                  messages.push(`${file.name}: ${saved.rowCount}건 업로드 · 분류 완료`);
                } catch (err) {
                  // [추가 11/17 고은] 사용자가 직접 취소한 건 실패가 아니라서 파일 목록에 「처리 실패」로 남기지 않음
                  if (err.code === 'CANCELLED') {
                    messages.push(`${file.name}: ${err.message}`);
                    continue;
                  }
                  // [추가 11/19 고은] 확장자는 맞지만 서버가 읽을 수 없는 양식(UNSUPPORTED_FORMAT)도 목록에 넣지 않음
                  // 예: 이름만 .csv로 바꾼 파일, 거래일자 · 가맹점명 · 금액 칸을 못 찾는 파일
                  if (err.code === 'UNSUPPORTED_FORMAT') {
                    rejected.push(file.name);
                    continue;
                  }
                  nextFiles.push({
                    id,
                    name: file.name,
                    count: 0,
                    at,
                    source,
                    status: '처리 실패',
                    error: err.message,
                  });
                  // [수정 11/17 고은] 서버 오류 코드별 안내 · 이유: 서버가 준 문구에 「다음에 할 일」을 덧붙임
                  // [수정 11/17 고은 · TT-113] 결제수단 · 중복은 이제 위 uploadWithChoices가 물어보고 다시 보냄
                  //   → 여기까지 오는 건 다시 보냈는데도 같은 오류가 난 드문 경우라 짧게 안내
                  const hint =
                    {
                      PAY_METHOD_REQUIRED: '결제수단을 다시 골라 주세요.',
                      DUPLICATE_WARNING: '이미 올린 파일인지 확인해주세요.',
                      OFFLINE: '백엔드 · 계산 서버가 켜져 있는지 확인해주세요.',
                    }[err.code] ||
                    '첫 행에 거래일자(승인일시) · 가맹점명 · 이용금액 칸이 있는지 확인한 뒤 다시 업로드해주세요.';
                  messages.push(`${file.name}: ${err.message} ${hint}`);
                }
              }
              onChange(nextRows, makeMeta(nextFiles));
              setError(messages.join('\n'));
              input.value = '';
              setBusy(false);
              // [추가 11/19 고은] 지원하지 않는 파일이 있었으면 알림 창 (여러 개면 이름을 줄바꿈으로 나열)
              if (rejected.length) {
                alert(
                  `지원하지 않는 형식입니다.\nCSV · XLSX · XLS 파일만 올릴 수 있어요.\n\n${rejected.join('\n')}`,
                );
              }
            }}
          />
        </label>
      </div>
      {/* [추가 11/17 고은] 결제수단 선택 창 (TT-113) · 서버가 PAY_METHOD_REQUIRED를 주면 열림
          버튼을 누르면 payAsk.answer(값)이 불려서 기다리던 업로드가 그 값으로 다시 보내짐 */}
      {payAsk && (
        <div className="duplicate-note" role="dialog" aria-label="결제수단 선택">
          <p role="status">
            <b>{payAsk.fileName}</b>에는 신용/체크 구분 칸이 없어요. 이 파일의 결제수단을 골라
            주세요. 파일 안의 모든 거래에 같은 결제수단이 적용돼요.
          </p>
          <div className="actions">
            <button type="button" className="btn" onClick={() => payAsk.answer('CREDIT')}>
              신용카드
            </button>
            <button type="button" className="btn" onClick={() => payAsk.answer('CHECK')}>
              체크카드
            </button>
            <button type="button" className="btn secondary" onClick={() => payAsk.answer(null)}>
              업로드 취소
            </button>
          </div>
        </div>
      )}
      <h4>업로드 파일 관리 · {files.length}개</h4>
      {!files.length && <p>등록된 파일이 없습니다. 새 파일을 추가해도 기존 파일은 유지됩니다.</p>}
      <div className="upload-files">
        {files.map((file) => {
          const fileRows = rows.filter((r) => (r.uploadId || 'legacy') === file.id),
            unknown = fileRows.some((r) => r.paymentMethod === '확인 필요' || !r.paymentMethod),
            pending = fileRows.filter((r) => r.duplicateState === 'pending').length,
            excluded = fileRows.filter((r) => r.duplicateState === 'excluded').length;
          const status =
            file.status === '처리 실패'
              ? '처리 실패'
              : unknown || pending
                ? '확인 필요'
                : '업로드 완료';
          return (
            <article className="upload-file" key={file.id}>
              <div className="card-heading">
                <b>{file.name}</b>
                <span className={'tag ' + (status === '업로드 완료' ? 'green' : 'amber')}>
                  {status}
                </span>
              </div>
              <p>
                {file.source === 'hometax' ? '홈택스 현금영수증 사용내역' : '카드사 이용내역'} ·{' '}
                {Array.from(new Set(fileRows.map((r) => r.paymentMethod || '확인 필요'))).join(
                  ' / ',
                ) || '거래 없음'}{' '}
                · {fileRows.length}건
              </p>
              {file.error && (
                <p className="error">
                  {file.error} · 안내된 헤더와 오류 행을 확인한 뒤 다시 추가해주세요.
                </p>
              )}
              {unknown && (
                <label className="field">
                  <span>누락된 결제수단 선택 · 선택 전 집계 제외</span>
                  <select
                    aria-label={`${file.name} 결제수단`}
                    defaultValue=""
                    onChange={(e) => {
                      if (e.target.value) changeMethod(file.id, e.target.value);
                    }}
                  >
                    <option value="">결제수단 선택 필요</option>
                    <option>신용카드</option>
                    <option>체크카드</option>
                  </select>
                </label>
              )}
              {pending > 0 && (
                <div className="duplicate-note">
                  <p role="status">
                    기존에 등록된 내역과 중복될 가능성이 있는 거래가 {pending}건 발견되었습니다.
                    확인 전 집계에서 제외합니다.
                  </p>
                  <details>
                    <summary>중복 가능 거래 확인 ({pending}건)</summary>
                    <ul>
                      {fileRows
                        .filter((r) => r.duplicateState === 'pending')
                        .map((r) => (
                          <li key={r.id}>
                            {r.date} · {r.merchant} · {r.amount.toLocaleString()}원 ·{' '}
                            {r.paymentMethod}
                          </li>
                        ))}
                    </ul>
                  </details>
                  <div className="actions">
                    <button
                      type="button"
                      className="btn secondary"
                      onClick={() => resolve(file.id, false)}
                    >
                      중복으로 확인 · 집계 제외
                    </button>
                    <button
                      type="button"
                      className="btn secondary"
                      onClick={() => resolve(file.id, true)}
                    >
                      별개 거래로 확인 · 집계 포함
                    </button>
                  </div>
                </div>
              )}
              {excluded > 0 && (
                <p>사용자 확인으로 중복 {excluded}건 집계 제외 · 원본 거래는 조회에 유지됩니다.</p>
              )}
              <button
                type="button"
                className="text-btn danger"
                // [수정 11/17 고은] 서버에 저장된 업로드면 서버에서도 지움 (TT-115)
                // 이유: 화면에서만 지우면 DB(EXPENSE)에 남아 세액 계산에 계속 들어감
                onClick={async () => {
                  if (
                    !window.confirm(
                      `${file.name}에서 등록한 거래 ${fileRows.length}건을 모두 삭제하시겠습니까? 서버에 저장된 거래도 지워지고 세액 계산에서 빠집니다. 다른 파일의 거래는 유지됩니다.`,
                    )
                  )
                    return;
                  if (file.serverSaved) {
                    try {
                      await deleteUpload(file.id); // 성공하면 200 (data: null) → 다음 줄로 · [수정 11/23 고은] 204 → 200
                    } catch (err) {
                      // NOT_FOUND: 이미 서버에서 지워진 업로드 → 화면에서만 마저 지우면 됨
                      // 그 밖의 오류: 서버에 남아 있으므로 화면에서도 지우지 않고 안내
                      if (err.code !== 'NOT_FOUND') {
                        setError(`${file.name}: 삭제하지 못했어요. ${err.message}`);
                        return;
                      }
                    }
                  }
                  const next = removeUpload(rows, files, file.id);
                  onChange(next.rows, next.meta);
                }}
              >
                파일 삭제
              </button>
            </article>
          );
        })}
      </div>
      {error && (
        <p className="error" role="status" style={{ whiteSpace: 'pre-line' }}>
          {error}
        </p>
      )}
      <p>
        중복 기준: 거래일·공백 제외 상호명·금액·결제수단 일치. 중복 가능 거래는 보존하며 확인 전
        집계 제외, 별개 거래로 확인한 경우에만 포함합니다. 카드사와 현금영수증의 결제수단이 다른
        거래는 자동 중복 판정을 하지 않습니다. 결제수단 누락은 임의로 신용카드에 포함하지 않습니다.
      </p>
      <TransactionExamples rows={rows} />
    </div>
  );
}
export function TransactionExamples({ rows }) {
  const [open, setOpen] = useState(false),
    [category, setCategory] = useState('일반'),
    [file, setFile] = useState(''),
    [limit, setLimit] = useState(15);
  const id = useId();
  const categories = ['일반', '대중교통', '전통시장', '문화체육', '공제 대상 아님'];
  const fileIds = Array.from(new Set(rows.map((r) => r.uploadId || 'legacy')));
  const matches = transactionExamples(
    file ? rows.filter((r) => (r.uploadId || 'legacy') === file) : rows,
    category,
    limit,
  );
  return (
    <div className="transaction-examples">
      <button
        type="button"
        className="dash-jump"
        aria-expanded={open}
        aria-controls={id}
        onClick={() => setOpen(!open)}
      >
        결제수단 거래 분류 확인하기 {open ? '⌃' : '⌄'}
      </button>
      {open && (
        <div id={id}>
          <label className="field">
            <span>업로드별 조회</span>
            <select
              value={file}
              onChange={(e) => {
                setFile(e.target.value);
                setLimit(15);
              }}
            >
              <option value="">전체 업로드 파일</option>
              {fileIds.map((id) => (
                <option key={id} value={id}>
                  {rows.find((r) => (r.uploadId || 'legacy') === id)?.uploadName || id}
                </option>
              ))}
            </select>
          </label>
          <div className="type-buttons">
            <button
              type="button"
              className={!category ? 'selected' : ''}
              onClick={() => {
                setCategory('');
                setLimit(15);
              }}
            >
              전체 유형
            </button>
            {categories.map((c) => (
              <button
                type="button"
                className={c === category ? 'selected' : ''}
                aria-pressed={c === category}
                onClick={() => {
                  setCategory(c);
                  setLimit(15);
                }}
                key={c}
              >
                {c}
              </button>
            ))}
          </div>
          <p>
            {category || '전체 유형'} 전체 {matches.count}건 · 전체 거래 합계{' '}
            {matches.total.toLocaleString()}원 · 집계 포함 합계{' '}
            {matches.countedTotal.toLocaleString()}원. 처음 15건을 표시하며 더보기로 나머지 거래를
            조회할 수 있습니다.
          </p>
          <div className="table-scroll">
            <table>
              <thead>
                <tr>
                  <th>거래일</th>
                  <th>거래처명</th>
                  <th>거래금액</th>
                  <th>결제수단</th>
                  <th>소비 유형</th>
                  <th>분류 근거 / 집계 상태</th>
                </tr>
              </thead>
              <tbody>
                {matches.examples.map((r) => (
                  <tr key={`${r.uploadId}:${r.id}`}>
                    <td>{r.date}</td>
                    <td>{r.merchant}</td>
                    <td>{r.amount.toLocaleString('ko-KR')}원</td>
                    <td>{r.paymentMethod || '확인 필요'}</td>
                    <td>{r.category === '공제 제외' ? '공제 대상 아님' : r.category}</td>
                    <td>
                      {r.classificationBasis || r.method}
                      <small className="block-note">
                        {r.duplicateState === 'pending'
                          ? '중복 확인 전 · 집계 제외'
                          : r.duplicateState === 'excluded'
                            ? '중복 확인 · 집계 제외'
                            : isCounted(r)
                              ? '집계 포함'
                              : '결제수단 확인 전 · 집계 제외'}
                      </small>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          {matches.examples.length < matches.count && (
            <button type="button" className="btn secondary" onClick={() => setLimit(limit + 15)}>
              더보기 ({matches.examples.length} / {matches.count}건)
            </button>
          )}
          {!matches.count && <p className="empty">해당 거래내역이 없습니다</p>}
          <p>
            소비 유형 자동 분류는 추정 결과이며 실제 세법상 공제 적격성을 보증하지 않습니다.
            공공데이터 상호명 목록을 사용하며 일치하는 상호명이 없으면 일반으로 분류합니다. 조회
            전용이며 소비 유형을 직접 수정할 수 없습니다. 소득공제액은 지출 합계와 다릅니다.
          </p>
        </div>
      )}
    </div>
  );
}
