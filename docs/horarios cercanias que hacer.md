# Diccionario de Datos GTFS - Cercanías

A continuación se detalla el contenido y la función de cada archivo dentro del paquete de datos estáticos GTFS.

## Archivos Principales

* **agency_2.txt:** Define la entidad operadora del servicio de transporte, proporcionando su configuración de zona horaria y datos de contacto general.
* **calendar_2.txt:** Establece el calendario de funcionamiento de los servicios indicando con "1" o "0" qué días de la semana operan. Define además la fecha de inicio y la fecha de fin de validez para cada identificador de servicio (`service_id`).
* **routes_2.txt:** Contiene el listado de las líneas comerciales de la red. Detalla el identificador, el nombre corto de la línea y el nombre largo que describe el recorrido principal.
* **shapes_2.txt:** Almacena la trayectoria geográfica que siguen los trenes mediante una sucesión ordenada de puntos. Incluye las coordenadas de latitud y longitud junto con la secuencia lógica necesaria para trazar el recorrido exacto en un mapa.
* **stops.txt:** Es el catálogo general de las estaciones o paradas físicas. Proporciona el identificador único, el nombre de la estación, sus coordenadas geográficas exactas y detalles técnicos sobre accesibilidad.
* **transfers.txt:** Estipula las reglas operativas y los tiempos de conexión para realizar transbordos entre diferentes trayectos o líneas. Define el tiempo mínimo en segundos requerido para que un viajero cambie de tren de forma viable en una estación.
* **trips.txt:** Define cada viaje individual de un tren conectando una línea comercial (`route_id`) con su calendario de operación (`service_id`). Asigna a cada recorrido un identificador único (`trip_id`) y lo vincula con la forma geográfica (`shape_id`) correspondiente a su trayecto.
* **stop_times.txt:** Cruza los viajes individuales con las estaciones físicas. Utiliza el `trip_id` para listar cronológicamente cada parada (`stop_id`) que hace un tren, estableciendo su hora de llegada (`arrival_time`), su hora de salida (`departure_time`) y el número de orden que ocupa esa estación dentro del trayecto completo (`stop_sequence`).

## Lógica Relacional para el Cálculo de Rutas

Para construir el algoritmo central de búsqueda de rutas, la lógica relacional consiste en encadenar estas tablas:

1. El usuario introduce un origen y destino que el programa buscará en **stops.txt**.
2. Utilizando esos identificadores de estación, el código rastreará en **stop_times.txt** los viajes (`trip_id`) que se detienen en ambas paradas en el orden temporal y secuencial correcto.
3. A continuación, cruzará ese viaje con **trips.txt** para saber a qué línea exacta pertenece (`route_id`).
4. Finalmente, consultará **calendar_2.txt** para verificar que el servicio está operativo en el día de la semana solicitado.