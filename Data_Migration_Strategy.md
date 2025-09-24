# WebGoat v3 Data Migration Strategy

## Overview

This document outlines the comprehensive data migration strategy for transitioning WebGoat v3 from a monolithic database architecture to a microservices-based data architecture with database-per-service pattern.

## Current Database Analysis (CAST Imaging)

### Database Inventory
- **Oracle Database**: 24 objects (16 tables, 4 procedures, 4 functions)
- **SQL Server Database**: 6 objects (1 table, 2 procedures, 3 functions)
- **Missing Tables**: 1 object (employee1)

### Data Relationships (17 Data Graphs)
```
AUTH → 240 interactions
EMPLOYEE → 263 interactions (Oracle), 267 interactions (SQL Server)
MESSAGES → 240 interactions
TRANSACTIONS → 246 interactions
USER_DATA → 249 interactions
```

## Target Database Architecture

### Service-Database Mapping

#### 1. Authentication Service Database (PostgreSQL)
```sql
-- Tables to migrate:
-- AUTH (Oracle) → auth_users
-- ROLES (Oracle) → user_roles
-- USER_SYSTEM_DATA (Oracle) → user_system_config

CREATE DATABASE webgoat_auth;

CREATE TABLE auth_users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(255) UNIQUE NOT NULL,
    password_hash VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_roles (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES auth_users(id),
    role_name VARCHAR(100) NOT NULL,
    granted_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_system_config (
    id SERIAL PRIMARY KEY,
    user_id INTEGER REFERENCES auth_users(id),
    config_key VARCHAR(255) NOT NULL,
    config_value TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 2. Employee Management Service Database (PostgreSQL)
```sql
-- Tables to migrate:
-- EMPLOYEE (Oracle/SQL Server) → employees
-- SALARIES (Oracle) → employee_salaries
-- USER_DATA (Oracle) → employee_profiles

CREATE DATABASE webgoat_employees;

