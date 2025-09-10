package org.owasp.webgoat.lessons.SQLInjection;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.logging.Logger;
import org.owasp.webgoat.util.InputValidator;

/**
 * SECURITY PATCH: SQL Injection Prevention
 * 
 * This class provides secure database access methods to replace
 * vulnerable string concatenation queries in SQLInjection.java
 * 
 * CAST Imaging Violation: Structural Flaw - SQL Injection
 * Priority: CRITICAL
 */
public class SecureDatabaseAccess {
    
    private static final Logger logger = Logger.getLogger(SecureDatabaseAccess.class.getName());
    private Connection connection;
    
    public SecureDatabaseAccess(Connection connection) {
        this.connection = connection;
    }
    
    /**
     * SECURE: Authenticate user with parameterized query
     * Replaces vulnerable string concatenation in login functionality
     */
    public boolean authenticateUser(String userId, String password) {
        // Input validation
        if (!InputValidator.isValidUsername(userId) || password == null) {
            logger.warning("Invalid authentication attempt with userId: " + userId);
            return false;
        }
        
        // Check for SQL injection patterns
        if (InputValidator.containsSQLInjection(userId) || InputValidator.containsSQLInjection(password)) {
            logger.warning("SQL injection attempt detected in authentication");
            return false;
        }
        
        String sql = "SELECT userid, password FROM employee WHERE userid = ? AND password = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, userId);
            pstmt.setString(2, password);
            
            try (ResultSet rs = pstmt.executeQuery()) {
                boolean authenticated = rs.next();
                if (authenticated) {
                    logger.info("Successful authentication for user: " + userId);
                } else {
                    logger.warning("Failed authentication attempt for user: " + userId);
                }
                return authenticated;
            }
        } catch (SQLException e) {
            logger.severe("Database error during authentication: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * SECURE: Search employees with parameterized query
     * Replaces vulnerable search functionality
     */
    public ResultSet searchEmployees(String searchTerm) {
        // Input validation
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return null;
        }
        
        // Sanitize search term
        searchTerm = searchTerm.trim();
        if (searchTerm.length() > 50) {
            searchTerm = searchTerm.substring(0, 50);
        }
        
        // Check for SQL injection
        if (InputValidator.containsSQLInjection(searchTerm)) {
            logger.warning("SQL injection attempt in search: " + searchTerm);
            return null;
        }
        
        String sql = "SELECT userid, first_name, last_name, phone, title, salary " +
                    "FROM employee WHERE first_name LIKE ? OR last_name LIKE ? " +
                    "ORDER BY last_name, first_name";
        
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            String searchPattern = "%" + searchTerm + "%";
            pstmt.setString(1, searchPattern);
            pstmt.setString(2, searchPattern);
            
            return pstmt.executeQuery();
        } catch (SQLException e) {
            logger.severe("Database error during employee search: " + e.getMessage());
            return null;
        }
    }
    
    /**
     * SECURE: Get employee by ID with parameterized query
     * Replaces vulnerable employee lookup
     */
    public ResultSet getEmployeeById(String employeeId) {
        // Input validation
        if (!InputValidator.isValidEmployeeId(employeeId)) {
            logger.warning("Invalid employee ID format: " + employeeId);
            return null;
        }
        
        String sql = "SELECT userid, first_name, last_name, phone, title, salary, " +
                    "address1, address2, manager, start_date, ssn, salary " +
                    "FROM employee WHERE userid = ?";
        
        try {
            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setInt(1, Integer.parseInt(employeeId));
            
            return pstmt.executeQuery();
        } catch (SQLException e) {
            logger.severe("Database error during employee lookup: " + e.getMessage());
            return null;
        } catch (NumberFormatException e) {
            logger.warning("Invalid employee ID number format: " + employeeId);
            return null;
        }
    }
    
    /**
     * SECURE: Update employee profile with parameterized query
     */
    public boolean updateEmployeeProfile(String employeeId, String firstName, String lastName, 
                                       String phone, String address1, String address2) {
        // Input validation
        if (!InputValidator.isValidEmployeeId(employeeId)) {
            return false;
        }
        
        // Sanitize inputs
        firstName = sanitizeInput(firstName, 50);
        lastName = sanitizeInput(lastName, 50);
        phone = sanitizeInput(phone, 20);
        address1 = sanitizeInput(address1, 100);
        address2 = sanitizeInput(address2, 100);
        
        String sql = "UPDATE employee SET first_name = ?, last_name = ?, phone = ?, " +
                    "address1 = ?, address2 = ? WHERE userid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, firstName);
            pstmt.setString(2, lastName);
            pstmt.setString(3, phone);
            pstmt.setString(4, address1);
            pstmt.setString(5, address2);
            pstmt.setInt(6, Integer.parseInt(employeeId));
            
            int rowsUpdated = pstmt.executeUpdate();
            return rowsUpdated > 0;
        } catch (SQLException e) {
            logger.severe("Database error during profile update: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * SECURE: Delete employee with parameterized query
     */
    public boolean deleteEmployee(String employeeId) {
        // Input validation
        if (!InputValidator.isValidEmployeeId(employeeId)) {
            return false;
        }
        
        String sql = "DELETE FROM employee WHERE userid = ?";
        
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setInt(1, Integer.parseInt(employeeId));
            
            int rowsDeleted = pstmt.executeUpdate();
            if (rowsDeleted > 0) {
                logger.info("Employee deleted: " + employeeId);
                return true;
            }
            return false;
        } catch (SQLException e) {
            logger.severe("Database error during employee deletion: " + e.getMessage());
            return false;
        }
    }
    
    /**
     * Helper method to sanitize string inputs
     */
    private String sanitizeInput(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        
        // Remove potential SQL injection characters
        input = input.replaceAll("[';\"\\-\\-]", "");
        
        // Trim and limit length
        input = input.trim();
        if (input.length() > maxLength) {
            input = input.substring(0, maxLength);
        }
        
        return input;
    }
}