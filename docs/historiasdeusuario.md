# Historias de usuario — Plataforma de movilidad de Madrid

**Versión:** 0.2  
**Estado:** Borrador inicial  
**Fecha:** 2026-09-29

## 1. Objetivo del documento

Este documento recoge las primeras ideas de funcionalidades para una aplicación de planificación de rutas y análisis de la movilidad en Madrid.

El alcance del proyecto todavía no está definido. Las historias de usuario son propuestas iniciales que podrán modificarse, dividirse, eliminarse o ampliarse durante el desarrollo.

No se presupone que todas las fuentes de datos necesarias estén disponibles ni que todas las funcionalidades sean técnicamente viables en su forma actual.

## 2. Actores principales

- **Viajero:** persona que consulta rutas y horarios para desplazarse.
- **Gestor de la red:** persona autorizada para añadir o modificar líneas, estaciones y paradas.
- **Analista de movilidad:** persona que estudia los desplazamientos y evalúa posibles mejoras de la red.
- **Sistema externo:** API o fuente de datos que proporciona información sobre la red y el servicio de transporte.
  // en cuanto a sistemas externos yo queria incluir la API de la EMT y si pudiesemos añadir algo similar a maps para que puedas elegir destinos que no sean necesariamente estaciones y el sistema encuentre la estacion mas cercana.

## 3. Épicas

- **EP-01:** Planificación de rutas entre ubicaciones.
- **EP-02:** Integración con fuentes externas de transporte.
- **EP-03:** Consulta de llegadas y horarios.
- **EP-04:** Administración de la red de transporte.
- **EP-05:** Análisis de los patrones de movilidad.
- **EP-06:** Evaluación de mejoras y zonas mal comunicadas.
- **EP-07:** Seguimiento en tiempo real.
- **EP-08:** Cuentas de usuario.
- **EP-09:** Alertas de incidencias.

---

## EP-01 — Planificación de rutas

### US-001 — Encontrar una ruta entre dos ubicaciones

**Historia de usuario**

Como viajero, quiero indicar un origen y un destino para conocer cómo desplazarme entre ambos utilizando los medios de transporte registrados en la aplicación.

**Criterios de aceptación iniciales**

1. El usuario puede seleccionar o introducir un origen y un destino.
2. El origen y el destino pueden ser ubicaciones arbitrarias, no necesariamente estaciones.
3. El sistema identifica las paradas o estaciones próximas a las ubicaciones de origen y destino.
4. El sistema calcula rutas utilizando las líneas, paradas, conexiones y servicios disponibles en la base de datos.
5. La ruta contempla, cuando corresponda, los desplazamientos a pie hasta las paradas de acceso y desde las paradas finales hasta el destino.
6. Si no se encuentra una ruta válida, el sistema informa al usuario.
7. Se muestran los principales pasos necesarios para completar el trayecto.

**Cuestiones pendientes**

- ¿Cómo se representarán las ubicaciones: coordenadas, direcciones, puntos seleccionados en un mapa u otros métodos?
- ¿Qué distancia máxima a pie se permitirá hasta una estación?
- ¿Se incluirán exclusivamente Metro y Cercanías o también otros transportes?
- ¿Qué algoritmo de búsqueda de rutas se utilizará?

### US-002 — Comparar rutas alternativas

**Historia de usuario**

Como viajero, quiero comparar distintas rutas entre un origen y un destino para elegir la que mejor se adapte a mis necesidades.

**Criterios de aceptación iniciales**

1. El sistema puede mostrar varias alternativas cuando existan y sus respectivas ventajas(solo utiliza metro, solo tren, no hay que moverse caminando entre paradas, menos intercambios, menor precio).
2. Las alternativas incluyen una estimación de la duración total del viaje.
3. Se muestran los transbordos y los desplazamientos a pie.
4. Cuando los datos estén disponibles, se tienen en cuenta los tiempos de espera y las incidencias.
5. El usuario puede comparar las rutas según criterios como duración, número de transbordos o distancia a pie.
6. Se indica qué información es estimada y qué información procede de datos observados o actualizados.

