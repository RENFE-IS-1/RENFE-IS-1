# Sistema de Planificación de Cercanías Madrid <Nombre a elegir>

## Descripción
Aplicación para la planificación de rutas y consulta de horarios de la red de transporte. Análisis de datos y mejoras del servicio.

## Requisitos Previos
* [Lenguaje y versión exacta: ] Java 17
* [Librerías o dependencias externas necesarias: ] a ver que programas usamos Eclipse 2026 y JDK25

## Instalación y Ejecución
1. Clonar el repositorio: `git clone https://github.com/RENFE-IS-1/RENFE-IS-1`
2. Instalar dependencias: `[comando de instalación]` a ver que usamos
3. Ejecutar la aplicación: `[comando de ejecución]` depende de la versión lo cambiamos

A medida que se escribe el código, ir comentando su funcionamiento, creando tests y actualizando la gestión de riesgos.

Luego esto ya lo formateais como querais, tampoco se hasta que punto vosotros os vais a tener que instalar nada.

He añadido pruebas automatizadas para la búsqueda de estaciones en el backend API. En mi equipo se ejecutan correctamente con `mvnw.cmd test` desde `files/backendAPI`.

Para ejecutarlas hace falta un **JDK 17**. No es necesario instalar Maven por separado porque el repositorio incluye Maven Wrapper (`mvnw.cmd`), que descarga y usa la versión de Maven del proyecto. La primera ejecución puede tardar porque descarga Maven y las dependencias.

Las pruebas usan estaciones de ejemplo creadas dentro del propio test, así que no necesitan descargar los horarios reales. Comprueban búsquedas sin distinguir mayúsculas ni tildes, texto vacío y ausencia de coincidencias.

Para ejecutarlas desde Eclipse, importad `files/backendAPI` como proyecto Maven y dejad que resuelva las dependencias. Eclipse IDE for Java Developers incluye soporte Maven; en otras ediciones puede hacer falta instalar Maven Integration for Eclipse (m2e). Esta configuración solo afecta a Eclipse, no al código ni a los requisitos de los tests.

Nota: para ejecutar los tests desde Eclipse, hay que abrir el proyecto de forma normal (ver sección de Spring Boot) y en la carpeta `src/test/java` hacer click derecho y hacer `Run as: Maven test`. Tarda un poco, ejecuta Spring Boot y al final saldrá el número de tests realizados y los errores.

-- OMITIR --

Ya NO hay que descargar el zip de https://data.renfe.com/dataset/horarios-cercanias: los txt están en `files/backendAPI/data/`.
Recordad hacer refresh en la carpeta mas exterior al hacer pull para que se cargen los archivos

------------

He cambiado que ya no hace falta descargar el zip manualmente ni nada pues se descarga automaticamente como el resto del repo. La unica diferencia es que el repositorio guarda un puntero al txt en vez del txt como tal.

De todas formas solo podemos utilzar el lfs con un maximo de 1 GB de descarga y como el txt ya pesa como 400 Mb solo podemos hacer dos clonaciones por mes. En principio los git pull y git push no deberian afectar o sumar a este maximo. Si no siempre se puede quitar.


## Backend (API REST con Spring Boot)
El proyecto `files/backendAPI` expone nuestras clases como una API web y sirve la web. Lee los horarios GTFS de `files/backendAPI/data` (se cambia con `renfe.datos` en `application.properties`).

1. En Eclipse: **File → Import… → Maven → Existing Maven Projects** → `files/backendAPI` → **Finish**. La primera vez descarga las dependencias (un par de minutos).
2. Abre `com.backendapi.BackendApiApplication` → clic derecho → **Run As → Java Application** (o **Spring Boot App** si tienes Spring Tools).
3. Cuando la consola diga `Started BackendApiApplication`, abre http://localhost:8080/swagger-ui.html para probar los endpoints.
4. Si quieres meterte en la web visual pensada para el usuario, abre http://localhost:8080/ en su lugar.

Todo el código está en `files/backendAPI/src/main/java` (`modelo`, `consultas`, `servicio`, etc.). La versión antigua de terminal (`files/Java`) se ha quitado del repositorio; se puede recuperar desde la etiqueta `version-terminal`. El diagrama `diagrama-clases-java` corresponde a esa versión.

## Estructura del Proyecto (actualizar con el mapa UML)
* `files/backendAPI/src/main/java`: código fuente de la aplicación (API REST con Spring Boot).
* `files/backendAPI/src/main/resources/static`: web (`index.html`) y mapa de Cercanías.
* `files/backendAPI/src/test`: pruebas JUnit.
* `files/backendAPI/data`: horarios GTFS de Renfe (`stop_times.txt` va con Git LFS).
* `/docs`: Documentación adicional y memorias.

## Autores
* Íñigo Cueto Hernández
* Rodrigo Antequera Díaz
* Diego Bañares Díez
* Pedro Vicente Polo
* Sergio Vazquez Fernandez
  
