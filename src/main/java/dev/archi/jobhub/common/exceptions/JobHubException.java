package dev.archi.jobhub.common.exceptions;

/** Root of every error we throw on purpose. Messages are written by us, so they're safe to return to callers. */
public abstract class JobHubException extends RuntimeException {
    protected JobHubException(String message){
        super(message);
    }
    protected JobHubException(String message, Throwable cause) {
        super(message, cause);
    }
}