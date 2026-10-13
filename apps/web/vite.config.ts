import { defineConfig } from 'vite';
import vue from '@vitejs/plugin-vue';
export default defineConfig({
  plugins: [vue()],
  base: './',
  server: { host: '127.0.0.1', proxy: { '/api': { target: process.env.API_PROXY_TARGET || 'http://127.0.0.1:8080', changeOrigin: false } } },
  build: { target: 'es2022', chunkSizeWarningLimit: 1200, rolldownOptions: { output: { codeSplitting: { groups: [
    { name: 'vue', test: /node_modules\/(vue|@vue|pinia|vue-router)\// },
    { name: 'charts', test: /node_modules\/(echarts|zrender)\// },
    { name: 'maps', test: /node_modules\/ol\// },
    { name: 'element', test: /node_modules\/element-plus\// }
  ] } } } }
});
