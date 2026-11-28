package com.backendapi.servicio;

import com.backendapi.modelo.DeclaracionIncidencia;
import com.backendapi.modelo.Incidencia;
import com.backendapi.modelo.OrigenIncidencia;
import com.backendapi.modelo.RolUsuario;
import com.backendapi.modelo.TipoIncidenciaUsuario;
import com.backendapi.modelo.Usuario;
import com.backendapi.repositorio.DeclaracionIncidenciaRepository;
import com.backendapi.repositorio.IncidenciaRepository;
import modelo.AvisoOficial;
import modelo.Linea;
import modelo.Viaje;
import repositorio.RedTransporte;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detección de incidencias en las líneas (US-017).
 *
 * Parte 1: detección propia por acumulación de retrasos. Cuando al menos MIN_TRENES_RETRASADOS
 * trenes de una misma línea llevan MIN_RETRASO_SEGUNDOS o más de retraso, se registra una
 * incidencia por retraso en esa línea (criterio 2). Si se sigue detectando no se duplica
 * (criterio 3) y, cuando deja de detectarse, se cierra con su hora de fin (criterio 4).
 *
 * Parte 2: avisos oficiales de Renfe. Cada aviso del feed de alertas se registra como incidencia
 * de cada línea afectada (criterio 1), sin duplicarse, y se cierra cuando sale del feed.
 *
 * Incidencias declaradas por viajeros: un VIAJERO declara una incidencia en una línea eligiendo un
 * tipo de una lista cerrada. Las declaraciones de varios viajeros sobre la misma línea y el mismo
 * tipo se agrupan en una sola incidencia, cuyo nivel de confianza sube con cada viajero distinto.
 * Estas incidencias las cierra un GESTOR.
 */
@Service
public class ServicioIncidencias {

    /** Número mínimo de trenes retrasados en una línea para registrar una incidencia (criterio 2). */
    static final int MIN_TRENES_RETRASADOS = 3;

    /** Retraso mínimo de un tren, en segundos, para contarlo como retrasado: 10 minutos (criterio 2). */
    static final int MIN_RETRASO_SEGUNDOS = 600;

    /** Tipo que se guarda en las incidencias detectadas por retrasos. */
    static final String TIPO_RETRASO = "RETRASO";

    /** Nombre comercial de una línea de Cercanías dentro de un trip_id, p. ej. "C3" o "C4a". */
    private static final Pattern PATRON_LINEA = Pattern.compile("(C\\d+[a-z]?)", Pattern.CASE_INSENSITIVE);

    /** Tipo que se guarda en las incidencias de avisos oficiales: el feed de Renfe no manda título. */
    static final String TIPO_AVISO_OFICIAL = "AVISO_OFICIAL";

    /** Longitud máxima de la descripción de una incidencia: la misma que su columna en Incidencia. */
    static final int MAX_DESCRIPCION = 2000;

    /** Hashtags del principio de los avisos de Renfe, p. ej. "#MadC3 #MadC4 "; la línea ya se guarda aparte. */
    private static final Pattern HASHTAGS_INICIALES = Pattern.compile("^(\\s*#\\S+)+\\s*");

    /** Zona horaria de los avisos: Renfe da la hora de inicio en segundos UTC y se guarda en hora de Madrid. */
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Longitudes máximas de los textos del viajero: las mismas que las columnas de DeclaracionIncidencia. */
    static final int MAX_ESPECIFICACION = 200;
    static final int MAX_COMENTARIO = 1000;

    private final IncidenciaRepository repositorio;
    private final DeclaracionIncidenciaRepository declaraciones;
    private final RedTransporte red;

    /**
     * Recibe los repositorios de incidencias y de declaraciones y la red con las líneas y los
     * retrasos en tiempo real (Spring los inyecta).
     */
    public ServicioIncidencias(IncidenciaRepository repositorio, DeclaracionIncidenciaRepository declaraciones,
                               RedTransporte red) {
        this.repositorio = repositorio;
        this.declaraciones = declaraciones;
        this.red = red;
    }

