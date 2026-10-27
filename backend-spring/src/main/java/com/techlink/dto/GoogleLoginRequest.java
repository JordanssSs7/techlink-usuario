package com.techlink.dto;

import jakarta.validation.constraints.AssertTrue;

// HU-02 (Google): lo que envia el frontend al iniciar sesion con Google.
// code: codigo de autorizacion que entrega el popup de Google (flujo usado por el frontend).
// idToken: alternativa, el token de identidad de Google ya listo. Debe llegar uno de los dos.
public record GoogleLoginRequest(String code, String idToken) {

    @AssertTrue(message = "Debes enviar el código de Google")
    public boolean isTieneCredencial() {
        return (code != null && !code.isBlank()) || (idToken != null && !idToken.isBlank());
    }
}
