package pl.zydron.platform.platformcore.common;

/**
 * Oznacza poprawnie uwierzytelnionego użytkownika bez wymaganych uprawnień.
 * Globalny handler zamienia wyjątek na HTTP 403.
 */
public class PlatformAccessDeniedException extends RuntimeException {

    public PlatformAccessDeniedException(String message) {
        super(message);
    }
}
