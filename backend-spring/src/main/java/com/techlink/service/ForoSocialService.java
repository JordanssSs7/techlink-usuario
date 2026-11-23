package com.techlink.service;

import com.techlink.dto.ForoDtos.ConsultaResumen;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Consulta;
import com.techlink.model.ForoGuardado;
import com.techlink.model.ForoReaccion;
import com.techlink.model.Respuesta;
import com.techlink.repository.ConsultaRepository;
import com.techlink.repository.ForoGuardadoRepository;
import com.techlink.repository.ForoReaccionRepository;
import com.techlink.repository.RespuestaRepository;
import com.techlink.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// Foro social: reacciones (me gusta, me encanta, me divierte, me sorprende, me molesta) a consultas y respuestas, y consultas guardadas
// para volver a verlas. Lo oculto por el administrador no se puede reaccionar ni aparece entre lo guardado.
@Service
public class ForoSocialService {

    public static final Set<String> REACCIONES = Set.of("ME_GUSTA", "ME_ENCANTA", "ME_DIVIERTE", "ME_SORPRENDE", "ME_MOLESTA");
    static final String CONSULTA = "CONSULTA";
    static final String RESPUESTA = "RESPUESTA";

    // Cuántas hay de cada reacción y cuál puso quien consulta (null si ninguna).
    public record Reacciones(Map<String, Integer> conteos, String mia) {
    }

    // Todo lo social de una consulta: sus reacciones, las de cada respuesta y si quien consulta la guardó.
    public record Social(Reacciones consulta, Map<Integer, Reacciones> respuestas, boolean guardada) {
    }

    private final ForoReaccionRepository reacciones;
    private final ForoGuardadoRepository guardados;
    private final ConsultaRepository consultas;
    private final RespuestaRepository respuestas;
    private final ForoService foro;
    private final NotificacionService notificaciones;
    private final UsuarioRepository usuarios;

    public ForoSocialService(ForoReaccionRepository reacciones, ForoGuardadoRepository guardados, ConsultaRepository consultas,
                             RespuestaRepository respuestas, ForoService foro, NotificacionService notificaciones,
                             UsuarioRepository usuarios) {
        this.notificaciones = notificaciones;
        this.usuarios = usuarios;
        this.reacciones = reacciones;
        this.guardados = guardados;
        this.consultas = consultas;
        this.respuestas = respuestas;
        this.foro = foro;
    }

    @Transactional(readOnly = true)
    public Social social(Integer usuarioId, Integer consultaId) {
        Consulta consulta = visible(consultaId);
        List<Integer> idsRespuestas = respuestas.findByConsultaId(consulta.getId()).stream().filter(r -> !r.isOculto())
                .map(Respuesta::getId).toList();
        Map<Integer, Reacciones> porRespuesta = new LinkedHashMap<>();
        for (Integer id : idsRespuestas) {
            porRespuesta.put(id, resumen(usuarioId, reacciones.findByObjetoAndIdObjetoIn(RESPUESTA, List.of(id))));
        }
        Reacciones deLaConsulta = resumen(usuarioId, reacciones.findByObjetoAndIdObjetoIn(CONSULTA, List.of(consulta.getId())));
        boolean guardada = guardados.findByIdUsuarioAndIdConsulta(usuarioId, consulta.getId()).isPresent();
        return new Social(deLaConsulta, porRespuesta, guardada);
    }

    // Pone la reacción; si ya tenía esa misma, la quita; si tenía otra, la cambia.
    @Transactional
    public Reacciones reaccionarConsulta(Integer usuarioId, Integer consultaId, String reaccion) {
        Consulta consulta = visible(consultaId);
        Reacciones resultado = reaccionar(usuarioId, CONSULTA, consulta.getId(), reaccion);
        if (reaccion.equals(resultado.mia())) {
            avisar(consulta.getAutor().getId(), usuarioId, "Reaccionaron a tu consulta", " reaccionó a tu consulta «", consulta);
        }
        return resultado;
    }

