# WebGoat v3 API Design and Communication Strategy

## Overview

This document defines the API architecture, communication patterns, and integration strategies for the WebGoat v3 microservices migration. Based on CAST Imaging analysis showing 42 transactions and complex inter-service dependencies, this design ensures scalable, secure, and maintainable service communication.

## Current API Analysis (CAST Imaging)

### Transaction Patterns
- **Total Transactions**: 42 identified business flows
- **Technology Stack**: Java, JSP, JavaScript, SQL, Azure SDK
- **Entry Points**: 20+ JSP pages (Login.jsp, EditProfile.jsp, ListStaff.jsp, etc.)
- **Communication Types**: HTTP requests, database calls, SOAP services

### Identified Communication Flows
```
Web Presentation (68 objects) → Web Coordination (7 objects)
Web Coordination → Business Logic (313 objects)
Business Logic → Data Access Services (52 objects)
Data Access Services → RDBMS Services (31 objects)
```

## API Gateway Architecture

### 1. API Gateway Selection: Kong Gateway

#### Gateway Configuration
```yaml
# kong.yml
_format_version: "3.0"
_transform: true

services:
  - name: auth-service
    url: http://auth-service:8080
    plugins:
      - name: rate-limiting
        config:
          minute: 100
          hour: 1000
      - name: jwt
        config:
          secret_is_base64: false
          key_claim_name: iss

  - name: employee-service
    url: http://employee-service:8080
    plugins:
      - name: rate-limiting
        config:
          minute: 200
          hour: 2000
      - name: jwt
      - name: cors
        config:
          origins:
            - "https://webgoat.local"
          methods:
            - GET
            - POST
            - PUT
            - DELETE
          headers:
            - Accept
            - Authorization
            - Content-Type

  - name: lesson-service
    url: http://lesson-service:8080
    plugins:
      - name: rate-limiting
        config:
          minute: 500
          hour: 5000
      - name: jwt

  - name: financial-service
    url: http://financial-service:8080
    plugins:
      - name: rate-limiting
        config:
          minute: 50
          hour: 500
      - name: jwt
      - name: request-size-limiting
        config:
          allowed_payload_size: 1

  - name: content-service
    url: http://content-service:8080
    plugins:
      - name: rate-limiting
        config:
          minute: 1000
          hour: 10000
      - name: jwt

routes:
  - name: auth-routes
    service: auth-service
    paths:
      - "/api/v1/auth"
    strip_path: true

  - name: employee-routes
    service: employee-service
    paths:
      - "/api/v1/employees"
    strip_path: true

  - name: lesson-routes
    service: lesson-service
    paths:
      - "/api/v1/lessons"
    strip_path: true

  - name: financial-routes
    service: financial-service
    paths:
      - "/api/v1/financial"
    strip_path: true

  - name: content-routes
    service: content-service
    paths:
      - "/api/v1/content"
    strip_path: true

consumers:
  - username: webgoat-frontend
    jwt_secrets:
      - key: webgoat-frontend-key
        secret: "your-256-bit-secret"
```

### 2. API Gateway Features

#### Load Balancing Configuration
```yaml
# Load balancing for high-availability services
upstreams:
  - name: employee-service-upstream
    algorithm: round-robin
    healthchecks:
      active:
        http_path: "/health"
        healthy:
          interval: 10
          successes: 3
        unhealthy:
          interval: 10
          http_failures: 3
    targets:
      - target: employee-service-1:8080
        weight: 100
      - target: employee-service-2:8080
        weight: 100
```

