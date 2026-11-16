package com.backendapi.modelo;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RolUsuario rol;

    @Column(nullable = false)
    private boolean aceptaPrivacidad;

    @Column(nullable = false)
    private LocalDateTime fechaRegistro;

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<EstacionFavorita> estacionesFavoritas = new ArrayList<>();

    @OneToMany(mappedBy = "usuario", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<RutaGuardada> rutasGuardadas = new ArrayList<>();

    public Usuario() {}

    public Usuario(String nombre, String email, String passwordHash, RolUsuario rol, boolean aceptaPrivacidad) {
        this.nombre = nombre;
        this.email = email.toLowerCase().trim();
        this.passwordHash = passwordHash;
        this.rol = rol;
        this.aceptaPrivacidad = aceptaPrivacidad;
        this.fechaRegistro = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public RolUsuario getRol() { return rol; }
    public void setRol(RolUsuario rol) { this.rol = rol; }
    public boolean isAceptaPrivacidad() { return aceptaPrivacidad; }
    public void setAceptaPrivacidad(boolean aceptaPrivacidad) { this.aceptaPrivacidad = aceptaPrivacidad; }
    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
}