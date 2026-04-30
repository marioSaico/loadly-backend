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
 
    // Guarda el histórico de los que ya se planificaron
    private List<Ruta> rutasPlanificadasHistorico = new ArrayList<>();
 
    // La Agenda de Eventos
    private PriorityQueue<EventoLogistico> agendaEventos = new PriorityQueue<>();
 
    private static final DateTimeFormatter FORMATO_RELOJ =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");
 
    public DataService(AeropuertoLoader aeropuertoLoader,
                       PlanVueloLoader planVueloLoader,
                       EnvioLoader envioLoader) {
        this.aeropuertoLoader = aeropuertoLoader;
        this.planVueloLoader  = planVueloLoader;
        this.envioLoader      = envioLoader;
    }
 
    @PostConstruct
    public void inicializar() {
        this.aeropuertos = aeropuertoLoader.cargar("src/main/resources/data/aeropuertos.txt");
        this.vuelos      = planVueloLoader.cargar("src/main/resources/data/planes_vuelo.txt");
 
        this.mapaAeropuertos = aeropuertos.stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, a -> a));
        this.mapaVuelosPorOrigen = vuelos.stream()
                .filter(v -> !v.isCancelado())
                .collect(Collectors.groupingBy(PlanVuelo::getOrigen));
 
        this.capacidadDinamicaAlmacenes = new HashMap<>();
        for (Aeropuerto a : aeropuertos) {
            capacidadDinamicaAlmacenes.put(a.getCodigo(), a.getCapacidad());
        }
 
        this.capacidadDinamicaVuelos = new HashMap<>();
        for (PlanVuelo v : vuelos) {
            capacidadDinamicaVuelos.put(claveVuelo(v), v.getCapacidad());
        }
 
        System.out.println("Aeropuertos cargados e indexados: " + aeropuertos.size());
        System.out.println("Vuelos cargados e indexados: "      + vuelos.size());
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
 
        return new ArrayList<>(this.enviosEnEspera);
    }
 
    // =========================================================================
    // 2. CONFIRMACIÓN Y RESERVA DE CAPACIDADES
    // =========================================================================
 
    /**
     * Confirma el mejor plan del GA y actualiza todas las capacidades dinámicas.
     *
     * Para cada ruta PLANIFICADA:
     *  - Decrementa el almacén ORIGEN inmediatamente (las maletas ya están ahí).
     *  - Agenda LIBERA_ALMACEN en ORIGEN cuando despega el primer vuelo.
     *
     *  Por cada vuelo de la ruta:
     *  - Decrementa la capacidad del vuelo.
     *  - Agenda RESET_VUELO 24h después del despegue (el mismo vuelo al día
     *    siguiente tiene capacidad fresca).
     *  - Decrementa el almacén DESTINO del vuelo (escala o destino final).
     *  - Si es escala: agenda LIBERA_ALMACEN cuando despega el siguiente vuelo.
     *  - Si es destino final: agenda LIBERA_ALMACEN 10 min después de llegar
     *    (tiempo de recojo por el cliente).
     */
    public void confirmarPlanYActualizarCapacidades(Individuo mejorPlan,
                                                     String fechaHoraReloj) {
        LocalDateTime relojActual = LocalDateTime.parse(fechaHoraReloj, FORMATO_RELOJ);
        List<Envio> enviosPlanificadosEnEstaRonda = new ArrayList<>();
 
        for (Ruta ruta : mejorPlan.getRutas()) {
            if (ruta.getEstado() != EstadoRuta.PLANIFICADA) continue;
 
            Envio envio = ruta.getEnvio();
            enviosPlanificadosEnEstaRonda.add(envio);
            rutasPlanificadasHistorico.add(ruta);
 
            List<PlanVuelo> vuelosRuta = ruta.getVuelos();
            int n = vuelosRuta.size();
 
            // ── Calcular datetimes reales de despegue y llegada por vuelo ──
            LocalDateTime[] despegues = new LocalDateTime[n];
            LocalDateTime[] llegadas  = new LocalDateTime[n];
 
            // Primer vuelo: espera desde hora de registro del envío
            long esperaPrimero = calcularMinutosHastaVuelo(
                    envio.getHoraRegistro(), envio.getMinutoRegistro(),
                    vuelosRuta.get(0).getHoraSalida());
            despegues[0] = relojActual.plusMinutes(esperaPrimero);
            llegadas[0]  = despegues[0].plusMinutes(
                    calcularDuracionVueloGMT(vuelosRuta.get(0)));
 
            // Vuelos siguientes: espera desde la llegada del vuelo anterior
            for (int i = 1; i < n; i++) {
                long espera = calcularEsperaEnEscala(
                        llegadas[i - 1], vuelosRuta.get(i).getHoraSalida());
                despegues[i] = llegadas[i - 1].plusMinutes(espera);
                llegadas[i]  = despegues[i].plusMinutes(
                        calcularDuracionVueloGMT(vuelosRuta.get(i)));
            }
 
            // ── 1. Almacén ORIGEN ─────────────────────────────────────────
            // Decrementa ahora (las maletas ocupan espacio hasta que despega)
            decrementarAlmacen(envio.getAeropuertoOrigen(), envio.getCantidadMaletas());
            // Libera cuando despega el primer vuelo
            int capMaxOrigen = capacidadOriginalAlmacen(envio.getAeropuertoOrigen());
            agendaEventos.add(new EventoLogistico(
                    despegues[0], "LIBERA_ALMACEN",
                    envio.getAeropuertoOrigen(),
                    envio.getCantidadMaletas(), capMaxOrigen));
 
            // ── 2. Vuelos y almacenes intermedios / destino ───────────────
            for (int i = 0; i < n; i++) {
                PlanVuelo vuelo  = vuelosRuta.get(i);
                String    claveV = claveVuelo(vuelo);
 
                // Decrementar capacidad del vuelo
                int capActualVuelo = capacidadDinamicaVuelos
                        .getOrDefault(claveV, vuelo.getCapacidad());
                capacidadDinamicaVuelos.put(claveV,
                        capActualVuelo - envio.getCantidadMaletas());
 
                // RESET_VUELO: el mismo vuelo mañana tiene capacidad fresca.
                // Se programa 24h después del despegue de hoy.
                agendaEventos.add(new EventoLogistico(
                        despegues[i].plusHours(24), "RESET_VUELO",
                        claveV,
                        envio.getCantidadMaletas(), vuelo.getCapacidad()));
 
                // Decrementar almacén DESTINO del vuelo
                decrementarAlmacen(vuelo.getDestino(), envio.getCantidadMaletas());
                int capMaxDestino = capacidadOriginalAlmacen(vuelo.getDestino());
 
                if (i < n - 1) {
                    // Almacén INTERMEDIO: libera cuando despega el siguiente vuelo
                    agendaEventos.add(new EventoLogistico(
                            despegues[i + 1], "LIBERA_ALMACEN",
                            vuelo.getDestino(),
                            envio.getCantidadMaletas(), capMaxDestino));
                } else {
                    // Almacén DESTINO FINAL: libera 10 min después de llegar
                    // (tiempo de recojo por el cliente)
                    agendaEventos.add(new EventoLogistico(
                            llegadas[i].plusMinutes(10), "LIBERA_ALMACEN",
                            vuelo.getDestino(),
                            envio.getCantidadMaletas(), capMaxDestino));
                }
            }
        }
 
        // Eliminar del backlog los envíos que consiguieron ruta
        this.enviosEnEspera.removeIf(enviosPlanificadosEnEstaRonda::contains);
    }
 
    // =========================================================================
    // 3. LA AGENDA DE EVENTOS (Control del Tiempo)
    // =========================================================================
 
    /**
     * Procesa todos los eventos vencidos hasta el reloj actual.
     *
     * LIBERA_ALMACEN → devuelve espacio al almacén correspondiente.
     * RESET_VUELO    → restaura la capacidad del vuelo para el día siguiente.
     */
    public void procesarEventosDelReloj(String fechaHoraRelojActual) {
        LocalDateTime relojActual =
                LocalDateTime.parse(fechaHoraRelojActual, FORMATO_RELOJ);
 
        while (!agendaEventos.isEmpty() &&
               !agendaEventos.peek().getHoraEvento().isAfter(relojActual)) {
 
            EventoLogistico evento = agendaEventos.poll();
 
            if ("LIBERA_ALMACEN".equals(evento.getTipo())) {
                int capActual = capacidadDinamicaAlmacenes
                        .getOrDefault(evento.getCodigo(), 0);
                int nueva = Math.min(
                        capActual + evento.getCantidad(),
                        evento.getCapacidadMaxima());
                capacidadDinamicaAlmacenes.put(evento.getCodigo(), nueva);
 
            } else if ("RESET_VUELO".equals(evento.getTipo())) {
                // Restaura la cantidad decrementada sin pasarse de la cap máx
                int capActual = capacidadDinamicaVuelos
                        .getOrDefault(evento.getCodigo(), 0);
                int nueva = Math.min(
                        capActual + evento.getCantidad(),
                        evento.getCapacidadMaxima());
                capacidadDinamicaVuelos.put(evento.getCodigo(), nueva);
            }
        }
    }
 
    // =========================================================================
    // 4. RESET EXPERIMENTO
    // =========================================================================
 
    public void resetEstadoExperimento() {
        this.capacidadDinamicaAlmacenes.clear();
        for (Aeropuerto a : aeropuertos) {
            capacidadDinamicaAlmacenes.put(a.getCodigo(), a.getCapacidad());
        }
        this.capacidadDinamicaVuelos.clear();
        for (PlanVuelo v : vuelos) {
            capacidadDinamicaVuelos.put(claveVuelo(v), v.getCapacidad());
        }
        this.enviosEnEspera.clear();
        this.rutasPlanificadasHistorico.clear();
        this.agendaEventos.clear();
    }
 
    // =========================================================================
    // 5. CLASE INTERNA — EVENTO LOGÍSTICO
    // =========================================================================
 
    @Data
    private static class EventoLogistico implements Comparable<EventoLogistico> {
        private LocalDateTime horaEvento;
        private String tipo;           // LIBERA_ALMACEN | RESET_VUELO
        private String codigo;         // código aeropuerto o clave vuelo
        private int    cantidad;       // maletas a liberar / restaurar
        private int    capacidadMaxima; // cap máx para no pasarse al restaurar
 
        public EventoLogistico(LocalDateTime horaEvento, String tipo,
                               String codigo, int cantidad, int capacidadMaxima) {
            this.horaEvento      = horaEvento;
            this.tipo            = tipo;
            this.codigo          = codigo;
            this.cantidad        = cantidad;
            this.capacidadMaxima = capacidadMaxima;
        }
 
        @Override
        public int compareTo(EventoLogistico o) {
            return this.horaEvento.compareTo(o.horaEvento);
        }
    }
 
    // =========================================================================
    // 6. MÉTODOS AUXILIARES
    // =========================================================================
 
    /** Decrementa la capacidad dinámica de un almacén. */
    private void decrementarAlmacen(String codigoAeropuerto, int maletas) {
        int capActual = capacidadDinamicaAlmacenes.getOrDefault(codigoAeropuerto, 0);
        capacidadDinamicaAlmacenes.put(codigoAeropuerto, capActual - maletas);
    }
 
    /** Devuelve la capacidad física original de un aeropuerto. */
    private int capacidadOriginalAlmacen(String codigoAeropuerto) {
        Aeropuerto a = mapaAeropuertos.get(codigoAeropuerto);
        return (a != null) ? a.getCapacidad() : Integer.MAX_VALUE;
    }
 
    /**
     * Calcula la duración real del vuelo en minutos (en GMT),
     * corrigiendo diferencia de husos horarios entre origen y destino.
     */
    private long calcularDuracionVueloGMT(PlanVuelo vuelo) {
        int minSalidaLoc  = convertirAMinutos(vuelo.getHoraSalida());
        int minLlegadaLoc = convertirAMinutos(vuelo.getHoraLlegada());
        Aeropuerto ao = mapaAeropuertos.get(vuelo.getOrigen());
        Aeropuerto ad = mapaAeropuertos.get(vuelo.getDestino());
        int gmtO = (ao != null) ? ao.getGmt() : 0;
        int gmtD = (ad != null) ? ad.getGmt() : 0;
        int minSalidaGMT  = minSalidaLoc  - (gmtO * 60);
        int minLlegadaGMT = minLlegadaLoc - (gmtD * 60);
        while (minLlegadaGMT < minSalidaGMT) minLlegadaGMT += 1440;
        return minLlegadaGMT - minSalidaGMT;
    }
 
    /**
     * Calcula los minutos de espera en una escala:
     * desde la hora de llegada del vuelo anterior hasta la salida del siguiente.
     */
    private long calcularEsperaEnEscala(LocalDateTime horaLlegada,
                                         String horaSalidaSiguiente) {
        int minLlegada = horaLlegada.getHour() * 60 + horaLlegada.getMinute();
        int minSalida  = convertirAMinutos(horaSalidaSiguiente);
        if (minSalida < minLlegada) minSalida += 1440;
        return minSalida - minLlegada;
    }
 
    /** Calcula minutos desde la hora de registro del envío hasta la salida del vuelo. */
    private long calcularMinutosHastaVuelo(int horaReg, int minReg,
                                            String horaSalidaVuelo) {
        int minRegistroAbs = (horaReg * 60) + minReg;
        int minSalidaAbs   = convertirAMinutos(horaSalidaVuelo);
        if (minSalidaAbs < minRegistroAbs) minSalidaAbs += 1440;
        return minSalidaAbs - minRegistroAbs;
    }
 
    private int convertirAMinutos(String hora) {
        String[] p = hora.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }
 
    private String claveVuelo(PlanVuelo vuelo) {
        return vuelo.getOrigen() + "-" + vuelo.getDestino() + "-" + vuelo.getHoraSalida();
    }
 
    // =========================================================================
    // 7. GETTERS
    // =========================================================================
 
    public List<Aeropuerto>              getAeropuertos()                { return aeropuertos; }
    public List<PlanVuelo>               getVuelos()                     { return vuelos; }
    public Map<String, Aeropuerto>       getMapaAeropuertos()            { return mapaAeropuertos; }
    public Map<String, List<PlanVuelo>>  getMapaVuelosPorOrigen()        { return mapaVuelosPorOrigen; }
    public Map<String, Integer>          getCapacidadDinamicaAlmacenes() { return capacidadDinamicaAlmacenes; }
    public Map<String, Integer>          getCapacidadDinamicaVuelos()    { return capacidadDinamicaVuelos; }
    public List<Envio>                   getEnviosEnEspera()             { return enviosEnEspera; }
    public List<Ruta>                    getRutasPlanificadasHistorico()  { return rutasPlanificadasHistorico; }
}
 