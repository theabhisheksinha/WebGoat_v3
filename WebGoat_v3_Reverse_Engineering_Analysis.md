# WebGoat v3 - Reverse Engineering Analysis

*Based on Cast Imaging Code Scan Results*

## Executive Summary

WebGoat v3 is a deliberately vulnerable web application designed for security training. The Cast Imaging analysis reveals a complex multi-layered architecture with **531 total objects** across **42 transactions** and **16 data interaction graphs**.

## Architecture Overview

### Component Distribution

| Component Layer | Object Count | Primary Technologies |
|----------------|--------------|---------------------|
| **Logic Services** | 348 objects | Java Business Logic (111), JEE (12), Servlets (4) |
| **Web Interaction** | 137 objects | JSP Presentation (54), JavaScript (76), Java Web (54) |
| **Database Services** | 30 objects | Oracle Database (24), SQL Server (6) |
| **Communication Services** | 16 objects | Apache Axis2 (2), Java Communication (3) |

### Technology Stack Analysis

#### Core Technologies
- **Java**: 432 objects (primary business logic)
- **JavaScript**: 63 objects (client-side functionality)
- **Apache Tomcat**: 52 objects (web server)
- **Oracle Database**: 24 objects (primary database)
- **Google Cloud SDK**: 16 objects (cloud integration)
- **SQL Server**: 6 objects (secondary database)
- **Apache Axis2**: 2 objects (web services)

#### Unclassified Components
- **105 objects** classified as "Unclassified APIs" indicating potential legacy or custom components

## Transaction Flow Analysis

### Major Entry Points

#### 1. Main Application Flow
- **main.jsp** (Transaction ID: 58475)
  - Size: 1,997 objects
  - Stack: Azure SDK, Java, JSP, Servlet, JavaScript, SQL
  - Primary application entry point

#### 2. Core Application Interface
- **webgoat.jsp** (Transaction ID: 58478)
  - Size: 1,797 objects
  - Stack: Azure SDK, Java, JSP, Servlet, JavaScript, SQL
  - Main application interface

- **webgoat_challenge.jsp** (Transaction ID: 58477)
  - Size: 1,798 objects
  - Similar stack to webgoat.jsp
  - Challenge-specific interface

#### 3. Servlet Operations
- **Catcher Servlet** (Transaction ID: 58468)
  - GET Operation: 1,832 objects
  - POST Operation: 1,798 objects (Transaction ID: 58470)
  - Critical for request handling

- **LessonSource Servlet** (Transaction ID: 58469)
  - POST Operation: 1,812 objects
  - Handles lesson content delivery

### Security Lesson Transactions

#### SQL Injection Lessons
- **EditProfileSQLI.jsp**: 195 objects
- **ListStaffSQLI.jsp**: 195 objects
- **LoginSQLI.jsp**: 83 objects
- **ViewProfileSQLI.jsp**: 207 objects

#### Cross-Site Scripting (XSS) Lessons
- **EditProfileCSS.jsp**: 195 objects
- **ListStaffCSS.jsp**: 195 objects
- **ViewProfileCSS.jsp**: 272 objects (largest XSS transaction)

#### Database-Specific Security Lessons
- **DBSQLInjection** variants: Multiple transactions with 195-207 objects each
- **DBCrossSiteScripting** variants: Similar object counts

#### Role-Based Access Control (RBAC)
- **RBAC** lesson variants: 194-206 objects per transaction
- Focus on access control vulnerabilities

#### AJAX Security
- **clientSideFiltering.jsp**: 77 objects
- Demonstrates client-side security issues

## Database Schema Analysis

### Oracle Database Tables (Primary)

| Table Name | Objects | Purpose |
|------------|---------|----------|
| **EMPLOYEE** | 261 | Employee data for HR scenarios |
| **USER_DATA** | 247 | User profile information |
| **PINS** | 246 | PIN/password data |
| **WEATHER_DATA** | 245 | Sample data for injection demos |
| **TRANSACTIONS** | 244 | Financial transaction records |
| **AUTH** | 238 | Authentication data |
| **MESSAGES** | 238 | Message/communication data |
| **ROLES** | 238 | Role-based access data |
| **SALARIES** | 238 | Salary information |
| **OWNERSHIP** | 238 | Ownership/permission data |
| **USER_DATA_TAN** | 238 | Transaction authentication numbers |
| **MFE_IMAGES** | 238 | Image/media data |
| **PRODUCT_SYSTEM_DATA** | 238 | Product catalog data |
| **USER_SYSTEM_DATA** | 238 | System user data |
| **TAN** | 239 | Transaction authentication |

