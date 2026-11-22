package com.techlink.controller;

import com.techlink.dto.NotificacionDtos.NotificacionResponse;
import com.techlink.dto.NotificacionDtos.NotificacionesResponse;
import com.techlink.dto.NotificacionDtos.PreferenciaResponse;
import com.techlink.dto.NotificacionDtos.PreferenciasRequest;
import com.techlink.dto.NotificacionDtos.ResumenNotificaciones;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.service.NotificacionService;
import com.techlink.service.PreferenciaNotificacionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

// HU-22 y HU-24: centro de notificaciones y preferencias. Requiere el token JWT de un ESTUDIANTE o un MENTOR
// (403 con otro rol). Cada usuario solo ve y cambia lo suyo.
@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;
    private final PreferenciaNotificacionService preferenciaService;

    public NotificacionController(NotificacionService notificacionService, PreferenciaNotificacionService preferenciaService) {
        this.notificacionService = notificacionService;
        this.preferenciaService = preferenciaService;
    }

    // Las más recientes primero. ?soloNoLeidas=true filtra las pendientes y ?limite= (1 a 100) recorta la lista.
    @GetMapping
    public ResponseEntity<NotificacionesResponse> listar(@AuthenticationPrincipal Jwt jwt,
                                                         @RequestParam(defaultValue = "false") boolean soloNoLeidas,
                                                         @RequestParam(required = false) Integer limite) {
        return ResponseEntity.ok(notificacionService.listar(usuario(jwt), soloNoLeidas, limite));
    }

    // Solo el número de pendientes: lo consulta el indicador de la campana cada cierto tiempo.
    @GetMapping("/resumen")
    public ResponseEntity<ResumenNotificaciones> resumen(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(new ResumenNotificaciones(notificacionService.noLeidas(usuario(jwt))));
    }

    // Marca una notificación como leída (404 si no existe o es de otro usuario).
    @PostMapping("/{id}/leida")
    public ResponseEntity<NotificacionResponse> marcarLeida(@AuthenticationPrincipal Jwt jwt, @PathVariable Integer id) {
        return ResponseEntity.ok(notificacionService.marcarLeida(usuario(jwt), id));
    }

    @PostMapping("/leer-todas")
    public ResponseEntity<ResumenNotificaciones> leerTodas(@AuthenticationPrincipal Jwt jwt) {
        notificacionService.marcarTodasLeidas(usuario(jwt));
        return ResponseEntity.ok(new ResumenNotificaciones(0));
    }

    // HU-24: lo que tiene elegido el usuario para cada tipo de aviso.
    @GetMapping("/preferencias")
    public ResponseEntity<List<PreferenciaResponse>> preferencias(@AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(preferenciaService.listar(usuario(jwt)));
    }

    // HU-24: guarda los cambios (400 con "campos" si algún tipo no es válido) y devuelve la lista actualizada.
    @PutMapping("/preferencias")
    public ResponseEntity<List<PreferenciaResponse>> guardarPreferencias(@AuthenticationPrincipal Jwt jwt,
                                                                         @RequestBody PreferenciasRequest request) {
        return ResponseEntity.ok(preferenciaService.guardar(usuario(jwt), request.preferencias()));
    }

    private static Integer usuario(Jwt jwt) {
        String rol = jwt.getClaimAsString("rol");
        if (!"ESTUDIANTE".equals(rol) && !"MENTOR".equals(rol)) {
            throw new ReglaNegocioException(HttpStatus.FORBIDDEN, "Solo estudiantes y mentores tienen notificaciones.");
        }
        return Integer.valueOf(jwt.getSubject());
    }
}