**Cuestiones pendientes**

- ¿Qué criterios de comparación son prioritarios?
- ¿Se calculará la ruta más rápida, la que tenga menos transbordos o varias alternativas?
- ¿Se tendrá en cuenta el coste del viaje?

### US-010 — Calcular rutas según los horarios del servicio *(técnica)*

**Historia de usuario**

Como responsable del desarrollo, quiero un algoritmo de cálculo de rutas que tenga en cuenta los horarios planificados de cada servicio para obtener viajes realizables a una hora concreta, y no solo tiempos medios.

**Criterios de aceptación iniciales**

1. Dados un origen, un destino y una hora de salida, el sistema devuelve el itinerario con la llegada más temprana utilizando los horarios planificados almacenados.
2. Solo se consideran los servicios que circulan el día de la consulta, respetando los calendarios de laborables, fines de semana y festivos.
3. Los transbordos respetan un tiempo mínimo configurable entre la llegada de un servicio y la salida del siguiente.
4. El algoritmo puede trabajar con horarios de varias fuentes (Cercanías, Metro, EMT) cuando estén integrados en el modelo interno.
5. El cálculo responde en menos de 2 segundos para cualquier par de estaciones de la red.
6. Existen pruebas automáticas con al menos 5 trayectos conocidos cuyo resultado se ha verificado manualmente con los horarios oficiales.

**Cuestiones pendientes**

- ¿Qué algoritmo se utilizará: Connection Scan Algorithm, RAPTOR, Dijkstra dependiente del tiempo u otro?
- ¿Cómo se integran los tramos a pie de US-001 (acceso a la primera parada y salida desde la última)?
- ¿Qué tiempo mínimo de transbordo se usará por defecto? ¿Dependerá de la estación?
- Metro y parte de la EMT funcionan por frecuencias más que por horarios exactos: ¿cómo se modelarán?

**Dependencias:** US-003.

### US-011 — Planificar un viaje a una hora determinada

**Historia de usuario**

Como viajero, quiero indicar a qué hora quiero salir o llegar para saber qué servicios concretos debo coger.

**Criterios de aceptación iniciales**

1. El usuario puede elegir entre "salir a partir de" y "llegar antes de", e indicar fecha y hora. Por defecto, se planifica saliendo en el momento actual.
2. El origen y el destino se seleccionan de la misma forma que en US-001.
3. En el modo "salir a partir de", se muestran hasta 3 opciones ordenadas por hora de llegada.
4. En el modo "llegar antes de", se muestran hasta 3 opciones que llegan antes de la hora indicada, ordenadas de la salida más tardía a la más temprana.
5. Cada opción muestra la hora de salida y de llegada, los servicios utilizados, los transbordos, los tiempos de espera y los tramos a pie.
6. Si no hay servicio en la franja solicitada (por ejemplo, de madrugada), se informa al usuario y se propone el primer servicio disponible.
7. Se indica que las horas proceden de horarios planificados y pueden variar por retrasos.

**Cuestiones pendientes**

- ¿Con cuánta antelación se podrá planificar? Depende del periodo de validez de los horarios de cada fuente.
- ¿Se incorporarán los retrasos en tiempo real a la estimación? (Relacionado con el criterio 4 de US-002.)
- ¿La planificación por hora y la comparación de alternativas de US-002 estarán en la misma pantalla o en pantallas distintas?

**Dependencias:** US-001, US-010.

---

## EP-02 — Integración con fuentes externas

### US-003 — Obtener datos de servicios externos de transporte

**Historia de usuario**

Como responsable del desarrollo, quiero obtener información de las API y otras fuentes de datos de transporte para utilizarla en la planificación de rutas y en las consultas de servicio.

**Criterios de aceptación iniciales**

1. La aplicación puede consultar las fuentes externas que se hayan identificado y cuya utilización esté permitida.
2. Se identifica qué información proporciona cada fuente.
3. Los datos recibidos se transforman a un formato compatible con el modelo interno de la aplicación cuando sea necesario.
4. Se gestionan los errores de conexión, las respuestas inválidas y la ausencia de datos.
5. Se registra la fecha de actualización de los datos relevantes.
6. Se distingue entre información estática, horarios planificados e información en tiempo real.
7. La aplicación puede seguir utilizando los datos locales disponibles cuando una fuente externa no responda, indicando las limitaciones correspondientes.

