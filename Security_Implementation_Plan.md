# WebGoat v3 Security Implementation Plan

## Overview

This document outlines the comprehensive security strategy for the WebGoat v3 microservices migration. Based on CAST Imaging analysis revealing 9 critical structural flaws and security vulnerabilities, this plan addresses both legacy security issues and implements modern microservices security patterns.

## Current Security Analysis (CAST Imaging)

### Identified Structural Flaws

1. **Calling functions in loop terminations** (8 objects affected)
   - **Risk**: Performance degradation and potential DoS
   - **Remediation**: Optimize loop conditions and implement caching

2. **Using eval() in JavaScript** (1 object affected)
   - **Risk**: Code injection vulnerabilities
   - **Remediation**: Replace eval() with safe alternatives

3. **Running SQL queries inside loops** (1 object affected)
   - **Risk**: N+1 query problem and performance issues
   - **Remediation**: Implement batch queries and caching

4. **Empty catch blocks** (1 object affected)
   - **Risk**: Silent failures and security issues
   - **Remediation**: Proper error handling and logging

5. **SQL injection in dynamic SQL** (1 object affected)
   - **Risk**: Data breach and unauthorized access
   - **Remediation**: Parameterized queries and input validation

6. **Remote calls inside loops** (1 object affected)
   - **Risk**: Performance issues and potential timeouts
   - **Remediation**: Batch processing and async calls

7. **Reflected cross-site scripting** (1 object affected)
   - **Risk**: XSS attacks and session hijacking
   - **Remediation**: Input sanitization and output encoding

8. **Persistent cross-site scripting** (1 object affected)
   - **Risk**: Stored XSS attacks
   - **Remediation**: Content Security Policy and input validation

## Microservices Security Architecture

### 1. Zero Trust Security Model

#### Network Segmentation
```yaml
# Kubernetes Network Policies
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: webgoat-network-policy
  namespace: webgoat
spec:
  podSelector: {}
  policyTypes:
  - Ingress
  - Egress
  ingress:
  - from:
    - namespaceSelector:
        matchLabels:
          name: webgoat
    - podSelector:
        matchLabels:
          app: api-gateway
    ports:
    - protocol: TCP
      port: 8080
  egress:
  - to:
    - namespaceSelector:
        matchLabels:
          name: webgoat-data
    ports:
    - protocol: TCP
      port: 5432  # PostgreSQL
    - protocol: TCP
      port: 27017 # MongoDB
  - to:
    - namespaceSelector:
        matchLabels:
          name: webgoat-messaging
    ports:
    - protocol: TCP
      port: 9092  # Kafka
```

#### Service Mesh Security (Istio)
```yaml
# Istio Security Policy
apiVersion: security.istio.io/v1beta1
kind: PeerAuthentication
metadata:
  name: webgoat-peer-auth
  namespace: webgoat
spec:
  mtls:
    mode: STRICT
---
apiVersion: security.istio.io/v1beta1
kind: AuthorizationPolicy
metadata:
  name: webgoat-authz
  namespace: webgoat
spec:
  selector:
    matchLabels:
      app: employee-service
  rules:
  - from:
    - source:
        principals: ["cluster.local/ns/webgoat/sa/api-gateway"]
  - to:
    - operation:
        methods: ["GET", "POST", "PUT", "DELETE"]
    when:
    - key: request.headers[authorization]
      values: ["Bearer *"]
```

### 2. Identity and Access Management (IAM)

#### OAuth 2.0 / OpenID Connect Implementation
```java
@Configuration
@EnableWebSecurity
@EnableGlobalMethodSecurity(prePostEnabled = true)
public class SecurityConfig {
    
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf().disable()
            .sessionManagement().sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            .and()
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/api/v1/auth/login", "/api/v1/auth/register").permitAll()
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers(HttpMethod.GET, "/api/v1/lessons/**").hasRole("USER")
                .requestMatchers(HttpMethod.POST, "/api/v1/employees/**").hasRole("ADMIN")
                .requestMatchers("/api/v1/financial/**").hasRole("FINANCIAL_USER")
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .decoder(jwtDecoder())
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            )
            .exceptionHandling(exceptions -> exceptions
                .authenticationEntryPoint(new JwtAuthenticationEntryPoint())
                .accessDeniedHandler(new JwtAccessDeniedHandler())
            );
            
        return http.build();
    }
    
    @Bean
    public JwtDecoder jwtDecoder() {
        NimbusJwtDecoder jwtDecoder = NimbusJwtDecoder
            .withJwkSetUri("http://auth-service:8080/.well-known/jwks.json")
            .build();
            
        jwtDecoder.setJwtValidator(jwtValidator());
        return jwtDecoder;
    }
    
    @Bean
    public Converter<Jwt, AbstractAuthenticationToken> jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            Collection<String> authorities = jwt.getClaimAsStringList("authorities");
            return authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .collect(Collectors.toList());
        });
        return converter;
    }
    
    @Bean
    public Validator<Jwt> jwtValidator() {
        List<Validator<Jwt>> validators = new ArrayList<>();
        validators.add(new JwtTimestampValidator());
        validators.add(new JwtIssuerValidator("https://webgoat.local"));
        validators.add(new JwtAudienceValidator("webgoat-api"));
        
        return new DelegatingValidator<>(validators);
    }
}
```

