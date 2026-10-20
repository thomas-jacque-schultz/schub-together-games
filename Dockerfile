# Les pages des jeux (web/), puis le relais qui les sert avec /ws : une image pour tous les mini-jeux.
FROM node:22-alpine AS web
WORKDIR /web
COPY web/package*.json ./
RUN npm ci
COPY web/ ./
RUN npm run build

FROM eclipse-temurin:21-jre
ARG JAR_FILE=target/*.jar
COPY ${JAR_FILE} app.jar
COPY --from=web /web/dist /srv/games
ENV GAMES_FILES=/srv/games
ENTRYPOINT ["java","-jar","/app.jar"]