#### Circuit Breaker Implementation
```lua
-- Kong circuit breaker plugin
local circuit_breaker = {
  PRIORITY = 1000,
  VERSION = "1.0.0"
}

function circuit_breaker:access(conf)
  local failure_threshold = conf.failure_threshold or 5
  local recovery_timeout = conf.recovery_timeout or 60
  local test_request_volume = conf.test_request_volume or 10
  
  -- Circuit breaker logic
  local cache_key = "circuit_breaker:" .. ngx.var.upstream_uri
  local failures = kong.cache:get(cache_key .. ":failures") or 0
  local last_failure = kong.cache:get(cache_key .. ":last_failure") or 0
  
  if failures >= failure_threshold then
    if (ngx.time() - last_failure) < recovery_timeout then
      return kong.response.exit(503, {
        message = "Service temporarily unavailable",
        retry_after = recovery_timeout - (ngx.time() - last_failure)
      })
    end
  end
end

return circuit_breaker
```

## Service-to-Service Communication

### 1. Synchronous Communication (REST APIs)

#### Authentication Service API
```yaml
# OpenAPI 3.0 specification
openapi: 3.0.3
info:
  title: WebGoat Authentication Service API
  version: 1.0.0
  description: Handles user authentication and authorization

paths:
  /api/v1/auth/login:
    post:
      summary: User login
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                username:
                  type: string
                  example: "john.doe"
                password:
                  type: string
                  example: "securePassword123"
              required:
                - username
                - password
      responses:
        '200':
          description: Login successful
          content:
            application/json:
              schema:
                type: object
                properties:
                  access_token:
                    type: string
                  refresh_token:
                    type: string
                  expires_in:
                    type: integer
                  user_id:
                    type: string
        '401':
          description: Invalid credentials
        '429':
          description: Too many login attempts

  /api/v1/auth/validate:
    post:
      summary: Validate JWT token
      security:
        - bearerAuth: []
      responses:
        '200':
          description: Token is valid
          content:
            application/json:
              schema:
                type: object
                properties:
                  valid:
                    type: boolean
                  user_id:
                    type: string
                  roles:
                    type: array
                    items:
                      type: string
        '401':
          description: Invalid or expired token

  /api/v1/auth/refresh:
    post:
      summary: Refresh access token
      requestBody:
        required: true
        content:
          application/json:
            schema:
              type: object
              properties:
                refresh_token:
                  type: string
              required:
                - refresh_token
      responses:
        '200':
          description: Token refreshed successfully
        '401':
          description: Invalid refresh token

components:
  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

#### Employee Service API
```yaml
openapi: 3.0.3
info:
  title: WebGoat Employee Service API
  version: 1.0.0
  description: Manages employee data and operations

paths:
  /api/v1/employees:
    get:
      summary: List employees
      parameters:
        - name: page
          in: query
          schema:
            type: integer
            default: 1
        - name: size
          in: query
          schema:
            type: integer
            default: 20
        - name: department
          in: query
          schema:
            type: string
        - name: search
          in: query
          schema:
            type: string
      security:
        - bearerAuth: []
      responses:
        '200':
          description: List of employees
          content:
            application/json:
              schema:
                type: object
                properties:
                  data:
                    type: array
                    items:
                      $ref: '#/components/schemas/Employee'
                  pagination:
                    $ref: '#/components/schemas/Pagination'

    post:
      summary: Create new employee
      security:
        - bearerAuth: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/CreateEmployeeRequest'
      responses:
        '201':
          description: Employee created successfully
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Employee'
        '400':
          description: Invalid input data
        '409':
          description: Employee already exists

  /api/v1/employees/{employeeId}:
    get:
      summary: Get employee by ID
      parameters:
        - name: employeeId
          in: path
          required: true
          schema:
            type: string
      security:
        - bearerAuth: []
      responses:
        '200':
          description: Employee details
          content:
            application/json:
              schema:
                $ref: '#/components/schemas/Employee'
        '404':
          description: Employee not found

    put:
      summary: Update employee
      parameters:
        - name: employeeId
          in: path
          required: true
          schema:
            type: string
      security:
        - bearerAuth: []
      requestBody:
        required: true
        content:
          application/json:
            schema:
              $ref: '#/components/schemas/UpdateEmployeeRequest'
      responses:
        '200':
          description: Employee updated successfully
        '404':
          description: Employee not found

    delete:
      summary: Delete employee
      parameters:
        - name: employeeId
          in: path
          required: true
          schema:
            type: string
      security:
        - bearerAuth: []
      responses:
        '204':
          description: Employee deleted successfully
        '404':
          description: Employee not found