CREATE TABLE employees (
    id SERIAL PRIMARY KEY,
    employee_id VARCHAR(50) UNIQUE NOT NULL,
    first_name VARCHAR(255) NOT NULL,
    last_name VARCHAR(255) NOT NULL,
    email VARCHAR(255) UNIQUE,
    department VARCHAR(100),
    position VARCHAR(100),
    hire_date DATE,
    is_active BOOLEAN DEFAULT true,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE employee_salaries (
    id SERIAL PRIMARY KEY,
    employee_id INTEGER REFERENCES employees(id),
    salary_amount DECIMAL(12,2),
    currency VARCHAR(3) DEFAULT 'USD',
    effective_date DATE,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE employee_profiles (
    id SERIAL PRIMARY KEY,
    employee_id INTEGER REFERENCES employees(id),
    profile_data JSONB,
    preferences JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 3. Security Lesson Service Database (MongoDB)
```javascript
// Collections to migrate:
// PRODUCT_SYSTEM_DATA → lesson_content
// MFE_IMAGES → lesson_media
// MESSAGES → lesson_messages

use webgoat_lessons;

// Lesson Content Collection
db.createCollection("lesson_content", {
   validator: {
      $jsonSchema: {
         bsonType: "object",
         required: ["lesson_id", "title", "content_type"],
         properties: {
            lesson_id: { bsonType: "string" },
            title: { bsonType: "string" },
            content_type: { enum: ["sql_injection", "xss", "csrf", "access_control"] },
            content: { bsonType: "object" },
            difficulty_level: { bsonType: "int", minimum: 1, maximum: 5 },
            created_at: { bsonType: "date" },
            updated_at: { bsonType: "date" }
         }
      }
   }
});

// Lesson Media Collection
db.createCollection("lesson_media", {
   validator: {
      $jsonSchema: {
         bsonType: "object",
         required: ["lesson_id", "media_type", "file_path"],
         properties: {
            lesson_id: { bsonType: "string" },
            media_type: { enum: ["image", "video", "document"] },
            file_path: { bsonType: "string" },
            file_size: { bsonType: "long" },
            mime_type: { bsonType: "string" },
            created_at: { bsonType: "date" }
         }
      }
   }
});

// Lesson Messages Collection
db.createCollection("lesson_messages", {
   validator: {
      $jsonSchema: {
         bsonType: "object",
         required: ["lesson_id", "message_type", "content"],
         properties: {
            lesson_id: { bsonType: "string" },
            message_type: { enum: ["hint", "error", "success", "warning"] },
            content: { bsonType: "string" },
            display_order: { bsonType: "int" },
            created_at: { bsonType: "date" }
         }
      }
   }
});
```

#### 4. Financial Transaction Service Database (PostgreSQL)
```sql
-- Tables to migrate:
-- TRANSACTIONS (Oracle) → financial_transactions
-- PINS (Oracle) → security_pins
-- TAN (Oracle) → transaction_numbers
-- USER_DATA_TAN (Oracle) → user_tan_mapping

CREATE DATABASE webgoat_financial;

CREATE TABLE financial_transactions (
    id SERIAL PRIMARY KEY,
    transaction_id VARCHAR(100) UNIQUE NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    transaction_type VARCHAR(50) NOT NULL,
    amount DECIMAL(15,2),
    currency VARCHAR(3) DEFAULT 'USD',
    status VARCHAR(20) DEFAULT 'pending',
    description TEXT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    processed_at TIMESTAMP
);

CREATE TABLE security_pins (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    pin_hash VARCHAR(255) NOT NULL,
    is_active BOOLEAN DEFAULT true,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE transaction_numbers (
    id SERIAL PRIMARY KEY,
    tan_value VARCHAR(20) UNIQUE NOT NULL,
    user_id VARCHAR(50) NOT NULL,
    is_used BOOLEAN DEFAULT false,
    expires_at TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE user_tan_mapping (
    id SERIAL PRIMARY KEY,
    user_id VARCHAR(50) NOT NULL,
    tan_id INTEGER REFERENCES transaction_numbers(id),
    assigned_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

#### 5. Content Delivery Service Database (PostgreSQL)
```sql
-- Tables to migrate:
-- WEATHER_DATA (Oracle) → content_metadata
-- OWNERSHIP (Oracle) → content_ownership

CREATE DATABASE webgoat_content;

CREATE TABLE content_metadata (
    id SERIAL PRIMARY KEY,
    content_id VARCHAR(100) UNIQUE NOT NULL,
    content_type VARCHAR(50) NOT NULL,
    title VARCHAR(255),
    description TEXT,
    file_path VARCHAR(500),
    file_size BIGINT,
    mime_type VARCHAR(100),
    metadata JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE content_ownership (
    id SERIAL PRIMARY KEY,
    content_id INTEGER REFERENCES content_metadata(id),
    owner_id VARCHAR(50) NOT NULL,
    owner_type VARCHAR(20) NOT NULL, -- 'user', 'system', 'lesson'
    permissions JSONB,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
);
```

## Migration Phases

### Phase 1: Data Extraction and Analysis

#### 1.1 Oracle Data Extraction Scripts
```sql
-- Extract AUTH table
SELECT * FROM WEBGOAT_GUEST.AUTH;

-- Extract EMPLOYEE table
SELECT * FROM WEBGOAT_GUEST.EMPLOYEE;

-- Extract all tables with row counts
SELECT 
    table_name,
    num_rows
FROM user_tables 
WHERE table_name IN (
    'AUTH', 'EMPLOYEE', 'MESSAGES', 'TRANSACTIONS', 
    'USER_DATA', 'ROLES', 'SALARIES'
);
```

#### 1.2 SQL Server Data Extraction Scripts
```sql
-- Extract EMPLOYEE table from SQL Server
SELECT * FROM webgoat.WEBGOAT_guest.EMPLOYEE;

-- Get table schemas
SELECT 
    COLUMN_NAME,
    DATA_TYPE,
    IS_NULLABLE,
    CHARACTER_MAXIMUM_LENGTH
FROM INFORMATION_SCHEMA.COLUMNS
WHERE TABLE_NAME = 'EMPLOYEE';
```

### Phase 2: Data Transformation and Mapping

#### 2.1 Data Transformation Scripts
```python
# Python script for data transformation
import pandas as pd
import json
from datetime import datetime

def transform_auth_data(oracle_auth_df):
    """
    Transform Oracle AUTH table to PostgreSQL auth_users format
    """
    transformed_df = pd.DataFrame({
        'username': oracle_auth_df['USERNAME'],
        'password_hash': oracle_auth_df['PASSWORD'],
        'email': oracle_auth_df['EMAIL'],
        'is_active': oracle_auth_df['STATUS'].map({'ACTIVE': True, 'INACTIVE': False}),
        'created_at': pd.to_datetime(oracle_auth_df['CREATED_DATE']),
        'updated_at': datetime.now()
    })
    return transformed_df

def transform_employee_data(oracle_employee_df, sqlserver_employee_df):
    """
    Merge and transform employee data from both databases
    """
    # Combine Oracle and SQL Server employee data
    combined_df = pd.concat([oracle_employee_df, sqlserver_employee_df], ignore_index=True)
    
    # Remove duplicates based on employee_id
    combined_df = combined_df.drop_duplicates(subset=['EMPLOYEE_ID'])
    
    transformed_df = pd.DataFrame({
        'employee_id': combined_df['EMPLOYEE_ID'],
        'first_name': combined_df['FIRST_NAME'],
        'last_name': combined_df['LAST_NAME'],
        'email': combined_df['EMAIL'],
        'department': combined_df['DEPARTMENT'],
        'position': combined_df['POSITION'],
        'hire_date': pd.to_datetime(combined_df['HIRE_DATE']),
        'is_active': True,
        'created_at': datetime.now(),
        'updated_at': datetime.now()
    })
    return transformed_df

def transform_lesson_content(oracle_product_data_df):
    """
    Transform Oracle PRODUCT_SYSTEM_DATA to MongoDB lesson_content format
    """
    lesson_documents = []
    
    for _, row in oracle_product_data_df.iterrows():
        document = {
            'lesson_id': row['PRODUCT_ID'],
            'title': row['PRODUCT_NAME'],
            'content_type': determine_lesson_type(row['PRODUCT_TYPE']),
            'content': {
                'description': row['DESCRIPTION'],
                'objectives': json.loads(row['OBJECTIVES']) if row['OBJECTIVES'] else [],
                'instructions': row['INSTRUCTIONS']
            },
            'difficulty_level': int(row['DIFFICULTY_LEVEL']) if row['DIFFICULTY_LEVEL'] else 1,
            'created_at': datetime.now(),
            'updated_at': datetime.now()
        }
        lesson_documents.append(document)
    
    return lesson_documents

def determine_lesson_type(product_type):
    """
    Map product types to lesson content types
    """
    type_mapping = {
        'SQL_INJECTION': 'sql_injection',
        'XSS': 'xss',
        'CSRF': 'csrf',
        'ACCESS_CONTROL': 'access_control'
    }
    return type_mapping.get(product_type, 'general')
```

### Phase 3: Dual-Write Implementation

#### 3.1 Dual-Write Pattern Implementation
```java
@Service
public class EmployeeService {
    
    @Autowired
    private LegacyEmployeeRepository legacyRepo;
    
    @Autowired
    private NewEmployeeRepository newRepo;
    
    @Autowired
    private EventPublisher eventPublisher;
    
    @Transactional
    public Employee createEmployee(Employee employee) {
        try {
            // Write to legacy database
            Employee legacyEmployee = legacyRepo.save(employee);
            
            // Write to new database
            Employee newEmployee = newRepo.save(employee);
            
            // Publish event for other services
            eventPublisher.publishEvent(new EmployeeCreatedEvent(newEmployee));
            
            return newEmployee;
        } catch (Exception e) {
            // Rollback and handle errors
            throw new DataMigrationException("Failed to create employee", e);
        }
    }
    
    @Transactional
    public Employee updateEmployee(Long id, Employee employee) {
        try {
            // Update legacy database
            Employee legacyEmployee = legacyRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
            legacyEmployee.updateFrom(employee);
            legacyRepo.save(legacyEmployee);
            
            // Update new database
            Employee newEmployee = newRepo.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Employee not found"));
            newEmployee.updateFrom(employee);
            newRepo.save(newEmployee);
            
            // Publish event
            eventPublisher.publishEvent(new EmployeeUpdatedEvent(newEmployee));
            
            return newEmployee;
        } catch (Exception e) {
            throw new DataMigrationException("Failed to update employee", e);
        }
    }
}
```

### Phase 4: Data Synchronization and Validation

#### 4.1 Data Consistency Validation Scripts
```sql
-- Validate data consistency between legacy and new databases

-- Check record counts
SELECT 
    'Legacy Oracle AUTH' as source,
    COUNT(*) as record_count
FROM WEBGOAT_GUEST.AUTH
UNION ALL
SELECT 
    'New PostgreSQL auth_users' as source,
    COUNT(*) as record_count
FROM webgoat_auth.auth_users;

-- Check for missing records
SELECT 
    o.username
FROM WEBGOAT_GUEST.AUTH o
LEFT JOIN webgoat_auth.auth_users n ON o.username = n.username
WHERE n.username IS NULL;

-- Validate data integrity
SELECT 
    COUNT(*) as total_records,
    COUNT(CASE WHEN username IS NOT NULL THEN 1 END) as valid_usernames,
    COUNT(CASE WHEN password_hash IS NOT NULL THEN 1 END) as valid_passwords
FROM webgoat_auth.auth_users;
```

#### 4.2 Data Synchronization Service
```java
@Component
public class DataSynchronizationService {
    
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    public void synchronizeEmployeeData() {
        List<Employee> legacyEmployees = legacyEmployeeRepository.findModifiedSince(
            LocalDateTime.now().minusMinutes(5)
        );
        
        for (Employee legacyEmployee : legacyEmployees) {
            try {
                Employee newEmployee = newEmployeeRepository.findByEmployeeId(
                    legacyEmployee.getEmployeeId()
                );
                
                if (newEmployee == null) {
                    // Create new record
                    newEmployee = new Employee();
                    newEmployee.copyFrom(legacyEmployee);
                    newEmployeeRepository.save(newEmployee);
                } else if (!newEmployee.equals(legacyEmployee)) {
                    // Update existing record
                    newEmployee.copyFrom(legacyEmployee);
                    newEmployeeRepository.save(newEmployee);
                }
            } catch (Exception e) {
                log.error("Failed to synchronize employee: {}", 
                    legacyEmployee.getEmployeeId(), e);
            }
        }
    }
}
```

### Phase 5: Cutover and Legacy Decommissioning

#### 5.1 Cutover Checklist
```markdown
## Pre-Cutover Validation
- [ ] All data migrated successfully
- [ ] Data consistency validation passed
- [ ] Performance testing completed
- [ ] Backup procedures verified
- [ ] Rollback plan documented

## Cutover Steps
1. [ ] Stop writes to legacy database
2. [ ] Perform final data synchronization
3. [ ] Switch application to read from new databases
4. [ ] Verify application functionality
5. [ ] Monitor system performance
6. [ ] Update DNS/load balancer configurations

## Post-Cutover Validation
- [ ] All services operational
- [ ] Data integrity maintained
- [ ] Performance metrics within acceptable range
- [ ] No data loss detected
- [ ] User acceptance testing passed
```

#### 5.2 Legacy Database Decommissioning
```sql
-- Create final backup before decommissioning
EXPORT DATA OPTIONS(
  uri='gs://webgoat-backup/legacy-final-backup-*.csv',
  format='CSV',
  overwrite=true,
  header=true
) AS
SELECT * FROM WEBGOAT_GUEST.AUTH
UNION ALL
SELECT * FROM WEBGOAT_GUEST.EMPLOYEE
-- ... other tables

-- Archive legacy data
CREATE TABLE WEBGOAT_GUEST.AUTH_ARCHIVE AS
SELECT *, CURRENT_TIMESTAMP as archived_at
FROM WEBGOAT_GUEST.AUTH;

-- Drop legacy tables (after confirmation)
-- DROP TABLE WEBGOAT_GUEST.AUTH;
-- DROP TABLE WEBGOAT_GUEST.EMPLOYEE;
```

## Monitoring and Alerting

### Data Migration Metrics
```yaml
# Prometheus metrics configuration
metrics:
  - name: data_migration_records_processed
    type: counter
    description: "Number of records processed during migration"
    labels: ["source_table", "target_service"]
    
  - name: data_migration_errors
    type: counter
    description: "Number of errors during data migration"
    labels: ["error_type", "service"]
    
  - name: data_consistency_check_duration
    type: histogram
    description: "Time taken for data consistency checks"
    labels: ["check_type"]
```

### Alerting Rules
```yaml
# Grafana alerting rules
alerts:
  - name: "Data Migration Error Rate High"
    condition: "rate(data_migration_errors[5m]) > 0.1"
    severity: "critical"
    
  - name: "Data Consistency Check Failed"
    condition: "data_consistency_check_status == 0"
    severity: "warning"
    
  - name: "Migration Performance Degraded"
    condition: "data_migration_records_processed_rate < 100"
    severity: "warning"
```

## Risk Mitigation

### Data Loss Prevention
1. **Multiple Backups**: Create backups at each migration phase
2. **Incremental Migration**: Migrate data in small batches
3. **Validation Checkpoints**: Verify data integrity at each step
4. **Rollback Procedures**: Maintain ability to rollback at any point

### Performance Impact Mitigation
1. **Off-Peak Migration**: Schedule intensive operations during low-traffic periods
2. **Read Replicas**: Use read replicas to minimize impact on production
3. **Connection Pooling**: Optimize database connections
4. **Batch Processing**: Process data in optimal batch sizes

## Success Criteria

1. **Data Integrity**: 100% data consistency between source and target
2. **Zero Data Loss**: No records lost during migration
3. **Performance**: Migration completed within planned timeframe
4. **Availability**: Less than 4 hours total downtime
5. **Validation**: All automated tests pass post-migration

---

*This data migration strategy ensures a safe, reliable, and efficient transition from the monolithic database architecture to a microservices-based data architecture for WebGoat v3.*