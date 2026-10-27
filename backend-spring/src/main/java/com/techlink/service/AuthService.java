package com.techlink.service;

import com.techlink.dto.LoginRequest;
import com.techlink.dto.LoginResponse;
import com.techlink.dto.RegisterRequest;
import com.techlink.dto.RegisterResponse;
import com.techlink.exception.CorreoDuplicadoException;
import com.techlink.exception.CredencialesInvalidasException;
import com.techlink.exception.CuentaBloqueadaException;
import com.techlink.exception.CuentaInactivaException;
import com.techlink.exception.DominioInvalidoException;
import com.techlink.model.EstadoUsuario;
import com.techlink.model.Estudiante;
import com.techlink.model.Rol;
import com.techlink.model.Usuario;
import com.techlink.repository.EstudianteRepository;
import com.techlink.repository.RolRepository;
import com.techlink.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;

// Logica de negocio de autenticacion: registro de estudiantes (HU-01) e inicio de sesion (HU-02).
@Service
public class AuthService {

    private static final String DOMINIO_INSTITUCIONAL = "@tecsup.edu.pe";
    private static final String ROL_ESTUDIANTE = "ESTUDIANTE";
    // HU-02: reglas del bloqueo (3 intentos fallidos = 15 minutos) y mensaje del login.
    private static final int MAX_INTENTOS_FALLIDOS = 3;
    private static final long MINUTOS_BLOQUEO = 15;
    private static final String MENSAJE_CREDENCIALES = "Correo o contraseña incorrectos";

    private final UsuarioRepository usuarioRepository;
    private final EstudianteRepository estudianteRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    // Spring inyecta automaticamente las dependencias por constructor.
    public AuthService(UsuarioRepository usuarioRepository,
                       EstudianteRepository estudianteRepository,
                       RolRepository rolRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.estudianteRepository = estudianteRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    // HU-01 - REGISTRO. @Transactional: si falla algun guardado, se revierten ambos (usuario y estudiante).
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        // El correo se guarda TAL CUAL lo escribio el usuario (con sus mayusculas); el login debe escribirlo igual.
        String correo = request.correoInstitucional().trim();

        // 1. Solo se aceptan correos institucionales (el dominio se compara sin distinguir mayusculas).
        if (!correo.toLowerCase().endsWith(DOMINIO_INSTITUCIONAL)) {
            throw new DominioInvalidoException("Debes registrarte con tu correo institucional " + DOMINIO_INSTITUCIONAL);
        }
        // 2. El correo no debe estar ya registrado.
        if (usuarioRepository.existsByCorreoInstitucional(correo)) {
            throw new CorreoDuplicadoException("El correo ya está registrado");
        }

        // 3. Todo registro nuevo nace con rol ESTUDIANTE.
        Rol rol = rolRepository.findByNombre(ROL_ESTUDIANTE)
                .orElseThrow(() -> new IllegalStateException("No existe el rol " + ROL_ESTUDIANTE));

        // 4. Se crea el usuario con la contraseña cifrada (BCrypt).
        Usuario usuario = new Usuario();
        usuario.setNombres(request.nombres().trim());
        usuario.setApellidos(request.apellidos().trim());
        usuario.setCorreoInstitucional(correo);
        usuario.setContrasenaHash(passwordEncoder.encode(request.password()));
        usuario.setRol(rol);
        usuarioRepository.save(usuario);

        // 5. Se crea su perfil de estudiante (ciclo y codigo opcional).
        Estudiante estudiante = new Estudiante();
        estudiante.setUsuario(usuario);
        estudiante.setCiclo(request.ciclo());
        String codigo = request.codigoEstudiante();
        estudiante.setCodigoEstudiante(codigo == null || codigo.isBlank() ? null : codigo.trim());
        estudianteRepository.saveAndFlush(estudiante);

        // 6. Se devuelve solo informacion segura (sin contraseña).
        return new RegisterResponse(
                usuario.getId(),
                usuario.getNombres(),
                usuario.getApellidos(),
                usuario.getCorreoInstitucional(),
                rol.getNombre(),
                estudiante.getCiclo(),
                estudiante.getCodigoEstudiante());
    }

    // HU-02 - LOGIN. noRollbackFor: aunque se lance la excepcion de error, el contador de intentos SI debe guardarse.
    @Transactional(noRollbackFor = {CredencialesInvalidasException.class, CuentaBloqueadaException.class})
    public LoginResponse login(LoginRequest request) {
        String correo = request.correoInstitucional().trim();

        // 1. El correo debe coincidir EXACTAMENTE con el registrado (distingue mayusculas). MySQL compara sin
        //    distinguirlas, por eso se vuelve a comparar aqui. Si no existe o no coincide, se responde igual
        //    que con contraseña mala (no revela que usuarios existen).
        Usuario usuario = usuarioRepository.findByCorreoInstitucional(correo)
                .filter(u -> u.getCorreoInstitucional().equals(correo))
                .orElseThrow(() -> new CredencialesInvalidasException(MENSAJE_CREDENCIALES));

        LocalDateTime ahora = LocalDateTime.now();

        // 2. Si sigue bloqueada, se rechaza indicando los minutos que faltan.
        if (usuario.getBloqueadoHasta() != null) {
            if (usuario.getBloqueadoHasta().isAfter(ahora)) {
                throw bloqueada(usuario, ahora);
            }
            // El bloqueo ya vencio: se limpia y se permite intentar de nuevo.
            usuario.setBloqueadoHasta(null);
            usuario.setIntentosFallidos(0);
        }

        // 3. Una cuenta inactiva no puede iniciar sesion.
        if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
            throw new CuentaInactivaException("La cuenta está inactiva");
        }

        // 4. Contraseña incorrecta: se cuenta el intento y al tercero se bloquea 15 minutos.
        if (!passwordEncoder.matches(request.password(), usuario.getContrasenaHash())) {
            int intentos = usuario.getIntentosFallidos() + 1;
            if (intentos >= MAX_INTENTOS_FALLIDOS) {
                usuario.setIntentosFallidos(0);
                usuario.setBloqueadoHasta(ahora.plusMinutes(MINUTOS_BLOQUEO));
                usuarioRepository.save(usuario);
                throw bloqueada(usuario, ahora);
            }
            usuario.setIntentosFallidos(intentos);
            usuarioRepository.save(usuario);
            throw new CredencialesInvalidasException(MENSAJE_CREDENCIALES);
        }

        // 5. Credenciales correctas: se reinicia el contador y se genera el token con el rol.
        usuario.setIntentosFallidos(0);
        usuario.setBloqueadoHasta(null);
        usuarioRepository.save(usuario);

        return new LoginResponse(
                jwtService.generarToken(usuario),
                "Bearer",
                jwtService.getExpiracionSegundos(),
                new LoginResponse.UsuarioSesion(
                        usuario.getId(),
                        usuario.getNombres(),
                        usuario.getApellidos(),
                        usuario.getCorreoInstitucional(),
                        usuario.getRol().getNombre()));
    }

    // HU-02: construye la excepcion de bloqueo con los minutos que faltan (redondeado hacia arriba).
    private CuentaBloqueadaException bloqueada(Usuario usuario, LocalDateTime ahora) {
        long segundos = Duration.between(ahora, usuario.getBloqueadoHasta()).getSeconds();
        long minutos = Math.max(1, (segundos + 59) / 60);
        return new CuentaBloqueadaException("Cuenta bloqueada temporalmente por intentos fallidos", minutos);
    }
}
