package com.techlink.service;

import com.techlink.exception.ReglaNegocioException;
import com.techlink.model.Amistad;
import com.techlink.model.Notificacion;
import com.techlink.model.Rol;
import com.techlink.model.Usuario;
import com.techlink.repository.AmistadRepository;
import com.techlink.repository.MentorRepository;
import com.techlink.repository.UsuarioFotoRepository;
import com.techlink.repository.UsuarioPortadaRepository;
import com.techlink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Seguidores: seguir sin aceptación, avisos y qué ve cada quien del perfil del otro.
class AmistadServiceTest {

    private static final int YO = 1;
    private static final int OTRO = 2;

    private AmistadRepository relaciones;
    private NotificacionService notificaciones;
    private AmistadService servicio;

    private static Usuario usuario(int id, String nombres, String rol) {
        Usuario u = PruebaSesiones.usuario(id, nombres, "Prueba", "u" + id + "@tecsup.edu.pe");
        Rol r = new Rol();
        r.setNombre(rol);
        u.setRol(r);
        u.setDescripcion("Descripción de " + nombres);
        u.getIntereses().addAll(Set.of("Java"));
        return u;
    }

    @BeforeEach
    void preparar() {
        relaciones = Mockito.mock(AmistadRepository.class);
        notificaciones = Mockito.mock(NotificacionService.class);
        UsuarioRepository usuarios = Mockito.mock(UsuarioRepository.class);
        when(usuarios.findById(YO)).thenReturn(Optional.of(usuario(YO, "Ana", "ESTUDIANTE")));
        when(usuarios.findById(OTRO)).thenReturn(Optional.of(usuario(OTRO, "Beto", "MENTOR")));
        when(usuarios.findById(9)).thenReturn(Optional.of(usuario(9, "Admin", "ADMIN")));
        when(relaciones.sigue(any(), any())).thenReturn(Optional.empty());
        servicio = new AmistadService(relaciones, usuarios, Mockito.mock(UsuarioFotoRepository.class),
                Mockito.mock(UsuarioPortadaRepository.class), notificaciones, Mockito.mock(MentorRepository.class));
    }

    @Test
    void seguirGuardaElSeguimientoYAvisaALaOtraPersona() {
        servicio.seguir(YO, OTRO);

        verify(relaciones).save(any(Amistad.class));
        verify(notificaciones).crear(eq(OTRO), eq(Notificacion.Tipo.AMISTAD), any(), any(), eq("usuario:" + YO));
    }

    @Test
    void noPuedesSeguirteATiMismoNiAUnAdmin() {
        assertThrows(ReglaNegocioException.class, () -> servicio.seguir(YO, YO));
        assertThrows(ReglaNegocioException.class, () -> servicio.seguir(YO, 9));
        verify(relaciones, never()).save(any());
    }

    @Test
    void seguirDosVecesNoDuplicaNiVuelveAAvisar() {
        when(relaciones.sigue(YO, OTRO)).thenReturn(Optional.of(new Amistad(YO, OTRO)));

        servicio.seguir(YO, OTRO);

        verify(relaciones, never()).save(any());
        verify(notificaciones, never()).crear(any(), any(), any(), any(), any());
    }

    @Test
    void dejarDeSeguirBorraLaRelacion() {
        Amistad a = new Amistad(YO, OTRO);
        when(relaciones.sigue(YO, OTRO)).thenReturn(Optional.of(a));

        servicio.dejarDeSeguir(YO, OTRO);

        verify(relaciones).delete(a);
    }

    @Test
    void elPerfilEsVisibleParaTodosYDiceSiLoSigo() {
        assertEquals("Descripción de Beto", servicio.perfil(YO, OTRO).descripcion());
        assertEquals(Set.of("Java"), Set.copyOf(servicio.perfil(YO, OTRO).intereses()));
        assertFalse(servicio.perfil(YO, OTRO).siguiendo());

        when(relaciones.sigue(YO, OTRO)).thenReturn(Optional.of(new Amistad(YO, OTRO)));

        assertTrue(servicio.perfil(YO, OTRO).siguiendo());
    }

    @Test
    void elPerfilDiceSiMeSigueYCuantosSeguidoresTiene() {
        when(relaciones.sigue(OTRO, YO)).thenReturn(Optional.of(new Amistad(OTRO, YO)));
        when(relaciones.contarSeguidores(OTRO)).thenReturn(7L);

        assertTrue(servicio.perfil(YO, OTRO).meSigue());
        assertEquals(7L, servicio.perfil(YO, OTRO).seguidores());
    }

    @Test
    void elPropioPerfilSiempreSeVeCompleto() {
        assertEquals("Descripción de Ana", servicio.perfil(YO, YO).descripcion());
    }
}
