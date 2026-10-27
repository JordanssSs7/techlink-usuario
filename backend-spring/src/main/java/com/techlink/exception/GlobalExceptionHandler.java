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

    // HU-01 - 400: dominio de correo no valido.
    @ExceptionHandler(DominioInvalidoException.class)
    public ResponseEntity<Map<String, Object>> dominioInvalido(DominioInvalidoException ex) {
        return error(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    // HU-01 - 409: correo repetido.
    @ExceptionHandler(CorreoDuplicadoException.class)
    public ResponseEntity<Map<String, Object>> correoDuplicado(CorreoDuplicadoException ex) {
        return error(HttpStatus.CONFLICT, ex.getMessage());
    }

    // HU-01 - 409: respaldo si MySQL rechaza un duplicado (correo o codigo de estudiante).
    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> integridad(DataIntegrityViolationException ex) {
        return error(HttpStatus.CONFLICT, "El correo o el código de estudiante ya está registrado");
    }

    // HU-02 - 401: correo o contraseña incorrectos en el login.
    @ExceptionHandler(CredencialesInvalidasException.class)
    public ResponseEntity<Map<String, Object>> credencialesInvalidas(CredencialesInvalidasException ex) {
        return error(HttpStatus.UNAUTHORIZED, ex.getMessage());
    }

    // HU-02 - 423: cuenta bloqueada por intentos fallidos; se informa cuantos minutos faltan.
    @ExceptionHandler(CuentaBloqueadaException.class)
    public ResponseEntity<Map<String, Object>> cuentaBloqueada(CuentaBloqueadaException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", HttpStatus.LOCKED.value());
        body.put("error", ex.getMessage());
        body.put("minutosRestantes", ex.getMinutosRestantes());
        return ResponseEntity.status(HttpStatus.LOCKED).body(body);
    }

    // HU-02 - 403: la cuenta esta inactiva.
    @ExceptionHandler(CuentaInactivaException.class)
    public ResponseEntity<Map<String, Object>> cuentaInactiva(CuentaInactivaException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // HU-02 (Google) - 404: entra con Google pero aun no tiene cuenta (debe registrarse primero).
    @ExceptionHandler(UsuarioNoRegistradoException.class)
    public ResponseEntity<Map<String, Object>> usuarioNoRegistrado(UsuarioNoRegistradoException ex) {
        return error(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    // HU-02 (Google) - 403: la cuenta de Google no es del dominio institucional.
    @ExceptionHandler(CorreoNoInstitucionalException.class)
    public ResponseEntity<Map<String, Object>> correoNoInstitucional(CorreoNoInstitucionalException ex) {
        return error(HttpStatus.FORBIDDEN, ex.getMessage());
    }

    // HU-02 (Google) - 503: falta configurar GOOGLE_CLIENT_ID en el servidor.
    @ExceptionHandler(GoogleNoConfiguradoException.class)
    public ResponseEntity<Map<String, Object>> googleNoConfigurado(GoogleNoConfiguradoException ex) {
        return error(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
    }

    // 400: fallan las validaciones del DTO; se listan los campos con error.
    @ExceptionHandler(MethodArgumentNotValidException.class)
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
