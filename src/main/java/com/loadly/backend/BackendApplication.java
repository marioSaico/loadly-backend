package com.loadly.backend;

import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.model.EstadoRuta;
import com.loadly.backend.model.PlanVuelo;
import com.loadly.backend.model.Ruta;
import com.loadly.backend.model.Envio;
import com.loadly.backend.planificador.Planificador;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.util.HashMap;
import java.util.Map;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(
            BackendApplication.class, args
        );

        Planificador planificador = context.getBean(Planificador.class);

        // 🛠️ Simular primera ejecución del planificador
        System.out.println("\n--- INICIO DE SIMULACIÓN (DÍA A DÍA / COLAPSO) ---");
        Individuo resultado = planificador.planificar("20260102-01-10");

        if (resultado != null) {
            System.out.println("\n=== RESULTADO FINAL DE LA ASIGNACIÓN DETALLADO ===");
            
            // 💡 1. Mapear las capacidades originales de los vuelos usados para descontarlas en vivo
            Map<String, Integer> capacidadRestanteVuelos = new HashMap<>();
            for (Ruta ruta : resultado.getRutas()) {
                if (ruta.getVuelos() != null) {
                    for (PlanVuelo vuelo : ruta.getVuelos()) {
                        String claveV = vuelo.getOrigen() + "-" + vuelo.getDestino() + "-" + vuelo.getHoraSalida();
                        // Guardamos la capacidad total la primera vez que vemos el vuelo
                        capacidadRestanteVuelos.putIfAbsent(claveV, vuelo.getCapacidad());
                    }
                }
            }

            // 💡 2. Imprimir cada ruta de forma elegante y detallada
            for (Ruta ruta : resultado.getRutas()) {
                Envio envio = ruta.getEnvio();
                
                System.out.printf("Envío: %s | Origen: %s -> Destino: %s | Estado: %s | Tiempo Total: %d min\n",
                        envio.getIdEnvio(), 
                        envio.getAeropuertoOrigen(), 
                        envio.getAeropuertoDestino(),
                        ruta.getEstado(), 
                        ruta.getTiempoTotalMinutos());

                if (ruta.getEstado() == EstadoRuta.PLANIFICADA && ruta.getVuelos() != null) {
                    System.out.println("   Vuelos asignados:");
                    for (PlanVuelo vuelo : ruta.getVuelos()) {
                        String claveV = vuelo.getOrigen() + "-" + vuelo.getDestino() + "-" + vuelo.getHoraSalida();
                        
                        // Descontar la capacidad (Simulación en vivo)
                        int capacidadActual = capacidadRestanteVuelos.get(claveV);
                        int nuevaCapacidad = capacidadActual - envio.getCantidadMaletas();
                        capacidadRestanteVuelos.put(claveV, nuevaCapacidad);
                        
                        System.out.printf("     -> [Vuelo %s a %s] | Salida: %s | Llegada: %s | Maletas: %d | Capacidad Restante: %d\n",
                                vuelo.getOrigen(),
                                vuelo.getDestino(),
                                vuelo.getHoraSalida(),
                                vuelo.getHoraLlegada(),
                                envio.getCantidadMaletas(),
                                nuevaCapacidad);
                    }
                }
                System.out.println("--------------------------------------------------------------------------------");
            }
            
            // 🛠️ Validación crucial para saber si el código perdió maletas:
            System.out.println("\n-> Total de maletas (rutas) planificadas al final: " + resultado.getRutas().size());
        }
    }
}