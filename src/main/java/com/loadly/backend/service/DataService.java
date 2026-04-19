package com.loadly.backend.service;

import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.loader.*;
import com.loadly.backend.model.*;
import lombok.Data;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
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

    // Controladores de Capacidad Dinámica
    private Map<String, Integer> capacidadDinamicaAlmacenes;
    private Map<String, Integer> capacidadDinamicaVuelos;

    // Solo guarda los envíos que AÚN NO TIENEN RUTA (Backlog)
    private List<Envio> enviosEnEspera = new ArrayList<>();
    
    // Guarda el histórico de los que ya se planificaron (para los logs/reportes)
    private List<Ruta> rutasPlanificadasHistorico = new ArrayList<>();

    // La Agenda de Eventos
    private PriorityQueue<EventoLogistico> agendaEventos = new PriorityQueue<>();

    private static final DateTimeFormatter FORMATO_RELOJ = DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");

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
        
        this.mapaAeropuertos = aeropuertos.stream().collect(Collectors.toMap(Aeropuerto::getCodigo, a -> a));
        this.mapaVuelosPorOrigen = vuelos.stream().filter(v -> !v.isCancelado()).collect(Collectors.groupingBy(PlanVuelo::getOrigen));
        
        // Inicializamos las capacidades dinámicas al tope máximo físico
        this.capacidadDinamicaAlmacenes = new HashMap<>();
        for (Aeropuerto a : aeropuertos) {
            capacidadDinamicaAlmacenes.put(a.getCodigo(), a.getCapacidad());
        }

        this.capacidadDinamicaVuelos = new HashMap<>();
        for (PlanVuelo v : vuelos) {
            capacidadDinamicaVuelos.put(claveVuelo(v), v.getCapacidad());
        }

        System.out.println("Aeropuertos cargados e indexados: " + aeropuertos.size());
        System.out.println("Vuelos cargados e indexados: " + vuelos.size());
    }

    // =========================================================================
    // 1. GESTIÓN DE ENVÍOS (Backlog)
    // =========================================================================

    public List<Envio> obtenerEnviosPendientes(String fechaHoraLimite) {
        List<Envio> enviosRecienLlegados = envioLoader.cargarPendientes(
            "src/main/resources/data/envios",
            fechaHoraLimite,
            this.aeropuertos 
        );

        if (!enviosRecienLlegados.isEmpty()) {
            this.enviosEnEspera.addAll(enviosRecienLlegados);
        }

        // Ahora SOLO devuelve los que acaban de llegar + los rezagados
        return new ArrayList<>(this.enviosEnEspera);
    }

    // =========================================================================
    // 2. CONFIRMACIÓN Y RESERVA DE CAPACIDADES
    // =========================================================================

    /**
     * Este método lo llamará el Main cada vez que el GA entregue un buen plan.
     * Reserva los espacios PERMANENTEMENTE y programa las liberaciones.
     */
    public void confirmarPlanYActualizarCapacidades(Individuo mejorPlan, String fechaHoraReloj) {
        LocalDateTime relojActual = LocalDateTime.parse(fechaHoraReloj, FORMATO_RELOJ);
        List<Envio> enviosPlanificadosEnEstaRonda = new ArrayList<>();

        for (Ruta ruta : mejorPlan.getRutas()) {
            if (ruta.getEstado() == EstadoRuta.PLANIFICADA) {
                Envio envio = ruta.getEnvio();
                enviosPlanificadosEnEstaRonda.add(envio);
                rutasPlanificadasHistorico.add(ruta);

                // 1. Restar espacio en Vuelos
                for (PlanVuelo vuelo : ruta.getVuelos()) {
                    String claveV = claveVuelo(vuelo);
                    int capActualVuelo = capacidadDinamicaVuelos.getOrDefault(claveV, vuelo.getCapacidad());
                    capacidadDinamicaVuelos.put(claveV, capActualVuelo - envio.getCantidadMaletas());
                }

                // 2. Agendar Liberación en el Almacén de Origen
                // Calculamos a qué hora exacta despega el primer vuelo en absoluto
                PlanVuelo primerVuelo = ruta.getVuelos().get(0);
                long minutosHastaDespegue = calcularMinutosHastaVuelo(envio.getHoraRegistro(), envio.getMinutoRegistro(), primerVuelo.getHoraSalida());
                
                LocalDateTime horaDespegueAbsoluta = relojActual.plusMinutes(minutosHastaDespegue);

                // Creamos el evento futuro
                agendaEventos.add(new EventoLogistico(
                    horaDespegueAbsoluta,
                    "LIBERA_ORIGEN",
                    envio.getAeropuertoOrigen(),
                    envio.getCantidadMaletas()
                ));
            }
        }

        // 3. Eliminar del backlog los envíos que ya consiguieron ruta
        this.enviosEnEspera.removeIf(e -> enviosPlanificadosEnEstaRonda.contains(e));
    }


    // =========================================================================
    // 3. LA AGENDA DE EVENTOS (Control del Tiempo)
    // =========================================================================

    /**
     * El Main llamará esto en cada iteración del bucle temporal.
     * Avanza en el tiempo y libera espacios si algún vuelo ya despegó.
     */
    public void procesarEventosDelReloj(String fechaHoraRelojActual) {
        LocalDateTime relojActual = LocalDateTime.parse(fechaHoraRelojActual, FORMATO_RELOJ);

        // Mientras haya eventos en el pasado o en este mismo minuto...
        while (!agendaEventos.isEmpty() && !agendaEventos.peek().getHoraEvento().isAfter(relojActual)) {
            EventoLogistico evento = agendaEventos.poll(); // Saca el evento de la agenda

            if ("LIBERA_ORIGEN".equals(evento.getTipo())) {
                int capacidadActual = capacidadDinamicaAlmacenes.getOrDefault(evento.getCodigoAeropuerto(), 0);
                int capacidadMax = mapaAeropuertos.get(evento.getCodigoAeropuerto()).getCapacidad();
                
                // Devuelve el espacio al almacén (sin pasarse del límite físico)
                int nuevaCapacidad = Math.min(capacidadActual + evento.getCantidadMaletas(), capacidadMax);
                capacidadDinamicaAlmacenes.put(evento.getCodigoAeropuerto(), nuevaCapacidad);
                
                // System.out.println("[EVENTO] -> Se liberaron " + evento.getCantidadMaletas() + " espacios en " + evento.getCodigoAeropuerto() + " por despegue.");
            }
        }
    }

    // =========================================================================
    // 4. MÉTODOS DE APOYO Y GETTERS
    // =========================================================================

    // Clase interna para manejar la Agenda
    @Data
    private static class EventoLogistico implements Comparable<EventoLogistico> {
        private LocalDateTime horaEvento;
        private String tipo;
        private String codigoAeropuerto;
        private int cantidadMaletas;

        public EventoLogistico(LocalDateTime horaEvento, String tipo, String codigoAeropuerto, int cantidadMaletas) {
            this.horaEvento = horaEvento;
            this.tipo = tipo;
            this.codigoAeropuerto = codigoAeropuerto;
            this.cantidadMaletas = cantidadMaletas;
        }

        @Override
        public int compareTo(EventoLogistico o) {
            return this.horaEvento.compareTo(o.horaEvento);
        }
    }

    private long calcularMinutosHastaVuelo(int horaReg, int minReg, String horaSalidaVuelo) {
        int minRegistroAbs = (horaReg * 60) + minReg;
        String[] partes = horaSalidaVuelo.split(":");
        int minSalidaAbs = Integer.parseInt(partes[0]) * 60 + Integer.parseInt(partes[1]);
        if (minSalidaAbs < minRegistroAbs) minSalidaAbs += 24 * 60;
        return minSalidaAbs - minRegistroAbs;
    }

    private String claveVuelo(PlanVuelo vuelo) {
        return vuelo.getOrigen() + "-" + vuelo.getDestino() + "-" + vuelo.getHoraSalida();
    }

    // Getters
    public List<Aeropuerto> getAeropuertos() { return aeropuertos; }
    public List<PlanVuelo> getVuelos() { return vuelos; }
    public Map<String, Aeropuerto> getMapaAeropuertos() { return mapaAeropuertos; }
    public Map<String, List<PlanVuelo>> getMapaVuelosPorOrigen() { return mapaVuelosPorOrigen; }
    public Map<String, Integer> getCapacidadDinamicaAlmacenes() { return capacidadDinamicaAlmacenes; }
    public Map<String, Integer> getCapacidadDinamicaVuelos() { return capacidadDinamicaVuelos; }
    public List<Envio> getEnviosEnEspera() { return enviosEnEspera; }
    public List<Ruta> getRutasPlanificadasHistorico() { return rutasPlanificadasHistorico; }
}