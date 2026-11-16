package modelo;

import java.time.LocalTime;
import java.time.LocalDateTime;

public class LlegadaDTO {
    private String linea;
    private String destino;
    private LocalTime horaPlanificada;
    private LocalTime horaEstimada;
    private int retrasoMinutos;
    private EstadoTren estado;
    private boolean esTiempoReal;
    private LocalDateTime ultimaActualizacion;

    public LlegadaDTO() {}

    public LlegadaDTO(String linea, String destino, LocalTime horaPlanificada, LocalTime horaEstimada,
                      int retrasoMinutos, EstadoTren estado, boolean esTiempoReal, LocalDateTime ultimaActualizacion) {
        this.linea = linea;
        this.destino = destino;
        this.horaPlanificada = horaPlanificada;
        this.horaEstimada = horaEstimada;
        this.retrasoMinutos = retrasoMinutos;
        this.estado = estado;
        this.esTiempoReal = esTiempoReal;
        this.ultimaActualizacion = ultimaActualizacion;
    }

    public String getLinea() { return linea; }
    public void setLinea(String linea) { this.linea = linea; }

    public String getDestino() { return destino; }
    public void setDestino(String destino) { this.destino = destino; }

    public LocalTime getHoraPlanificada() { return horaPlanificada; }
    public void setHoraPlanificada(LocalTime horaPlanificada) { this.horaPlanificada = horaPlanificada; }

    public LocalTime getHoraEstimada() { return horaEstimada; }
    public void setHoraEstimada(LocalTime horaEstimada) { this.horaEstimada = horaEstimada; }

    public int getRetrasoMinutos() { return retrasoMinutos; }
    public void setRetrasoMinutos(int retrasoMinutos) { this.retrasoMinutos = retrasoMinutos; }

    public EstadoTren getEstado() { return estado; }
    public void setEstado(EstadoTren estado) { this.estado = estado; }

    public boolean isEsTiempoReal() { return esTiempoReal; }
    public void setEsTiempoReal(boolean esTiempoReal) { this.esTiempoReal = esTiempoReal; }

    public LocalDateTime getUltimaActualizacion() { return ultimaActualizacion; }
    public void setUltimaActualizacion(LocalDateTime ultimaActualizacion) { this.ultimaActualizacion = ultimaActualizacion; }
}
