package com.techlink.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// HU-24: qué canales quiere el usuario para cada tipo de notificación. Si no hay fila, recibe los dos.
@Entity
@Table(name = "preferencias_notificacion")
public class PreferenciaNotificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id_preferencia")
    private Integer id;

    @Column(name = "id_usuario", nullable = false)
    private Integer idUsuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Notificacion.Tipo tipo;

    @Column(name = "en_app", nullable = false)
    private boolean enApp = true;

    @Column(nullable = false)
    private boolean correo = true;

    public PreferenciaNotificacion() {
    }

    public PreferenciaNotificacion(Integer idUsuario, Notificacion.Tipo tipo, boolean enApp, boolean correo) {
        this.idUsuario = idUsuario;
        this.tipo = tipo;
        this.enApp = enApp;
        this.correo = correo;
    }

    public Integer getId() { return id; }

    public Integer getIdUsuario() { return idUsuario; }
    public void setIdUsuario(Integer idUsuario) { this.idUsuario = idUsuario; }

    public Notificacion.Tipo getTipo() { return tipo; }
    public void setTipo(Notificacion.Tipo tipo) { this.tipo = tipo; }

    public boolean isEnApp() { return enApp; }
    public void setEnApp(boolean enApp) { this.enApp = enApp; }

    public boolean isCorreo() { return correo; }
    public void setCorreo(boolean correo) { this.correo = correo; }
}
