FROM node:24.11.0-alpine AS builder
WORKDIR /app
COPY package*.json ./
RUN npm ci
COPY . .
RUN npm run build
FROM nginxinc/nginx-unprivileged:1.28.0-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY proxy_params.network /etc/nginx/proxy_params.network
COPY --from=builder /app/dist /usr/share/nginx/html
EXPOSE 8080
