package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;

@Component
public class Fitness {

    // Pesos de penalización
    private static final double ALPHA = 10000.0; // Peso envío sin ruta o inalcanzable
    private static final double BETA  = 10.0;    // Peso por CADA MINUTO de retraso
    private static final double GAMMA = 50.0;    // Peso por CADA MALETA extra en almacén
    private static final double DELTA = 50.0;    // Peso por CADA MALETA extra en vuelo
    private static final double EPSILON = 0.01;  // Presión evolutiva para rutas más rápidas

    /**
     * Evalúa a toda la población.
     * OPTIMIZACIÓN: Precalculamos las capacidades originales aquí una sola vez por generación.
     */
    public void evaluarPoblacion(Poblacion poblacion, Map<String, Aeropuerto> mapaAeropuertos, Map<String, Integer> capVuelos) {                                     
        // 💡 Ya no construimos el mapa desde cero, usamos capVuelos directamente
        for (Individuo individuo : poblacion.getIndividuos()) {
            evaluar(individuo, mapaAeropuertos, capVuelos); // Pasamos capVuelos
        }
    }

    /**
     * Evalúa el fitness de un individuo completo.
     */
    public void evaluar(Individuo individuo,
                        Map<String, Aeropuerto> mapaAeropuertos,
                        Map<String, Integer> capacidadesOriginalesVuelos) { 

        int enviosSinRuta = 0;
        long minutosTotalesRetraso = 0;
        long totalMaletasExcesoAlmacen = 0;
        long totalMaletasExcesoVuelos = 0;
        long sumatoriaTiempoTodasLasRutas = 0;

        Map<String, Integer> maletasPorAeropuerto = new HashMap<>();
        Map<String, Integer> maletasPorVuelo = new HashMap<>();

        for (Ruta ruta : individuo.getRutas()) {
            Envio envio = ruta.getEnvio();
            String codigoOrigen = envio.getAeropuertoOrigen();

            // Sumamos las maletas al almacén de origen ANTES de evaluar si tiene ruta.
            // Si el envío se queda varado, igual está ocupando espacio en el mostrador.
            maletasPorAeropuerto.put(
                codigoOrigen,
                maletasPorAeropuerto.getOrDefault(codigoOrigen, 0) + envio.getCantidadMaletas()
            );

            // 💡 CAMBIO AQUÍ: Añadimos la validación para INALCANZABLE
            // Penalización 1: Envío sin ruta o ruta imposible
            if (ruta.getEstado() == EstadoRuta.SIN_RUTA || 
                ruta.getEstado() == EstadoRuta.INALCANZABLE || 
                ruta.getVuelos() == null || 
                ruta.getVuelos().isEmpty()) {
                enviosSinRuta++;
                continue; 
            }
            
            sumatoriaTiempoTodasLasRutas += ruta.getTiempoTotalMinutos();

            // Penalización 2: Envío que excede el plazo
            Aeropuerto aeropOrigen = mapaAeropuertos.get(codigoOrigen);
            Aeropuerto aeropDestino = mapaAeropuertos.get(envio.getAeropuertoDestino());

            long plazoMaximoMinutos = 48 * 60; 
            if (aeropOrigen != null && aeropDestino != null) {
                if (aeropOrigen.getContinente().equals(aeropDestino.getContinente())) {
                    plazoMaximoMinutos = 24 * 60; 
                }
            }

            if (ruta.getTiempoTotalMinutos() > plazoMaximoMinutos) {
                minutosTotalesRetraso += (ruta.getTiempoTotalMinutos() - plazoMaximoMinutos);
            }

            // Acumular maletas por aeropuerto (escalas/destino) y por vuelo
            for (PlanVuelo vuelo : ruta.getVuelos()) {
                String codigoAeropuertoDestino = vuelo.getDestino();
                maletasPorAeropuerto.put(
                    codigoAeropuertoDestino,
                    maletasPorAeropuerto.getOrDefault(codigoAeropuertoDestino, 0) + envio.getCantidadMaletas()
                );

                String claveV = claveVuelo(vuelo);
                maletasPorVuelo.put(
                    claveV,
                    maletasPorVuelo.getOrDefault(claveV, 0) + envio.getCantidadMaletas()
                );
            }
        }

        // Penalización 3: Almacenes saturados
        for (Map.Entry<String, Integer> entry : maletasPorAeropuerto.entrySet()) {
            String codigoAeropuerto = entry.getKey();
            int maletasEnAeropuerto = entry.getValue();

            Aeropuerto aeropuerto = mapaAeropuertos.get(codigoAeropuerto);
            if (aeropuerto != null && maletasEnAeropuerto > aeropuerto.getCapacidad()) {
                totalMaletasExcesoAlmacen += (maletasEnAeropuerto - aeropuerto.getCapacidad());
            }
        }

        // Penalización 4: Vuelos saturados
        for (Map.Entry<String, Integer> entry : maletasPorVuelo.entrySet()) {
            String claveV = entry.getKey();
            int maletasEnVuelo = entry.getValue();
            int capacidadOriginal = capacidadesOriginalesVuelos.getOrDefault(claveV, Integer.MAX_VALUE);

            if (maletasEnVuelo > capacidadOriginal) {
                totalMaletasExcesoVuelos += (maletasEnVuelo - capacidadOriginal);
            }
        }

        // Calcular fitness final
        double penalizaciones = (ALPHA * enviosSinRuta)
                              + (BETA  * minutosTotalesRetraso)
                              + (GAMMA * totalMaletasExcesoAlmacen)
                              + (DELTA * totalMaletasExcesoVuelos)
                              + (EPSILON * sumatoriaTiempoTodasLasRutas);

        double fitness = 1.0 / (1.0 + penalizaciones);
        individuo.setFitness(fitness);
    }

    private String claveVuelo(PlanVuelo vuelo) {
        return vuelo.getOrigen() + "-" + vuelo.getDestino() + "-" + vuelo.getHoraSalida();
    }
}