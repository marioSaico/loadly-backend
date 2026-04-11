package com.loadly.backend;

import com.loadly.backend.model.Aeropuerto;
import com.loadly.backend.model.PlanVuelo;
import com.loadly.backend.model.Envio;
import com.loadly.backend.service.DataService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.util.List;

@SpringBootApplication
public class BackendApplication {

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(
            BackendApplication.class, args
        );

        DataService dataService = context.getBean(DataService.class);

        // Verificar aeropuertos
        List<Aeropuerto> aeropuertos = dataService.getAeropuertos();
        System.out.println("=== AEROPUERTOS: " + aeropuertos.size() + " ===");
        aeropuertos.forEach(a -> 
            System.out.println(a.getCodigo() + " - " + a.getCiudad() 
                + " | GMT: " + a.getGmt() 
                + " | Cap: " + a.getCapacidad() + " | LATITUD: " + a.getLatitud() + " | LONGITUD: " + a.getLongitud() + " | CONTINENTE: " + a.getContinente())
        );

        // Verificar vuelos
        List<PlanVuelo> vuelos = dataService.getVuelos();
        System.out.println("\n=== VUELOS: " + vuelos.size() + " ===");
        vuelos.stream().limit(5).forEach(v -> 
            System.out.println(v.getOrigen() + " -> " + v.getDestino() 
                + " | Sale: " + v.getHoraSalida() 
                + " | Llega: " + v.getHoraLlegada()
                + " | Cap: " + v.getCapacidad())
        );

        // Verificar envíos pendientes con Sc de 70 minutos
        // Fecha inicio: 20260102-00-00, más 70 min = 20260102-01-10
        List<Envio> envios = dataService.obtenerEnviosPendientes("20260102-01-10");
        System.out.println("\n=== ENVIOS PENDIENTES HASTA 01:10: " 
            + envios.size() + " ===");
        envios.forEach(e -> 
            System.out.println(e.getIdEnvio() 
                + " | " + e.getAeropuertoOrigen() 
                + " -> " + e.getAeropuertoDestino()
                + " | " + e.getCantidadMaletas() + " maletas"
                + " | " + e.getFechaRegistro() 
                + " " + e.getHoraRegistro() 
                + ":" + e.getMinutoRegistro())
        );
    }
}