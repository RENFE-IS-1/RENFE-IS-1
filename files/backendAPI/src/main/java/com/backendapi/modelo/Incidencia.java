package com.backendapi.modelo;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Incidencia registrada en una línea (US-017).
 *
 * Cada incidencia guarda la línea, el tipo, la descripción, el origen (aviso oficial o detección
 * propia) y las horas de inicio y fin (criterio 5). Se guarda en la base de datos H2 local para
 * conservar el histórico (también útil para los análisis de EP-05).
 *
 * Estado: una incidencia está activa mientras no tenga hora de fin. No se guarda un campo
 * "activa" aparte para que no pueda contradecir a la hora de fin; se calcula con isActiva().
 */
@Entity
@Table(name = "incidencias", indexes = {
    @Index(name = "idx_incidencia_linea", columnList = "linea"),
    @Index(name = "idx_incidencia_id_externo", columnList = "idExterno")
})
public class Incidencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Línea afectada, por ejemplo "C3". */
    @Column(nullable = false)
    private String linea;

    /**
     * Tipo de incidencia, por ejemplo "RETRASO" en las detecciones propias o el título del aviso
     * en los avisos oficiales. Es texto libre porque Renfe no publica un catálogo cerrado.
     */
    @Column(nullable = false)
    private String tipo;

    /** Texto descriptivo. Los avisos oficiales pueden ser largos, de ahí la longitud ampliada. */
    @Column(length = 2000)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrigenIncidencia origen;

    /**
     * Identificador del aviso en la fuente externa (id de la entidad del feed GTFS-RT).
     * Sirve para no registrar dos veces el mismo aviso oficial (criterio 3).
     * Es null en las detecciones propias, que se identifican por línea + origen mientras siguen activas.
     */
    private String idExterno;

    /** Momento en que se detectó por primera vez. */
    @Column(nullable = false)
    private LocalDateTime inicio;

    /** Momento en que dejó de detectarse; null mientras sigue activa (criterio 4). */
    private LocalDateTime fin;

    /** Última vez que se volvió a detectar o se actualizó su descripción (lo usará US-031). */
    @Column(nullable = false)
    private LocalDateTime ultimaActualizacion;

    /** Constructor vacío exigido por JPA. */
    public Incidencia() {}

    public Incidencia(String linea, String tipo, String descripcion, OrigenIncidencia origen,
                      String idExterno, LocalDateTime inicio) {
        this.linea = linea;
        this.tipo = tipo;
        this.descripcion = descripcion;
        this.origen = origen;
        this.idExterno = idExterno;
        this.inicio = inicio;
        this.ultimaActualizacion = inicio;
    }

    /** Una incidencia está activa mientras no tenga hora de fin. */
    public boolean isActiva() {
        return fin == null;
    }

    /**
     * La incidencia se ha vuelto a detectar: no se crea un registro nuevo (criterio 3), solo se
     * actualiza la hora de última actualización y, si viene una, la descripción.
     */
    public void registrarNuevaDeteccion(LocalDateTime momento, String nuevaDescripcion) {
        this.ultimaActualizacion = momento;
        if (nuevaDescripcion != null && !nuevaDescripcion.isBlank()) {
            this.descripcion = nuevaDescripcion;
        }
    }

    /**
     * La incidencia ha dejado de detectarse: se marca como finalizada con su hora de fin (criterio 4).
     * Si ya estaba finalizada no se cambia, para conservar la hora de fin original.
     */
    public void finalizar(LocalDateTime momento) {
        if (fin == null) {
            this.fin = momento;
            this.ultimaActualizacion = momento;
        }
    }

    public Long getId() { return id; }
    public String getLinea() { return linea; }
    public void setLinea(String linea) { this.linea = linea; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }
    public OrigenIncidencia getOrigen() { return origen; }
    public void setOrigen(OrigenIncidencia origen) { this.origen = origen; }
    public String getIdExterno() { return idExterno; }
    public void setIdExterno(String idExterno) { this.idExterno = idExterno; }
    public LocalDateTime getInicio() { return inicio; }
    public LocalDateTime getFin() { return fin; }
    public LocalDateTime getUltimaActualizacion() { return ultimaActualizacion; }
}