#### JWT Token Service
```java
@Service
public class JwtTokenService {
    
    private final JWKSource<SecurityContext> jwkSource;
    private final JwtEncoder jwtEncoder;
    private final JwtDecoder jwtDecoder;
    private final RedisTemplate<String, String> redisTemplate;
    
    public String generateAccessToken(UserPrincipal userPrincipal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(15, ChronoUnit.MINUTES);
        
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("https://webgoat.local")
            .subject(userPrincipal.getUserId())
            .audience(List.of("webgoat-api"))
            .issuedAt(now)
            .expiresAt(expiry)
            .claim("username", userPrincipal.getUsername())
            .claim("authorities", userPrincipal.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toList()))
            .claim("jti", UUID.randomUUID().toString())
            .build();
            
        return jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
    }
    
    public String generateRefreshToken(UserPrincipal userPrincipal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(7, ChronoUnit.DAYS);
        
        JwtClaimsSet claims = JwtClaimsSet.builder()
            .issuer("https://webgoat.local")
            .subject(userPrincipal.getUserId())
            .audience(List.of("webgoat-refresh"))
            .issuedAt(now)
            .expiresAt(expiry)
            .claim("type", "refresh")
            .claim("jti", UUID.randomUUID().toString())
            .build();
            
        String refreshToken = jwtEncoder.encode(JwtEncoderParameters.from(claims)).getTokenValue();
        
        // Store refresh token in Redis with expiration
        redisTemplate.opsForValue().set(
            "refresh_token:" + userPrincipal.getUserId(),
            refreshToken,
            Duration.ofDays(7)
        );
        
        return refreshToken;
    }
    
    public void revokeToken(String tokenId) {
        // Add token to blacklist
        redisTemplate.opsForValue().set(
            "blacklist:" + tokenId,
            "revoked",
            Duration.ofHours(24) // Keep blacklisted for max token lifetime
        );
    }
    
    public boolean isTokenBlacklisted(String tokenId) {
        return redisTemplate.hasKey("blacklist:" + tokenId);
    }
}
```

### 3. API Security

#### Input Validation and Sanitization
```java
@Component
public class InputValidationService {
    
    private final Validator validator;
    private final PolicyFactory htmlSanitizer;
    
    public InputValidationService() {
        this.validator = Validation.buildDefaultValidatorFactory().getValidator();
        this.htmlSanitizer = new HtmlPolicyBuilder()
            .allowElements("b", "i", "em", "strong")
            .allowAttributes("class").onElements("span")
            .toFactory();
    }
    
    public <T> void validateInput(T input) {
        Set<ConstraintViolation<T>> violations = validator.validate(input);
        if (!violations.isEmpty()) {
            String message = violations.stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.joining(", "));
            throw new ValidationException("Input validation failed: " + message);
        }
    }
    
    public String sanitizeHtml(String input) {
        if (input == null) {
            return null;
        }
        return htmlSanitizer.sanitize(input);
    }
    
    public String sanitizeSql(String input) {
        if (input == null) {
            return null;
        }
        // Remove SQL injection patterns
        return input.replaceAll("(?i)(union|select|insert|update|delete|drop|create|alter|exec|execute)", "")
                   .replaceAll("[';\"\\-]", "");
    }
    
    public boolean isValidEmail(String email) {
        String emailRegex = "^[a-zA-Z0-9._%+-]+@[a-zA-Z0-9.-]+\\.[a-zA-Z]{2,}$";
        return email != null && email.matches(emailRegex);
    }
    
    public boolean isValidEmployeeId(String employeeId) {
        // Employee ID should be alphanumeric, 6-10 characters
        String employeeIdRegex = "^[a-zA-Z0-9]{6,10}$";
        return employeeId != null && employeeId.matches(employeeIdRegex);
    }
}
```