**Cuestiones pendientes**

- ¿Qué API oficiales están disponibles para Metro de Madrid y Cercanías?
- ¿La información incluye recorridos, horarios, llegadas en tiempo real, incidencias u ocupación?
- ¿Qué condiciones de acceso, límites de uso y licencias tiene cada fuente?
- ¿Qué información conviene almacenar localmente y cuál consultar bajo demanda?

---

## EP-03 — Consulta de llegadas y horarios

### US-004 — Consultar las próximas llegadas a una estación

**Historia de usuario**

Como viajero, quiero consultar cuándo llegan los próximos trenes o metros a una estación para decidir cuándo desplazarme y qué servicio utilizar.

**Criterios de aceptación iniciales**

1. El usuario puede seleccionar una estación o parada.
2. Se muestran los servicios que pasan por ella y cuya información esté disponible.
3. Se indica la hora prevista de llegada de los siguientes servicios.
4. Se identifica la línea y el sentido de circulación cuando estos datos estén disponibles.
5. Se indica la fecha y hora de actualización de la información.
6. Si solo se dispone de horarios planificados, se presentan como tales y no como predicciones en tiempo real.
7. Si no hay información disponible, se informa al usuario.

**Cuestiones pendientes**

- ¿Se mostrarán las próximas llegadas de todas las líneas o solo de una seleccionada?
- ¿Cómo se tratarán los retrasos y las cancelaciones?
- ¿Con qué frecuencia se actualizarán los datos?

---

## EP-04 — Administración de la red de transporte

### US-005 — Añadir líneas y estaciones

**Historia de usuario**

Como gestor de la red, quiero añadir líneas y estaciones a la base de datos para representar nuevos servicios o ampliar la red existente.

**Criterios de aceptación iniciales**

1. Los usuarios autorizados pueden crear líneas y estaciones.
2. Cada elemento tiene un identificador y los atributos necesarios para describirlo.
3. Se pueden asociar estaciones a las líneas correspondientes.
4. Se comprueba que las referencias entre elementos sean válidas.
5. Se detectan posibles identificadores duplicados y datos incompletos.
6. Los cambios pueden revisarse antes de publicarse.

**Cuestiones pendientes**

- ¿Qué atributos tendrá una línea o estación?
- ¿Cómo se representarán las líneas con distintos ramales?
- ¿Cómo se distinguirán una estación física, una parada de servicio y un punto de intercambio?

### US-006 — Añadir y modificar paradas y conexiones

**Historia de usuario**

Como gestor de la red, quiero añadir, modificar y relacionar paradas para mantener actualizado el modelo de la red de transporte.

**Criterios de aceptación iniciales**

1. Se pueden registrar las paradas y su ubicación geográfica.
2. Se puede establecer el orden de las paradas de una línea.
3. Se pueden representar conexiones entre líneas y servicios.
4. Se pueden modificar o retirar elementos existentes.
5. Antes de publicar un cambio se comprueba si afecta a rutas o conexiones dependientes.
6. Los cambios quedan registrados para poder identificar qué se modificó y cuándo.

**Cuestiones pendientes**

- ¿Se permitirá que un gestor cree una red hipotética además de modificar una red existente?
- ¿Cómo se validará la correspondencia entre la base de datos y la red real?
- ¿Cómo se conservarán las versiones anteriores de la red?

---

## EP-05 — Análisis de los patrones de movilidad

### US-007 — Analizar los desplazamientos entre ubicaciones

**Historia de usuario**

Como analista de movilidad, quiero conocer entre qué zonas se desplazan las personas y cuánto tardan en realizar sus viajes para comprender los patrones de movilidad.

**Criterios de aceptación iniciales**

