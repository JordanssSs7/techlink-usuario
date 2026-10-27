package com.techlink.service;

import com.techlink.dto.GoogleLoginRequest;
import com.techlink.dto.LoginRequest;
import com.techlink.dto.LoginResponse;
import com.techlink.dto.RegisterRequest;
import com.techlink.dto.RegisterResponse;
import com.techlink.exception.CorreoDuplicadoException;
import com.techlink.exception.CredencialesInvalidasException;
import com.techlink.exception.CuentaBloqueadaException;
import com.techlink.exception.CuentaInactivaException;
import com.techlink.exception.DominioInvalidoException;
import com.techlink.exception.UsuarioNoRegistradoException;
import com.techlink.model.EstadoUsuario;
import com.techlink.model.Estudiante;
import com.techlink.model.ProveedorUsuario;
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
import java.util.Optional;

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
    private final GoogleTokenVerifier googleTokenVerifier;

    // Spring inyecta automaticamente las dependencias por constructor.
    public AuthService(UsuarioRepository usuarioRepository,
                       EstudianteRepository estudianteRepository,
                       RolRepository rolRepository,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       GoogleTokenVerifier googleTokenVerifier) {
        this.usuarioRepository = usuarioRepository;
        this.estudianteRepository = estudianteRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.googleTokenVerifier = googleTokenVerifier;
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

        // 1.1 Las cuentas creadas con Google no tienen contraseña: se avisa sin llamar a BCrypt ni contar intentos.
        if (usuario.getContrasenaHash() == null) {
            throw new CredencialesInvalidasException("Esta cuenta usa Google");
        }

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

        return respuestaLogin(usuario);
    }

    // HU-02 (Google) - LOGIN CON GOOGLE. Si el correo no existe, se crea la cuenta con los datos de Google
    // (sin contraseña, ciclo vacio); el estudiante elige su ciclo despues con PATCH /api/estudiantes/me.
    @Transactional
    public LoginResponse loginConGoogle(GoogleLoginRequest request) {
        // 1. Se canjea el codigo y se verifica el token con Google (firma, vencimiento, Client ID, correo verificado
        //    y dominio institucional; si no es @tecsup.edu.pe responde 403).
        GoogleTokenVerifier.GoogleUsuario google = googleTokenVerifier.verificar(request.code(), request.idToken());

        // 2. Google entrega el correo en minusculas: se busca sin distinguir mayusculas (la BD ya lo hace asi).
        Optional<Usuario> existente = usuarioRepository.findByCorreoInstitucional(google.correo());
        Usuario usuario;

        if (existente.isEmpty()) {
            // 3. Primer ingreso: se crea la cuenta como ESTUDIANTE.
            usuario = crearCuentaDeGoogle(google);
        } else {
            usuario = existente.get();
            // 4. Si la cuenta ya esta vinculada a OTRA cuenta de Google, no se deja entrar (correo reasignado).
            if (usuario.getGoogleId() != null && !usuario.getGoogleId().equals(google.googleId())) {
                throw new CredencialesInvalidasException("Este correo está vinculado a otra cuenta de Google");
            }
            // 5. Una cuenta inactiva no puede iniciar sesion.
            if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
                throw new CuentaInactivaException("La cuenta está inactiva");
            }
            // 6. Se vincula la cuenta de Google la primera vez y se limpian los intentos fallidos y el bloqueo.
            if (usuario.getGoogleId() == null) {
                usuario.setGoogleId(google.googleId());
            }
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(null);
            usuarioRepository.save(usuario);
        }

        // 7. Desde aqui es igual al login normal: se entrega NUESTRO token JWT.
        return respuestaLogin(usuario);
    }

    // HU-02 (Google): crea el usuario y su perfil de estudiante con los datos que entrega Google.
    private Usuario crearCuentaDeGoogle(GoogleTokenVerifier.GoogleUsuario google) {
        Rol rol = rolRepository.findByNombre(ROL_ESTUDIANTE)
                .orElseThrow(() -> new IllegalStateException("No existe el rol " + ROL_ESTUDIANTE));

        Usuario usuario = new Usuario();
        usuario.setNombres(recortar(google.nombres(), 100));
        usuario.setApellidos(recortar(google.apellidos(), 100));
        usuario.setCorreoInstitucional(google.correo());
        usuario.setContrasenaHash(null);
        usuario.setProveedor(ProveedorUsuario.GOOGLE);
        usuario.setGoogleId(google.googleId());
        usuario.setRol(rol);
        usuarioRepository.save(usuario);

        Estudiante estudiante = new Estudiante();
        estudiante.setUsuario(usuario);
        estudiante.setCiclo(null);
        estudianteRepository.saveAndFlush(estudiante);
        return usuario;
    }

    private String recortar(String texto, int maximo) {
        return texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }

    // HU-02 (Google) - PATCH /api/estudiantes/me: guarda el ciclo que el estudiante elige en su primer ingreso.
    @Transactional
    public LoginResponse.UsuarioSesion actualizarCiclo(Integer idUsuario, Integer ciclo) {
        Estudiante estudiante = estudianteRepository.findByUsuarioId(idUsuario)
                .orElseThrow(() -> new UsuarioNoRegistradoException("No tienes perfil de estudiante"));
        estudiante.setCiclo(ciclo);
        estudianteRepository.save(estudiante);
        return datosDeSesion(estudiante.getUsuario(), ciclo);
    }

    // HU-02: arma la respuesta del login (token JWT y datos del usuario). La usan el login normal y el de Google.
    private LoginResponse respuestaLogin(Usuario usuario) {
        Integer ciclo = estudianteRepository.findByUsuarioId(usuario.getId())
                .map(Estudiante::getCiclo)
                .orElse(null);
        return new LoginResponse(
                jwtService.generarToken(usuario),
                "Bearer",
                jwtService.getExpiracionSegundos(),
                datosDeSesion(usuario, ciclo));
    }

    // HU-02: datos del usuario que viajan al frontend (ciclo vacio = todavia debe elegirlo).
    private LoginResponse.UsuarioSesion datosDeSesion(Usuario usuario, Integer ciclo) {
        return new LoginResponse.UsuarioSesion(
                usuario.getId(),
                usuario.getNombres(),
                usuario.getApellidos(),
                usuario.getCorreoInstitucional(),
                usuario.getRol().getNombre(),
                ciclo);
    }

    // HU-02: construye la excepcion de bloqueo con los minutos que faltan (redondeado hacia arriba).
    private CuentaBloqueadaException bloqueada(Usuario usuario, LocalDateTime ahora) {
        long segundos = Duration.between(ahora, usuario.getBloqueadoHasta()).getSeconds();
        long minutos = Math.max(1, (segundos + 59) / 60);
        return new CuentaBloqueadaException("Cuenta bloqueada temporalmente por intentos fallidos", minutos);
    }
}
