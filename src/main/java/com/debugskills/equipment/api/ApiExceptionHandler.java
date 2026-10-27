package com.debugskills.equipment.api;

import com.debugskills.equipment.app.EquipmentRequestException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.stream.Collectors;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> onValidationFailed(MethodArgumentNotValidException ex) {
        String fields = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField())
                .distinct()
                .collect(Collectors.joining(", "));
        return ResponseEntity.badRequest()
                .body(new ApiError("validation_failed", "Обязательные поля некорректны: " + fields));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> onMalformedRequest(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest()
                .body(new ApiError("malformed_request", "Некорректный формат запроса"));
    }

    @ExceptionHandler(EquipmentRequestException.class)
    public ResponseEntity<ApiError> onEquipmentRequest(EquipmentRequestException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(new ApiError(ex.getCode(), ex.getMessage()));
    }
}