/**
 * WebGoat Configuration JavaScript
 * This file provides externalized URLs to fix hardcoded URL cloud blockers
 * Include this file before other JavaScript files that need URL configuration
 */

// Initialize webgoat configuration object
window.webgoatConfig = {
    // External URLs
    owaspUrl: 'https://www.owasp.org',
    aspectSecurityUrl: 'https://www.aspectsecurity.com',
    aspectWebgoatUrl: 'https://www.aspectsecurity.com/webgoat.html',
    googleSearchUrl: 'https://www.google.com/search?q=aspect+security',
    partnetUrl: 'https://www.partnet.com',
    mandiantUrl: 'https://www.mandiant.com',
    codeGoogleWebgoatUrl: 'https://code.google.com/p/webgoat',
    yehgTrainingUrl: 'https://yehg.net/lab/pr0js/training/webgoat.php',
    w3schoolsSqlUpdateUrl: 'https://www.w3schools.com/SQl/sql_update.asp',
    w3schoolsSqlInsertUrl: 'https://www.w3schools.com/SQl/sql_insert.asp',
    webscarabUrl: 'https://www.owasp.org/index.php/Category:OWASP_WebScarab_Project',
    webDeveloperUrl: 'https://chrispederick.com/work/web-developer/',
    hackbarUrl: 'https://devels-playground.blogspot.com/',
    hsqldbGuideUrl: 'https://hsqldb.org/doc/guide/ch09.html',
    ouncelabsUrl: 'https://www.ouncelabs.com',
    zionsecurityUrl: 'https://www.zionsecurity.com',
    
    // Local URLs (configurable for different environments)
    webgoatBaseUrl: 'https://localhost:8443/WebGoat',
    webgoatAttackUrl: 'https://localhost:8443/WebGoat/attack',
    webgoatConfUrl: 'https://localhost:8443/WebGoat/conf',
    webgoatCaptureUrl: 'https://localhost:8443/WebGoat/capture/PROPERTY=yes&ADD_CREDENTIALS_HERE',
    ajaxSameOriginUrl: 'lessons/Ajax/sameOrigin.jsp',
    
    // XML Namespaces and DTDs (using HTTPS)
    w3cXhtmlDtdUrl: 'https://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd',
    w3cHtmlDtdUrl: 'https://www.w3.org/TR/html4/loose.dtd',
    javaSunDtdUrl: 'https://java.sun.com/dtd/web-app_2_3.dtd',
    mavenNamespaceUrl: 'https://maven.apache.org/POM/4.0.0',
    mavenSchemaUrl: 'https://maven.apache.org/maven-v4_0_0.xsd',
    axisWsddNamespaceUrl: 'https://xml.apache.org/axis/wsdd/',
    axisJavaNamespaceUrl: 'https://xml.apache.org/axis/wsdd/providers/java',
    w3cSchemaInstanceUrl: 'https://www.w3.org/2001/XMLSchema-instance',
    javaDownloadMavenUrl: 'https://download.java.net/maven/2'
};

// Helper function to get configuration values with fallback
window.getWebGoatConfig = function(key, fallback) {
    return window.webgoatConfig[key] || fallback;
};

// Console log for debugging
if (console && console.log) {
    console.log('WebGoat configuration loaded successfully');
}