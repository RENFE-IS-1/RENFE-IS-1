import csv
import os

# Esto le dice a Python: "busca la carpeta 'data' exactamente donde está este script"
BASE_DIR = os.path.dirname(os.path.abspath(__file__))
DATA_DIR = os.path.join(BASE_DIR, "data")

def cargar_csv(nombre_archivo):
    """Función auxiliar para leer cualquier archivo CSV del GTFS de forma segura."""
    ruta = os.path.join(DATA_DIR, nombre_archivo)
    if not os.path.exists(ruta):
        print(f"[Aviso] No se encuentra el archivo: {nombre_archivo}")
        return []
    
    with open(ruta, mode="r", encoding="utf-8") as f:
        return list(csv.DictReader(f))

def mostrar_resumen_red():
    """Muestra un resumen rápido de lo que hay en el transporte."""
    print("==========================================")
    print("  ESTADO DE LA RED DE CERCANÍAS (CRTM)")
    print("==========================================")
    
    # 1. Cargar estaciones
    estaciones = cargar_csv("stops.txt")
    print(f"Total de estaciones/paradas encontradas: {len(estaciones)}")
    
    # 2. Cargar líneas
    lineas = cargar_csv("routes.txt")
    print(f"Total de líneas de transporte: {len(lineas)}")
    print("\nLíneas disponibles:")
    for linea in lineas:
        # Dependiendo del GTFS, el nombre corto suele estar en route_short_name o route_long_name
        nombre = linea.get("route_short_name", "Desconocida")
        desc = linea.get("route_long_name", "")
        print(f" - Línea {nombre}: {desc}")

if __name__ == "__main__":
    mostrar_resumen_red()
