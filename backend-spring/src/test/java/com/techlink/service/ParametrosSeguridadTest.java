package com.techlink.service;

import com.techlink.dto.LoginRequest;
import com.techlink.exception.CorreoNoInstitucionalException;
import com.techlink.exception.CredencialesInvalidasException;
import com.techlink.exception.CuentaBloqueadaException;
import com.techlink.model.ParametrosSeguridad;
import com.techlink.model.Rol;
import com.techlink.model.Usuario;
import com.techlink.repository.EstudianteRepository;
import com.techlink.repository.ParametrosSeguridadRepository;
import com.techlink.repository.RolRepository;
import com.techlink.repository.UsuarioRepository;
import com.techlink.service.ParametrosSeguridadService.Parametros;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

// Pruebas de HU-60: los parámetros de seguridad (dominio, intentos máximos y minutos de bloqueo) que cambia el
// administrador se aplican en los siguientes inicios de sesión.
class ParametrosSeguridadTest {

    private static final String CORREO = "admin@techlink.com";
    private static final String PASSWORD = "clave1234";

    private Parametros vigentes = Parametros.PREDETERMINADOS;
    private UsuarioRepository usuarioRepository;
    private Usuario admin;
    private AuthService authService;

    @BeforeEach
    void preparar() {
        usuarioRepository = Mockito.mock(UsuarioRepository.class);
        JwtService jwtService = Mockito.mock(JwtService.class);
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        Supplier<Parametros> actuales = () -> vigentes;
        authService = new AuthService(usuarioRepository, Mockito.mock(EstudianteRepository.class),
                Mockito.mock(RolRepository.class), encoder, jwtService, Mockito.mock(GoogleTokenVerifier.class), false, actuales);

        Rol rol = new Rol();
        rol.setNombre("ADMIN");
        admin = new Usuario();
        admin.setId(1);
        admin.setNombres("Ana");
        admin.setApellidos("Perez");
        admin.setCorreoInstitucional(CORREO);
        admin.setContrasenaHash(encoder.encode(PASSWORD));
        admin.setRol(rol);
        when(usuarioRepository.findByCorreoInstitucional(CORREO)).thenReturn(Optional.of(admin));
        when(usuarioRepository.save(any(Usuario.class))).thenAnswer(i -> i.getArgument(0));
        when(jwtService.generarToken(any(Usuario.class))).thenReturn("token");
        when(jwtService.getExpiracionSegundos()).thenReturn(3600L);
    }

    private void fallar() {
        assertThrows(CredencialesInvalidasException.class, () -> authService.login(new LoginRequest(CORREO, "mala")));
    }

    // ---- Intentos máximos y tiempo de bloqueo ----

    @Test
    void conLosValoresPredeterminadosSeBloqueaAlTercerIntentoPor15Minutos() {
        fallar();
        fallar();
        CuentaBloqueadaException bloqueo = assertThrows(CuentaBloqueadaException.class,
                () -> authService.login(new LoginRequest(CORREO, "mala")));

        assertTrue(bloqueo.getMinutosRestantes() >= 14 && bloqueo.getMinutosRestantes() <= 15);
    }

    @Test
    void conUnMaximoDeDosIntentosSeBloqueaAlSegundo() {
        vigentes = new Parametros("tecsup.edu.pe", 2, 15);

        fallar();
        assertThrows(CuentaBloqueadaException.class, () -> authService.login(new LoginRequest(CORREO, "mala")));
        assertNotNull(admin.getBloqueadoHasta());
    }

    @Test
    void conUnMaximoDeCincoIntentosElTerceroTodaviaNoBloquea() {
        vigentes = new Parametros("tecsup.edu.pe", 5, 15);

        fallar();
        fallar();
        fallar();
        fallar();
        assertEquals(4, admin.getIntentosFallidos());
        assertEquals(null, admin.getBloqueadoHasta());
        assertThrows(CuentaBloqueadaException.class, () -> authService.login(new LoginRequest(CORREO, "mala")));
    }

    @Test
    void elTiempoDeBloqueoSaleDeLosParametros() {
        vigentes = new Parametros("tecsup.edu.pe", 1, 60);

        CuentaBloqueadaException bloqueo = assertThrows(CuentaBloqueadaException.class,
                () -> authService.login(new LoginRequest(CORREO, "mala")));

        assertTrue(bloqueo.getMinutosRestantes() > 55 && bloqueo.getMinutosRestantes() <= 60);
        assertTrue(admin.getBloqueadoHasta().isAfter(LocalDateTime.now().plusMinutes(55)));
    }

