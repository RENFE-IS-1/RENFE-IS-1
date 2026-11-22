# Evidencias de pruebas y calidad — Sprint 4

**Fecha de recopilación:** 15 de noviembre de 2026  
**Proyecto:** Sistema de planificación de Cercanías Madrid  
**Responsable:** Pedro Vicente Polo

## Resumen de ejecución del backend

Se ejecutó la suite desde `files/backendAPI` con:

```powershell
.\mvnw.cmd test -q
```

**Resultado de la última ejecución completa:** 48 pruebas, 0 fallos, 0 errores y 0 omitidas. Los informes Surefire disponibles al recopilar estas evidencias están en `files/backendAPI/target/surefire-reports/`.

| Suite | Pruebas | Fallos | Errores |
| --- | ---: | ---: | ---: |
| `BackendApiApplicationTests` | 1 | 0 | 0 |
| `RutasControllerIntegrationTest` | 9 | 0 | 0 |
| `ClienteApiRenfeTest` | 4 | 0 | 0 |
| `GTFSLoaderTest` | 5 | 0 | 0 |
| `RetrasosTiempoRealTest` | 3 | 0 | 0 |
| `ServicioBuscadorRutasTest` | 4 | 0 | 0 |
| `BuscadorEstacionesTest` | 11 | 0 | 0 |
| `ConsultaLlegadasTest` | 11 | 0 | 0 |
| **Total** | **48** | **0** | **0** |

Las pruebas del cliente Renfe usan respuestas HTTP simuladas (`MockRestServiceServer`); no dependen del servicio externo ni de una conexión a Renfe.

## Evidencias de integración y datos inválidos

Las pruebas de integración comprueban los endpoints de estaciones, llegadas y rutas. Las pruebas del cliente Renfe verifican que se acepten posiciones válidas y que los feeds vacíos, coordenadas inválidas, errores HTTP o JSON mal formado se gestionen sin conservar posiciones anteriores como actuales. Se usan respuestas simuladas, sin depender de Renfe.

## Evidencias de revisión estática de accesibilidad

Se revisó la página web. El JavaScript inline pasó la comprobación de sintaxis, y los 13 campos revisados tienen nombre accesible y etiquetas correctamente asociadas. Se mejoraron el foco de teclado, los avisos accesibles y la navegación por el mapa.

## Trazabilidad de resultados

- La compilación inicial del backend falló por referencias a `EstadoTren` y `LlegadaDTO` inexistentes y por imports a un paquete `gtfsrt` inexistente. Se añadieron los tipos usados por el controlador y se retiraron los imports sin uso; después la suite pasó con 44 pruebas.
- Al añadir las cuatro pruebas del cliente Renfe, la primera ejecución completa detectó que Spring no identificaba el constructor de producción. Se indicó con `@Autowired` y la ejecución completa posterior pasó con 48 pruebas.
- Los informes Surefire se generan en `target/` y normalmente no se incluyen en Git. Este documento conserva el resumen de la ejecución revisada para la sprint review.
