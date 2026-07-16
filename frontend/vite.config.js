import { defineConfig, loadEnv } from 'vite';

export default defineConfig(({ mode }) => {
    const env = loadEnv(mode, process.cwd(), '');
    const backend = env.VITE_BACKEND_PROXY_TARGET || 'http://localhost:8080';

    return {
        server: {
            port: 5173,
            proxy: {
                '/api': { target: backend, changeOrigin: true },
                '/actuator': { target: backend, changeOrigin: true }
            }
        },
        preview: {
            port: 4173
        }
    };
});
