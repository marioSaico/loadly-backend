package com.loadly.backend.algoritmo.aco;
 
import com.loadly.backend.model.*;
import lombok.Data;
 
import java.util.*;
 
/**
 * ============================================================
 * FeromenaGrafo — Grafo de feromonas compartido por la colonia
 * ============================================================
 *
 * Modela el "rastro de feromonas" que las hormigas dejan en los tramos de vuelo.
 * Cada tramo de vuelo se identifica con la clave: "ORIG-DEST-HoraSalida".
 *
 * Ciclo de vida de las feromonas en cada iteración del ACO:
 *   1. EVAPORACIÓN:  todas las feromonas se reducen un % (RHO).
 *   2. DEPÓSITO:     las mejores hormigas refuerzan los vuelos que usaron.
 *
 * Inicialmente todos los tramos tienen la misma feromona base (FEROMONA_INICIAL),
 * para que en la primera iteración la selección sea principalmente heurística.
 */
@Data
public class FeromenaGrafo {
 
    // ---- Almacén de feromonas: clave "ORIG-DEST-HoraSalida" → valor feromona ----
    private Map<String, Double> feromona;
 
    // ---- Parámetros ACO ----
 
    /** Nivel inicial de feromona en todos los tramos al arrancar. */
    private static final double FEROMONA_INICIAL = 1.0;
 
    /** Tasa de evaporación por iteración (10%). */
    private static final double RHO = 0.1;
 
    /**
     * Factor de escala para el depósito de feromona.
     * El fitness está entre 0 y 1; multiplicarlo por este factor lo lleva a un rango
     * comparable con FEROMONA_INICIAL (1.0) para que el depósito tenga impacto real.
     * Ejemplo: fitness=0.9 → deposita 9.0 unidades. fitness=0.1 → deposita 1.0 unidades.
     */
    private static final double FACTOR_DEPOSITO = 10.0;
 
    /**
     * Feromona mínima permitida (piso).
     * Evita que algún tramo quede en 0 y sea ignorado completamente por las hormigas.
     */
    private static final double FEROMONA_MIN = 0.01;
 
    // =========================================================================
    //  CONSTRUCTOR
    // =========================================================================
 
    /**
     * Inicializa el grafo con feromona uniforme en todos los vuelos del plan de vuelo.
     * Al ser iguales al inicio, la selección en la primera iteración depende
     * principalmente de la heurística (distancia geográfica al destino).
     */
    public FeromenaGrafo(Map<String, Aeropuerto> mapaAeropuertos,
                         Map<String, List<PlanVuelo>> mapaVuelosPorOrigen) {
        this.feromona = new HashMap<>();
 
        if (mapaVuelosPorOrigen != null) {
            for (List<PlanVuelo> lista : mapaVuelosPorOrigen.values()) {
                for (PlanVuelo vuelo : lista) {
                    feromona.put(claveVuelo(vuelo), FEROMONA_INICIAL);
                }
            }
        }
    }
 
    // =========================================================================
    //  OPERACIONES PRINCIPALES
    // =========================================================================
 
    /**
     * Devuelve la feromona actual de un vuelo.
     * Llamado por BuscadorRutasACO en cada paso de construcción de ruta.
     *
     * @param vuelo El vuelo del que se quiere saber la feromona.
     * @return Valor de feromona actual (>= FEROMONA_MIN).
     */
    public double getFeromona(PlanVuelo vuelo) {
        return feromona.getOrDefault(claveVuelo(vuelo), FEROMONA_INICIAL);
    }
 
    /**
     * Evaporación global: reduce todas las feromonas en RHO%.
     * Simula que el rastro se desvanece con el tiempo si no es reforzado.
     * Las feromonas no bajan de FEROMONA_MIN para que ningún tramo quede inaccesible.
     *
     * Debe llamarse UNA VEZ por iteración, ANTES del depósito.
     */
    public void evaporar() {
        for (Map.Entry<String, Double> entry : feromona.entrySet()) {
            double nueva = entry.getValue() * (1.0 - RHO);
            entry.setValue(Math.max(nueva, FEROMONA_MIN));
        }
    }
 
    /**
     * Depósito de feromona: la hormiga refuerza los vuelos de su solución.
     * Solo deposita en rutas PLANIFICADAS (las que sí encontraron camino válido).
     * La cantidad depositada es proporcional al fitness:
     *   delta = fitness * FACTOR_DEPOSITO
     *
     * Debe llamarse DESPUÉS de la evaporación en cada iteración.
     *
     * @param hormiga La hormiga que deposita (debe tener fitness ya evaluado).
     */
    public void depositarFeromona(Hormiga hormiga) {
        double delta = hormiga.getFitness() * FACTOR_DEPOSITO;
 
        for (Ruta ruta : hormiga.getRutas()) {
            if (ruta.getEstado() == EstadoRuta.PLANIFICADA && ruta.getVuelos() != null) {
                for (PlanVuelo vuelo : ruta.getVuelos()) {
                    String clave  = claveVuelo(vuelo);
                    double actual = feromona.getOrDefault(clave, FEROMONA_INICIAL);
                    feromona.put(clave, actual + delta);
                }
            }
        }
    }
 
    /**
     * Reinicia todas las feromonas al valor inicial.
     * Útil si se quiere reiniciar la exploración desde cero entre escenarios.
     */
    public void reiniciarFeromonas() {
        feromona.replaceAll((k, v) -> FEROMONA_INICIAL);
    }

    /**
     * Incrementa la feromona de una clave de vuelo específica.
     * Usado para siembra inicial basada en rutas A*.
     */
    public void aumentarFeromona(String clave, double delta) {
        double actual = feromona.getOrDefault(clave, FEROMONA_INICIAL);
        feromona.put(clave, Math.max(FEROMONA_MIN, actual + delta));
    }
 
    // =========================================================================
    //  MÉTODOS PRIVADOS DE APOYO
    // =========================================================================
 
    /**
     * Genera una clave única para identificar un tramo de vuelo.
     * Formato: "ORIG-DEST-HH:mm" — igual que el idVuelo usado en capVuelos.
     */
    private String claveVuelo(PlanVuelo vuelo) {
        return vuelo.getOrigen() + "-" + vuelo.getDestino() + "-" + vuelo.getHoraSalida();
    }
}