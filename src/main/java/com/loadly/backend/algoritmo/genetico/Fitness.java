package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
public class Fitness {

    // Pesos de penalización para cada tipo de problema
    // Ajuste de pesos para trabajar con "excesos" (gradientes) en lugar de contadores simples.
    private static final double ALPHA = 10000.0; // Peso envío sin ruta (muy grave, sigue siendo binario)
    private static final double BETA  = 10.0;    // Peso por CADA MINUTO de retraso
    private static final double GAMMA = 50.0;    // Peso por CADA MALETA extra en almacén (Aplica para Origen, Escala y Destino)
    private static final double DELTA = 50.0;    // Peso por CADA MALETA extra en vuelo
    
    // 💡 NUEVO (OPCIÓN B): Penalización sutil por cada minuto que dura el viaje, incluso si está a tiempo.
    // Esto obliga a la IA a buscar vuelos más rápidos para subir su calificación.
    private static final double EPSILON = 0.01; 

    /**
     * Evalúa el fitness de un individuo completo.
     * Recorre todas sus rutas y calcula las penalizaciones por:
     * 1. Envíos sin ruta asignada
     * 2. Envíos que exceden el plazo de entrega
     * 3. Almacenes saturados (Origen, Escalas y Destino)
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
        
        // Acumuladores de "cantidades de error"
        long minutosTotalesRetraso = 0;
        long totalMaletasExcesoAlmacen = 0;
        long totalMaletasExcesoVuelos = 0;
        
        // 💡 NUEVO: Acumulador del tiempo total de TODAS las maletas juntas
        long sumatoriaTiempoTodasLasRutas = 0;

        // Mapa para acumular cuántas maletas pasan por cada aeropuerto
        // Clave: código del aeropuerto, Valor: total de maletas acumuladas
        Map<String, Integer> maletasPorAeropuerto = new HashMap<>();

        // Mapa para acumular cuántas maletas usa cada vuelo
        // Clave: "ORIG-DEST-HH:MM", Valor: total de maletas asignadas
        Map<String, Integer> maletasPorVuelo = new HashMap<>();

        // Mapa de capacidades originales de vuelos para comparar
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
            
            // 💡 NUEVO: Sumamos el tiempo de esta ruta al total general del individuo
            sumatoriaTiempoTodasLasRutas += ruta.getTiempoTotalMinutos();

            // ── Penalización 2: Envío que excede el plazo ─────────────────
            Aeropuerto aeropOrigen = mapaAeropuertos.get(envio.getAeropuertoOrigen());
            Aeropuerto aeropDestino = mapaAeropuertos.get(envio.getAeropuertoDestino());

            long plazoMaximoMinutos = 48 * 60; // 2 días por defecto
            if (aeropOrigen != null && aeropDestino != null) {
                if (aeropOrigen.getContinente().equals(aeropDestino.getContinente())) {
                    plazoMaximoMinutos = 24 * 60; // 1 día mismo continente
                }
            }

            if (ruta.getTiempoTotalMinutos() > plazoMaximoMinutos) {
                minutosTotalesRetraso += (ruta.getTiempoTotalMinutos() - plazoMaximoMinutos);
            }

            // Sumar las maletas al almacén de ORIGEN
            // Ya que si la maleta tiene un vuelo, significa que el cliente la dejó en el mostrador
            // de origen y estuvo esperando allí ocupando espacio físico.
            String codigoOrigen = envio.getAeropuertoOrigen();
            maletasPorAeropuerto.put(
                codigoOrigen,
                maletasPorAeropuerto.getOrDefault(codigoOrigen, 0) + envio.getCantidadMaletas()
            );

            // ── Acumular maletas por aeropuerto (escalas/destino) y por vuelo
            for (PlanVuelo vuelo : ruta.getVuelos()) {

                // Acumular maletas en el aeropuerto de destino de cada vuelo (Escala o Destino Final)
                String codigoAeropuertoDestino = vuelo.getDestino();
                maletasPorAeropuerto.put(
                    codigoAeropuertoDestino,
                    maletasPorAeropuerto.getOrDefault(codigoAeropuertoDestino, 0) + envio.getCantidadMaletas()
                );

                // Acumular maletas asignadas a cada vuelo específico
                String claveV = claveVuelo(vuelo);
                maletasPorVuelo.put(
                    claveV,
                    maletasPorVuelo.getOrDefault(claveV, 0) + envio.getCantidadMaletas()
                );
            }
        }

        // ── Penalización 3: Almacenes saturados ───────────────────────────
        for (Map.Entry<String, Integer> entry : maletasPorAeropuerto.entrySet()) {
            String codigoAeropuerto = entry.getKey();
            int maletasEnAeropuerto = entry.getValue();

            Aeropuerto aeropuerto = mapaAeropuertos.get(codigoAeropuerto);
            if (aeropuerto != null && maletasEnAeropuerto > aeropuerto.getCapacidad()) {
                // Sumamos exactamente CUÁNTAS maletas sobran en el almacén (sea origen, escala o destino)
                totalMaletasExcesoAlmacen += (maletasEnAeropuerto - aeropuerto.getCapacidad());
            }
        }

        // ── Penalización 4: Vuelos saturados ──────────────────────────────
        for (Map.Entry<String, Integer> entry : maletasPorVuelo.entrySet()) {
            String claveV = entry.getKey();
            int maletasEnVuelo = entry.getValue();
            int capacidadOriginal = capacidadesOriginalesVuelos.getOrDefault(claveV, Integer.MAX_VALUE);

            if (maletasEnVuelo > capacidadOriginal) {
                totalMaletasExcesoVuelos += (maletasEnVuelo - capacidadOriginal);
            }
        }

        // ── Calcular fitness final ─────────────────────────────────────────
        // 💡 NUEVO: Sumamos la penalización por desgaste de tiempo (EPSILON)
        double penalizaciones = (ALPHA * enviosSinRuta)
                              + (BETA  * minutosTotalesRetraso)
                              + (GAMMA * totalMaletasExcesoAlmacen)
                              + (DELTA * totalMaletasExcesoVuelos)
                              + (EPSILON * sumatoriaTiempoTodasLasRutas); // <-- EL TRUCO MAESTRO

        double fitness = 1.0 / (1.0 + penalizaciones);

        // Guardar el fitness calculado en el individuo
        individuo.setFitness(fitness);
    }

    public void evaluarPoblacion(Poblacion poblacion,
                                 Map<String, Aeropuerto> mapaAeropuertos,
                                 List<PlanVuelo> todosLosVuelos) {
        for (Individuo individuo : poblacion.getIndividuos()) {
            evaluar(individuo, mapaAeropuertos, todosLosVuelos);
        }
    }

    private String claveVuelo(PlanVuelo vuelo) {
        return vuelo.getOrigen() + "-" + vuelo.getDestino()
             + "-" + vuelo.getHoraSalida();
    }
}