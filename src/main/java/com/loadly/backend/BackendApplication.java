package com.loadly.backend;

import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.model.*;
import com.loadly.backend.planificador.Planificador;
import com.loadly.backend.planificador.PlanificadorACO;
import com.loadly.backend.service.DataService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
public class BackendApplication {

    // =========================================================================
    // FORMATEADORES GLOBALES
    // =========================================================================
    private static final DateTimeFormatter FMT_INPUT    = DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");
    private static final DateTimeFormatter FMT_LOG      = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FMT_DISPLAY  = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_FECHA    = DateTimeFormatter.ofPattern("yyyyMMdd");

    // =========================================================================
    // INTERFAZ FUNCIONAL — abstrae GA y ACO en el bucle principal
    // =========================================================================
    @FunctionalInterface
    interface PlanificadorFunc {
        Individuo planificar(String fechaHoraLimite, int tamano, long tiempoMs);
    }

    // =========================================================================
    // CLASE INTERNA — Evento para la bitácora cronológica
    // =========================================================================
    static class EventoSimulacion {
        LocalDateTime tiempoGMT;
        String        aeropuerto;
        int           variacionCapacidad;
        String        mensaje;

        EventoSimulacion(LocalDateTime tiempo, String aeropuerto,
                         int variacion, String mensaje) {
            this.tiempoGMT          = tiempo;
            this.aeropuerto         = aeropuerto;
            this.variacionCapacidad = variacion;
            this.mensaje            = mensaje;
        }
    }

    // =========================================================================
    // MAIN [CORRECCIÓN: Agregado Menú Dinámico con Scanner]
    // =========================================================================
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(BackendApplication.class, args);
        Planificador    planificador    = context.getBean(Planificador.class);
        PlanificadorACO planificadorACO = context.getBean(PlanificadorACO.class);
        DataService     dataService     = context.getBean(DataService.class);

        System.out.println("=== SISTEMA DE PLANIFICACIÓN LOADLY ===");

        // ---------------------------------------------------------
        // 1. SELECCIÓN DE ALGORITMO (Comenta el que NO vayas a usar)
        // ---------------------------------------------------------
        
        // ---> OPCIÓN A: Algoritmo Genético (GA)
        PlanificadorFunc planFunc = (lim, tam, ms) -> planificador.planificar(lim, tam, ms);
        String nombreAlg = "GA";

        // ---> OPCIÓN B: Algoritmo de Hormigas (ACO)
        // PlanificadorFunc planFunc = (lim, tam, ms) -> planificadorACO.planificar(lim, tam, ms);
        // String nombreAlg = "ACO";


        // ---------------------------------------------------------
        // 2. SELECCIÓN DE ESCENARIO (Descomenta SOLO 1 a la vez)
        // ---------------------------------------------------------
        // Parámetros: nombre, fechaInicio, fechaFin, Ta(seg), Sa(min), K, tamañoPoblacion, alg, func, data

        // ---> ESCENARIO 1: DÍA A DÍA (1 Día, K=1, Ta=60s)
        ejecutarEscenario("DIA A DIA", "20260101-00-00", "20260102-00-00", 60, 1440, 1, 5, nombreAlg, planFunc, dataService);

        // ---> ESCENARIO 2: PERIODO (5 Días, K=5, Ta=25s)
        // ejecutarEscenario("PERIODO", "20260101-00-00", "20260106-00-00", 25, 10, 5, 100, nombreAlg, planFunc, dataService);

