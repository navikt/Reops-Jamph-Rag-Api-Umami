# Multi-stage build for Jamph-Rag-Api-Umami
# Stage 1: Build with Maven
FROM maven:3.9-eclipse-temurin-21 AS builder

WORKDIR /build

# Copy pom.xml and download dependencies (cached layer)
COPY pom.xml ./
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true -B

# Extract ollamaUrl from routes.json into a .env file for the runtime stage
RUN OLLAMA_URL=$(sed -n 's/.*"ollamaUrl": *"\([^"]*\)".*/\1/p' src/main/resources/routes.json) && \
    echo "OLLAMA_BASE_URL=${OLLAMA_URL}" > /tmp/routes.env

# Stage 2: Runtime
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Install system packages for healthcheck and networking
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
    wget \
    curl \
    ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# Copy the fat JAR and routes defaults from builder stage
COPY --from=builder /build/target/api-1.0-SNAPSHOT-jar-with-dependencies.jar ./app.jar
COPY --from=builder /tmp/routes.env ./routes.env
COPY entrypoint.sh ./entrypoint.sh
RUN chmod +x ./entrypoint.sh

# Expose API port
EXPOSE 8004

# Environment variables with defaults (OLLAMA_BASE_URL is sourced from routes.env at runtime)
ENV API_PORT=8004 \
    API_HOST=0.0.0.0 \
    OLLAMA_MODEL=llama3.2:3b

# Run the application
ENTRYPOINT ["/app/entrypoint.sh"]
