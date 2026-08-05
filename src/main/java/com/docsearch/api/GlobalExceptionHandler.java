package com.docsearch.api;

import com.docsearch.application.DocumentNotFoundException;
import com.docsearch.web.CorrelationIdFilter;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.io.IOException;
import java.net.URI;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Turns exceptions into RFC 9457 {@code application/problem+json} responses.
 *
 * <p>Extends {@link ResponseEntityExceptionHandler} deliberately. A bare
 * {@code @ExceptionHandler(Exception.class)} catch-all looks like thorough error handling
 * and is a trap: it also swallows the exceptions Spring MVC already maps correctly —
 * a missing query parameter, an unsupported method, the wrong content type — and reports
 * all of them as 500. Extending the base class keeps Spring's statuses and lets this
 * class add to them.
 *
 * <p>Two properties hold for every response:
 * <ul>
 *   <li>The request's correlation id is included, so a caller can quote it and the exact
 *       request can be found in the logs. {@link #handleExceptionInternal} is the single
 *       funnel Spring routes its own handled exceptions through, so decorating there
 *       covers all of them.
 *   <li>Unexpected exceptions return a generic message. Stack traces and internal
 *       messages go to the log, never to the client — an error body is an information
 *       disclosure surface.
 * </ul>
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final String BASE_TYPE = "https://docsearch.example/problems/";

    // ---------- application exceptions ----------

    @ExceptionHandler(DocumentNotFoundException.class)
    public ProblemDetail handleNotFound(DocumentNotFoundException exception) {
        ProblemDetail problem = problem(HttpStatus.NOT_FOUND, "Document not found",
                exception.getMessage(), "document-not-found");
        problem.setProperty("documentId", exception.documentId());
        return problem;
    }

    /** A datastore call failed. Distinguished from a bug so it can be retried upstream. */
    @ExceptionHandler(IOException.class)
    public ProblemDetail handleDatastoreFailure(IOException exception) {
        log.error("Datastore call failed", exception);
        return problem(HttpStatus.SERVICE_UNAVAILABLE, "Datastore unavailable",
                "A datastore could not be reached. The request was not applied.",
                "datastore-unavailable");
    }

    /** Raised by validated method parameters on beans proxied with {@code @Validated}. */
    @ExceptionHandler(ConstraintViolationException.class)
    public ProblemDetail handleConstraintViolation(ConstraintViolationException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
            errors.put(String.valueOf(violation.getPropertyPath()), violation.getMessage());
        }

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "The request has %d invalid value(s)".formatted(errors.size()), "validation-failed");
        problem.setProperty("errors", errors);
        return problem;
    }

    /**
     * Anything unanticipated. The client gets a correlation id and nothing else; the
     * detail goes to the log, where it belongs.
     */
    @ExceptionHandler(Exception.class)
    public ProblemDetail handleUnexpected(Exception exception) {
        log.error("Unhandled exception", exception);
        return problem(HttpStatus.INTERNAL_SERVER_ERROR, "Internal server error",
                "The request could not be completed. Quote the correlationId when reporting this.",
                "internal-error");
    }

    // ---------- Spring MVC exceptions, enriched ----------

    /** Request body failed Bean Validation — report every field at once, not just the first. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(
            MethodArgumentNotValidException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        Map<String, String> errors = new LinkedHashMap<>();
        for (FieldError error : exception.getBindingResult().getFieldErrors()) {
            // merge() so two failures on one field do not silently discard the first
            errors.merge(error.getField(),
                    error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage(),
                    (a, b) -> a + "; " + b);
        }
        exception.getBindingResult().getGlobalErrors().forEach(error ->
                errors.merge(error.getObjectName(), error.getDefaultMessage(), (a, b) -> a + "; " + b));

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Validation failed",
                "The request body has %d invalid field(s)".formatted(errors.size()),
                "validation-failed");
        problem.setProperty("errors", errors);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** A query or path parameter failed its constraint — for example {@code limit}. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        List<String> messages = exception.getParameterValidationResults().stream()
                .flatMap(result -> result.getResolvableErrors().stream())
                .map(error -> error.getDefaultMessage())
                .filter(message -> message != null)
                .toList();

        ProblemDetail problem = problem(HttpStatus.BAD_REQUEST, "Invalid request parameter",
                messages.isEmpty() ? "A request parameter is invalid" : String.join("; ", messages),
                "invalid-parameter");
        problem.setProperty("errors", messages);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(problem);
    }

    /** Malformed JSON, or a value that cannot be coerced into the target type. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(
            HttpMessageNotReadableException exception, HttpHeaders headers,
            HttpStatusCode status, WebRequest request) {

        log.debug("Rejected unreadable request body", exception);
        // Deliberately does not echo the parser message: it quotes the payload back,
        // which turns an error body into a reflection surface.
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(
                problem(HttpStatus.BAD_REQUEST, "Malformed request body",
                        "The request body is not valid JSON, or a field has the wrong type",
                        "malformed-body"));
    }

    /**
     * Every exception Spring MVC handles itself passes through here, so this is where the
     * correlation id and timestamp get attached to those responses too.
     */
    @Override
    protected ResponseEntity<Object> handleExceptionInternal(
            Exception exception, Object body, HttpHeaders headers,
            HttpStatusCode statusCode, WebRequest request) {

        ResponseEntity<Object> response =
                super.handleExceptionInternal(exception, body, headers, statusCode, request);

        if (response != null && response.getBody() instanceof ProblemDetail problem) {
            decorate(problem);
        }
        return response;
    }

    // ---------- helpers ----------

    private ProblemDetail problem(HttpStatus status, String title, String detail, String type) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        problem.setType(URI.create(BASE_TYPE + type));
        decorate(problem);
        return problem;
    }

    private void decorate(ProblemDetail problem) {
        Map<String, Object> properties = problem.getProperties();
        if (properties == null || !properties.containsKey("timestamp")) {
            problem.setProperty("timestamp", Instant.now());
        }
        String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
        if (correlationId != null) {
            problem.setProperty("correlationId", correlationId);
        }
    }
}
