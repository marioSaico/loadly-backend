package com.loadly.backend;

import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.model.EstadoRuta;
import com.loadly.backend.model.Ruta;
import com.loadly.backend.planificador.Planificador;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(
            BackendApplication.class, args
        );

        Planificador planificador = context.getBean(Planificador.class);

        // Simular primera ejecución del planificador
        // Fecha inicio + Sc de 70 minutos (K=14, Sa=5)
        Individuo resultado = planificador.planificar("20260102-01-10");

        if (resultado != null) {
            System.out.println("\n=== RESULTADO FINAL ===");
            for (Ruta ruta : resultado.getRutas()) {
                System.out.println(
                    "Envío: " + ruta.getEnvio().getIdEnvio()
                    + " | " + ruta.getEnvio().getAeropuertoOrigen()
                    + " → " + ruta.getEnvio().getAeropuertoDestino()
                    + " | Estado: " + ruta.getEstado()
                    + " | Tiempo: " + ruta.getTiempoTotalMinutos() + " min"
                    + " | Vuelos: " + (ruta.getVuelos() != null
                        ? ruta.getVuelos().size() : 0)
                );
            }
        }
    }
}