# JadeBase Frontend

JadeBase 的独立 Web 工作台，使用 Vite 构建，并通过同源 `/api` 和 `/actuator` 路径访问后端。

## 本地开发

先在仓库根目录启动 Spring Boot 后端，然后执行：

```bash
corepack enable
pnpm install
pnpm dev
```

访问 <http://localhost:5173>。默认代理到 <http://localhost:8080>，可在 `.env.local` 中通过
`VITE_BACKEND_PROXY_TARGET` 修改。

## 生产构建

```bash
pnpm build
pnpm preview
```

根目录的 Docker Compose 会构建本目录的 Nginx 镜像，并将接口请求代理到后端容器。
