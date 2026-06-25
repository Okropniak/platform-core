package pl.zydron.platform.platformcore.common;

/**
 * Oznacza niepoprawne dane lub żądanie, którego nie można wykonać.
 *
 * <p>Globalny handler zamienia ten wyjątek na HTTP 400.</p>
 */
public class BadRequestException extends RuntimeException {

    public BadRequestException(String message) {
        super(message);
    }
}
