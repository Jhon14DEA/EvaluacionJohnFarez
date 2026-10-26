# Dockerfile multi-stage del frontend Angular.
# Compila el bundle de producción y lo sirve con nginx, que además hace de reverse proxy
# de /api hacia el backend para que el navegador hable con un mismo origen.

# --- Etapa de build: instala dependencias exactas y genera el bundle de producción ---
FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build -- --configuration production

# --- Etapa de runtime: nginx sirve los estáticos y proxya /api ---
FROM nginx:1.27-alpine AS runtime
COPY nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/evaluacion-john-farez-frontend/browser/ /usr/share/nginx/html/
EXPOSE 80
