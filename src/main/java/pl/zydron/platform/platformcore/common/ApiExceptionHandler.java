package pl.zydron.platform.platformcore.common;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pl.zydron.platform.platformcore.tenants.TenantAccessDeniedException;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(TenantAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    ErrorResponse handleAccessDenied(TenantAccessDeniedException exception) {
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

    public record ErrorResponse(String message) {
    }
}