1. El sistema puede trabajar con datos de desplazamientos procedentes de fuentes autorizadas.
2. Se pueden analizar los orígenes y destinos de los viajes mediante ubicaciones geográficas o zonas agregadas.
3. Se pueden calcular estadísticas de duración para los desplazamientos que dispongan de información temporal suficiente.
4. Se pueden agrupar los resultados por zona, periodo temporal u otros criterios disponibles.
5. Se indica el tamaño de la muestra, el periodo analizado y las limitaciones de los datos.
6. Se distingue entre los tiempos realmente observados y los estimados por un modelo.
7. El tratamiento de los datos respeta las restricciones de privacidad aplicables.

**Cuestiones pendientes**

- ¿Qué fuentes permiten conocer los orígenes y destinos de los viajeros(yo diria que cada vez que alguien utilice el buscador de origen/destino se guarde la busqueda)?
- ¿Los datos incluirán coordenadas, zonas agregadas, tiempos de entrada y salida u otras variables?
- ¿Cómo se obtendrán los tiempos de viaje puerta a puerta?
- ¿Qué tamaño de muestra será suficiente para realizar comparaciones fiables?

---

## EP-06 — Evaluación de mejoras de la red

### US-008 — Identificar zonas con mala comunicación

**Historia de usuario**

Como analista de movilidad, quiero identificar zonas con pocas opciones de transporte público o con tiempos de acceso y desplazamiento elevados para estudiar posibles mejoras de conectividad.

**Criterios de aceptación iniciales**

1. El sistema permite analizar una zona geográfica o un conjunto de ubicaciones.
2. Se pueden calcular indicadores de accesibilidad utilizando la red disponible.
3. Se pueden estimar los tiempos necesarios para alcanzar estaciones o destinos de interés cuando existan datos suficientes.
4. Se identifican las zonas que cumplen determinados criterios de baja accesibilidad.
5. Se pueden comparar los resultados entre zonas y franjas temporales.
6. Se documentan los indicadores, umbrales y supuestos empleados.
7. Los resultados se presentan como análisis basados en criterios explícitos, no como una clasificación universal de las zonas.

**Cuestiones pendientes**

- ¿Qué significa exactamente que una zona esté mal comunicada?
- ¿Se utilizarán tiempos de acceso a estaciones, duración de los viajes, frecuencia del servicio o combinación de indicadores?
- ¿Se tendrá en cuenta la distribución de la población y de los principales destinos?
- ¿Cómo se representarán los resultados en el mapa?

### US-009 — Proponer nuevas paradas o líneas

**Historia de usuario**

Como analista de movilidad, quiero estudiar posibles nuevas paradas o líneas para evaluar si podrían reducir los tiempos de desplazamiento y mejorar la accesibilidad.

**Criterios de aceptación iniciales**

1. El sistema permite definir escenarios hipotéticos de ampliación de la red.
2. Se pueden añadir paradas y conexiones propuestas sin alterar la red actualmente publicada.
3. Se calculan las rutas y los indicadores de accesibilidad del escenario cuando el modelo y los datos lo permiten.
4. Se comparan los resultados con los de la red de referencia.
5. Se identifican las zonas y los desplazamientos que podrían beneficiarse de cada escenario.
6. Se explicitan los supuestos, las limitaciones y las incertidumbres del análisis.
7. Las propuestas se presentan como alternativas para evaluar, no como decisiones automáticas de construcción.

**Cuestiones pendientes**

- ¿Qué objetivo se quiere minimizar: tiempo medio, tiempo total, tiempo máximo o alguna combinación?
- ¿Cómo se estimará la demanda de una línea que todavía no existe?
- ¿Se tendrán en cuenta costes, capacidad, viabilidad técnica e impacto ambiental?
- ¿Cómo se evitará mejorar una zona a costa de empeorar otras?

---

## EP-07 — Seguimiento en tiempo real

### US-012 — Ver la posición de los trenes en tiempo real en un mapa

**Historia de usuario**

Como viajero, quiero ver en un mapa la red de transporte y la posición actual de los trenes para saber si me da tiempo a llegar a la estación y cómo está funcionando el servicio.

**Criterios de aceptación iniciales**

