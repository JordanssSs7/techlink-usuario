package com.techlink.dto;

import java.time.LocalDateTime;
import java.util.List;

// HU-22 y HU-24: lo que ve el usuario en el centro de notificaciones y en sus preferencias.
public final class NotificacionDtos {

    private NotificacionDtos() {
    }

    // tipo: SESION, RECORDATORIO o ANUNCIO.
    public record NotificacionResponse(Integer id, String tipo, String titulo, String mensaje, boolean leida,
                                       LocalDateTime fecha) {
    }

    // noLeidas cuenta todas las pendientes, aunque la lista venga recortada.
    public record NotificacionesResponse(long noLeidas, List<NotificacionResponse> notificaciones) {
    }

    public record ResumenNotificaciones(long noLeidas) {
    }

    // correoDisponible: false si ese tipo no se envía por correo (los anuncios solo aparecen en la aplicación).
    public record PreferenciaResponse(String tipo, String nombre, String descripcion, boolean enApp, boolean correo,
                                      boolean correoDisponible) {
    }

    // Un cambio: los campos que vengan null no se tocan.
    public record PreferenciaCambio(String tipo, Boolean enApp, Boolean correo) {
    }

    public record PreferenciasRequest(List<PreferenciaCambio> preferencias) {
    }
}
