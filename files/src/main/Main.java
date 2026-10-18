package main;

import consultas.BuscadorEstaciones;
import consultas.ConsultaLlegadas;
import loader.GTFSLoader;
import modelo.Estacion;
import modelo.Linea;
import modelo.Llegada;
import modelo.ParadaHorario;
import modelo.Viaje;
import repositorio.RedTransporte;
import modelo.Ruta;
import modelo.Tramo;
import servicio.ComparadoresRuta;
import servicio.ServicioComparadorRutas;

import java.io.File;
import java.text.Normalizer;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

public class Main {
    public static void main(String[] args) {
        RedTransporte red = new RedTransporte();
        GTFSLoader loader = new GTFSLoader();
        
        System.out.println("Iniciando carga estructurada...");
        long startTime = System.currentTimeMillis();
        
        loader.cargarDesdeDirectorio(directorioDatos(), red);
        
        long endTime = System.currentTimeMillis();
        System.out.println("Carga y optimización completadas en " + (endTime - startTime) + " ms.");
        if (red.getAllEstaciones().isEmpty()) {
            System.out.println("AVISO: no se ha cargado ninguna estación. Descarga el GTFS de "
                    + "https://data.renfe.com/dataset/horarios-cercanias y extrae los .txt en "
                    + "data/horarios_cercanias/ (raíz del repositorio) o en src/Java/data/.");
        }
        
        // Comparador numérico para forzar orden lógico en las líneas (C1, C2, C3... en lugar de C1, C10, C2)
        Comparator<String> ordenComercialLineas = new Comparator<String>() {
            @Override
            public int compare(String s1, String s2) {
                String num1Str = s1.replaceAll("[^0-9]", "");
                String num2Str = s2.replaceAll("[^0-9]", "");
                int n1 = num1Str.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(num1Str);
                int n2 = num2Str.isEmpty() ? Integer.MAX_VALUE : Integer.parseInt(num2Str);
                
                if (n1 != n2) {
                    return Integer.compare(n1, n2);
                }
                return s1.compareToIgnoreCase(s2);
            }
        };
        
        Scanner scanner = new Scanner(System.in);
        while (true) {
            System.out.print("\nComandos disponibles:\n"
                    + " - [Nombre/ID estación] : Busca una estación\n"
                    + " - 'lista Cx'           : Estaciones de una línea (Alfabético)\n"
                    + " - 'linea Cx'           : Estaciones de una línea (Orden geográfico)\n"
                    + " - 'lineas'             : Muestra todas las líneas del sistema\n"
                    + " - 'estaciones'         : Muestra todas las estaciones y sus líneas\n"
                    + " - 'llegadas <estación> [Cx] [HH:MM] [dd/mm]' : Próximos trenes de una estación\n"
                    + " - 'comparar'           : Compara diferentes opciones de ruta entre estaciones\n"
                    + " - 'salir'              : Termina la ejecución\n> ");
            
            String input = scanner.nextLine().trim();
            if (input.equalsIgnoreCase("salir")) break;
            
            String inputNorm = normalizar(input);
            
            // 1. Comando: lineas (Global)
            if (inputNorm.equals("lineas")) {
                Set<String> todasLasLineas = new TreeSet<>(ordenComercialLineas);
                // Extraemos las líneas directamente de las asociaciones por estación para incluir las inyectadas
                for (Estacion e : red.getAllEstaciones()) {
                    for (Linea l : red.getLineasEnEstacion(e.getId())) {
                        todasLasLineas.add(l.getShortName());
                    }
                }
                System.out.println("\nLíneas disponibles en el sistema:");
                for (String l : todasLasLineas) {
                    System.out.println(" - " + l);
                }
                continue;
            }
            
            // 2. Comando: estaciones (Global)
            if (inputNorm.equals("estaciones")) {
                List<Estacion> todas = red.getAllEstaciones();
                todas.sort(Comparator.comparing(Estacion::getNombre));
                System.out.println("\nLista de todas las estaciones registradas:");
                for (Estacion e : todas) {
                    Set<String> lineasEst = new TreeSet<>(ordenComercialLineas);
                    for (Linea l : red.getLineasEnEstacion(e.getId())) {
                        lineasEst.add(l.getShortName());
                    }
                    System.out.println(" - " + e.getNombre() + " " + lineasEst);
                }
                continue;
            }
            
            // 3. Comando: lista Cx
            if (inputNorm.startsWith("lista ")) {
                String lineaBuscada = inputNorm.substring(6).trim();
                Set<String> estacionesDeLinea = new TreeSet<>();
                
                for (Estacion est : red.getAllEstaciones()) {
                    for (Linea linea : red.getLineasEnEstacion(est.getId())) {
                        if (normalizar(linea.getShortName()).equals(lineaBuscada)) {
                            estacionesDeLinea.add(est.getNombre());
                            break;
                        }
                    }
                }
                
                if (estacionesDeLinea.isEmpty()) {
                    System.out.println("No se han encontrado estaciones para la línea: " + input.substring(6));
                } else {
                    System.out.println("\nEstaciones de la línea " + input.substring(6).toUpperCase() + " (Alfabético):");
                    for (String nombreEstacion : estacionesDeLinea) {
                        System.out.println(" - " + nombreEstacion);
                    }
                }
                continue;
            }
            
            // 4. Comando: linea Cx
            if (inputNorm.startsWith("linea ")) {
                String lineaBuscada = inputNorm.substring(6).trim();
                Set<String> routeIds = new HashSet<>();
                
                for (Linea l : red.getAllLineas()) {
                    if (normalizar(l.getShortName()).equals(lineaBuscada)) {
                        routeIds.add(l.getId());
                    }
                }
                
                Viaje viajeMasLargo = null;
                int maxParadas = 0;
                for (Viaje v : red.getAllViajes()) {
                    if (routeIds.contains(v.getRouteId())) {
                        int numParadas = red.getRutaDeViaje(v.getId()).size();
                        if (numParadas > maxParadas) {
                            maxParadas = numParadas;
                            viajeMasLargo = v;
                        }
                    }
                }
                
                if (viajeMasLargo == null) {
                    System.out.println("No se han encontrado recorridos continuos en GTFS para la línea: " + input.substring(6));
                    continue;
                }
                
                List<ParadaHorario> paradas = red.getRutaDeViaje(viajeMasLargo.getId());
                paradas.sort(Comparator.comparingInt(ParadaHorario::getStopSequence));
                
                System.out.println("\nEstaciones de la línea " + input.substring(6).toUpperCase() + " (Ruta secuencial física más larga):");
                for (ParadaHorario ph : paradas) {
                    Estacion e = red.getEstacion(ph.getStopId());
                    if (e != null) {
                        System.out.println(" " + ph.getStopSequence() + ".\t" + e.getNombre());
                    }
                }
                continue;
            }
            
            // 5. Comando: llegadas <estación> [línea] [HH:MM] [dd/mm]  (US-004)
            if (inputNorm.equals("llegadas") || inputNorm.startsWith("llegadas ")) {
                mostrarLlegadas(red, input.substring("llegadas".length()).trim());
                continue;
            }
            
         // 6. Comando: comparar (US-002)
            if (inputNorm.equals("comparar")) {
                System.out.println("\n--- BÚSQUEDA Y COMPARACIÓN DE RUTAS (US-002) ---");
                BuscadorEstaciones buscador = new BuscadorEstaciones(red);

                // 6.1 Pedir y validar ORIGEN
                Estacion origen = null;
                while (origen == null) {
                    System.out.print("Introduce la estación de ORIGEN (o 'cancelar'): ");
                    String origenInput = scanner.nextLine().trim();
                    if (origenInput.equalsIgnoreCase("cancelar")) break;

                    List<Estacion> origenes = buscador.buscar(origenInput);
                    if (origenes.isEmpty()) {
                        System.out.println("Estación de origen no encontrada. Verifica el ID o prueba con otro texto.\n");
                    } else if (origenes.size() == 1) {
                        origen = origenes.get(0);
                        System.out.println("-> Estación seleccionada: " + origen.getNombre() + " (ID: " + origen.getId() + ")\n");
                    } else {
                        System.out.println("\nMúltiples estaciones coinciden con '" + origenInput + "':");
                        for (Estacion e : origenes) {
                            System.out.println(" - " + e.getNombre() + " (ID: " + e.getId() + ")");
                        }
                        System.out.print("Escribe el nombre exacto/ID de la estación deseada (o 'm' para modificar la búsqueda): ");
                        String seleccion = scanner.nextLine().trim();
                        
                        if (!seleccion.equalsIgnoreCase("m") && !seleccion.equalsIgnoreCase("modificar")) {
                            List<Estacion> reBuscadas = buscador.buscar(seleccion);
                            if (reBuscadas.size() == 1) {
                                origen = reBuscadas.get(0);
                                System.out.println("-> Estación seleccionada: " + origen.getNombre() + " (ID: " + origen.getId() + ")\n");
                            } else {
                                System.out.println("Selección no válida. Inténtalo de nuevo.\n");
                            }
                        } else {
                            System.out.println();
                        }
                    }
                }
                if (origen == null) continue;

                // 6.2 Pedir y validar DESTINO
                Estacion destino = null;
                while (destino == null) {
                    System.out.print("Introduce la estación de DESTINO (o 'cancelar'): ");
                    String destinoInput = scanner.nextLine().trim();
                    if (destinoInput.equalsIgnoreCase("cancelar")) break;

                    List<Estacion> destinos = buscador.buscar(destinoInput);
                    if (destinos.isEmpty()) {
                        System.out.println("Estación de destino no encontrada. Verifica el ID o prueba con otro texto.\n");
                    } else if (destinos.size() == 1) {
                        destino = destinos.get(0);
                        System.out.println("-> Estación seleccionada: " + destino.getNombre() + " (ID: " + destino.getId() + ")\n");
                    } else {
                        System.out.println("\nMúltiples estaciones coinciden con '" + destinoInput + "':");
                        for (Estacion e : destinos) {
                            System.out.println(" - " + e.getNombre() + " (ID: " + e.getId() + ")");
                        }
                        System.out.print("Escribe el nombre exacto/ID de la estación deseada (o 'm' para modificar la búsqueda): ");
                        String seleccion = scanner.nextLine().trim();
                        
                        if (!seleccion.equalsIgnoreCase("m") && !seleccion.equalsIgnoreCase("modificar")) {
                            List<Estacion> reBuscadas = buscador.buscar(seleccion);
                            if (reBuscadas.size() == 1) {
                                destino = reBuscadas.get(0);
                                System.out.println("-> Estación seleccionada: " + destino.getNombre() + " (ID: " + destino.getId() + ")\n");
                            } else {
                                System.out.println("Selección no válida. Inténtalo de nuevo.\n");
                            }
                        } else {
                            System.out.println();
                        }
                    }
                }
                if (destino == null) continue;

                // --- NUEVO: Solicitar y validar la hora de salida ---
                System.out.print("Introduce la hora de salida (HH:MM) o pulsa ENTER para usar la hora actual: ");
                String inputHora = scanner.nextLine().trim();
                LocalTime horaSalida;

                if (inputHora.isEmpty()) {
                    horaSalida = LocalTime.now(ZONA_MADRID);
                } else {
                    try {
                        // Admite formatos como "8:30" añadiendo un cero delante para parsear "08:30"
                        horaSalida = LocalTime.parse(inputHora.length() == 4 ? "0" + inputHora : inputHora);
                    } catch (Exception e) {
                        System.out.println("Formato de hora incorrecto. Se utilizará la hora actual por defecto.");
                        horaSalida = LocalTime.now(ZONA_MADRID);
                    }
                }

                System.out.println("Buscando opciones desde " + origen.getNombre() + " hasta " + destino.getNombre() + " a partir de las " + horaSalida.format(FMT_HORA) + "...");

                // 6.3 Obtener rutas disponibles y compararlas usando la hora indicada
                servicio.ServicioBuscadorRutas buscadorRutas = new servicio.ServicioBuscadorRutas(red);
                List<Ruta> rutasEncontradas = buscadorRutas.buscarRutas(origen, destino, horaSalida);

                if (rutasEncontradas.isEmpty()) {
                    System.out.println("No se han encontrado rutas disponibles entre " + origen.getNombre() + " y " + destino.getNombre()
                            + ". Puede que no haya trenes a esa hora o que la fecha quede fuera del periodo de los horarios cargados.");
                    continue;
                }

                // El buscador ya las devuelve ordenadas por hora de llegada con trenes reales: la
                // mejor opción es la que llega antes, no la de menor duración (podría salir más tarde)
                List<Ruta> opciones = rutasEncontradas;

                // Si ninguna opción sale cerca de la hora pedida (de noche no hay servicio), se avisa
                LocalDateTime pedida = LocalDateTime.of(LocalDate.now(ZONA_MADRID), horaSalida.withSecond(0).withNano(0));
                LocalDateTime primeraSalida = null;
                for (Ruta r : opciones) {
                    LocalDateTime s = r.getFechaHoraSalida();
                    if (s != null && (primeraSalida == null || s.isBefore(primeraSalida))) primeraSalida = s;
                }
                if (primeraSalida != null && primeraSalida.isAfter(pedida.plusMinutes(servicio.ServicioBuscadorRutas.MAX_ESPERA_MINUTOS))) {
                    String cuando = primeraSalida.toLocalDate().equals(pedida.toLocalDate()) ? "hoy" : "mañana";
                    System.out.println("\nAVISO: no hay ninguna conexión entre " + origen.getNombre() + " y " + destino.getNombre()
                            + " cerca de las " + pedida.format(FMT_HORA) + ". El primer viaje posible sale " + cuando
                            + " a las " + primeraSalida.format(FMT_HORA) + ".");
                }

                System.out.println("\n--- RUTAS ENCONTRADAS (Mejor opción primero) ---");
                for (int i = 0; i < opciones.size(); i++) {
                    Ruta r = opciones.get(i);
                    Tramo primero = r.getTramos().get(0);
                    Tramo ultimo = r.getTramos().get(r.getTramos().size() - 1);
                    String hSalida = formatearHora(primero.getFechaHoraSalida(), primero.getHoraSalida());
                    String hLlegada = formatearHora(ultimo.getFechaHoraLlegada(), ultimo.getHoraLlegada());
                    
                    StringBuilder lineasRuta = new StringBuilder();
                    for (Tramo t : r.getTramos()) {
                        if (lineasRuta.length() > 0) lineasRuta.append(" > ");
                        lineasRuta.append(t.getLinea().getShortName());
                    }
                    System.out.printf("%d. Salida: %s -> Llegada: %s | Duración: %d min | Precio: %.2f EUR | Transbordos: %d | %s\n",
                            i + 1, hSalida, hLlegada, r.getTiempoTotalMinutos(), r.getPrecioTotal(), r.getNumeroTransbordos(), lineasRuta);
                }

                System.out.print("\nIntroduce el número de la ruta para ver los detalles (0 para salir): ");
                String seleccionRuta = scanner.nextLine().trim();
                
                try {
                    int num = Integer.parseInt(seleccionRuta);
                    if (num > 0 && num <= opciones.size()) {
                        Ruta elegida = opciones.get(num - 1);
                        System.out.println("\nDetalles del itinerario:");
                        for (Tramo t : elegida.getTramos()) {
                            System.out.println(" - [" + formatearHora(t.getFechaHoraSalida(), t.getHoraSalida()) + " a "
                                    + formatearHora(t.getFechaHoraLlegada(), t.getHoraLlegada()) + "] " +
                                    t.getLinea().getShortName() + ": " + t.getOrigen().getNombre() + " -> " + t.getDestino().getNombre());
                        }
                    }
                } catch (NumberFormatException e) {
                    // Si el usuario pulsa Enter vacío o introduce texto, se cancela la visualización
                }
                continue;
            
            }
            
            // 7. Búsqueda de Estaciones
            Estacion est = null;
            
            // 7.1 Búsqueda por ID
            est = red.getEstacion(input);
            
            // 7.2 Búsqueda exacta normalizada
            if (est == null) {
                for (Estacion e : red.getAllEstaciones()) {
                    if (normalizar(e.getNombre()).equals(inputNorm)) {
                        est = e;
                        break;
                    }
                }
            }
            
            // 7.3 Búsqueda parcial (Avisando si hay múltiples)
            if (est == null) {
                List<Estacion> coincidencias = new ArrayList<>();
                for (Estacion e : red.getAllEstaciones()) {
                    if (normalizar(e.getNombre()).contains(inputNorm)) {
                        coincidencias.add(e);
                    }
                }
                
                if (coincidencias.size() == 1) {
                    est = coincidencias.get(0);
                    System.out.println("\nCoincidencia encontrada: " + est.getNombre());
                } else if (coincidencias.size() > 1) {
                    System.out.println("\nMúltiples estaciones coinciden con '" + input + "'. Por favor, sé más específico:");
                    for (Estacion e : coincidencias) {
                        System.out.println(" - " + e.getNombre() + " (ID: " + e.getId() + ")");
                    }
                    continue;
                }
            }

            if (est == null) {
                System.out.println("Estación no encontrada. Verifica el ID o prueba con otro fragmento del nombre.");
                continue;
            }
            
            Set<String> nombresLineas = new TreeSet<>(ordenComercialLineas);
            for (Linea linea : red.getLineasEnEstacion(est.getId())) {
                nombresLineas.add(linea.getShortName());
            }
            
            System.out.println("\nLíneas operativas en " + est.getNombre() + " (ID: " + est.getId() + "):");
            for (String nombreLinea : nombresLineas) {
                System.out.println(" - " + nombreLinea);
            }
        }
        scanner.close();
        System.out.println("Ejecución finalizada.");
    }

