package com.network.identity;

import org.springframework.http.*;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
class ApiErrors {
    @ExceptionHandler(ResponseStatusException.class) ProblemDetail status(ResponseStatusException e) {
        return ProblemDetail.forStatusAndDetail(e.getStatusCode(),e.getReason()==null ? "Request denied" : e.getReason());
    }
    @ExceptionHandler({MethodArgumentNotValidException.class,IllegalArgumentException.class}) ProblemDetail validation(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Check the submitted fields");
    }
    @ExceptionHandler({org.springframework.dao.DataAccessException.class,org.springframework.web.client.RestClientException.class}) ProblemDetail unavailable(Exception e) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE,"A required service is unavailable. Try again later.");
    }
}