        // ---> ESCENARIO 3: COLAPSO MASIVO
        // ejecutarEscenario("COLAPSO", "20260101-00-00", "20260106-00-00", 45, 10, 7, 100, nombreAlg, planFunc, dataService);
        
    }

    // =========================================================================
    // MÉTODO PRINCIPAL DE SIMULACIÓN
    // =========================================================================
    public static void ejecutarEscenario(
            String nombre, String inicioStr, String finStr,
            int taSegundos, int sa, int k, int tamano,
            String tipoAlgoritmo, PlanificadorFunc planFunc,
            DataService dataService) {

        LocalDateTime relojSimulado      = LocalDateTime.parse(inicioStr, FMT_INPUT);
        LocalDateTime finSimulacion      = LocalDateTime.parse(finStr,    FMT_INPUT);
        LocalDateTime limiteLecturaDatos = relojSimulado;

        int  sc             = sa * k;
        long tiempoLimiteMs = taSegundos * 1000L;

        Map<String, Integer> capacidadOriginal = dataService.getAeropuertos().stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getCapacidad));
        Map<String, Integer> mapaGmt = dataService.getAeropuertos().stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getGmt));

        List<EventoSimulacion> bitacoraGlobal = new ArrayList<>();
        boolean colapsoDetectado = false;
        
        // [CORRECCIÓN]: El timeline debe ser global durante toda la ejecución del escenario
        // para recordar los envíos de las iteraciones anteriores.
        Map<String, List<long[]>> timelineAlmacenesGlobal = new HashMap<>();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("   INICIANDO ESCENARIO [" + tipoAlgoritmo + "]: " + nombre);
        System.out.println("   Parámetros -> Ta: " + taSegundos + "s | Sa: " + sa
                + " min | K: " + k + " | Sc: " + sc + " min | Tamaño: " + tamano);
        System.out.println("   Período: " + inicioStr + " → " + finStr
                + " (" + ChronoUnit.DAYS.between(relojSimulado, finSimulacion) + " días)");
        System.out.println("=".repeat(80));

        while ((limiteLecturaDatos.isBefore(finSimulacion)
                || limiteLecturaDatos.isEqual(finSimulacion))
               && !colapsoDetectado) {

            String limiteLecturaStr = limiteLecturaDatos.format(FMT_INPUT);
            String relojActualStr   = relojSimulado.format(FMT_INPUT);

            System.out.println("\n>>> [RELOJ: " + relojSimulado.format(FMT_LOG)
                    + "] Planificando envíos hasta " + limiteLecturaDatos.format(FMT_LOG) + "...");

            dataService.procesarEventosDelReloj(relojActualStr);
            Individuo resultado = planFunc.planificar(limiteLecturaStr, tamano, tiempoLimiteMs);

            if (resultado == null) {
                System.out.println("    [-] No hay envíos nuevos ni rezagados en este intervalo.");
            } else {
                ResultadoColapso colapso = detectarColapso(resultado, dataService, relojSimulado);
                if (colapso.hayColapso()) {
                    imprimirAlertas(colapso);
                    colapsoDetectado = true;
                } else {
                    // Pasamos el timelineGlobal al reporte para que se actualice
                    imprimirReporteIntervalo(resultado, relojSimulado, limiteLecturaDatos,
                            taSegundos, tipoAlgoritmo, dataService, timelineAlmacenesGlobal);
                }
            }

            limiteLecturaDatos = limiteLecturaDatos.plusMinutes(sc);
            relojSimulado      = relojSimulado.plusMinutes(sa);
        }

        List<Ruta> rutasHistorico = dataService.getRutasPlanificadasHistorico();
        for (Ruta ruta : rutasHistorico) {
            procesarRutaEnBitacora(ruta, mapaGmt, bitacoraGlobal);
        }
        imprimirBitacoraFinal(bitacoraGlobal, capacidadOriginal,
                rutasHistorico.size(), colapsoDetectado, relojSimulado, rutasHistorico);
    }

    // =========================================================================
    // DETECCIÓN DE COLAPSO
    // =========================================================================
    static class ResultadoColapso {
        boolean topologico;
        boolean porTiempo;
        boolean porEspacio;
        boolean hayColapso() { return topologico || porTiempo || porEspacio; }
    }

    private static ResultadoColapso detectarColapso(
            Individuo resultado, DataService dataService, LocalDateTime relojSimulado) {

        ResultadoColapso rc = new ResultadoColapso();

        for (Ruta r : resultado.getRutas()) {
            if (r.getEstado() == EstadoRuta.INALCANZABLE) { rc.topologico = true; break; }
        }

        Map<String, Integer> ocupacionBacklog = new HashMap<>();
        for (Envio env : dataService.getEnviosEnEspera()) {
            long maxHoras = 48;
            Aeropuerto o = dataService.getMapaAeropuertos().get(env.getAeropuertoOrigen());
            Aeropuerto d = dataService.getMapaAeropuertos().get(env.getAeropuertoDestino());
            if (o != null && d != null && o.getContinente().equals(d.getContinente())) maxHoras = 24;

            LocalDateTime horaReg = LocalDateTime.of(
                    Integer.parseInt(env.getFechaRegistro().substring(0, 4)),
                    Integer.parseInt(env.getFechaRegistro().substring(4, 6)),
                    Integer.parseInt(env.getFechaRegistro().substring(6, 8)),
                    env.getHoraRegistro(), env.getMinutoRegistro());
            if (ChronoUnit.HOURS.between(horaReg, relojSimulado) > maxHoras) rc.porTiempo = true;
            ocupacionBacklog.merge(env.getAeropuertoOrigen(), env.getCantidadMaletas(), Integer::sum);
        }

        Map<String, Integer> capDinamica = dataService.getCapacidadDinamicaAlmacenes();
        for (Map.Entry<String, Integer> entry : ocupacionBacklog.entrySet()) {
            if (entry.getValue() > capDinamica.getOrDefault(entry.getKey(), 0)) {
                rc.porEspacio = true; break;
            }
        }
        return rc;
    }

    private static void imprimirAlertas(ResultadoColapso rc) {
        System.out.println("    [!] ¡ALERTA DE COLAPSO LOGÍSTICO!");
        if (rc.topologico) System.out.println(
                "        -> [Topológico] No existen vuelos físicos posibles para llegar al destino.");
        if (rc.porTiempo)  System.out.println(
                "        -> [Tiempo] Uno o más envíos excedieron el plazo límite (24h/48h) en tierra.");
        if (rc.porEspacio) System.out.println(
                "        -> [Espacio] El almacén se desbordó por la acumulación de envíos en espera.");
    }

    // =========================================================================
    // IMPRESIÓN DEL REPORTE POR INTERVALO
    // =========================================================================
    private static void imprimirReporteIntervalo(
            Individuo resultado, LocalDateTime relojSimulado,
            LocalDateTime limiteLecturaDatos,
            int taSegundos, String tipoAlgoritmo,
            DataService dataService,
            Map<String, List<long[]>> timelineAlmacenesGlobal) { // [CORRECCIÓN] Recibe el mapa global

        long planificados = resultado.getRutas().stream()
                .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA).count();
        int maletasTotales = resultado.getRutas().stream()
                .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA)
                .mapToInt(r -> r.getEnvio().getCantidadMaletas()).sum();

        System.out.println("\n" + "=".repeat(70));
        System.out.println(" [RELOJ SIMULADO: " + relojSimulado.format(FMT_LOG) + "]");
        System.out.println(" Límite de lectura de datos: " + limiteLecturaDatos.format(FMT_LOG));
        System.out.println("=".repeat(70));
        System.out.println(" -> Envíos planificados en este salto: " + planificados);
        System.out.println(" -> Maletas procesadas en este salto:  " + maletasTotales);
        System.out.printf(" -> %s %ds | Mejor Fitness: %.6f%n",
                tipoAlgoritmo, taSegundos, resultado.getFitness());

        if (planificados == 0) {
            System.out.println("=".repeat(70) + "\n");
            return;
        }

        System.out.println(" >>> REPORTE DE RUTAS ASIGNADAS <<<");
        System.out.println("-".repeat(70));

        List<Ruta> rutasOrdenadas = resultado.getRutas().stream()
                .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA
                        && r.getVuelos() != null && !r.getVuelos().isEmpty())
                .sorted(Comparator.comparingInt(r -> {
                    PlanVuelo pv = r.getVuelos().get(0);
                    Aeropuerto aero = dataService.getMapaAeropuertos().get(pv.getOrigen());
                    int gmt = (aero != null) ? aero.getGmt() : 0;
                    String[] p = pv.getHoraSalida().split(":");
                    return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]) - gmt * 60;
                }))
                .collect(Collectors.toList());

        Map<String, Integer> ocupacionVuelos = new HashMap<>();
        for (Ruta r : rutasOrdenadas) {
            for (PlanVuelo v : r.getVuelos()) {
                String clave = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
                ocupacionVuelos.merge(clave, r.getEnvio().getCantidadMaletas(), Integer::sum);
            }
        }

        // [CORRECCIÓN] Agregamos eventos al timeline GLOBAL, no a uno nuevo local
        for (Ruta r : rutasOrdenadas) {
            Envio envio = r.getEnvio();
            Aeropuerto aeroOrigen = dataService.getMapaAeropuertos().get(envio.getAeropuertoOrigen());
            int gmtOrigen = (aeroOrigen != null) ? aeroOrigen.getGmt() : 0;

            LocalDateTime regGMT = LocalDateTime.of(
                    LocalDate.parse(envio.getFechaRegistro(), FMT_FECHA),
                    LocalTime.of(envio.getHoraRegistro(), envio.getMinutoRegistro())
            ).minusHours(gmtOrigen);

            agregarEventoTimeline(timelineAlmacenesGlobal, envio.getAeropuertoOrigen(),
                    regGMT, +envio.getCantidadMaletas());

            LocalDateTime cursor = regGMT;
            List<PlanVuelo> vuelos = r.getVuelos();
            for (int i = 0; i < vuelos.size(); i++) {
                PlanVuelo v = vuelos.get(i);
                Aeropuerto ao = dataService.getMapaAeropuertos().get(v.getOrigen());
                Aeropuerto ad = dataService.getMapaAeropuertos().get(v.getDestino());
                int gmtO = (ao != null) ? ao.getGmt() : 0;
                int gmtD = (ad != null) ? ad.getGmt() : 0;

                int minSalidaGMT = convertirAMinutos(v.getHoraSalida()) - gmtO * 60;
                LocalTime salidaGMT = minutosALocalTime((minSalidaGMT % 1440 + 1440) % 1440);
                LocalDateTime despegue = cursor.with(salidaGMT);
                if (despegue.isBefore(cursor)) despegue = despegue.plusDays(1);

                int minSGMT = convertirAMinutos(v.getHoraSalida()) - gmtO * 60;
                int minLGMT = convertirAMinutos(v.getHoraLlegada()) - gmtD * 60;
                while (minLGMT < minSGMT) minLGMT += 1440;
                long duracion = minLGMT - minSGMT;
                LocalDateTime llegada = despegue.plusMinutes(duracion);

                agregarEventoTimeline(timelineAlmacenesGlobal, v.getOrigen(),
                        despegue, -envio.getCantidadMaletas());

                agregarEventoTimeline(timelineAlmacenesGlobal, v.getDestino(),
                        llegada, +envio.getCantidadMaletas());

                cursor = llegada;
            }
        }

        for (Ruta r : rutasOrdenadas) {
            imprimirDetalleRuta(r, dataService, ocupacionVuelos, timelineAlmacenesGlobal);
        }

        System.out.println("=".repeat(70) + "\n");
    }

    // =========================================================================
    // HELPERS DE TIMELINE
    // =========================================================================

    private static void agregarEventoTimeline(
            Map<String, List<long[]>> timeline,
            String aeropuerto, LocalDateTime tiempoGMT, int delta) {
        
        // [CORRECCIÓN] Usar Epoch exacto en lugar de hardcodear "2026, 1, 1" 
        // Esto permite que el sistema funcione con cualquier fecha real.
        long minutos = tiempoGMT.toEpochSecond(ZoneOffset.UTC) / 60;
        timeline.computeIfAbsent(aeropuerto, k -> new ArrayList<>())
                .add(new long[]{minutos, delta});
    }

    private static int getOcupacionAlmacen(
            Map<String, List<long[]>> timeline,
            String aeropuerto, LocalDateTime despegueGMT) {

        LocalDateTime fotoHora = despegueGMT.withMinute(0).withSecond(0).withNano(0);
        // [CORRECCIÓN] Coherencia con el Epoch dinámico
        long minutosHasta = fotoHora.toEpochSecond(ZoneOffset.UTC) / 60;

        List<long[]> eventos = timeline.getOrDefault(aeropuerto, new ArrayList<>());
        int ocupacion = 0;
        for (long[] evento : eventos) {
            if (evento[0] <= minutosHasta) {
                ocupacion += (int) evento[1];
            }
        }
        return Math.max(0, ocupacion);
    }

    private static int convertirAMinutos(String hora) {
        String[] p = hora.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    private static LocalTime minutosALocalTime(int minutos) {
        return LocalTime.of((minutos / 60) % 24, minutos % 60);
    }

    // =========================================================================
    // IMPRESIÓN DE DETALLE DE RUTA
    // =========================================================================

    private static void imprimirDetalleRuta(
            Ruta r, DataService dataService,
            Map<String, Integer> ocupacionVuelos,
            Map<String, List<long[]>> timelineAlmacenes) {

        Envio      env     = r.getEnvio();
        Aeropuerto origen  = dataService.getMapaAeropuertos().get(env.getAeropuertoOrigen());
        Aeropuerto destino = dataService.getMapaAeropuertos().get(env.getAeropuertoDestino());

        int gmtOrigen  = (origen  != null) ? origen.getGmt()  : 0;

        long limiteSlaHoras = (origen != null && destino != null
                && origen.getContinente().equals(destino.getContinente())) ? 24 : 48;

        LocalDateTime regLocal = LocalDateTime.of(
                LocalDate.parse(env.getFechaRegistro(), FMT_FECHA),
                LocalTime.of(env.getHoraRegistro(), env.getMinutoRegistro()));
        LocalDateTime regGMT = regLocal.minusHours(gmtOrigen);

        LocalDateTime llegadaGMT   = regGMT.plusMinutes(r.getTiempoTotalMinutos());

        String estado = (r.getTiempoTotalMinutos() <= limiteSlaHoras * 60)
                ? "[OK] A TIEMPO" : "[!] FUERA DE PLAZO";

        System.out.printf("ENVÍO: %s | %s (%s) -> %s (%s) | %d maletas%n",
                env.getIdEnvio(),
                env.getAeropuertoOrigen(),
                (origen  != null ? origen.getContinente()  : "?"),
                env.getAeropuertoDestino(),
                (destino != null ? destino.getContinente() : "?"),
                env.getCantidadMaletas());
        System.out.printf("Registro: %s | SLA: %dh%n",
                regGMT.format(FMT_DISPLAY),
                limiteSlaHoras);
        System.out.printf("ESTADO: %s%n", estado);
        System.out.printf("Tiempo real: %dh %dm | Llegada: %s%n",
                r.getTiempoTotalMinutos() / 60,
                r.getTiempoTotalMinutos() % 60,
                llegadaGMT.format(FMT_DISPLAY));
        System.out.printf("Ruta (%d vuelo/s):%n", r.getVuelos().size());

        LocalDateTime cursor = regGMT;
        int paso = 1;

        for (PlanVuelo v : r.getVuelos()) {
            Aeropuerto aeroOrig = dataService.getMapaAeropuertos().get(v.getOrigen());
            Aeropuerto aeroDest = dataService.getMapaAeropuertos().get(v.getDestino());
            int gmtO = (aeroOrig != null) ? aeroOrig.getGmt() : 0;
            int gmtD = (aeroDest != null) ? aeroDest.getGmt() : 0;

            int minSalidaGMT = convertirAMinutos(v.getHoraSalida()) - gmtO * 60;
            LocalTime salidaGMT  = minutosALocalTime((minSalidaGMT % 1440 + 1440) % 1440);
            LocalDateTime despegueGMT = cursor.with(salidaGMT);
            if (despegueGMT.isBefore(cursor)) despegueGMT = despegueGMT.plusDays(1);

            int minSGMT = convertirAMinutos(v.getHoraSalida()) - gmtO * 60;
            int minLGMT = convertirAMinutos(v.getHoraLlegada()) - gmtD * 60;
            while (minLGMT < minSGMT) minLGMT += 1440;
            LocalDateTime llegadaGMTVuelo = despegueGMT.plusMinutes(minLGMT - minSGMT);

            String claveVuelo = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
            int ocupadoVuelo  = ocupacionVuelos.getOrDefault(claveVuelo, 0);
            int capMaxVuelo   = v.getCapacidad();

            int capMaxAlmacen  = (aeroOrig != null) ? aeroOrig.getCapacidad() : 0;
            int ocupadoAlmacen = getOcupacionAlmacen(timelineAlmacenes, v.getOrigen(), despegueGMT);

            System.out.printf("  (%d) %s -> %s | Salida: %s | Llegada: %s"
                    + " | Vuelo: %d/%d | Almacén %s: %d/%d%n",
                    paso++,
                    v.getOrigen(), v.getDestino(),
                    salidaGMT, llegadaGMTVuelo.toLocalTime(),
                    ocupadoVuelo, capMaxVuelo,
                    v.getOrigen(), ocupadoAlmacen, capMaxAlmacen);

            cursor = llegadaGMTVuelo;
        }
        System.out.println("-".repeat(70));
    }

    // =========================================================================
    // BITÁCORA CRONOLÓGICA
    // =========================================================================
    private static void procesarRutaEnBitacora(
            Ruta ruta, Map<String, Integer> mapaGmt,
            List<EventoSimulacion> bitacora) {

        Envio envio     = ruta.getEnvio();
        int   gmtOrigen = mapaGmt.getOrDefault(envio.getAeropuertoOrigen(), 0);

        LocalDateTime regLocal = LocalDateTime.of(
                Integer.parseInt(envio.getFechaRegistro().substring(0, 4)),
                Integer.parseInt(envio.getFechaRegistro().substring(4, 6)),
                Integer.parseInt(envio.getFechaRegistro().substring(6, 8)),
                envio.getHoraRegistro(), envio.getMinutoRegistro());
        LocalDateTime regGMT = regLocal.minusHours(gmtOrigen);
        LocalDateTime finGMT = regGMT.plusMinutes(ruta.getTiempoTotalMinutos());

        bitacora.add(new EventoSimulacion(regGMT, envio.getAeropuertoOrigen(),
                -envio.getCantidadMaletas(), "[CHECK-IN]   Envío " + envio.getIdEnvio()));

        LocalDateTime cursor = regGMT;
        if (ruta.getVuelos() != null) {
            for (PlanVuelo v : ruta.getVuelos()) {
                int gmtVOrig = mapaGmt.getOrDefault(v.getOrigen(),  0);
                int gmtVDest = mapaGmt.getOrDefault(v.getDestino(), 0);

                LocalTime sGMT = LocalTime.parse(v.getHoraSalida()).minusHours(gmtVOrig);
                LocalTime lGMT = LocalTime.parse(v.getHoraLlegada()).minusHours(gmtVDest);

                LocalDateTime salida  = cursor.with(sGMT);
                if (salida.isBefore(cursor)) salida = salida.plusDays(1);
                LocalDateTime llegada = salida.with(lGMT);
                if (llegada.isBefore(salida)) llegada = llegada.plusDays(1);

                bitacora.add(new EventoSimulacion(salida, v.getOrigen(),
                        envio.getCantidadMaletas(),
                        "[DESPEGUE]   " + v.getOrigen() + "->" + v.getDestino()
                                + " (" + envio.getIdEnvio() + ")"));
                bitacora.add(new EventoSimulacion(llegada, v.getDestino(),
                        -envio.getCantidadMaletas(),
                        "[ATERRIZAJE] " + v.getOrigen() + "->" + v.getDestino()
                                + " (" + envio.getIdEnvio() + ")"));
                cursor = llegada;
            }
        }

        bitacora.add(new EventoSimulacion(finGMT, envio.getAeropuertoDestino(),
                envio.getCantidadMaletas(), "[RECOJO]     Envío " + envio.getIdEnvio()));
    }

    // =========================================================================
    // BITÁCORA FINAL Y RESUMEN
    // =========================================================================
    private static void imprimirBitacoraFinal(
            List<EventoSimulacion> bitacora,
            Map<String, Integer> capsOrig,
            int total, boolean colapso,
            LocalDateTime relojParada,
            List<Ruta> rutasHistorico) {

        if (!rutasHistorico.isEmpty()) {
            System.out.println("\n" + "=".repeat(100));
            System.out.println("   MAPA DE RUTAS ASIGNADAS CONSOLIDADO (TOTAL HISTÓRICO)");
            System.out.println("=".repeat(100));
            System.out.printf(" %-10s | %-7s | %-20s | %-10s | %s%n",
                    "ENVÍO", "MALETAS", "ORIGEN->DESTINO", "TIEMPO", "ITINERARIO");
            System.out.println("-".repeat(100));
            for (Ruta r : rutasHistorico) {
                String origDest   = r.getEnvio().getAeropuertoOrigen()
                        + " -> " + r.getEnvio().getAeropuertoDestino();
                String tiempoStr  = String.format("%dh %02dm",
                        r.getTiempoTotalMinutos() / 60, r.getTiempoTotalMinutos() % 60);
                String itinerario = r.getVuelos().stream()
                        .map(v -> v.getOrigen() + "->" + v.getDestino())
                        .collect(Collectors.joining(", "));
                System.out.printf(" %-10s | %-7d | %-20s | %-10s | [%s]%n",
                        r.getEnvio().getIdEnvio(),
                        r.getEnvio().getCantidadMaletas(),
                        origDest, tiempoStr, itinerario);
            }
        }

        System.out.println("\n\n" + "=".repeat(80));
        System.out.println("       BITÁCORA CRONOLÓGICA DE EVENTOS (TODOS LOS ALMACENES)");
        System.out.println("=".repeat(80));

        bitacora.sort(Comparator.comparing(e -> e.tiempoGMT));
        Map<String, Integer> capsDin = new HashMap<>(capsOrig);

        for (EventoSimulacion e : bitacora) {
            int nueva   = capsDin.getOrDefault(e.aeropuerto, 0) + e.variacionCapacidad;
            capsDin.put(e.aeropuerto, nueva);
            int ocupado = capsOrig.getOrDefault(e.aeropuerto, 0) - nueva;
            System.out.printf("[%s GMT] %-40s | %s %d | Almacén %s: %d/%d%n",
                    e.tiempoGMT.format(FMT_LOG), e.mensaje,
                    (e.variacionCapacidad > 0 ? "LIBERA" : "OCUPA "),
                    Math.abs(e.variacionCapacidad),
                    e.aeropuerto, ocupado,
                    capsOrig.getOrDefault(e.aeropuerto, 0));
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println(" RESUMEN DEL ESCENARIO");
        System.out.println(" - Envíos planificados exitosamente: " + total);
        if (colapso) {
            System.out.println(" - [!] ESTADO: COLAPSO. El sistema se detuvo en: "
                    + relojParada.format(FMT_LOG));
        } else {
            System.out.println(" - [OK] La simulación completó su período exitosamente.");
        }
        System.out.println("=".repeat(80) + "\n");
    }
}