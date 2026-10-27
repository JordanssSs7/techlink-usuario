package com.techlink.config;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;

// HU-02: Configura como se FIRMAN (encoder) y se VALIDAN (decoder) los tokens JWT con una clave secreta (HS256).
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);

    private final SecretKey secretKey;

    // La clave viene de la variable de entorno JWT_SECRET (minimo 32 caracteres).
    // Si no existe, se genera una al azar: sirve para desarrollo, pero los tokens dejan de valer al reiniciar.
    public JwtConfig(@Value("${app.jwt.secret:}") String secret) {
        byte[] bytes;
        if (secret == null || secret.length() < 32) {
            bytes = new byte[32];
            new SecureRandom().nextBytes(bytes);
            log.warn("JWT_SECRET no definida o menor a 32 caracteres: se usa una clave temporal. "
                    + "Definela como variable de entorno para que los tokens sobrevivan a un reinicio.");
        } else {
            bytes = secret.getBytes(StandardCharsets.UTF_8);
        }
        this.secretKey = new SecretKeySpec(bytes, "HmacSHA256");
    }

    // Firma los tokens que se entregan al iniciar sesion.
    @Bean
    public JwtEncoder jwtEncoder() {
        return new NimbusJwtEncoder(new ImmutableSecret<>(secretKey));
    }

    // Valida la firma y el vencimiento de los tokens que llegan en cada peticion protegida.
    @Bean
    public JwtDecoder jwtDecoder() {
        return NimbusJwtDecoder.withSecretKey(secretKey).macAlgorithm(MacAlgorithm.HS256).build();
    }
}
