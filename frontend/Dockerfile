# --- Build stage -------------------------------------------------------------
FROM node:20-alpine AS build
WORKDIR /workspace

# Cache dependencies separately from source changes.
COPY package.json package-lock.json ./
RUN npm ci

COPY . .
RUN npm run build

# --- Runtime stage -----------------------------------------------------------
FROM nginx:1.27-alpine

COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/documind/browser /usr/share/nginx/html

EXPOSE 80

HEALTHCHECK --interval=20s --timeout=3s --start-period=10s --retries=5 \
  CMD wget -qO- http://127.0.0.1/ > /dev/null || exit 1
