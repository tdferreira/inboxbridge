FROM node:24-alpine AS build
WORKDIR /app
ENV NODE_OPTIONS=--max-old-space-size=6144
ARG RUN_TESTS=false
COPY admin-ui/package.json ./
RUN npm install
COPY admin-ui/ ./
RUN if [ "$RUN_TESTS" = "true" ]; then CI=1 node ./scripts/run-vitest.mjs run --pool threads --maxWorkers=1; fi
RUN npm run build

FROM nginx:1.31.2-alpine
COPY admin-ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80 443
