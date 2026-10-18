# Sistema de Planificación de Cercanías Madrid <Nombre a elegir>

## Descripción
Aplicación para la planificación de rutas y consulta de horarios de la red de transporte. 

## Requisitos Previos
* [Lenguaje y versión exacta: ] Java 17
* [Librerías o dependencias externas necesarias: ] a ver que programas usamos Eclipse 2026 y JDK25

## Instalación y Ejecución
1. Clonar el repositorio: `git clone https://github.com/RENFE-IS-1/RENFE-IS-1`
2. Instalar dependencias: `[comando de instalación]` a ver que usamos
3. Ejecutar la aplicación: `[comando de ejecución]` depende de la versión lo cambiamos

NO hay que descargar el zip de https://data.renfe.com/dataset/horarios-cercanias y extraer los txt en la carpeta data/horarios_cercanias/ y en
src/Java/data/
Recordad hacer refresh en la carpeta mas exterior al hacer pull para que se cargen los archivos
Crear una carpeta vacia (ej. Workspace), clonar el repositorio dentro, y hacer new > Java proyect > desmarcar la casilla default location y elegir src/Java
y desmarcar create mmodulo info java (esta abajo)

He cambiado que ya no hace falta descargar el zip manualmente ni nada pues se descarga automaticamente como el resto del repo. La unica diferencia es que la repo guarda un puntero al txt en vez del txt como tal.

De todas formas solo podemos utilzar el lfs con un maximo de 1 GB de descarga y como el txt ya pesa como 400 Mb solo podemos hacer dos clonaciones por mes. En principio los git pull y git push no deberian afectar o sumar a este maximo. Si no siempre se puede quitar.


## Estructura del Proyecto
* `/src`: Código fuente de la aplicación.
* `/data`: Archivos de datos estáticos (horarios, estaciones, conexiones...)
* `/docs`: Documentación adicional y memorias.

## Autores
* Íñigo Cueto Hernández
* Rodrigo Antequera Díaz
* Diego Bañares Díez
* Pedro Vicente Polo
* Sergio Alcalá Cervantes