components:
  schemas:
    Employee:
      type: object
      properties:
        id:
          type: string
        employee_id:
          type: string
        first_name:
          type: string
        last_name:
          type: string
        email:
          type: string
        department:
          type: string
        position:
          type: string
        hire_date:
          type: string
          format: date
        is_active:
          type: boolean
        created_at:
          type: string
          format: date-time
        updated_at:
          type: string
          format: date-time

    CreateEmployeeRequest:
      type: object
      properties:
        employee_id:
          type: string
        first_name:
          type: string
        last_name:
          type: string
        email:
          type: string
        department:
          type: string
        position:
          type: string
        hire_date:
          type: string
          format: date
      required:
        - employee_id
        - first_name
        - last_name
        - email

    UpdateEmployeeRequest:
      type: object
      properties:
        first_name:
          type: string
        last_name:
          type: string
        email:
          type: string
        department:
          type: string
        position:
          type: string
        is_active:
          type: boolean

    Pagination:
      type: object
      properties:
        page:
          type: integer
        size:
          type: integer
        total_pages:
          type: integer
        total_elements:
          type: integer
        has_next:
          type: boolean
        has_previous:
          type: boolean

  securitySchemes:
    bearerAuth:
      type: http
      scheme: bearer
      bearerFormat: JWT
```

### 2. Asynchronous Communication (Event-Driven)

#### Apache Kafka Configuration
```yaml
# docker-compose.yml for Kafka
version: '3.8'
services:
  zookeeper:
    image: confluentinc/cp-zookeeper:7.4.0
    environment:
      ZOOKEEPER_CLIENT_PORT: 2181
      ZOOKEEPER_TICK_TIME: 2000

  kafka:
    image: confluentinc/cp-kafka:7.4.0
    depends_on:
      - zookeeper
    ports:
      - "9092:9092"
    environment:
      KAFKA_BROKER_ID: 1
      KAFKA_ZOOKEEPER_CONNECT: zookeeper:2181
      KAFKA_ADVERTISED_LISTENERS: PLAINTEXT://localhost:9092
      KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
      KAFKA_AUTO_CREATE_TOPICS_ENABLE: true

  kafka-ui:
    image: provectuslabs/kafka-ui:latest
    depends_on:
      - kafka
    ports:
      - "8090:8080"
    environment:
      KAFKA_CLUSTERS_0_NAME: local
      KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:9092
```

#### Event Schema Definitions
```json
{
  "namespace": "com.webgoat.events",
  "type": "record",
  "name": "EmployeeCreated",
  "fields": [
    {"name": "event_id", "type": "string"},
    {"name": "event_time", "type": "long"},
    {"name": "event_version", "type": "string", "default": "1.0"},
    {"name": "employee_id", "type": "string"},
    {"name": "first_name", "type": "string"},
    {"name": "last_name", "type": "string"},
    {"name": "email", "type": "string"},
    {"name": "department", "type": "string"},
    {"name": "created_by", "type": "string"}
  ]
}

{
  "namespace": "com.webgoat.events",
  "type": "record",
  "name": "EmployeeUpdated",
  "fields": [
    {"name": "event_id", "type": "string"},
    {"name": "event_time", "type": "long"},
    {"name": "event_version", "type": "string", "default": "1.0"},
    {"name": "employee_id", "type": "string"},
    {"name": "changes", "type": {
      "type": "map",
      "values": "string"
    }},
    {"name": "updated_by", "type": "string"}
  ]
}

