package pl.zydron.platform.platformcore.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Zamienia kontrolowane wyjątki aplikacji na przewidywalne odpowiedzi HTTP.
 *
 * <p>Dzięki temu kontrolery i serwisy mogą zgłaszać wyjątki opisujące problem,
 * a ta klasa wybiera właściwy kod statusu i prosty format odpowiedzi JSON.</p>
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(PlatformAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ErrorResponse handleAccessDenied(PlatformAccessDeniedException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(BadRequestException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    ErrorResponse handleBadRequest(BadRequestException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(ConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    ErrorResponse handleConflict(ConflictException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    @ExceptionHandler(InvalidJwtSubjectException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    ErrorResponse handleInvalidJwtSubject(InvalidJwtSubjectException exception) {
        return new ErrorResponse(exception.getMessage());
    }

    /**
     * Prosty kontrakt błędu zwracany klientowi API.
     *
     * @param message komunikat możliwy do pokazania lub zalogowania przez klienta
     */
    public record ErrorResponse(String message) {
    }
}
