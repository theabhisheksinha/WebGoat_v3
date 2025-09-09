package org.owasp.webgoat.util;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Utility class to load WebGoat configuration properties
 * This fixes the hardcoded URL cloud blocker by externalizing URLs
 */
public class WebGoatProperties {
    
    private static Properties properties = new Properties();
    private static boolean loaded = false;
    
    static {
        loadProperties();
    }
    
    private static void loadProperties() {
        if (!loaded) {
            try {
                InputStream input = WebGoatProperties.class.getClassLoader()
                    .getResourceAsStream("webgoat-urls.properties");
                if (input != null) {
                    properties.load(input);
                    loaded = true;
                    input.close();
                }
            } catch (IOException e) {
                System.err.println("Failed to load webgoat-urls.properties: " + e.getMessage());
            }
        }
    }
    
    /**
     * Get a property value by key
     * @param key the property key
     * @return the property value or null if not found
     */
    public static String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    /**
     * Get a property value by key with default value
     * @param key the property key
     * @param defaultValue the default value if key not found
     * @return the property value or default value
     */
    public static String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    // Convenience methods for commonly used URLs
    public static String getAspectSecurityUrl() {
        return getProperty("aspect.security.url", "https://www.aspectsecurity.com");
    }
    
    public static String getAspectWebGoatUrl() {
        return getProperty("aspect.webgoat.url", "https://www.aspectsecurity.com/webgoat.html");
    }
    
    public static String getWebGoatCaptureUrl() {
        return getProperty("webgoat.capture.url", "https://localhost:8443/WebGoat/capture/PROPERTY=yes&ADD_CREDENTIALS_HERE");
    }
    
    public static String getGoogleSearchUrl() {
        return getProperty("google.search.url", "https://www.google.com/search?q=aspect+security");
    }
    
    public static String getWebGoatAjaxSameOriginUrl() {
        return getProperty("webgoat.ajax.sameorigin.url", "lessons/Ajax/sameOrigin.jsp");
    }
}