    @Transactional
    public Reacciones reaccionarRespuesta(Integer usuarioId, Integer respuestaId, String reaccion) {
        Respuesta respuesta = respuestas.findById(respuestaId).filter(r -> !r.isOculto())
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La respuesta no existe."));
        Consulta consulta = visible(respuesta.getConsulta().getId());
        Reacciones resultado = reaccionar(usuarioId, RESPUESTA, respuesta.getId(), reaccion);
        if (reaccion.equals(resultado.mia())) {
            avisar(respuesta.getAutor().getId(), usuarioId, "Reaccionaron a tu respuesta", " reaccionó a tu respuesta en «", consulta);
        }
        return resultado;
    }

    private Reacciones reaccionar(Integer usuarioId, String objeto, Integer idObjeto, String reaccion) {
        if (reaccion == null || !REACCIONES.contains(reaccion)) {
            throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Esa reacción no existe.");
        }
        Optional<ForoReaccion> actual = reacciones.findByIdUsuarioAndObjetoAndIdObjeto(usuarioId, objeto, idObjeto);
        if (actual.isPresent() && actual.get().getReaccion().equals(reaccion)) {
            reacciones.delete(actual.get());
            reacciones.flush();
        } else if (actual.isPresent()) {
            actual.get().setReaccion(reaccion);
            reacciones.saveAndFlush(actual.get());
        } else {
            reacciones.saveAndFlush(new ForoReaccion(usuarioId, objeto, idObjeto, reaccion));
        }
        return resumen(usuarioId, reacciones.findByObjetoAndIdObjetoIn(objeto, List.of(idObjeto)));
    }

    @Transactional
    public void guardar(Integer usuarioId, Integer consultaId) {
        Consulta consulta = visible(consultaId);
        if (guardados.findByIdUsuarioAndIdConsulta(usuarioId, consulta.getId()).isEmpty()) {
            guardados.save(new ForoGuardado(usuarioId, consulta.getId()));
            avisar(consulta.getAutor().getId(), usuarioId, "Guardaron tu consulta", " guardó tu consulta «", consulta);
        }
    }

    // Avisa a quien escribió (menos si es la misma persona que reacciona o guarda).
    private void avisar(Integer destino, Integer quien, String titulo, String medio, Consulta consulta) {
        String tituloConsulta = consulta.getTitulo();
        if (destino.equals(quien)) {
            return;
        }
        String nombre = usuarios.findById(quien).map(SalaMensajeService::nombreDe).orElse("Alguien");
        String corto = tituloConsulta.length() > 80 ? tituloConsulta.substring(0, 79) + "…" : tituloConsulta;
        notificaciones.crear(destino, com.techlink.model.Notificacion.Tipo.FORO, titulo, nombre + medio + corto + "».",
                "foro:" + consulta.getId());
    }

    @Transactional
    public void quitarGuardado(Integer usuarioId, Integer consultaId) {
        guardados.findByIdUsuarioAndIdConsulta(usuarioId, consultaId).ifPresent(guardados::delete);
    }

    // Las consultas que guardó, la última guardada primero (sin las que ya no existen o fueron ocultadas).
    @Transactional(readOnly = true)
    public List<ConsultaResumen> guardadas(Integer usuarioId) {
        List<Integer> ids = guardados.findByIdUsuarioOrderByIdDesc(usuarioId).stream().map(ForoGuardado::getIdConsulta).toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        Map<Integer, Consulta> porId = new LinkedHashMap<>();
        for (Consulta c : consultas.findAllById(ids)) {
            if (!c.isOculto()) {
                porId.put(c.getId(), c);
            }
        }
        return foro.resumir(ids.stream().map(porId::get).filter(java.util.Objects::nonNull).toList());
    }

    private static Reacciones resumen(Integer usuarioId, List<ForoReaccion> lista) {
        Map<String, Integer> conteos = new LinkedHashMap<>();
        String mia = null;
        for (ForoReaccion r : lista) {
            conteos.merge(r.getReaccion(), 1, Integer::sum);
            if (r.getIdUsuario().equals(usuarioId)) {
                mia = r.getReaccion();
            }
        }
        return new Reacciones(conteos, mia);
    }

    private Consulta visible(Integer consultaId) {
        return consultas.findByIdAndOcultoFalse(consultaId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "La consulta no existe."));
    }
}
