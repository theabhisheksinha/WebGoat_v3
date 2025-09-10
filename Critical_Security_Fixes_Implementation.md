# Critical Security Vulnerabilities - Implementation Fixes

## Overview
This document provides specific code fixes for the critical security vulnerabilities identified in WebGoat_v3 by CAST Imaging analysis.

## Phase 1: Critical Security Fixes (Immediate Priority)

### 1. SQL Injection Prevention

#### Vulnerability Location: SQLInjection.java
**File:** `webgoat/JavaSource/org/owasp/webgoat/lessons/SQLInjection/SQLInjection.java`

**Current Vulnerable Pattern:**
```java
// Vulnerable code pattern found in hints
"SELECT * FROM employee WHERE userid = " + userId + " and password = " + password
```

**Fix Implementation:**
```java
// SECURE: Use PreparedStatement with parameterized queries
public boolean authenticateUser(String userId, String password) {
    String sql = "SELECT * FROM employee WHERE userid = ? AND password = ?";
    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
        pstmt.setString(1, userId);
        pstmt.setString(2, password);
        ResultSet rs = pstmt.executeQuery();
        return rs.next();
    } catch (SQLException e) {
        logger.error("Database error during authentication", e);
        return false;
    }
}
```

#### Additional SQL Injection Fixes:

**Search Functionality Fix:**
```java
// BEFORE (Vulnerable)
String query = "SELECT * FROM employee WHERE name LIKE '%" + searchTerm + "%'";

// AFTER (Secure)
String query = "SELECT * FROM employee WHERE name LIKE ?";
PreparedStatement pstmt = connection.prepareStatement(query);
pstmt.setString(1, "%" + searchTerm + "%");
```

**Employee ID Lookup Fix:**
```java
// BEFORE (Vulnerable)
String query = "SELECT * FROM employee WHERE employee_id = " + employeeId;

// AFTER (Secure)
String query = "SELECT * FROM employee WHERE employee_id = ?";
PreparedStatement pstmt = connection.prepareStatement(query);
pstmt.setInt(1, Integer.parseInt(employeeId));
```

### 2. Cross-Site Scripting (XSS) Prevention

#### Vulnerability Location: ReflectedXSS.java
**File:** `webgoat/JavaSource/org/owasp/webgoat/lessons/ReflectedXSS.java`

**Current Vulnerable Code (Line ~175):**
```java
// VULNERABLE: Direct output without encoding
tr.addElement(new TD().addElement("<input name='field1' type='TEXT' value='" + param1 + "'>"));
```

**Fix Implementation:**
```java
// SECURE: Proper HTML encoding
import org.owasp.webgoat.util.HtmlEncoder;

// Replace vulnerable line with:
tr.addElement(new TD().addElement("<input name='field1' type='TEXT' value='" + HtmlEncoder.encode(param1) + "'>"));
```

**Additional XSS Prevention Methods:**
```java
// Input Validation and Sanitization
public String sanitizeInput(String input) {
    if (input == null) return "";
    
    // Remove script tags and dangerous content
    input = input.replaceAll("(?i)<script[^>]*>.*?</script>", "");
    input = input.replaceAll("(?i)<.*?javascript:.*?>", "");
    input = input.replaceAll("(?i)on\\w+\\s*=", "");
    
    // HTML encode the result
    return HtmlEncoder.encode(input);
}

// Output Encoding for Different Contexts
public String encodeForHTML(String input) {
    return HtmlEncoder.encode(input);
}

public String encodeForJavaScript(String input) {
    return input.replace("\\", "\\\\")
                .replace("'", "\\'")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r");
}
```

#### Stored XSS Fix
**File:** `webgoat/JavaSource/org/owasp/webgoat/lessons/StoredXss.java`

```java
// SECURE: Encode all user input before storing and displaying
public void storeUserComment(String comment, String username) {
    // Sanitize and encode before storage
    String safeComment = sanitizeInput(comment);
    String safeUsername = HtmlEncoder.encode(username);
    
    // Store in database with prepared statement
    String sql = "INSERT INTO comments (username, comment, created_date) VALUES (?, ?, ?)";
    try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
        pstmt.setString(1, safeUsername);
        pstmt.setString(2, safeComment);
        pstmt.setTimestamp(3, new Timestamp(System.currentTimeMillis()));
        pstmt.executeUpdate();
    } catch (SQLException e) {
        logger.error("Error storing comment", e);
    }
}
```

### 3. Input Validation Framework

**Create:** `webgoat/JavaSource/org/owasp/webgoat/util/InputValidator.java`

