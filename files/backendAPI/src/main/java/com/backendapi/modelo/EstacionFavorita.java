package com.backendapi.modelo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "estaciones_favoritas", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"usuario_id", "stopId"})
})
public class EstacionFavorita {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String stopId;

    @Column(nullable = false)
    private String nombreEstacion;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    @JsonIgnore
    private Usuario usuario;

    public EstacionFavorita() {}

    public EstacionFavorita(String stopId, String nombreEstacion, Usuario usuario) {
        this.stopId = stopId;
        this.nombreEstacion = nombreEstacion;
        this.usuario = usuario;
    }

    public Long getId() { return id; }
    public String getStopId() { return stopId; }
    public void setStopId(String stopId) { this.stopId = stopId; }
    public String getNombreEstacion() { return nombreEstacion; }
    public void setNombreEstacion(String nombreEstacion) { this.nombreEstacion = nombreEstacion; }
    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }
}