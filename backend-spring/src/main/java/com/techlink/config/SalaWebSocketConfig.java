package com.techlink.config;

import com.techlink.service.SalaAccesoService;
import com.techlink.service.SalaMensajeService;
import com.techlink.service.SalaRegistro;
import com.techlink.service.SalaTicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.Optional;

// HU-08: registra la conexión en tiempo real de la sala virtual en /ws/sala. No lleva el token de sesión en la dirección:
// se entra con un ticket de un solo uso que el frontend pidió antes por /api/salas/{tipo}/{id}/ticket.
@Configuration
@EnableWebSocket
public class SalaWebSocketConfig implements WebSocketConfigurer {

    // Una señal de WebRTC o un fragmento de código caben de sobra en 64 KB.
    private static final int MAX_MENSAJE_BYTES = 64 * 1024;

    private final SalaRegistro registro;
    private final SalaAccesoService acceso;
    private final SalaMensajeService mensajes;
    private final SalaTicketService tickets;
    private final String[] origenesPermitidos;

    public SalaWebSocketConfig(SalaRegistro registro, SalaAccesoService acceso, SalaMensajeService mensajes,
                               SalaTicketService tickets,
                               @Value("${app.sala.origenes-permitidos:http://localhost:5173,http://localhost:3000}") String[] origenesPermitidos) {
        this.registro = registro;
        this.acceso = acceso;
        this.mensajes = mensajes;
        this.tickets = tickets;
        this.origenesPermitidos = origenesPermitidos;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new SalaWebSocketHandler(registro, acceso, mensajes), "/ws/sala")
                .addInterceptors(new TicketInterceptor(tickets))
                .setAllowedOrigins(origenesPermitidos);
    }

    @Bean
    public ServletServerContainerFactoryBean salaContenedorWebSocket() {
        ServletServerContainerFactoryBean contenedor = new ServletServerContainerFactoryBean();
        contenedor.setMaxTextMessageBufferSize(MAX_MENSAJE_BYTES);
        return contenedor;
    }

    // Antes de abrir la conexión, canjea el ticket. Sin ticket válido (o ya usado, o vencido) responde 401.
    static final class TicketInterceptor implements HandshakeInterceptor {
        private final SalaTicketService tickets;

        TicketInterceptor(SalaTicketService tickets) {
            this.tickets = tickets;
        }

        @Override
        public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                       Map<String, Object> attributes) {
            String ticket = UriComponentsBuilder.fromUri(request.getURI()).build().getQueryParams().getFirst("ticket");
            Optional<SalaTicketService.Datos> datos = tickets.consumir(ticket);
            if (datos.isEmpty()) {
                response.setStatusCode(HttpStatus.UNAUTHORIZED);
                return false;
            }
            attributes.put(SalaWebSocketHandler.ATRIBUTO_DATOS, datos.get());
            return true;
        }

        @Override
        public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response, WebSocketHandler handler,
                                   Exception exception) {
        }
    }
}