```java
package org.owasp.webgoat.util;

import java.util.regex.Pattern;

public class InputValidator {
    
    private static final Pattern ALPHANUMERIC = Pattern.compile("^[a-zA-Z0-9]+$");
    private static final Pattern EMAIL = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    private static final Pattern NUMERIC = Pattern.compile("^[0-9]+$");
    
    public static boolean isValidEmployeeId(String input) {
        return input != null && NUMERIC.matcher(input).matches() && input.length() <= 10;
    }
    
    public static boolean isValidUsername(String input) {
        return input != null && ALPHANUMERIC.matcher(input).matches() && 
               input.length() >= 3 && input.length() <= 20;
    }
    
    public static boolean isValidEmail(String input) {
        return input != null && EMAIL.matcher(input).matches() && input.length() <= 100;
    }
    
    public static String sanitizeForSQL(String input) {
        if (input == null) return null;
        // Remove SQL injection patterns
        return input.replaceAll("[';\"\\-\\-]", "");
    }
    
    public static boolean containsSQLInjection(String input) {
        if (input == null) return false;
        String lowerInput = input.toLowerCase();
        String[] sqlKeywords = {"union", "select", "insert", "update", "delete", 
                               "drop", "create", "alter", "exec", "execute", "--", "/*"};
        
        for (String keyword : sqlKeywords) {
            if (lowerInput.contains(keyword)) {
                return true;
            }
        }
        return false;
    }
}
```

### 4. Security Configuration Updates

**Update:** `webgoat/WEB-INF/web.xml`

```xml
<!-- Add security headers filter -->
<filter>
    <filter-name>SecurityHeadersFilter</filter-name>
    <filter-class>org.owasp.webgoat.filters.SecurityHeadersFilter</filter-class>
</filter>
<filter-mapping>
    <filter-name>SecurityHeadersFilter</filter-name>
    <url-pattern>/*</url-pattern>
</filter-mapping>

<!-- Content Security Policy -->
<context-param>
    <param-name>contentSecurityPolicy</param-name>
    <param-value>default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'</param-value>
</context-param>
```

**Create:** `webgoat/JavaSource/org/owasp/webgoat/filters/SecurityHeadersFilter.java`

```java
package org.owasp.webgoat.filters;

import javax.servlet.*;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

public class SecurityHeadersFilter implements Filter {
    
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        
        HttpServletResponse httpResponse = (HttpServletResponse) response;
        
        // Prevent XSS attacks
        httpResponse.setHeader("X-XSS-Protection", "1; mode=block");
        
        // Prevent clickjacking
        httpResponse.setHeader("X-Frame-Options", "DENY");
        
        // Prevent MIME type sniffing
        httpResponse.setHeader("X-Content-Type-Options", "nosniff");
        
        // Content Security Policy
        httpResponse.setHeader("Content-Security-Policy", 
            "default-src 'self'; script-src 'self' 'unsafe-inline'; style-src 'self' 'unsafe-inline'");
        
        chain.doFilter(request, response);
    }
    
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {}
    
    @Override
    public void destroy() {}
}
```

## Implementation Timeline

### Week 1: SQL Injection Fixes
- [ ] Update SQLInjection.java with parameterized queries
- [ ] Fix all database query methods in GoatHillsFinancial classes
- [ ] Create InputValidator utility class
- [ ] Test all authentication and search functionality

### Week 2: XSS Prevention
- [ ] Fix ReflectedXSS.java output encoding
- [ ] Update StoredXss.java with input sanitization
- [ ] Implement SecurityHeadersFilter
- [ ] Update web.xml with security configurations
- [ ] Test all user input fields and outputs

## Testing Checklist

### SQL Injection Tests
- [ ] Test login with `' OR '1'='1` - should fail
- [ ] Test search with `'; DROP TABLE employee; --` - should fail
- [ ] Test employee ID with `101 OR 1=1` - should fail
- [ ] Verify legitimate queries still work

### XSS Tests
- [ ] Test input fields with `<script>alert('XSS')</script>` - should be encoded
- [ ] Test comment storage with malicious scripts - should be sanitized
- [ ] Verify security headers are present in responses
- [ ] Test legitimate HTML content still displays correctly

## Success Metrics
- **SQL Injection**: 0 vulnerable database queries
- **XSS**: 0 unencoded user outputs
- **Security Headers**: 100% coverage on all responses
- **Input Validation**: All user inputs validated and sanitized

## Monitoring
- Use CAST Imaging to verify violation reduction
- Implement logging for security events
- Regular security testing with OWASP ZAP
- Code review for all new database and user input handling code