package com.techlink.service;

import com.techlink.exception.IaNoDisponibleException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

// HU-04: cliente de la API de Gemini (Google AI Studio, plan gratuito). Hace una consulta y devuelve el texto JSON
// que respondio el modelo. La clave se toma de la variable de entorno GEMINI_API_KEY y nunca se escribe en logs.
// La disponibilidad del plan gratuito cambia modelo por modelo (uno puede estar saturado y otro no) y los nombres de
// los modelos se retiran con el tiempo, por eso se configura una LISTA de modelos y se usa el primero que responda.
@Component
public class GeminiClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiClient.class);

    private final String apiKey;
    private final List<String> modelos;
    private final String baseUrl;
    private final RestClient restClient;

    // modelos: nombres separados por coma, en orden de preferencia (GEMINI_MODEL).
    public GeminiClient(@Value("${app.gemini.api-key:}") String apiKey,
                        @Value("${app.gemini.model:gemini-3.5-flash,gemini-3.5-flash-lite,gemini-flash-lite-latest}") String modelos,
                        @Value("${app.gemini.base-url:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
                        @Value("${app.gemini.timeout-segundos:12}") int timeoutSegundos) {
        this.apiKey = sinEspacios(apiKey);
        this.modelos = nombresDeModelos(modelos);
        this.baseUrl = baseUrl == null ? "" : baseUrl.trim().replaceAll("/+$", "");

        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(timeoutSegundos));
        this.restClient = RestClient.builder().requestFactory(fabrica).build();
    }

    // Una clave nunca lleva espacios: si se copio de un chat o una pagina puede colarse uno invisible.
    private static String sinEspacios(String valor) {
        return valor == null ? "" : valor.replaceAll("[\\s\\p{Z}\\u200B\\uFEFF]", "");
    }

    private static List<String> nombresDeModelos(String lista) {
        List<String> nombres = new ArrayList<>();
        if (lista != null) {
            for (String nombre : Arrays.asList(lista.split(","))) {
                String limpio = sinEspacios(nombre);
                if (!limpio.isEmpty() && !nombres.contains(limpio)) {
                    nombres.add(limpio);
                }
            }
        }
        return nombres;
    }

    public boolean estaConfigurado() {
        return !apiKey.isEmpty() && !modelos.isEmpty() && !baseUrl.isEmpty();
    }

    // Envia la instruccion del sistema y la consulta, pidiendo la respuesta en JSON con la forma del esquema.
    // Prueba los modelos en orden y devuelve la respuesta del primero que conteste. Si fallan todos, lanza
    // IaNoDisponibleException; el que llama decide el plan de respaldo.
    public String generarJson(String instruccionSistema, String consulta, Map<String, Object> esquema) {
        if (!estaConfigurado()) {
            throw new IaNoDisponibleException("La IA no está configurada");
        }

        Map<String, Object> cuerpo = Map.of(
                "systemInstruction", Map.of("parts", List.of(Map.of("text", instruccionSistema))),
                "contents", List.of(Map.of("role", "user", "parts", List.of(Map.of("text", consulta)))),
                "generationConfig", Map.of(
                        "temperature", 0.2,
                        "responseMimeType", "application/json",
                        "responseSchema", esquema));

        List<String> fallos = new ArrayList<>();
        for (String modelo : modelos) {
            try {
                String texto = extraerTexto(consultar(modelo, cuerpo));
                log.info("Gemini respondio con el modelo {}", modelo);
                return texto;
            } catch (IaNoDisponibleException e) {
                // Solo se guarda el motivo corto (nunca la clave ni la consulta) y se prueba el siguiente modelo.
                fallos.add(modelo + ": " + e.getMessage());
                log.warn("El modelo {} no respondio: {}", modelo, e.getMessage());
            }
        }
        throw new IaNoDisponibleException("Ningún modelo de Gemini respondió (" + String.join("; ", fallos) + ")");
    }

    private Map<?, ?> consultar(String modelo, Map<String, Object> cuerpo) {
        try {
            return restClient.post()
                    .uri(baseUrl + "/models/" + modelo + ":generateContent")
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(cuerpo)
                    .retrieve()
                    .body(Map.class);
        } catch (RuntimeException e) {
            // Se guarda solo el tipo de error (por ejemplo ServiceUnavailable o NotFound): el mensaje podria traer la consulta.
            throw new IaNoDisponibleException(e.getClass().getSimpleName(), e);
        }
    }

    // candidates[0].content.parts[*].text, ignorando las partes de "pensamiento" que algunos modelos agregan.
    private static String extraerTexto(Map<?, ?> respuesta) {
        try {
            List<?> candidatos = (List<?>) respuesta.get("candidates");
            Map<?, ?> contenido = (Map<?, ?>) ((Map<?, ?>) candidatos.get(0)).get("content");
            StringBuilder texto = new StringBuilder();
            for (Object parte : (List<?>) contenido.get("parts")) {
                Map<?, ?> p = (Map<?, ?>) parte;
                if (!Boolean.TRUE.equals(p.get("thought")) && p.get("text") != null) {
                    texto.append(p.get("text"));
                }
            }
            if (texto.isEmpty()) {
                throw new IaNoDisponibleException("respuesta vacía");
            }
            return texto.toString();
        } catch (IaNoDisponibleException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new IaNoDisponibleException("formato inesperado", e);
        }
    }
}
