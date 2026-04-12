package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Fitness {

    // Pesos de penalización para cada tipo de problema
    // Estos valores son parámetros ajustables según el escenario
    private static final double ALPHA = 10.0; // Peso envío sin ruta (muy grave)
    private static final double BETA  = 5.0;  // Peso envío retrasado (grave)
    private static final double GAMMA = 2.0;  // Peso almacén saturado (moderado)

    // Tiempo de recojo en destino final en minutos (parámetro del problema)
    private static final int TIEMPO_RECOJO_DESTINO = 10;

    /**
     * Evalúa el fitness de un individuo completo.
     * Recorre todas sus rutas y calcula las penalizaciones.
     * El resultado se guarda directamente en el individuo.
     *
     * @param individuo      el individuo a evaluar
     * @param mapaAeropuertos mapa de aeropuertos para obtener capacidades
     */
    public void evaluar(Individuo individuo,
                        Map<String, Aeropuerto> mapaAeropuertos) {

        // Contadores de problemas encontrados
        int enviosSinRuta = 0;
        int enviosRetrasados = 0;

        // Mapa para acumular cuántas maletas pasan por cada aeropuerto
        // Clave: código del aeropuerto, Valor: total de maletas acumuladas
        Map<String, Integer> maletasPorAeropuerto = new HashMap<>();

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
            // Determinar plazo máximo según continente
            Aeropuerto aeropOrigen = mapaAeropuertos.get(
                envio.getAeropuertoOrigen()
            );
            Aeropuerto aeropDestino = mapaAeropuertos.get(
                envio.getAeropuertoDestino()
            );

            long plazoMaximoMinutos = 48 * 60; // Por defecto 2 días
            if (aeropOrigen != null && aeropDestino != null) {
                if (aeropOrigen.getContinente()
                        .equals(aeropDestino.getContinente())) {
                    plazoMaximoMinutos = 24 * 60; // 1 día mismo continente
                }
            }

            // El tiempo total ya incluye los 10 min de recojo en destino
            // (se sumaron en Población al construir la ruta)
            if (ruta.getTiempoTotalMinutos() > plazoMaximoMinutos) {
                enviosRetrasados++;
            }

            // ── Acumular maletas por aeropuerto ───────────────────────────
            // Por cada vuelo de la ruta, las maletas pasan por el aeropuerto
            // de destino de ese vuelo (escala o destino final)
            // porque según la problemática siempre ingresan al almacén
            for (PlanVuelo vuelo : ruta.getVuelos()) {
                String codigoAeropuerto = vuelo.getDestino();
                maletasPorAeropuerto.put(
                    codigoAeropuerto,
                    maletasPorAeropuerto.getOrDefault(
                        codigoAeropuerto, 0
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

        // ── Calcular fitness final ─────────────────────────────────────────
        // Fórmula: 1 / (1 + penalizaciones)
        // Si no hay penalizaciones → fitness = 1 / (1+0) = 1.0 (perfecto)
        // A más penalizaciones → fitness se acerca a 0.0
        double penalizaciones = (ALPHA * enviosSinRuta)
                              + (BETA  * enviosRetrasados)
                              + (GAMMA * almacenesSaturados);

        double fitness = 1.0 / (1.0 + penalizaciones);

        // Guardar el fitness en el individuo
        individuo.setFitness(fitness);
    }

    /**
     * Evalúa todos los individuos de una población.
     *
     * @param poblacion       la población a evaluar
     * @param mapaAeropuertos mapa de aeropuertos para obtener capacidades
     */
    public void evaluarPoblacion(Poblacion poblacion,
                                  Map<String, Aeropuerto> mapaAeropuertos) {
        for (Individuo individuo : poblacion.getIndividuos()) {
            evaluar(individuo, mapaAeropuertos);
        }
    }
}