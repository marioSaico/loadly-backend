package com.loadly.backend.algoritmo.genetico;
 
import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;
 
import java.util.HashMap;
import java.util.Map;
 
@Component
public class Fitness {
 
    // Penalización dura: se aplica por cada violación detectada.
    // Es lo suficientemente grande para que el GA siempre prefiera
    // cualquier ruta válida sobre una que viole restricciones.
    private static final double PENALIZACION_DURA = 999999.0;
 
    /**
     * Evalúa a toda la población.
     */
    public void evaluarPoblacion(
            Poblacion poblacion,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, Integer> capVuelos) {
        for (Individuo individuo : poblacion.getIndividuos()) {
            evaluar(individuo, mapaAeropuertos, capVuelos);
        }
    }
 
    /**
     * Evalúa el fitness de un individuo.
     *
     * El fitness tiene dos partes:
     *
     * PARTE 1 — Por cada ruta individualmente:
     *   - SIN_RUTA o INALCANZABLE → penalización dura
     *   - Excede SLA              → penalización dura
     *   - Válida y a tiempo       → costo += minutos de viaje
     *
     * PARTE 2 — Acumulado de todo el individuo:
     *   - Vuelo con más maletas de las que cabe   → penalización dura por vuelo
     *   - Almacén con más maletas de las que cabe → penalización dura por almacén
     *
     * La parte 2 es necesaria porque la mutación llama a A* sin validar
     * capacidades, pudiendo generar rutas que individualmente parecen válidas
     * pero que colectivamente desbordan vuelos o almacenes.
     *
     * fitness = 1 / (1 + costoTotal) → mayor es mejor.
     */
    public void evaluar(
            Individuo individuo,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, Integer> capVuelos) {
 
        double costoTotal = 0.0;
 
        // Acumuladores para verificar violaciones agregadas del individuo completo
        // (un vuelo o almacén puede ser compartido por varios envíos)
        Map<String, Integer> maletasPorVuelo    = new HashMap<>();
        Map<String, Integer> maletasPorAlmacen  = new HashMap<>();
 
        // ── PARTE 1: Evaluar cada ruta individualmente ────────────────────────
        for (Ruta ruta : individuo.getRutas()) {
            Envio envio = ruta.getEnvio();
 
            // Caso 1: Sin ruta
            if (ruta.getEstado() == EstadoRuta.SIN_RUTA ||
                ruta.getEstado() == EstadoRuta.INALCANZABLE ||
                ruta.getVuelos() == null ||
                ruta.getVuelos().isEmpty()) {
                costoTotal += PENALIZACION_DURA;
                continue;
            }
 
            // Acumular maletas en almacén origen
            maletasPorAlmacen.merge(
                envio.getAeropuertoOrigen(),
                envio.getCantidadMaletas(),
                Integer::sum
            );
 
            // Acumular maletas en cada vuelo y en cada almacén destino
            // (incluye escalas intermedias y destino final)
            for (PlanVuelo vuelo : ruta.getVuelos()) {
                String claveVuelo = vuelo.getOrigen() + "-" +
                                    vuelo.getDestino() + "-" +
                                    vuelo.getHoraSalida();
                maletasPorVuelo.merge(claveVuelo, envio.getCantidadMaletas(), Integer::sum);
                maletasPorAlmacen.merge(vuelo.getDestino(), envio.getCantidadMaletas(), Integer::sum);
            }
 
            // Verificar SLA
            Aeropuerto aeropOrigen  = mapaAeropuertos.get(envio.getAeropuertoOrigen());
            Aeropuerto aeropDestino = mapaAeropuertos.get(envio.getAeropuertoDestino());
 
            long plazoMaximoMinutos = 48 * 60;
            if (aeropOrigen != null && aeropDestino != null &&
                aeropOrigen.getContinente().equals(aeropDestino.getContinente())) {
                plazoMaximoMinutos = 24 * 60;
            }
 
            if (ruta.getTiempoTotalMinutos() > plazoMaximoMinutos) {
                // Llegó tarde: penalización dura
                costoTotal += PENALIZACION_DURA;
            } else {
                // A tiempo: costo = minutos de viaje (menor es mejor)
                costoTotal += ruta.getTiempoTotalMinutos();
            }
        }
 
        // ── PARTE 2: Verificar violaciones agregadas ──────────────────────────
 
        // Verificar capacidad de vuelos
        // capVuelos tiene la capacidad dinámica actual (ya descontada por rutas
        // confirmadas anteriores). Si el acumulado del individuo la supera → penalizar.
        for (Map.Entry<String, Integer> entry : maletasPorVuelo.entrySet()) {
            int capacidadDisponible = capVuelos.getOrDefault(entry.getKey(), Integer.MAX_VALUE);
            if (entry.getValue() > capacidadDisponible) {
                costoTotal += PENALIZACION_DURA;
            }
        }
 
        // Verificar capacidad de almacenes
        // Usamos la capacidad original del aeropuerto (mapaAeropuertos) porque
        // no tenemos la capacidad dinámica de almacenes en el fitness.
        // Esto detecta casos donde el acumulado de maletas supera la capacidad
        // total del almacén, independientemente de lo que ya haya confirmado.
        for (Map.Entry<String, Integer> entry : maletasPorAlmacen.entrySet()) {
            Aeropuerto aeropuerto = mapaAeropuertos.get(entry.getKey());
            if (aeropuerto != null && entry.getValue() > aeropuerto.getCapacidad()) {
                costoTotal += PENALIZACION_DURA;
            }
        }
 
        individuo.setFitness(1.0 / (1.0 + costoTotal));
    }
}
 