    private static final ZoneId ZONA_MADRID = ZoneId.of("Europe/Madrid");
    private static final int LLEGADAS_A_MOSTRAR = 10;
    private static final DateTimeFormatter FMT_HORA = DateTimeFormatter.ofPattern("HH:mm");
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    /**
     * Carpeta con los .txt del GTFS de Renfe. Se admite tanto src/Java/data/ (directorio de
     * trabajo de Eclipse) como data/horarios_cercanias/ en la raíz del repositorio.
     */
    private static String directorioDatos() {
        String[] candidatos = {"data/", "../../data/horarios_cercanias/", "data/horarios_cercanias/"};
        for (String c : candidatos) {
            if (new File(c + "stop_times.txt").exists()) return c;
        }
        return candidatos[0];
    }

    /** Hora HH:mm; si el tren es de otro día distinto a hoy, añade la fecha (p. ej. "07:20 (12/10)"). */
    private static String formatearHora(LocalDateTime fechaHora, LocalTime hora) {
        if (fechaHora != null && !fechaHora.toLocalDate().equals(LocalDate.now(ZONA_MADRID))) {
            return fechaHora.format(DateTimeFormatter.ofPattern("HH:mm (dd/MM)"));
        }
        return hora.format(FMT_HORA);
    }

    /** US-004: muestra los próximos trenes que pasan por una estación. */
    private static void mostrarLlegadas(RedTransporte red, String argumentos) {
        if (argumentos.isEmpty()) {
            System.out.println("Uso: llegadas <estación> [línea] [HH:MM] [dd/mm]   p. ej. 'llegadas atocha C3 08:30'");
            return;
        }

        if (red.getAllEstaciones().isEmpty()) {
            System.out.println("No hay datos de horarios cargados, así que no se pueden consultar llegadas. "
                    + "Revisa que los archivos GTFS estén en data/horarios_cercanias/.");
            return;
        }

        // Los parámetros opcionales se reconocen por su formato al final del texto
        List<String> partes = new ArrayList<>(Arrays.asList(argumentos.split("\\s+")));
        String filtroLinea = null;
        LocalDateTime ahora = LocalDateTime.now(ZONA_MADRID).withSecond(0).withNano(0);
        LocalDate fecha = ahora.toLocalDate();
        LocalTime hora = ahora.toLocalTime();
        try {
            boolean quedanOpciones = true;
            while (partes.size() > 1 && quedanOpciones) {
                String ultimo = partes.get(partes.size() - 1);
                if (ultimo.matches("\\d{1,2}:\\d{2}")) {
                    hora = LocalTime.parse(ultimo.length() == 4 ? "0" + ultimo : ultimo);
                } else if (ultimo.matches("\\d{1,2}/\\d{1,2}")) {
                    String[] dm = ultimo.split("/");
                    fecha = LocalDate.of(ahora.getYear(), Integer.parseInt(dm[1]), Integer.parseInt(dm[0]));
                } else if (ultimo.matches("(?i)c\\d+[a-z]?")) {
                    filtroLinea = ultimo;
                } else {
                    quedanOpciones = false;
                    continue;
                }
                partes.remove(partes.size() - 1);
            }
        } catch (RuntimeException e) {
            System.out.println("Hora o fecha no válida. Usa HH:MM para la hora y dd/mm para la fecha.");
            return;
        }

        String textoEstacion = String.join(" ", partes);
        List<Estacion> encontradas = new BuscadorEstaciones(red).buscar(textoEstacion);
        if (encontradas.isEmpty()) {
            System.out.println("Estación no encontrada: '" + textoEstacion + "'.");
            return;
        }
        if (encontradas.size() > 1) {
            System.out.println("\nVarias estaciones coinciden con '" + textoEstacion + "'. Sé más específico:");
            for (Estacion e : encontradas) {
                System.out.println(" - " + e.getNombre() + " (ID: " + e.getId() + ")");
            }
            return;
        }

        Estacion estacion = encontradas.get(0);
        LocalDateTime desde = LocalDateTime.of(fecha, hora);
        ConsultaLlegadas consulta = new ConsultaLlegadas(red);

        String dia = fecha.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.forLanguageTag("es-ES"));
        System.out.println("\nPróximas llegadas a " + estacion.getNombre()
                + (filtroLinea != null ? " (línea " + filtroLinea.toUpperCase() + ")" : "")
                + " — " + dia + " " + fecha.format(FMT_FECHA) + " desde las " + hora.format(FMT_HORA));

