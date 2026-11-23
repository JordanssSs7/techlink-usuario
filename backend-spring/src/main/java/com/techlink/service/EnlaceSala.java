package com.techlink.service;

// La dirección de la sala virtual de una sesión: es la que va en los correos, avisos y botones. Al abrirla, la aplicación lleva a la persona
// a «Mis sesiones» con la sala de esa sesión abierta.
public final class EnlaceSala {

    private EnlaceSala() {
    }

    public static String paraSesion(String urlBase, Integer sesionId) {
        String base = urlBase == null || urlBase.isBlank() ? "http://localhost:5173" : urlBase.replaceAll("/+$", "");
        return base + "/sala/sesion/" + sesionId;
    }
}
