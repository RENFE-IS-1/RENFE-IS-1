package com.backendapi.modelo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.time.LocalDateTime;

/**
 * Declaración de un viajero sobre una incidencia.
 *
 * Las declaraciones de varios viajeros sobre la misma línea y el mismo tipo se agrupan en una sola
 * Incidencia (origen DECLARADA_POR_USUARIO); aquí se guarda lo que aporta cada uno. Un viajero solo
 * puede declarar una vez cada incidencia (restricción única), para que no cuente dos veces.
 */
@Entity
@Table(name = "declaraciones_incidencia", uniqueConstraints = {
    @UniqueConstraint(columnNames = {"incidencia_id", "usuario_id"})
})
public class DeclaracionIncidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Incidencia (grupo) a la que pertenece esta declaración. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "incidencia_id", nullable = false)
    @JsonIgnore
    private Incidencia incidencia;

    /**
     * Viajero que la declaró. Si borra su cuenta, la base de datos pone este campo a null
     * (ON DELETE SET NULL): la declaración se conserva y borrar la cuenta no falla.
     * No se envía en el JSON para no exponer los datos del usuario.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id")
    @OnDelete(action = OnDeleteAction.SET_NULL)
    @JsonIgnore
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TipoIncidenciaUsuario tipo;

    /** Qué ocurre, escrito por el viajero; obligatorio cuando el tipo es OTRO. */
    @Column(length = 200)
    private String especificacion;

    /** Comentario opcional del viajero. */
    @Column(length = 1000)
    private String comentario;

    @Column(nullable = false)
    private LocalDateTime fecha;

    /** Constructor vacío exigido por JPA. */
    public DeclaracionIncidencia() {}

    /** Crea la declaración de un viajero; lo necesitamos al declarar una incidencia. */
    public DeclaracionIncidencia(Incidencia incidencia, Usuario usuario, TipoIncidenciaUsuario tipo,
                                 String especificacion, String comentario, LocalDateTime fecha) {
        this.incidencia = incidencia;
        this.usuario = usuario;
        this.tipo = tipo;
        this.especificacion = especificacion;
        this.comentario = comentario;
        this.fecha = fecha;
    }

    public Long getId() { return id; }
    public Incidencia getIncidencia() { return incidencia; }
    public Usuario getUsuario() { return usuario; }
    public TipoIncidenciaUsuario getTipo() { return tipo; }
    public String getEspecificacion() { return especificacion; }
    public String getComentario() { return comentario; }
    public LocalDateTime getFecha() { return fecha; }
}
