package com.villagebooker.common.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.Map;

// Every error leaves the API as ProblemDetail (RFC 9457) with an extra "code" (ErrorCode) that the
// frontend translates; "detail" is English, for developers. Business exceptions may add "params".
// Extends ResponseEntityExceptionHandler so standard Spring MVC errors (malformed JSON, 404 route, 405...) keep their status.
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    public static final String CODE = "code";
    public static final String PARAMS = "params";
    public static final String ERRORS = "errors";

    // NotFound / Conflict / BadRequest / Forbidden: the exception knows its status and code
    @ExceptionHandler(ApiException.class)
    public ProblemDetail handleApi(ApiException ex, HttpServletRequest request) {
        ProblemDetail problem = problem(ex.status(), ex.getCode(), ex.getMessage(), request);
        if (!ex.getParams().isEmpty()) {
            problem.setProperty(PARAMS, ex.getParams());
        }
        return problem;
    }

    // @Version: someone changed the same row in the meantime
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(OptimisticLockingFailureException ex, HttpServletRequest request) {
        return problem(HttpStatus.CONFLICT, ErrorCode.CONCURRENT_MODIFICATION,
                "The resource was modified by someone else. Reload and try again.", request);
    }

    // unique / CHECK / FK violated in the database; database details stay in the log
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ProblemDetail handleDataIntegrity(DataIntegrityViolationException ex, HttpServletRequest request) {
        log.warn("Data integrity violation on {}: {}", request.getRequestURI(), ex.getMostSpecificCause().getMessage());
        return problem(HttpStatus.CONFLICT, ErrorCode.DATA_CONFLICT, "The request conflicts with existing data.", request);
    }

    // wrong email or password on login; anything else as a generic 401
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        if (ex instanceof BadCredentialsException) {
            return problem(HttpStatus.UNAUTHORIZED, ErrorCode.INVALID_CREDENTIALS, "Invalid email or password.", request);
        }
        return problem(HttpStatus.UNAUTHORIZED, ErrorCode.AUTHENTICATION_REQUIRED, "Authentication failed.", request);
    }

    // @PreAuthorize denied the call
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return problem(HttpStatus.FORBIDDEN, ErrorCode.ACCESS_DENIED, "Access denied.", request);
    }

    // anything unexpected: generic message to the client, stack trace only in the log
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled exception on {}", request.getRequestURI(), ex);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, ErrorCode.INTERNAL_ERROR, "Unexpected error.", request);
    }

    // Bean Validation on a @Valid request DTO: 400 with
    // "errors": {"password": {"code": "Size", "params": {"min": 8, "max": 72}, "message": "..."}}
    // code = the annotation's simple name; params = its numeric attributes (for "najmanje 8 znakova")
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, Object> errors = new LinkedHashMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), fieldError(error));
        }
        for (ObjectError error : ex.getBindingResult().getGlobalErrors()) {
            errors.putIfAbsent(error.getObjectName(), fieldError(error));
        }

        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed.");
        problem.setProperty(CODE, ErrorCode.VALIDATION_FAILED);
        problem.setProperty(ERRORS, errors);
        if (request instanceof ServletWebRequest servletRequest) {
            problem.setInstance(URI.create(servletRequest.getRequest().getRequestURI()));
        }
        return ResponseEntity.badRequest().body(problem);
    }

    // every other standard Spring MVC error (malformed JSON, unknown route, wrong method...) passes through here
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
                                                             HttpStatusCode statusCode, WebRequest request) {
        if (body instanceof ProblemDetail problem
                && (problem.getProperties() == null || !problem.getProperties().containsKey(CODE))) {
            problem.setProperty(CODE, codeForStatus(statusCode));
        }
        return super.handleExceptionInternal(ex, body, headers, statusCode, request);
    }

    private static ErrorCode codeForStatus(HttpStatusCode status) {
        return switch (status.value()) {
            case 400 -> ErrorCode.MALFORMED_REQUEST;
            case 404 -> ErrorCode.ROUTE_NOT_FOUND;
            case 405 -> ErrorCode.METHOD_NOT_ALLOWED;
            case 406, 415 -> ErrorCode.UNSUPPORTED_MEDIA_TYPE;
            default -> status.is5xxServerError() ? ErrorCode.INTERNAL_ERROR : ErrorCode.REQUEST_ERROR;
        };
    }

    private static Map<String, Object> fieldError(ObjectError error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put(CODE, error.getCode());

        Map<String, Object> params = new LinkedHashMap<>();
        if (error.contains(ConstraintViolation.class)) {
            error.unwrap(ConstraintViolation.class).getConstraintDescriptor().getAttributes()
                    .forEach((name, value) -> {
                        if (value instanceof Number) {
                            params.put((String) name, value);
                        }
                    });
        }
        if (!params.isEmpty()) {
            result.put(PARAMS, params);
        }
        result.put("message", error.getDefaultMessage());
        return result;
    }

    private static ProblemDetail problem(HttpStatus status, ErrorCode code, String detail, HttpServletRequest request) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setProperty(CODE, code);
        problem.setInstance(URI.create(request.getRequestURI()));
        return problem;
    }
}
