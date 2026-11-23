package com.techlink.service;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

// Tiempo real general: quién tiene la sesión abierta ahora mismo y por dónde avisarle de cosas nuevas (avisos, cambios de foto).
// Una persona puede tener varias pestañas abiertas: está "en línea" mientras quede al menos una. Está en memoria: al reiniciar el
// servidor se vacía y los navegadores se reconectan solos.
@Component
public class EventosRegistro {

    private final Map<Integer, List<SalaRegistro.Canal>> conectados = new ConcurrentHashMap<>();

    // Registra una conexión. Devuelve true si es la primera de esa persona (acaba de ponerse en línea).
    public boolean conectar(int usuarioId, SalaRegistro.Canal canal) {
        boolean[] primera = {false};
        conectados.compute(usuarioId, (id, lista) -> {
            if (lista == null) {
                lista = new CopyOnWriteArrayList<>();
                primera[0] = true;
            }
            lista.add(canal);
            return lista;
        });
        return primera[0];
    }

    // Quita una conexión. Devuelve true si era la última de esa persona (acaba de salir de línea).
    public boolean desconectar(int usuarioId, SalaRegistro.Canal canal) {
        boolean[] ultima = {false};
        conectados.computeIfPresent(usuarioId, (id, lista) -> {
            lista.remove(canal);
            if (lista.isEmpty()) {
                ultima[0] = true;
                return null;
            }
            return lista;
        });
        return ultima[0];
    }

    public Set<Integer> enLinea() {
        return new HashSet<>(conectados.keySet());
    }

    public boolean estaEnLinea(int usuarioId) {
        return conectados.containsKey(usuarioId);
    }

    public void enviarA(int usuarioId, String json) {
        for (SalaRegistro.Canal canal : conectados.getOrDefault(usuarioId, List.of())) {
            enviarSeguro(canal, json);
        }
    }

    public void difundir(String json) {
        for (List<SalaRegistro.Canal> canales : new ArrayList<>(conectados.values())) {
            for (SalaRegistro.Canal canal : canales) {
                enviarSeguro(canal, json);
            }
        }
    }

    private static void enviarSeguro(SalaRegistro.Canal canal, String json) {
        try {
            canal.enviar(json);
        } catch (RuntimeException e) {
            // Conexión caída: se limpia cuando el servidor note el cierre.
        }
    }
}
