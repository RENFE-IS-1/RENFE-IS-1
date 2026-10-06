package main;

import loader.GTFSLoader;
import modelo.Estacion;
import modelo.Linea;
import modelo.ParadaHorario;
import modelo.Viaje;
import repositorio.RedTransporte;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Scanner;
import java.util.Set;
import java.util.TreeSet;

public class Main {
    public static void main(String[] args) {
        RedTransporte red = new RedTransporte();
        GTFSLoader loader = new GTFSLoader();
        
        System.out.println("Iniciando carga estructurada...");
        long startTime = System.currentTimeMillis();
        
        loader.cargarDesdeDirectorio("data/", red);
        
        long endTime = System.currentTimeMillis();
        System.out.println("Carga y optimización completadas en " + (endTime - startTime) + " ms.");
        
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
            
            // 5. Búsqueda de Estaciones
            Estacion est = null;
            
            // 5.1 Búsqueda por ID
            est = red.getEstacion(input);
            
            // 5.2 Búsqueda exacta normalizada
            if (est == null) {
                for (Estacion e : red.getAllEstaciones()) {
                    if (normalizar(e.getNombre()).equals(inputNorm)) {
                        est = e;
                        break;
                    }
                }
            }
            
            // 5.3 Búsqueda parcial (Avisando si hay múltiples)
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

    private static String normalizar(String texto) {
        if (texto == null) return "";
        String normalizado = Normalizer.normalize(texto, Normalizer.Form.NFD);
        return normalizado.replaceAll("\\p{M}", "").toLowerCase();
    }
}