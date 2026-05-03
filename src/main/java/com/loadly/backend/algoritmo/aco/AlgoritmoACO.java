package com.loadly.backend.algoritmo.aco;
 
import com.loadly.backend.algoritmo.genetico.Fitness;
import com.loadly.backend.algoritmo.genetico.BuscadorRutas;
import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;
 
import java.util.*;
 
/**
 * ============================================================
 * AlgoritmoACO — Motor principal del Ant Colony Optimization
 * ============================================================
 *
 * Analogía con el GA para entender el paralelo:
 *   GA:  Población → Individuos → Evolución (cruce + mutación) → Fitness
 *   ACO: Colonia   → Hormigas   → Construcción guiada por feromonas → Fitness
 *
 * Flujo de cada iteración ACO:
 *   1. Evaporación:   las feromonas se reducen (rastro se desvanece).
 *   2. Depósito:      las hormigas élite refuerzan los vuelos que usaron.
 *   3. Construcción:  cada hormiga construye su solución guiada por feromonas + heurística.
 *   4. Evaluación:    se calcula el fitness con el mismo Fitness.java del GA.
 *   5. Actualización: se guarda el mejor global si hay mejora.
 *
 * CRITERIO DE PARADA — solo por tiempo (Ta), igual que el GA:
 *   El bucle corre durante exactamente tiempoLimiteMs milisegundos.
 *   Esto es intencional: garantiza que GA y ACO se ejecuten bajo las mismas
 *   condiciones de tiempo, haciendo que la comparación del IEN sea válida.
 *   (Versiones anteriores paraban también por "iteraciones sin mejora",
 *   lo que hacía que el ACO terminara antes que el GA y no fueran comparables.)
 */
@Component
public class AlgoritmoACO {
 
    /** Porcentaje de hormigas élite que depositan feromona por iteración (top 20%). */
    private static final double PORCENTAJE_ELITE = 0.20;
    /** Iteraciones sin mejora antes de forzar reinicio de feromonas. */
    private static final int LIMITE_ESTANCAMIENTO = 15;
 
    private final Fitness evaluadorFitness;
 
    public AlgoritmoACO(Fitness evaluadorFitness) {
        this.evaluadorFitness = evaluadorFitness;
    }
 
    // =========================================================================
    //  MÉTODO PRINCIPAL
    // =========================================================================
 
