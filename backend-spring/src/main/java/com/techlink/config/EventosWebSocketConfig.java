package com.techlink.config;

import com.techlink.service.EventosRegistro;
import com.techlink.service.EventosService;
import com.techlink.service.SalaTicketService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

// Registra /ws/eventos. Se entra con un ticket de un solo uso (POST /api/eventos/ticket), igual que la sala virtual.
@Configuration
public class EventosWebSocketConfig implements WebSocketConfigurer {

    private final EventosRegistro registro;
    private final EventosService eventos;
    private final SalaTicketService tickets;
    private final String[] origenesPermitidos;

    public EventosWebSocketConfig(EventosRegistro registro, EventosService eventos, SalaTicketService tickets,
                                  @Value("${app.sala.origenes-permitidos:http://localhost:5173,http://localhost:3000}") String[] origenesPermitidos) {
        this.registro = registro;
        this.eventos = eventos;
        this.tickets = tickets;
        this.origenesPermitidos = origenesPermitidos;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(new EventosWebSocketHandler(registro, eventos), "/ws/eventos")
                .addInterceptors(new SalaWebSocketConfig.TicketInterceptor(tickets))
                .setAllowedOrigins(origenesPermitidos);
    }
}
