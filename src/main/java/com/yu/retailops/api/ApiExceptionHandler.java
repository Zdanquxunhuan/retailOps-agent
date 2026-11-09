package com.yu.retailops.api;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Error> forbidden() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new Error("ACCESS_DENIED", "无权访问该订单或任务", null));
    }

    @ExceptionHandler({MethodArgumentNotValidException.class, HttpMessageNotReadableException.class,
            MethodArgumentTypeMismatchException.class})
    ResponseEntity<Error> badRequest() {
        return ResponseEntity.badRequest().body(new Error("INVALID_REQUEST", "请求格式错误；message须为1至2000字，taskId须为UUID", null));
    }

    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Error> status(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new Error("REQUEST_REJECTED", e.getReason(), null));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Error> internal(Exception e) {
        String reference = UUID.randomUUID().toString();
        log.error("Diagnosis failure reference={} type={}", reference, e.getClass().getSimpleName());
        return ResponseEntity.internalServerError().body(new Error("INTERNAL_ERROR", "诊断未能完成，请通过事件编号排查", reference));
    }

    public record Error(String code, String message, String reference) { }
}
