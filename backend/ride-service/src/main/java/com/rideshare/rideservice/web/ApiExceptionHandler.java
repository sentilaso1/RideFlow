package com.rideshare.rideservice.web;

import com.rideshare.rideservice.domain.ActiveRideExistsException;
import com.rideshare.rideservice.domain.InvalidRideStateException;
import com.rideshare.rideservice.domain.RideNotFoundException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Maps domain errors to RFC 9457 Problem Details with the right HTTP status. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler(RideNotFoundException.class)
    public ProblemDetail handleNotFound(RideNotFoundException ex) {
        return problem(HttpStatus.NOT_FOUND, "Ride not found", ex.getMessage());
    }

    @ExceptionHandler(InvalidRideStateException.class)
    public ProblemDetail handleInvalidState(InvalidRideStateException ex) {
        return problem(HttpStatus.CONFLICT, "Invalid ride state", ex.getMessage());
    }

    @ExceptionHandler(ActiveRideExistsException.class)
    public ProblemDetail handleActiveRide(ActiveRideExistsException ex) {
        return problem(HttpStatus.CONFLICT, "Ride already in progress", ex.getMessage());
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleConcurrentUpdate(ObjectOptimisticLockingFailureException ex) {
        return problem(HttpStatus.CONFLICT, "Concurrent update",
                "The ride was updated at the same time by someone else. Refresh and try again.");
    }

    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        Map<String, List<String>> errors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> errors
                .computeIfAbsent(error.getField(), key -> new ArrayList<>())
                .add(error.getDefaultMessage()));

        ProblemDetail body = problem(HttpStatus.BAD_REQUEST, "Invalid request", "Request validation failed");
        body.setProperty("errors", errors);
        return ResponseEntity.badRequest().body(body);
    }

    private static ProblemDetail problem(HttpStatus status, String title, String detail) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(status, detail);
        problem.setTitle(title);
        return problem;
    }
}
