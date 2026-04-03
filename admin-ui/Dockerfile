FROM node:25-alpine AS build
WORKDIR /app
ENV NODE_OPTIONS=--max-old-space-size=6144
ARG RUN_TESTS=true
COPY admin-ui/package.json ./
RUN npm install
COPY admin-ui/ ./
RUN if [ "$RUN_TESTS" = "true" ]; then npm run test:run; fi
RUN npm run build

FROM nginx:1.29-alpine
COPY admin-ui/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 80 443
