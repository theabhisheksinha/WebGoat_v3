# User Story: SearchStaff Feature in WebGoat_v3

## Epic: GoatHills Financial Employee Management System

### User Story
**As a** GoatHills Financial employee  
**I want to** search for staff members by name  
**So that** I can quickly find and access employee profile information for business operations

---

## Story Details

### User Persona
- **Primary User**: GoatHills Financial Employee (authenticated user)
- **Secondary Users**: Security researchers, penetration testers, students
- **User Level**: Basic to intermediate computer skills
- **Context**: Educational security training environment

### Functional Requirements

#### Core Functionality
1. **Search Input**
   - User can enter partial or complete employee names
   - Search accepts both first name and last name queries
   - Case-insensitive search with wildcard matching
   - Single text input field with submit button

2. **Search Results**
   - Display "Employee [name] not found" for unsuccessful searches
   - Successful searches redirect to employee profile view
   - Search results include complete employee data:
     - Personal information (name, SSN, phone, address)
     - Employment details (title, manager, start date)
     - Financial data (salary, credit card info)
     - Disciplinary records

3. **Navigation Flow**
   - Accessible from ListStaff.jsp via "SearchStaff" action
   - Integrates with ViewProfile.jsp for detailed viewing
   - Maintains session state throughout search process

### Technical Implementation (Cast Analysis Findings)

#### Architecture Components
- **Frontend**: `SearchStaff.jsp` (22 lines, JSP presentation layer)
- **Controller**: `SearchStaff.java` (47 lines, action handler)
- **Business Logic**: `FindProfile.java` (161 lines, search implementation)
- **Database**: Oracle/SQL Server EMPLOYEE table integration

#### Database Schema Integration
```sql
SELECT * FROM employee 
WHERE first_name LIKE ? OR last_name LIKE ?
```

**EMPLOYEE Table Fields**:
- userid (Primary Key)
- first_name, last_name (Search fields)
- ssn, title, phone, address1, address2
- manager, start_date, salary
- ccn, ccn_limit, disciplined_date, disciplined_notes
- personal_description

#### Technology Stack
- **Web Layer**: Java Server Pages (JSP)
- **Application Layer**: Java Servlets
- **Data Layer**: JDBC with PreparedStatement
- **Database**: Oracle Database (primary), SQL Server (secondary)
- **Framework**: WebGoat lesson framework

### Security Context (Educational Vulnerabilities)

#### Intentional Security Weaknesses
1. **Information Disclosure**
   - Returns complete employee profiles including sensitive data
   - No field-level access controls
   - Exposes SSN, salary, and disciplinary information

2. **Access Control Issues**
   - All authenticated users can search all employees
   - No role-based restrictions on search functionality
   - Horizontal privilege escalation potential

3. **Data Enumeration Risk**
   - Wildcard search enables data mining
   - No rate limiting on search requests
   - Potential for automated data extraction

#### Security Controls (Positive Aspects)
1. **SQL Injection Prevention**
   - Uses PreparedStatement with parameterized queries
   - No direct string concatenation in SQL
   - Proper input escaping implemented

2. **Authentication Required**
   - Requires valid user session
   - Integrates with WebGoat authentication system

### Acceptance Criteria

#### Scenario 1: Successful Employee Search
**Given** I am an authenticated GoatHills Financial employee  
**When** I enter "John" in the search field and click "FindProfile"  
**Then** I should be redirected to John's complete employee profile  
**And** I should see all employee details including sensitive information

#### Scenario 2: Unsuccessful Search
**Given** I am an authenticated GoatHills Financial employee  
**When** I enter "NonExistentEmployee" in the search field  
**Then** I should see "Employee NonExistentEmployee not found" message  
**And** I should remain on the search page

#### Scenario 3: Partial Name Search
**Given** I am an authenticated GoatHills Financial employee  
**When** I enter "Smi" in the search field  
**Then** I should find employees with "Smith" in their first or last name  
**And** The system should return the first matching result

#### Scenario 4: Security Vulnerability Demonstration
**Given** I am a security student using WebGoat  
**When** I attempt various search inputs  
**Then** I should be able to demonstrate information disclosure vulnerabilities  
**And** I should learn about proper access control implementation

### Non-Functional Requirements

#### Performance
- Search response time: < 2 seconds
- Database query optimization with proper indexing
- Session management efficiency

#### Security (Educational Context)
- **WARNING**: Contains intentional vulnerabilities for educational purposes
- Must not be deployed in production environments
- Isolated execution environment required

#### Usability
- Simple, intuitive search interface
- Clear error messaging
- Consistent with WebGoat UI patterns

#### Compatibility
- Works with Oracle and SQL Server databases
- Compatible with Java Servlet containers
- Cross-browser compatibility for web interface

### Dependencies

#### System Dependencies
- WebGoat framework v5.3
- Java Runtime Environment
- Database connectivity (Oracle/SQL Server)
- Apache Tomcat or compatible servlet container

#### Lesson Module Dependencies
- GoatHillsFinancial lesson framework
- Employee database with sample data
- User authentication system
- Session management components

### Related User Stories

1. **Employee Profile Management**
   - View employee profiles (ViewProfile.jsp)
   - Edit employee information (EditProfile.jsp)
   - List all staff members (ListStaff.jsp)

2. **Security Learning Objectives**
   - SQL Injection lesson variants
   - Cross-Site Scripting demonstrations
   - Access control testing scenarios

### Educational Value

#### Learning Objectives
1. **Secure Coding Practices**
   - Demonstrate proper use of PreparedStatement
   - Show parameterized query implementation
   - Illustrate input validation techniques

2. **Vulnerability Identification**
   - Information disclosure risks
   - Access control weaknesses
   - Data enumeration threats

3. **Attack Vector Analysis**
   - Search-based exploitation techniques
   - Privilege escalation scenarios
   - Data mining methodologies

#### Success Metrics
- Students can identify information disclosure vulnerabilities
- Users understand proper database query implementation
- Learners recognize access control design flaws
- Participants can implement secure search functionality

### Implementation Notes

#### Cast Analysis Insights
- **Component Distribution**: 4 architectural layers (Web, Logic, Communication, Database)
- **Transaction Complexity**: 42 total transactions, SearchStaff among top 10
- **Database Integration**: 16 tables, EMPLOYEE table central to functionality
- **Security Findings**: 23 critical SQL injection instances across application

#### Code Quality Observations
- Well-structured separation of concerns
- Proper use of design patterns
- Consistent error handling
- Educational documentation embedded

---

## Definition of Done

- [ ] Search functionality works for partial and complete names
- [ ] Error handling displays appropriate messages
- [ ] Integration with employee profile viewing works
- [ ] Security vulnerabilities are demonstrable for educational purposes
- [ ] Database queries use proper PreparedStatement implementation
- [ ] Session management maintains user state correctly
- [ ] Documentation explains both secure and vulnerable aspects
- [ ] Testing covers both functional and security scenarios

---

*This user story is part of the WebGoat v3 educational security platform and contains intentional vulnerabilities for learning purposes. It should never be deployed in production environments.*