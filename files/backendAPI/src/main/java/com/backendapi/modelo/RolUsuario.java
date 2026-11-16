package com.backendapi.modelo;

/**
 * Roles del sistema (US-013):
 * - VIAJERO: registro público permitido.
 * - GESTOR y ANALISTA: cuentas restringidas, no creables desde el registro público.
 */
public enum RolUsuario {
    VIAJERO,
    GESTOR,
    ANALISTA
}