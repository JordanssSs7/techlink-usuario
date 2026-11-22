package com.techlink.service;

import com.techlink.dto.PerfilRequest;
import com.techlink.dto.PerfilResponse;
import com.techlink.exception.DatosInvalidosException;
import com.techlink.model.Estudiante;
import com.techlink.model.Rol;
import com.techlink.model.Usuario;
import com.techlink.model.UsuarioFoto;
import com.techlink.repository.EstudianteRepository;
import com.techlink.repository.UsuarioFotoRepository;
import com.techlink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas de HU-13: editar el perfil (descripción, ciclo, intereses) y la foto.
class PerfilServiceTest {

    private UsuarioRepository usuarioRepository;
    private EstudianteRepository estudianteRepository;
    private UsuarioFotoRepository fotoRepository;
    private PerfilService servicio;
    private Usuario usuario;
    private Estudiante estudiante;

    @BeforeEach
    void preparar() {
        usuarioRepository = Mockito.mock(UsuarioRepository.class);
        estudianteRepository = Mockito.mock(EstudianteRepository.class);
        fotoRepository = Mockito.mock(UsuarioFotoRepository.class);
        servicio = new PerfilService(usuarioRepository, estudianteRepository, fotoRepository);

        usuario = PruebaSesiones.usuario(1, "Ana", "Quispe", "ana@tecsup.edu.pe");
        Rol rol = new Rol();
        rol.setNombre("ESTUDIANTE");
        usuario.setRol(rol);
        estudiante = new Estudiante();
        estudiante.setUsuario(usuario);
        estudiante.setCiclo(2);

        when(usuarioRepository.findById(1)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(estudianteRepository.findByUsuarioId(1)).thenReturn(Optional.of(estudiante));
        when(estudianteRepository.save(any(Estudiante.class))).thenAnswer(i -> i.getArgument(0));
        when(fotoRepository.findById(1)).thenReturn(Optional.empty());
    }

    private static DatosInvalidosException invalido(Runnable accion) {
        return assertThrows(DatosInvalidosException.class, accion::run);
    }

    @Test
    void guardaDescripcionCicloEInteresesYDevuelveElPerfilActualizado() {
        PerfilResponse r = servicio.actualizar(1, new PerfilRequest("  Me gusta el backend  ", 4,
                List.of("Java", " java ", "Bases de datos", "")));

        assertEquals("Me gusta el backend", r.descripcion());
        assertEquals(4, r.ciclo());
        assertEquals(List.of("Java", "Bases de datos"), r.intereses());
        assertEquals(4, estudiante.getCiclo());
        verify(usuarioRepository).save(usuario);
    }

    @Test
    void reemplazaLosInteresesAnteriores() {
        usuario.getIntereses().add("Viejo");

        servicio.actualizar(1, new PerfilRequest("Hola", 3, List.of("Nuevo")));

        assertEquals(List.of("Nuevo"), List.copyOf(usuario.getIntereses()));
    }

    @Test
    void laDescripcionEsObligatoria() {
        for (String vacia : new String[]{null, "", "   "}) {
            DatosInvalidosException e = invalido(() -> servicio.actualizar(1, new PerfilRequest(vacia, 3, List.of())));
            assertEquals("La descripción es obligatoria.", e.getCampos().get("descripcion"));
        }
        verify(usuarioRepository, never()).save(any());
    }

    @Test
    void laDescripcionNoPuedeSuperar300Caracteres() {
        assertDoesNotThrow(() -> servicio.actualizar(1, new PerfilRequest("a".repeat(300), 3, List.of())));

        DatosInvalidosException e = invalido(() -> servicio.actualizar(1, new PerfilRequest("a".repeat(301), 3, List.of())));

        assertTrue(e.getCampos().get("descripcion").contains("300"));
    }

    @Test
    void unEstudianteDebeElegirUnCicloDe1a6() {
        for (Integer ciclo : new Integer[]{null, 0, 7}) {
            assertTrue(invalido(() -> servicio.actualizar(1, new PerfilRequest("Hola", ciclo, List.of())))
                    .getCampos().containsKey("ciclo"));
        }
    }

    @Test
    void unMentorNoNecesitaCiclo() {
        Rol rol = new Rol();
        rol.setNombre("MENTOR");
        usuario.setRol(rol);

        PerfilResponse r = servicio.actualizar(1, new PerfilRequest("Mentor de BD", null, List.of()));

        assertNull(r.ciclo());
        verify(estudianteRepository, never()).save(any());
    }

    @Test
    void validaLosInteresesYTodosLosErroresJuntos() {
        DatosInvalidosException e = invalido(() -> servicio.actualizar(1, new PerfilRequest(" ", 9,
                List.of("a", "b", "c", "d", "e", "f", "g", "h", "ii"))));

        assertEquals(3, e.getCampos().size());
        assertTrue(e.getCampos().containsKey("descripcion"));
        assertTrue(e.getCampos().containsKey("ciclo"));
        assertTrue(e.getCampos().get("intereses").contains("entre 2 y 50"));
    }

    @Test
    void noAceptaMasDe8Intereses() {
        DatosInvalidosException e = invalido(() -> servicio.actualizar(1, new PerfilRequest("Hola", 3,
                List.of("uno", "dos", "tres", "cuatro", "cinco", "seis", "siete", "ocho", "nueve"))));

        assertTrue(e.getCampos().get("intereses").contains("8"));
    }

    // ---------- foto ----------

    @Test
    void guardaUnaFotoJpgOPng() {
        PerfilResponse r = servicio.guardarFoto(1,
                new MockMultipartFile("foto", "yo.jpg", "image/jpeg", ArchivoSubidoTest.JPG));

        ArgumentCaptor<UsuarioFoto> foto = ArgumentCaptor.forClass(UsuarioFoto.class);
        verify(fotoRepository).saveAndFlush(foto.capture());
        assertEquals(1, foto.getValue().getIdUsuario());
        assertEquals("image/jpeg", foto.getValue().getTipoContenido());
        assertNotNull(r);

        servicio.guardarFoto(1, new MockMultipartFile("foto", "yo.png", "image/png", ArchivoSubidoTest.PNG));
        verify(fotoRepository, Mockito.times(2)).saveAndFlush(any(UsuarioFoto.class));
    }

    @Test
    void rechazaUnaFotoQueNoEsImagen() {
        assertTrue(invalido(() -> servicio.guardarFoto(1,
                new MockMultipartFile("foto", "yo.jpg", "image/jpeg", ArchivoSubidoTest.PDF))).getCampos().containsKey("foto"));
        assertTrue(invalido(() -> servicio.guardarFoto(1,
                new MockMultipartFile("foto", "yo.png", "image/png", ArchivoSubidoTest.TEXTO))).getCampos().containsKey("foto"));
        verify(fotoRepository, never()).saveAndFlush(any());
    }

    @Test
    void rechazaUnaFotoDeMasDe2Mb() {
        byte[] grande = new byte[2 * 1024 * 1024 + 1];
        System.arraycopy(ArchivoSubidoTest.JPG, 0, grande, 0, ArchivoSubidoTest.JPG.length);

        DatosInvalidosException e = invalido(() -> servicio.guardarFoto(1,
                new MockMultipartFile("foto", "yo.jpg", "image/jpeg", grande)));

        assertTrue(e.getCampos().get("foto").contains("2 MB"));
    }

    @Test
    void elPerfilIndicaSiTieneFoto() {
        assertFalse(servicio.obtener(1).tieneFoto());

        when(fotoRepository.findById(1)).thenReturn(Optional.of(new UsuarioFoto()));

        assertTrue(servicio.obtener(1).tieneFoto());
    }
}
