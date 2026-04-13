package com.loadly.backend.service;

import com.loadly.backend.loader.*;
import com.loadly.backend.model.*;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.util.List;

@Service
public class DataService {

    private final AeropuertoLoader aeropuertoLoader;
    private final PlanVueloLoader planVueloLoader;
    private final EnvioLoader envioLoader;

    private List<Aeropuerto> aeropuertos;
    private List<PlanVuelo> vuelos;

    public DataService(AeropuertoLoader aeropuertoLoader,
                       PlanVueloLoader planVueloLoader,
                       EnvioLoader envioLoader) {
        this.aeropuertoLoader = aeropuertoLoader;
        this.planVueloLoader = planVueloLoader;
        this.envioLoader = envioLoader;
    }

    @PostConstruct
    public void inicializar() {
        // Solo aeropuertos y vuelos se cargan una sola vez
        this.aeropuertos = aeropuertoLoader.cargar(
            "src/main/resources/data/aeropuertos.txt"
        );
        this.vuelos = planVueloLoader.cargar(
            "src/main/resources/data/planes_vuelo.txt"
        );
        System.out.println("Aeropuertos cargados: " + aeropuertos.size());
        System.out.println("Vuelos cargados: " + vuelos.size());
    }

    // Este método lo llamará el planificador cada Sa minutos
    // pasándole el instante simulado hasta donde debe leer
    public List<Envio> obtenerEnviosPendientes(String fechaHoraLimite) {
        return envioLoader.cargarPendientes(
            "src/main/resources/data/envios",
            fechaHoraLimite,
            this.aeropuertos // 💡 NUEVO: Le mandamos los aeropuertos para que sepa los GMT
        );
    }

    public List<Aeropuerto> getAeropuertos() {
        return aeropuertos;
    }

    public List<PlanVuelo> getVuelos() {
        return vuelos;
    }
}