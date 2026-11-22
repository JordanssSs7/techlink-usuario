package com.techlink.service;

import com.techlink.dto.PerfilRequest;
import com.techlink.dto.PerfilResponse;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Estudiante;
import com.techlink.model.Usuario;
import com.techlink.model.UsuarioFoto;
import com.techlink.repository.EstudianteRepository;
import com.techlink.repository.UsuarioFotoRepository;
import com.techlink.repository.UsuarioRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

// HU-13: el usuario edita su perfil (foto, descripción, ciclo e intereses). Se rechazan los campos obligatorios vacíos
// con un mensaje por campo. La foto es JPG o PNG de hasta 2 MB.
@Service
public class PerfilService {

    static final int MAX_DESCRIPCION = 300;
    static final int MAX_INTERESES = 8;
    static final int MIN_INTERES = 2;
    static final int MAX_INTERES = 50;
    static final long MAX_FOTO_BYTES = 2L * 1024 * 1024;

    private static final String ROL_ESTUDIANTE = "ESTUDIANTE";

    private final UsuarioRepository usuarioRepository;
    private final EstudianteRepository estudianteRepository;
    private final UsuarioFotoRepository fotoRepository;

    public PerfilService(UsuarioRepository usuarioRepository,
                         EstudianteRepository estudianteRepository,
                         UsuarioFotoRepository fotoRepository) {
        this.usuarioRepository = usuarioRepository;
        this.estudianteRepository = estudianteRepository;
        this.fotoRepository = fotoRepository;
    }

    @Transactional(readOnly = true)
    public PerfilResponse obtener(Integer usuarioId) {
        return aRespuesta(buscarUsuario(usuarioId));
    }

    @Transactional
    public PerfilResponse actualizar(Integer usuarioId, PerfilRequest peticion) {
        Usuario usuario = buscarUsuario(usuarioId);
        boolean esEstudiante = ROL_ESTUDIANTE.equals(usuario.getRol().getNombre());
        Map<String, String> errores = new LinkedHashMap<>();

        String descripcion = peticion.descripcion() == null ? "" : peticion.descripcion().trim();
        if (descripcion.isEmpty()) {
            errores.put("descripcion", "La descripción es obligatoria.");
        } else if (descripcion.length() > MAX_DESCRIPCION) {
            errores.put("descripcion", "La descripción no puede superar los " + MAX_DESCRIPCION + " caracteres.");
        }

        Integer ciclo = peticion.ciclo();
        if (esEstudiante && (ciclo == null || ciclo < 1 || ciclo > 6)) {
            errores.put("ciclo", "Elige tu ciclo (de 1 a 6).");
        }

        Set<String> intereses = limpiarIntereses(peticion.intereses(), errores);

        if (!errores.isEmpty()) {
            throw new DatosInvalidosException(errores);
        }

        usuario.setDescripcion(descripcion);
        usuario.getIntereses().clear();
        usuario.getIntereses().addAll(intereses);
        usuarioRepository.save(usuario);

        if (esEstudiante) {
            Estudiante estudiante = estudianteRepository.findByUsuarioId(usuarioId)
                    .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "No tienes perfil de estudiante."));
            estudiante.setCiclo(ciclo);
            estudianteRepository.save(estudiante);
        }
        return aRespuesta(usuario);
    }

    // JPG o PNG de hasta 2 MB. Reemplaza la foto anterior si había una.
    @Transactional
    public PerfilResponse guardarFoto(Integer usuarioId, MultipartFile archivo) {
        Usuario usuario = buscarUsuario(usuarioId);
        byte[] bytes = ArchivoSubido.leerYValidar(archivo, "foto", MAX_FOTO_BYTES,
                Set.of(ArchivoSubido.PNG, ArchivoSubido.JPG), "una imagen JPG o PNG");

        UsuarioFoto foto = new UsuarioFoto();
        foto.setIdUsuario(usuario.getId());
        foto.setTipoContenido(ArchivoSubido.detectarTipo(bytes));
        foto.setContenido(bytes);
        foto.setFechaActualizacion(LocalDateTime.now());
        fotoRepository.saveAndFlush(foto);
        return aRespuesta(usuario);
    }

    @Transactional(readOnly = true)
    public Optional<UsuarioFoto> foto(Integer usuarioId) {
        return fotoRepository.findById(usuarioId);
    }

    private Set<String> limpiarIntereses(List<String> crudos, Map<String, String> errores) {
        Set<String> limpios = new LinkedHashSet<>();
        Set<String> vistos = new LinkedHashSet<>();
        if (crudos != null) {
            for (String crudo : crudos) {
                String interes = crudo == null ? "" : crudo.trim().replaceAll("\\s+", " ");
                if (interes.isEmpty()) {
                    continue;
                }
                if (interes.length() < MIN_INTERES || interes.length() > MAX_INTERES) {
                    errores.put("intereses", "Cada interés debe tener entre " + MIN_INTERES + " y " + MAX_INTERES + " caracteres.");
                    continue;
                }
                if (vistos.add(interes.toLowerCase(Locale.ROOT))) {
                    limpios.add(interes);
                }
            }
        }
        if (limpios.size() > MAX_INTERESES) {
            errores.put("intereses", "Puedes agregar hasta " + MAX_INTERESES + " intereses.");
        }
        return limpios;
    }

    private Usuario buscarUsuario(Integer usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ReglaNegocioException(HttpStatus.NOT_FOUND, "El usuario no existe."));
    }

    private PerfilResponse aRespuesta(Usuario usuario) {
        Integer ciclo = ROL_ESTUDIANTE.equals(usuario.getRol().getNombre())
                ? estudianteRepository.findByUsuarioId(usuario.getId()).map(Estudiante::getCiclo).orElse(null)
                : null;
        Optional<UsuarioFoto> foto = fotoRepository.findById(usuario.getId());
        List<String> intereses = new ArrayList<>(usuario.getIntereses());
        return new PerfilResponse(
                usuario.getId(), usuario.getNombres(), usuario.getApellidos(), usuario.getCorreoInstitucional(),
                usuario.getRol().getNombre(), usuario.getDescripcion(), ciclo, intereses,
                foto.isPresent(), foto.map(UsuarioFoto::getFechaActualizacion).orElse(null));
    }
}
