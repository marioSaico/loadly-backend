package com.loadly.backend.service;

import com.loadly.backend.loader.*;
import com.loadly.backend.model.*;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DataService {

    private final AeropuertoLoader aeropuertoLoader;
    private final PlanVueloLoader planVueloLoader;
    private final EnvioLoader envioLoader;

    private List<Aeropuerto> aeropuertos;
    private List<PlanVuelo> vuelos;

    private Map<String, Aeropuerto> mapaAeropuertos;
    private Map<String, List<PlanVuelo>> mapaVuelosPorOrigen;

    private List<Envio> enviosAcumuladosGlobal = new ArrayList<>();

    public DataService(AeropuertoLoader aeropuertoLoader,
                       PlanVueloLoader planVueloLoader,
                       EnvioLoader envioLoader) {
        this.aeropuertoLoader = aeropuertoLoader;
        this.planVueloLoader = planVueloLoader;
        this.envioLoader = envioLoader;
    }

    @PostConstruct
    public void inicializar() {
        this.aeropuertos = aeropuertoLoader.cargar("src/main/resources/data/aeropuertos.txt");
        this.vuelos = planVueloLoader.cargar("src/main/resources/data/planes_vuelo.txt");
        
        // 💡 CREAS LOS MAPAS UNA SOLA VEZ AQUÍ
        this.mapaAeropuertos = aeropuertos.stream().collect(Collectors.toMap(Aeropuerto::getCodigo, a -> a));
        this.mapaVuelosPorOrigen = vuelos.stream().filter(v -> !v.isCancelado()).collect(Collectors.groupingBy(PlanVuelo::getOrigen));
        
        System.out.println("Aeropuertos cargados e indexados: " + aeropuertos.size());
        System.out.println("Vuelos cargados e indexados: " + vuelos.size());
    }

    // Este método lo llamará el planificador cada Sa minutos
    // pasándole el instante simulado hasta donde debe leer
    public List<Envio> obtenerEnviosPendientes(String fechaHoraLimite) {
    
        // El Loader usa el "Cursor" para traer SOLO los envíos nuevos en milisegundos
        List<Envio> enviosRecienLlegados = envioLoader.cargarPendientes(
            "src/main/resources/data/envios",
            fechaHoraLimite,
            this.aeropuertos 
        );

        // Metemos los nuevos a nuestra bolsa acumulada
        if (!enviosRecienLlegados.isEmpty()) {
            this.enviosAcumuladosGlobal.addAll(enviosRecienLlegados);
        }

        // Le entregamos al Planificador la bolsa COMPLETA para que no rompa la Planificación
        return this.enviosAcumuladosGlobal;
    }

    public List<Aeropuerto> getAeropuertos() {
        return aeropuertos;
    }

    public List<PlanVuelo> getVuelos() {
        return vuelos;
    }

    public Map<String, Aeropuerto> getMapaAeropuertos() {
        return mapaAeropuertos;
    }

    public Map<String, List<PlanVuelo>> getMapaVuelosPorOrigen() {
        return mapaVuelosPorOrigen;
    }
}