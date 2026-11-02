# Built by CI after `./mvnw package`; the jar is never built on the VPS.
FROM eclipse-temurin:21-jre-alpine AS extract
WORKDIR /build
COPY target/app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --launcher --destination extracted

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S app && adduser -S app -G app
USER app
WORKDIR /app
COPY --from=extract /build/extracted/dependencies/ ./
COPY --from=extract /build/extracted/spring-boot-loader/ ./
COPY --from=extract /build/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/extracted/application/ ./

# Tuned for a 1 GB VPS: ~300 MB resident.
# Production profile by default; override with SPRING_PROFILES_ACTIVE only for non-production runs.
ENV SPRING_PROFILES_ACTIVE=prod
ENV JAVA_OPTS="-Xmx200m -Xss512k -XX:MaxMetaspaceSize=128m -XX:+UseSerialGC -XX:TieredStopAtLevel=1 -XX:+ExitOnOutOfMemoryError"
EXPOSE 8080
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]