    /**
     * Ejecuta el ACO durante exactamente tiempoLimiteMs milisegundos.
     *
     * @param envios               Envíos a planificar en esta ventana temporal.
     * @param mapaAeropuertos      Mapa de aeropuertos con datos completos.
     * @param mapaVuelosPorOrigen  Índice de vuelos por aeropuerto de origen (O(1)).
     * @param capVuelos            Capacidades dinámicas actuales de los vuelos.
     * @param capAlmacenes         Capacidades dinámicas actuales de los almacenes.
     * @param numHormigas          Número de hormigas (análogo al tamaño de población del GA).
     * @param tiempoLimiteMs       Tiempo máximo de ejecución en ms (= Ta × 1000).
     * @return Mejor Individuo encontrado, compatible con Planificador y DataService.
     */
    public Individuo ejecutar(
            List<Envio> envios,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, List<PlanVuelo>> mapaVuelosPorOrigen,
            Map<String, Integer> capVuelos,
            Map<String, Integer> capAlmacenes,
            int numHormigas,
            long tiempoLimiteMs) {
 
        System.out.println("=== ACO - Iniciando Optimización ===");
        System.out.println("    Envíos a procesar: " + envios.size());
        System.out.println("    Hormigas:          " + numHormigas);

        List<Envio> enviosPrioritarios = priorizarEnvios(
            envios,
            mapaAeropuertos,
            mapaVuelosPorOrigen,
            capVuelos,
            capAlmacenes);
        if (!enviosPrioritarios.isEmpty()) {
            System.out.println("    Priorización activa: hard-first con A* + topología + tamaño.");
        }
 
        // --- Crear la colonia (inicializa feromonas uniformes en todos los vuelos) ---
        Colonia colonia = new Colonia(numHormigas, mapaAeropuertos, mapaVuelosPorOrigen);

        // --- Siembra inicial de feromonas usando A* para guiar la colonia hacia rutas factibles ---
        // NOTA: Usamos copias locales para siembra; no modificamos las capacidades reales
        Map<String, Integer> capVuelosCopiaInicial = new HashMap<>(capVuelos);
        Map<String, Integer> capAlmacenesCopiaInicial = new HashMap<>(capAlmacenes);
        sembrarFeromonasAStar(colonia.getFeromenaGrafo(), enviosPrioritarios, mapaAeropuertos, mapaVuelosPorOrigen, capVuelosCopiaInicial, capAlmacenesCopiaInicial, "inicial");
 
        // --- Construcción inicial ---
        // Primera iteración: feromonas iguales → selección guiada principalmente por heurística.
        colonia.construirSoluciones(enviosPrioritarios, capVuelos, capAlmacenes);
        evaluarColonia(colonia, mapaAeropuertos, capVuelos, capAlmacenes);
 
        Hormiga mejorHormiga = seleccionarMejorHormiga(colonia.getHormigas());
        Hormiga mejorGlobal  = copiarHormiga(mejorHormiga);
        System.out.println("    Fitness inicial: " + String.format("%.6f", mejorGlobal.getFitness()));
 
        // --- Bucle principal ACO — controlado SOLO por tiempo (igual que el GA) ---
        long tiempoInicio   = System.currentTimeMillis();
        int  iteracionesTotal = 0;
        int  iteracionesSinMejora = 0;
 
        while ((System.currentTimeMillis() - tiempoInicio) < tiempoLimiteMs) {
 
            // Paso 1 — Evaporación: reducir todas las feromonas
            // El rastro se desvanece para no quedar atrapado en soluciones antiguas
            colonia.getFeromenaGrafo().evaporar();
 
            // Paso 2 — Depósito: solo las hormigas élite refuerzan sus rutas
            // Se ordenan de mayor a menor fitness y solo el top 20% deposita feromona
            List<Hormiga> ordenadas = new ArrayList<>(colonia.getHormigas());
            ordenadas.sort(this::compararHormigas);
            int numElite = Math.max(1, (int)(numHormigas * PORCENTAJE_ELITE));
            for (int i = 0; i < numElite; i++) {
                colonia.getFeromenaGrafo().depositarFeromona(ordenadas.get(i));
            }
 
            // Paso 3 — Construcción: nuevas soluciones guiadas por feromonas actualizadas
            List<Envio> enviosIteracion = priorizarPorRutasPendientes(mejorGlobal, enviosPrioritarios, iteracionesTotal);
            colonia.construirSoluciones(enviosIteracion, capVuelos, capAlmacenes);
            evaluarColonia(colonia, mapaAeropuertos, capVuelos, capAlmacenes);
 
            // Paso 4 — Actualizar mejor global si hay mejora
            Hormiga mejorActual = seleccionarMejorHormiga(colonia.getHormigas());
            if (mejorActual != null && compararHormigas(mejorActual, mejorGlobal) < 0) {
                mejorGlobal = copiarHormiga(mejorActual);
                iteracionesSinMejora = 0;
                System.out.println("    [ACO] Iter " + iteracionesTotal
                        + " → Nuevo mejor fitness: "
                        + String.format("%.6f", mejorGlobal.getFitness()));
            } else {
                iteracionesSinMejora++;
            }

            if (iteracionesSinMejora >= LIMITE_ESTANCAMIENTO) {
                System.out.println("    [ACO] Estancamiento detectado. Reinicio parcial de feromonas + resiembra A*.");
                colonia.getFeromenaGrafo().reiniciarFeromonas();
                // Copias nuevas para resiembra
                Map<String, Integer> capVuelosCopiaReinicio = new HashMap<>(capVuelos);
                Map<String, Integer> capAlmacenesCopiaReinicio = new HashMap<>(capAlmacenes);
                sembrarFeromonasAStar(colonia.getFeromenaGrafo(), enviosPrioritarios, mapaAeropuertos, mapaVuelosPorOrigen, capVuelosCopiaReinicio, capAlmacenesCopiaReinicio, "reinicio");
                iteracionesSinMejora = 0;
            }
 
            iteracionesTotal++;
        }
 
        System.out.println("=== ACO - Finalizado ===");
        System.out.println("    Iteraciones ejecutadas: " + iteracionesTotal);
        System.out.println("    Mejor Fitness:          "
                + String.format("%.6f", mejorGlobal.getFitness()));

        Hormiga mejorRescatada = rescatarRutasPendientes(
            mejorGlobal,
            mapaAeropuertos,
            mapaVuelosPorOrigen,
            capVuelos,
            capAlmacenes,
            colonia.getFeromenaGrafo());

        if (compararHormigas(mejorRescatada, mejorGlobal) < 0) {
            mejorGlobal = mejorRescatada;
            System.out.println("    [ACO] Rescate final aplicado: mejoró la solución y redujo rutas pendientes.");
        }

        imprimirResumen(mejorGlobal);
 
        // Convertir la mejor Hormiga a Individuo para compatibilidad con el sistema
        return new Individuo(mejorGlobal.getRutas());
    }
 
