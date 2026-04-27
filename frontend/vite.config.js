import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  define: {
    global: 'globalThis'
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
      '/ws':  { target: 'http://localhost:8080', ws: true }
    }
  }
})