{
  "namespace": "com.webgoat.events",
  "type": "record",
  "name": "LessonCompleted",
  "fields": [
    {"name": "event_id", "type": "string"},
    {"name": "event_time", "type": "long"},
    {"name": "event_version", "type": "string", "default": "1.0"},
    {"name": "user_id", "type": "string"},
    {"name": "lesson_id", "type": "string"},
    {"name": "lesson_type", "type": "string"},
    {"name": "completion_time", "type": "long"},
    {"name": "score", "type": "int"},
    {"name": "attempts", "type": "int"}
  ]
}
```

#### Event Publisher Implementation
```java
@Component
public class EventPublisher {
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final ObjectMapper objectMapper;
    
    public EventPublisher(KafkaTemplate<String, Object> kafkaTemplate, 
                         ObjectMapper objectMapper) {
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
    }
    
    public void publishEmployeeCreated(Employee employee) {
        EmployeeCreatedEvent event = EmployeeCreatedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventTime(Instant.now().toEpochMilli())
            .employeeId(employee.getEmployeeId())
            .firstName(employee.getFirstName())
            .lastName(employee.getLastName())
            .email(employee.getEmail())
            .department(employee.getDepartment())
            .createdBy(SecurityContextHolder.getContext().getAuthentication().getName())
            .build();
            
        kafkaTemplate.send("employee.created", employee.getEmployeeId(), event)
            .addCallback(
                result -> log.info("Event published successfully: {}", event.getEventId()),
                failure -> log.error("Failed to publish event: {}", event.getEventId(), failure)
            );
    }
    
    public void publishLessonCompleted(String userId, String lessonId, 
                                     String lessonType, int score, int attempts) {
        LessonCompletedEvent event = LessonCompletedEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .eventTime(Instant.now().toEpochMilli())
            .userId(userId)
            .lessonId(lessonId)
            .lessonType(lessonType)
            .completionTime(Instant.now().toEpochMilli())
            .score(score)
            .attempts(attempts)
            .build();
            
        kafkaTemplate.send("lesson.completed", userId, event);
    }
}
```

#### Event Consumer Implementation
```java
@Component
public class EmployeeEventConsumer {
    
    private final EmployeeProfileService profileService;
    private final NotificationService notificationService;
    
    @KafkaListener(topics = "employee.created", groupId = "profile-service")
    public void handleEmployeeCreated(EmployeeCreatedEvent event) {
        try {
            // Create employee profile
            EmployeeProfile profile = EmployeeProfile.builder()
                .employeeId(event.getEmployeeId())
                .displayName(event.getFirstName() + " " + event.getLastName())
                .email(event.getEmail())
                .department(event.getDepartment())
                .createdAt(Instant.ofEpochMilli(event.getEventTime()))
                .build();
                
            profileService.createProfile(profile);
            
            // Send welcome notification
            notificationService.sendWelcomeNotification(event.getEmail());
            
            log.info("Processed employee created event: {}", event.getEmployeeId());
        } catch (Exception e) {
            log.error("Failed to process employee created event: {}", 
                event.getEmployeeId(), e);
            // Send to dead letter queue for retry
            throw e;
        }
    }
    
    @KafkaListener(topics = "lesson.completed", groupId = "analytics-service")
    public void handleLessonCompleted(LessonCompletedEvent event) {
        try {
            // Update user progress analytics
            UserProgress progress = UserProgress.builder()
                .userId(event.getUserId())
                .lessonId(event.getLessonId())
                .lessonType(event.getLessonType())
                .completedAt(Instant.ofEpochMilli(event.getCompletionTime()))
                .score(event.getScore())
                .attempts(event.getAttempts())
                .build();
                
            analyticsService.recordProgress(progress);
            
            // Check for achievements
            achievementService.checkAchievements(event.getUserId());
            
            log.info("Processed lesson completed event: {} for user {}", 
                event.getLessonId(), event.getUserId());
        } catch (Exception e) {
            log.error("Failed to process lesson completed event: {} for user {}", 
                event.getLessonId(), event.getUserId(), e);
            throw e;
        }
    }
}
```

### 3. GraphQL Federation for Complex Queries

#### GraphQL Gateway Configuration
```javascript
// Apollo Federation Gateway
const { ApolloGateway, IntrospectAndCompose } = require('@apollo/gateway');
const { ApolloServer } = require('apollo-server-express');
const express = require('express');

