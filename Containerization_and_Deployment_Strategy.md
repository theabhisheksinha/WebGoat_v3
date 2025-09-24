# WebGoat v3 Containerization and Deployment Strategy

## Executive Summary

This document outlines the comprehensive containerization and deployment strategy for migrating WebGoat v3 to a microservices architecture. Based on CAST Imaging analysis revealing 42 transactions and 7 architectural components, this strategy provides production-ready deployment patterns using Docker containers and Kubernetes orchestration.

## Container Strategy Overview

### Containerization Approach

- **Base Images**: Use official, security-hardened base images
- **Multi-stage Builds**: Optimize image size and security
- **Distroless Images**: Minimize attack surface for production
- **Image Scanning**: Automated vulnerability scanning in CI/CD
- **Registry Management**: Private container registry with image signing

### Container Architecture

```
WebGoat Microservices Container Ecosystem
├── Infrastructure Containers
│   ├── API Gateway (Kong)
│   ├── Service Mesh (Istio)
│   ├── Message Broker (Kafka)
│   └── Databases (PostgreSQL, Redis)
├── Application Containers
│   ├── Authentication Service
│   ├── Employee Management Service
│   ├── Lesson Management Service
│   └── Financial Services
└── Supporting Containers
    ├── Monitoring (Prometheus, Grafana)
    ├── Logging (ELK Stack)
    ├── Security (Vault, Falco)
    └── Backup Services
```

## Docker Implementation

### 1. Base Dockerfile Template

```dockerfile
# Multi-stage Dockerfile for WebGoat Microservices
# Stage 1: Build
FROM maven:3.9.4-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy dependency files first for better caching
COPY pom.xml .
COPY src/main/resources/application.yml src/main/resources/

# Download dependencies
RUN mvn dependency:go-offline -B

# Copy source code
COPY src ./src

# Build application
RUN mvn clean package -DskipTests -B

# Stage 2: Security scan
FROM builder AS security-scan
RUN mvn org.owasp:dependency-check-maven:check

# Stage 3: Runtime
FROM gcr.io/distroless/java17-debian11:nonroot AS runtime

# Create application user
USER nonroot:nonroot

# Copy application
COPY --from=builder --chown=nonroot:nonroot /app/target/*.jar /app/application.jar

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# Expose port
EXPOSE 8080

# Set JVM options for container
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+UseStringDeduplication"

# Run application
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
```

### 2. Service-Specific Dockerfiles

#### Authentication Service
```dockerfile
FROM webgoat/base-service:latest

# Service-specific configurations
ENV SERVICE_NAME=auth-service
ENV SERVICE_PORT=8080
ENV SPRING_PROFILES_ACTIVE=production

# Copy service JAR
COPY target/auth-service-*.jar /app/application.jar

# Security hardening
RUN addgroup --system --gid 1001 authgroup && \
    adduser --system --uid 1001 --gid 1001 authuser

USER authuser:authgroup

# Health check specific to auth service
HEALTHCHECK --interval=15s --timeout=5s --start-period=30s --retries=3 \
    CMD curl -f http://localhost:8080/auth/health || exit 1

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
```

#### Employee Service
```dockerfile
FROM webgoat/base-service:latest

ENV SERVICE_NAME=employee-service
ENV SERVICE_PORT=8081
ENV SPRING_PROFILES_ACTIVE=production

# Copy service JAR
COPY target/employee-service-*.jar /app/application.jar

# Create service user
RUN addgroup --system --gid 1002 empgroup && \
    adduser --system --uid 1002 --gid 1002 empuser

USER empuser:empgroup

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=45s --retries=3 \
    CMD curl -f http://localhost:8081/employees/health || exit 1

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
```

### 3. Docker Compose for Local Development

```yaml
# docker-compose.yml for local development
version: '3.8'

services:
  # Infrastructure Services
  postgres:
    image: postgres:15-alpine
    environment:
      POSTGRES_DB: webgoat
      POSTGRES_USER: webgoat
      POSTGRES_PASSWORD: ${DB_PASSWORD}
    volumes:
      - postgres_data:/var/lib/postgresql/data
      - ./scripts/init-db.sql:/docker-entrypoint-initdb.d/init-db.sql
    ports:
      - "5432:5432"
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U webgoat"]
      interval: 10s
      timeout: 5s
      retries: 5

  redis:
    image: redis:7-alpine
    command: redis-server --requirepass ${REDIS_PASSWORD}
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 3

  kafka:
    image: confluentinc/cp-kafka:latest
    environment:
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
    ports:
      - "9092:9092"
    depends_on:
      - zookeeper

  zookeeper:
    image: confluentinc/cp-zookeeper:latest
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000
    ports:
      - "2181:2181"

  # Application Services
  auth-service:
    build:
      context: ./services/auth-service
      dockerfile: Dockerfile
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: webgoat_auth
      DB_USERNAME: webgoat
      DB_PASSWORD: ${DB_PASSWORD}
      REDIS_HOST: redis
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
    ports:
      - "8080:8080"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:8080/auth/health"]
      interval: 30s
      timeout: 10s
      retries: 3

  employee-service:
    build:
      context: ./services/employee-service
      dockerfile: Dockerfile
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: webgoat_employee
      DB_USERNAME: webgoat
      DB_PASSWORD: ${DB_PASSWORD}
      KAFKA_BROKERS: kafka:9092
      AUTH_SERVICE_URL: http://auth-service:8080
    ports:
      - "8081:8081"
    depends_on:
      postgres:
        condition: service_healthy
      kafka:
        condition: service_started
      auth-service:
        condition: service_healthy

  lesson-service:
    build:
      context: ./services/lesson-service
      dockerfile: Dockerfile
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: webgoat_lessons
      DB_USERNAME: webgoat
      DB_PASSWORD: ${DB_PASSWORD}
      REDIS_HOST: redis
      REDIS_PASSWORD: ${REDIS_PASSWORD}
      KAFKA_BROKERS: kafka:9092
    ports:
      - "8082:8082"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
      kafka:
        condition: service_started

  financial-service:
    build:
      context: ./services/financial-service
      dockerfile: Dockerfile
    environment:
      SPRING_PROFILES_ACTIVE: docker
      DB_HOST: postgres
      DB_PORT: 5432
      DB_NAME: webgoat_financial
      DB_USERNAME: webgoat
      DB_PASSWORD: ${DB_PASSWORD}
      VAULT_URL: http://vault:8200
      VAULT_TOKEN: ${VAULT_TOKEN}
    ports:
      - "8083:8083"
    depends_on:
      postgres:
        condition: service_healthy
      vault:
        condition: service_started

  # API Gateway
  api-gateway:
    image: kong:3.4
    environment:
      KONG_DATABASE: "off"
      KONG_DECLARATIVE_CONFIG: /kong/declarative/kong.yml
      KONG_PROXY_ACCESS_LOG: /dev/stdout
      KONG_ADMIN_ACCESS_LOG: /dev/stdout
      KONG_PROXY_ERROR_LOG: /dev/stderr
      KONG_ADMIN_ERROR_LOG: /dev/stderr
      KONG_ADMIN_LISTEN: 0.0.0.0:8001
    volumes:
      - ./config/kong.yml:/kong/declarative/kong.yml
    ports:
      - "8000:8000"
      - "8001:8001"
    depends_on:
      - auth-service
      - employee-service
      - lesson-service
      - financial-service

  # Security
  vault:
    image: vault:1.15
    cap_add:
      - IPC_LOCK
    environment:
      VAULT_DEV_ROOT_TOKEN_ID: ${VAULT_TOKEN}
      VAULT_DEV_LISTEN_ADDRESS: 0.0.0.0:8200
    ports:
      - "8200:8200"

volumes:
  postgres_data:
  redis_data:

networks:
  default:
    name: webgoat-network
```

