\# Memoria - Sprint 1



\## 1. Trabajo Realizado

Resumen de las tareas completadas durante estas dos semanas. Se indica qué historias de usuario se han cerrado completamente y cuáles quedan pendientes.

**US-004 — Consultar las próximas llegadas a una estación** (responsable: Íñigo Cueto Hernández)

Estado: completada, con 6 de sus 7 criterios de aceptación cumplidos; el criterio 5 se cumple parcialmente por una limitación de los datos (ver *Problemas encontrados*). La implementación se ha desarrollado con ayuda de un asistente de IA (Claude), según lo acordado por el equipo.

- Carga del calendario de servicios (`calendar.txt` y `calendar_dates.txt`) para saber qué trenes circulan cada día.
- Clase `ConsultaLlegadas`, que devuelve los próximos trenes de una estación a partir de una fecha y hora, con su línea y su destino, e indica si el tren termina en esa estación. También lista las líneas que paran en la estación ese día y sus sentidos.
- Clase `BuscadorEstaciones`, que busca estaciones por ID o por nombre (sin distinguir mayúsculas ni tildes) y se puede reutilizar en US-001.
- Nuevo comando de terminal `llegadas <estación> [línea] [HH:MM] [dd/mm]`, por ejemplo `llegadas atocha`, `llegadas sol c3 08:00` o `llegadas chamartin c4 07:00 10/10`. Indica que los horarios son planificados y el periodo de validez de los datos.
- Verificación de los resultados con los datos reales de Renfe (ver *Decisiones técnicas*).

| Criterio de US-004 | Estado |
|---|---|
| 1. Seleccionar una estación o parada | Cumplido (por nombre, fragmento o ID; avisa si es ambiguo) |
| 2. Mostrar los servicios que pasan por ella | Cumplido (líneas del día y sus sentidos) |
| 3. Hora prevista de llegada | Cumplido |
| 4. Línea y sentido | Cumplido (el sentido se expresa como estación de destino) |
| 5. Fecha y hora de actualización | Parcial (periodo de validez y fecha de los archivos; el feed no incluye fecha de publicación) |
| 6. Distinguir horarios planificados de tiempo real | Cumplido (aviso explícito en cada consulta) |
| 7. Informar si no hay información | Cumplido (estación inexistente, fecha sin datos, sin trenes o sin datos cargados) |

Fuera de alcance en este sprint: retrasos y cancelaciones en tiempo real, y estaciones de Metro.




\## 2. Decisiones Técnicas

Explicación de por qué se han tomado ciertas decisiones (por ejemplo, justificar la elección del formato de los datos o de las estructuras de datos utilizadas para almacenar las estaciones).

**US-004**

- **Uso del calendario GTFS.** Los horarios de Renfe cubren un mes completo, así que `stop_times.txt` mezcla los trenes de todos los días. Cada viaje tiene un `service_id`, y `calendar.txt` indica qué días circula. Sin cargarlo, una consulta en domingo mostraría también los trenes de los días laborables. Se ha creado la clase `ServicioCalendario`, que aplica primero las excepciones de `calendar_dates.txt` (por ejemplo, un festivo), después el rango de fechas y por último el día de la semana, que es el orden que marca el estándar GTFS.
- **Horas posteriores a las 24:00.** En GTFS, un tren del lunes que pasa a las 00:05 del martes figura como `24:05:00` con el calendario del lunes. Por eso, una consulta en la fecha D revisa los días de servicio D−1, D y D+1, y cada hora se convierte a fecha y hora reales (inicio del día de servicio más los segundos indicados). Así aparecen los trenes que cruzan la medianoche, y se pueden mostrar trenes del día siguiente cuando ya no quedan más ese día.
- **Sentido de circulación.** El campo `trip_headsign` viene vacío en el GTFS de Renfe, así que el sentido se calcula como la última parada del viaje (la de mayor `stop_sequence`). Se guarda en caché por viaje para no recalcularlo.
- **Filtro por línea.** `C4` incluye los ramales `C4a` y `C4b`, pero `C1` no incluye `C10`: solo se acepta un prefijo si lo que sigue es una letra.
- **Lectura del calendario por cabecera.** A diferencia del resto del cargador, las columnas de `calendar.txt` y `calendar_dates.txt` se localizan por el nombre de la cabecera y no por su posición, para no depender del orden de columnas de cada fuente.
- **Búsqueda de estaciones reutilizable.** La búsqueda por nombre estaba dentro de `Main`. Se ha extraído a `BuscadorEstaciones` para usarla en US-004 y en US-001, sin modificar la búsqueda original para no interferir con el trabajo de otros miembros.
- **Verificación.** Los resultados de Atocha y las líneas que paran en ella se contrastaron con un cálculo independiente hecho directamente sobre los archivos GTFS originales. Además, se revisaron consultas en Sol, Chamartín, Parla (estación terminal) y Villaverde Alto (trenes que cruzan la medianoche). De momento no hay pruebas automáticas (JUnit); quedan pendientes para un próximo sprint.




\## 3. Problemas Encontrados y Soluciones

Documentación de los errores técnicos que han surgido y cómo los ha resuelto el equipo.

**US-004**

- **No hay fecha oficial de publicación de los datos.** El GTFS de Renfe no incluye `feed_info.txt`, así que no se puede mostrar cuándo se publicaron los horarios (criterio 5). Como aproximación se muestran el periodo de validez del calendario y la fecha del archivo `stop_times.txt`, aunque esta última cambia si se copia el archivo.
- **Validez limitada de los horarios.** El GTFS descargado solo cubre del 06/10/2026 al 04/11/2026. Si se consulta una fecha fuera de ese periodo, la aplicación lo indica y recomienda descargar datos nuevos. Habrá que actualizar los datos antes de que caduquen.
- **Formatos GTFS distintos.** El GTFS del Consorcio que había en `data/` tiene otro orden de columnas que el de Renfe, y el cargador actual (que usa posiciones fijas) no lo interpreta bien. Por eso las partes nuevas leen las columnas por la cabecera.
- **Carpeta `src/Java/data` ausente al clonar.** El `.classpath` del proyecto la declara como carpeta de código, pero `.gitignore` también ignora su `.gitkeep`, así que no existe tras clonar y Eclipse da un error de compilación. Solución provisional: crearla a mano. Además, `Main` busca ahora los datos también en `data/horarios_cercanias/`, para no tener que duplicarlos.




\## 4. Retrospectiva

\* Qué ha funcionado bien en el trabajo en equipo.

\* Qué ha fallado en la coordinación.

\* Acciones de mejora para el siguiente sprint.