    /**
     * Revisa los retrasos de la última descarga y crea, actualiza o cierra las incidencias por retraso.
     * Lo necesitamos para cumplir los criterios 2, 3 y 4 de US-017; se llamará en cada actualización
     * de los datos en tiempo real.
     *
     * Si la última descarga de retrasos ha fallado no se cambia nada: no saber si hay retrasos no
     * significa que la incidencia haya terminado, y no queremos cerrarla por un fallo de la API.
     *
     * @param ahora momento de la revisión; se guarda como hora de inicio, de actualización o de fin
     */
    @Transactional
    public void detectarIncidenciasPorRetraso(LocalDateTime ahora) {
        if (!red.isHorariosTiempoRealActivos()) return;

        Map<String, Integer> retrasadosPorLinea = contarTrenesRetrasadosPorLinea();

        // 1. Crear o actualizar la incidencia de cada línea que cumple la condición.
        for (Map.Entry<String, Integer> entrada : retrasadosPorLinea.entrySet()) {
            String linea = entrada.getKey();
            int trenes = entrada.getValue();
            if (trenes < MIN_TRENES_RETRASADOS) continue;

            String descripcion = descripcionRetraso(trenes);
            Optional<Incidencia> existente =
                    repositorio.findByLineaAndOrigenAndFinIsNull(linea, OrigenIncidencia.DETECCION_PROPIA);
            if (existente.isPresent()) {
                Incidencia incidencia = existente.get();
                incidencia.registrarNuevaDeteccion(ahora, descripcion);
                repositorio.save(incidencia);
            } else {
                repositorio.save(new Incidencia(linea, TIPO_RETRASO, descripcion,
                        OrigenIncidencia.DETECCION_PROPIA, null, ahora));
            }
        }

        // 2. Cerrar las incidencias por retraso activas cuya línea ya no cumple la condición.
        List<Incidencia> activas = repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.DETECCION_PROPIA);
        for (Incidencia incidencia : activas) {
            int trenes = retrasadosPorLinea.getOrDefault(incidencia.getLinea(), 0);
            if (trenes < MIN_TRENES_RETRASADOS) {
                incidencia.finalizar(ahora);
                repositorio.save(incidencia);
            }
        }
    }

    /**
     * Cuenta, para cada línea, cuántos trenes llevan MIN_RETRASO_SEGUNDOS o más de retraso.
     * Lo necesitamos para decidir en qué líneas hay una incidencia por retraso.
     * El retraso de cada tren es el de su próxima parada con datos (getRetrasoViajeSegundos).
     * Los trenes cuya línea no se puede averiguar se ignoran.
     */
    private Map<String, Integer> contarTrenesRetrasadosPorLinea() {
        Map<String, Integer> contador = new HashMap<>();
        for (String tripId : red.getViajesConRetraso()) {
            if (red.getRetrasoViajeSegundos(tripId) < MIN_RETRASO_SEGUNDOS) continue;
            String linea = lineaDeViaje(tripId);
            if (linea != null) contador.merge(linea, 1, Integer::sum);
        }
        return contador;
    }

    /**
     * Averigua la línea (p. ej. "C3") de un tren a partir de su trip_id.
     * Lo necesitamos para agrupar por línea los trenes retrasados.
     * Primero se busca el viaje en los horarios GTFS cargados; si Renfe usa un trip_id que no está
     * en ellos, se intenta sacar el nombre de la línea del propio trip_id, igual que hace el mapa
     * de trenes (RutasController). Devuelve null si no se puede averiguar.
     */
    private String lineaDeViaje(String tripId) {
        Viaje viaje = red.getViaje(tripId);
        if (viaje != null) {
            Linea linea = red.getLinea(viaje.getRouteId());
            if (linea != null && linea.getShortName() != null && !linea.getShortName().isBlank()) {
                return linea.getShortName();
            }
        }
        Matcher m = PATRON_LINEA.matcher(tripId);
        return m.find() ? nombreComercial(m.group(1)) : null;
    }

    /**
     * Escribe el nombre de una línea como en los horarios GTFS: "C" mayúscula y la letra de ramal en
     * minúscula ("c4A" -> "C4a"). Revisión: antes se pasaba todo a mayúsculas y salía "C4A", así que
     * los trenes de la C4a con trip_id conocido y los de trip_id desconocido se contaban como dos
     * líneas distintas (y podían quedarse por debajo del mínimo de 3 trenes en las dos).
     */
    static String nombreComercial(String linea) {
        return "C" + linea.substring(1).toLowerCase();
    }

    /**
     * Registra los avisos oficiales de la última descarga del feed de alertas de Renfe como incidencias
     * de las líneas afectadas (US-017, criterio 1). Se guarda una incidencia por cada aviso y línea:
     * si ya existe activa se actualiza y no se duplica (criterio 3), y las de avisos que ya no están
     * en el feed se cierran con su hora de fin (criterio 4). Se llamará en cada actualización.
     *
     * Si la última descarga de alertas ha fallado no se cambia nada, para no cerrar avisos por un
     * fallo de la API. Los avisos con hora de inicio futura (p. ej. obras anunciadas) aún no se
     * registran: empezarán a contar cuando llegue esa hora.
     *
     * @param ahora momento de la revisión
     */
    @Transactional
    public void registrarAvisosOficiales(LocalDateTime ahora) {
        if (!red.isIncidenciasTiempoRealActivas()) return;

        // 1. Crear o actualizar una incidencia por cada aviso vigente y cada línea afectada.
        Set<String> vigentes = new HashSet<>();
        for (AvisoOficial aviso : red.getAvisosOficiales()) {
            LocalDateTime inicio = inicioDeAviso(aviso, ahora);
            if (inicio.isAfter(ahora)) continue;
            String descripcion = descripcionDeAviso(aviso);

            for (String linea : lineasDeAviso(aviso)) {
                vigentes.add(claveAviso(aviso.id(), linea));
                Optional<Incidencia> existente = repositorio.findByIdExternoAndLineaAndFinIsNull(aviso.id(), linea);
                if (existente.isPresent()) {
                    Incidencia incidencia = existente.get();
                    incidencia.registrarNuevaDeteccion(ahora, descripcion);
                    repositorio.save(incidencia);
                } else {
                    repositorio.save(new Incidencia(linea, TIPO_AVISO_OFICIAL, descripcion,
                            OrigenIncidencia.AVISO_OFICIAL, aviso.id(), inicio));
                }
            }
        }

        // 2. Cerrar las incidencias de avisos oficiales que ya no aparecen en el feed.
        for (Incidencia incidencia : repositorio.findByOrigenAndFinIsNull(OrigenIncidencia.AVISO_OFICIAL)) {
            if (!vigentes.contains(claveAviso(incidencia.getIdExterno(), incidencia.getLinea()))) {
                incidencia.finalizar(ahora);
                repositorio.save(incidencia);
            }
        }
    }

    /**
     * Averigua las líneas de Madrid a las que afecta un aviso (p. ej. "C3", "C4a").
     * Lo necesitamos para guardar el aviso en cada línea afectada. Primero usa las rutas (route_id)
     * del aviso; si no trae rutas, usa las líneas que pasan por sus estaciones. Las rutas que no
     * están en la red (otros núcleos de Cercanías) se ignoran, porque la red solo carga Madrid.
     */
    private Set<String> lineasDeAviso(AvisoOficial aviso) {
        Set<String> lineas = new LinkedHashSet<>();
        for (String routeId : aviso.routeIds()) {
            Linea linea = red.getLinea(routeId);
            if (linea != null) anadirNombre(lineas, linea);
        }
        if (aviso.routeIds().isEmpty()) {
            for (String stopId : aviso.stopIds()) {
                for (Linea linea : red.getLineasEnEstacion(stopId)) anadirNombre(lineas, linea);
            }
        }
        return lineas;
    }

    /** Añade el nombre comercial de una línea a un conjunto si lo tiene; lo usa lineasDeAviso. */
    private static void anadirNombre(Set<String> lineas, Linea linea) {
        if (linea.getShortName() != null && !linea.getShortName().isBlank()) lineas.add(linea.getShortName().trim());
    }

    /**
     * Hora de inicio de un aviso en hora de Madrid, o "ahora" si Renfe no la indica.
     * Lo necesitamos para guardar como inicio de la incidencia la hora real en que empezó (criterio 5).
     */
    private static LocalDateTime inicioDeAviso(AvisoOficial aviso, LocalDateTime ahora) {
        if (aviso.inicioEpoch() == null) return ahora;
        return LocalDateTime.ofInstant(Instant.ofEpochSecond(aviso.inicioEpoch()), MADRID);
    }

    /**
     * Texto de la incidencia a partir del aviso: título (si lo hay) y descripción, sin los hashtags del
     * principio y recortado a la longitud de la columna. Lo necesitamos para que el viajero lea un
     * texto limpio y para que un aviso muy largo no haga fallar el guardado.
     */
    static String descripcionDeAviso(AvisoOficial aviso) {
        String texto = aviso.descripcion() == null ? "" : HASHTAGS_INICIALES.matcher(aviso.descripcion()).replaceFirst("").trim();
        if (aviso.titulo() != null && !aviso.titulo().isBlank()) {
            texto = texto.isEmpty() ? aviso.titulo().trim() : aviso.titulo().trim() + ": " + texto;
        }
        if (texto.isEmpty()) texto = "Aviso oficial de Renfe";
        return texto.length() > MAX_DESCRIPCION ? texto.substring(0, MAX_DESCRIPCION) : texto;
    }

    /** Clave aviso + línea; la necesitamos para saber qué incidencias de avisos siguen en el feed. */
    private static String claveAviso(String idExterno, String linea) {
        return idExterno + "|" + linea;
    }

    /**
     * Un viajero declara una incidencia en una línea. Si ya hay una incidencia declarada activa en esa
     * línea con ese tipo, el viajero se suma a ella (sube su nivel de confianza); si no, se crea una
     * nueva. Lo necesitamos para que los viajeros puedan avisar de incidencias desde la aplicación.
     *
     * Ojo con el tipo OTRO: todas las declaraciones OTRO de una línea van al mismo grupo aunque cada
     * viajero describa algo distinto, así que su nivel de confianza mezcla incidencias diferentes.
     *
     * @param usuario        viajero con la sesión iniciada
     * @param linea          línea afectada, p. ej. "C3"
     * @param tipo           tipo elegido de la lista cerrada
     * @param especificacion qué ocurre; obligatorio si el tipo es OTRO
     * @param comentario     comentario opcional
     * @param ahora          momento de la declaración
     * @return la incidencia (grupo) en la que queda la declaración
     * @throws ResponseStatusException 403 si no es VIAJERO; 400 si falta el tipo, falta la especificación
     *         de OTRO, algún texto es demasiado largo o la línea no existe; 409 si ese viajero ya
     *         declaró esa incidencia
     */
    @Transactional
    public Incidencia declararIncidencia(Usuario usuario, String linea, TipoIncidenciaUsuario tipo,
                                         String especificacion, String comentario, LocalDateTime ahora) {
        if (usuario.getRol() != RolUsuario.VIAJERO) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo los viajeros pueden declarar incidencias.");
        }
        if (tipo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica el tipo de incidencia.");
        }
        String textoEspecificacion = textoOpcional(especificacion);
        if (tipo.requiereEspecificacion() && textoEspecificacion == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Indica qué ocurre cuando el tipo es \"Otra incidencia\".");
        }
        String textoComentario = textoOpcional(comentario);
        // Revisión: sin estos límites, un texto más largo que su columna hacía fallar el guardado
        // con un error 500 en vez de un 400 que la web pueda explicar al viajero.
        if (textoEspecificacion != null && textoEspecificacion.length() > MAX_ESPECIFICACION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "La descripción de la incidencia no puede tener más de " + MAX_ESPECIFICACION + " caracteres.");
        }
        if (textoComentario != null && textoComentario.length() > MAX_COMENTARIO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "El comentario no puede tener más de " + MAX_COMENTARIO + " caracteres.");
        }
        String lineaNormalizada = normalizarLineaExistente(linea);

        Optional<Incidencia> existente = repositorio.findByLineaAndTipoAndOrigenAndFinIsNull(
                lineaNormalizada, tipo.name(), OrigenIncidencia.DECLARADA_POR_USUARIO);
        Incidencia grupo;
        if (existente.isPresent()) {
            grupo = existente.get();
            if (declaraciones.existsByIncidenciaIdAndUsuarioId(grupo.getId(), usuario.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya has declarado esta incidencia.");
            }
        } else {
            grupo = new Incidencia(lineaNormalizada, tipo.name(), tipo.getEtiqueta(),
                    OrigenIncidencia.DECLARADA_POR_USUARIO, null, ahora);
        }

        // Limitación conocida: si dos viajeros declaran a la vez, los dos pueden leer el mismo número
        // de declarantes y el contador sumar uno de menos (o, si aún no había grupo, crearse dos
        // grupos). Con el tráfico del proyecto es muy improbable; si hiciera falta, la solución sería
        // bloqueo optimista (@Version en Incidencia).
        grupo.sumarDeclarante(ahora);
        grupo = repositorio.save(grupo);
        try {
            declaraciones.save(new DeclaracionIncidencia(grupo, usuario, tipo, textoEspecificacion,
                    textoComentario, ahora));
        } catch (DataIntegrityViolationException e) {
            // Revisión: si el mismo viajero envía la declaración dos veces casi a la vez (doble clic),
            // las dos pasan la comprobación de arriba y la base de datos rechaza la segunda por la
            // restricción única (incidencia, usuario). Se responde 409 como en el caso normal, en vez
            // de 500, y la transacción se deshace, así que tampoco cuenta dos veces.
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya has declarado esta incidencia.");
        }
        return grupo;
    }

    /**
     * Un GESTOR cierra una incidencia declarada por viajeros, con su hora de fin. Lo necesitamos
     * porque estas incidencias no se cierran solas: no hay datos automáticos que digan que ha terminado.
     * Si ya estaba cerrada se devuelve tal cual, conservando su hora de fin original.
     *
     * @throws ResponseStatusException 403 si no es GESTOR; 404 si no existe; 400 si no es una
     *         incidencia declarada por usuarios (las de Renfe y las propias se cierran solas)
     */
    @Transactional
    public Incidencia finalizarIncidenciaDeclarada(Usuario usuario, Long id, LocalDateTime ahora) {
        if (usuario.getRol() != RolUsuario.GESTOR) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Solo un gestor puede cerrar incidencias declaradas.");
        }
        Incidencia incidencia = repositorio.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Incidencia no encontrada."));
        if (incidencia.getOrigen() != OrigenIncidencia.DECLARADA_POR_USUARIO) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Solo se pueden cerrar a mano las incidencias declaradas por viajeros.");
        }
        incidencia.finalizar(ahora);
        return repositorio.save(incidencia);
    }

    /**
     * Comprueba que la línea existe en la red y la devuelve con el nombre que usa la red (p. ej. "c3" -> "C3").
     * Lo necesitamos para no guardar declaraciones de líneas inexistentes y para que "c3" y "C3"
     * acaben en el mismo grupo.
     */
    private String normalizarLineaExistente(String linea) {
        String buscada = textoOpcional(linea);
        if (buscada != null) {
            for (Linea l : red.getAllLineas()) {
                String nombre = l.getShortName() == null ? null : l.getShortName().trim();
                if (nombre != null && nombre.equalsIgnoreCase(buscada)) return nombre;
            }
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "La línea indicada no existe.");
    }

    /** Quita los espacios de un texto y lo convierte en null si queda vacío; lo necesitamos para validar los campos opcionales. */
    private static String textoOpcional(String texto) {
        if (texto == null) return null;
        String limpio = texto.trim();
        return limpio.isEmpty() ? null : limpio;
    }

    /** Texto de la descripción de una incidencia por retraso; lo necesitamos para que el viajero sepa qué ocurre. */
    private static String descripcionRetraso(int trenes) {
        return trenes + " trenes acumulan un retraso de " + (MIN_RETRASO_SEGUNDOS / 60) + " minutos o más.";
    }
}