## Kubernetes Deployment Strategy

### 1. Cluster Architecture

```yaml
# Kubernetes Cluster Configuration
apiVersion: v1
kind: Namespace
metadata:
  name: webgoat-system
  labels:
    name: webgoat-system
    istio-injection: enabled
---
apiVersion: v1
kind: Namespace
metadata:
  name: webgoat-dev
  labels:
    name: webgoat-dev
    istio-injection: enabled
---
apiVersion: v1
kind: Namespace
metadata:
  name: webgoat-staging
  labels:
    name: webgoat-staging
    istio-injection: enabled
---
apiVersion: v1
kind: Namespace
metadata:
  name: webgoat-prod
  labels:
    name: webgoat-prod
    istio-injection: enabled
```

### 2. ConfigMaps and Secrets

```yaml
# Application Configuration
apiVersion: v1
kind: ConfigMap
metadata:
  name: webgoat-config
  namespace: webgoat-prod
data:
  application.yml: |
    server:
      port: 8080
      shutdown: graceful
    
    spring:
      application:
        name: ${SERVICE_NAME:webgoat-service}
      
      datasource:
        url: jdbc:postgresql://${DB_HOST:postgres}:${DB_PORT:5432}/${DB_NAME}
        username: ${DB_USERNAME}
        password: ${DB_PASSWORD}
        hikari:
          maximum-pool-size: 20
          minimum-idle: 5
          connection-timeout: 30000
          idle-timeout: 600000
          max-lifetime: 1800000
      
      jpa:
        hibernate:
          ddl-auto: validate
        show-sql: false
        properties:
          hibernate:
            dialect: org.hibernate.dialect.PostgreSQLDialect
            format_sql: true
      
      redis:
        host: ${REDIS_HOST:redis}
        port: ${REDIS_PORT:6379}
        password: ${REDIS_PASSWORD}
        timeout: 2000ms
        lettuce:
          pool:
            max-active: 8
            max-idle: 8
            min-idle: 0
      
      kafka:
        bootstrap-servers: ${KAFKA_BROKERS:kafka:9092}
        producer:
          key-serializer: org.apache.kafka.common.serialization.StringSerializer
          value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
          acks: all
          retries: 3
        consumer:
          group-id: ${SERVICE_NAME:webgoat-service}
          key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
          value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
          auto-offset-reset: earliest
    
    management:
      endpoints:
        web:
          exposure:
            include: health,info,metrics,prometheus
      endpoint:
        health:
          show-details: always
      metrics:
        export:
          prometheus:
            enabled: true
    
    logging:
      level:
        com.webgoat: INFO
        org.springframework.security: DEBUG
      pattern:
        console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
        file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
---
apiVersion: v1
kind: Secret
metadata:
  name: webgoat-secrets
  namespace: webgoat-prod
type: Opaque
data:
  db-password: <base64-encoded-password>
  redis-password: <base64-encoded-password>
  jwt-secret: <base64-encoded-jwt-secret>
  vault-token: <base64-encoded-vault-token>
```

### 3. Deployment Manifests

#### Authentication Service Deployment
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: auth-service
  namespace: webgoat-prod
  labels:
    app: auth-service
    version: v1
spec:
  replicas: 3
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  selector:
    matchLabels:
      app: auth-service
      version: v1
  template:
    metadata:
      labels:
        app: auth-service
        version: v1
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8080"
        prometheus.io/path: "/actuator/prometheus"
    spec:
      serviceAccountName: webgoat-service-account
      securityContext:
        runAsNonRoot: true
        runAsUser: 1001
        fsGroup: 1001
      containers:
      - name: auth-service
        image: webgoat/auth-service:v1.0.0
        imagePullPolicy: Always
        ports:
        - containerPort: 8080
          name: http
          protocol: TCP
        env:
        - name: SERVICE_NAME
          value: "auth-service"
        - name: SPRING_PROFILES_ACTIVE
          value: "kubernetes"
        - name: DB_HOST
          value: "postgres-service"
        - name: DB_PORT
          value: "5432"
        - name: DB_NAME
          value: "webgoat_auth"
        - name: DB_USERNAME
          value: "webgoat"
        - name: DB_PASSWORD
          valueFrom:
            secretKeyRef:
              name: webgoat-secrets
              key: db-password
        - name: REDIS_HOST
          value: "redis-service"
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: webgoat-secrets
              key: redis-password
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: webgoat-secrets
              key: jwt-secret
        volumeMounts:
        - name: config-volume
          mountPath: /app/config
          readOnly: true
        - name: tmp-volume
          mountPath: /tmp
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /auth/health
            port: 8080
          initialDelaySeconds: 60
          periodSeconds: 30
          timeoutSeconds: 10
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /auth/health
            port: 8080
          initialDelaySeconds: 30
          periodSeconds: 10
          timeoutSeconds: 5
          failureThreshold: 3
        securityContext:
          allowPrivilegeEscalation: false
          readOnlyRootFilesystem: true
          capabilities:
            drop:
            - ALL
      volumes:
      - name: config-volume
        configMap:
          name: webgoat-config
      - name: tmp-volume
        emptyDir: {}
      imagePullSecrets:
      - name: webgoat-registry-secret
