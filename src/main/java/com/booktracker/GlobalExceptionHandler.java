package com.booktracker;

import jakarta.validation.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

/**
 * Global exception handler — maps application exceptions to consistent error shapes (D-03, D-04).
 *
 * <p>Error response shapes:
 * <ul>
 *   <li>409 Conflict (duplicate email): {@code {"message": "Email already registered"}} (D-04)</li>
 *   <li>401 Unauthorized (bad credentials): {@code {"message": "Invalid credentials"}} (D-03)</li>
 *   <li>400 Bad Request (validation): {@code {"message": "Validation failed", "errors": {field: reason}}} (D-03)</li>
 * </ul>
 *
 * <p>Security: no internal detail (stack traces, SQL text, field names beyond what D-03/D-04 allows)
 * is ever exposed in the response body (T-02-03 mitigated for duplicate-email case).
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * D-04: Duplicate email on register → 409 Conflict.
     *
     * <p>Spring Data JPA wraps JDBC {@code SQLIntegrityConstraintViolationException} in
     * {@code DataIntegrityViolationException} when the {@code users_email_uq} constraint fires.
     * The generic message is intentional — it does not reveal which field triggered the
     * constraint (T-02-03 mitigation).
     */
    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleDuplicate(DataIntegrityViolationException ex) {
        return Map.of("message", "Email already registered");
    }

    /**
     * D-03: Invalid credentials → 401 Unauthorized.
     *
     * <p>Generic message prevents distinguishing "wrong email" from "wrong password"
     * (email enumeration attack mitigation, AUTH-02).
     */
    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleBadCredentials(BadCredentialsException ex) {
        return Map.of("message", "Invalid credentials");
    }

    /**
     * D-03: Method-parameter constraint violation → 400 Bad Request.
     *
     * <p>Raised when {@code @Validated} + {@code @NotBlank} (or similar) is used on
     * a {@code @RequestParam} or path variable (e.g., blank {@code q} on
     * GET /books/search). Spring maps this to {@code ConstraintViolationException},
     * NOT {@code MethodArgumentNotValidException} (which only covers {@code @Valid}
     * on request body DTOs).
     */
    @ExceptionHandler(ConstraintViolationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> handleConstraintViolation(ConstraintViolationException ex) {
        return Map.of("message", "Validation failed");
    }

    /**
     * D-03: Bean Validation failure → 400 Bad Request with field-level detail.
     *
     * <p>Field errors from {@code @Valid} on request DTOs are collected into a
     * map of {@code {fieldName: validationMessage}} and included under the
     * {@code "errors"} key. This provides actionable detail for the client
     * without exposing internal implementation detail.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = ex.getBindingResult().getFieldErrors().stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        FieldError::getDefaultMessage,
                        (first, second) -> first  // keep first message if multiple errors on same field
                ));
        return Map.of("message", "Validation failed", "errors", errors);
    }
}
