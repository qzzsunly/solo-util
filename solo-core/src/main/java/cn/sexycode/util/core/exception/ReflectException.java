package cn.sexycode.util.core.exception;

/**
 * Indicates failure of an assertion: a possible bug in Hibernate.
 */
public class ReflectException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    /**
     * Creates an instance of AssertionFailure using the given message.
     *
     * @param message The message explaining the reason for the exception
     */
    public ReflectException(String message) {
        super(message);
    }

    /**
     * Creates an instance of AssertionFailure using the given message and underlying cause.
     *
     * @param message The message explaining the reason for the exception
     * @param cause   The underlying cause.
     */
    public ReflectException(String message, Throwable cause) {
        super(message, cause);
    }
}
