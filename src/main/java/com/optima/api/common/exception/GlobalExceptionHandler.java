package com.optima.api.common.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.stream.Collectors;

/**
 * GlobalExceptionHandler - Traduce excepciones a respuestas HTTP JSON.
 *
 * Sin esta clase, cualquier excepcion no controlada resulta en una
 * pagina de error de Spring (HTML feo) con stacktrace expuesto. Aqui
 * capturamos las excepciones tipicas y las convertimos en cuerpos JSON
 * consistentes (ErrorResponse), seguros y utiles para el cliente.
 *
 * COMUNICACION:
 * - Lo activa Spring por @RestControllerAdvice: intercepta excepciones
 *   lanzadas por CUALQUIER @RestController o capa que invoquen.
 * - Devuelve ErrorResponse serializado a JSON.
 *
 * Mapeo de excepciones a status:
 *   MethodArgumentNotValidException  -> 400 (validacion @Valid falla)
 *   IllegalArgumentException         -> 400
 *   ResponseStatusException          -> el status que lleva (401/404/409...)
 *   EntityNotFoundException          -> 404
 *   AccessDeniedException            -> 403 (de @PreAuthorize)
 *   Exception (catch-all)            -> 500 + log con stacktrace
 *
 * El catch-all NO devuelve detalles del error al cliente (mensaje
 * generico) para no filtrar info sensible. El stacktrace queda en logs
 * del servidor.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * Captura fallos de validacion de @Valid en @RequestBody (DTOs Request).
     * Concatena todos los field errors en un mensaje legible.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleValidation(MethodArgumentNotValidException ex) {
        String errors = ex.getBindingResult().getFieldErrors().stream()
            .map(e -> e.getField() + ": " + e.getDefaultMessage())
            .collect(Collectors.joining(", "));
        return new ErrorResponse(400, "Bad Request", errors,
            Instant.now().toString());
    }

    /**
     * Captura IllegalArgumentException - lanzadas a mano cuando un
     * argumento no respeta una precondicion (no del @Valid sino logica).
     */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse handleIllegalArgument(IllegalArgumentException ex) {
        return new ErrorResponse(400, "Bad Request", ex.getMessage(),
            Instant.now().toString());
    }

    /**
     * Captura ResponseStatusException - el mecanismo principal que usan
     * los services para devolver errores HTTP semanticos (404, 409, 401).
     * Preserva el status original y el mensaje (reason) que puso el service.
     */
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        ErrorResponse body = new ErrorResponse(
            ex.getStatusCode().value(),
            ex.getStatusCode().toString(),
            ex.getReason(),
            Instant.now().toString());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    /**
     * Captura EntityNotFoundException de JPA - se lanza cuando se accede
     * a una relacion lazy de un id que ya no existe en BD. En nuestro
     * codigo es raro porque preferimos findById().orElseThrow(...), pero
     * se mantiene por seguridad.
     */
    @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse handleEntityNotFound(jakarta.persistence.EntityNotFoundException ex) {
        return new ErrorResponse(404, "Not Found", ex.getMessage(),
            Instant.now().toString());
    }

    /**
     * Captura la excepcion que lanza Spring Security al rechazar un acceso
     * por @PreAuthorize. La AOP la lanza a nivel de metodo y bypassa el
     * AccessDeniedHandler del SecurityConfig (que solo cubre rechazos en
     * la cadena de filtros), asi que la traducimos aqui al mismo 403.
     */
    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ErrorResponse handleAccessDenied(AccessDeniedException ex) {
        return new ErrorResponse(403, "403 FORBIDDEN",
            "No tienes permisos suficientes para esta operacion",
            Instant.now().toString());
    }

    /**
     * Catch-all para cualquier excepcion no contemplada arriba.
     * Loguea el stacktrace en el servidor pero NO lo devuelve al cliente:
     * el cuerpo solo lleva un mensaje generico para no filtrar info sensible.
     */
    @ExceptionHandler(Exception.class)
    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
    public ErrorResponse handleGeneric(Exception ex) {
        log.error("Error no controlado", ex);
        return new ErrorResponse(500, "Internal Server Error",
            "Error interno del servidor", Instant.now().toString());
    }
}