    // =========================================================================
    //  MÉTODOS PRIVADOS DE APOYO
    // =========================================================================
 
    /**
     * Evalúa el fitness de todas las hormigas usando Fitness.java del GA.
     * Usar la misma función objetivo garantiza que la comparación GA vs ACO
     * en el IEN sea válida (no se comparan métricas distintas).
     */
    private void evaluarColonia(Colonia colonia,
                             Map<String, Aeropuerto> mapaAeropuertos,
                             Map<String, Integer> capVuelos,
                             Map<String, Integer> capAlmacenes) {
        for (Hormiga hormiga : colonia.getHormigas()) {
            Individuo indv = new Individuo(hormiga.getRutas());
            // Fitness solo lee las capacidades; reutilizar las snapshots evita copiar mapas por hormiga.
            evaluadorFitness.evaluar(indv, mapaAeropuertos, capVuelos, capAlmacenes);
            hormiga.setFitness(indv.getFitness());
            hormiga.setFeromonaDepositada(indv.getFitness());
        }
    }
 
    /**
     * Crea una copia de una hormiga para preservar el mejor global entre iteraciones.
     * Sin esta copia, la referencia se sobreescribiría en la siguiente construcción.
     */
    private Hormiga copiarHormiga(Hormiga original) {
        Hormiga copia = new Hormiga(new ArrayList<>(original.getRutas()));
        copia.setFitness(original.getFitness());
        copia.setFeromonaDepositada(original.getFeromonaDepositada());
        return copia;
    }
 
    /**
     * Imprime un resumen de los estados de las rutas en la mejor solución encontrada.
     */
    private void imprimirResumen(Hormiga mejorHormiga) {
        long planificadas  = mejorHormiga.getRutas().stream()
                .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA).count();
        long inalcanzables = mejorHormiga.getRutas().stream()
                .filter(r -> r.getEstado() == EstadoRuta.INALCANZABLE).count();
        long sinRuta       = mejorHormiga.getRutas().stream()
                .filter(r -> r.getEstado() == EstadoRuta.SIN_RUTA).count();
 
