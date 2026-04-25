package com.loadly.backend.experimento;

import com.loadly.backend.BackendApplication;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;

/**
 * Main separado exclusivo para el diseño de experimentos.
 * NO reemplaza a BackendApplication — ambos coexisten.
 *
 * Para ejecutar el experimento:
 *   1. Compilar el proyecto normalmente (mvn compile)
 *   2. Ejecutar esta clase (no BackendApplication)
 *      → En IntelliJ: click derecho → Run 'ExperimentoMain'
 *      → En Maven: mvn exec:java -Dexec.mainClass="com.loadly.backend.experimento.ExperimentoMain"
 *
 * Al terminar genera:
 *   - resultados_experimento.csv  → datos para el análisis estadístico en Python
 *   - Resumen impreso en consola
 */
public class ExperimentoMain {

    public static void main(String[] args) {
        System.out.println("Iniciando contexto Spring para el experimento...");

        // Reutiliza el mismo contexto de Spring (carga aeropuertos, vuelos, etc.)
        // Solo cambia el punto de entrada — los beans son los mismos
        ApplicationContext ctx = SpringApplication.run(BackendApplication.class, args);

        // Obtener el runner del contexto y ejecutar
        ExperimentoRunner runner = ctx.getBean(ExperimentoRunner.class);
        runner.ejecutar();

        System.out.println("\nExperimento finalizado. Revisa resultados_experimento.csv");
        System.exit(0);
    }
}
