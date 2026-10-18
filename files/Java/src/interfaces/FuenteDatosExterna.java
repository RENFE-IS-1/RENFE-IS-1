package interfaces;

import repositorio.RedTransporte;

public interface FuenteDatosExterna {
    void conectar();
    void cargarDatos(RedTransporte red);
    void actualizarTiempoReal(RedTransporte red);
}