---
apiVersion: v1
kind: Service
metadata:
  name: auth-service
  namespace: webgoat-prod
  labels:
    app: auth-service
spec:
  type: ClusterIP
  ports:
  - port: 8080
    targetPort: 8080
    protocol: TCP
    name: http
  selector:
    app: auth-service
```

### 4. Horizontal Pod Autoscaler

```yaml
apiVersion: autoscaling/v2
kind: HorizontalPodAutoscaler
metadata:
  name: auth-service-hpa
  namespace: webgoat-prod
spec:
  scaleTargetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: auth-service
  minReplicas: 3
  maxReplicas: 20
  metrics:
  - type: Resource
    resource:
      name: cpu
      target:
        type: Utilization
        averageUtilization: 70
  - type: Resource
    resource:
      name: memory
      target:
        type: Utilization
        averageUtilization: 80
  - type: Pods
    pods:
      metric:
        name: http_requests_per_second
      target:
        type: AverageValue
        averageValue: "100"
  behavior:
    scaleDown:
      stabilizationWindowSeconds: 300
      policies:
      - type: Percent
        value: 10
        periodSeconds: 60
      - type: Pods
        value: 2
        periodSeconds: 60
      selectPolicy: Min
    scaleUp:
      stabilizationWindowSeconds: 60
      policies:
      - type: Percent
        value: 50
        periodSeconds: 60
      - type: Pods
        value: 4
        periodSeconds: 60
      selectPolicy: Max
```

### 5. Network Policies

```yaml
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: webgoat-network-policy
  namespace: webgoat-prod
spec:
  podSelector:
    matchLabels:
      app: auth-service
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: istio-system
    - podSelector:
        matchLabels:
          app: api-gateway
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - podSelector:
        matchLabels:
          app: postgres
    ports:
    - protocol: TCP
      port: 5432
  - to:
    - podSelector:
        matchLabels:
          app: redis
    ports:
    - protocol: TCP
      port: 6379
  - to: []
    ports:
    - protocol: TCP
      port: 53
    - protocol: UDP
      port: 53
```

## CI/CD Pipeline Implementation

### 1. GitHub Actions Workflow

```yaml
name: WebGoat Microservices CI/CD

on:
  push:
    branches: [ main, develop ]
    paths:
      - 'services/**'
      - '.github/workflows/**'
  pull_request:
    branches: [ main ]
    paths:
      - 'services/**'

env:
  REGISTRY: ghcr.io
  IMAGE_NAME: webgoat

