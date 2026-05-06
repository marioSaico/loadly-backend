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
import java.util.*;
import java.util.stream.Collectors;

@SpringBootApplication
public class BackendApplication {

    private static final DateTimeFormatter FMT_INPUT    = DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");
    private static final DateTimeFormatter FMT_LOG      = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FMT_DISPLAY  = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_FECHA    = DateTimeFormatter.ofPattern("yyyyMMdd");

    @FunctionalInterface
    interface PlanificadorFunc {
        Individuo planificar(String inicioStr, String fechaHoraLimite, int tamano, long tiempoMs);
    }

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(BackendApplication.class, args);
        Planificador    planificador    = context.getBean(Planificador.class);
        PlanificadorACO planificadorACO = context.getBean(PlanificadorACO.class);
        DataService     dataService     = context.getBean(DataService.class);

        System.out.println("=== SISTEMA DE PLANIFICACIÓN LOADLY ===");

        // ---------------------------------------------------------
        // 1. SELECCIÓN DE ALGORITMO (Comenta el que NO vayas a usar)
        // ---------------------------------------------------------
        PlanificadorFunc planFunc = (inicio, lim, tam, ms) -> planificador.planificar(inicio, lim, tam, ms);
        String nombreAlg = "GA";

        // Alternativa: algoritmo anterior
        //PlanificadorFunc planFunc = (inicio,lim, relojActual, tam, ms) -> planificadorACO.planificar(inicio,lim, relojActual,tam, ms);
        //String nombreAlg = "ACO";

        // ---------------------------------------------------------
        // 2. SELECCIÓN DE ESCENARIO (Descomenta SOLO 1 a la vez)
        // ---------------------------------------------------------
        //ejecutarEscenario("DIA A DIA", "20260101-20-40", "20260101-21-00", 5, 10, 1, 5, nombreAlg, planFunc, dataService);
        
        ejecutarEscenario("PERIODO", "20270515-00-00", "20270516-00-00", 30, 10, 6, 10, nombreAlg, planFunc, dataService);

