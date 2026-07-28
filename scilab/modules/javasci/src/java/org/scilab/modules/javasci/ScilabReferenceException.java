package org.scilab.modules.javasci;

/**
 * Thrown when a by-reference view can no longer resolve its variable — it was
 * cleared, or its type changed underneath the view.
 *
 * Unchecked on purpose: the ScilabType accessors this is raised from declare no
 * checked exceptions, so JavasciException cannot escape an override.
 */
public class ScilabReferenceException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ScilabReferenceException(String message) {
        super(message);
    }

    public ScilabReferenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
