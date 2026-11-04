package com.techlink.model;

import jakarta.persistence.*;

import java.time.LocalDateTime;

// Entidad que representa la tabla "usuarios": datos de acceso comunes a todos los roles.
@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_usuario")
    private Integer id;

    @Column(nullable = false, length = 100)
    private String nombres;

    @Column(nullable = false, length = 100)
    private String apellidos;

    @Column(name = "correo_institucional", nullable = false, unique = true, length = 150)
    private String correoInstitucional;

    // Se guarda el hash BCrypt, nunca la contraseña en texto plano. Es null en las cuentas creadas con Google (V3).
    @Column(name = "contrasena_hash")
    private String contrasenaHash;

    // Relación muchos usuarios -> un rol (clave foránea id_rol).
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "id_rol", nullable = false)
    private Rol rol;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EstadoUsuario estado = EstadoUsuario.ACTIVO;

    // La llena MySQL con CURRENT_TIMESTAMP, por eso es solo lectura.
    @Column(name = "fecha_registro", insertable = false, updatable = false)
    private LocalDateTime fechaRegistro;

    // HU-02: contador de contraseñas incorrectas seguidas (V2__bloqueo_login.sql).
    @Column(name = "intentos_fallidos", nullable = false)
    private Integer intentosFallidos = 0;

    // HU-02: si tiene fecha futura, la cuenta está bloqueada hasta ese momento.
    @Column(name = "bloqueado_hasta")
    private LocalDateTime bloqueadoHasta;

    // HU-02 (Google): como se creo la cuenta y el identificador estable de su cuenta de Google (V3__google_login.sql).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProveedorUsuario proveedor = ProveedorUsuario.LOCAL;

    @Column(name = "google_id", length = 50, unique = true)
    private String googleId;

    public Usuario() {
    }

    public Integer getId() { return id; }
    public void setId(Integer id) { this.id = id; }

    public String getNombres() { return nombres; }
    public void setNombres(String nombres) { this.nombres = nombres; }

    public String getApellidos() { return apellidos; }
    public void setApellidos(String apellidos) { this.apellidos = apellidos; }

    public String getCorreoInstitucional() { return correoInstitucional; }
    public void setCorreoInstitucional(String correoInstitucional) { this.correoInstitucional = correoInstitucional; }

    public String getContrasenaHash() { return contrasenaHash; }
    public void setContrasenaHash(String contrasenaHash) { this.contrasenaHash = contrasenaHash; }

    public Rol getRol() { return rol; }
    public void setRol(Rol rol) { this.rol = rol; }

    public EstadoUsuario getEstado() { return estado; }
    public void setEstado(EstadoUsuario estado) { this.estado = estado; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }

    public Integer getIntentosFallidos() { return intentosFallidos; }
    public void setIntentosFallidos(Integer intentosFallidos) { this.intentosFallidos = intentosFallidos; }

    public ProveedorUsuario getProveedor() { return proveedor; }
    public void setProveedor(ProveedorUsuario proveedor) { this.proveedor = proveedor; }

    public String getGoogleId() { return googleId; }
    public void setGoogleId(String googleId) { this.googleId = googleId; }

    public LocalDateTime getBloqueadoHasta() { return bloqueadoHasta; }
    public void setBloqueadoHasta(LocalDateTime bloqueadoHasta) { this.bloqueadoHasta = bloqueadoHasta; }
}