    @Test
    void unCambioDeParametrosRigeEnElSiguienteIntentoSinReiniciar() {
        fallar();
        // El administrador baja el máximo a 2 mientras la cuenta ya lleva 1 intento fallido.
        vigentes = new Parametros("tecsup.edu.pe", 2, 5);

        CuentaBloqueadaException bloqueo = assertThrows(CuentaBloqueadaException.class,
                () -> authService.login(new LoginRequest(CORREO, "mala")));

        assertTrue(bloqueo.getMinutosRestantes() <= 5);
    }

    // ---- Dominio institucional ----

    private Jwt tokenDeGoogle(String correo) {
        return Jwt.withTokenValue("t").header("alg", "RS256").subject("google-1")
                .claim("email", correo).claim("email_verified", true).claim("given_name", "Ana").build();
    }

    private GoogleTokenVerifier verificadorCon(Parametros parametros) {
        return new GoogleTokenVerifier("id", "secreto", "http://x/token", "http://x/certs", () -> parametros);
    }

    @Test
    void conElDominioPredeterminadoSoloEntraTecsup() {
        GoogleTokenVerifier verificador = verificadorCon(Parametros.PREDETERMINADOS);

        assertEquals("ana@tecsup.edu.pe", verificador.extraerUsuario(tokenDeGoogle("ana@tecsup.edu.pe")).correo());
        assertThrows(CorreoNoInstitucionalException.class, () -> verificador.extraerUsuario(tokenDeGoogle("ana@gmail.com")));
    }

    @Test
    void elDominioConfiguradoReemplazaAlAnterior() {
        GoogleTokenVerifier verificador = verificadorCon(new Parametros("instituto.edu.pe", 3, 15));

        assertEquals("ana@instituto.edu.pe", verificador.extraerUsuario(tokenDeGoogle("ana@instituto.edu.pe")).correo());
        CorreoNoInstitucionalException error = assertThrows(CorreoNoInstitucionalException.class,
                () -> verificador.extraerUsuario(tokenDeGoogle("ana@tecsup.edu.pe")));
        assertEquals("Solo se permiten cuentas @instituto.edu.pe", error.getMessage());
    }

    @Test
    void unDominioParecidoNoSeAcepta() {
        GoogleTokenVerifier verificador = verificadorCon(Parametros.PREDETERMINADOS);

        assertThrows(CorreoNoInstitucionalException.class, () -> verificador.extraerUsuario(tokenDeGoogle("ana@fake-tecsup.edu.pe")));
        assertThrows(CorreoNoInstitucionalException.class, () -> verificador.extraerUsuario(tokenDeGoogle("ana@tecsup.edu.pe.evil.com")));
    }

    @Test
    void elDominioNoDistingueMayusculas() {
        assertTrue(Parametros.PREDETERMINADOS.aceptaCorreo("ANA@TECSUP.EDU.PE"));
        assertFalse(Parametros.PREDETERMINADOS.aceptaCorreo(null));
    }

    // ---- Lectura de la base ----

    @Test
    void elServicioLeeLaFilaDeLaBaseYNormalizaElDominio() {
        ParametrosSeguridadRepository repositorio = Mockito.mock(ParametrosSeguridadRepository.class);
        ParametrosSeguridad fila = new ParametrosSeguridad();
        fila.setDominioPermitido("  Instituto.EDU.pe ");
        fila.setMaxIntentos(4);
        fila.setMinutosBloqueo(30);
        when(repositorio.findById(ParametrosSeguridad.ID)).thenReturn(Optional.of(fila));

        Parametros p = new ParametrosSeguridadService(repositorio).obtener();

        assertEquals(new Parametros("instituto.edu.pe", 4, 30), p);
    }

    @Test
    void sinFilaSeUsanLosValoresPredeterminados() {
        ParametrosSeguridadRepository repositorio = Mockito.mock(ParametrosSeguridadRepository.class);
        when(repositorio.findById(ParametrosSeguridad.ID)).thenReturn(Optional.empty());

        assertEquals(Parametros.PREDETERMINADOS, new ParametrosSeguridadService(repositorio).obtener());
    }
}
