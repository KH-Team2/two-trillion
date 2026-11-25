// [추가 11/24 · 이우진 · TT-154] 고객센터 「상담 채팅」 탭 (기능명세서 F-07-03 · API 명세서 CONSULT-01 ~ 03 · WS-01 ~ 03)
// 회원: 「상담 시작하기」 → 내 방(회원당 1개) 열기 → 실시간 대화 · 지난 대화 다시 보기
// 관리자: 상담 방 목록(최근 메시지 순 · 아이디 일부 가림) → 방 골라 답하기 (관리자 여러 명이 같은 방에 답할 수 있음)
// 글자만 보내요 · 사진 · 파일은 보내지 않음 (11/24 결정 · 증명서 사진의 민감 정보 보관 책임 · 기한)
import React, { useEffect, useRef, useState } from 'react';
import { ArrowLeft, Headphones, RefreshCw, Send } from 'lucide-react';
import { accountService } from '@/api/account-service.js';
import {
  CONTENT_MAX,
  PAGE_SIZE,
  checkContent,
  contentLength,
  fetchConsultMessages,
  fetchConsultRooms,
  formatConsultTime,
  isMine,
  mergeMessages,
  myMemberId,
  openConsultRoom,
  senderLabel,
} from '@/api/consult-service.js';
import { openConsultSocket } from '@/api/consult-socket.js';
import { Btn } from '@/components/common/site-primitives.jsx';
import styles from './ConsultChat.module.css';

const STATUS_TEXT = {
  CONNECTING: '연결 중',
  CONNECTED: '연결됨',
  RECONNECTING: '연결이 끊겨 다시 연결하는 중',
  DENIED: '연결이 막혔어요 · 다시 로그인한 뒤 들어와 주세요',
  NO_LOGIN: '로그인이 필요해요',
};

export default function ConsultChat({ notify }) {
  const account = accountService.current();
  // 상담은 서버 로그인 회원만 (빠른 체험 계정은 서버 토큰이 없음)
  if (!account?.server) {
    return (
      <div className="empty">
        <Headphones size={32} />
        <p>로그인한 회원만 상담 채팅을 쓸 수 있어요.</p>
      </div>
    );
  }
  return account.role === 'ADMIN' ? (
    <AdminConsult notify={notify} />
  ) : (
    <MemberConsult notify={notify} />
  );
}

// 회원: 버튼을 눌러야 방을 열어요 (탭만 봐도 방이 생기지 않게)
function MemberConsult({ notify }) {
  const [roomId, setRoomId] = useState(null);
  const [opening, setOpening] = useState(false);

  async function start() {
    setOpening(true);
    try {
      setRoomId(await openConsultRoom());
    } catch (err) {
      notify(err.message);
    } finally {
      setOpening(false);
    }
  }

  if (roomId) return <ConsultRoom roomId={roomId} admin={false} notify={notify} />;
  return (
    <div className="empty">
      <Headphones size={32} />
      <h3>상담사와 1:1 채팅</h3>
      <p>
        연말정산 계산 · 화면 사용 중 궁금한 점을 상담사에게 물어보세요. 전에 나눈 대화가 있으면
        이어서 보여 드려요.
      </p>
      <Btn onClick={start} disabled={opening}>
        {opening ? '여는 중…' : '상담 시작하기'}
      </Btn>
    </div>
  );
}