jobs:
  detect-changes:
    runs-on: ubuntu-latest
    outputs:
      services: ${{ steps.changes.outputs.services }}
    steps:
    - uses: actions/checkout@v4
    - uses: dorny/paths-filter@v2
      id: changes
      with:
        filters: |
          auth-service:
            - 'services/auth-service/**'
          employee-service:
            - 'services/employee-service/**'
          lesson-service:
            - 'services/lesson-service/**'
          financial-service:
            - 'services/financial-service/**'
        list-files: json
        base: ${{ github.ref }}

  test:
    needs: detect-changes
    if: ${{ needs.detect-changes.outputs.services != '[]' }}
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: ${{ fromJSON(needs.detect-changes.outputs.services) }}
    steps:
    - name: Checkout code
      uses: actions/checkout@v4

    - name: Set up JDK 17
      uses: actions/setup-java@v3
      with:
        java-version: '17'
        distribution: 'temurin'
        cache: maven

    - name: Cache SonarQube packages
      uses: actions/cache@v3
      with:
        path: ~/.sonar/cache
        key: ${{ runner.os }}-sonar
        restore-keys: ${{ runner.os }}-sonar

    - name: Run unit tests
      run: |
        cd services/${{ matrix.service }}
        mvn clean test -B

    - name: Run integration tests
      run: |
        cd services/${{ matrix.service }}
        mvn verify -P integration-tests -B

    - name: Security scan - OWASP Dependency Check
      run: |
        cd services/${{ matrix.service }}
        mvn org.owasp:dependency-check-maven:check -B

    - name: Security scan - Snyk
      uses: snyk/actions/maven@master
      env:
        SNYK_TOKEN: ${{ secrets.SNYK_TOKEN }}
      with:
        args: --severity-threshold=high --file=services/${{ matrix.service }}/pom.xml

    - name: SonarQube analysis
      env:
        GITHUB_TOKEN: ${{ secrets.GITHUB_TOKEN }}
        SONAR_TOKEN: ${{ secrets.SONAR_TOKEN }}
      run: |
        cd services/${{ matrix.service }}
        mvn sonar:sonar \
          -Dsonar.projectKey=webgoat_${{ matrix.service }} \
          -Dsonar.organization=webgoat \
          -Dsonar.host.url=https://sonarcloud.io \
          -Dsonar.login=${{ secrets.SONAR_TOKEN }}

    - name: Upload test results
      uses: actions/upload-artifact@v3
      if: always()
      with:
        name: test-results-${{ matrix.service }}
        path: |
          services/${{ matrix.service }}/target/surefire-reports/
          services/${{ matrix.service }}/target/failsafe-reports/

  build-and-push:
    needs: [detect-changes, test]
    if: ${{ needs.detect-changes.outputs.services != '[]' && github.ref == 'refs/heads/main' }}
    runs-on: ubuntu-latest
    strategy:
      matrix:
        service: ${{ fromJSON(needs.detect-changes.outputs.services) }}
    steps:
    - name: Checkout code
      uses: actions/checkout@v4

    - name: Set up Docker Buildx
      uses: docker/setup-buildx-action@v3

    - name: Log in to Container Registry
      uses: docker/login-action@v3
      with:
        registry: ${{ env.REGISTRY }}
        username: ${{ github.actor }}
        password: ${{ secrets.GITHUB_TOKEN }}

    - name: Extract metadata
      id: meta
      uses: docker/metadata-action@v5
      with:
        images: ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/${{ matrix.service }}
        tags: |
          type=ref,event=branch
          type=ref,event=pr
          type=sha,prefix={{branch}}-
          type=raw,value=latest,enable={{is_default_branch}}

    - name: Build and push Docker image
      uses: docker/build-push-action@v5
      with:
        context: ./services/${{ matrix.service }}
        file: ./services/${{ matrix.service }}/Dockerfile
        push: true
        tags: ${{ steps.meta.outputs.tags }}
        labels: ${{ steps.meta.outputs.labels }}
        cache-from: type=gha
        cache-to: type=gha,mode=max
        platforms: linux/amd64,linux/arm64

    - name: Sign container image
      run: |
        echo "${{ secrets.COSIGN_PRIVATE_KEY }}" > cosign.key
        cosign sign --key cosign.key ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/${{ matrix.service }}:${{ github.sha }}
        rm cosign.key
      env:
        COSIGN_PASSWORD: ${{ secrets.COSIGN_PASSWORD }}

    - name: Generate SBOM
      run: |
        syft ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/${{ matrix.service }}:${{ github.sha }} -o spdx-json > sbom-${{ matrix.service }}.json
        cosign attest --key cosign.key --predicate sbom-${{ matrix.service }}.json ${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/${{ matrix.service }}:${{ github.sha }}

  deploy-staging:
    needs: [detect-changes, build-and-push]
    if: ${{ needs.detect-changes.outputs.services != '[]' && github.ref == 'refs/heads/main' }}
    runs-on: ubuntu-latest
    environment: staging
    strategy:
      matrix:
        service: ${{ fromJSON(needs.detect-changes.outputs.services) }}
    steps:
    - name: Checkout code
      uses: actions/checkout@v4

    - name: Configure kubectl
      run: |
        echo "${{ secrets.KUBE_CONFIG }}" | base64 -d > kubeconfig
        export KUBECONFIG=kubeconfig

    - name: Deploy to staging
      run: |
        export KUBECONFIG=kubeconfig
        kubectl set image deployment/${{ matrix.service }} \
          ${{ matrix.service }}=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/${{ matrix.service }}:${{ github.sha }} \
          -n webgoat-staging
        
        kubectl rollout status deployment/${{ matrix.service }} -n webgoat-staging --timeout=300s

    - name: Run smoke tests
      run: |
        export KUBECONFIG=kubeconfig
        kubectl run smoke-test-${{ matrix.service }}-${{ github.run_number }} \
          --image=curlimages/curl:latest \
          --rm -i --restart=Never \
          --namespace=webgoat-staging \
          -- curl -f http://${{ matrix.service }}:8080/health

  deploy-production:
    needs: [detect-changes, deploy-staging]
    if: ${{ needs.detect-changes.outputs.services != '[]' && github.ref == 'refs/heads/main' }}
    runs-on: ubuntu-latest
    environment: production
    strategy:
      matrix:
        service: ${{ fromJSON(needs.detect-changes.outputs.services) }}
    steps:
    - name: Checkout code
      uses: actions/checkout@v4

    - name: Configure kubectl
      run: |
        echo "${{ secrets.PROD_KUBE_CONFIG }}" | base64 -d > kubeconfig
        export KUBECONFIG=kubeconfig

    - name: Blue-Green Deployment
      run: |
        export KUBECONFIG=kubeconfig
        
        # Create new deployment with green suffix
        kubectl patch deployment ${{ matrix.service }} \
          -p '{"spec":{"selector":{"matchLabels":{"version":"green"}},"template":{"metadata":{"labels":{"version":"green"}}}}}' \
          -n webgoat-prod
        
        kubectl set image deployment/${{ matrix.service }} \
          ${{ matrix.service }}=${{ env.REGISTRY }}/${{ env.IMAGE_NAME }}/${{ matrix.service }}:${{ github.sha }} \
          -n webgoat-prod
        
        # Wait for rollout
        kubectl rollout status deployment/${{ matrix.service }} -n webgoat-prod --timeout=600s
        
        # Update service to point to green version
        kubectl patch service ${{ matrix.service }} \
          -p '{"spec":{"selector":{"version":"green"}}}' \
          -n webgoat-prod
        
        # Health check
        sleep 30
        kubectl run health-check-${{ matrix.service }}-${{ github.run_number }} \
          --image=curlimages/curl:latest \
          --rm -i --restart=Never \
          --namespace=webgoat-prod \
          -- curl -f http://${{ matrix.service }}:8080/health

    - name: Cleanup old version
      run: |
        export KUBECONFIG=kubeconfig
        # Remove blue version after successful deployment
        kubectl delete pods -l app=${{ matrix.service }},version=blue -n webgoat-prod
```

### 2. Helm Charts for Deployment

```yaml
# Chart.yaml
apiVersion: v2
name: webgoat-microservices
description: A Helm chart for WebGoat microservices
type: application
version: 1.0.0
appVersion: "1.0.0"

dependencies:
- name: postgresql
  version: 12.x.x
  repository: https://charts.bitnami.com/bitnami
  condition: postgresql.enabled
- name: redis
  version: 17.x.x
  repository: https://charts.bitnami.com/bitnami
  condition: redis.enabled
- name: kafka
  version: 22.x.x
  repository: https://charts.bitnami.com/bitnami
  condition: kafka.enabled
```

```yaml
# values.yaml
global:
  imageRegistry: ghcr.io
  imagePullSecrets:
    - webgoat-registry-secret
  storageClass: "fast-ssd"

services:
  authService:
    enabled: true
    image:
      repository: webgoat/auth-service
      tag: "v1.0.0"
      pullPolicy: Always
    replicaCount: 3
    resources:
      requests:
        memory: "512Mi"
        cpu: "250m"
      limits:
        memory: "1Gi"
        cpu: "500m"
    autoscaling:
      enabled: true
      minReplicas: 3
      maxReplicas: 20
      targetCPUUtilizationPercentage: 70
      targetMemoryUtilizationPercentage: 80
    service:
      type: ClusterIP
      port: 8080
    ingress:
      enabled: true
      className: "nginx"
      annotations:
        nginx.ingress.kubernetes.io/rewrite-target: /
        cert-manager.io/cluster-issuer: "letsencrypt-prod"
      hosts:
        - host: api.webgoat.com
          paths:
            - path: /auth
              pathType: Prefix
      tls:
        - secretName: webgoat-tls
          hosts:
            - api.webgoat.com

  employeeService:
    enabled: true
    image:
      repository: webgoat/employee-service
      tag: "v1.0.0"
      pullPolicy: Always
    replicaCount: 2
    resources:
      requests:
        memory: "512Mi"
        cpu: "250m"
      limits:
        memory: "1Gi"
        cpu: "500m"
    autoscaling:
      enabled: true
      minReplicas: 2
      maxReplicas: 15
      targetCPUUtilizationPercentage: 70
    service:
      type: ClusterIP
      port: 8081

  lessonService:
    enabled: true
    image:
      repository: webgoat/lesson-service
      tag: "v1.0.0"
      pullPolicy: Always
    replicaCount: 3
    resources:
      requests:
        memory: "768Mi"
        cpu: "500m"
      limits:
        memory: "1.5Gi"
        cpu: "1000m"
    autoscaling:
      enabled: true
      minReplicas: 3
      maxReplicas: 25
      targetCPUUtilizationPercentage: 70
    service:
      type: ClusterIP
      port: 8082

  financialService:
    enabled: true
    image:
      repository: webgoat/financial-service
      tag: "v1.0.0"
      pullPolicy: Always
    replicaCount: 2
    resources:
      requests:
        memory: "512Mi"
        cpu: "250m"
      limits:
        memory: "1Gi"
        cpu: "500m"
    autoscaling:
      enabled: true
      minReplicas: 2
      maxReplicas: 10
      targetCPUUtilizationPercentage: 70
    service:
      type: ClusterIP
      port: 8083

# Infrastructure Dependencies
postgresql:
  enabled: true
  auth:
    postgresPassword: "secure-postgres-password"
    database: "webgoat"
  primary:
    persistence:
      enabled: true
      size: 100Gi
      storageClass: "fast-ssd"
    resources:
      requests:
        memory: "1Gi"
        cpu: "500m"
      limits:
        memory: "2Gi"
        cpu: "1000m"
  metrics:
    enabled: true
    serviceMonitor:
      enabled: true

redis:
  enabled: true
  auth:
    enabled: true
    password: "secure-redis-password"
  master:
    persistence:
      enabled: true
      size: 50Gi
      storageClass: "fast-ssd"
    resources:
      requests:
        memory: "512Mi"
        cpu: "250m"
      limits:
        memory: "1Gi"
        cpu: "500m"
  metrics:
    enabled: true
    serviceMonitor:
      enabled: true

kafka:
  enabled: true
  persistence:
    enabled: true
    size: 100Gi
    storageClass: "fast-ssd"
  zookeeper:
    persistence:
      enabled: true
      size: 20Gi
      storageClass: "fast-ssd"
  metrics:
    kafka:
      enabled: true
      serviceMonitor:
        enabled: true
    jmx:
      enabled: true
      serviceMonitor:
        enabled: true

# Monitoring
prometheus:
  enabled: true
  server:
    persistentVolume:
      enabled: true
      size: 100Gi
      storageClass: "fast-ssd"
    resources:
      requests:
        memory: "2Gi"
        cpu: "1000m"
      limits:
        memory: "4Gi"
        cpu: "2000m"

grafana:
  enabled: true
  persistence:
    enabled: true
    size: 10Gi
    storageClass: "fast-ssd"
  adminPassword: "secure-grafana-password"

# Security
vault:
  enabled: true
  server:
    ha:
      enabled: true
      replicas: 3
    dataStorage:
      enabled: true
      size: 50Gi
      storageClass: "fast-ssd"
```

## Monitoring and Observability

### 1. Prometheus Configuration

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: webgoat-system
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
      evaluation_interval: 15s
      external_labels:
        cluster: 'webgoat-prod'
        region: 'us-east-1'
    
    rule_files:
      - "/etc/prometheus/rules/*.yml"
    
    scrape_configs:
      # Kubernetes API Server
      - job_name: 'kubernetes-apiservers'
        kubernetes_sd_configs:
          - role: endpoints
        scheme: https
        tls_config:
          ca_file: /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
        bearer_token_file: /var/run/secrets/kubernetes.io/serviceaccount/token
        relabel_configs:
          - source_labels: [__meta_kubernetes_namespace, __meta_kubernetes_service_name, __meta_kubernetes_endpoint_port_name]
            action: keep
            regex: default;kubernetes;https
      
      # Kubernetes Nodes
      - job_name: 'kubernetes-nodes'
        kubernetes_sd_configs:
          - role: node
        scheme: https
        tls_config:
          ca_file: /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
        bearer_token_file: /var/run/secrets/kubernetes.io/serviceaccount/token
        relabel_configs:
          - action: labelmap
            regex: __meta_kubernetes_node_label_(.+)
          - target_label: __address__
            replacement: kubernetes.default.svc:443
          - source_labels: [__meta_kubernetes_node_name]
            regex: (.+)
            target_label: __metrics_path__
            replacement: /api/v1/nodes/${1}/proxy/metrics
      
      # WebGoat Services
      - job_name: 'webgoat-services'
        kubernetes_sd_configs:
          - role: endpoints
            namespaces:
              names:
                - webgoat-prod
                - webgoat-staging
        relabel_configs:
          - source_labels: [__meta_kubernetes_service_annotation_prometheus_io_scrape]
            action: keep
            regex: true
          - source_labels: [__meta_kubernetes_service_annotation_prometheus_io_path]
            action: replace
            target_label: __metrics_path__
            regex: (.+)
          - source_labels: [__address__, __meta_kubernetes_service_annotation_prometheus_io_port]
            action: replace
            regex: ([^:]+)(?::\d+)?;(\d+)
            replacement: $1:$2
            target_label: __address__
          - action: labelmap
            regex: __meta_kubernetes_service_label_(.+)
          - source_labels: [__meta_kubernetes_namespace]
            action: replace
            target_label: kubernetes_namespace
          - source_labels: [__meta_kubernetes_service_name]
            action: replace
            target_label: kubernetes_name
      
      # PostgreSQL
      - job_name: 'postgresql'
        static_configs:
          - targets: ['postgres-exporter:9187']
        scrape_interval: 30s
      
      # Redis
      - job_name: 'redis'
        static_configs:
          - targets: ['redis-exporter:9121']
        scrape_interval: 30s
      
      # Kafka
      - job_name: 'kafka'
        static_configs:
          - targets: ['kafka-exporter:9308']
        scrape_interval: 30s
    
    alerting:
      alertmanagers:
        - static_configs:
            - targets:
              - alertmanager:9093
---
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-rules
  namespace: webgoat-system
data:
  webgoat.yml: |
    groups:
    - name: webgoat.rules
      rules:
      # Service Health
      - alert: ServiceDown
        expr: up{job=~"webgoat-.*"} == 0
        for: 1m
        labels:
          severity: critical
        annotations:
          summary: "WebGoat service {{ $labels.kubernetes_name }} is down"
          description: "Service {{ $labels.kubernetes_name }} in namespace {{ $labels.kubernetes_namespace }} has been down for more than 1 minute."
      
      # High Error Rate
      - alert: HighErrorRate
        expr: |
          (
            rate(http_requests_total{job=~"webgoat-.*",status=~"5.."}[5m])
            /
            rate(http_requests_total{job=~"webgoat-.*"}[5m])
          ) * 100 > 5
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High error rate detected for {{ $labels.kubernetes_name }}"
          description: "Error rate is {{ $value }}% for service {{ $labels.kubernetes_name }}"
      
      # High Response Time
      - alert: HighResponseTime
        expr: |
          histogram_quantile(0.95, 
            rate(http_request_duration_seconds_bucket{job=~"webgoat-.*"}[5m])
          ) > 2
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High response time for {{ $labels.kubernetes_name }}"
          description: "95th percentile response time is {{ $value }}s for service {{ $labels.kubernetes_name }}"
      
      # High CPU Usage
      - alert: HighCPUUsage
        expr: |
          (
            rate(container_cpu_usage_seconds_total{pod=~".*webgoat.*"}[5m])
            /
            container_spec_cpu_quota{pod=~".*webgoat.*"} * container_spec_cpu_period{pod=~".*webgoat.*"}
          ) * 100 > 80
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High CPU usage for {{ $labels.pod }}"
          description: "CPU usage is {{ $value }}% for pod {{ $labels.pod }}"
      
      # High Memory Usage
      - alert: HighMemoryUsage
        expr: |
          (
            container_memory_working_set_bytes{pod=~".*webgoat.*"}
            /
            container_spec_memory_limit_bytes{pod=~".*webgoat.*"}
          ) * 100 > 85
        for: 10m
        labels:
          severity: warning
        annotations:
          summary: "High memory usage for {{ $labels.pod }}"
          description: "Memory usage is {{ $value }}% for pod {{ $labels.pod }}"
      
      # Database Connection Issues
      - alert: DatabaseConnectionFailure
        expr: increase(database_connection_errors_total[5m]) > 5
        for: 2m
        labels:
          severity: critical
        annotations:
          summary: "Database connection failures detected"
          description: "{{ $value }} database connection failures in the last 5 minutes"
      
      # Kafka Lag
      - alert: KafkaConsumerLag
        expr: kafka_consumer_lag_sum > 1000
        for: 5m
        labels:
          severity: warning
        annotations:
          summary: "High Kafka consumer lag"
          description: "Consumer lag is {{ $value }} messages for topic {{ $labels.topic }}"
```

### 2. Grafana Dashboards

```json
{
  "dashboard": {
    "id": null,
    "title": "WebGoat Microservices Overview",
    "tags": ["webgoat", "microservices"],
    "timezone": "browser",
    "panels": [
      {
        "id": 1,
        "title": "Service Health",
        "type": "stat",
        "targets": [
          {
            "expr": "up{job=~\"webgoat-.*\"}",
            "legendFormat": "{{ kubernetes_name }}"
          }
        ],
        "fieldConfig": {
          "defaults": {
            "color": {
              "mode": "thresholds"
            },
            "thresholds": {
              "steps": [
                {"color": "red", "value": 0},
                {"color": "green", "value": 1}
              ]
            }
          }
        }
      },
      {
        "id": 2,
        "title": "Request Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_requests_total{job=~\"webgoat-.*\"}[5m])",
            "legendFormat": "{{ kubernetes_name }}"
          }
        ]
      },
      {
        "id": 3,
        "title": "Response Time (95th percentile)",
        "type": "graph",
        "targets": [
          {
            "expr": "histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{job=~\"webgoat-.*\"}[5m]))",
            "legendFormat": "{{ kubernetes_name }}"
          }
        ]
      },
      {
        "id": 4,
        "title": "Error Rate",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(http_requests_total{job=~\"webgoat-.*\",status=~\"5..\"}[5m]) / rate(http_requests_total{job=~\"webgoat-.*\"}[5m]) * 100",
            "legendFormat": "{{ kubernetes_name }}"
          }
        ]
      },
      {
        "id": 5,
        "title": "CPU Usage",
        "type": "graph",
        "targets": [
          {
            "expr": "rate(container_cpu_usage_seconds_total{pod=~\".*webgoat.*\"}[5m]) * 100",
            "legendFormat": "{{ pod }}"
          }
        ]
      },
      {
        "id": 6,
        "title": "Memory Usage",
        "type": "graph",
        "targets": [
          {
            "expr": "container_memory_working_set_bytes{pod=~\".*webgoat.*\"} / 1024 / 1024",
            "legendFormat": "{{ pod }}"
          }
        ]
      }
    ],
    "time": {
      "from": "now-1h",
      "to": "now"
    },
    "refresh": "30s"
  }
}
```

## Security Implementation

### 1. Pod Security Standards

```yaml
apiVersion: v1
kind: Namespace
metadata:
  name: webgoat-prod
  labels:
    pod-security.kubernetes.io/enforce: restricted
    pod-security.kubernetes.io/audit: restricted
    pod-security.kubernetes.io/warn: restricted
```

### 2. RBAC Configuration

```yaml
apiVersion: v1
kind: ServiceAccount
metadata:
  name: webgoat-service-account
  namespace: webgoat-prod
---
apiVersion: rbac.authorization.k8s.io/v1
kind: Role
metadata:
  namespace: webgoat-prod
  name: webgoat-role
rules:
- apiGroups: [""]
  resources: ["configmaps", "secrets"]
  verbs: ["get", "list"]
- apiGroups: [""]
  resources: ["pods"]
  verbs: ["get", "list"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: RoleBinding
metadata:
  name: webgoat-role-binding
  namespace: webgoat-prod
subjects:
- kind: ServiceAccount
  name: webgoat-service-account
  namespace: webgoat-prod
roleRef:
  kind: Role
  name: webgoat-role
  apiGroup: rbac.authorization.k8s.io
```

### 3. Falco Security Rules

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: falco-rules
  namespace: webgoat-system
data:
  webgoat_rules.yaml: |
    - rule: Unauthorized Process in WebGoat Container
      desc: Detect unauthorized processes in WebGoat containers
      condition: >
        spawned_process and
        container and
        k8s.ns.name in (webgoat-prod, webgoat-staging) and
        not proc.name in (java, sh, bash, curl, wget)
      output: >
        Unauthorized process in WebGoat container
        (user=%user.name command=%proc.cmdline container=%container.name
        image=%container.image.repository:%container.image.tag)
      priority: WARNING
      tags: [webgoat, process]
    
    - rule: Sensitive File Access in WebGoat
      desc: Detect access to sensitive files in WebGoat containers
      condition: >
        open_read and
        container and
        k8s.ns.name in (webgoat-prod, webgoat-staging) and
        (fd.name startswith /etc/passwd or
         fd.name startswith /etc/shadow or
         fd.name startswith /etc/ssh/ or
         fd.name contains id_rsa or
         fd.name contains id_dsa)
      output: >
        Sensitive file access in WebGoat container
        (user=%user.name file=%fd.name container=%container.name
        image=%container.image.repository:%container.image.tag)
      priority: WARNING
      tags: [webgoat, filesystem]
    
    - rule: Network Connection from WebGoat to External
      desc: Detect unexpected network connections from WebGoat
      condition: >
        outbound and
        container and
        k8s.ns.name in (webgoat-prod, webgoat-staging) and
        not fd.sip in (10.0.0.0/8, 172.16.0.0/12, 192.168.0.0/16) and
        not fd.sport in (80, 443, 53)
      output: >
        Unexpected network connection from WebGoat
        (user=%user.name connection=%fd.name container=%container.name
        image=%container.image.repository:%container.image.tag)
      priority: NOTICE
      tags: [webgoat, network]
```

## Disaster Recovery and Backup

### 1. Backup Strategy

```bash
#!/bin/bash
# WebGoat Backup Script

set -euo pipefail

BACKUP_DATE=$(date +%Y%m%d_%H%M%S)
BACKUP_DIR="/backups/webgoat/${BACKUP_DATE}"
S3_BUCKET="webgoat-backups"
RETENTION_DAYS=30

echo "Starting WebGoat backup at $(date)"

# Create backup directory
mkdir -p "${BACKUP_DIR}"

# Database backups
echo "Backing up databases..."
for db in webgoat_auth webgoat_employee webgoat_lessons webgoat_financial; do
    echo "Backing up ${db}..."
    kubectl exec -n webgoat-prod deployment/postgresql -- \
        pg_dump -U webgoat "${db}" | gzip > "${BACKUP_DIR}/${db}.sql.gz"
done

# Redis backup
echo "Backing up Redis..."
kubectl exec -n webgoat-prod deployment/redis -- \
    redis-cli --rdb /tmp/dump.rdb
kubectl cp webgoat-prod/redis-0:/tmp/dump.rdb "${BACKUP_DIR}/redis-dump.rdb"

# Kubernetes manifests
echo "Backing up Kubernetes manifests..."
kubectl get all,configmaps,secrets,pvc -n webgoat-prod -o yaml > "${BACKUP_DIR}/k8s-manifests.yaml"

# Application logs
echo "Backing up application logs..."
kubectl logs -n webgoat-prod -l app=auth-service --tail=10000 > "${BACKUP_DIR}/auth-service.log"
kubectl logs -n webgoat-prod -l app=employee-service --tail=10000 > "${BACKUP_DIR}/employee-service.log"
kubectl logs -n webgoat-prod -l app=lesson-service --tail=10000 > "${BACKUP_DIR}/lesson-service.log"
kubectl logs -n webgoat-prod -l app=financial-service --tail=10000 > "${BACKUP_DIR}/financial-service.log"

# Create archive
echo "Creating backup archive..."
tar -czf "webgoat-backup-${BACKUP_DATE}.tar.gz" -C "/backups/webgoat" "${BACKUP_DATE}"

# Upload to S3
echo "Uploading to S3..."
aws s3 cp "webgoat-backup-${BACKUP_DATE}.tar.gz" "s3://${S3_BUCKET}/"

# Cleanup local files
rm -rf "${BACKUP_DIR}"
rm "webgoat-backup-${BACKUP_DATE}.tar.gz"

# Cleanup old backups
echo "Cleaning up old backups..."
aws s3 ls "s3://${S3_BUCKET}/" | \
    awk '{print $4}' | \
    grep "webgoat-backup-" | \
    sort | \
    head -n -${RETENTION_DAYS} | \
    xargs -I {} aws s3 rm "s3://${S3_BUCKET}/{}"

echo "Backup completed successfully at $(date)"
```

### 2. Disaster Recovery Plan

```bash
#!/bin/bash
# WebGoat Disaster Recovery Script

set -euo pipefail

BACKUP_FILE="$1"
RECOVERY_NAMESPACE="webgoat-recovery"

if [ -z "${BACKUP_FILE}" ]; then
    echo "Usage: $0 <backup-file>"
    exit 1
fi

echo "Starting disaster recovery from ${BACKUP_FILE}"

# Create recovery namespace
kubectl create namespace "${RECOVERY_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

# Download backup from S3
echo "Downloading backup..."
aws s3 cp "s3://webgoat-backups/${BACKUP_FILE}" .

# Extract backup
echo "Extracting backup..."
tar -xzf "${BACKUP_FILE}"
BACKUP_DIR=$(echo "${BACKUP_FILE}" | sed 's/.tar.gz$//')

# Restore databases
echo "Restoring databases..."
for db in webgoat_auth webgoat_employee webgoat_lessons webgoat_financial; do
    echo "Restoring ${db}..."
    kubectl exec -n "${RECOVERY_NAMESPACE}" deployment/postgresql -- \
        createdb -U webgoat "${db}" || true
    gunzip -c "${BACKUP_DIR}/${db}.sql.gz" | \
        kubectl exec -i -n "${RECOVERY_NAMESPACE}" deployment/postgresql -- \
        psql -U webgoat "${db}"
done

# Restore Redis
echo "Restoring Redis..."
kubectl cp "${BACKUP_DIR}/redis-dump.rdb" "${RECOVERY_NAMESPACE}/redis-0:/tmp/dump.rdb"
kubectl exec -n "${RECOVERY_NAMESPACE}" deployment/redis -- \
    redis-cli --rdb /tmp/dump.rdb

# Apply Kubernetes manifests
echo "Applying Kubernetes manifests..."
sed "s/namespace: webgoat-prod/namespace: ${RECOVERY_NAMESPACE}/g" "${BACKUP_DIR}/k8s-manifests.yaml" | \
    kubectl apply -f -

# Wait for services to be ready
echo "Waiting for services to be ready..."
kubectl wait --for=condition=ready pod -l app=auth-service -n "${RECOVERY_NAMESPACE}" --timeout=300s
kubectl wait --for=condition=ready pod -l app=employee-service -n "${RECOVERY_NAMESPACE}" --timeout=300s
kubectl wait --for=condition=ready pod -l app=lesson-service -n "${RECOVERY_NAMESPACE}" --timeout=300s
kubectl wait --for=condition=ready pod -l app=financial-service -n "${RECOVERY_NAMESPACE}" --timeout=300s

# Verify recovery
echo "Verifying recovery..."
for service in auth-service employee-service lesson-service financial-service; do
    kubectl run test-${service}-$(date +%s) \
        --image=curlimages/curl:latest \
        --rm -i --restart=Never \
        --namespace="${RECOVERY_NAMESPACE}" \
        -- curl -f "http://${service}:8080/health"
done

echo "Disaster recovery completed successfully"
```

## Performance Optimization

### 1. JVM Tuning for Containers

```dockerfile
# Optimized JVM settings for containerized Java applications
ENV JAVA_OPTS="\
    -XX:+UseContainerSupport \
    -XX:MaxRAMPercentage=75.0 \
    -XX:+UseG1GC \
    -XX:+UseStringDeduplication \
    -XX:+OptimizeStringConcat \
    -XX:+UseCompressedOops \
    -XX:+UseCompressedClassPointers \
    -XX:+TieredCompilation \
    -XX:TieredStopAtLevel=1 \
    -XX:+UnlockExperimentalVMOptions \
    -XX:+UseCGroupMemoryLimitForHeap \
    -Djava.security.egd=file:/dev/./urandom \
    -Dspring.jmx.enabled=false \
    -Dspring.main.lazy-initialization=true"
```

### 2. Resource Optimization

```yaml
# Vertical Pod Autoscaler
apiVersion: autoscaling.k8s.io/v1
kind: VerticalPodAutoscaler
metadata:
  name: auth-service-vpa
  namespace: webgoat-prod
spec:
  targetRef:
    apiVersion: apps/v1
    kind: Deployment
    name: auth-service
  updatePolicy:
    updateMode: "Auto"
  resourcePolicy:
    containerPolicies:
    - containerName: auth-service
      minAllowed:
        cpu: 100m
        memory: 256Mi
      maxAllowed:
        cpu: 2
        memory: 2Gi
      controlledResources: ["cpu", "memory"]
```

### 3. Caching Strategy

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: redis-cache-config
  namespace: webgoat-prod
data:
  redis.conf: |
    # Memory optimization
    maxmemory 1gb
    maxmemory-policy allkeys-lru
    
    # Persistence
    save 900 1
    save 300 10
    save 60 10000
    
    # Network
    tcp-keepalive 300
    timeout 0
    
    # Performance
    hash-max-ziplist-entries 512
    hash-max-ziplist-value 64
    list-max-ziplist-size -2
    set-max-intset-entries 512
    zset-max-ziplist-entries 128
    zset-max-ziplist-value 64
```

## Success Metrics and KPIs

### 1. Technical Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Service Availability | 99.9% | Uptime monitoring |
| Response Time (P95) | < 500ms | Application metrics |
| Error Rate | < 0.1% | HTTP status codes |
| Container Start Time | < 30s | Kubernetes events |
| Resource Utilization | 60-80% | Prometheus metrics |
| Database Connection Pool | < 80% | Connection metrics |

### 2. Business Metrics

| Metric | Target | Measurement |
|--------|--------|-------------|
| Deployment Frequency | Daily | CI/CD pipeline |
| Lead Time | < 2 hours | Git to production |
| Mean Time to Recovery | < 15 minutes | Incident response |
| Change Failure Rate | < 5% | Rollback frequency |
| Security Scan Pass Rate | 100% | Vulnerability scans |

### 3. Monitoring Dashboard

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: webgoat-sli-dashboard
  namespace: webgoat-system
data:
  dashboard.json: |
    {
      "dashboard": {
        "title": "WebGoat SLI/SLO Dashboard",
        "panels": [
          {
            "title": "Service Level Indicators",
            "type": "stat",
            "targets": [
              {
                "expr": "avg_over_time(up{job=~\"webgoat-.*\"}[24h]) * 100",
                "legendFormat": "Availability %"
              },
              {
                "expr": "histogram_quantile(0.95, rate(http_request_duration_seconds_bucket{job=~\"webgoat-.*\"}[5m])) * 1000",
                "legendFormat": "P95 Latency (ms)"
              },
              {
                "expr": "(rate(http_requests_total{job=~\"webgoat-.*\",status=~\"5..\"}[5m]) / rate(http_requests_total{job=~\"webgoat-.*\"}[5m])) * 100",
                "legendFormat": "Error Rate %"
              }
            ]
          }
        ]
      }
    }
```

## Conclusion

This comprehensive containerization and deployment strategy provides:

1. **Production-Ready Containers**: Multi-stage builds with security hardening
2. **Scalable Kubernetes Architecture**: Auto-scaling and resource optimization
3. **Robust CI/CD Pipeline**: Automated testing, security scanning, and deployment
4. **Comprehensive Monitoring**: Prometheus, Grafana, and alerting
5. **Security Implementation**: Pod security standards, RBAC, and runtime protection
6. **Disaster Recovery**: Automated backup and recovery procedures
7. **Performance Optimization**: JVM tuning and resource management

The strategy supports the migration of WebGoat v3's 42 transactions across 7 architectural components into a resilient, scalable microservices platform ready for production deployment.

### Next Steps

1. Set up Kubernetes cluster with required infrastructure
2. Implement CI/CD pipeline with security scanning
3. Deploy services incrementally using blue-green deployment
4. Configure monitoring and alerting
5. Conduct load testing and performance optimization
6. Train operations team on new deployment procedures
7. Establish incident response procedures

This deployment strategy ensures a smooth transition to microservices while maintaining high availability, security, and performance standards.