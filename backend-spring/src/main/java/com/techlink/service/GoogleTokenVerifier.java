package com.techlink.service;

import com.techlink.exception.CorreoNoInstitucionalException;
import com.techlink.exception.CredencialesInvalidasException;
import com.techlink.exception.GoogleNoConfiguradoException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.Map;

// HU-02 (Google): canjea el codigo de Google por el ID token, lo verifica (firma, emisor, audiencia, vencimiento)
// y comprueba que el correo este verificado y sea del dominio institucional.
@Component
public class GoogleTokenVerifier {

    private static final String DOMINIO_INSTITUCIONAL = "@tecsup.edu.pe";

    // Datos del usuario que se toman del token de Google.
    public record GoogleUsuario(String googleId, String correo, String nombres, String apellidos) {
    }

    private final String clientId;
    private final String clientSecret;
    private final String tokenUri;
    private final String jwksUri;
    private NimbusJwtDecoder decoder;

    // Las direcciones de Google se pueden cambiar por propiedades (solo se hace en las pruebas automaticas).
    @Autowired
    public GoogleTokenVerifier(@Value("${app.google.client-id:}") String clientId,
                               @Value("${app.google.client-secret:}") String clientSecret,
                               @Value("${app.google.token-uri:https://oauth2.googleapis.com/token}") String tokenUri,
                               @Value("${app.google.jwks-uri:https://www.googleapis.com/oauth2/v3/certs}") String jwksUri) {
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.tokenUri = tokenUri;
        this.jwksUri = jwksUri;
    }

    // Verifica lo que envio el frontend (code o idToken) y devuelve los datos del usuario de Google.
    public GoogleUsuario verificar(String code, String idToken) {
        if (clientId == null || clientId.isBlank()) {
            throw new GoogleNoConfiguradoException("El inicio de sesión con Google no está configurado");
        }
        String token = (code != null && !code.isBlank()) ? canjearCodigo(code) : idToken;
        Jwt jwt;
        try {
            jwt = decoder().decode(token);
        } catch (JwtException e) {
            throw new CredencialesInvalidasException("El token de Google no es válido o ya venció");
        }
        return extraerUsuario(jwt);
    }

    // Canjea el codigo de autorizacion por el ID token llamando a Google (redirect_uri=postmessage es el del popup).
    private String canjearCodigo(String code) {
        if (clientSecret == null || clientSecret.isBlank()) {
            throw new GoogleNoConfiguradoException("El inicio de sesión con Google no está configurado");
        }
        MultiValueMap<String, String> formulario = new LinkedMultiValueMap<>();
        formulario.add("code", code);
        formulario.add("client_id", clientId);
        formulario.add("client_secret", clientSecret);
        formulario.add("redirect_uri", "postmessage");
        formulario.add("grant_type", "authorization_code");
        try {
            Map<?, ?> respuesta = RestClient.create().post()
                    .uri(tokenUri)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(formulario)
                    .retrieve()
                    .body(Map.class);
            Object idToken = respuesta == null ? null : respuesta.get("id_token");
            if (idToken == null) {
                throw new CredencialesInvalidasException("Google no devolvió la identidad del usuario");
            }
            return idToken.toString();
        } catch (RestClientException e) {
            throw new CredencialesInvalidasException("El código de Google no es válido o ya fue usado");
        }
    }

    // Reglas sobre el contenido del token (separado para poder probarlo sin conectarse a Google).
    GoogleUsuario extraerUsuario(Jwt jwt) {
        String correo = jwt.getClaimAsString("email");
        Object verificado = jwt.getClaim("email_verified");
        boolean correoVerificado = Boolean.TRUE.equals(verificado) || "true".equals(String.valueOf(verificado));

        if (correo == null || !correoVerificado) {
            throw new CredencialesInvalidasException("Tu cuenta de Google no tiene el correo verificado");
        }
        if (!correo.toLowerCase().endsWith(DOMINIO_INSTITUCIONAL)) {
            throw new CorreoNoInstitucionalException("Solo se permiten cuentas " + DOMINIO_INSTITUCIONAL);
        }

        String nombres = jwt.getClaimAsString("given_name");
        if (nombres == null || nombres.isBlank()) {
            nombres = correo.substring(0, correo.indexOf('@'));
        }
        String apellidos = jwt.getClaimAsString("family_name");
        if (apellidos == null || apellidos.isBlank()) {
            apellidos = "-";
        }
        // "sub" es el identificador unico y permanente de la cuenta de Google.
        return new GoogleUsuario(jwt.getSubject(), correo, nombres, apellidos);
    }

    // Decodificador con las claves de Google; se crea una sola vez, la primera que se necesita.
    private synchronized NimbusJwtDecoder decoder() {
        if (decoder == null) {
            decoder = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
            decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                    new JwtTimestampValidator(), validarEmisor(), validarAudiencia()));
        }
        return decoder;
    }

    // El token debe haber sido emitido por Google.
    private OAuth2TokenValidator<Jwt> validarEmisor() {
        return jwt -> {
            String emisor = jwt.getClaimAsString("iss");
            boolean esGoogle = "https://accounts.google.com".equals(emisor) || "accounts.google.com".equals(emisor);
            return esGoogle ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Emisor no es Google", null));
        };
    }

    // El token debe haberse emitido para ESTA aplicacion (nuestro Client ID), no para otra.
    private OAuth2TokenValidator<Jwt> validarAudiencia() {
        return jwt -> jwt.getAudience() != null && jwt.getAudience().contains(clientId)
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Audiencia incorrecta", null));
    }
}
