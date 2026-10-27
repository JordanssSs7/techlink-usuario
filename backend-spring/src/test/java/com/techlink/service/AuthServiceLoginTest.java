package com.techlink.service;

import com.techlink.dto.LoginRequest;
import com.techlink.dto.LoginResponse;
import com.techlink.exception.CredencialesInvalidasException;
import com.techlink.exception.CuentaBloqueadaException;
import com.techlink.exception.CuentaInactivaException;
import com.techlink.model.EstadoUsuario;
import com.techlink.model.Rol;
import com.techlink.model.Usuario;
import com.techlink.repository.EstudianteRepository;
import com.techlink.repository.RolRepository;
import com.techlink.repository.UsuarioRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Pruebas unitarias del login (HU-02): no necesitan base de datos, los repositorios son simulados.
class AuthServiceLoginTest {

    private static final String CORREO = "ana@tecsup.edu.pe";
    private static final String PASSWORD = "clave1234";

    private UsuarioRepository usuarioRepository;
    private JwtService jwtService;
    private AuthService authService;
    private Usuario usuario;

    @BeforeEach
    void preparar() {
        usuarioRepository = Mockito.mock(UsuarioRepository.class);
        jwtService = Mockito.mock(JwtService.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        authService = new AuthService(usuarioRepository, Mockito.mock(EstudianteRepository.class),
                Mockito.mock(RolRepository.class), encoder, jwtService, Mockito.mock(GoogleTokenVerifier.class));

        Rol rol = new Rol();
        rol.setNombre("ESTUDIANTE");
        usuario = new Usuario();
        usuario.setId(1);
        usuario.setNombres("Ana");
        usuario.setApellidos("Perez");
        usuario.setCorreoInstitucional(CORREO);
        usuario.setContrasenaHash(encoder.encode(PASSWORD));
        usuario.setRol(rol);

        when(usuarioRepository.findByCorreoInstitucional(CORREO)).thenReturn(Optional.of(usuario));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generarToken(any(Usuario.class))).thenReturn("token-de-prueba");
        when(jwtService.getExpiracionSegundos()).thenReturn(3600L);
    }

    @Test
    void credencialesCorrectasDevuelvenTokenYRol() {
        LoginResponse respuesta = authService.login(new LoginRequest(CORREO, PASSWORD));

        assertEquals("token-de-prueba", respuesta.token());
        assertEquals("Bearer", respuesta.tipo());
        assertEquals("ESTUDIANTE", respuesta.usuario().rol());
    }

    @Test
    void correoRegistradoEnMinusculasExigeEscribirloEnMinusculas() {
        // MySQL compara sin distinguir mayusculas, por eso el repositorio simulado SI devuelve al usuario.
        when(usuarioRepository.findByCorreoInstitucional("ANA@tecsup.edu.pe")).thenReturn(Optional.of(usuario));

        assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequest("ANA@tecsup.edu.pe", PASSWORD)));
        assertEquals(0, usuario.getIntentosFallidos());
    }

    @Test
    void correoRegistradoConMayusculaExigeEscribirloConMayuscula() {
        usuario.setCorreoInstitucional("Ana@tecsup.edu.pe");
        when(usuarioRepository.findByCorreoInstitucional("Ana@tecsup.edu.pe")).thenReturn(Optional.of(usuario));
        when(usuarioRepository.findByCorreoInstitucional("ana@tecsup.edu.pe")).thenReturn(Optional.of(usuario));

        // Idéntico al registrado: entra.
        assertDoesNotThrow(() -> authService.login(new LoginRequest("Ana@tecsup.edu.pe", PASSWORD)));
        // Distinto en mayúsculas: no entra y no cuenta como intento fallido.
        assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequest("ana@tecsup.edu.pe", PASSWORD)));
        assertEquals(0, usuario.getIntentosFallidos());
    }

    @Test
    void espaciosAlrededorDelCorreoSiSeIgnoran() {
        assertDoesNotThrow(() -> authService.login(new LoginRequest("  " + CORREO + " ", PASSWORD)));
    }

    @Test
    void contrasenaIncorrectaSumaUnIntento() {
        assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequest(CORREO, "incorrecta1")));

        assertEquals(1, usuario.getIntentosFallidos());
        assertNull(usuario.getBloqueadoHasta());
    }

    @Test
    void correoInexistenteDaElMismoErrorQueContrasenaMala() {
        when(usuarioRepository.findByCorreoInstitucional("nadie@tecsup.edu.pe")).thenReturn(Optional.empty());

        assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequest("nadie@tecsup.edu.pe", PASSWORD)));
    }

    @Test
    void tercerIntentoFallidoBloqueaLaCuentaQuinceMinutos() {
        LoginRequest mala = new LoginRequest(CORREO, "incorrecta1");
        assertThrows(CredencialesInvalidasException.class, () -> authService.login(mala));
        assertThrows(CredencialesInvalidasException.class, () -> authService.login(mala));

        CuentaBloqueadaException bloqueo = assertThrows(CuentaBloqueadaException.class, () -> authService.login(mala));

        assertTrue(bloqueo.getMinutosRestantes() >= 14 && bloqueo.getMinutosRestantes() <= 15);
        assertNotNull(usuario.getBloqueadoHasta());
    }

    @Test
    void cuentaBloqueadaRechazaIncluyendoLaContrasenaCorrecta() {
        usuario.setBloqueadoHasta(LocalDateTime.now().plusMinutes(10));

        assertThrows(CuentaBloqueadaException.class,
                () -> authService.login(new LoginRequest(CORREO, PASSWORD)));
    }

    @Test
    void bloqueoVencidoPermiteEntrarYLimpiaElContador() {
        usuario.setBloqueadoHasta(LocalDateTime.now().minusMinutes(1));
        usuario.setIntentosFallidos(2);

        LoginResponse respuesta = authService.login(new LoginRequest(CORREO, PASSWORD));

        assertNotNull(respuesta.token());
        assertNull(usuario.getBloqueadoHasta());
        assertEquals(0, usuario.getIntentosFallidos());
    }

    @Test
    void loginCorrectoReiniciaLosIntentosFallidos() {
        usuario.setIntentosFallidos(2);

        authService.login(new LoginRequest(CORREO, PASSWORD));

        assertEquals(0, usuario.getIntentosFallidos());
    }

    @Test
    void cuentaInactivaNoPuedeIniciarSesion() {
        usuario.setEstado(EstadoUsuario.INACTIVO);

        assertThrows(CuentaInactivaException.class,
                () -> authService.login(new LoginRequest(CORREO, PASSWORD)));
    }
}
