package pl.zydron.platform.platformcore.common;

public class InvalidJwtSubjectException extends RuntimeException {

    public InvalidJwtSubjectException(String message, Throwable cause) {
        super(message, cause);
    }
}