#### SQL Injection Prevention
```java
@Repository
public class EmployeeRepository {
    
    private final JdbcTemplate jdbcTemplate;
    private final NamedParameterJdbcTemplate namedParameterJdbcTemplate;
    
    // SECURE: Using parameterized queries
    public List<Employee> findByDepartment(String department) {
        String sql = "SELECT * FROM employees WHERE department = ? AND is_active = true";
        return jdbcTemplate.query(sql, new Object[]{department}, new EmployeeRowMapper());
    }
    
    // SECURE: Using named parameters
    public List<Employee> searchEmployees(String searchTerm, String department) {
        String sql = """
            SELECT * FROM employees 
            WHERE (first_name ILIKE :searchTerm OR last_name ILIKE :searchTerm)
            AND (:department IS NULL OR department = :department)
            AND is_active = true
            ORDER BY last_name, first_name
            """;
            
        Map<String, Object> params = Map.of(
            "searchTerm", "%" + searchTerm + "%",
            "department", department
        );
        
        return namedParameterJdbcTemplate.query(sql, params, new EmployeeRowMapper());
    }
    
    // SECURE: Using JPA Criteria API for dynamic queries
    public List<Employee> findEmployeesWithCriteria(EmployeeSearchCriteria criteria) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Employee> query = cb.createQuery(Employee.class);
        Root<Employee> root = query.from(Employee.class);
        
        List<Predicate> predicates = new ArrayList<>();
        
        if (criteria.getFirstName() != null) {
            predicates.add(cb.like(cb.lower(root.get("firstName")), 
                "%" + criteria.getFirstName().toLowerCase() + "%"));
        }
        
        if (criteria.getDepartment() != null) {
            predicates.add(cb.equal(root.get("department"), criteria.getDepartment()));
        }
        
        if (criteria.getHiredAfter() != null) {
            predicates.add(cb.greaterThanOrEqualTo(root.get("hireDate"), criteria.getHiredAfter()));
        }
        
        predicates.add(cb.equal(root.get("isActive"), true));
        
        query.where(predicates.toArray(new Predicate[0]));
        query.orderBy(cb.asc(root.get("lastName")), cb.asc(root.get("firstName")));
        
        return entityManager.createQuery(query).getResultList();
    }
}
```

#### XSS Prevention
```java
@Component
public class XssProtectionFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, 
                        FilterChain chain) throws IOException, ServletException {
        
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Add security headers
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        httpResponse.setHeader("X-Frame-Options", "DENY");
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
        httpResponse.setHeader("Content-Security-Policy", 
            "default-src 'self'; " +
            "script-src 'self' 'unsafe-inline'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: https:; " +
            "font-src 'self'; " +
            "connect-src 'self'; " +
            "frame-ancestors 'none';");
        
        // Wrap request to sanitize parameters
        XssRequestWrapper wrappedRequest = new XssRequestWrapper(httpRequest);
        
        chain.doFilter(wrappedRequest, response);
    }
}

public class XssRequestWrapper extends HttpServletRequestWrapper {
    
    private final Pattern[] patterns = new Pattern[]{
        Pattern.compile("<script>(.*?)</script>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("src[\r\n]*=[\r\n]*\\'(.*?)\\'", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("src[\r\n]*=[\r\n]*\\\"(.*?)\\\"", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("</script>", Pattern.CASE_INSENSITIVE),
        Pattern.compile("<script(.*?)>", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("eval\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("expression\\((.*?)\\)", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL),
        Pattern.compile("javascript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("vbscript:", Pattern.CASE_INSENSITIVE),
        Pattern.compile("onload(.*?)=", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE | Pattern.DOTALL)
    };
    
    public XssRequestWrapper(HttpServletRequest servletRequest) {
        super(servletRequest);
    }
    
    @Override
    public String[] getParameterValues(String parameter) {
        String[] values = super.getParameterValues(parameter);
        if (values == null) {
            return null;
        }
        
        int count = values.length;
        String[] encodedValues = new String[count];
        for (int i = 0; i < count; i++) {
            encodedValues[i] = stripXSS(values[i]);
        }
        
        return encodedValues;
    }
    
    @Override
    public String getParameter(String parameter) {
        String value = super.getParameter(parameter);
        return stripXSS(value);
    }
    
    @Override
    public String getHeader(String name) {
        String value = super.getHeader(name);
        return stripXSS(value);
    }
    
    private String stripXSS(String value) {
        if (value != null) {
            // Encode HTML entities
            value = HtmlUtils.htmlEscape(value);
            
            // Remove XSS patterns
            for (Pattern pattern : patterns) {
                value = pattern.matcher(value).replaceAll("");
            }
        }
        return value;
    }
}
```

