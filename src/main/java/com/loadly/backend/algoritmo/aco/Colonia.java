package com.loadly.backend.algoritmo.aco;
 
import com.loadly.backend.model.*;
import lombok.Getter;
 
import java.util.*;
 
/**
 * ============================================================
 * Colonia — Gestiona el conjunto de hormigas y el grafo de feromonas
 * ============================================================
 *
 * Responsabilidades:
 *   1. Mantener la lista de hormigas.
 *   2. Mantener el FeromenaGrafo compartido (persiste entre iteraciones).
 *   3. Orquestar la construcción de soluciones: cada hormiga construye su
 *      ruta de forma independiente usando BuscadorRutasACO.
 *
 * NOTA SOBRE @Getter vs @Data:
 *   Se usa @Getter en lugar de @Data porque:
 *   - @Data genera un constructor para todos los campos 'final', lo que entra
 *     en conflicto con el constructor propio que definimos aquí.
 *   - @Getter solo genera getters, que es lo único que necesita AlgoritmoACO
 *     para acceder a feromenaGrafo y hormigas.
 *
 * El FeromenaGrafo se crea UNA SOLA VEZ en el constructor y persiste durante
 * toda la ejecución del ACO para que las feromonas se acumulen entre iteraciones
 * (memoria colectiva de la colonia).
 */
@Getter
public class Colonia {
 
    private final List<Hormiga>  hormigas;
    private final int            numHormigas;
    private final FeromenaGrafo  feromenaGrafo;
 
    // Datos estáticos guardados para no pasarlos en cada llamada a construirSoluciones
    private final Map<String, Aeropuerto>      mapaAeropuertos;
    private final Map<String, List<PlanVuelo>> mapaVuelosPorOrigen;
 
    // Constructor de ruta propio del ACO (no usa el BuscadorRutas del GA)
    private final BuscadorRutasACO buscadorRutasACO = new BuscadorRutasACO();
 
    // =========================================================================
    //  CONSTRUCTOR
    // =========================================================================
 
    /**
     * @param numHormigas         Cantidad de hormigas (análogo al tamaño de población del GA).
     * @param mapaAeropuertos     Datos de cada aeropuerto (GMT, lat, lon, continente, capacidad).
     * @param mapaVuelosPorOrigen Índice de vuelos agrupados por aeropuerto origen.
     */
    public Colonia(int numHormigas,
                   Map<String, Aeropuerto>      mapaAeropuertos,
                   Map<String, List<PlanVuelo>> mapaVuelosPorOrigen) {
        this.numHormigas         = numHormigas;
        this.mapaAeropuertos     = mapaAeropuertos;
        this.mapaVuelosPorOrigen = mapaVuelosPorOrigen;
        this.hormigas            = new ArrayList<>();
 
        // El grafo de feromonas se crea aquí y persiste en toda la vida del objeto Colonia.
        // Así las feromonas se acumulan iteración a iteración (memoria colectiva).
        this.feromenaGrafo = new FeromenaGrafo(mapaAeropuertos, mapaVuelosPorOrigen);
    }
 
    // =========================================================================
    //  CONSTRUCCIÓN DE SOLUCIONES
    // =========================================================================
 
    /**
     * Hace que todas las hormigas construyan su solución de forma independiente.
     *
     * Cada hormiga recibe:
     *   1. Capacidades CLONADAS para explorar su propio espacio.
     *   2. Un ORDEN ALEATORIO DE ENVÍOS para que explores diferentes prioritizaciones
     *      (emulando cómo el GA explora múltiples asignaciones/ordenamientos).
     *
     * BuscadorRutasACO consulta el FeromenaGrafo (solo lectura) para hacer la
     * selección probabilística en cada paso.
     *
     * Este método se llama: una vez al inicio, y luego en cada iteración del bucle.
     *
     * @param envios       Envíos a planificar en esta ventana temporal.
     * @param capVuelos    Capacidades reales de vuelos (se clonan por hormiga).
     * @param capAlmacenes Capacidades reales de almacenes (se clonan por hormiga).
     */
    public void construirSoluciones(
            List<Envio> envios,
            Map<String, Integer> capVuelos,
            Map<String, Integer> capAlmacenes) {

        Random random = new Random();
        hormigas.clear(); // limpiar la generación anterior

        for (int i = 0; i < numHormigas; i++) {

            // Clonar capacidades: cada hormiga trabaja sobre su propia copia
            Map<String, Integer> capVuelosClonada    = new HashMap<>(capVuelos);
            Map<String, Integer> capAlmacenesClonada = new HashMap<>(capAlmacenes);

            // PERMUTACIÓN DINÁMICA: cada hormiga procesa envíos en orden aleatorio
            // Esto permite explorar diferentes ordenamientos, similar a cómo GA
            // explora múltiples asignaciones/recombinaciones en paralelo
            List<Envio> enviospermutados = new ArrayList<>(envios);
            Collections.shuffle(enviospermutados, random);

            // Mapa para reordenar rutas: de vuelta al orden original
            List<Ruta> rutasPorEnvio = new ArrayList<>();
            for (int j = 0; j < envios.size(); j++) {
                rutasPorEnvio.add(null); // placeholder
            }

            for (Envio envio : enviospermutados) {
                Ruta ruta = buscadorRutasACO.construirRuta(
                        envio,
                        mapaVuelosPorOrigen,
                        mapaAeropuertos,
                        feromenaGrafo,        // feromonas compartidas (solo lectura)
                        capVuelosClonada,     // capacidades propias de esta hormiga
                        capAlmacenesClonada,
                        random
                );
                // Encontrar índice original del envío en la lista original
                int indiceOriginal = envios.indexOf(envio);
                rutasPorEnvio.set(indiceOriginal, ruta);
            }

            hormigas.add(new Hormiga(rutasPorEnvio));
        }
    }
 
    // =========================================================================
    //  CONSULTAS
    // =========================================================================
 
    /**
     * Devuelve la hormiga con mayor fitness de la generación actual.
     * Devuelve null si no hay hormigas (no se llamó a construirSoluciones aún).
     */
    public Hormiga getMejorHormiga() {
        return hormigas.stream()
                .max(Comparator.comparingDouble(Hormiga::getFitness))
                .orElse(null);
    }
}