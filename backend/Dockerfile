# Runtime image for the QueryFlow backend.
# Build the jar first (keeps the image build free of network/Maven access):
#   cd backend && ./mvnw -DskipTests package
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/backend-*.jar /app/queryflow.jar
EXPOSE 8080
HEALTHCHECK --interval=5s --timeout=3s --start-period=20s --retries=30 \
  CMD curl -fs http://localhost:8080/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/queryflow.jar"]
