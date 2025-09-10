package org.owasp.webgoat.util;

import java.util.regex.Pattern;
import java.util.logging.Logger;

/**
 * SECURITY PATCH: XSS Prevention Utilities
 * 
 * This class provides secure encoding and sanitization methods to prevent
 * Cross-Site Scripting (XSS) attacks in WebGoat application
 * 
 * CAST Imaging Violations: 
 * - Structural Flaw - Reflected XSS
 * - Structural Flaw - Persistent XSS
 * Priority: CRITICAL
 */
public class XSSPrevention {
    
    private static final Logger logger = Logger.getLogger(XSSPrevention.class.getName());
    
    // Dangerous HTML patterns
    private static final Pattern SCRIPT_PATTERN = Pattern.compile(
        "(?i)<script[^>]*>.*?</script>", Pattern.DOTALL);
    private static final Pattern JAVASCRIPT_PATTERN = Pattern.compile(
        "(?i)javascript:", Pattern.DOTALL);
    private static final Pattern VBSCRIPT_PATTERN = Pattern.compile(
        "(?i)vbscript:", Pattern.DOTALL);
    private static final Pattern ONLOAD_PATTERN = Pattern.compile(
        "(?i)on\\w+\\s*=", Pattern.DOTALL);
    private static final Pattern IFRAME_PATTERN = Pattern.compile(
        "(?i)<iframe[^>]*>.*?</iframe>", Pattern.DOTALL);
    private static final Pattern OBJECT_PATTERN = Pattern.compile(
        "(?i)<object[^>]*>.*?</object>", Pattern.DOTALL);
    private static final Pattern EMBED_PATTERN = Pattern.compile(
        "(?i)<embed[^>]*>", Pattern.DOTALL);
    
    /**
     * SECURE: HTML encode user input to prevent XSS
     * Use this for all user data displayed in HTML context
     */
    public static String encodeForHTML(String input) {
        if (input == null) {
            return "";
        }
        
        StringBuilder encoded = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '<':
                    encoded.append("&lt;");
                    break;
                case '>':
                    encoded.append("&gt;");
                    break;
                case '&':
                    encoded.append("&amp;");
                    break;
                case '"':
                    encoded.append("&quot;");
                    break;
                case '\'':
                    encoded.append("&#x27;");
                    break;
                case '/':
                    encoded.append("&#x2F;");
                    break;
                default:
                    encoded.append(c);
                    break;
            }
        }
        
        return encoded.toString();
    }
    
    /**
     * SECURE: JavaScript encode for use in JavaScript context
     * Use this when inserting user data into JavaScript code
     */
    public static String encodeForJavaScript(String input) {
        if (input == null) {
            return "";
        }
        
        StringBuilder encoded = new StringBuilder();
        for (char c : input.toCharArray()) {
            switch (c) {
                case '\\':
                    encoded.append("\\\\");
                    break;
                case '"':
                    encoded.append("\\\"");
                    break;
                case '\'':
                    encoded.append("\\\'")
                    break;
                case '\n':
                    encoded.append("\\n");
                    break;
                case '\r':
                    encoded.append("\\r");
                    break;
                case '\t':
                    encoded.append("\\t");
                    break;
                case '\b':
                    encoded.append("\\b");
                    break;
                case '\f':
                    encoded.append("\\f");
                    break;
                case '<':
                    encoded.append("\\u003C");
                    break;
                case '>':
                    encoded.append("\\u003E");
                    break;
                default:
                    if (c < 32 || c > 126) {
                        encoded.append(String.format("\\u%04X", (int) c));
                    } else {
                        encoded.append(c);
                    }
                    break;
            }
        }
        
        return encoded.toString();
    }
    
    /**
     * SECURE: URL encode for use in URL parameters
     */
    public static String encodeForURL(String input) {
        if (input == null) {
            return "";
        }
        
        try {
            return java.net.URLEncoder.encode(input, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            logger.severe("UTF-8 encoding not supported: " + e.getMessage());
            return input.replaceAll("[^a-zA-Z0-9]", "");
        }
    }
    
    /**
     * SECURE: CSS encode for use in CSS context
     */
    public static String encodeForCSS(String input) {
        if (input == null) {
            return "";
        }
        
        StringBuilder encoded = new StringBuilder();
        for (char c : input.toCharArray()) {
            if ((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9')) {
                encoded.append(c);
            } else {
                encoded.append(String.format("\\%06X ", (int) c));
            }
        }
        
        return encoded.toString();
    }
    
    /**
     * SECURE: Sanitize user input by removing dangerous content
     * Use this as first line of defense before encoding
     */
    public static String sanitizeInput(String input) {
        if (input == null) {
            return "";
        }
        
        // Log potential XSS attempts
        if (containsXSSPatterns(input)) {
            logger.warning("Potential XSS attempt detected and sanitized: " + 
                          input.substring(0, Math.min(input.length(), 100)));
        }
        
        // Remove dangerous patterns
        String sanitized = input;
        sanitized = SCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = JAVASCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = VBSCRIPT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = ONLOAD_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = IFRAME_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = OBJECT_PATTERN.matcher(sanitized).replaceAll("");
        sanitized = EMBED_PATTERN.matcher(sanitized).replaceAll("");
        
        // Remove null bytes and control characters
        sanitized = sanitized.replaceAll("\\x00", "");
        sanitized = sanitized.replaceAll("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\x7F]", "");
        
        return sanitized;
    }
    
    /**
     * Check if input contains potential XSS patterns
     */
    public static boolean containsXSSPatterns(String input) {
        if (input == null) {
            return false;
        }
        
        String lowerInput = input.toLowerCase();
        
        // Check for common XSS patterns
        return lowerInput.contains("<script") ||
               lowerInput.contains("javascript:") ||
               lowerInput.contains("vbscript:") ||
               lowerInput.contains("onload=") ||
               lowerInput.contains("onerror=") ||
               lowerInput.contains("onclick=") ||
               lowerInput.contains("onmouseover=") ||
               lowerInput.contains("<iframe") ||
               lowerInput.contains("<object") ||
               lowerInput.contains("<embed") ||
               lowerInput.contains("eval(") ||
               lowerInput.contains("expression(");
    }
    
    /**
     * SECURE: Validate and sanitize form input
     * Use this for all form submissions
     */
    public static String validateFormInput(String input, int maxLength) {
        if (input == null) {
            return "";
        }
        
        // Sanitize first
        String sanitized = sanitizeInput(input);
        
        // Trim whitespace
        sanitized = sanitized.trim();
        
        // Enforce length limit
        if (sanitized.length() > maxLength) {
            sanitized = sanitized.substring(0, maxLength);
            logger.info("Input truncated to maximum length: " + maxLength);
        }
        
        return sanitized;
    }
    
    /**
     * SECURE: Create safe HTML input field value
     * Replacement for vulnerable input field generation in ReflectedXSS.java
     */
    public static String createSafeInputField(String name, String value, String type) {
        if (name == null) name = "";
        if (value == null) value = "";
        if (type == null) type = "text";
        
        // Encode all attributes
        String safeName = encodeForHTML(name);
        String safeValue = encodeForHTML(value);
        String safeType = encodeForHTML(type);
        
        return "<input name='" + safeName + "' type='" + safeType + "' value='" + safeValue + "'>";
    }
    
    /**
     * SECURE: Create safe HTML content
     * Use this for any dynamic HTML generation
     */
    public static String createSafeHTMLContent(String content) {
        if (content == null) {
            return "";
        }
        
        // First sanitize, then encode
        String sanitized = sanitizeInput(content);
        return encodeForHTML(sanitized);
    }
}