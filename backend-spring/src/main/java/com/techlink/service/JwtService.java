package com.techlink.service;

import com.techlink.model.Usuario;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

// HU-02: Genera los tokens JWT que se entregan al iniciar sesion.
@Service
public class JwtService {

    private final JwtEncoder jwtEncoder;
    private final long expiracionMinutos;

    public JwtService(JwtEncoder jwtEncoder,
                      @Value("${app.jwt.expiracion-minutos:60}") long expiracionMinutos) {
        this.jwtEncoder = jwtEncoder;
        this.expiracionMinutos = expiracionMinutos;
    }

    // Crea un token firmado con el id, correo y rol del usuario. Vence a los 60 minutos (configurable).
    public String generarToken(Usuario usuario) {
        Instant ahora = Instant.now();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer("techlink")
                .subject(String.valueOf(usuario.getId()))
                .issuedAt(ahora)
                .expiresAt(ahora.plus(Duration.ofMinutes(expiracionMinutos)))
                .claim("correo", usuario.getCorreoInstitucional())
                .claim("rol", usuario.getRol().getNombre())
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }

    // Duracion del token en segundos (se informa al frontend).
    public long getExpiracionSegundos() {
        return expiracionMinutos * 60;
    }
}