### 4. Data Protection

#### Encryption at Rest
```yaml
# PostgreSQL encryption configuration
apiVersion: v1
kind: ConfigMap
metadata:
  name: postgres-config
data:
  postgresql.conf: |
    # Enable SSL
    ssl = on
    ssl_cert_file = '/etc/ssl/certs/server.crt'
    ssl_key_file = '/etc/ssl/private/server.key'
    ssl_ca_file = '/etc/ssl/certs/ca.crt'
    
    # Enable transparent data encryption
    shared_preload_libraries = 'pg_tde'
    
    # Logging
    log_statement = 'all'
    log_connections = on
    log_disconnections = on
    log_line_prefix = '%t [%p]: [%l-1] user=%u,db=%d,app=%a,client=%h '
---
apiVersion: v1
kind: Secret
metadata:
  name: postgres-tde-key
type: Opaque
data:
  master-key: <base64-encoded-encryption-key>
```

#### Field-Level Encryption
```java
@Service
public class EncryptionService {
    
    private final AESUtil aesUtil;
    private final RSAUtil rsaUtil;
    
    @Value("${app.encryption.aes.key}")
    private String aesKey;
    
    @Value("${app.encryption.rsa.public-key}")
    private String rsaPublicKey;
    
    @Value("${app.encryption.rsa.private-key}")
    private String rsaPrivateKey;
    
    public String encryptSensitiveData(String plainText) {
        try {
            return aesUtil.encrypt(plainText, aesKey);
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt sensitive data", e);
        }
    }
    
    public String decryptSensitiveData(String encryptedText) {
        try {
            return aesUtil.decrypt(encryptedText, aesKey);
        } catch (Exception e) {
            throw new EncryptionException("Failed to decrypt sensitive data", e);
        }
    }
    
    public String encryptWithRSA(String plainText) {
        try {
            return rsaUtil.encrypt(plainText, rsaPublicKey);
        } catch (Exception e) {
            throw new EncryptionException("Failed to encrypt with RSA", e);
        }
    }
    
    public String decryptWithRSA(String encryptedText) {
        try {
            return rsaUtil.decrypt(encryptedText, rsaPrivateKey);
        } catch (Exception e) {
            throw new EncryptionException("Failed to decrypt with RSA", e);
        }
    }
}

@Entity
@Table(name = "employees")
public class Employee {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(name = "employee_id")
    private String employeeId;
    
    @Column(name = "first_name")
    private String firstName;
    
    @Column(name = "last_name")
    private String lastName;
    
    @Column(name = "email")
    @Convert(converter = EncryptedStringConverter.class)
    private String email;
    
    @Column(name = "ssn")
    @Convert(converter = EncryptedStringConverter.class)
    private String ssn;
    
    @Column(name = "salary")
    @Convert(converter = EncryptedBigDecimalConverter.class)
    private BigDecimal salary;
    
    // getters and setters
}

@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {
    
    @Autowired
    private EncryptionService encryptionService;
    
    @Override
    public String convertToDatabaseColumn(String attribute) {
        if (attribute == null) {
            return null;
        }
        return encryptionService.encryptSensitiveData(attribute);
    }
    
    @Override
    public String convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return encryptionService.decryptSensitiveData(dbData);
    }
}
```

### 5. Secrets Management

#### HashiCorp Vault Integration
```java
@Configuration
@EnableVaultRepositories
public class VaultConfig {
    
    @Bean
    public VaultTemplate vaultTemplate() {
        VaultEndpoint vaultEndpoint = VaultEndpoint.create("vault.webgoat.local", 8200);
        vaultEndpoint.setScheme("https");
        
        ClientAuthentication clientAuthentication = new TokenAuthentication(
            System.getenv("VAULT_TOKEN")
        );
        
        return new VaultTemplate(vaultEndpoint, clientAuthentication);
    }
}

@Service
public class SecretsService {
    
    private final VaultTemplate vaultTemplate;
    
    public SecretsService(VaultTemplate vaultTemplate) {
        this.vaultTemplate = vaultTemplate;
    }
    
    public String getDatabasePassword(String serviceName) {
        VaultResponse response = vaultTemplate.read("secret/data/database/" + serviceName);
        if (response != null && response.getData() != null) {
            Map<String, Object> data = (Map<String, Object>) response.getData().get("data");
            return (String) data.get("password");
        }
        throw new SecretNotFoundException("Database password not found for service: " + serviceName);
    }
    
    public String getApiKey(String serviceName, String keyName) {
        VaultResponse response = vaultTemplate.read("secret/data/api-keys/" + serviceName);
        if (response != null && response.getData() != null) {
            Map<String, Object> data = (Map<String, Object>) response.getData().get("data");
            return (String) data.get(keyName);
        }
        throw new SecretNotFoundException("API key not found: " + keyName + " for service: " + serviceName);
    }
    
    public void rotateSecret(String path, String key, String newValue) {
        Map<String, Object> secretData = new HashMap<>();
        secretData.put(key, newValue);
        secretData.put("rotated_at", Instant.now().toString());
        
        vaultTemplate.write(path, secretData);
        
        // Notify services about secret rotation
        eventPublisher.publishEvent(new SecretRotatedEvent(path, key));
    }
}
```

