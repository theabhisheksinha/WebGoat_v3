package org.owasp.webgoat.session;

import javax.servlet.ServletContext;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * SessionManager handles externalized session storage for cloud deployment.
 * This class replaces direct HttpSession storage with external session store.
 */
public class SessionManager {
    private static final Logger logger = Logger.getLogger(SessionManager.class.getName());
    private static final String SESSION_ID_ATTRIBUTE = "webgoat.session.id";
    private static final String EXTERNAL_SESSION_ENABLED = "webgoat.external.session.enabled";
    
    private final SessionStore sessionStore;
    private final boolean externalSessionEnabled;
    
    /**
     * Constructor for SessionManager
     * @param context ServletContext to read configuration
     */
    public SessionManager(ServletContext context) {
        // Check if external session storage is enabled via system property or context param
        String enabledParam = System.getProperty(EXTERNAL_SESSION_ENABLED, 
            context.getInitParameter(EXTERNAL_SESSION_ENABLED));
        this.externalSessionEnabled = "true".equalsIgnoreCase(enabledParam);
        
        if (externalSessionEnabled) {
            // In production, this could be RedisSessionStore or DatabaseSessionStore
            this.sessionStore = createSessionStore(context);
            logger.info("External session storage enabled with: " + sessionStore.getClass().getSimpleName());
        } else {
            this.sessionStore = null;
            logger.info("Using default HttpSession storage (not recommended for cloud deployment)");
        }
    }
    
    /**
     * Factory method to create appropriate SessionStore implementation
     * @param context ServletContext for configuration
     * @return SessionStore implementation
     */
    private SessionStore createSessionStore(ServletContext context) {
        String storeType = System.getProperty("webgoat.session.store.type", "memory");
        
        switch (storeType.toLowerCase()) {
            case "redis":
                // TODO: Implement RedisSessionStore when Redis dependency is available
                logger.warning("Redis session store not implemented, falling back to in-memory store");
                return new InMemorySessionStore();
            case "database":
                // TODO: Implement DatabaseSessionStore
                logger.warning("Database session store not implemented, falling back to in-memory store");
                return new InMemorySessionStore();
            case "memory":
            default:
                return new InMemorySessionStore();
        }
    }
    
    /**
     * Get or create WebSession for the request
     * @param request HttpServletRequest
     * @param response HttpServletResponse
     * @param context ServletContext
     * @param webgoatContext WebgoatContext
     * @return WebSession instance
     */
    public WebSession getWebSession(HttpServletRequest request, HttpServletResponse response, 
                                   ServletContext context, WebgoatContext webgoatContext) {
        
        if (!externalSessionEnabled) {
            // Fall back to traditional HttpSession storage
            return getWebSessionFromHttpSession(request, context, webgoatContext);
        }
        
        try {
            HttpSession httpSession = request.getSession(true);
            String sessionId = httpSession.getId();
            
            // Try to retrieve existing WebSession from external store
            WebSession webSession = sessionStore.retrieveSession(sessionId);
            
            if (webSession == null) {
                // Create new WebSession
                webSession = new WebSession(webgoatContext, context);
                sessionStore.storeSession(sessionId, webSession);
                sessionStore.setSessionTimeout(sessionId, 60 * 60 * 24 * 2); // 2 days
                logger.info("Created new external WebSession: " + sessionId);
            } else {
                logger.fine("Retrieved existing external WebSession: " + sessionId);
            }
            
            // Update the session with current request/response
            webSession.update(request, response, "HammerHead");
            
            // Store updated session back to external store
            sessionStore.storeSession(sessionId, webSession);
            
            return webSession;
            
        } catch (SessionStoreException e) {
            logger.log(Level.SEVERE, "Failed to manage external session, falling back to HttpSession", e);
            return getWebSessionFromHttpSession(request, context, webgoatContext);
        }
    }
    
    /**
     * Traditional HttpSession-based WebSession management (fallback)
     */
    private WebSession getWebSessionFromHttpSession(HttpServletRequest request, 
                                                   ServletContext context, 
                                                   WebgoatContext webgoatContext) {
        HttpSession httpSession = request.getSession(true);
        WebSession webSession = (WebSession) httpSession.getAttribute(WebSession.SESSION);
        
        if (webSession == null) {
            webSession = new WebSession(webgoatContext, context);
            httpSession.setAttribute(WebSession.SESSION, webSession);
            httpSession.setMaxInactiveInterval(60 * 60 * 24 * 2); // 2 days
        }
        
        return webSession;
    }
    
    /**
     * Invalidate session in both HttpSession and external store
     * @param request HttpServletRequest
     */
    public void invalidateSession(HttpServletRequest request) {
        HttpSession httpSession = request.getSession(false);
        if (httpSession != null) {
            String sessionId = httpSession.getId();
            
            if (externalSessionEnabled && sessionStore != null) {
                try {
                    sessionStore.removeSession(sessionId);
                    logger.info("Removed external session: " + sessionId);
                } catch (SessionStoreException e) {
                    logger.log(Level.WARNING, "Failed to remove external session: " + sessionId, e);
                }
            }
            
            httpSession.invalidate();
        }
    }
    
    /**
     * Check if external session storage is enabled
     * @return true if external storage is enabled
     */
    public boolean isExternalSessionEnabled() {
        return externalSessionEnabled;
    }
    
    /**
     * Get session store instance (for testing/monitoring)
     * @return SessionStore instance or null if not using external storage
     */
    public SessionStore getSessionStore() {
        return sessionStore;
    }
}