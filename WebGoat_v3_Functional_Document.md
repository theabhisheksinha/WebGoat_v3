# WebGoat v3 - Functional Document

## Table of Contents
1. [Executive Summary](#executive-summary)
2. [Application Overview](#application-overview)
3. [Functional Requirements](#functional-requirements)
4. [System Architecture](#system-architecture)
5. [User Interface Design](#user-interface-design)
6. [Security Features](#security-features)
7. [Database Design](#database-design)
8. [Integration Points](#integration-points)
9. [Performance Requirements](#performance-requirements)
10. [Security Considerations](#security-considerations)
11. [Deployment Architecture](#deployment-architecture)
12. [Appendices](#appendices)

## Executive Summary

WebGoat v3 is a deliberately vulnerable web application designed for security education and training. The application serves as a comprehensive platform for learning about web application security vulnerabilities and their mitigation strategies. Built on Java/J2EE technology stack, it provides hands-on experience with common security flaws found in web applications.

### Key Statistics
- **Total Objects**: 531 across all architectural layers
- **Transactions**: 42 functional flows
- **Database Tables**: 16 (Oracle and SQL Server)
- **Security Lessons**: 20+ vulnerability categories
- **Technology Stack**: Java, JSP, JavaScript, Apache Tomcat, Oracle/SQL Server

## Application Overview

### Purpose
WebGoat v3 is an educational platform that allows users to:
- Learn about web application security vulnerabilities
- Practice exploitation techniques in a safe environment
- Understand proper security implementation
- Gain hands-on experience with security testing tools

### Target Users
- Security professionals and penetration testers
- Software developers learning secure coding practices
- Students studying cybersecurity
- IT professionals responsible for web application security

### Core Functionality
- Interactive security lessons with practical exercises
- Vulnerable code examples demonstrating common flaws
- Step-by-step guidance for vulnerability exploitation
- Secure coding examples and best practices
- Progress tracking and lesson completion status

## Functional Requirements

### FR-001: User Authentication and Session Management
**Description**: The system shall provide user authentication and session management capabilities.

**Functional Details**:
- User registration and login functionality
- Session-based authentication
- Role-based access control (RBAC)
- Password management features
- User profile management

**Related Components**:
- `Login.jsp` (Multiple instances across lessons)
- `EditProfile.jsp`
- `ViewProfile.jsp`
- Authentication servlets

### FR-002: Security Lesson Management
**Description**: The system shall provide a comprehensive set of security lessons covering various vulnerability types.

**Functional Details**:
- SQL Injection lessons (Multiple variants)
- Cross-Site Scripting (XSS) demonstrations
- Access control bypass techniques
- AJAX security vulnerabilities
- Database security issues
- Authentication bypass methods

**Related Components**:
- `DBSQLInjection` package
- `CrossSiteScripting` lessons
- `RoleBasedAccessControl` modules
- `Ajax` security demonstrations

### FR-003: Database Interaction and Management
**Description**: The system shall provide database connectivity and data management capabilities.

**Functional Details**:
- Multi-database support (Oracle and SQL Server)
- Employee data management
- Financial transaction processing
- User data storage and retrieval
- Message and communication systems

**Database Tables**:
- `EMPLOYEE` - Staff information management
- `USER_DATA` - User account information
- `TRANSACTIONS` - Financial transaction records
- `MESSAGES` - Communication system
- `SALARIES` - Employee compensation data
- `AUTH` - Authentication credentials
- `ROLES` - Role-based access definitions

### FR-004: Web Service Integration
**Description**: The system shall provide web service capabilities for external integration.

**Functional Details**:
- SOAP web service endpoints
- RESTful API capabilities
- External system integration
- Data exchange protocols

**Related Components**:
- Communication Services layer (16 objects)
- Web service configuration files
- Integration endpoints

### FR-005: Reporting and Analytics
**Description**: The system shall provide reporting capabilities for lesson progress and security metrics.

**Functional Details**:
- User progress tracking
- Lesson completion statistics
- Security vulnerability reports
- Performance analytics

## System Architecture

### Architectural Layers

#### 1. Web Interaction Layer (137 objects)
**Purpose**: Handles user interface and presentation logic

**Components**:
- JSP pages for lesson presentation
- JavaScript for client-side functionality
- HTML forms and user interfaces
- CSS styling and layout

**Key Transactions**:
- `main.jsp` - Primary application entry point
- `webgoat.jsp` - Main lesson interface
- `webgoat_challenge.jsp` - Challenge scenarios

#### 2. Logic Services Layer (348 objects)
**Purpose**: Contains business logic and application processing

**Components**:
- Java business logic classes
- Lesson implementation modules
- Security vulnerability demonstrations
- Application controllers and processors

**Key Packages**:
- `org.owasp.webgoat.lessons.*` - Lesson implementations
- `org.owasp.webgoat.session.*` - Session management
- Business logic processors

#### 3. Communication Services Layer (16 objects)
**Purpose**: Manages external communications and integrations

**Components**:
- Web service endpoints
- External API integrations
- Communication protocols
- Data exchange mechanisms

#### 4. Database Services Layer (30 objects)
**Purpose**: Handles data persistence and database operations

**Components**:
- Database connection management
- Data access objects (DAOs)
- SQL query processors
- Transaction management

### Technology Stack

#### Backend Technologies
- **Java**: Core application logic (432 Java methods)
- **Java Servlets**: Web request processing
- **JSP**: Server-side page generation
- **Apache Tomcat**: Application server (52 objects)

#### Frontend Technologies
- **JavaScript**: Client-side functionality (63 objects)
- **HTML/CSS**: User interface presentation
- **AJAX**: Asynchronous web interactions

#### Database Technologies
- **Oracle Database**: Primary database system (24 objects)
- **SQL Server**: Secondary database system (6 objects)
- **JDBC**: Database connectivity

## User Interface Design

### Main Interface Components

#### 1. Navigation Structure
- Main menu with lesson categories
- Breadcrumb navigation
- Progress indicators
- Help and documentation links

#### 2. Lesson Interface
- Lesson description and objectives
- Interactive exercise areas
- Code examples and demonstrations
- Hint and solution systems

#### 3. Administrative Interface
- User management screens
- Progress monitoring dashboards
- System configuration panels
- Reporting interfaces

### Key User Interfaces

#### Staff Management System
- `ListStaff.jsp` - Employee listing interface
- `SearchStaff.jsp` - Staff search functionality
- `SearchStaffCSS.jsp` - Enhanced search with styling

#### Financial System
- Transaction processing interfaces
- Account management screens
- Financial reporting dashboards

#### Security Lesson Interfaces
- SQL injection demonstration forms
- XSS vulnerability test pages
- Authentication bypass scenarios
- Access control testing interfaces

## Security Features

### Intentional Vulnerabilities (Educational Purpose)

#### 1. SQL Injection Vulnerabilities
**Location**: Multiple lesson modules
**Purpose**: Demonstrate SQL injection attack vectors
**Examples**:
- String concatenation in SQL queries
- Unparameterized database calls
- Dynamic query construction

#### 2. Cross-Site Scripting (XSS)
**Location**: XSS lesson modules
**Purpose**: Show XSS attack techniques
**Examples**:
- Reflected XSS scenarios
- Stored XSS demonstrations
- DOM-based XSS examples

#### 3. Authentication Bypass
**Location**: Authentication lesson modules
**Purpose**: Illustrate authentication weaknesses
**Examples**:
- Weak password policies
- Session management flaws
- Authorization bypass techniques

#### 4. Access Control Issues
**Location**: RBAC lesson modules
**Purpose**: Demonstrate access control failures
**Examples**:
- Privilege escalation scenarios
- Horizontal access control bypass
- Vertical access control failures

### Security Controls (Protective Measures)

#### 1. Environment Isolation
- Sandboxed execution environment
- Isolated database instances
- Controlled network access

#### 2. Educational Safeguards
- Clear vulnerability documentation
- Guided exploitation scenarios
- Secure coding examples

## Database Design

### Database Schema Overview

#### Oracle Database Tables (Primary)
1. **AUTH** - Authentication credentials
2. **EMPLOYEE** - Staff information
3. **MESSAGES** - Communication system
4. **OWNERSHIP** - Asset ownership tracking
5. **PINS** - Personal identification numbers
6. **PRODUCT_SYSTEM_DATA** - Product information
7. **ROLES** - Role definitions
8. **SALARIES** - Compensation data
9. **TAN** - Transaction authentication numbers
10. **TRANSACTIONS** - Financial transactions
11. **USER_DATA** - User account information
12. **USER_DATA_TAN** - User TAN associations
13. **USER_SYSTEM_DATA** - System user data
14. **WEATHER_DATA** - Weather information

#### SQL Server Database Tables (Secondary)
1. **MFE_IMAGES** - Image storage
2. **Additional supporting tables**

### Data Relationships
- User authentication linked to role assignments
- Employee data connected to salary information
- Transaction records tied to user accounts
- Message system integrated with user profiles

### Database Procedures and Functions
- **Oracle Procedures**: 2 stored procedures
- **Oracle Functions**: 4 database functions
- **SQL Server Procedures**: 1 stored procedure
- **SQL Server Functions**: 2 database functions

## Integration Points

### External System Integrations

#### 1. Web Service Endpoints
- SOAP service interfaces
- RESTful API endpoints
- Data exchange protocols

#### 2. Database Connectivity
- JDBC connection pooling
- Multi-database support
- Transaction management

#### 3. Authentication Systems
- LDAP integration capabilities
- Single sign-on (SSO) support
- External authentication providers

### Internal Component Integration

#### 1. Lesson Module Integration
- Standardized lesson interfaces
- Common security frameworks
- Shared utility libraries

#### 2. Session Management
- Cross-module session sharing
- State persistence mechanisms
- User context management

## Performance Requirements

### Response Time Requirements
- **Page Load Time**: < 3 seconds for standard pages
- **Database Queries**: < 1 second for simple queries
- **Complex Transactions**: < 5 seconds for multi-step operations

### Scalability Requirements
- **Concurrent Users**: Support for 50+ simultaneous users
- **Database Connections**: Efficient connection pooling
- **Memory Usage**: Optimized for educational environments

### Availability Requirements
- **Uptime**: 99% availability during training sessions
- **Recovery Time**: < 5 minutes for system restart
- **Backup Frequency**: Daily database backups

## Security Considerations

### Production Deployment Warnings

⚠️ **CRITICAL WARNING**: WebGoat v3 contains intentional security vulnerabilities and should NEVER be deployed in a production environment.

### Recommended Security Measures

#### 1. Network Isolation
- Deploy in isolated network segments
- Restrict external network access
- Use VPN for remote access

#### 2. Access Controls
- Implement strong authentication for administrators
- Limit user access to training purposes only
- Monitor and log all system activities

#### 3. Data Protection
- Use non-production data only
- Implement data encryption for sensitive information
- Regular security assessments

### Vulnerability Management

#### 1. Known Vulnerabilities
- **SQL Injection**: 23 identified instances
- **XSS Vulnerabilities**: Multiple demonstration points
- **Authentication Bypass**: Educational scenarios
- **Access Control Issues**: Lesson-specific implementations

#### 2. Mitigation Strategies
- Educational documentation for each vulnerability
- Secure coding examples provided
- Best practice recommendations included

## Deployment Architecture

### Recommended Deployment Environment

#### 1. Hardware Requirements
- **CPU**: 2+ cores, 2.0 GHz minimum
- **Memory**: 4 GB RAM minimum, 8 GB recommended
- **Storage**: 10 GB available disk space
- **Network**: Isolated network segment

#### 2. Software Requirements
- **Operating System**: Windows/Linux/macOS
- **Java Runtime**: JRE 8 or higher
- **Application Server**: Apache Tomcat 8+
- **Database**: Oracle 11g+ or SQL Server 2012+

#### 3. Network Configuration
- **Port Requirements**: 8080 (HTTP), 8443 (HTTPS)
- **Firewall Rules**: Restrict to training network only
- **SSL/TLS**: Recommended for secure communications

### Installation and Configuration

#### 1. Database Setup
- Create Oracle and SQL Server instances
- Execute database creation scripts
- Configure connection parameters

#### 2. Application Deployment
- Deploy WAR file to Tomcat
- Configure database connections
- Set up user authentication

#### 3. Security Configuration
- Enable logging and monitoring
- Configure access controls
- Set up backup procedures

## Appendices

### Appendix A: Transaction Catalog

#### Primary Transactions (Top 10 by Complexity)
1. **main.jsp** - Main application interface
2. **webgoat.jsp** - Primary lesson platform
3. **Login.jsp** - Authentication interface
4. **ListStaff.jsp** - Employee management
5. **SearchStaff.jsp** - Staff search functionality
6. **EditProfile.jsp** - User profile management
7. **ViewProfile.jsp** - Profile viewing interface
8. **webgoat_challenge.jsp** - Challenge scenarios
9. **SearchStaffCSS.jsp** - Enhanced search interface
10. **clientSideFiltering.jsp** - Client-side data filtering

### Appendix B: Security Lesson Catalog

#### SQL Injection Lessons
- Basic SQL injection techniques
- Blind SQL injection methods
- Advanced SQL injection scenarios
- Parameterized query demonstrations

#### Cross-Site Scripting Lessons
- Reflected XSS demonstrations
- Stored XSS scenarios
- DOM-based XSS examples
- XSS prevention techniques

#### Authentication and Authorization
- Password-based authentication
- Role-based access control
- Session management security
- Authentication bypass techniques

### Appendix C: Database Schema Details

#### Table Relationships
```
USER_DATA (1) -----> (M) USER_DATA_TAN
USER_DATA (1) -----> (M) TRANSACTIONS
EMPLOYEE (1) -----> (1) SALARIES
USER_DATA (1) -----> (M) MESSAGES
ROLES (1) -----> (M) USER_DATA
```

#### Key Indexes
- Primary key indexes on all tables
- Foreign key indexes for relationships
- Performance indexes on frequently queried columns

### Appendix D: Technology Integration Matrix

| Component | Technology | Objects | Purpose |
|-----------|------------|---------|----------|
| Presentation | JSP | 54 | User interface |
| Business Logic | Java | 111 | Application logic |
| Data Access | JDBC | 50 | Database connectivity |
| Web Services | SOAP/REST | 16 | External integration |
| Client-side | JavaScript | 63 | Interactive features |
| Database | Oracle/SQL Server | 30 | Data persistence |

---

**Document Version**: 1.0  
**Last Updated**: January 2025  
**Prepared By**: System Analysis Team  
**Classification**: Educational Use Only  

**Disclaimer**: This document describes a deliberately vulnerable application designed for educational purposes. The application should never be deployed in a production environment due to intentional security vulnerabilities.