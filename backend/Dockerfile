FROM node:18-alpine AS builder

WORKDIR /app

# Copier les fichiers de dépendances
COPY package*.json ./
COPY prisma ./prisma/

# Installer les dépendances
RUN npm install

# Générer le client Prisma
RUN npx prisma generate

# Copier le reste du code
COPY . .

# Build l'application NestJS
RUN npm run build

# Étape de production
FROM node:18-alpine

WORKDIR /app

COPY --from=builder /app/node_modules ./node_modules
COPY --from=builder /app/package*.json ./
COPY --from=builder /app/dist ./dist
COPY --from=builder /app/prisma ./prisma

# Exposer le port
EXPOSE 3000

# Démarrer l'application (en appliquant les migrations db push en prod - à adapter selon besoin)
CMD ["sh", "-c", "npx prisma db push && npm run start:prod"]