const gateway = new ApolloGateway({
  supergraphSdl: new IntrospectAndCompose({
    subgraphs: [
      { name: 'employees', url: 'http://employee-service:8080/graphql' },
      { name: 'lessons', url: 'http://lesson-service:8080/graphql' },
      { name: 'auth', url: 'http://auth-service:8080/graphql' },
      { name: 'content', url: 'http://content-service:8080/graphql' }
    ],
  }),
  buildService({ url }) {
    return new RemoteGraphQLDataSource({
      url,
      willSendRequest({ request, context }) {
        // Forward authentication headers
        if (context.authorization) {
          request.http.headers.set('authorization', context.authorization);
        }
      }
    });
  }
});

const server = new ApolloServer({
  gateway,
  context: ({ req }) => ({
    authorization: req.headers.authorization
  }),
  subscriptions: false
});

const app = express();
server.applyMiddleware({ app, path: '/graphql' });

app.listen(4000, () => {
  console.log('GraphQL Gateway running on http://localhost:4000/graphql');
});
```

#### Employee Service GraphQL Schema
```graphql
type Employee @key(fields: "id") {
  id: ID!
  employeeId: String!
  firstName: String!
  lastName: String!
  email: String!
  department: String
  position: String
  hireDate: String
  isActive: Boolean!
  profile: EmployeeProfile
  lessons: [LessonProgress!]!
}

type EmployeeProfile {
  id: ID!
  displayName: String!
  avatar: String
  bio: String
  skills: [String!]!
  certifications: [Certification!]!
}

type Certification {
  id: ID!
  name: String!
  issuer: String!
  issuedDate: String!
  expiryDate: String
  credentialUrl: String
}

type Query {
  employee(id: ID!): Employee
  employees(filter: EmployeeFilter, pagination: PaginationInput): EmployeeConnection!
  searchEmployees(query: String!): [Employee!]!
}

type Mutation {
  createEmployee(input: CreateEmployeeInput!): Employee!
  updateEmployee(id: ID!, input: UpdateEmployeeInput!): Employee!
  deleteEmployee(id: ID!): Boolean!
}

input EmployeeFilter {
  department: String
  position: String
  isActive: Boolean
  hiredAfter: String
  hiredBefore: String
}

input PaginationInput {
  page: Int = 1
  size: Int = 20
}

type EmployeeConnection {
  edges: [EmployeeEdge!]!
  pageInfo: PageInfo!
  totalCount: Int!
}

type EmployeeEdge {
  node: Employee!
  cursor: String!
}

type PageInfo {
  hasNextPage: Boolean!
  hasPreviousPage: Boolean!
  startCursor: String
  endCursor: String
}

input CreateEmployeeInput {
  employeeId: String!
  firstName: String!
  lastName: String!
  email: String!
  department: String
  position: String
  hireDate: String
}

input UpdateEmployeeInput {
  firstName: String
  lastName: String
  email: String
  department: String
  position: String
  isActive: Boolean
}
```

## Service Discovery and Registration

### Consul Configuration
```hcl
# consul.hcl
datacenter = "webgoat-dc1"
data_dir = "/opt/consul/data"
log_level = "INFO"
server = true
bootstrap_expect = 1
bind_addr = "0.0.0.0"
client_addr = "0.0.0.0"
retry_join = ["consul-server"]
ui_config {
  enabled = true
}
connect {
  enabled = true
}
ports {
  grpc = 8502
}
```

### Service Registration
```java
@Configuration
@EnableDiscoveryClient
public class ConsulConfig {
    
