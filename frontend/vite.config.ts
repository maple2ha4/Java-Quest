import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
const codespaceHost = process.env.CODESPACE_NAME
  ? `${process.env.CODESPACE_NAME}-${process.env.FRONTEND_PORT || 5173}.${process.env.GITHUB_CODESPACES_PORT_FORWARDING_DOMAIN || 'app.github.dev'}`
  : undefined
export default defineConfig({
  plugins: [vue()],
  server: {
    port: Number(process.env.FRONTEND_PORT || 5173),
    allowedHosts: codespaceHost ? [codespaceHost] : [],
    hmr: codespaceHost ? { host: codespaceHost, protocol: 'wss', clientPort: 443 } : undefined,
    strictPort: true,
    proxy: { '/api': process.env.API_PROXY_TARGET || 'http://127.0.0.1:8080' },
  },
  build: { chunkSizeWarningLimit: 3000 },
})