        if (!red.hayCalendario()) {
            System.out.println("No se ha podido cargar el calendario de servicios (calendar.txt): no se sabe qué trenes circulan cada día.");
            return;
        }
        if (!consulta.fechaCubierta(fecha)) {
            System.out.println("No hay horarios para esa fecha. Los datos cargados cubren del "
                    + red.getInicioValidez().format(FMT_FECHA) + " al " + red.getFinValidez().format(FMT_FECHA)
                    + ". Descarga un GTFS más reciente de data.renfe.com.");
            return;
        }

        Map<String, Set<String>> lineas = consulta.lineasYSentidos(estacion, fecha, filtroLinea);
        if (!lineas.isEmpty()) {
            System.out.println("Líneas que paran aquí ese día y sus sentidos:");
            for (Map.Entry<String, Set<String>> e : lineas.entrySet()) {
                String sentidos = e.getValue().isEmpty() ? "(todos sus trenes terminan aquí)"
                        : "→ " + String.join(" / ", e.getValue());
                System.out.printf("  %-4s %s%n", e.getKey(), sentidos);
            }
            System.out.println("Próximos trenes:");
        }

        List<Llegada> llegadas = consulta.proximasLlegadas(estacion, desde, LLEGADAS_A_MOSTRAR, filtroLinea);
        if (llegadas.isEmpty()) {
            System.out.println("No hay trenes programados en las próximas horas"
                    + (filtroLinea != null ? " para esa línea." : "."));
        } else {
            for (Llegada ll : llegadas) {
                String linea = ll.linea() != null ? ll.linea().getShortName() : "?";
                String destino = ll.terminaAqui() ? "(termina en esta estación)"
                        : "→ " + (ll.destino() != null ? ll.destino().getNombre() : "destino desconocido");
                String otroDia = ll.hora().toLocalDate().equals(fecha) ? "" : " (" + ll.hora().format(DateTimeFormatter.ofPattern("dd/MM")) + ")";
                System.out.printf("  %s%s  %-4s %s%n", ll.hora().format(FMT_HORA), otroDia, linea, destino);
            }
        }

        System.out.println("Horario planificado: no incluye retrasos ni incidencias en tiempo real.");
        String fechaDatos = red.getFechaDatos() != null
                ? red.getFechaDatos().atZone(ZONA_MADRID).format(DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm"))
                : "desconocida";
        System.out.println("Horarios válidos del " + red.getInicioValidez().format(FMT_FECHA) + " al "
                + red.getFinValidez().format(FMT_FECHA) + " · Archivos de datos del " + fechaDatos);
    }

    private static String normalizar(String texto) {
        if (texto == null) return "";
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return normalizado.replaceAll("\\p{M}", "").toLowerCase();
    }
}