    @Bean
    public ConsulRegistration consulRegistration(
            ConsulDiscoveryProperties properties,
            ApplicationContext context) {
        
        NewService newService = new NewService();
        newService.setId(properties.getInstanceId());
        newService.setName(properties.getServiceName());
        newService.setAddress(properties.getHostname());
        newService.setPort(properties.getPort());
        
        // Health check configuration
        NewService.Check check = new NewService.Check();
        check.setHttp("http://" + properties.getHostname() + ":" + 
                     properties.getPort() + "/actuator/health");
        check.setInterval("10s");
        check.setTimeout("3s");
        newService.setCheck(check);
        
        // Service tags
        newService.setTags(Arrays.asList(
            "version=" + getClass().getPackage().getImplementationVersion(),
            "environment=" + environment.getActiveProfiles()[0],
            "microservice"
        ));
        
        return new ConsulRegistration(newService, properties);
    }
}
```

## Error Handling and Resilience

### Circuit Breaker Implementation
```java
@Component
public class EmployeeServiceClient {
    
    private final WebClient webClient;
    private final CircuitBreaker circuitBreaker;
    
    public EmployeeServiceClient(WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
            .baseUrl("http://employee-service")
            .build();
            
        this.circuitBreaker = CircuitBreaker.ofDefaults("employee-service");
        circuitBreaker.getEventPublisher()
            .onStateTransition(event -> 
                log.info("Circuit breaker state transition: {}", event));
    }
    
    public Mono<Employee> getEmployee(String employeeId) {
        return circuitBreaker.executeSupplier(() -> 
            webClient.get()
                .uri("/api/v1/employees/{id}", employeeId)
                .retrieve()
                .bodyToMono(Employee.class)
                .block()
        ).map(Mono::just)
         .orElse(Mono.error(new ServiceUnavailableException("Employee service unavailable")));
    }
    
    public Mono<Employee> getEmployeeWithFallback(String employeeId) {
        return getEmployee(employeeId)
            .onErrorResume(throwable -> {
                log.warn("Employee service call failed, using fallback", throwable);
                return getCachedEmployee(employeeId)
                    .switchIfEmpty(Mono.just(createFallbackEmployee(employeeId)));
            });
    }
    
    private Mono<Employee> getCachedEmployee(String employeeId) {
        // Implementation to get cached employee data
        return redisTemplate.opsForValue()
            .get("employee:" + employeeId)
            .map(cached -> objectMapper.readValue(cached, Employee.class))
            .onErrorResume(throwable -> Mono.empty());
    }
    
    private Employee createFallbackEmployee(String employeeId) {
        return Employee.builder()
            .id(employeeId)
            .firstName("Unknown")
            .lastName("Employee")
            .email("unknown@webgoat.local")
            .isActive(false)
            .build();
    }
}
```

### Retry Configuration
```java
@Configuration
public class RetryConfig {
    
    @Bean
    public Retry employeeServiceRetry() {
        return Retry.ofDefaults("employee-service")
            .toBuilder()
            .maxAttempts(3)
            .waitDuration(Duration.ofMillis(500))
            .retryOnException(throwable -> 
                throwable instanceof ConnectException ||
                throwable instanceof SocketTimeoutException ||
                (throwable instanceof WebClientResponseException &&
                 ((WebClientResponseException) throwable).getStatusCode().is5xxServerError()))
            .build();
    }
    
    @Bean
    public RetryTemplate retryTemplate() {
        RetryTemplate retryTemplate = new RetryTemplate();
        
        FixedBackOffPolicy backOffPolicy = new FixedBackOffPolicy();
        backOffPolicy.setBackOffPeriod(1000L);
        retryTemplate.setBackOffPolicy(backOffPolicy);
        
        SimpleRetryPolicy retryPolicy = new SimpleRetryPolicy();
        retryPolicy.setMaxAttempts(3);
        retryTemplate.setRetryPolicy(retryPolicy);
        
        return retryTemplate;
    }
}
```

## API Versioning Strategy

### URL-Based Versioning
```java
@RestController
@RequestMapping("/api/v1/employees")
public class EmployeeControllerV1 {
    // Version 1 implementation
}