        System.out.println("    Rutas PLANIFICADAS:  " + planificadas);
        System.out.println("    Rutas INALCANZABLES: " + inalcanzables);
        System.out.println("    Rutas SIN_RUTA:      " + sinRuta);
    }

    /**
     * Intenta convertir las rutas fallidas de la mejor hormiga en rutas planificadas.
     * Primero intenta A*; si no logra ruta, vuelve a intentar con ACO usando las feromonas aprendidas.
     */
    private Hormiga rescatarRutasPendientes(Hormiga original,
                                            Map<String, Aeropuerto> mapaAeropuertos,
                                            Map<String, List<PlanVuelo>> mapaVuelosPorOrigen,
                                            Map<String, Integer> capVuelos,
                                            Map<String, Integer> capAlmacenes,
                                            FeromenaGrafo feromenaGrafo) {

        BuscadorRutas buscadorAStar = new BuscadorRutas();
        BuscadorRutasACO buscadorACO = new BuscadorRutasACO();
        Random random = new Random(20240501L);

        Map<String, Integer> capVuelosTrabajo = new HashMap<>(capVuelos);
        Map<String, Integer> capAlmacenesTrabajo = new HashMap<>(capAlmacenes);
        List<Ruta> rutasRescatadas = new ArrayList<>();

        int rescatadas = 0;
        int intentosFallidos = 0;

        for (Ruta ruta : original.getRutas()) {
            if (ruta.getEstado() == EstadoRuta.PLANIFICADA && ruta.getVuelos() != null && !ruta.getVuelos().isEmpty()) {
                rutasRescatadas.add(ruta);
                aplicarRutaEnCapacidades(ruta, capVuelosTrabajo, capAlmacenesTrabajo);
                continue;
            }

            Envio envio = ruta.getEnvio();

            // Hacer copia local para cada intento de rescate (no modificar capVuelosTrabajo si falla)
            Map<String, Integer> capVuelosIntento = new HashMap<>(capVuelosTrabajo);
            Map<String, Integer> capAlmacenesIntento = new HashMap<>(capAlmacenesTrabajo);

            Ruta rutaAStar = buscadorAStar.buscarRuta(
                    envio,
                    null,
                    mapaVuelosPorOrigen,
                    mapaAeropuertos,
                    capVuelosIntento,
                    capAlmacenesIntento,
                    random,
                    0.0);

            if (rutaAStar.getEstado() == EstadoRuta.PLANIFICADA) {
                rutasRescatadas.add(rutaAStar);
                // Solo actualizamos capacidades si el rescate fue exitoso
                capVuelosTrabajo = capVuelosIntento;
                capAlmacenesTrabajo = capAlmacenesIntento;
                rescatadas++;
                continue;
            }

            // Reintentar con ACO si A* falla
            capVuelosIntento = new HashMap<>(capVuelosTrabajo);
            capAlmacenesIntento = new HashMap<>(capAlmacenesTrabajo);

            Ruta rutaAco = buscadorACO.construirRuta(
                    envio,
                    mapaVuelosPorOrigen,
                    mapaAeropuertos,
                    feromenaGrafo,
                    capVuelosIntento,
                    capAlmacenesIntento,
                    random);

            if (rutaAco.getEstado() == EstadoRuta.PLANIFICADA) {
                rutasRescatadas.add(rutaAco);
                // Solo actualizamos capacidades si el rescate fue exitoso
                capVuelosTrabajo = capVuelosIntento;
                capAlmacenesTrabajo = capAlmacenesIntento;
                rescatadas++;
            } else {
                rutasRescatadas.add(ruta);
                intentosFallidos++;
            }
        }

        if (rescatadas > 0) {
            System.out.println("    [ACO] Rescate final: " + rescatadas + " rutas recuperadas, " + intentosFallidos + " siguen pendientes.");
        }

        int sinRutaResiduales = normalizarSinRutaResidual(rutasRescatadas);
        if (sinRutaResiduales > 0) {
            System.out.println("    [ACO] Normalización final: " + sinRutaResiduales + " rutas SIN_RUTA residuales convertidas a INALCANZABLE.");
        }

        Hormiga rescatada = new Hormiga(rutasRescatadas);
        Individuo evaluacion = new Individuo(rutasRescatadas);
        evaluadorFitness.evaluar(evaluacion, mapaAeropuertos, capVuelosTrabajo, capAlmacenesTrabajo);
        rescatada.setFitness(evaluacion.getFitness());
        rescatada.setFeromonaDepositada(evaluacion.getFitness());
        return rescatada;
    }

    /**
     * Convierte los estados SIN_RUTA residuales en INALCANZABLE para que el resultado final
     * refleje un fallo estructural confirmado, no un fallo intermedio de búsqueda.
     */
    private int normalizarSinRutaResidual(List<Ruta> rutas) {
        int convertidas = 0;
        for (Ruta ruta : rutas) {
            if (ruta != null && ruta.getEstado() == EstadoRuta.SIN_RUTA) {
                ruta.setEstado(EstadoRuta.INALCANZABLE);
                convertidas++;
            }
        }
        return convertidas;
    }

    /**
     * Aplica el consumo de una ruta ya planificada sobre las capacidades de trabajo del rescate.
     */
    private void aplicarRutaEnCapacidades(Ruta ruta,
                                          Map<String, Integer> capVuelosTrabajo,
                                          Map<String, Integer> capAlmacenesTrabajo) {
        if (ruta == null || ruta.getEnvio() == null || ruta.getVuelos() == null) {
            return;
        }

        Envio envio = ruta.getEnvio();
        capAlmacenesTrabajo.put(
                envio.getAeropuertoOrigen(),
                capAlmacenesTrabajo.getOrDefault(envio.getAeropuertoOrigen(), 0) - envio.getCantidadMaletas());

        for (PlanVuelo vuelo : ruta.getVuelos()) {
            String clave = vuelo.getOrigen() + "-" + vuelo.getDestino() + "-" + vuelo.getHoraSalida();
            capVuelosTrabajo.put(
                    clave,
                    capVuelosTrabajo.getOrDefault(clave, vuelo.getCapacidad()) - envio.getCantidadMaletas());
            capAlmacenesTrabajo.put(
                    vuelo.getDestino(),
                    capAlmacenesTrabajo.getOrDefault(vuelo.getDestino(), 0) - envio.getCantidadMaletas());
        }
    }

    /**
     * Ordena los envíos por dificultad estimada: primero los más frágiles.
     * La señal A* marca rutas imposibles o largas; la topología y el tamaño
     * ayudan a que la colonia no agote capacidad en envíos fáciles primero.
     */
    private List<Envio> priorizarEnvios(List<Envio> envios,
                                        Map<String, Aeropuerto> mapaAeropuertos,
                                        Map<String, List<PlanVuelo>> mapaVuelosPorOrigen,
                                        Map<String, Integer> capVuelos,
                                        Map<String, Integer> capAlmacenes) {

        if (envios == null || envios.isEmpty()) {
            return Collections.emptyList();
        }

        Map<String, Integer> salidasPorAeropuerto = new HashMap<>();
        Map<String, Integer> entradasPorAeropuerto = new HashMap<>();
        for (Map.Entry<String, List<PlanVuelo>> entry : mapaVuelosPorOrigen.entrySet()) {
            salidasPorAeropuerto.put(entry.getKey(), entry.getValue().size());
            for (PlanVuelo vuelo : entry.getValue()) {
                entradasPorAeropuerto.merge(vuelo.getDestino(), 1, Integer::sum);
            }
        }

        BuscadorRutas buscador = new BuscadorRutas();
        Random random = new Random(8675309L);

        List<EnvioConPrioridad> priorizados = new ArrayList<>();
        for (Envio envio : envios) {
            int salidasOrigen = salidasPorAeropuerto.getOrDefault(envio.getAeropuertoOrigen(), 0);
            int entradasDestino = entradasPorAeropuerto.getOrDefault(envio.getAeropuertoDestino(), 0);

            double dificultadHeuristica = calcularDificultadHeuristica(
                envio,
                salidasOrigen,
                entradasDestino);

            double dificultad;
            if (dificultadHeuristica >= 500.0) {
            Map<String, Integer> capVuelosCopia = new HashMap<>(capVuelos);
            Map<String, Integer> capAlmacenesCopia = new HashMap<>(capAlmacenes);

            Ruta estimada = buscador.buscarRuta(
                envio,
                null,
                mapaVuelosPorOrigen,
                mapaAeropuertos,
                capVuelosCopia,
                capAlmacenesCopia,
                random,
                0.0);

            dificultad = calcularDificultadEnvio(
                envio,
                estimada,
                salidasOrigen,
                entradasDestino);
            } else {
            dificultad = dificultadHeuristica;
            }

            priorizados.add(new EnvioConPrioridad(envio, dificultad));
        }

        priorizados.sort((a, b) -> Double.compare(b.prioridad, a.prioridad));

        List<Envio> ordenados = new ArrayList<>(priorizados.size());
        for (EnvioConPrioridad item : priorizados) {
            ordenados.add(item.envio);
        }
        return ordenados;
    }

    private double calcularDificultadEnvio(Envio envio,
                                           Ruta estimada,
                                           int salidasOrigen,
                                           int entradasDestino) {
        double score = calcularDificultadHeuristica(envio, salidasOrigen, entradasDestino);

        // Si A* no encuentra camino, este envío debe ir primero.
        if (estimada == null || estimada.getEstado() != EstadoRuta.PLANIFICADA) {
            score += 10000.0;
        } else {
            score += estimada.getVuelos().size() * 120.0;
            score += estimada.getTiempoTotalMinutos() / 2.0;
        }

        // Menos alternativas => más frágil.
        score += Math.max(0, 15 - salidasOrigen) * 80.0;
        score += Math.max(0, 15 - entradasDestino) * 60.0;

        // Cargas más grandes consumen más capacidad y conviene resolverlas antes.
        score += envio.getCantidadMaletas() * 25.0;

        return score;
    }

    private double calcularDificultadHeuristica(Envio envio,
                                                int salidasOrigen,
                                                int entradasDestino) {
        double score = 0.0;
        score += Math.max(0, 15 - salidasOrigen) * 80.0;
        score += Math.max(0, 15 - entradasDestino) * 60.0;
        score += envio.getCantidadMaletas() * 25.0;
        return score;
    }

    private static class EnvioConPrioridad {
        final Envio envio;
        final double prioridad;

        EnvioConPrioridad(Envio envio, double prioridad) {
            this.envio = envio;
            this.prioridad = prioridad;
        }
    }

    private void sembrarFeromonasAStar(FeromenaGrafo feromena,
                                        List<Envio> enviosOrdenados,
                                        Map<String, Aeropuerto> mapaAeropuertos,
                                        Map<String, List<PlanVuelo>> mapaVuelosPorOrigen,
                                        Map<String, Integer> capVuelos,
                                        Map<String, Integer> capAlmacenes,
                                        String etiqueta) {
        try {
            System.out.println("    Siembra " + etiqueta + ": ejecutando A* para rutas semilla...");
            BuscadorRutas buscador = new BuscadorRutas();
            Random seedRand = new Random(12345);
            final double SEMILLA_FACTOR = 5.0;

            Map<String, Integer> capVuelosCopia = new HashMap<>(capVuelos);
            Map<String, Integer> capAlmacenesCopia = new HashMap<>(capAlmacenes);

            for (Envio e : enviosOrdenados) {
                Ruta r = buscador.buscarRuta(e, null, mapaVuelosPorOrigen, mapaAeropuertos, capVuelosCopia, capAlmacenesCopia, seedRand, 0.0);
                if (r != null && r.getEstado() == EstadoRuta.PLANIFICADA && r.getVuelos() != null) {
                    for (PlanVuelo v : r.getVuelos()) {
                        String clave = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
                        feromena.aumentarFeromona(clave, SEMILLA_FACTOR);
                    }
                }
            }
            System.out.println("    Siembra " + etiqueta + " completada.");
        } catch (Exception ex) {
            System.out.println("    [WARN] Error en siembra " + etiqueta + " A*: " + ex.getMessage());
        }
    }

    private List<Envio> priorizarPorRutasPendientes(Hormiga mejorGlobal, List<Envio> base, int iteracion) {
        if (mejorGlobal == null || mejorGlobal.getRutas() == null || mejorGlobal.getRutas().isEmpty()) {
            return diversificarOrden(base, iteracion);
        }

        Set<String> idsPendientes = new HashSet<>();
        for (Ruta r : mejorGlobal.getRutas()) {
            if (r.getEstado() != EstadoRuta.PLANIFICADA && r.getEnvio() != null) {
                idsPendientes.add(r.getEnvio().getIdEnvio());
            }
        }

        if (idsPendientes.isEmpty()) {
            return diversificarOrden(base, iteracion);
        }

        List<Envio> pendientes = new ArrayList<>();
        List<Envio> restantes = new ArrayList<>();
        for (Envio e : base) {
            if (idsPendientes.contains(e.getIdEnvio())) {
                pendientes.add(e);
            } else {
                restantes.add(e);
            }
        }

        Collections.shuffle(pendientes, new Random(97L + iteracion));
        Collections.shuffle(restantes, new Random(197L + iteracion));

        List<Envio> resultado = new ArrayList<>(base.size());
        resultado.addAll(pendientes);
        resultado.addAll(restantes);
        return resultado;
    }

    private List<Envio> diversificarOrden(List<Envio> base, int iteracion) {
        List<Envio> copia = new ArrayList<>(base);
        Collections.shuffle(copia, new Random(313L + iteracion));
        return copia;
    }

    private Hormiga seleccionarMejorHormiga(List<Hormiga> hormigas) {
        if (hormigas == null || hormigas.isEmpty()) {
            return null;
        }
        return hormigas.stream().min(this::compararHormigas).orElse(null);
    }

    private int compararHormigas(Hormiga h1, Hormiga h2) {
        if (h1 == null && h2 == null) return 0;
        if (h1 == null) return 1;
        if (h2 == null) return -1;

        EstadoConteo c1 = contarEstados(h1);
        EstadoConteo c2 = contarEstados(h2);

        if (c1.planificadas != c2.planificadas) return Integer.compare(c2.planificadas, c1.planificadas);
        if (c1.inalcanzables != c2.inalcanzables) return Integer.compare(c1.inalcanzables, c2.inalcanzables);
        if (c1.sinRuta != c2.sinRuta) return Integer.compare(c1.sinRuta, c2.sinRuta);
        return Double.compare(h2.getFitness(), h1.getFitness());
    }

    private EstadoConteo contarEstados(Hormiga h) {
        EstadoConteo c = new EstadoConteo();
        if (h == null || h.getRutas() == null) return c;
        for (Ruta r : h.getRutas()) {
            if (r.getEstado() == EstadoRuta.PLANIFICADA) c.planificadas++;
            else if (r.getEstado() == EstadoRuta.INALCANZABLE) c.inalcanzables++;
            else if (r.getEstado() == EstadoRuta.SIN_RUTA) c.sinRuta++;
        }
        return c;
    }

    private static class EstadoConteo {
        int planificadas;
        int inalcanzables;
        int sinRuta;
    }
}
