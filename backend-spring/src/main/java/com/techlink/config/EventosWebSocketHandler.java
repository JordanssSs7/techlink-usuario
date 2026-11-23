package com.techlink.config;

import com.techlink.service.EventosRegistro;
import com.techlink.service.EventosService;
import com.techlink.service.SalaRegistro;
import com.techlink.service.SalaTicketService;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.ConcurrentWebSocketSessionDecorator;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.stream.Collectors;

// Conexión en tiempo real general (/ws/eventos): el servidor le avisa a cada navegador quién está en línea, cuándo llega un aviso
// nuevo y cuándo alguien cambia su foto. El navegador no envía nada por aquí.
public class EventosWebSocketHandler extends TextWebSocketHandler {

    private static final String ATRIBUTO_CANAL = "eventos.canal";

    private final EventosRegistro registro;
    private final EventosService eventos;

    public EventosWebSocketHandler(EventosRegistro registro, EventosService eventos) {
        this.registro = registro;
        this.eventos = eventos;
    }

    private static final class CanalWs implements SalaRegistro.Canal {
        private final WebSocketSession sesion;

        CanalWs(WebSocketSession sesion) {
            this.sesion = new ConcurrentWebSocketSessionDecorator(sesion, 10_000, 256 * 1024);
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
        SalaTicketService.Datos datos = (SalaTicketService.Datos) session.getAttributes().get(SalaWebSocketHandler.ATRIBUTO_DATOS);
        CanalWs canal = new CanalWs(session);
        session.getAttributes().put(ATRIBUTO_CANAL, canal);
        boolean primera = registro.conectar(datos.usuarioId(), canal);
        String lista = registro.enLinea().stream().map(String::valueOf).collect(Collectors.joining(","));
        canal.enviar("{\"t\":\"presencia-inicial\",\"enLinea\":[" + lista + "]}");
        if (primera) {
            eventos.presencia(datos.usuarioId(), true);
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        salir(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        salir(session);
    }

    private void salir(WebSocketSession session) {
        SalaTicketService.Datos datos = (SalaTicketService.Datos) session.getAttributes().get(SalaWebSocketHandler.ATRIBUTO_DATOS);
        Object canal = session.getAttributes().remove(ATRIBUTO_CANAL);
        if (datos != null && canal instanceof SalaRegistro.Canal c && registro.desconectar(datos.usuarioId(), c)) {
            eventos.presencia(datos.usuarioId(), false);
        }
    }
}
