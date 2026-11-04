package com.techlink.service;

import com.techlink.dto.GoogleLoginRequest;
import com.techlink.dto.LoginRequest;
import com.techlink.dto.LoginResponse;
import com.techlink.exception.CredencialesInvalidasException;
import com.techlink.exception.CuentaInactivaException;
import com.techlink.exception.UsuarioNoRegistradoException;
import com.techlink.model.EstadoUsuario;
import com.techlink.model.Estudiante;
import com.techlink.model.ProveedorUsuario;
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

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

// Pruebas unitarias del login con Google y del primer ingreso (HU-02): Google y los repositorios son simulados.
class AuthServiceGoogleTest {

    private static final String CORREO = "ana@tecsup.edu.pe";
    private static final String GOOGLE_ID = "1234567890";

    private UsuarioRepository usuarioRepository;
    private EstudianteRepository estudianteRepository;
    private AuthService authService;
    private Rol rol;
    private Usuario existente;

    @BeforeEach
    void preparar() {
        usuarioRepository = Mockito.mock(UsuarioRepository.class);
        estudianteRepository = Mockito.mock(EstudianteRepository.class);
        GoogleTokenVerifier googleTokenVerifier = Mockito.mock(GoogleTokenVerifier.class);
        RolRepository rolRepository = Mockito.mock(RolRepository.class);
        JwtService jwtService = Mockito.mock(JwtService.class);
        authService = new AuthService(usuarioRepository, estudianteRepository, rolRepository,
                new BCryptPasswordEncoder(), jwtService, googleTokenVerifier);

        rol = new Rol();
        rol.setNombre("ESTUDIANTE");
        existente = new Usuario();
        existente.setId(7);
        existente.setNombres("Ana");
        existente.setApellidos("Perez");
        existente.setCorreoInstitucional(CORREO);
        existente.setContrasenaHash("hash");
        existente.setRol(rol);

        when(rolRepository.findByNombre("ESTUDIANTE")).thenReturn(Optional.of(rol));
        when(googleTokenVerifier.verificar("codigo-google", null))
                .thenReturn(new GoogleTokenVerifier.GoogleUsuario(GOOGLE_ID, CORREO, "Ana", "Perez"));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generarToken(any(Usuario.class))).thenReturn("nuestro-jwt");
        when(jwtService.getExpiracionSegundos()).thenReturn(3600L);
    }

    @Test
    void primerIngresoCreaLaCuentaConLosDatosDeGoogleYCicloVacio() {
        when(usuarioRepository.findByCorreoInstitucional(CORREO)).thenReturn(Optional.empty());

        LoginResponse respuesta = authService.loginConGoogle(new GoogleLoginRequest("codigo-google", null));

        assertEquals("nuestro-jwt", respuesta.token());
        assertEquals("ESTUDIANTE", respuesta.usuario().rol());
        assertNull(respuesta.usuario().ciclo());

        ArgumentCaptor<Usuario> usuario = ArgumentCaptor.forClass(Usuario.class);
        verify(usuarioRepository).save(usuario.capture());
        assertNull(usuario.getValue().getContrasenaHash());
        assertEquals(ProveedorUsuario.GOOGLE, usuario.getValue().getProveedor());
        assertEquals(GOOGLE_ID, usuario.getValue().getGoogleId());
        assertEquals("Ana", usuario.getValue().getNombres());

        ArgumentCaptor<Estudiante> estudiante = ArgumentCaptor.forClass(Estudiante.class);
        verify(estudianteRepository).saveAndFlush(estudiante.capture());
        assertNull(estudiante.getValue().getCiclo());
    }

    @Test
    void usuarioQueYaExisteEntraSinCrearOtraCuenta() {
        when(usuarioRepository.findByCorreoInstitucional(CORREO)).thenReturn(Optional.of(existente));

        LoginResponse respuesta = authService.loginConGoogle(new GoogleLoginRequest("codigo-google", null));

        assertEquals(7, respuesta.usuario().id());
        verify(estudianteRepository, never()).saveAndFlush(any(Estudiante.class));
    }

    @Test
    void laRespuestaIncluyeElCicloQueYaEligio() {
        Estudiante perfil = new Estudiante();
        perfil.setCiclo(4);
        when(usuarioRepository.findByCorreoInstitucional(CORREO)).thenReturn(Optional.of(existente));
        when(estudianteRepository.findByUsuarioId(7)).thenReturn(Optional.of(perfil));

        LoginResponse respuesta = authService.loginConGoogle(new GoogleLoginRequest("codigo-google", null));

        assertEquals(4, respuesta.usuario().ciclo());
    }

    @Test
    void laPrimeraVezSeVinculaLaCuentaDeGoogleSinQuitarLaContrasena() {
        when(usuarioRepository.findByCorreoInstitucional(CORREO)).thenReturn(Optional.of(existente));

        authService.loginConGoogle(new GoogleLoginRequest("codigo-google", null));

        assertEquals(GOOGLE_ID, existente.getGoogleId());
        assertEquals("hash", existente.getContrasenaHash());
        assertEquals(ProveedorUsuario.LOCAL, existente.getProveedor());
    }

    @Test
    void cuentaVinculadaAOtraCuentaDeGoogleNoPuedeEntrar() {
        existente.setGoogleId("otro-google-id");
        when(usuarioRepository.findByCorreoInstitucional(CORREO)).thenReturn(Optional.of(existente));

        assertThrows(CredencialesInvalidasException.class,
                () -> authService.loginConGoogle(new GoogleLoginRequest("codigo-google", null)));
    }

    @Test
    void cuentaInactivaNoPuedeEntrarConGoogle() {
        existente.setEstado(EstadoUsuario.INACTIVO);
        when(usuarioRepository.findByCorreoInstitucional(CORREO)).thenReturn(Optional.of(existente));

        assertThrows(CuentaInactivaException.class,
                () -> authService.loginConGoogle(new GoogleLoginRequest("codigo-google", null)));
    }

    @Test
    void entrarConGoogleLimpiaIntentosFallidosYBloqueo() {
        existente.setIntentosFallidos(2);
        existente.setBloqueadoHasta(LocalDateTime.now().plusMinutes(5));
        when(usuarioRepository.findByCorreoInstitucional(CORREO)).thenReturn(Optional.of(existente));

        authService.loginConGoogle(new GoogleLoginRequest("codigo-google", null));

        assertEquals(0, existente.getIntentosFallidos());
        assertNull(existente.getBloqueadoHasta());
    }

    @Test
    void cuentaDeGoogleSinContrasenaAvisaQueUsaGoogleYNoCuentaIntentos() {
        existente.setContrasenaHash(null);
        existente.setProveedor(ProveedorUsuario.GOOGLE);
        when(usuarioRepository.findByCorreoInstitucional(CORREO)).thenReturn(Optional.of(existente));

        CredencialesInvalidasException error = assertThrows(CredencialesInvalidasException.class,
                () -> authService.login(new LoginRequest(CORREO, "cualquiera1")));

        assertEquals("Esta cuenta usa Google", error.getMessage());
        assertEquals(0, existente.getIntentosFallidos());
    }

    @Test
    void elEstudianteGuardaSuCiclo() {
        Estudiante perfil = new Estudiante();
        perfil.setUsuario(existente);
        when(estudianteRepository.findByUsuarioId(7)).thenReturn(Optional.of(perfil));

        LoginResponse.UsuarioSesion sesion = authService.actualizarCiclo(7, 5);

        assertEquals(5, perfil.getCiclo());
        assertEquals(5, sesion.ciclo());
        verify(estudianteRepository).save(perfil);
    }

    @Test
    void quienNoTienePerfilDeEstudianteNoPuedeElegirCiclo() {
        when(estudianteRepository.findByUsuarioId(99)).thenReturn(Optional.empty());

        assertThrows(UsuarioNoRegistradoException.class, () -> authService.actualizarCiclo(99, 2));
    }

    @Test
    void laPeticionSinCodigoNiTokenNoEsValida() {
        assertFalse(new GoogleLoginRequest(null, null).isTieneCredencial());
        assertFalse(new GoogleLoginRequest("  ", "").isTieneCredencial());
        assertTrue(new GoogleLoginRequest("codigo", null).isTieneCredencial());
        assertTrue(new GoogleLoginRequest(null, "id-token").isTieneCredencial());
    }

    @Test
    void elCicloQueSeEligeDebeEstarEntre1Y6() {
        var validador = jakarta.validation.Validation.buildDefaultValidatorFactory().getValidator();

        assertEquals(1, validador.validate(new com.techlink.dto.CicloRequest(null)).size());
        assertEquals(1, validador.validate(new com.techlink.dto.CicloRequest(0)).size());
        assertEquals(1, validador.validate(new com.techlink.dto.CicloRequest(7)).size());
        assertEquals(0, validador.validate(new com.techlink.dto.CicloRequest(1)).size());
        assertEquals(0, validador.validate(new com.techlink.dto.CicloRequest(6)).size());
    }
}
