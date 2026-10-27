package com.techlink.service;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.RSASSASigner;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.gen.RSAKeyGenerator;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.techlink.exception.CorreoNoInstitucionalException;
import com.techlink.exception.CredencialesInvalidasException;
import com.techlink.exception.GoogleNoConfiguradoException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

// Pruebas del login con Google (HU-02). Un servidor local hace de "Google": entrega las llaves publicas (/certs)
// y canjea codigos (/token), asi se prueba el flujo completo y la verificacion de la firma sin cuentas reales.
class GoogleTokenVerifierTest {

    private static final String CLIENT_ID = "cliente-de-prueba.apps.googleusercontent.com";
    private static final String SECRET = "secreto-de-prueba";
    private static final String EMISOR_GOOGLE = "https://accounts.google.com";

    private HttpServer googleFalso;
    private RSAKey llaveDeGoogle;
    private String urlBase;
    private GoogleTokenVerifier verificador;
    private String idTokenQueEntregaGoogle;

    @BeforeEach
    void iniciarGoogleFalso() throws Exception {
        llaveDeGoogle = new RSAKeyGenerator(2048).keyID("k1").generate();
        googleFalso = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        googleFalso.createContext("/certs", ex -> responder(ex, 200, new JWKSet(llaveDeGoogle.toPublicJWK()).toString()));
        googleFalso.createContext("/token", ex -> {
            String cuerpo = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            boolean valido = cuerpo.contains("code=codigo-valido")
                    && cuerpo.contains("redirect_uri=postmessage")
                    && cuerpo.contains("client_id=" + CLIENT_ID)
                    && cuerpo.contains("client_secret=" + SECRET)
                    && cuerpo.contains("grant_type=authorization_code");
            if (valido) {
                responder(ex, 200, "{\"id_token\":\"" + idTokenQueEntregaGoogle + "\"}");
            } else {
                responder(ex, 400, "{\"error\":\"invalid_grant\"}");
            }
        });
        googleFalso.start();
        urlBase = "http://127.0.0.1:" + googleFalso.getAddress().getPort();
        verificador = new GoogleTokenVerifier(CLIENT_ID, SECRET, urlBase + "/token", urlBase + "/certs");
    }

    @AfterEach
    void detenerGoogleFalso() {
        googleFalso.stop(0);
    }

