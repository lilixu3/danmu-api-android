FROM node:24-alpine

WORKDIR /app

COPY package.json package-lock.json ./
RUN npm ci --omit=dev

COPY public ./public
COPY src ./src
COPY views ./views

ENV NODE_ENV=production

CMD ["node", "src/server.js"]
