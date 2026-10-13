package com.techlink.service;

import com.techlink.dto.RegisterRequest;
import com.techlink.dto.RegisterResponse;
import com.techlink.exception.CorreoDuplicadoException;
import com.techlink.exception.DominioInvalidoException;
import com.techlink.model.Estudiante;
import com.techlink.model.Rol;
import com.techlink.model.Usuario;
import com.techlink.repository.EstudianteRepository;
import com.techlink.repository.RolRepository;
import com.techlink.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// Logica de negocio de autenticacion. Por ahora: registro de estudiantes (HU-01).
@Service
public class AuthService {

    private static final String DOMINIO_INSTITUCIONAL = "@tecsup.edu.pe";
    private static final String ROL_ESTUDIANTE = "ESTUDIANTE";

    private final UsuarioRepository usuarioRepository;
    private final EstudianteRepository estudianteRepository;
    private final RolRepository rolRepository;
    private final PasswordEncoder passwordEncoder;

    // Spring inyecta automaticamente las dependencias por constructor.
    public AuthService(UsuarioRepository usuarioRepository,
                       EstudianteRepository estudianteRepository,
                       RolRepository rolRepository,
                       PasswordEncoder passwordEncoder) {
        this.usuarioRepository = usuarioRepository;
        this.estudianteRepository = estudianteRepository;
        this.rolRepository = rolRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // @Transactional: si falla algun guardado, se revierten ambos (usuario y estudiante).
    @Transactional
    public RegisterResponse register(RegisterRequest request) {
        String correo = request.correoInstitucional().trim().toLowerCase();

        // 1. Solo se aceptan correos institucionales.
        if (!correo.endsWith(DOMINIO_INSTITUCIONAL)) {
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
}