#### Kubernetes Secrets Integration
```yaml
apiVersion: v1
kind: Secret
metadata:
  name: webgoat-secrets
  namespace: webgoat
type: Opaque
data:
  database-password: <base64-encoded-password>
  jwt-secret: <base64-encoded-jwt-secret>
  encryption-key: <base64-encoded-encryption-key>
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: employee-service
spec:
  template:
    spec:
      containers:
      - name: employee-service
        image: webgoat/employee-service:latest
        env:
        - name: DATABASE_PASSWORD
          valueFrom:
            secretKeyRef:
              name: webgoat-secrets
              key: database-password
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: webgoat-secrets
              key: jwt-secret
        - name: ENCRYPTION_KEY
          valueFrom:
            secretKeyRef:
              name: webgoat-secrets
              key: encryption-key
```

### 6. Audit and Compliance

#### Audit Logging
```java
@Component
public class AuditLogger {
    
    private final Logger auditLog = LoggerFactory.getLogger("AUDIT");
    private final KafkaTemplate<String, AuditEvent> kafkaTemplate;
    
    public void logUserAction(String userId, String action, String resource, 
                             String details, boolean success) {
        AuditEvent event = AuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .userId(userId)
            .action(action)
            .resource(resource)
            .details(details)
            .success(success)
            .ipAddress(getCurrentUserIpAddress())
            .userAgent(getCurrentUserAgent())
            .sessionId(getCurrentSessionId())
            .build();
            
        // Log to file
        auditLog.info("AUDIT: {}", objectMapper.writeValueAsString(event));
        
        // Send to audit topic for centralized processing
        kafkaTemplate.send("audit.events", userId, event);
    }
    
    public void logDataAccess(String userId, String dataType, String recordId, 
                             String operation) {
        logUserAction(userId, "DATA_ACCESS", dataType + ":" + recordId, 
                     "Operation: " + operation, true);
    }
    
    public void logSecurityEvent(String userId, String eventType, String details) {
        AuditEvent event = AuditEvent.builder()
            .eventId(UUID.randomUUID().toString())
            .timestamp(Instant.now())
            .userId(userId)
            .action("SECURITY_EVENT")
            .resource("SYSTEM")
            .details(eventType + ": " + details)
            .success(false)
            .severity("HIGH")
            .build();
            
        auditLog.warn("SECURITY_AUDIT: {}", objectMapper.writeValueAsString(event));
        kafkaTemplate.send("security.events", userId, event);
    }
    
    private String getCurrentUserIpAddress() {
        RequestAttributes requestAttributes = RequestContextHolder.getRequestAttributes();
        if (requestAttributes instanceof ServletRequestAttributes) {
            HttpServletRequest request = ((ServletRequestAttributes) requestAttributes).getRequest();
            return getClientIpAddress(request);
        }
        return "unknown";
    }
    
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isEmpty()) {
            return xForwardedFor.split(",")[0].trim();
        }
        
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isEmpty()) {
            return xRealIp;
        }
        
        return request.getRemoteAddr();
    }
}
```