1. El mapa muestra las líneas incluidas en la aplicación, con su color oficial, y sus estaciones.
2. Al seleccionar una estación, se muestran su nombre y las líneas que pasan por ella.
3. En las líneas cuya fuente de datos ofrezca posiciones en tiempo real, cada tren aparece en su posición según el último dato recibido.
4. Las posiciones se actualizan sin recargar la página, con un intervalo máximo de 30 segundos.
5. Si los datos tienen más de 2 minutos de antigüedad, se muestra un aviso visible de datos no actualizados con la hora de la última actualización correcta.
6. Las líneas sin datos de posición en tiempo real se identifican como tales en la leyenda del mapa.
7. El mapa es interactivo en menos de 3 segundos con una conexión estándar y se puede utilizar en pantallas de 360 px de ancho.

**Cuestiones pendientes**

- ¿Qué fuentes ofrecen posiciones en tiempo real? Renfe publica GTFS-Realtime para Cercanías; en Metro no consta un feed público de posiciones; en la EMT hay que verificarlo.
- ¿Se mostrarán también los autobuses de la EMT o solo los trenes?
- ¿Al seleccionar un tren se mostrará su detalle (destino, próxima parada, retraso)?
- ¿Se podrá filtrar el mapa por línea?

**Dependencias:** US-003.

---

## EP-08 — Cuentas de usuario

### US-013 — Registrarse e iniciar sesión

**Historia de usuario**

Como viajero, quiero crear una cuenta e iniciar sesión para conservar mis datos y preferencias entre sesiones.

**Criterios de aceptación iniciales**

1. El usuario puede registrarse con un email no registrado previamente y una contraseña de al menos 8 caracteres.
2. La contraseña se almacena mediante un algoritmo de hash seguro, nunca en texto claro.
3. Si las credenciales son incorrectas, se muestra un error sin indicar si falla el email o la contraseña.
4. El usuario puede cerrar sesión, y la sesión queda invalidada.
5. Cada cuenta tiene un rol (viajero, gestor de la red o analista de movilidad), y solo los roles autorizados acceden a las funciones de EP-04, EP-05 y EP-06.
6. Las cuentas de gestor y de analista no pueden crearse desde el registro público.
7. Al registrarse, el usuario acepta una política de privacidad que indica qué datos se guardan y con qué finalidad.
8. El usuario puede eliminar su cuenta junto con sus datos asociados.

**Cuestiones pendientes**

- ¿Cómo se darán de alta los gestores y los analistas?
- ¿Se ofrecerá recuperación de contraseña?
- ¿Qué funciones podrán usarse sin cuenta? En principio, la consulta de rutas y llegadas debería ser pública.
- Si se guardan las búsquedas para US-007, ¿se asociarán a la cuenta o se anonimizarán?

**Dependencias:** ninguna.

### US-014 — Guardar estaciones favoritas

**Historia de usuario**

Como viajero, quiero marcar estaciones o paradas como favoritas para consultar sus próximas llegadas rápidamente.

**Criterios de aceptación iniciales**

1. Con la sesión iniciada, el usuario puede marcar una estación o parada como favorita desde su ficha o desde el mapa.
2. Las estaciones favoritas aparecen en el panel personal del usuario.
3. Al seleccionar una favorita, se abre la consulta de sus próximas llegadas (US-004).
4. El usuario puede desmarcar una favorita, y esta desaparece del panel.
5. Si el usuario no ha iniciado sesión e intenta marcar una favorita, se le invita a iniciar sesión.

**Cuestiones pendientes**

- ¿Habrá un número máximo de favoritas?
- ¿Se podrá guardar una línea o un sentido concreto dentro de una estación favorita?

**Dependencias:** US-004, US-013.

### US-015 — Guardar rutas

**Historia de usuario**

Como viajero, quiero guardar las rutas que hago habitualmente para no tener que introducirlas cada vez.

**Criterios de aceptación iniciales**

