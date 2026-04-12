package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class Fitness {

    // Pesos de penalización para cada tipo de problema
    // Estos valores son parámetros ajustables según el escenario
    private static final double ALPHA = 10.0; // Peso envío sin ruta (muy grave)
    private static final double BETA  = 5.0;  // Peso envío retrasado (grave)
    private static final double GAMMA = 2.0;  // Peso almacén saturado (moderado)
    private static final double DELTA = 3.0;  // Peso vuelo saturado (moderado-grave)

    /**
     * Evalúa el fitness de un individuo completo.
     * Recorre todas sus rutas y calcula las penalizaciones por:
     * 1. Envíos sin ruta asignada
     * 2. Envíos que exceden el plazo de entrega
     * 3. Almacenes saturados
     * 4. Vuelos con exceso de capacidad
     *
     * @param individuo       el individuo a evaluar
     * @param mapaAeropuertos mapa de aeropuertos para obtener capacidades
     * @param todosLosVuelos  lista de todos los vuelos para obtener capacidades
     */
    public void evaluar(Individuo individuo,
                        Map<String, Aeropuerto> mapaAeropuertos,
                        List<PlanVuelo> todosLosVuelos) {

        // Contadores de problemas encontrados
        int enviosSinRuta = 0;
        int enviosRetrasados = 0;

        // Mapa para acumular cuántas maletas pasan por cada aeropuerto
        // Clave: código del aeropuerto, Valor: total de maletas acumuladas
        Map<String, Integer> maletasPorAeropuerto = new HashMap<>();

        // Mapa para acumular cuántas maletas usa cada vuelo
        // Clave: "ORIG-DEST-HH:MM", Valor: total de maletas asignadas
        Map<String, Integer> maletasPorVuelo = new HashMap<>();

        // Mapa de capacidades originales de vuelos para comparar
        // Clave: "ORIG-DEST-HH:MM", Valor: capacidad original del vuelo
        Map<String, Integer> capacidadesOriginalesVuelos = new HashMap<>();
        for (PlanVuelo v : todosLosVuelos) {
            capacidadesOriginalesVuelos.put(claveVuelo(v), v.getCapacidad());
        }

        // ── Evaluar cada ruta del individuo ───────────────────────────────
        for (Ruta ruta : individuo.getRutas()) {

            Envio envio = ruta.getEnvio();

            // ── Penalización 1: Envío sin ruta ────────────────────────────
            if (ruta.getEstado() == EstadoRuta.SIN_RUTA
                    || ruta.getVuelos() == null
                    || ruta.getVuelos().isEmpty()) {
                enviosSinRuta++;
                continue; // No hay más que evaluar para esta ruta
            }

            // ── Penalización 2: Envío que excede el plazo ─────────────────
            // Determinar plazo máximo según continente de origen y destino
            Aeropuerto aeropOrigen = mapaAeropuertos.get(
                envio.getAeropuertoOrigen()
            );
            Aeropuerto aeropDestino = mapaAeropuertos.get(
                envio.getAeropuertoDestino()
            );

            // Por defecto 2 días si no se encuentra el aeropuerto
            long plazoMaximoMinutos = 48 * 60;
            if (aeropOrigen != null && aeropDestino != null) {
                if (aeropOrigen.getContinente()
                        .equals(aeropDestino.getContinente())) {
                    plazoMaximoMinutos = 24 * 60; // 1 día mismo continente
                }
            }

            // El tiempoTotalMinutos ya incluye los 10 min de recojo
            // (se sumaron en Población al construir la ruta)
            if (ruta.getTiempoTotalMinutos() > plazoMaximoMinutos) {
                enviosRetrasados++;
            }

            // ── Acumular maletas por aeropuerto y por vuelo ───────────────
            for (PlanVuelo vuelo : ruta.getVuelos()) {

                // Acumular maletas en el aeropuerto de destino de cada vuelo
                // porque según la problemática siempre ingresan al almacén
                String codigoAeropuerto = vuelo.getDestino();
                maletasPorAeropuerto.put(
                    codigoAeropuerto,
                    maletasPorAeropuerto.getOrDefault(
                        codigoAeropuerto, 0
                    ) + envio.getCantidadMaletas()
                );

                // Acumular maletas asignadas a cada vuelo específico
                String claveV = claveVuelo(vuelo);
                maletasPorVuelo.put(
                    claveV,
                    maletasPorVuelo.getOrDefault(
                        claveV, 0
                    ) + envio.getCantidadMaletas()
                );
            }
        }

        // ── Penalización 3: Almacenes saturados ───────────────────────────
        // Verificar cuántos aeropuertos tienen más maletas que su capacidad
        int almacenesSaturados = 0;
        for (Map.Entry<String, Integer> entry
                : maletasPorAeropuerto.entrySet()) {

            String codigoAeropuerto = entry.getKey();
            int maletasEnAeropuerto = entry.getValue();

            Aeropuerto aeropuerto = mapaAeropuertos.get(codigoAeropuerto);
            if (aeropuerto != null
                    && maletasEnAeropuerto > aeropuerto.getCapacidad()) {
                almacenesSaturados++;
            }
        }

        // ── Penalización 4: Vuelos saturados ──────────────────────────────
        // Verificar cuántos vuelos tienen más maletas que su capacidad
        // Esto puede ocurrir después del cruce y mutación del GA
        int vuelosSaturados = 0;
        for (Map.Entry<String, Integer> entry
                : maletasPorVuelo.entrySet()) {

            String claveV = entry.getKey();
            int maletasEnVuelo = entry.getValue();

            // Comparar con la capacidad original del vuelo
            int capacidadOriginal = capacidadesOriginalesVuelos
                .getOrDefault(claveV, Integer.MAX_VALUE);

            if (maletasEnVuelo > capacidadOriginal) {
                vuelosSaturados++;
            }
        }

        // ── Calcular fitness final ─────────────────────────────────────────
        // Fórmula: 1 / (1 + penalizaciones totales)
        // Si no hay penalizaciones → fitness = 1.0 (perfecto)
        // A más penalizaciones → fitness se acerca a 0.0
        double penalizaciones = (ALPHA * enviosSinRuta)
                              + (BETA  * enviosRetrasados)
                              + (GAMMA * almacenesSaturados)
                              + (DELTA * vuelosSaturados);

        double fitness = 1.0 / (1.0 + penalizaciones);

        // Guardar el fitness calculado en el individuo
        individuo.setFitness(fitness);
    }

    /**
     * Evalúa todos los individuos de una población.
     *
     * @param poblacion       la población a evaluar
     * @param mapaAeropuertos mapa de aeropuertos para obtener capacidades
     * @param todosLosVuelos  lista de todos los vuelos para obtener capacidades
     */
    public void evaluarPoblacion(Poblacion poblacion,
                                  Map<String, Aeropuerto> mapaAeropuertos,
                                  List<PlanVuelo> todosLosVuelos) {
        for (Individuo individuo : poblacion.getIndividuos()) {
            evaluar(individuo, mapaAeropuertos, todosLosVuelos);
        }
    }

    /**
     * Genera una clave única para identificar un vuelo específico.
     * Formato: "ORIG-DEST-HH:MM"
     */
    private String claveVuelo(PlanVuelo vuelo) {
        return vuelo.getOrigen() + "-" + vuelo.getDestino()
             + "-" + vuelo.getHoraSalida();
    }
}