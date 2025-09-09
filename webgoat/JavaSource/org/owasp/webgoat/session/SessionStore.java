package org.owasp.webgoat.session;

import java.io.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.logging.Logger;

/**
 * Interface for external session storage to enable stateless application deployment.
 * This abstraction allows for different storage implementations (Redis, Database, etc.)
 */
public interface SessionStore {
    
    /**
     * Store a WebSession object with the given session ID
     * @param sessionId The unique session identifier
     * @param webSession The WebSession object to store
     * @throws SessionStoreException if storage operation fails
     */
    void storeSession(String sessionId, WebSession webSession) throws SessionStoreException;
    
    /**
     * Retrieve a WebSession object by session ID
     * @param sessionId The unique session identifier
     * @return The WebSession object, or null if not found
     * @throws SessionStoreException if retrieval operation fails
     */
    WebSession retrieveSession(String sessionId) throws SessionStoreException;
    
    /**
     * Remove a session from storage
     * @param sessionId The unique session identifier
     * @throws SessionStoreException if removal operation fails
     */
    void removeSession(String sessionId) throws SessionStoreException;
    
    /**
     * Check if a session exists in storage
     * @param sessionId The unique session identifier
     * @return true if session exists, false otherwise
     */
    boolean sessionExists(String sessionId);
    
    /**
     * Set session timeout in seconds
     * @param sessionId The unique session identifier
     * @param timeoutSeconds Timeout in seconds
     */
    void setSessionTimeout(String sessionId, int timeoutSeconds);
}

/**
 * Exception thrown when session store operations fail
 */
class SessionStoreException extends Exception {
    private static final long serialVersionUID = 1L;
    
    public SessionStoreException(String message) {
        super(message);
    }
    
    public SessionStoreException(String message, Throwable cause) {
        super(message, cause);
    }
}

/**
 * In-memory implementation of SessionStore for development/testing.
 * In production, this should be replaced with Redis or database implementation.
 */
class InMemorySessionStore implements SessionStore {
    private static final Logger logger = Logger.getLogger(InMemorySessionStore.class.getName());
    private final ConcurrentMap<String, SerializedSession> sessions = new ConcurrentHashMap<>();
    
    private static class SerializedSession {
        final byte[] data;
        final long timestamp;
        int timeoutSeconds;
        
        SerializedSession(byte[] data, int timeoutSeconds) {
            this.data = data;
            this.timestamp = System.currentTimeMillis();
            this.timeoutSeconds = timeoutSeconds;
        }
        
        boolean isExpired() {
            if (timeoutSeconds <= 0) return false;
            return (System.currentTimeMillis() - timestamp) > (timeoutSeconds * 1000L);
        }
    }
    
    @Override
    public void storeSession(String sessionId, WebSession webSession) throws SessionStoreException {
        try {
            byte[] serializedData = serializeSession(webSession);
            sessions.put(sessionId, new SerializedSession(serializedData, 60 * 60 * 24 * 2)); // 2 days default
            logger.info("Stored session: " + sessionId);
        } catch (Exception e) {
            throw new SessionStoreException("Failed to store session: " + sessionId, e);
        }
    }
    
    @Override
    public WebSession retrieveSession(String sessionId) throws SessionStoreException {
        SerializedSession serializedSession = sessions.get(sessionId);
        if (serializedSession == null) {
            return null;
        }
        
        if (serializedSession.isExpired()) {
            sessions.remove(sessionId);
            logger.info("Removed expired session: " + sessionId);
            return null;
        }
        
        try {
            WebSession webSession = deserializeSession(serializedSession.data);
            logger.info("Retrieved session: " + sessionId);
            return webSession;
        } catch (Exception e) {
            throw new SessionStoreException("Failed to retrieve session: " + sessionId, e);
        }
    }
    
    @Override
    public void removeSession(String sessionId) throws SessionStoreException {
        sessions.remove(sessionId);
        logger.info("Removed session: " + sessionId);
    }
    
    @Override
    public boolean sessionExists(String sessionId) {
        SerializedSession session = sessions.get(sessionId);
        if (session == null) return false;
        if (session.isExpired()) {
            sessions.remove(sessionId);
            return false;
        }
        return true;
    }
    
    @Override
    public void setSessionTimeout(String sessionId, int timeoutSeconds) {
        SerializedSession session = sessions.get(sessionId);
        if (session != null) {
            session.timeoutSeconds = timeoutSeconds;
        }
    }
    
    private byte[] serializeSession(WebSession webSession) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        ObjectOutputStream oos = new ObjectOutputStream(baos);
        oos.writeObject(webSession);
        oos.close();
        return baos.toByteArray();
    }
    
    private WebSession deserializeSession(byte[] data) throws IOException, ClassNotFoundException {
        ByteArrayInputStream bais = new ByteArrayInputStream(data);
        ObjectInputStream ois = new ObjectInputStream(bais);
        WebSession webSession = (WebSession) ois.readObject();
        ois.close();
        return webSession;
    }
}