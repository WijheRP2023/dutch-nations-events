FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY . .
RUN chmod +x gradlew && ./gradlew -p server installDist --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/server/build/install/dutch-nations-api/ /app/
ENV DN_BIND=0.0.0.0
ENV DN_PORT=8787
ENV DN_DATA_FILE=/data/state.json
VOLUME ["/data"]
EXPOSE 8787
ENTRYPOINT ["/app/bin/dutch-nations-api"]