@RestController
@RequestMapping("/api/v2/employees")
public class EmployeeControllerV2 {
    // Version 2 implementation with enhanced features
}
```

### Header-Based Versioning
```java
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {
    
    @GetMapping(headers = "API-Version=1")
    public ResponseEntity<List<EmployeeV1>> getEmployeesV1() {
        // Version 1 response format
    }
    
    @GetMapping(headers = "API-Version=2")
    public ResponseEntity<List<EmployeeV2>> getEmployeesV2() {
        // Version 2 response format
    }
}
```

## Monitoring and Observability

### API Metrics Collection
```java
@Component
public class ApiMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    private final Counter apiCallsCounter;
    private final Timer apiResponseTimer;
    
    public ApiMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        this.apiCallsCounter = Counter.builder("api.calls.total")
            .description("Total number of API calls")
            .register(meterRegistry);
        this.apiResponseTimer = Timer.builder("api.response.time")
            .description("API response time")
            .register(meterRegistry);
    }
    
    @EventListener
    public void handleApiCall(ApiCallEvent event) {
        apiCallsCounter.increment(
            Tags.of(
                "service", event.getServiceName(),
                "endpoint", event.getEndpoint(),
                "method", event.getHttpMethod(),
                "status", String.valueOf(event.getStatusCode())
            )
        );
        
        apiResponseTimer.record(
            event.getResponseTime(),
            TimeUnit.MILLISECONDS,
            Tags.of(
                "service", event.getServiceName(),
                "endpoint", event.getEndpoint()
            )
        );
    }
}
```

### Distributed Tracing
```java
@Configuration
public class TracingConfig {
    
    @Bean
    public Sender sender() {
        return OkHttpSender.create("http://jaeger:14268/api/traces");
    }
    
    @Bean
    public AsyncReporter<Span> spanReporter() {
        return AsyncReporter.create(sender());
    }
    
    @Bean
    public Tracing tracing() {
        return Tracing.newBuilder()
            .localServiceName("webgoat-api-gateway")
            .spanReporter(spanReporter())
            .sampler(Sampler.create(1.0f))
            .build();
    }
}
```

## Security Implementation

### JWT Token Validation
```java
@Component
public class JwtTokenValidator {
    
    private final JwtDecoder jwtDecoder;
    private final RedisTemplate<String, String> redisTemplate;
    
    public boolean validateToken(String token) {
        try {
            Jwt jwt = jwtDecoder.decode(token);
            
            // Check if token is blacklisted
            String tokenId = jwt.getClaimAsString("jti");
            if (redisTemplate.hasKey("blacklist:" + tokenId)) {
                return false;
            }
            
            // Check expiration
            Instant expiration = jwt.getExpiresAt();
            if (expiration != null && expiration.isBefore(Instant.now())) {
                return false;
            }
            
            return true;
        } catch (JwtException e) {
            log.warn("Invalid JWT token: {}", e.getMessage());
            return false;
        }
    }
    
    public UserPrincipal extractUserPrincipal(String token) {
        Jwt jwt = jwtDecoder.decode(token);
        
        return UserPrincipal.builder()
            .userId(jwt.getSubject())
            .username(jwt.getClaimAsString("username"))
            .roles(jwt.getClaimAsStringList("roles"))
            .permissions(jwt.getClaimAsStringList("permissions"))
            .build();
    }
}
```

### Rate Limiting
```java
@Component
public class RateLimitingFilter implements WebFilter {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RateLimitProperties rateLimitProperties;
    
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String clientId = extractClientId(exchange.getRequest());
        String key = "rate_limit:" + clientId;
        