#### GDPR Compliance
```java
@Service
public class GdprComplianceService {
    
    private final EmployeeRepository employeeRepository;
    private final AuditLogger auditLogger;
    private final EncryptionService encryptionService;
    
    @Transactional
    public void anonymizeEmployeeData(String employeeId, String requestedBy) {
        Employee employee = employeeRepository.findByEmployeeId(employeeId)
            .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
            
        // Log the anonymization request
        auditLogger.logUserAction(requestedBy, "GDPR_ANONYMIZE", 
            "Employee:" + employeeId, "Data anonymization requested", true);
            
        // Anonymize personal data
        employee.setFirstName("[ANONYMIZED]");
        employee.setLastName("[ANONYMIZED]");
        employee.setEmail("anonymized@webgoat.local");
        employee.setSsn(null);
        employee.setAnonymized(true);
        employee.setAnonymizedAt(Instant.now());
        employee.setAnonymizedBy(requestedBy);
        
        employeeRepository.save(employee);
        
        // Publish event for other services to anonymize related data
        eventPublisher.publishEvent(new EmployeeAnonymizedEvent(employeeId, requestedBy));
    }
    
    public EmployeeDataExport exportEmployeeData(String employeeId, String requestedBy) {
        Employee employee = employeeRepository.findByEmployeeId(employeeId)
            .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
            
        // Log the data export request
        auditLogger.logDataAccess(requestedBy, "Employee", employeeId, "GDPR_EXPORT");
        
        // Collect all personal data
        EmployeeDataExport export = EmployeeDataExport.builder()
            .employeeId(employee.getEmployeeId())
            .personalData(collectPersonalData(employee))
            .lessonProgress(collectLessonProgress(employeeId))
            .auditTrail(collectAuditTrail(employeeId))
            .exportedAt(Instant.now())
            .exportedBy(requestedBy)
            .build();
            
        return export;
    }
    
    @Transactional
    public void deleteEmployeeData(String employeeId, String requestedBy) {
        Employee employee = employeeRepository.findByEmployeeId(employeeId)
            .orElseThrow(() -> new EmployeeNotFoundException(employeeId));
            
        // Log the deletion request
        auditLogger.logUserAction(requestedBy, "GDPR_DELETE", 
            "Employee:" + employeeId, "Data deletion requested", true);
            
        // Soft delete to maintain referential integrity
        employee.setDeleted(true);
        employee.setDeletedAt(Instant.now());
        employee.setDeletedBy(requestedBy);
        
        // Clear personal data
        employee.setFirstName(null);
        employee.setLastName(null);
        employee.setEmail(null);
        employee.setSsn(null);
        
        employeeRepository.save(employee);
        
        // Publish event for other services to delete related data
        eventPublisher.publishEvent(new EmployeeDeletedEvent(employeeId, requestedBy));
    }
}
```

### 7. Security Monitoring and Incident Response

#### Security Event Detection
```java
@Component
public class SecurityEventDetector {
    
    private final RedisTemplate<String, String> redisTemplate;
    private final NotificationService notificationService;
    
    @EventListener
    public void handleFailedLogin(AuthenticationFailureEvent event) {
        String username = event.getAuthentication().getName();
        String clientIp = getClientIpAddress();
        
        // Track failed login attempts
        String key = "failed_login:" + clientIp + ":" + username;
        Long attempts = redisTemplate.opsForValue().increment(key);
        
        if (attempts == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(15));
        }
        
        // Alert on suspicious activity
        if (attempts >= 5) {
            SecurityAlert alert = SecurityAlert.builder()
                .alertType("BRUTE_FORCE_ATTACK")
                .severity("HIGH")
                .description("Multiple failed login attempts detected")
                .sourceIp(clientIp)
                .targetUser(username)
                .attemptCount(attempts.intValue())
                .timestamp(Instant.now())
                .build();
                
            notificationService.sendSecurityAlert(alert);
            
            // Temporarily block IP
            blockIpAddress(clientIp, Duration.ofMinutes(30));
        }
    }
    
    @EventListener
    public void handleSuspiciousDataAccess(DataAccessEvent event) {
        // Detect unusual data access patterns
        if (isUnusualAccessPattern(event)) {
            SecurityAlert alert = SecurityAlert.builder()
                .alertType("UNUSUAL_DATA_ACCESS")
                .severity("MEDIUM")
                .description("Unusual data access pattern detected")
                .userId(event.getUserId())
                .resourceAccessed(event.getResource())
                .timestamp(Instant.now())
                .build();
                
            notificationService.sendSecurityAlert(alert);
        }
    }
    
    private boolean isUnusualAccessPattern(DataAccessEvent event) {
        // Check for:
        // 1. Access outside normal hours
        // 2. Large volume of data access
        // 3. Access to sensitive data by unauthorized roles
        // 4. Geographic anomalies
        
        LocalTime accessTime = event.getTimestamp().atZone(ZoneId.systemDefault()).toLocalTime();
        if (accessTime.isBefore(LocalTime.of(6, 0)) || accessTime.isAfter(LocalTime.of(22, 0))) {
            return true;
        }
        
        // Check access volume in last hour
        String key = "access_count:" + event.getUserId() + ":" + 
                    Instant.now().truncatedTo(ChronoUnit.HOURS);
        Long accessCount = redisTemplate.opsForValue().increment(key);
        
        return accessCount > 100; // Threshold for suspicious activity
    }
    
    private void blockIpAddress(String ipAddress, Duration duration) {
        redisTemplate.opsForValue().set(
            "blocked_ip:" + ipAddress,
            "blocked",
            duration
        );
    }
}
```