// 관리자: 방 목록 → 방 하나 열기 → 뒤로 가면 목록 다시 불러옴
function AdminConsult({ notify }) {
  const [rooms, setRooms] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState('');
  const [selected, setSelected] = useState(null);
  const [reload, setReload] = useState(0);

  useEffect(() => {
    if (selected) return undefined;
    let ignore = false;
    setLoading(true);
    setLoadError('');
    fetchConsultRooms()
      .then((list) => {
        if (!ignore) setRooms(list);
      })
      .catch((err) => {
        if (!ignore) setLoadError(err.message);
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => {
      ignore = true;
    };
  }, [selected, reload]);

  if (selected) {
    return (
      <ConsultRoom
        roomId={selected.roomId}
        admin
        title={`${selected.customer} 고객님`}
        onBack={() => setSelected(null)}
        notify={notify}
      />
    );
  }
  return (
    <>
      <div className={styles.listHead}>
        <h3>상담 방 목록</h3>
        <button type="button" className="text-btn" onClick={() => setReload((n) => n + 1)}>
          <RefreshCw size={14} /> 새로고침
        </button>
      </div>
      <p className="muted">최근 메시지 순 · 최대 100개 · 아이디는 일부만 보여요.</p>
      {loading ? (
        <p className="muted">불러오는 중…</p>
      ) : loadError ? (
        <p className={styles.error}>{loadError}</p>
      ) : rooms.length ? (
        <div className="table-scroll">
          <table>
            <thead>
              <tr>
                {['고객', '상담 시작', '마지막 메시지', ''].map((s) => (
                  <th key={s}>{s}</th>
                ))}
              </tr>
            </thead>
            <tbody>
              {rooms.map((room) => (
                <tr key={room.roomId}>
                  <td>{room.customer}</td>
                  <td>{formatConsultTime(room.createdAt)}</td>
                  <td>
                    {room.lastMessageAt ? formatConsultTime(room.lastMessageAt) : '메시지 없음'}
                  </td>
                  <td>
                    <button type="button" className="text-btn" onClick={() => setSelected(room)}>
                      열기
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="muted">아직 상담 요청이 없어요.</p>
      )}
    </>
  );
}

// 방 하나: 지난 메시지 불러오기 → 실시간 연결 → 보내기
function ConsultRoom({ roomId, admin, title, onBack, notify }) {
  const [messages, setMessages] = useState([]);
  const [hasMore, setHasMore] = useState(false);
  const [loadingOld, setLoadingOld] = useState(false);
  const [status, setStatus] = useState('CONNECTING');
  const [draft, setDraft] = useState('');
  const [sendError, setSendError] = useState('');
  const socket = useRef(null);
  const listRef = useRef(null);
  const stickBottom = useRef(true); // 맨 아래를 보고 있을 때만 새 메시지에 따라 내려감
  const myId = myMemberId();

  // 방에 들어오면: 가장 최근 30개 → 실시간 연결 · 방을 나가면 연결 끊기
  useEffect(() => {
    let ignore = false;
    stickBottom.current = true;
    fetchConsultMessages(roomId)
      .then((list) => {
        if (ignore) return;
        setMessages((prev) => mergeMessages(prev, list));
        setHasMore(list.length === PAGE_SIZE);
      })
      .catch((err) => {
        if (!ignore) notify(err.message);
      });
    socket.current = openConsultSocket({
      roomId,
      onStatus: (next) => {
        if (!ignore) setStatus(next);
      },
      onMessage: (message) => {
        if (!ignore && message.roomId === roomId) {
          setMessages((prev) => mergeMessages(prev, [message]));
        }
      },
      onError: (error) => {
        if (!ignore) setSendError(error.message);
      },
      // 끊긴 사이 온 메시지 채우기 (가장 최근 30개를 다시 받아 합침)
      onReconnect: () => {
        fetchConsultMessages(roomId)
          .then((list) => {
            if (!ignore) setMessages((prev) => mergeMessages(prev, list));
          })
          .catch(() => {});
      },
    });
    return () => {
      ignore = true;
      socket.current?.close();
      socket.current = null;
    };
  }, [roomId]);

  // 새 메시지가 오면 맨 아래로 (위로 올려 지난 대화를 보는 중이면 그대로)
  useEffect(() => {
    const box = listRef.current;
    if (box && stickBottom.current) box.scrollTop = box.scrollHeight;
  }, [messages]);

  function onScroll() {
    const box = listRef.current;
    if (box) stickBottom.current = box.scrollHeight - box.scrollTop - box.clientHeight < 40;
  }

  // 이전 메시지 더 보기: 지금 맨 위 메시지보다 오래된 30개 · 보던 위치 유지
  async function loadOlder() {
    const oldest = messages[0]?.messageId;
    if (!oldest || loadingOld) return;
    const box = listRef.current;
    const before = box ? box.scrollHeight - box.scrollTop : 0;
    setLoadingOld(true);
    try {
      const list = await fetchConsultMessages(roomId, oldest);
      stickBottom.current = false;
      setMessages((prev) => mergeMessages(list, prev));
      setHasMore(list.length === PAGE_SIZE);
      requestAnimationFrame(() => {
        if (box) box.scrollTop = box.scrollHeight - before;
      });
    } catch (err) {
      notify(err.message);
    } finally {
      setLoadingOld(false);
    }
  }

  function submit(e) {
    e.preventDefault();
    const problem = checkContent(draft);
    if (problem) {
      setSendError(problem);
      return;
    }
    if (!socket.current?.send(draft)) {
      setSendError('아직 연결 중이에요. 잠시 후 다시 보내 주세요.');
      return;
    }
    setSendError('');
    setDraft('');
    stickBottom.current = true;
  }

  const length = contentLength(draft);
  return (
    <div className={styles.room}>
      <div className={styles.roomHead}>
        {onBack && (
          <button type="button" className="text-btn" onClick={onBack} aria-label="방 목록으로">
            <ArrowLeft size={16} />
          </button>
        )}
        <div>
          <h3>{title || '상담사와 1:1 채팅'}</h3>
          <small className={status === 'CONNECTED' ? styles.online : styles.offline}>
            {STATUS_TEXT[status] || status}
          </small>
        </div>
      </div>
      <div className={styles.messages} ref={listRef} onScroll={onScroll}>
        {hasMore && (
          <button type="button" className={styles.more} onClick={loadOlder} disabled={loadingOld}>
            {loadingOld ? '불러오는 중…' : '이전 메시지 더 보기'}
          </button>
        )}
        {!messages.length && (
          <p className={styles.hint}>
            {admin
              ? '아직 메시지가 없어요.'
              : '궁금한 점을 남겨 주세요. 상담사가 확인 후 답해 드려요.'}
          </p>
        )}
        {messages.map((m) => {
          const mine = isMine(m, myId);
          return (
            <div key={m.messageId} className={mine ? styles.mine : styles.other}>
              <span className={styles.sender}>{senderLabel(m, myId, admin)}</span>
              {/* 글자 그대로만 그림 (React 가 HTML 을 막아 줌 · 줄바꿈만 살림) */}
              <p className={styles.bubble}>{m.content}</p>
              <small className={styles.time}>{formatConsultTime(m.createdAt)}</small>
            </div>
          );
        })}
      </div>
      <form className={styles.form} onSubmit={submit}>
        <textarea
          aria-label="상담 메시지"
          rows={2}
          value={draft}
          placeholder="메시지를 입력하세요 (Enter 보내기 · Shift+Enter 줄바꿈)"
          onChange={(e) => {
            setDraft(e.target.value);
            if (sendError) setSendError('');
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) submit(e);
          }}
        />
        <button type="submit" aria-label="메시지 보내기" disabled={status !== 'CONNECTED'}>
          <Send size={16} />
        </button>
      </form>
      <div className={styles.formFoot}>
        <small className={styles.notice}>
          주민번호 · 계좌번호 · 카드번호는 입력하지 마세요. 사진 · 파일은 보낼 수 없어요.
        </small>
        <small className={length > CONTENT_MAX ? styles.over : styles.count}>
          {length} / {CONTENT_MAX}
        </small>
      </div>
      {sendError && <p className={styles.error}>{sendError}</p>}
    </div>
  );
}
