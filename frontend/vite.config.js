import { defineConfig, loadEnv } from 'vite';
import react from '@vitejs/plugin-react';
import { fileURLToPath, URL } from 'node:url';

// 배포 전용 런타임을 일반 Vite로 바꾸고 원본의 @ 경로 별칭을 유지합니다.
// 개발 서버 프록시: 화면이 부르는 /api/... 를 Spring 서버로 넘겨요 (브라우저 CORS 막힘 방지 · 개발용)
// 주소는 .env의 VITE_API_PROXY (없으면 http://localhost:8080)
// 넘길 때 Origin 헤더를 뗌: 팀 서버처럼 다른 PC에서 http://192.168.x.x:5173 으로 접속하면
//   브라우저가 POST · PUT · DELETE에 Origin을 붙이는데, back CorsConfig는 localhost:5173만 허용해서 403이 남
//   프록시를 거치면 back 입장에서는 같은 주소 요청이라 Origin이 필요 없음 (개발 서버 전용 · 배포에는 영향 없음)
export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), 'VITE_');
  return {
    plugins: [react()],
    server: {
      allowedHosts: ['terminal.local'],
      proxy: {
        '/api': {
          target: env.VITE_API_PROXY || 'http://localhost:8080',
          changeOrigin: true,
          configure: (proxy) => {
            proxy.on('proxyReq', (proxyReq) => proxyReq.removeHeader('origin'));
          },
        },
        // [추가 11/24 · 이우진 · TT-154] 상담 채팅 WebSocket(/ws · STOMP)도 Spring 서버로 넘김 (ws: true)
        // /api 와 같은 이유로 Origin 헤더를 뗌 → back WebSocketConfig 는 localhost:5173만 허용해서
        //   다른 PC 주소로 접속하면 연결이 막힘 (Origin이 없으면 같은 주소 요청으로 봄 · 개발 서버 전용)
        '/ws': {
          target: env.VITE_API_PROXY || 'http://localhost:8080',
          changeOrigin: true,
          ws: true,
          configure: (proxy) => {
            proxy.on('proxyReqWs', (proxyReq) => proxyReq.removeHeader('origin'));
          },
        },
      },
    },
    resolve: { alias: { '@': fileURLToPath(new URL('./src', import.meta.url)) } },
  };
});