#### Incident Response Automation
```java
@Service
public class IncidentResponseService {
    
    private final NotificationService notificationService;
    private final AuditLogger auditLogger;
    
    @EventListener
    public void handleSecurityIncident(SecurityIncidentEvent event) {
        IncidentResponse response = createIncidentResponse(event);
        
        switch (event.getSeverity()) {
            case CRITICAL:
                handleCriticalIncident(response);
                break;
            case HIGH:
                handleHighSeverityIncident(response);
                break;
            case MEDIUM:
                handleMediumSeverityIncident(response);
                break;
            case LOW:
                handleLowSeverityIncident(response);
                break;
        }
    }
    
    private void handleCriticalIncident(IncidentResponse response) {
        // Immediate actions for critical incidents
        
        // 1. Alert security team immediately
        notificationService.sendImmediateAlert(response);
        
        // 2. Disable affected user accounts
        if (response.getAffectedUsers() != null) {
            response.getAffectedUsers().forEach(this::disableUserAccount);
        }
        
        // 3. Block suspicious IP addresses
        if (response.getSuspiciousIps() != null) {
            response.getSuspiciousIps().forEach(ip -> 
                blockIpAddress(ip, Duration.ofHours(24)));
        }
        
        // 4. Initiate security lockdown if necessary
        if (response.requiresLockdown()) {
            initiateSecurityLockdown();
        }
        
        // 5. Log incident
        auditLogger.logSecurityEvent("SYSTEM", "CRITICAL_INCIDENT", 
            "Critical security incident detected: " + response.getDescription());
    }
    
    private void handleHighSeverityIncident(IncidentResponse response) {
        // Alert security team
        notificationService.sendSecurityAlert(response.toSecurityAlert());
        
        // Increase monitoring for affected resources
        increaseMonitoring(response.getAffectedResources());
        
        // Log incident
        auditLogger.logSecurityEvent("SYSTEM", "HIGH_SEVERITY_INCIDENT", 
            response.getDescription());
    }
    
    private void disableUserAccount(String userId) {
        // Implementation to disable user account
        userService.disableAccount(userId, "Security incident");
        
        // Revoke all active sessions
        sessionService.revokeAllSessions(userId);
        
        // Invalidate JWT tokens
        jwtTokenService.revokeAllTokens(userId);
    }
    
    private void initiateSecurityLockdown() {
        // Implementation for security lockdown
        // - Disable non-essential services
        // - Increase authentication requirements
        // - Enable additional logging
        
        systemConfigService.enableSecurityLockdown();
        notificationService.broadcastSecurityLockdown();
    }
}
```

## Security Testing Strategy

### 1. Automated Security Testing

#### OWASP ZAP Integration
```yaml
# CI/CD Pipeline Security Testing
name: Security Testing
on:
  push:
    branches: [ main, develop ]
  pull_request:
    branches: [ main ]

jobs:
  security-test:
    runs-on: ubuntu-latest
    steps:
    - uses: actions/checkout@v3
    
    - name: Run OWASP ZAP Baseline Scan
      uses: zaproxy/action-baseline@v0.7.0
      with:
        target: 'http://localhost:8080'
        rules_file_name: '.zap/rules.tsv'
        cmd_options: '-a'
        
    - name: Run OWASP ZAP Full Scan
      uses: zaproxy/action-full-scan@v0.4.0
      with:
        target: 'http://localhost:8080'
        rules_file_name: '.zap/rules.tsv'
        cmd_options: '-a'
        
    - name: Upload ZAP Results
      uses: actions/upload-artifact@v3
      with:
        name: zap-results
        path: report_html.html
```

#### SonarQube Security Rules
```xml
<!-- sonar-project.properties -->
sonar.projectKey=webgoat-v3
sonar.projectName=WebGoat v3
sonar.projectVersion=1.0
sonar.sources=src/main/java
sonar.tests=src/test/java
sonar.java.binaries=target/classes
sonar.java.test.binaries=target/test-classes
sonar.coverage.jacoco.xmlReportPaths=target/site/jacoco/jacoco.xml

# Security-specific rules
sonar.security.hotspots.enable=true
sonar.security.review.enable=true
```

### 2. Penetration Testing

