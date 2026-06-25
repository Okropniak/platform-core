package pl.zydron.platform.platformcore.common;

/**
 * Oznacza token, którego pole {@code sub} nie zawiera oczekiwanego UUID.
 * Globalny handler zwraca dla niego HTTP 401.
 */
public class InvalidJwtSubjectException extends RuntimeException {

    public InvalidJwtSubjectException(String message, Throwable cause) {
        super(message, cause);
    }
}
