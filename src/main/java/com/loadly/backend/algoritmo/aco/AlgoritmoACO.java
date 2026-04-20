package com.loadly.backend.algoritmo.aco;

import com.loadly.backend.algoritmo.genetico.Fitness;
import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;

import java.util.*;

@Component
public class AlgoritmoACO {
    
    // Parámetros ACO
    private static final int ITERACIONES_SIN_MEJORA_MAX = 10;
    
    private final Fitness evaluadorFitness;
    
    public AlgoritmoACO(Fitness evaluadorFitness) {
        this.evaluadorFitness = evaluadorFitness;
    }
    
    /**
     * Ejecuta ACO para optimizar rutas de envíos.
     * 
     * @param envios Lista de envíos a planificar
     * @param vuelos Lista completa de vuelos disponibles
     * @param mapaAeropuertos Mapa de aeropuertos
     * @param mapaVuelosPorOrigen Índice de vuelos por origen
     * @param capVuelos Capacidades dinámicas de vuelos
     * @param capAlmacenes Capacidades dinámicas de almacenes
     * @param numHormigas Cantidad de hormigas (población)
     * @param tiempoLimiteMs Tiempo máximo de ejecución
     * @return Mejor solución encontrada
     */
    public Individuo ejecutar(
            List<Envio> envios,
            List<PlanVuelo> vuelos,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, List<PlanVuelo>> mapaVuelosPorOrigen,
            Map<String, Integer> capVuelos,
            Map<String, Integer> capAlmacenes,
            int numHormigas,
            long tiempoLimiteMs) {
        
        System.out.println("=== ACO - Iniciando Optimización ===");
        System.out.println("    Envíos a procesar: " + envios.size());
        System.out.println("    Hormigas (población): " + numHormigas);
        
        // 1. Crear colonia
        Colonia colonia = new Colonia(numHormigas, mapaAeropuertos, mapaVuelosPorOrigen);
        
        // 2. Construir soluciones iniciales
        colonia.construirSoluciones(envios, vuelos, mapaAeropuertos, capVuelos, capAlmacenes);
        evaluarColonia(colonia, mapaAeropuertos, capVuelos);
        
        Hormiga mejorHormiga = colonia.getMejorHormiga();
        Hormiga mejorGlobal = copiarHormiga(mejorHormiga);
        
        long tiempoInicio = System.currentTimeMillis();
        int iteracionesSinMejora = 0;
        int iteracionesContadas = 0;
        
        System.out.println("    Fitness inicial: " + mejorGlobal.getFitness());
        
        // 3. Bucle evolutivo ACO
        while ((System.currentTimeMillis() - tiempoInicio) < tiempoLimiteMs 
               && iteracionesSinMejora < ITERACIONES_SIN_MEJORA_MAX) {
            
            // 3.1 Evaporación de feromonas
            colonia.getFeromenaGrafo().evaporar();
            
            // 3.2 Depósito de feromonas (las mejores hormigas depositan más)
            List<Hormiga> hormigazOrdenadas = new ArrayList<>(colonia.getHormigas());
            hormigazOrdenadas.sort((h1, h2) -> Double.compare(h2.getFitness(), h1.getFitness()));
            
            // Solo las top 20% depositan feromona
            int topHormigas = Math.max(1, numHormigas / 5);
            for (int i = 0; i < topHormigas; i++) {
                colonia.getFeromenaGrafo().depositarFeromona(hormigazOrdenadas.get(i));
            }
            
            // 3.3 Construir nuevas soluciones
            colonia.construirSoluciones(envios, vuelos, mapaAeropuertos, capVuelos, capAlmacenes);
            evaluarColonia(colonia, mapaAeropuertos, capVuelos);
            
            // 3.4 Actualizar mejor global
            Hormiga mejorActual = colonia.getMejorHormiga();
            if (mejorActual.getFitness() > mejorGlobal.getFitness()) {
                mejorGlobal = copiarHormiga(mejorActual);
                iteracionesSinMejora = 0;
                System.out.println("    [ACO] Nueva mejor solución encontrada - Fitness: " + mejorGlobal.getFitness());
            } else {
                iteracionesSinMejora++;
            }
            
            iteracionesContadas++;
        }
        
        System.out.println("=== ACO - Finalizado ===");
        System.out.println("    Iteraciones procesadas: " + iteracionesContadas);
        System.out.println("    Mejor Fitness encontrado: " + mejorGlobal.getFitness());
        imprimirResumen(mejorGlobal);
        
        // Convertir Hormiga a Individuo para mantener compatibilidad
        return new Individuo(mejorGlobal.getRutas());
    }
    
    /**
     * Evalúa a todas las hormigas de la colonia.
     */
    private void evaluarColonia(Colonia colonia, Map<String, Aeropuerto> mapaAeropuertos,
                                 Map<String, Integer> capVuelos) {
        for (Hormiga hormiga : colonia.getHormigas()) {
            Individuo indv = new Individuo(hormiga.getRutas());
            evaluadorFitness.evaluar(indv, mapaAeropuertos, capVuelos);
            hormiga.setFitness(indv.getFitness());
            hormiga.setFeromonaDepositada(indv.getFitness());
        }
    }
    
    /**
     * Copia una hormiga (clon profundo de rutas).
     */
    private Hormiga copiarHormiga(Hormiga hormiga) {
        Hormiga copia = new Hormiga(new ArrayList<>(hormiga.getRutas()));
        copia.setFitness(hormiga.getFitness());
        copia.setFeromonaDepositada(hormiga.getFeromonaDepositada());
        return copia;
    }
    
    /**
     * Imprime un resumen de la mejor solución.
     */
    private void imprimirResumen(Hormiga mejorHormiga) {
        int rutasPlanificadas = 0;
        long tiempoTotal = 0;
        
        for (Ruta ruta : mejorHormiga.getRutas()) {
            if (ruta.getEstado() == EstadoRuta.PLANIFICADA) {
                rutasPlanificadas++;
                tiempoTotal += ruta.getTiempoTotalMinutos();
            }
        }
        
        System.out.println("    Rutas planificadas: " + rutasPlanificadas);
        System.out.println("    Tiempo total acumulado: " + tiempoTotal + " minutos");
        System.out.println("    Fitness: " + mejorHormiga.getFitness());
    }
}