#### Security Test Cases
```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SecurityIntegrationTest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    void testSqlInjectionPrevention() {
        // Test SQL injection attempts
        String maliciousInput = "'; DROP TABLE employees; --";
        
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/employees/search?name=" + maliciousInput,
            String.class
        );
        
        // Should return 400 Bad Request or sanitized results
        assertThat(response.getStatusCode()).isIn(HttpStatus.BAD_REQUEST, HttpStatus.OK);
        
        // Verify database integrity
        assertThat(employeeRepository.count()).isGreaterThan(0);
    }
    
    @Test
    void testXssProtection() {
        // Test XSS attempts
        String xssPayload = "<script>alert('XSS')</script>";
        
        CreateEmployeeRequest request = CreateEmployeeRequest.builder()
            .firstName(xssPayload)
            .lastName("Test")
            .email("test@example.com")
            .build();
            
        ResponseEntity<String> response = restTemplate.postForEntity(
            "/api/v1/employees",
            request,
            String.class
        );
        
        // Should sanitize input or reject request
        if (response.getStatusCode().is2xxSuccessful()) {
            assertThat(response.getBody()).doesNotContain("<script>");
        }
    }
    
    @Test
    void testUnauthorizedAccess() {
        // Test access without authentication
        ResponseEntity<String> response = restTemplate.getForEntity(
            "/api/v1/employees",
            String.class
        );
        
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }
    
    @Test
    void testRateLimiting() {
        // Test rate limiting
        String token = getValidJwtToken();
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        HttpEntity<String> entity = new HttpEntity<>(headers);
        
        // Make multiple requests rapidly
        for (int i = 0; i < 150; i++) {
            ResponseEntity<String> response = restTemplate.exchange(
                "/api/v1/employees",
                HttpMethod.GET,
                entity,
                String.class
            );
            
            if (i > 100) {
                // Should start rate limiting
                assertThat(response.getStatusCode())
                    .isIn(HttpStatus.TOO_MANY_REQUESTS, HttpStatus.OK);
            }
        }
    }
}
```

## Security Metrics and KPIs

### Security Dashboard
```yaml
# Grafana Dashboard Configuration
dashboard:
  title: "WebGoat Security Metrics"
  panels:
    - title: "Authentication Failures"
      type: "stat"
      targets:
        - expr: "sum(rate(authentication_failures_total[5m]))"
          legendFormat: "Failed Logins/min"
    
    - title: "Security Incidents"
      type: "table"
      targets:
        - expr: "security_incidents_total"
          legendFormat: "{{severity}} - {{type}}"
    
    - title: "Vulnerability Scan Results"
      type: "bargauge"
      targets:
        - expr: "vulnerability_count by (severity)"
          legendFormat: "{{severity}}"
    
    - title: "API Security Metrics"
      type: "timeseries"
      targets:
        - expr: "rate(api_requests_total[5m])"
          legendFormat: "Total Requests"
        - expr: "rate(api_requests_total{status=~"4.."}[5m])"
          legendFormat: "4xx Errors"
        - expr: "rate(api_requests_total{status=~"5.."}[5m])"
          legendFormat: "5xx Errors"
```

### Security KPIs
```java
@Component
public class SecurityMetricsCollector {
    
    private final MeterRegistry meterRegistry;
    
    // Security metrics
    private final Counter authenticationFailures;
    private final Counter securityIncidents;
    private final Gauge vulnerabilityCount;
    private final Timer securityScanDuration;
    
    public SecurityMetricsCollector(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        this.authenticationFailures = Counter.builder("authentication.failures.total")
            .description("Total number of authentication failures")
            .register(meterRegistry);
            
        this.securityIncidents = Counter.builder("security.incidents.total")
            .description("Total number of security incidents")
            .register(meterRegistry);
            
        this.vulnerabilityCount = Gauge.builder("vulnerabilities.count")
            .description("Current number of known vulnerabilities")
            .register(meterRegistry, this, SecurityMetricsCollector::getVulnerabilityCount);
            
        this.securityScanDuration = Timer.builder("security.scan.duration")
            .description("Duration of security scans")
            .register(meterRegistry);
    }
    
    public void recordAuthenticationFailure(String reason) {
        authenticationFailures.increment(Tags.of("reason", reason));
    }
    
    public void recordSecurityIncident(String type, String severity) {
        securityIncidents.increment(Tags.of("type", type, "severity", severity));
    }
    
    private double getVulnerabilityCount() {
        // Implementation to get current vulnerability count
        return vulnerabilityService.getCurrentVulnerabilityCount();
    }
}
```

---

*This security implementation plan provides comprehensive protection for the WebGoat v3 microservices architecture, addressing both legacy vulnerabilities and modern security challenges while ensuring compliance with industry standards and regulations.*