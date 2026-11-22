package com.techlink.service;

import com.techlink.dto.CriteriosEmparejamientoResponse;
import com.techlink.dto.PesosEmparejamientoRequest;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.CriterioEmparejamiento;
import com.techlink.repository.CriterioEmparejamientoRepository;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

// HU-61: los pesos con los que el emparejamiento ordena a los mentores. El administrador los ajusta desde el panel; el agente
// de recomendación (HU-04) los lee cada vez que recomienda. Si faltan en la base se usan los iniciales (70, 0 y 30), que son
// el cálculo que existía antes de que fueran ajustables.
@Service
public class CriteriosEmparejamientoService {

    public static final String CURSOS = "CURSOS";
    public static final String CALIFICACION = "CALIFICACION";
    public static final String DISPONIBILIDAD = "DISPONIBILIDAD";

    // Los tres pesos. Cada uno va de 0 a 100 y no pueden ser los tres cero. Se usan en proporción: 70/0/30 = 7/0/3.
    public record Pesos(int cursos, int calificacion, int disponibilidad) {

        public static final Pesos PREDETERMINADOS = new Pesos(70, 0, 30);

        public int suma() {
            return cursos + calificacion + disponibilidad;
        }

        // Fracción (0 a 1) que representa cada peso del total.
        public double fraccionCursos() { return (double) cursos / suma(); }
        public double fraccionCalificacion() { return (double) calificacion / suma(); }
        public double fraccionDisponibilidad() { return (double) disponibilidad / suma(); }

        public void validar() {
            for (int peso : new int[]{cursos, calificacion, disponibilidad}) {
                if (peso < 0 || peso > 100) {
                    throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Cada peso debe estar entre 0 y 100.");
                }
            }
            if (suma() <= 0) {
                throw new ReglaNegocioException(HttpStatus.BAD_REQUEST, "Al menos un criterio debe tener un peso mayor que 0.");
            }
        }

        public static Pesos de(PesosEmparejamientoRequest r) {
            if (r == null || r.cursos() == null || r.calificacion() == null || r.disponibilidad() == null) {
                throw new ReglaNegocioException(HttpStatus.BAD_REQUEST,
                        "Indica el peso de los tres criterios: cursos, calificación y disponibilidad.");
            }
            Pesos pesos = new Pesos(r.cursos(), r.calificacion(), r.disponibilidad());
            pesos.validar();
            return pesos;
        }

        public PesosEmparejamientoRequest aDto() {
            return new PesosEmparejamientoRequest(cursos, calificacion, disponibilidad);
        }
    }

    private final CriterioEmparejamientoRepository repositorio;
    private final JdbcTemplate jdbc;

    public CriteriosEmparejamientoService(CriterioEmparejamientoRepository repositorio, JdbcTemplate jdbc) {
        this.repositorio = repositorio;
        this.jdbc = jdbc;
    }

    // Los pesos vigentes. Un criterio que falte o tenga un valor fuera de rango vuelve a su valor inicial.
    @Transactional(readOnly = true)
    public Pesos actuales() {
        Map<String, Integer> guardados = new HashMap<>();
        for (CriterioEmparejamiento c : repositorio.findAll()) {
            guardados.put(c.getClave(), c.getPeso());
        }
        Pesos base = Pesos.PREDETERMINADOS;
        Pesos pesos = new Pesos(
                guardados.getOrDefault(CURSOS, base.cursos()),
                guardados.getOrDefault(CALIFICACION, base.calificacion()),
                guardados.getOrDefault(DISPONIBILIDAD, base.disponibilidad()));
        try {
            pesos.validar();
            return pesos;
        } catch (ReglaNegocioException e) {
            return base;
        }
    }

    @Transactional(readOnly = true)
    public CriteriosEmparejamientoResponse consultar() {
        LocalDateTime actualizado = repositorio.findAll().stream()
                .map(CriterioEmparejamiento::getFechaActualizacion)
                .filter(f -> f != null)
                .max(LocalDateTime::compareTo)
                .orElse(null);
        return new CriteriosEmparejamientoResponse(actuales().aDto(), Pesos.PREDETERMINADOS.aDto(), actualizado);
    }

    // Guarda los pesos nuevos y lo anota en el historial de acciones del administrador (HU-59).
    @Transactional
    public CriteriosEmparejamientoResponse guardar(Integer idAdmin, PesosEmparejamientoRequest solicitud) {
        Pesos nuevos = Pesos.de(solicitud);
        Pesos antes = actuales();
        guardarUno(CURSOS, nuevos.cursos(), idAdmin);
        guardarUno(CALIFICACION, nuevos.calificacion(), idAdmin);
        guardarUno(DISPONIBILIDAD, nuevos.disponibilidad(), idAdmin);
        if (!antes.equals(nuevos)) {
            jdbc.update("INSERT INTO acciones_admin (id_admin, tipo, entidad, id_entidad, detalle) VALUES (?, ?, ?, NULL, ?)",
                    idAdmin, "CRITERIOS_IA_ACTUALIZADOS", "emparejamiento",
                    "Cambió los pesos del emparejamiento (cursos / calificación / disponibilidad) de "
                            + antes.cursos() + "/" + antes.calificacion() + "/" + antes.disponibilidad() + " a "
                            + nuevos.cursos() + "/" + nuevos.calificacion() + "/" + nuevos.disponibilidad());
        }
        return consultar();
    }

    private void guardarUno(String clave, int peso, Integer idAdmin) {
        CriterioEmparejamiento criterio = repositorio.findById(clave).orElseGet(() -> new CriterioEmparejamiento(clave, peso));
        criterio.setPeso(peso);
        criterio.setIdAdmin(idAdmin);
        repositorio.save(criterio);
    }
}
