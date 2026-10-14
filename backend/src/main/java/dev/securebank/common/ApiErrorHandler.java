package dev.securebank.common;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiErrorHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiErrorHandler.class);

    @ExceptionHandler(ApiException.class)
    ResponseEntity<?> business(ApiException error, HttpServletRequest request) {
        return ResponseEntity.status(error.status()).body(body(error.status().value(), error.code(), error.getMessage(), request));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException error, HttpServletRequest request) {
        var response = body(400, "VALIDATION_ERROR", "Please check the submitted values.", request);
        var fields = new LinkedHashMap<String, String>();
        error.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        response.put("fieldErrors", fields);
        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    ResponseEntity<?> forbidden(Exception error, HttpServletRequest request) {
        return ResponseEntity.status(403).body(body(403, "FORBIDDEN", "You do not have permission for this action.", request));
    }

    @ExceptionHandler({org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.web.bind.ServletRequestBindingException.class,
            org.springframework.web.method.annotation.HandlerMethodValidationException.class,
            jakarta.validation.ConstraintViolationException.class})
    ResponseEntity<?> malformed(Exception error, HttpServletRequest request) {
        return ResponseEntity.badRequest().body(body(400, "INVALID_REQUEST", "The request is not valid.", request));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception error, HttpServletRequest request) {
        log.error("Request failed: {}", error.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(body(500, "INTERNAL_ERROR", "We could not complete your request.", request));
    }

    private Map<String, Object> body(int status, String code, String message, HttpServletRequest request) {
        var body = new LinkedHashMap<String, Object>();
        body.put("status", status); body.put("code", code); body.put("message", message);
        body.put("correlationId", String.valueOf(request.getAttribute("correlationId")));
        return body;
    }
}
