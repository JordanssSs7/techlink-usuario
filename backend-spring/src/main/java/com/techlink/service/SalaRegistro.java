package com.techlink.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

// HU-08: quién está conectado en cada sala virtual ahora mismo (en memoria: se vacía al reiniciar el servidor, y los
// navegadores se reconectan). Cada participante tiene un "canal" para enviarle mensajes, que en la práctica es su
// conexión WebSocket; se separa para poder probar la lógica sin conexiones reales.
@Component
public class SalaRegistro {

    // Por dónde se le escribe a un participante y cómo se le cierra la conexión.
    public interface Canal {
        void enviar(String json);

        void cerrar(int codigo, String motivo);
    }

    // Estado de cámara, micrófono y pantalla compartida, para que los demás lo muestren (por ejemplo "cámara apagada").
    public record Estado(boolean camara, boolean microfono, boolean pantalla, String pantallaStream, boolean grabando, boolean pausada,
                         boolean mano) {

        public Estado(boolean camara, boolean microfono, boolean pantalla, String pantallaStream, boolean grabando, boolean pausada) {
            this(camara, microfono, pantalla, pantallaStream, grabando, pausada, false);
        }

        public Estado(boolean camara, boolean microfono, boolean pantalla, String pantallaStream) {
            this(camara, microfono, pantalla, pantallaStream, false, false, false);
        }

        public static final Estado INICIAL = new Estado(false, false, false, null);
    }

    public static final class Participante {
        private final int id;
        private final String nombre;
        private final String rol;
        private final Canal canal;
        private volatile Estado estado = Estado.INICIAL;

        Participante(int id, String nombre, String rol, Canal canal) {
            this.id = id;
            this.nombre = nombre;
            this.rol = rol;
            this.canal = canal;
        }

        public int id() { return id; }
        public String nombre() { return nombre; }
        public String rol() { return rol; }
        public Canal canal() { return canal; }
        public Estado estado() { return estado; }
    }

    // Códigos con los que se cierra una conexión (4000 a 4999 son de uso propio del WebSocket).
    public static final int CERRADA_POR_OTRA_PESTANA = 4001;
    public static final int SALA_LLENA = 4003;

    public sealed interface Resultado permits Unido, Llena {
    }

    // otros: quienes ya estaban en la sala (para que el recién llegado les inicie la llamada).
    public record Unido(Participante yo, List<Participante> otros) implements Resultado {
    }

    public record Llena() implements Resultado {
    }

    private final Map<String, Map<Integer, Participante>> salas = new ConcurrentHashMap<>();
    public boolean invitadoPresente(String clave) {
        return participantes(clave).stream().anyMatch(p -> "INVITADO".equals(p.rol()));
    }


    // Mete a alguien a la sala. Si ya estaba conectado (otra pestaña), la conexión anterior se cierra y esta la reemplaza.
    // Si la sala ya tiene el máximo de personas distintas, responde Llena.
    public Resultado unir(String clave, int usuarioId, String nombre, String rol, Canal canal, int maximo) {
        Map<Integer, Participante> sala = salas.computeIfAbsent(clave, k -> new ConcurrentHashMap<>());
        synchronized (sala) {
            Participante anterior = sala.get(usuarioId);
            if (anterior == null && sala.size() >= maximo) {
                return new Llena();
            }
            Participante nuevo = new Participante(usuarioId, nombre, rol, canal);
            sala.put(usuarioId, nuevo);
            if (anterior != null) {
                anterior.canal().cerrar(CERRADA_POR_OTRA_PESTANA, "Entraste a la sala desde otra pestaña");
            }
            List<Participante> otros = new ArrayList<>(sala.values());
            otros.removeIf(p -> p.id() == usuarioId);
            return new Unido(nuevo, otros);
        }
    }

    // Saca a alguien de la sala, pero solo si su conexión sigue siendo la vigente: cuando una pestaña nueva reemplaza a la
    // anterior, el cierre de la anterior no debe sacar a la nueva. Devuelve true si salió.
    public boolean salir(String clave, int usuarioId, Canal canal) {
        Map<Integer, Participante> sala = salas.get(clave);
        if (sala == null) {
            return false;
        }
        synchronized (sala) {
            Participante p = sala.get(usuarioId);
            if (p == null || p.canal() != canal) {
                return false;
            }
            sala.remove(usuarioId);
            if (sala.isEmpty()) {
                salas.remove(clave);
            }
            return true;
        }
    }

    public Optional<Participante> participante(String clave, int usuarioId) {
        Map<Integer, Participante> sala = salas.get(clave);
        return sala == null ? Optional.empty() : Optional.ofNullable(sala.get(usuarioId));
    }

    public List<Participante> participantes(String clave) {
        Map<Integer, Participante> sala = salas.get(clave);
        return sala == null ? List.of() : new ArrayList<>(sala.values());
    }

    // Envía el mismo mensaje a todos los de la sala, menos a "exceptoId" (si no es null).
    public void difundir(String clave, String json, Integer exceptoId) {
        for (Participante p : participantes(clave)) {
            if (exceptoId == null || p.id() != exceptoId) {
                enviarSeguro(p, json);
            }
        }
    }

    // Envía un mensaje a una persona de la sala. Devuelve false si ya no está.
    public boolean enviarA(String clave, int destinoId, String json) {
        Optional<Participante> destino = participante(clave, destinoId);
        destino.ifPresent(p -> enviarSeguro(p, json));
        return destino.isPresent();
    }

    // Cambia el estado de cámara, micrófono y pantalla de alguien.
    public Optional<Estado> actualizarEstado(String clave, int usuarioId, Estado nuevo) {
        return participante(clave, usuarioId).map(p -> {
            p.estado = nuevo;
            return nuevo;
        });
    }

    // Una conexión caída no debe impedir que el mensaje llegue a los demás.
    private static void enviarSeguro(Participante p, String json) {
        try {
            p.canal().enviar(json);
        } catch (RuntimeException e) {
            // Se limpia cuando la conexión avise que se cerró.
        }
    }
}
