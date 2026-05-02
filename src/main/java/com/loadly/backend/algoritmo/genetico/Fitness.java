package com.loadly.backend.algoritmo.genetico;
 
import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;
 
import java.util.HashMap;
import java.util.Map;
 
@Component
public class Fitness {
 
    private static final double PENALIZACION_DURA = 999999.0;
    
    // Pesos para que la saturación sume lo suficiente al "costoTotal" 
    // y el algoritmo prefiera vuelos/almacenes vacíos.
    private static final double PESO_SATURACION_VUELO   = 120.0;
    private static final double PESO_SATURACION_ALMACEN = 180.0;
 
    public void evaluarPoblacion(
            Poblacion poblacion,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, Integer> capVuelos,
            Map<String, Integer> capAlmacenes) {
        for (Individuo individuo : poblacion.getIndividuos()) {
            evaluar(individuo, mapaAeropuertos, capVuelos, capAlmacenes);
        }
    }
 
    public void evaluar(
            Individuo individuo,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, Integer> capVuelos,
            Map<String, Integer> capAlmacenes) {
 
        double costoTotal = 0.0;
 
        Map<String, Integer> maletasPorVuelo   = new HashMap<>();
        Map<String, Integer> maletasPorAlmacen = new HashMap<>();
 
        // ── PARTE 1: Evaluar cada ruta individualmente ────────────────────
        for (Ruta ruta : individuo.getRutas()) {
            Envio envio = ruta.getEnvio();
 
            // [CORREGIDO] Validaciones originales tuyas intactas
            // Caso 1: Sin ruta o ruta inválida
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
 
            // Acumular maletas en cada vuelo y almacén destino
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
                costoTotal += PENALIZACION_DURA;
            } else {
                costoTotal += ruta.getTiempoTotalMinutos();
            }
        }
 
        // ── PARTE 2: Verificar violaciones agregadas y Balanceo de Carga ───────
 
        // Verificar capacidad de vuelos con capacidad dinámica actual
        for (Map.Entry<String, Integer> entry : maletasPorVuelo.entrySet()) {
            int capacidadDisponible = capVuelos.getOrDefault(entry.getKey(), Integer.MAX_VALUE);
            int maletasUsadas = entry.getValue();

            if (capacidadDisponible <= 0 || maletasUsadas > capacidadDisponible) {
                costoTotal += PENALIZACION_DURA;
            } else {
                // Agregar penalización por saturación (más lleno = mayor costo)
                double proporcionUsoVuelo = (double) maletasUsadas / capacidadDisponible;
                costoTotal += (proporcionUsoVuelo * PESO_SATURACION_VUELO);
            }
        }
 
        // Verificar capacidad de almacenes con capacidad dinámica actual
        for (Map.Entry<String, Integer> entry : maletasPorAlmacen.entrySet()) {
            Aeropuerto aeropuerto = mapaAeropuertos.get(entry.getKey());
            int capacidadDisponible = capAlmacenes.getOrDefault(
                entry.getKey(),
                aeropuerto != null ? aeropuerto.getCapacidad() : Integer.MAX_VALUE
            );
            int maletasUsadas = entry.getValue();

            if (capacidadDisponible <= 0 || maletasUsadas > capacidadDisponible) {
                costoTotal += PENALIZACION_DURA;
            } else {
                // Agregar penalización por saturación en almacén
                double proporcionUsoAlmacen = (double) maletasUsadas / capacidadDisponible;
                costoTotal += (proporcionUsoAlmacen * PESO_SATURACION_ALMACEN);
            }
        }
 
        // [CORREGIDO] Inversión matemática original para maximizar el fitness (0 a 1)
        individuo.setFitness(1000000.0 / (1.0 + costoTotal));
    }
}