        // ejecutarEscenario("COLAPSO", "20260101-00-00", "20260106-00-00", 45, 10, 7, 100, nombreAlg, planFunc, dataService);
    }

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

        boolean colapsoDetectado = false;
        ResultadoColapso colapsoFinal = null;
        Map<String, List<long[]>> timelineAlmacenesGlobal = new HashMap<>();

        System.out.println("\n" + "=".repeat(80));
        System.out.println("   INICIANDO ESCENARIO [" + tipoAlgoritmo + "]: " + nombre);
        System.out.println("   Período: " + inicioStr + " → " + finStr);
        System.out.println("=".repeat(80));

        long inicioEscenarioMs = System.currentTimeMillis();

        while ((limiteLecturaDatos.isBefore(finSimulacion) || limiteLecturaDatos.isEqual(finSimulacion)) && !colapsoDetectado) {

            String limiteLecturaStr = limiteLecturaDatos.format(FMT_INPUT);
            String relojActualStr   = relojSimulado.format(FMT_INPUT);

            System.out.println("\n>>> [RELOJ: " + relojSimulado.format(FMT_LOG) + "] Planificando envios hasta " + limiteLecturaDatos.format(FMT_LOG));

            dataService.procesarEventosDelReloj(relojActualStr);
            Individuo resultado = planFunc.planificar(inicioStr, limiteLecturaStr, tamano, tiempoLimiteMs);

            if (resultado != null) {
                ResultadoColapso colapso = detectarColapso(resultado, dataService, relojSimulado, timelineAlmacenesGlobal);
                
                if (colapso.hayColapso()) {
                    imprimirAlertas(colapso, relojSimulado);
                    colapsoDetectado = true;
                    colapsoFinal = colapso;
                } else {
                    imprimirReporteIntervalo(resultado, dataService, timelineAlmacenesGlobal);
                }
            }

            if (!colapsoDetectado) {
                limiteLecturaDatos = limiteLecturaDatos.plusMinutes(sc);
                relojSimulado      = relojSimulado.plusMinutes(sa);
            }
        }

        long tiempoEjecucionRealMs = System.currentTimeMillis() - inicioEscenarioMs;
        // [MODIFICADO] Ahora pasamos el DataService completo para imprimir el Backlog
        // Agregamos el timelineAlmacenesGlobal y la fecha de inicio para calcular promedios
        imprimirResumenFinal(dataService, colapsoFinal, relojSimulado, tiempoEjecucionRealMs, timelineAlmacenesGlobal, LocalDateTime.parse(inicioStr, FMT_INPUT));
    }

    private static void imprimirReporteIntervalo(
            Individuo resultado, DataService dataService, Map<String, List<long[]>> timelineAlmacenesGlobal) {

        List<Ruta> rutasOrdenadas = resultado.getRutas().stream()
                .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA && r.getVuelos() != null && !r.getVuelos().isEmpty())
                .sorted(Comparator.comparing(r -> {
                    Envio e = r.getEnvio();
                    int gmt = dataService.getMapaAeropuertos().get(e.getAeropuertoOrigen()).getGmt();
                    return LocalDateTime.of(LocalDate.parse(e.getFechaRegistro(), FMT_FECHA),
                            LocalTime.of(e.getHoraRegistro(), e.getMinutoRegistro())).minusHours(gmt);
                }))
                .collect(Collectors.toList());

        Map<String, Integer> ocupacionVuelos = new HashMap<>();
        
        for (Ruta r : rutasOrdenadas) {
            Envio envio = r.getEnvio();
            int gmtO = dataService.getMapaAeropuertos().get(envio.getAeropuertoOrigen()).getGmt();
            LocalDateTime regGMT = LocalDateTime.of(LocalDate.parse(envio.getFechaRegistro(), FMT_FECHA),
                    LocalTime.of(envio.getHoraRegistro(), envio.getMinutoRegistro())).minusHours(gmtO);

            agregarEventoTimeline(timelineAlmacenesGlobal, envio.getAeropuertoOrigen(), regGMT, +envio.getCantidadMaletas());

            LocalDateTime cursor = regGMT;
            for (PlanVuelo v : r.getVuelos()) {
                String clave = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
                ocupacionVuelos.merge(clave, envio.getCantidadMaletas(), Integer::sum);

                int gmtVOrig = dataService.getMapaAeropuertos().get(v.getOrigen()).getGmt();
                int gmtVDest = dataService.getMapaAeropuertos().get(v.getDestino()).getGmt();

                int minSGMT = (convertirAMinutos(v.getHoraSalida()) - gmtVOrig * 60 + 1440) % 1440;
                LocalDateTime despegue = cursor.with(minutosALocalTime(minSGMT));
                if (despegue.isBefore(cursor)) despegue = despegue.plusDays(1);

                int duracion = (convertirAMinutos(v.getHoraLlegada()) - gmtVDest * 60) - (convertirAMinutos(v.getHoraSalida()) - gmtVOrig * 60);
                if (duracion < 0) duracion += 1440;
                LocalDateTime llegada = despegue.plusMinutes(duracion);

                agregarEventoTimeline(timelineAlmacenesGlobal, v.getOrigen(), despegue, -envio.getCantidadMaletas());
                agregarEventoTimeline(timelineAlmacenesGlobal, v.getDestino(), llegada, +envio.getCantidadMaletas());
                
                cursor = llegada;
            }
            agregarEventoTimeline(timelineAlmacenesGlobal, envio.getAeropuertoDestino(), cursor, -envio.getCantidadMaletas());
        }

        for (Ruta r : rutasOrdenadas) {
            imprimirDetalleRuta(r, dataService, ocupacionVuelos, timelineAlmacenesGlobal);
        }
    }

    private static void imprimirDetalleRuta(Ruta r, DataService dataService,
                                            Map<String, Integer> ocupacionVuelos,
                                            Map<String, List<long[]>> timelineAlmacenes) {
        Envio env = r.getEnvio();
        Aeropuerto origen = dataService.getMapaAeropuertos().get(env.getAeropuertoOrigen());
        Aeropuerto destino = dataService.getMapaAeropuertos().get(env.getAeropuertoDestino());
        
        LocalDateTime regGMT = LocalDateTime.of(LocalDate.parse(env.getFechaRegistro(), FMT_FECHA),
                LocalTime.of(env.getHoraRegistro(), env.getMinutoRegistro())).minusHours(origen.getGmt());
        
        long horasTotales = r.getTiempoTotalMinutos() / 60;
        long minutosRestantes = r.getTiempoTotalMinutos() % 60;
        long slaHoras = (origen != null && destino != null && origen.getContinente().equals(destino.getContinente())) ? 24 : 48;

        // Exactamente tu diseño, pero con ASCII seguro para el Bloc de Notas
        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.printf("| ENVÍO: %-10s | %s -> %s | MALETAS: %-40d |%n",
                env.getIdEnvio(), env.getAeropuertoOrigen(), env.getAeropuertoDestino(), env.getCantidadMaletas());
        System.out.printf("| REGISTRO: %-16s | LLEGADA: %-46s |%n",
                regGMT.format(FMT_DISPLAY), regGMT.plusMinutes(r.getTiempoTotalMinutos()).format(FMT_DISPLAY));
        System.out.printf("| DURACIÓN: %02dh %02dm           | SLA: %dh                                               |%n",
                horasTotales, minutosRestantes, slaHoras);
        System.out.println("-----------------------------------------------------------------------------------------");

        LocalDateTime cursor = regGMT;
        int paso = 1;
        for (PlanVuelo v : r.getVuelos()) {
            Aeropuerto ao = dataService.getMapaAeropuertos().get(v.getOrigen());
            int gmtO = ao.getGmt();
            int gmtD = dataService.getMapaAeropuertos().get(v.getDestino()).getGmt();

            int minSGMT = (convertirAMinutos(v.getHoraSalida()) - gmtO * 60 + 1440) % 1440;
            LocalDateTime despegue = cursor.with(minutosALocalTime(minSGMT));
            if (despegue.isBefore(cursor)) despegue = despegue.plusDays(1);

            int duracion = (convertirAMinutos(v.getHoraLlegada()) - gmtD * 60) - (convertirAMinutos(v.getHoraSalida()) - gmtO * 60);
            if (duracion < 0) duracion += 1440;
            LocalDateTime llegada = despegue.plusMinutes(duracion);

            int ocupadoAlm = getOcupacionAlmacen(timelineAlmacenes, v.getOrigen(), despegue);
            String claveV = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();

            // Formato exacto para las líneas de vuelo
            System.out.printf("| (%d) %s->%-4s | Sale: %s | Llega: %s | Vuelo: %3d/%-3d | Almacen %s: %3d/%-3d |%n",
                    paso++, v.getOrigen(), v.getDestino(), despegue.toLocalTime(), llegada.toLocalTime(),
                    ocupacionVuelos.getOrDefault(claveV, 0), v.getCapacidad(), v.getOrigen(), ocupadoAlm, ao.getCapacidad());
            cursor = llegada;
        }
        System.out.println("-----------------------------------------------------------------------------------------\n");
    }

    // [MODIFICADO] Ahora incluye la sección de ENVÍOS PENDIENTES (Backlog)
    private static void imprimirResumenFinal(DataService dataService, ResultadoColapso colapso, LocalDateTime relojParada, long tiempoEjecucionRealMs, Map<String, List<long[]>> timelineAlmacenes, LocalDateTime relojInicio) {
        List<Ruta> rutasHistorico = dataService.getRutasPlanificadasHistorico();
        
        System.out.println("\n" + "=".repeat(120));
        System.out.println("   RESUMEN DEL ESCENARIO - CONSOLIDADO FINAL");
        System.out.println("=".repeat(120));
        System.out.printf(" %-12s | %-7s | %-12s | %-10s | %-6s | %-6s | %-9s | %s%n", 
                          "ENVÍO", "MALETAS", "RUTA", "TIEMPO", "% SLA", "LÍMITE", "ESTADO", "ITINERARIO");
        System.out.println("-".repeat(120));

        int totalMaletasPlanificadas = 0;
        double sumaConsumoSLA = 0; // Para el promedio global del SLA

        // Variables para Ocupación Promedio Vuelos
        Map<String, Integer> usoVuelo = new HashMap<>();
        Map<String, Integer> capVuelo = new HashMap<>();

        for (Ruta r : rutasHistorico) {
            totalMaletasPlanificadas += r.getEnvio().getCantidadMaletas();
            String itinerario = r.getVuelos().stream().map(v -> v.getOrigen() + "->" + v.getDestino()).collect(Collectors.joining(", "));
            
            Aeropuerto o = dataService.getMapaAeropuertos().get(r.getEnvio().getAeropuertoOrigen());
            Aeropuerto d = dataService.getMapaAeropuertos().get(r.getEnvio().getAeropuertoDestino());
            long slaHoras = (o != null && d != null && o.getContinente().equals(d.getContinente())) ? 24 : 48;
            
            double consumoSLA = (r.getTiempoTotalMinutos() * 100.0) / (slaHoras * 60);
            sumaConsumoSLA += consumoSLA; // Acumulamos el % para el promedio final
            
            String estadoSLA = (r.getTiempoTotalMinutos() <= (slaHoras * 60)) ? " OK" : " NO";

            System.out.printf(" %-12s | %-7d | %-12s | %2dh %02dm    | %5.1f%% | %2dh    | %-9s | [%s]%n",
                    r.getEnvio().getIdEnvio(), 
                    r.getEnvio().getCantidadMaletas(),
                    r.getEnvio().getAeropuertoOrigen() + "->" + r.getEnvio().getAeropuertoDestino(),
                    r.getTiempoTotalMinutos() / 60, r.getTiempoTotalMinutos() % 60, 
                    consumoSLA,
                    slaHoras, 
                    estadoSLA,
                    itinerario);

            // Registro de uso de vuelos
            if (r.getVuelos() != null) {
                for (PlanVuelo v : r.getVuelos()) {
                    String clave = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
                    usoVuelo.put(clave, usoVuelo.getOrDefault(clave, 0) + r.getEnvio().getCantidadMaletas());
                    capVuelo.put(clave, v.getCapacidad());
                }
            }
        }

        // --- CÁLCULO DE PROMEDIO PONDERADO DE VUELOS (La idea de tu amigo) ---
        long totalMaletasEnVuelos = 0;
        long totalCapacidadDeVuelosUsados = 0;
        for (String clave : usoVuelo.keySet()) {
            totalMaletasEnVuelos += usoVuelo.get(clave);
            totalCapacidadDeVuelosUsados += capVuelo.get(clave);
        }
        double promVuelos = totalCapacidadDeVuelosUsados == 0 ? 0 : (totalMaletasEnVuelos * 100.0) / totalCapacidadDeVuelosUsados;

        // --- CÁLCULO DE PROMEDIO DE SLA ---
        double promConsumoSLA = rutasHistorico.isEmpty() ? 0 : sumaConsumoSLA / rutasHistorico.size();

        // --- CÁLCULO DE PROMEDIO DE ALMACENES (Área bajo la curva / Tiempo) ---
        double sumaPorcentajesAlm = 0;
        int almacenesUsados = 0;
        long inicioMin = relojInicio.toEpochSecond(ZoneOffset.UTC) / 60;
        long finMin = relojParada.toEpochSecond(ZoneOffset.UTC) / 60;
        long totalMinutosSimulacion = finMin - inicioMin;

        if (totalMinutosSimulacion > 0) {
            for (Map.Entry<String, List<long[]>> entry : timelineAlmacenes.entrySet()) {
                Aeropuerto aero = dataService.getMapaAeropuertos().get(entry.getKey());
                if (aero == null || aero.getCapacidad() == 0) continue;

                List<long[]> eventos = new ArrayList<>(entry.getValue());
                eventos.sort(Comparator.comparingLong(a -> a[0])); // Orden cronológico

                long areaMaletaMinutos = 0;
                long lastTime = inicioMin;
                int currentOc = 0;

                for (long[] ev : eventos) {
                    long tiempoEvento = Math.max(inicioMin, Math.min(ev[0], finMin));
                    areaMaletaMinutos += currentOc * (tiempoEvento - lastTime);
                    currentOc += (int) ev[1];
                    lastTime = tiempoEvento;
                }
                areaMaletaMinutos += currentOc * (finMin - lastTime);

                double ocupacionPromedioMaletas = (double) areaMaletaMinutos / totalMinutosSimulacion;
                sumaPorcentajesAlm += (ocupacionPromedioMaletas * 100.0) / aero.getCapacidad();
                almacenesUsados++;
            }
        }
        double promAlmacenes = almacenesUsados == 0 ? 0 : sumaPorcentajesAlm / almacenesUsados;

        int enviosNoPlanificados = 0;
        int maletasNoPlanificadas = 0;
        if (colapso != null && colapso.hayColapso()) {
            enviosNoPlanificados = dataService.getEnviosEnEspera().size();
            maletasNoPlanificadas = dataService.getEnviosEnEspera().stream().mapToInt(Envio::getCantidadMaletas).sum();
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println(" ESTADÍSTICAS FINALES DEL ESCENARIO");
        System.out.println(" - Envíos procesados exitosamente: " + rutasHistorico.size());
        System.out.println(" - Total de maletas planificadas:  " + totalMaletasPlanificadas);
        if (colapso != null && colapso.hayColapso()) {
            System.out.println(" - Envíos NO planificados (Fallo): " + enviosNoPlanificados);
            System.out.println(" - Maletas NO planificadas:        " + maletasNoPlanificadas);
        }
        System.out.printf(" - Consumo prom. del SLA:          %.2f%%%n", promConsumoSLA);
        System.out.printf(" - Ocupación prom. Vuelos Usados:  %.2f%%%n", promVuelos);
        System.out.printf(" - Ocupación prom. de Almacenes:   %.2f%%%n", promAlmacenes);
        double FO = (promConsumoSLA*4 + promVuelos*3 + promAlmacenes*3)/10;
        System.out.printf(" - Funcion Objetivo:               %.2f%%%n", FO);
        System.out.printf(" - Tiempo de ejecución real:       %.3f segundos%n", (tiempoEjecucionRealMs / 1000.0));
        
        if (colapso != null && colapso.hayColapso()) {
            System.out.println(" - [!] ESTADO: COLAPSO DETECTADO en el reloj " + relojParada.format(FMT_LOG));
        } else {
            System.out.println(" - [OK] ESTADO: Simulación completada sin interrupciones limitantes.");
        }
        System.out.println("=".repeat(80));
    }

    // --- HELPERS ---
    private static void agregarEventoTimeline(Map<String, List<long[]>> timeline, String aero, LocalDateTime t, int d) {
        timeline.computeIfAbsent(aero, k -> new ArrayList<>()).add(new long[]{t.toEpochSecond(ZoneOffset.UTC) / 60, d});
    }

    private static int getOcupacionAlmacen(Map<String, List<long[]>> timeline, String aero, LocalDateTime despegueGMT) {
        long minLimit = despegueGMT.toEpochSecond(ZoneOffset.UTC) / 60;
        int ocupacion = 0;
        for (long[] ev : timeline.getOrDefault(aero, new ArrayList<>())) {
            if (ev[0] <= minLimit) ocupacion += (int) ev[1];
        }
        return Math.max(0, ocupacion);
    }

    private static int convertirAMinutos(String h) {
        String[] p = h.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    private static LocalTime minutosALocalTime(int m) {
        return LocalTime.of((m / 60) % 24, m % 60);
    }

    // =========================================================================
    // LÓGICA DE DETECCIÓN DE COLAPSO FORENSE
    // =========================================================================
    static class EventoForense {
        long minuto; int delta; String idEnvio;
        EventoForense(long m, int d, String id) { this.minuto = m; this.delta = d; this.idEnvio = id; }
    }

    private static ResultadoColapso detectarColapso(Individuo res, DataService ds, LocalDateTime reloj, Map<String, List<long[]>> timelineGlobal) {
        ResultadoColapso rc = new ResultadoColapso();
        Map<String, Integer> ocupacionVuelosActuales = new HashMap<>();

        for (Ruta r : res.getRutas()) {
            Envio env = r.getEnvio();
            if (r.getEstado() == EstadoRuta.INALCANZABLE) {
                rc.topologico = true; rc.idEnvioCausante = env.getIdEnvio();
                rc.rutaCausante = env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino(); // <-- NUEVO
                rc.maletasCausantes = env.getCantidadMaletas(); // <-- NUEVO
                rc.detalle = "No existe conexión física o vuelos factibles para llegar de " + env.getAeropuertoOrigen() + " a " + env.getAeropuertoDestino();
                return rc;
            } 
            else if (r.getEstado() == EstadoRuta.SIN_RUTA) {
                rc.porRutaNoEncontrada = true; 
                rc.idEnvioCausante = env.getIdEnvio();
                rc.rutaCausante = env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino(); // <-- NUEVO
                rc.maletasCausantes = env.getCantidadMaletas(); // <-- NUEVO
                rc.detalle = "No se encontró una solución que respete los límites de tiempo y capacidad.";
                return rc;
            }
            else if (r.getEstado() == EstadoRuta.PLANIFICADA) {
                Aeropuerto o = ds.getMapaAeropuertos().get(env.getAeropuertoOrigen());
                Aeropuerto d = ds.getMapaAeropuertos().get(env.getAeropuertoDestino());
                long sla = (o != null && d != null && o.getContinente().equals(d.getContinente())) ? 24 : 48;
                
                if (r.getTiempoTotalMinutos() > sla * 60) {
                    rc.porSLA = true; rc.idEnvioCausante = env.getIdEnvio();
                    rc.rutaCausante = env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino(); // <-- NUEVO
                rc.maletasCausantes = env.getCantidadMaletas(); // <-- NUEVO
                    rc.detalle = String.format("El tiempo calculado (%dh %dm) excede el SLA de %dh", r.getTiempoTotalMinutos()/60, r.getTiempoTotalMinutos()%60, sla);
                    return rc;
                }
                if (r.getVuelos() != null) {
                    for (PlanVuelo v : r.getVuelos()) {
                        String key = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
                        int nuevaOc = ocupacionVuelosActuales.getOrDefault(key, 0) + env.getCantidadMaletas();
                        ocupacionVuelosActuales.put(key, nuevaOc);
                        if (nuevaOc > v.getCapacidad()) {
                            rc.porEspacioVuelo = true; rc.idEnvioCausante = env.getIdEnvio();
                            rc.rutaCausante = env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino(); // <-- NUEVO
                            rc.maletasCausantes = env.getCantidadMaletas(); // <-- NUEVO
                            rc.detalle = String.format("El Vuelo %s->%s ha superado su capacidad física. Requerido: %d | Máximo: %d", v.getOrigen(), v.getDestino(), nuevaOc, v.getCapacidad());
                            return rc;
                        }
                    }
                }
            }
        }

        Map<String, List<EventoForense>> tlForense = new HashMap<>();
        for (Map.Entry<String, List<long[]>> entry : timelineGlobal.entrySet()) {
            List<EventoForense> lista = new ArrayList<>();
            for (long[] ev : entry.getValue()) lista.add(new EventoForense(ev[0], (int)ev[1], "HISTORICO"));
            tlForense.put(entry.getKey(), lista);
        }

        for (Ruta r : res.getRutas()) {
            if (r.getEstado() == EstadoRuta.PLANIFICADA && r.getVuelos() != null) {
                Envio env = r.getEnvio();
                Aeropuerto o = ds.getMapaAeropuertos().get(env.getAeropuertoOrigen());
                LocalDateTime cursor = LocalDateTime.of(LocalDate.parse(env.getFechaRegistro(), FMT_FECHA), LocalTime.of(env.getHoraRegistro(), env.getMinutoRegistro())).minusHours(o.getGmt());
                
                //tlForense.computeIfAbsent(o.getCodigo(), k -> new ArrayList<>()).add(new EventoForense(cursor.toEpochSecond(ZoneOffset.UTC)/60, env.getCantidadMaletas(), env.getIdEnvio()));

                for (PlanVuelo v : r.getVuelos()) {
                    int gmtO = ds.getMapaAeropuertos().get(v.getOrigen()).getGmt();
                    int gmtD = ds.getMapaAeropuertos().get(v.getDestino()).getGmt();
                    int minSGMT = (convertirAMinutos(v.getHoraSalida()) - gmtO * 60 + 1440) % 1440;
                    LocalDateTime despegue = cursor.with(minutosALocalTime(minSGMT));
                    if (despegue.isBefore(cursor)) despegue = despegue.plusDays(1);

                    int dur = (convertirAMinutos(v.getHoraLlegada()) - gmtD * 60) - (convertirAMinutos(v.getHoraSalida()) - gmtO * 60);
                    LocalDateTime llegada = despegue.plusMinutes(dur < 0 ? dur + 1440 : dur);

                    tlForense.computeIfAbsent(v.getOrigen(), k->new ArrayList<>()).add(new EventoForense(despegue.toEpochSecond(ZoneOffset.UTC)/60, -env.getCantidadMaletas(), env.getIdEnvio()));
                    tlForense.computeIfAbsent(v.getDestino(), k->new ArrayList<>()).add(new EventoForense(llegada.toEpochSecond(ZoneOffset.UTC)/60, +env.getCantidadMaletas(), env.getIdEnvio()));
                    cursor = llegada;
                }
                tlForense.computeIfAbsent(env.getAeropuertoDestino(), k->new ArrayList<>()).add(new EventoForense(cursor.toEpochSecond(ZoneOffset.UTC)/60, -env.getCantidadMaletas(), env.getIdEnvio()));
            }
        }

        for (Map.Entry<String, List<EventoForense>> entry : tlForense.entrySet()) {
            String aero = entry.getKey();
            int maxCap = ds.getMapaAeropuertos().get(aero).getCapacidad();
            List<EventoForense> eventos = entry.getValue();
            
            eventos.sort((a, b) -> a.minuto != b.minuto ? Long.compare(a.minuto, b.minuto) : Integer.compare(a.delta, b.delta));
            
            int ocupacion = 0;
            for (EventoForense ev : eventos) {
                ocupacion += ev.delta;
                if (ocupacion > maxCap) {
                    rc.porEspacioAlmacen = true; rc.idEnvioCausante = ev.idEnvio; rc.ubicacionConflicto = aero;
                    rc.detalle = String.format("Límite superado a las %s GMT. Ocupación: %d | Capacidad: %d", 
                                 LocalDateTime.ofEpochSecond(ev.minuto * 60, 0, ZoneOffset.UTC).format(FMT_LOG), ocupacion, maxCap);
                    return rc;
                }
            }
        }
        return rc;
    }

    private static void imprimirAlertas(ResultadoColapso rc, LocalDateTime reloj) {
        System.out.println("\n    " + "!".repeat(70));
        System.out.println("    [!] ¡COLAPSO DETECTADO EN EL SISTEMA!");
        System.out.println("    [!] MOMENTO DEL SISTEMA: " + reloj.format(FMT_LOG));
        System.out.println("    [!] TIPO DE FALLO:       " + rc.getTipoError());
        System.out.println("    [!] ENVÍO CAUSANTE:      " + rc.idEnvioCausante + " | RUTA: " + rc.rutaCausante + " | MALETAS: " + rc.maletasCausantes);
        if (rc.ubicacionConflicto != null) System.out.println("    [!] LUGAR CONFLICTO:     " + rc.ubicacionConflicto);
        System.out.println("    [!] DETALLE TÉCNICO:     " + rc.detalle);
        System.out.println("    " + "!".repeat(70) + "\n");
    }

    static class ResultadoColapso {
        boolean topologico = false, porSLA = false, porEspacioAlmacen = false, porEspacioVuelo = false;
        boolean porRutaNoEncontrada = false;
        String idEnvioCausante = "N/A";
        String rutaCausante = "N/A";
        int maletasCausantes = 0;
        String ubicacionConflicto = null;
        String detalle = "";

        boolean hayColapso() { return topologico || porSLA || porEspacioAlmacen || porEspacioVuelo || porRutaNoEncontrada; }

        String getTipoError() {
            if (topologico) return "ERROR TOPOLÓGICO (SIN RUTA FACTIBLE)";
            if (porRutaNoEncontrada) return "ERROR DE OPTIMIZACIÓN (NO SE PUDO ASIGNAR RUTA)";
            if (porSLA) return "INCUMPLIMIENTO DE SLA (TIEMPO EXCEDIDO)";
            if (porEspacioAlmacen) return "EXCESO DE CAPACIDAD EN ALMACÉN";
            if (porEspacioVuelo) return "EXCESO DE CAPACIDAD EN VUELO";
            return "MOTIVO DESCONOCIDO";
        }
    }
}