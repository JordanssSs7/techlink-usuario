package com.techlink.service;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.techlink.exception.IaNoDisponibleException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

// Pruebas del cliente de Gemini (HU-04). Un servidor local hace de Gemini y responde con la forma de la API real,
// asi se prueba la peticion y la lectura de la respuesta sin internet y sin gastar la cuota gratuita.
class GeminiClientTest {

    private static final String CLAVE = "clave-de-prueba-123";

    private HttpServer servidor;
    private String urlBase;
    private final AtomicReference<String> rutaRecibida = new AtomicReference<>();
    private final AtomicReference<String> claveRecibida = new AtomicReference<>();
    private final AtomicReference<String> cuerpoRecibido = new AtomicReference<>();
    private volatile int codigoRespuesta = 200;
    private volatile String cuerpoRespuesta;
    private volatile long demoraMs = 0;
    private final java.util.Map<String, Integer> codigosPorModelo = new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.List<String> modelosConsultados = new java.util.concurrent.CopyOnWriteArrayList<>();

    @BeforeEach
    void iniciarGeminiFalso() throws Exception {
        cuerpoRespuesta = "{\"candidates\":[{\"content\":{\"parts\":[{\"text\":\"{\\\"recomendaciones\\\":[]}\"}]}}]}";
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1beta", ex -> {
            rutaRecibida.set(ex.getRequestURI().getPath());
            claveRecibida.set(ex.getRequestHeaders().getFirst("x-goog-api-key"));
            cuerpoRecibido.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            if (demoraMs > 0) {
                try {
                    Thread.sleep(demoraMs);
                } catch (InterruptedException ignorada) {
                    Thread.currentThread().interrupt();
                }
            }
            modelosConsultados.add(ex.getRequestURI().getPath());
            int codigo = codigoRespuesta;
            for (Map.Entry<String, Integer> caso : codigosPorModelo.entrySet()) {
                if (ex.getRequestURI().getPath().contains("/" + caso.getKey() + ":")) {
                    codigo = caso.getValue();
                }
            }
            responder(ex, codigo, codigo == 200 ? cuerpoRespuesta : "{\"error\":{\"code\":" + codigo + "}}");
        });
        servidor.start();
        urlBase = "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1beta";
    }

    @AfterEach
    void detenerGeminiFalso() {
        servidor.stop(0);
    }

    private static void responder(HttpExchange ex, int codigo, String cuerpo) throws IOException {
        byte[] bytes = cuerpo.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(codigo, bytes.length);
        try (OutputStream salida = ex.getResponseBody()) {
            salida.write(bytes);
        }
    }

    private GeminiClient cliente(String clave) {
        return new GeminiClient(clave, "gemini-prueba", urlBase, 3);
    }

    private static final Map<String, Object> ESQUEMA = Map.of("type", "OBJECT");

    @Test
    void enviaLaPeticionConLaFormaDeLaApiDeGemini() {
        String texto = cliente(CLAVE).generarJson("Eres un asistente", "Recomienda un mentor", ESQUEMA);

        assertEquals("{\"recomendaciones\":[]}", texto);
        assertEquals("/v1beta/models/gemini-prueba:generateContent", rutaRecibida.get());
        String cuerpo = cuerpoRecibido.get();
        assertTrue(cuerpo.contains("\"systemInstruction\""));
        assertTrue(cuerpo.contains("Eres un asistente"));
        assertTrue(cuerpo.contains("Recomienda un mentor"));
        assertTrue(cuerpo.contains("\"responseMimeType\":\"application/json\""));
        assertTrue(cuerpo.contains("\"responseSchema\""));
    }

    @Test
    void laClaveViajaEnUnEncabezadoYNuncaEnLaDireccion() {
        cliente(CLAVE).generarJson("s", "c", ESQUEMA);

        assertEquals(CLAVE, claveRecibida.get());
        assertFalse(rutaRecibida.get().contains(CLAVE));
    }

    @Test
    void losEspaciosInvisiblesDeLaClaveSeIgnoran() {
        cliente(" " + CLAVE + " \n").generarJson("s", "c", ESQUEMA);

        assertEquals(CLAVE, claveRecibida.get());
    }

    @Test
    void sinClaveNoEstaConfiguradoYNoHaceLlamadas() {
        GeminiClient sinClave = cliente("");

        assertFalse(sinClave.estaConfigurado());
        assertThrows(IaNoDisponibleException.class, () -> sinClave.generarJson("s", "c", ESQUEMA));
        assertNull(rutaRecibida.get());
    }

    @Test
    void conClaveEstaConfigurado() {
        assertTrue(cliente(CLAVE).estaConfigurado());
    }

    @Test
    void ignoraLasPartesDePensamientoYUneElTexto() {
        cuerpoRespuesta = "{\"candidates\":[{\"content\":{\"parts\":["
                + "{\"text\":\"razonando...\",\"thought\":true},"
                + "{\"text\":\"{\\\"a\\\":\"},{\"text\":\"1}\"}]}}]}";

        assertEquals("{\"a\":1}", cliente(CLAVE).generarJson("s", "c", ESQUEMA));
    }

    @Test
    void unLimiteDeUsoDeGoogleSeConvierteEnIaNoDisponible() {
        codigoRespuesta = 429;
        cuerpoRespuesta = "{\"error\":{\"code\":429,\"message\":\"quota\"}}";

        assertThrows(IaNoDisponibleException.class, () -> cliente(CLAVE).generarJson("s", "c", ESQUEMA));
    }

    @Test
    void unaClaveRechazadaPorGoogleSeConvierteEnIaNoDisponible() {
        codigoRespuesta = 403;
        cuerpoRespuesta = "{\"error\":{\"code\":403,\"message\":\"API key not valid\"}}";

        assertThrows(IaNoDisponibleException.class, () -> cliente(CLAVE).generarJson("s", "c", ESQUEMA));
    }

    @Test
    void unaRespuestaSinCandidatosEsIaNoDisponible() {
        cuerpoRespuesta = "{\"promptFeedback\":{\"blockReason\":\"SAFETY\"}}";

        assertThrows(IaNoDisponibleException.class, () -> cliente(CLAVE).generarJson("s", "c", ESQUEMA));
    }

    @Test
    void unaRespuestaVaciaEsIaNoDisponible() {
        cuerpoRespuesta = "{\"candidates\":[{\"content\":{\"parts\":[]}}]}";

        assertThrows(IaNoDisponibleException.class, () -> cliente(CLAVE).generarJson("s", "c", ESQUEMA));
    }

    @Test
    void unaRespuestaQueTardaDemasiadoSeCortaPorTiempo() {
        demoraMs = 5_000;
        GeminiClient impaciente = new GeminiClient(CLAVE, "gemini-prueba", urlBase, 1);

        assertThrows(IaNoDisponibleException.class, () -> impaciente.generarJson("s", "c", ESQUEMA));
    }

    @Test
    void siUnModeloEstaSaturadoUsaElSiguienteDeLaLista() {
        codigosPorModelo.put("saturado", 503);
        codigosPorModelo.put("retirado", 404);
        GeminiClient cliente = new GeminiClient(CLAVE, "saturado, retirado ,bueno", urlBase, 3);

        assertEquals("{\"recomendaciones\":[]}", cliente.generarJson("s", "c", ESQUEMA));
        assertEquals(3, modelosConsultados.size());
        assertTrue(modelosConsultados.get(2).endsWith("/models/bueno:generateContent"));
    }

    @Test
    void siRespondeElPrimerModeloNoSeConsultaElResto() {
        GeminiClient cliente = new GeminiClient(CLAVE, "primero,segundo", urlBase, 3);

        cliente.generarJson("s", "c", ESQUEMA);

        assertEquals(1, modelosConsultados.size());
    }

    @Test
    void siFallanTodosLosModelosEsIaNoDisponibleSinFiltrarLaClave() {
        codigoRespuesta = 503;
        GeminiClient cliente = new GeminiClient(CLAVE, "uno,dos", urlBase, 3);

        IaNoDisponibleException error = assertThrows(IaNoDisponibleException.class,
                () -> cliente.generarJson("s", "c", ESQUEMA));

        assertEquals(2, modelosConsultados.size());
        assertTrue(error.getMessage().contains("uno") && error.getMessage().contains("dos"));
        assertFalse(error.getMessage().contains(CLAVE));
    }

    @Test
    void unaListaDeModelosVaciaNoEstaConfigurada() {
        assertFalse(new GeminiClient(CLAVE, " , ", urlBase, 3).estaConfigurado());
    }

    @Test
    void elMensajeDeErrorNoFiltraLaClaveNiLaConsulta() {
        codigoRespuesta = 500;

        IaNoDisponibleException error = assertThrows(IaNoDisponibleException.class,
                () -> cliente(CLAVE).generarJson("s", "consulta secreta del estudiante", ESQUEMA));

        assertFalse(error.getMessage().contains(CLAVE));
        assertFalse(error.getMessage().contains("consulta secreta"));
    }
}
