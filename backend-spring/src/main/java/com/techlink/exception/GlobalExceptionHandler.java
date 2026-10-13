package com.techlink.exception;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

// Convierte las excepciones en respuestas JSON con el codigo HTTP adecuado.
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DominioInvalidoException.class)
    // 400: dominio de correo no valido.
    public ResponseEntity<Map<String, Object>> dominioInvalido(DominioInvalidoException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(CorreoDuplicadoException.class)
    // 409: correo repetido.
    public ResponseEntity<Map<String, Object>> correoDuplicado(CorreoDuplicadoException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    // 409: respaldo si MySQL rechaza un duplicado (correo o codigo de estudiante).
    public ResponseEntity<Map<String, Object>> integridad(DataIntegrityViolationException ex) {
        return error(HttpStatus.CONFLICT, "El correo o el código de estudiante ya está registrado");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    // 400: fallan las validaciones del DTO; se listan los campos con error.
    public ResponseEntity<Map<String, Object>> validacion(MethodArgumentNotValidException ex) {
        Map<String, String> campos = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors()
                .forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", 400);
        body.put("error", "Datos inválidos");
        body.put("campos", campos);
        return ResponseEntity.badRequest().body(body);
    }

    private ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status.value());
        body.put("error", message);
        return ResponseEntity.status(status).body(body);
    }
}
