package com.techlink.service;

import com.techlink.dto.SalaDtos.Mensaje;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.SalaMensaje;
import com.techlink.model.Usuario;
import com.techlink.repository.SalaMensajeRepository;
import com.techlink.repository.UsuarioRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

// HU-08: los mensajes del chat de la sala virtual. Valida el texto o el fragmento de código, lo guarda para que quede el
// historial y limita el ritmo de envío para que nadie inunde la sala.
@Service
public class SalaMensajeService {

    public static final int MAX_TEXTO = 1000;
    public static final int MAX_CODIGO = 4000;
    public static final int HISTORIAL = 100;
    // Como máximo 10 mensajes cada 10 segundos por persona.
    static final int MAX_POR_VENTANA = 10;
    static final Duration VENTANA = Duration.ofSeconds(10);

    // Lenguajes que se pueden indicar en un fragmento de código; cualquier otro se guarda como "texto".
    public static final Set<String> LENGUAJES = Set.of("texto", "java", "javascript", "typescript", "python", "sql", "html",
            "css", "c", "cpp", "csharp", "php", "json", "bash", "xml", "kotlin", "go");

    private final SalaMensajeRepository repositorio;
    private final UsuarioRepository usuarioRepository;
    private final Clock reloj;
    private final Map<Integer, Deque<Instant>> envios = new ConcurrentHashMap<>();

    @Autowired
    public SalaMensajeService(SalaMensajeRepository repositorio, UsuarioRepository usuarioRepository) {
        this(repositorio, usuarioRepository, Clock.systemUTC());
    }

    // El reloj se puede cambiar solo en las pruebas automaticas.
    SalaMensajeService(SalaMensajeRepository repositorio, UsuarioRepository usuarioRepository, Clock reloj) {
        this.repositorio = repositorio;
        this.usuarioRepository = usuarioRepository;
        this.reloj = reloj;
    }

    // Guarda un mensaje de la sala. tipo: "TEXTO" o "CODIGO". 400 con "campos" si el contenido falta o es muy largo; 429 si
    // escribe demasiado rápido.
    @Transactional
    public Mensaje enviar(SalaAccesoService.Sala sala, Integer usuarioId, String nombre, String tipo, String lenguaje,
                          String contenido) {
        boolean codigo = "CODIGO".equalsIgnoreCase(tipo);
        String limpio = limpiar(contenido, codigo);
        int maximo = codigo ? MAX_CODIGO : MAX_TEXTO;
        if (limpio.isBlank()) {
            throw new DatosInvalidosException("contenido", codigo ? "Escribe o pega el código." : "Escribe un mensaje.");
        }
        if (limpio.length() > maximo) {
            throw new DatosInvalidosException("contenido", "El mensaje no puede pasar de %d caracteres.".formatted(maximo));
        }
        // Los mensajes de texto pasan por la moderación (el código no: puede llevar cualquier cosa).
        if (!codigo) {
            ModeracionTexto.exigirLimpio(limpio);
        }
        controlarRitmo(usuarioId);

        String lenguajeFinal = null;
        if (codigo) {
            String pedido = lenguaje == null ? "" : lenguaje.trim().toLowerCase(Locale.ROOT);
            lenguajeFinal = LENGUAJES.contains(pedido) ? pedido : "texto";
        }
        SalaMensaje guardado = repositorio.save(new SalaMensaje(sala.tipo().name(), sala.id(), usuarioId,
                codigo ? "CODIGO" : "TEXTO", lenguajeFinal, limpio));
        LocalDateTime fecha = guardado.getFechaCreacion() != null ? guardado.getFechaCreacion() : LocalDateTime.now(reloj);
        return new Mensaje(guardado.getId(), usuarioId, nombre, guardado.getTipo(), lenguajeFinal, limpio, fecha.toString());
    }

    // Los últimos mensajes de la sala, del más antiguo al más reciente.
    @Transactional(readOnly = true)
    public List<Mensaje> historial(SalaAccesoService.Sala sala) {
        List<SalaMensaje> recientes = repositorio.recientes(sala.tipo().name(), sala.id(), PageRequest.of(0, HISTORIAL));
        Map<Integer, String> nombres = new HashMap<>();
        usuarioRepository.findAllById(recientes.stream().map(SalaMensaje::getIdUsuario).collect(Collectors.toSet()))
                .forEach(u -> nombres.put(u.getId(), nombreDe(u)));
        List<Mensaje> lista = recientes.stream()
                .map(m -> new Mensaje(m.getId(), m.getIdUsuario(), nombres.getOrDefault(m.getIdUsuario(), "Usuario"),
                        m.getTipo(), m.getLenguaje(), m.getContenido(),
                        m.getFechaCreacion() == null ? null : m.getFechaCreacion().toString()))
                .collect(Collectors.toCollection(java.util.ArrayList::new));
        Collections.reverse(lista);
        return lista;
    }

    public static String nombreDe(Usuario u) {
        String apellidos = u.getApellidos() == null || "-".equals(u.getApellidos()) ? "" : u.getApellidos();
        return (u.getNombres() + " " + apellidos).trim();
    }

    // Quita los caracteres de control (salvo saltos de línea y tabulaciones) y unifica los saltos de línea. En el texto se
    // recortan los espacios de los extremos; en el código solo los del final, para no perder la sangría.
    static String limpiar(String contenido, boolean codigo) {
        if (contenido == null) {
            return "";
        }
        String sinControl = contenido.replace("\r\n", "\n").replace('\r', '\n').replaceAll("[\\p{Cntrl}&&[^\n\t]]", "");
        return codigo ? sinControl.stripTrailing().replaceFirst("^(\\s*\n)+", "") : sinControl.strip();
    }

    private void controlarRitmo(Integer usuarioId) {
        Instant ahora = reloj.instant();
        Deque<Instant> recientes = envios.computeIfAbsent(usuarioId, k -> new ArrayDeque<>());
        synchronized (recientes) {
            while (!recientes.isEmpty() && recientes.peekFirst().isBefore(ahora.minus(VENTANA))) {
                recientes.pollFirst();
            }
            if (recientes.size() >= MAX_POR_VENTANA) {
                throw new ReglaNegocioException(HttpStatus.TOO_MANY_REQUESTS, "Estás escribiendo muy rápido. Espera unos segundos.");
            }
            recientes.addLast(ahora);
        }
    }
}