        return redisTemplate.opsForValue()
            .increment(key)
            .flatMap(count -> {
                if (count == 1) {
                    // Set expiration for the first request
                    redisTemplate.expire(key, Duration.ofMinutes(1));
                }
                
                if (count > rateLimitProperties.getMaxRequests()) {
                    return handleRateLimitExceeded(exchange);
                }
                
                return chain.filter(exchange);
            });
    }
    
    private String extractClientId(ServerHttpRequest request) {
        // Extract client ID from JWT token or IP address
        String authorization = request.getHeaders().getFirst("Authorization");
        if (authorization != null && authorization.startsWith("Bearer ")) {
            try {
                String token = authorization.substring(7);
                Jwt jwt = jwtDecoder.decode(token);
                return jwt.getSubject();
            } catch (Exception e) {
                // Fall back to IP address
            }
        }
        
        return request.getRemoteAddress().getAddress().getHostAddress();
    }
    
    private Mono<Void> handleRateLimitExceeded(ServerWebExchange exchange) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.TOO_MANY_REQUESTS);
        response.getHeaders().add("Content-Type", "application/json");
        
        String body = "{\"error\":\"Rate limit exceeded\",\"retry_after\":60}";
        DataBuffer buffer = response.bufferFactory().wrap(body.getBytes());
        
        return response.writeWith(Mono.just(buffer));
    }
}
```

## Performance Optimization

### Response Caching
```java
@Configuration
@EnableCaching
public class CacheConfig {
    
    @Bean
    public CacheManager cacheManager() {
        RedisCacheManager.Builder builder = RedisCacheManager
            .RedisCacheManagerBuilder
            .fromConnectionFactory(redisConnectionFactory())
            .cacheDefaults(cacheConfiguration());
            
        return builder.build();
    }
    
    private RedisCacheConfiguration cacheConfiguration() {
        return RedisCacheConfiguration.defaultCacheConfig()
            .entryTtl(Duration.ofMinutes(10))
            .serializeKeysWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new StringRedisSerializer()))
            .serializeValuesWith(RedisSerializationContext.SerializationPair
                .fromSerializer(new GenericJackson2JsonRedisSerializer()));
    }
}

@Service
public class EmployeeService {
    
    @Cacheable(value = "employees", key = "#employeeId")
    public Employee getEmployee(String employeeId) {
        return employeeRepository.findByEmployeeId(employeeId)
            .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
    }
    
    @CacheEvict(value = "employees", key = "#employee.employeeId")
    public Employee updateEmployee(Employee employee) {
        return employeeRepository.save(employee);
    }
    
    @Cacheable(value = "employee-list", key = "#department + ':' + #page + ':' + #size")
    public Page<Employee> getEmployeesByDepartment(String department, int page, int size) {
        Pageable pageable = PageRequest.of(page, size);
        return employeeRepository.findByDepartment(department, pageable);
    }
}
```

### Connection Pooling
```yaml
# application.yml
spring:
  datasource:
    hikari:
      maximum-pool-size: 20
      minimum-idle: 5
      idle-timeout: 300000
      max-lifetime: 1200000
      connection-timeout: 20000
      validation-timeout: 5000
      leak-detection-threshold: 60000
      
  data:
    mongodb:
      uri: mongodb://mongodb:27017/webgoat_lessons?maxPoolSize=20&minPoolSize=5
      
  kafka:
    producer:
      bootstrap-servers: kafka:9092
      key-serializer: org.apache.kafka.common.serialization.StringSerializer
      value-serializer: org.springframework.kafka.support.serializer.JsonSerializer
      acks: all
      retries: 3
      batch-size: 16384
      linger-ms: 5
      buffer-memory: 33554432
      
    consumer:
      bootstrap-servers: kafka:9092
      group-id: webgoat-services
      key-deserializer: org.apache.kafka.common.serialization.StringDeserializer
      value-deserializer: org.springframework.kafka.support.serializer.JsonDeserializer
      auto-offset-reset: earliest
      enable-auto-commit: false
      max-poll-records: 500
      fetch-min-size: 1
      fetch-max-wait: 500
```

---

*This API design and communication strategy provides a robust, scalable, and secure foundation for the WebGoat v3 microservices architecture, ensuring efficient service-to-service communication while maintaining high availability and performance.*