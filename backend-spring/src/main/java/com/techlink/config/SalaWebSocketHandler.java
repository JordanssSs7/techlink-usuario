package com.techlink.config;

import com.techlink.dto.SalaDtos.Mensaje;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.service.SalaAccesoService;
import com.techlink.service.SalaMensajeService;
import com.techlink.service.SalaRegistro;
import com.techlink.service.SalaTicketService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

// HU-08: la conexión en tiempo real de la sala virtual (/ws/sala). Por aquí viaja:
//  - el chat y los fragmentos de código (se guardan y se reparten a todos los de la sala),
//  - las señales de la videollamada (ofertas, respuestas y candidatos de WebRTC), que el servidor solo reenvía de una
//    persona a otra: el video y el audio van directo entre los navegadores, no pasan por aquí,
//  - el estado de cada participante (cámara, micrófono y pantalla compartida) y quién entra o sale.
// Todos los mensajes son JSON con un campo "t" que dice de qué tipo son.
public class SalaWebSocketHandler extends TextWebSocketHandler {

    private static final Logger logger = LoggerFactory.getLogger(SalaWebSocketHandler.class);
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final int TIEMPO_DE_ENVIO_MS = 10_000;
    private static final int BUFFER_DE_ENVIO = 1024 * 1024;

    static final String ATRIBUTO_DATOS = "sala.datos";
    private static final String ATRIBUTO_CANAL = "sala.canal";
    private static final String ATRIBUTO_SALA = "sala.sala";

    private final SalaRegistro registro;
    private final SalaAccesoService acceso;
    private final SalaMensajeService mensajes;

    public SalaWebSocketHandler(SalaRegistro registro, SalaAccesoService acceso, SalaMensajeService mensajes) {
        this.registro = registro;
        this.acceso = acceso;
        this.mensajes = mensajes;
    }

    // Un WebSocket de Spring no admite dos envíos a la vez: el decorador los pone en fila.
    private static final class CanalWs implements SalaRegistro.Canal {
        private final WebSocketSession sesion;

        CanalWs(WebSocketSession sesion) {
            this.sesion = new ConcurrentWebSocketSessionDecorator(sesion, TIEMPO_DE_ENVIO_MS, BUFFER_DE_ENVIO);
        }