### SQL Server Database
- **EMPLOYEE** table: 265 objects (duplicate/mirror of Oracle EMPLOYEE)

## Component Dependencies

### Bidirectional Flows
1. **Web Interaction ↔ Logic Services**
   - Heavy interaction between presentation and business logic
   - JSP pages calling Java methods and vice versa

2. **Communication Services ↔ Logic Services**
   - Web services integration with business logic
   - API endpoints calling internal methods

### Unidirectional Flows
1. **Logic Services → Database Services**
   - Business logic accessing database resources
   - SQL queries and stored procedure calls

2. **Web Interaction → Communication Services**
   - Direct web service calls from presentation layer
   - AJAX requests to external services

## Security Architecture Insights

### Vulnerability Categories

#### 1. SQL Injection Vulnerabilities
- **23 critical instances** of "Use of an unsecured data string"
- Found across multiple lesson modules:
  - `DOS_Login.java`
  - `DBSQLInjection.java`
  - `SQLInjection.java`
  - `BlindNumericSqlInjection.java`
  - `BlindStringSqlInjection.java`

#### 2. Cross-Site Scripting (XSS)
- Multiple XSS lesson implementations
- Both reflected and stored XSS scenarios
- Database-driven XSS examples

#### 3. Authentication & Authorization
- Role-based access control lessons
- Multi-level login scenarios
- Session management vulnerabilities

#### 4. AJAX Security
- Client-side filtering bypass
- Asynchronous request vulnerabilities

### Lesson Module Architecture

Each security lesson follows a consistent pattern:
1. **JSP Presentation Layer**: User interface
2. **Java Business Logic**: Vulnerability implementation
3. **Database Interaction**: Data persistence
4. **Instructor Variants**: Teaching materials

## Technology Integration Points

### Web Services
- **Apache Axis2**: SOAP web service framework
- **Google Cloud SDK**: Cloud service integration
- **Azure SDK**: Microsoft cloud services

### Database Connectivity
- **Oracle**: Primary database with 15 tables
- **SQL Server**: Secondary database (1 table)
- **JDBC**: Database connectivity layer

### Presentation Technologies
- **JSP**: Server-side rendering
- **JavaScript**: Client-side functionality
- **HTML/CSS**: Static presentation

## Reverse Engineering Insights

### Code Organization
- **Package Structure**: `org.owasp.webgoat.lessons.*`
- **Lesson Inheritance**: Common `AbstractLesson` base class
- **Database Abstraction**: Centralized database access patterns

### Design Patterns
1. **MVC Architecture**: Clear separation of concerns
2. **Template Method**: Lesson structure standardization
3. **Factory Pattern**: Database connection management
4. **Servlet Pattern**: Request/response handling

### Data Flow Patterns
1. **Request Processing**: JSP → Servlet → Business Logic → Database
2. **Response Generation**: Database → Business Logic → JSP → Client
3. **Error Handling**: Centralized error pages and logging

## Recommendations for Analysis

### Security Assessment
1. **Focus Areas**: SQL injection, XSS, authentication bypass
2. **Critical Files**: Database access classes, input validation
3. **Test Scenarios**: Each lesson represents a specific vulnerability

### Architecture Review
1. **Dependency Analysis**: Heavy coupling between layers
2. **Performance**: Large transaction sizes indicate complex flows
3. **Maintainability**: Consistent lesson structure aids understanding

### Modernization Opportunities
1. **Database Migration**: Consolidate Oracle/SQL Server usage
2. **Framework Updates**: Modernize JSP to newer technologies
3. **Security Hardening**: Fix intentional vulnerabilities for production use

## Conclusion

WebGoat v3 represents a comprehensive security training platform with a well-structured but deliberately vulnerable architecture. The Cast Imaging analysis reveals:

- **Complex multi-layer architecture** with clear separation of concerns
- **Extensive database schema** supporting diverse security scenarios
- **Consistent lesson structure** enabling systematic security education
- **Multiple technology integrations** reflecting real-world enterprise environments
- **Intentional security vulnerabilities** across all major categories

This reverse engineering analysis provides a foundation for understanding the application's architecture, identifying security patterns, and planning potential modernization or security hardening efforts.