    private static void responder(HttpExchange ex, int codigo, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream salida = ex.getResponseBody()) {
            salida.write(bytes);
        }
    }

    // Crea un ID token firmado con la llave indicada, como lo haria Google.
    private String firmar(RSAKey llave, String emisor, String audiencia, String correo, boolean verificado, Date vence)
            throws Exception {
        JWTClaimsSet claims = new JWTClaimsSet.Builder()
                .issuer(emisor).audience(audiencia).subject("123")
                .issueTime(new Date()).expirationTime(vence)
                .claim("email", correo).claim("email_verified", verificado)
                .claim("given_name", "Ana").claim("family_name", "Perez")
                .build();
        SignedJWT jwt = new SignedJWT(new JWSHeader.Builder(JWSAlgorithm.RS256).keyID("k1").build(), claims);
        jwt.sign(new RSASSASigner(llave));
        return jwt.serialize();
    }

    private Date enUnaHora() {
        return new Date(System.currentTimeMillis() + 3_600_000);
    }

    private String tokenValido(String correo) throws Exception {
        return firmar(llaveDeGoogle, EMISOR_GOOGLE, CLIENT_ID, correo, true, enUnaHora());
    }

    @Test
    void codigoValidoSeCanjeaYDevuelveElUsuario() throws Exception {
        idTokenQueEntregaGoogle = tokenValido("ana@tecsup.edu.pe");

        GoogleTokenVerifier.GoogleUsuario usuario = verificador.verificar("codigo-valido", null);

        assertEquals("ana@tecsup.edu.pe", usuario.correo());
        assertEquals("Ana", usuario.nombres());
        assertEquals("Perez", usuario.apellidos());
    }

    @Test
    void idTokenEnviadoDirectamenteTambienSeVerifica() throws Exception {
        GoogleTokenVerifier.GoogleUsuario usuario = verificador.verificar(null, tokenValido("ana@tecsup.edu.pe"));

        assertEquals("ana@tecsup.edu.pe", usuario.correo());
    }

    @Test
    void codigoQueGoogleRechazaEsInvalido() {
        assertThrows(CredencialesInvalidasException.class, () -> verificador.verificar("codigo-usado", null));
    }

    @Test
    void tokenFirmadoConOtraLlaveEsRechazado() throws Exception {
        RSAKey llaveDelAtacante = new RSAKeyGenerator(2048).keyID("k1").generate();
        String falso = firmar(llaveDelAtacante, EMISOR_GOOGLE, CLIENT_ID, "ana@tecsup.edu.pe", true, enUnaHora());

        assertThrows(CredencialesInvalidasException.class, () -> verificador.verificar(null, falso));
    }

    @Test
    void tokenEmitidoParaOtraAplicacionEsRechazado() throws Exception {
        String deOtraApp = firmar(llaveDeGoogle, EMISOR_GOOGLE, "otra-app.apps.googleusercontent.com",
                "ana@tecsup.edu.pe", true, enUnaHora());

        assertThrows(CredencialesInvalidasException.class, () -> verificador.verificar(null, deOtraApp));
    }

    @Test
    void tokenQueNoEsEmitidoPorGoogleEsRechazado() throws Exception {
        String deOtroEmisor = firmar(llaveDeGoogle, "https://sitio-falso.com", CLIENT_ID,
                "ana@tecsup.edu.pe", true, new Date(System.currentTimeMillis() + 3_600_000));

        assertThrows(CredencialesInvalidasException.class, () -> verificador.verificar(null, deOtroEmisor));
    }

    @Test
    void tokenVencidoEsRechazado() throws Exception {
        String vencido = firmar(llaveDeGoogle, EMISOR_GOOGLE, CLIENT_ID, "ana@tecsup.edu.pe", true,
                new Date(System.currentTimeMillis() - 600_000));

        assertThrows(CredencialesInvalidasException.class, () -> verificador.verificar(null, vencido));
    }

    @Test
    void correoSinVerificarEsRechazado() throws Exception {
        String sinVerificar = firmar(llaveDeGoogle, EMISOR_GOOGLE, CLIENT_ID, "ana@tecsup.edu.pe", false, enUnaHora());

        assertThrows(CredencialesInvalidasException.class, () -> verificador.verificar(null, sinVerificar));
    }

    @Test
    void cuentaDeOtroDominioEsRechazadaConAccesoDenegado() throws Exception {
        assertThrows(CorreoNoInstitucionalException.class,
                () -> verificador.verificar(null, tokenValido("ana@gmail.com")));
    }

    @Test
    void dominioFalsoQueContieneElInstitucionalEsRechazado() throws Exception {
        assertThrows(CorreoNoInstitucionalException.class,
                () -> verificador.verificar(null, tokenValido("ana@tecsup.edu.pe.evil.com")));
    }

    @Test
    void sinClientIdConfiguradoSeAvisaQueNoEstaDisponible() {
        GoogleTokenVerifier sinConfigurar = new GoogleTokenVerifier("", SECRET, urlBase + "/token", urlBase + "/certs");

        assertThrows(GoogleNoConfiguradoException.class, () -> sinConfigurar.verificar("codigo-valido", null));
    }

    @Test
    void sinSecretoNoSePuedeCanjearElCodigo() {
        GoogleTokenVerifier sinSecreto = new GoogleTokenVerifier(CLIENT_ID, "", urlBase + "/token", urlBase + "/certs");

        assertThrows(GoogleNoConfiguradoException.class, () -> sinSecreto.verificar("codigo-valido", null));
    }
}