        @Override
        public void enviar(String json) {
            try {
                sesion.sendMessage(new TextMessage(json));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }

        @Override
        public void cerrar(int codigo, String motivo) {
            try {
                sesion.close(new CloseStatus(codigo, motivo));
            } catch (IOException e) {
                // Ya estaba cerrada.
            }
        }
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        SalaTicketService.Datos datos = (SalaTicketService.Datos) session.getAttributes().get(ATRIBUTO_DATOS);
        CanalWs canal = new CanalWs(session);
        try {
            // Se vuelve a comprobar todo: entre pedir el ticket y conectar pudo pasar algo (cancelaron la sesión, cerró la sala).
            SalaAccesoService.Sala sala = acceso.autorizar(datos.usuarioId(), datos.tipo(), datos.salaId());
            if (!sala.abiertaEn(acceso.ahora())) {
                rechazar(canal, sala.motivoCerrada(acceso.ahora()), CloseStatus.POLICY_VIOLATION.getCode());
                return;
            }
            SalaRegistro.Resultado resultado = registro.unir(sala.clave(), datos.usuarioId(), datos.nombre(), datos.rol(),
                    canal, sala.maxParticipantes());
            if (resultado instanceof SalaRegistro.Llena) {
                rechazar(canal, "La sala está llena (máximo " + sala.maxParticipantes() + " personas).", SalaRegistro.SALA_LLENA);
                return;
            }
            SalaRegistro.Unido unido = (SalaRegistro.Unido) resultado;
            session.getAttributes().put(ATRIBUTO_CANAL, canal);
            session.getAttributes().put(ATRIBUTO_SALA, sala);

            Map<String, Object> bienvenida = new LinkedHashMap<>();
            bienvenida.put("t", "bienvenida");
            bienvenida.put("yo", Map.of("id", datos.usuarioId(), "nombre", datos.nombre(), "rol", datos.rol()));
            bienvenida.put("sala", Map.of("tipo", sala.tipo().name(), "id", sala.id(), "titulo", sala.titulo(),
                    "cierra", sala.cierra().toString()));
            bienvenida.put("participantes", unido.otros().stream().map(SalaWebSocketHandler::descripcion).toList());
            bienvenida.put("historial", mensajes.historial(sala));
            canal.enviar(JSON.writeValueAsString(bienvenida));

            registro.difundir(sala.clave(), JSON.writeValueAsString(Map.of("t", "entra", "participante", descripcion(unido.yo()))),
                    datos.usuarioId());
        } catch (ReglaNegocioException e) {
            rechazar(canal, e.getMessage(), CloseStatus.POLICY_VIOLATION.getCode());
        } catch (RuntimeException e) {
            logger.error("No se pudo abrir la sala", e);
            rechazar(canal, "No se pudo entrar a la sala.", CloseStatus.SERVER_ERROR.getCode());
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        SalaTicketService.Datos datos = (SalaTicketService.Datos) session.getAttributes().get(ATRIBUTO_DATOS);
        SalaAccesoService.Sala sala = (SalaAccesoService.Sala) session.getAttributes().get(ATRIBUTO_SALA);
        CanalWs canal = (CanalWs) session.getAttributes().get(ATRIBUTO_CANAL);
        if (sala == null || canal == null) {
            return;
        }
        JsonNode json;
        try {
            json = JSON.readTree(message.getPayload());
        } catch (RuntimeException e) {
            error(canal, "Mensaje no válido.");
            return;
        }
        String tipo = json.path("t").asString("");
        try {
            switch (tipo) {
                case "chat", "codigo" -> chat(sala, datos, canal, tipo, json);
                case "senal" -> senal(sala, datos, json);
                case "estado" -> estado(sala, datos, json);
                default -> error(canal, "Tipo de mensaje desconocido.");
            }
        } catch (DatosInvalidosException e) {
            error(canal, String.join(" ", e.getCampos().values()));
        } catch (ReglaNegocioException e) {
            error(canal, e.getMessage());
        } catch (RuntimeException e) {
            logger.error("Error al procesar un mensaje de la sala {}", sala.clave(), e);
            error(canal, "No se pudo procesar el mensaje.");
        }
    }

    private void chat(SalaAccesoService.Sala sala, SalaTicketService.Datos datos, CanalWs canal, String tipo, JsonNode json) {
        if (!sala.abiertaEn(acceso.ahora())) {
            error(canal, "La sala ya cerró.");
            canal.cerrar(CloseStatus.NORMAL.getCode(), "La sala ya cerró");
            return;
        }
        Mensaje mensaje = mensajes.enviar(sala, datos.usuarioId(), datos.nombre(), "codigo".equals(tipo) ? "CODIGO" : "TEXTO",
                json.path("lenguaje").asString(""), json.path("contenido").asString(""));
        registro.difundir(sala.clave(), JSON.writeValueAsString(Map.of("t", "mensaje", "mensaje", mensaje)), null);
    }

    // Reenvía una señal de WebRTC (oferta, respuesta o candidato ICE) a la persona indicada. El servidor no la interpreta.
    private void senal(SalaAccesoService.Sala sala, SalaTicketService.Datos datos, JsonNode json) {
        JsonNode para = json.path("para");
        if (!para.isInt() || !json.has("datos")) {
            return;
        }
        registro.enviarA(sala.clave(), para.asInt(), JSON.writeValueAsString(Map.of("t", "senal", "de", datos.usuarioId(),
                "datos", json.get("datos"))));
    }

    private void estado(SalaAccesoService.Sala sala, SalaTicketService.Datos datos, JsonNode json) {
        String stream = json.path("pantallaStream").asString("");
        SalaRegistro.Estado nuevo = new SalaRegistro.Estado(json.path("camara").asBoolean(false),
                json.path("microfono").asBoolean(false), json.path("pantalla").asBoolean(false),
                stream.isBlank() || stream.length() > 100 ? null : stream);
        registro.actualizarEstado(sala.clave(), datos.usuarioId(), nuevo);
        registro.difundir(sala.clave(), JSON.writeValueAsString(Map.of("t", "estado", "id", datos.usuarioId(),
                "estado", descripcionEstado(nuevo))), datos.usuarioId());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        salir(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        logger.debug("Error de transporte en la sala: {}", exception.getMessage());
        salir(session);
    }

    private void salir(WebSocketSession session) {
        SalaTicketService.Datos datos = (SalaTicketService.Datos) session.getAttributes().get(ATRIBUTO_DATOS);
        SalaAccesoService.Sala sala = (SalaAccesoService.Sala) session.getAttributes().get(ATRIBUTO_SALA);
        CanalWs canal = (CanalWs) session.getAttributes().get(ATRIBUTO_CANAL);
        if (datos == null || sala == null || canal == null) {
            return;
        }
        if (registro.salir(sala.clave(), datos.usuarioId(), canal)) {
            registro.difundir(sala.clave(), JSON.writeValueAsString(Map.of("t", "sale", "id", datos.usuarioId())), null);
        }
    }

    private static void rechazar(CanalWs canal, String motivo, int codigo) {
        error(canal, motivo);
        canal.cerrar(codigo, motivo == null ? "" : motivo.length() > 120 ? motivo.substring(0, 120) : motivo);
    }

    private static void error(CanalWs canal, String texto) {
        try {
            canal.enviar(JSON.writeValueAsString(Map.of("t", "error", "mensaje", texto == null ? "Error." : texto)));
        } catch (RuntimeException e) {
            // La conexión ya no responde.
        }
    }

    private static Map<String, Object> descripcion(SalaRegistro.Participante p) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("id", p.id());
        d.put("nombre", p.nombre());
        d.put("rol", p.rol());
        d.put("estado", descripcionEstado(p.estado()));
        return d;
    }

    private static Map<String, Object> descripcionEstado(SalaRegistro.Estado e) {
        Map<String, Object> d = new LinkedHashMap<>();
        d.put("camara", e.camara());
        d.put("microfono", e.microfono());
        d.put("pantalla", e.pantalla());
        d.put("pantallaStream", e.pantallaStream());
        return d;
    }

    // Lista de participantes ya presentes, para pruebas y diagnóstico.
    List<Map<String, Object>> presentes(SalaAccesoService.Sala sala) {
        List<Map<String, Object>> lista = new ArrayList<>();
        registro.participantes(sala.clave()).forEach(p -> lista.add(descripcion(p)));
        return lista;
    }
}
