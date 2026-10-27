package com.techlink.service;

import com.techlink.dto.RegisterRequest;
import com.techlink.exception.CorreoDuplicadoException;
import com.techlink.exception.DominioInvalidoException;
import com.techlink.model.Rol;
import com.techlink.model.Usuario;
import com.techlink.repository.EstudianteRepository;
import com.techlink.repository.RolRepository;
import com.techlink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas unitarias de las reglas del correo en el registro (HU-01): no necesitan base de datos.
class AuthServiceRegisterTest {

    private UsuarioRepository usuarioRepository;
    private AuthService authService;

    @BeforeEach
    void preparar() {
        usuarioRepository = Mockito.mock(UsuarioRepository.class);
        RolRepository rolRepository = Mockito.mock(RolRepository.class);
        EstudianteRepository estudianteRepository = Mockito.mock(EstudianteRepository.class);
        authService = new AuthService(usuarioRepository, estudianteRepository, rolRepository,
                new BCryptPasswordEncoder(), Mockito.mock(JwtService.class));

        Rol rol = new Rol();
        rol.setNombre("ESTUDIANTE");
        when(rolRepository.findByNombre("ESTUDIANTE")).thenReturn(Optional.of(rol));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
    }

    private RegisterRequest conCorreo(String correo) {
        return new RegisterRequest("Ana", "Perez", correo, "clave1234", 1, null);
    }

    @Test
    void elCorreoSeGuardaTalCualConSusMayusculas() {
        authService.register(conCorreo("Ana@tecsup.edu.pe"));

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertEquals("Ana@tecsup.edu.pe", guardado.getValue().getCorreoInstitucional());
    }

    @Test
    void correoEnMinusculasSeGuardaIgual() {
        authService.register(conCorreo("ana@tecsup.edu.pe"));

        ArgumentCaptor<Usuario> guardado = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(guardado.capture());
        assertEquals("ana@tecsup.edu.pe", guardado.getValue().getCorreoInstitucional());
    }

    @Test
    void correoDeOtroDominioEsRechazado() {
        assertThrows(DominioInvalidoException.class, () -> authService.register(conCorreo("ana@gmail.com")));
    }

    @Test
    void dominioFalsoQueContieneElInstitucionalEsRechazado() {
        assertThrows(DominioInvalidoException.class,
                () -> authService.register(conCorreo("ana@tecsup.edu.pe.evil.com")));
    }

    @Test
    void correoYaRegistradoEsRechazado() {
        when(usuarioRepository.existsByCorreoInstitucional("ana@tecsup.edu.pe")).thenReturn(true);

        assertThrows(CorreoDuplicadoException.class, () -> authService.register(conCorreo("ana@tecsup.edu.pe")));
    }
}
