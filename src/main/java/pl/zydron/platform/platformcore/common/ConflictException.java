package pl.zydron.platform.platformcore.common;

/**
 * Oznacza konflikt żądania z istniejącym stanem danych, na przykład próbę
 * ponownego dodania tego samego członka. Jest zwracany jako HTTP 409.
 */
public class ConflictException extends RuntimeException {

    public ConflictException(String message) {
        super(message);
    }
}
