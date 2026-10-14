# Front-end CodeShield (TanStack Start, SSR) servi par Bun.
FROM oven/bun:1.3-alpine AS build
WORKDIR /app
COPY package.json bun.lock ./
RUN bun install --frozen-lockfile
COPY . .
# L'URL de l'API est intégrée au build (variable Vite).
ARG VITE_API_URL=http://localhost:8000
ENV VITE_API_URL=$VITE_API_URL
# Installeurs servis ailleurs (GitHub Releases, S3…) ; défaut : /downloads via serve.ts.
ARG VITE_DOWNLOAD_URL=/downloads
ENV VITE_DOWNLOAD_URL=$VITE_DOWNLOAD_URL
RUN bun run build

FROM oven/bun:1.3-alpine
WORKDIR /app
COPY --from=build /app/dist ./dist
COPY --from=build /app/serve.ts ./serve.ts
# Installeurs de l'app desktop : montez un volume ici (ex. -v ./downloads:/app/downloads:ro).
RUN mkdir -p /app/downloads
ENV DOWNLOADS_DIR=/app/downloads
USER bun
EXPOSE 3000
ENV PORT=3000
CMD ["bun", "run", "serve.ts"]