1. Con la sesión iniciada, el usuario puede guardar una ruta calculada asignándole un nombre (por ejemplo, "Casa - Universidad").
2. Se guardan el origen, el destino y los criterios de búsqueda utilizados, no el resultado del cálculo.
3. Al seleccionar una ruta guardada, se recalcula con los datos actuales.
4. El usuario puede renombrar y eliminar sus rutas guardadas.

**Cuestiones pendientes**

- ¿Se podrá asociar una hora habitual de viaje a una ruta guardada, para planificarla con US-011?
- ¿Se usarán las rutas guardadas para sugerir líneas frecuentes en US-016?

**Dependencias:** US-001, US-013.

---

## EP-09 — Alertas de incidencias

### US-016 — Suscribirse a avisos de líneas frecuentes

**Historia de usuario**

Como viajero, quiero indicar qué líneas utilizo con frecuencia para recibir avisos solo de las incidencias que me afectan.

**Criterios de aceptación iniciales**

1. Con la sesión iniciada, el usuario puede seleccionar una o varias líneas como frecuentes.
2. Para activar los avisos por email, el usuario debe dar su consentimiento explícito; sin él no se envía ningún email.
3. El usuario puede modificar sus líneas o desactivar los avisos en cualquier momento.
4. El panel personal muestra las líneas suscritas y si los avisos están activos.

**Cuestiones pendientes**

- ¿Las líneas frecuentes se eligen manualmente o se sugieren a partir de las rutas guardadas?
- ¿Se podrán limitar los avisos a franjas horarias (por ejemplo, solo días laborables de 7:00 a 9:00)?

**Dependencias:** US-013.

### US-017 — Detectar incidencias en las líneas *(técnica)*

**Historia de usuario**

Como responsable del desarrollo, quiero detectar automáticamente las incidencias de cada línea para poder notificarlas a los usuarios afectados.

**Criterios de aceptación iniciales**

1. Los avisos oficiales de incidencias publicados por las fuentes externas se registran como incidencias de las líneas afectadas.
2. Cuando al menos 3 trenes de una misma línea acumulan un retraso de 10 minutos o más, se registra una incidencia por retraso.
3. Una incidencia ya registrada que se sigue detectando no genera un registro nuevo.
4. Cuando una incidencia deja de detectarse, se marca como finalizada con su hora de fin.
5. Cada incidencia guarda la línea, el tipo, la descripción, el origen (aviso oficial o detección propia) y las horas de inicio y fin.

**Cuestiones pendientes**

- ¿Qué umbrales definitivos se usarán para considerar que hay una incidencia por retraso?
- ¿Qué fuentes publican avisos de incidencias (Renfe, Metro, EMT) y en qué formato?
- ¿Se aprovechará el histórico de incidencias en los análisis de EP-05 y EP-06?

**Dependencias:** US-003.

### US-018 — Recibir un email cuando una línea frecuente tiene una incidencia

**Historia de usuario**

Como viajero, quiero recibir un email cuando una de mis líneas frecuentes sufra una incidencia para buscar alternativas a tiempo.

**Criterios de aceptación iniciales**

1. Cuando se registra una incidencia nueva en una línea suscrita con avisos activos, se envía un email en menos de 10 minutos.
2. El email indica la línea afectada, el tipo de incidencia, la descripción y la hora de inicio.
3. No se envía más de un email por incidencia y usuario.
4. Cada email incluye un enlace para darse de baja sin necesidad de iniciar sesión.
5. Los fallos de envío se registran y se reintentan hasta 3 veces.

**Cuestiones pendientes**

- ¿Qué servicio de envío de emails se utilizará y qué límites tiene su plan gratuito?
- ¿Se avisará también cuando la incidencia finalice?
- ¿Se agruparán en un solo email varias incidencias simultáneas?

**Dependencias:** US-016, US-017.

---

##. Registro de cambios

| Versión | Fecha | Descripción |
|---|---|---|
| 0.1 | 2026-09-29 | Creación del borrador inicial |
| 0.2 | 2026-09-29 | Añadidas US-010 a US-018 y las épicas EP-07 a EP-09: planificación por hora, mapa en tiempo real, cuentas de usuario y alertas de incidencias |
