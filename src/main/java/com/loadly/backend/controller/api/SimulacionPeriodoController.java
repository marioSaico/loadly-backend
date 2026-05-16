package com.loadly.backend.controller.api;

import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.dto.SimulacionEventDTO;
import com.loadly.backend.dto.SimulacionEventDTO.*;
import com.loadly.backend.model.*;
import com.loadly.backend.planificador.Planificador;
import com.loadly.backend.service.DataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/simulacion")
@CrossOrigin(origins = "*")
public class SimulacionPeriodoController {

    private static final DateTimeFormatter FMT_INPUT = DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");
    private static final DateTimeFormatter FMT_LOG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FMT_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired private Planificador planificador;
    @Autowired private DataService dataService;

    @GetMapping(value = "/periodo", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter ejecutarSimulacion(
            @RequestParam String inicioStr, 
            @RequestParam String finStr,
            @RequestParam(defaultValue = "30") int taSegundos,
            @RequestParam(defaultValue = "10") int sa,
            @RequestParam(defaultValue = "6") int k,
            @RequestParam(defaultValue = "10") int tamano) {

        SseEmitter emitter = new SseEmitter(0L); 

        new Thread(() -> {
            try {
                LocalDateTime relojSimulado = LocalDateTime.parse(inicioStr, FMT_INPUT);
                LocalDateTime finSimulacion = LocalDateTime.parse(finStr, FMT_INPUT);
                LocalDateTime limiteLecturaDatos = relojSimulado;

                int sc = sa * k;
                long tiempoLimiteMs = taSegundos * 1000L;
                long inicioEscenarioMs = System.currentTimeMillis();

                Map<String, List<long[]>> timelineAlmacenesGlobal = new HashMap<>();
                List<Ruta> rutasHistorico = new ArrayList<>();
                boolean colapsoDetectado = false;
                ResultadoColapso colapsoFinal = null;

                System.out.println("\n" + "=".repeat(80));
                System.out.println("   INICIANDO ESCENARIO DESDE API (FRONTEND)");
                System.out.println("=".repeat(80));

                while ((limiteLecturaDatos.isBefore(finSimulacion) || limiteLecturaDatos.isEqual(finSimulacion)) && !colapsoDetectado) {
                    String limiteLecturaStr = limiteLecturaDatos.format(FMT_INPUT);
                    String relojActualStr = relojSimulado.format(FMT_INPUT);

                    System.out.println("\n>>> [RELOJ: " + relojSimulado.format(FMT_LOG) + "] Planificando...");

                    dataService.procesarEventosDelReloj(relojActualStr);
                    Individuo resultado = planificador.planificar(inicioStr, limiteLecturaStr, tamano, tiempoLimiteMs);

                    if (resultado != null) {
                        ResultadoColapso colapso = detectarColapso(resultado, dataService, relojSimulado, timelineAlmacenesGlobal);

                        if (colapso.hayColapso()) {
                            imprimirAlertas(colapso, relojSimulado);
                            colapsoFinal = colapso;
                            colapsoDetectado = true;
                            
                            enviarEvento(emitter, SimulacionEventDTO.builder()
                                    .tipo("COLAPSO")
                                    .relojSimulado(relojSimulado.format(FMT_DISPLAY))
                                    .colapso(ColapsoDTO.builder()
                                            .tipoError(colapso.getTipoError()).idEnvioCausante(colapso.idEnvioCausante)
                                            .rutaCausante(colapso.rutaCausante).maletasCausantes(colapso.maletasCausantes)
                                            .ubicacionConflicto(colapso.ubicacionConflicto).detalle(colapso.detalle).build())
                                    .build());
                            break;
                        } else {
                            List<SimulacionEventDTO.RutaPlanificadaDTO> dtosIteracion = procesarReporteYGenerarDTOs(
                                    resultado, dataService, timelineAlmacenesGlobal, rutasHistorico);

                            if (!dtosIteracion.isEmpty()) {
                                enviarEvento(emitter, SimulacionEventDTO.builder()
                                        .tipo("ITERACION")
                                        .relojSimulado(relojSimulado.format(FMT_DISPLAY))
                                        .rutasPlanificadas(dtosIteracion)
                                        .build());
                            }
                        }
                    }

                    if (!colapsoDetectado) {
                        limiteLecturaDatos = limiteLecturaDatos.plusMinutes(sc);
                        relojSimulado = relojSimulado.plusMinutes(sa);
                    }
                    Thread.sleep(100); 
                }

                long tiempoEjecucionRealMs = System.currentTimeMillis() - inicioEscenarioMs;
                
                // Imprimir el resumen final en la consola del backend
                imprimirResumenFinal(dataService, colapsoFinal, relojSimulado, tiempoEjecucionRealMs, timelineAlmacenesGlobal, LocalDateTime.parse(inicioStr, FMT_INPUT));

                // Enviar resumen al front
                enviarEvento(emitter, SimulacionEventDTO.builder()
                        .tipo("RESUMEN_FINAL")
                        .resumenFinal(ResumenFinalDTO.builder()
                                .totalEnviosPlanificados(rutasHistorico.size())
                                .totalMaletasPlanificadas(rutasHistorico.stream().mapToInt(r -> r.getEnvio().getCantidadMaletas()).sum())
                                .tiempoEjecucionRealSegundos(tiempoEjecucionRealMs / 1000.0)
                                .build())
                        .build());

                emitter.complete();

            } catch (Exception e) {
                e.printStackTrace();
                emitter.completeWithError(e);
            }
        }).start();

        return emitter;
    }

    // =========================================================================
    // LÓGICA DE PROCESAMIENTO Y DTOs
    // =========================================================================

    private List<SimulacionEventDTO.RutaPlanificadaDTO> procesarReporteYGenerarDTOs(
            Individuo resultado, DataService dataService, 
            Map<String, List<long[]>> timelineAlmacenesGlobal, List<Ruta> rutasHistorico) {

        List<SimulacionEventDTO.RutaPlanificadaDTO> dtosGenerados = new ArrayList<>();
        Map<String, Integer> ocupacionVuelos = new HashMap<>();

        List<Ruta> rutasOrdenadas = resultado.getRutas().stream()
                .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA && r.getVuelos() != null && !r.getVuelos().isEmpty())
                .sorted(Comparator.comparing(r -> {
                    Envio e = r.getEnvio();
                    int gmt = dataService.getMapaAeropuertos().get(e.getAeropuertoOrigen()).getGmt();
                    return LocalDateTime.of(LocalDate.parse(e.getFechaRegistro(), FMT_FECHA),
                            LocalTime.of(e.getHoraRegistro(), e.getMinutoRegistro())).minusHours(gmt);
                }))
                .collect(Collectors.toList());

        for (Ruta r : rutasOrdenadas) {
            rutasHistorico.add(r);
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
            dtosGenerados.add(construirRutaDTO(r, dataService, ocupacionVuelos, timelineAlmacenesGlobal));
        }

        return dtosGenerados;
    }

    private SimulacionEventDTO.RutaPlanificadaDTO construirRutaDTO(Ruta r, DataService ds, Map<String, Integer> ocupacionVuelos, Map<String, List<long[]>> timeline) {
        Envio env = r.getEnvio();
        Aeropuerto origen = ds.getMapaAeropuertos().get(env.getAeropuertoOrigen());
        Aeropuerto destino = ds.getMapaAeropuertos().get(env.getAeropuertoDestino());

        LocalDateTime regGMT = LocalDateTime.of(LocalDate.parse(env.getFechaRegistro(), FMT_FECHA),
                LocalTime.of(env.getHoraRegistro(), env.getMinutoRegistro())).minusHours(origen.getGmt());

        long horasTotales = r.getTiempoTotalMinutos() / 60;
        long minutosRestantes = r.getTiempoTotalMinutos() % 60;
        long slaHoras = (origen != null && destino != null && origen.getContinente().equals(destino.getContinente())) ? 24 : 48;

        List<SimulacionEventDTO.VueloPlanificadoDTO> tramos = new ArrayList<>();
        LocalDateTime cursor = regGMT;
        int paso = 1;

        for (PlanVuelo v : r.getVuelos()) {
            Aeropuerto ao = ds.getMapaAeropuertos().get(v.getOrigen());
            Aeropuerto ad = ds.getMapaAeropuertos().get(v.getDestino());
            
            int minSGMT = (convertirAMinutos(v.getHoraSalida()) - ao.getGmt() * 60 + 1440) % 1440;
            LocalDateTime despegue = cursor.with(minutosALocalTime(minSGMT));
            if (despegue.isBefore(cursor)) despegue = despegue.plusDays(1);

            int duracion = (convertirAMinutos(v.getHoraLlegada()) - ad.getGmt() * 60) - (convertirAMinutos(v.getHoraSalida()) - ao.getGmt() * 60);
            if (duracion < 0) duracion += 1440;
            LocalDateTime llegada = despegue.plusMinutes(duracion);

            String claveV = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();

            tramos.add(SimulacionEventDTO.VueloPlanificadoDTO.builder()
                    .orden(paso++)
                    .origen(v.getOrigen()).destino(v.getDestino())
                    .sale(despegue.toLocalTime().toString()).llega(llegada.toLocalTime().toString())
                    .maletasVuelo(ocupacionVuelos.getOrDefault(claveV, 0)).capacidadVuelo(v.getCapacidad())
                    .ocupacionAlmacenOrigen(getOcupacionAlmacen(timeline, v.getOrigen(), despegue))
                    .capacidadAlmacenOrigen(ao.getCapacidad())
                    .ocupacionAlmacenDestino(getOcupacionAlmacen(timeline, v.getDestino(), llegada)) // Destino agregado
                    .capacidadAlmacenDestino(ad.getCapacidad())
                    .build());
            
            cursor = llegada;
        }

        return SimulacionEventDTO.RutaPlanificadaDTO.builder()
                .idEnvio(env.getIdEnvio()).origen(env.getAeropuertoOrigen()).destino(env.getAeropuertoDestino())
                .maletas(env.getCantidadMaletas()).fechaRegistro(regGMT.format(FMT_DISPLAY))
                .fechaLlegada(cursor.format(FMT_DISPLAY))
                .duracion(String.format("%02dh %02dm", horasTotales, minutosRestantes))
                .sla(slaHoras + "h").tramos(tramos).build();
    }

    private void enviarEvento(SseEmitter emitter, SimulacionEventDTO dto) {
        try { emitter.send(SseEmitter.event().name(dto.getTipo()).data(dto)); } catch (Exception ignored) {}
    }

    // =========================================================================
    // UTILIDADES CLONADAS EXACTAMENTE DEL MAIN
    // =========================================================================

    private static void agregarEventoTimeline(Map<String, List<long[]>> timeline, String aero, LocalDateTime t, int d) {
        timeline.computeIfAbsent(aero, k -> new ArrayList<>()).add(new long[] { t.toEpochSecond(ZoneOffset.UTC) / 60, d });
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
    // LÓGICA DE DETECCIÓN DE COLAPSO FORENSE (IDÉNTICA)
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
                rc.rutaCausante = env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino();
                rc.maletasCausantes = env.getCantidadMaletas();
                rc.detalle = "No existe conexión física o vuelos factibles para llegar de " + env.getAeropuertoOrigen() + " a " + env.getAeropuertoDestino();
                return rc;
            } else if (r.getEstado() == EstadoRuta.SIN_RUTA) {
                rc.porRutaNoEncontrada = true; rc.idEnvioCausante = env.getIdEnvio();
                rc.rutaCausante = env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino();
                rc.maletasCausantes = env.getCantidadMaletas();
                rc.detalle = "No se encontró una solución que respete los límites de tiempo y capacidad.";
                return rc;
            } else if (r.getEstado() == EstadoRuta.PLANIFICADA) {
                Aeropuerto o = ds.getMapaAeropuertos().get(env.getAeropuertoOrigen());
                Aeropuerto d = ds.getMapaAeropuertos().get(env.getAeropuertoDestino());
                long sla = (o != null && d != null && o.getContinente().equals(d.getContinente())) ? 24 : 48;

                if (r.getTiempoTotalMinutos() > sla * 60) {
                    rc.porSLA = true; rc.idEnvioCausante = env.getIdEnvio();
                    rc.rutaCausante = env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino();
                    rc.maletasCausantes = env.getCantidadMaletas();
                    rc.detalle = String.format("El tiempo calculado (%dh %dm) excede el SLA de %dh", r.getTiempoTotalMinutos() / 60, r.getTiempoTotalMinutos() % 60, sla);
                    return rc;
                }
                if (r.getVuelos() != null) {
                    for (PlanVuelo v : r.getVuelos()) {
                        String key = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
                        int nuevaOc = ocupacionVuelosActuales.getOrDefault(key, 0) + env.getCantidadMaletas();
                        ocupacionVuelosActuales.put(key, nuevaOc);
                        if (nuevaOc > v.getCapacidad()) {
                            rc.porEspacioVuelo = true; rc.idEnvioCausante = env.getIdEnvio();
                            rc.rutaCausante = env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino();
                            rc.maletasCausantes = env.getCantidadMaletas();
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
            for (long[] ev : entry.getValue()) lista.add(new EventoForense(ev[0], (int) ev[1], "HISTORICO"));
            tlForense.put(entry.getKey(), lista);
        }

        for (Ruta r : res.getRutas()) {
            if (r.getEstado() == EstadoRuta.PLANIFICADA && r.getVuelos() != null) {
                Envio env = r.getEnvio();
                Aeropuerto o = ds.getMapaAeropuertos().get(env.getAeropuertoOrigen());
                LocalDateTime cursor = LocalDateTime.of(LocalDate.parse(env.getFechaRegistro(), FMT_FECHA),
                        LocalTime.of(env.getHoraRegistro(), env.getMinutoRegistro())).minusHours(o.getGmt());

                for (PlanVuelo v : r.getVuelos()) {
                    int gmtO = ds.getMapaAeropuertos().get(v.getOrigen()).getGmt();
                    int gmtD = ds.getMapaAeropuertos().get(v.getDestino()).getGmt();
                    int minSGMT = (convertirAMinutos(v.getHoraSalida()) - gmtO * 60 + 1440) % 1440;
                    LocalDateTime despegue = cursor.with(minutosALocalTime(minSGMT));
                    if (despegue.isBefore(cursor)) despegue = despegue.plusDays(1);

                    int dur = (convertirAMinutos(v.getHoraLlegada()) - gmtD * 60) - (convertirAMinutos(v.getHoraSalida()) - gmtO * 60);
                    LocalDateTime llegada = despegue.plusMinutes(dur < 0 ? dur + 1440 : dur);

                    tlForense.computeIfAbsent(v.getOrigen(), k -> new ArrayList<>()).add(new EventoForense(despegue.toEpochSecond(ZoneOffset.UTC) / 60, -env.getCantidadMaletas(), env.getIdEnvio()));
                    tlForense.computeIfAbsent(v.getDestino(), k -> new ArrayList<>()).add(new EventoForense(llegada.toEpochSecond(ZoneOffset.UTC) / 60, +env.getCantidadMaletas(), env.getIdEnvio()));
                    cursor = llegada;
                }
                tlForense.computeIfAbsent(env.getAeropuertoDestino(), k -> new ArrayList<>()).add(new EventoForense(cursor.toEpochSecond(ZoneOffset.UTC) / 60, -env.getCantidadMaletas(), env.getIdEnvio()));
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

    private static void imprimirDetalleRuta(Ruta r, DataService dataService, Map<String, Integer> ocupacionVuelos, Map<String, List<long[]>> timelineAlmacenes) {
        Envio env = r.getEnvio();
        Aeropuerto origen = dataService.getMapaAeropuertos().get(env.getAeropuertoOrigen());
        Aeropuerto destino = dataService.getMapaAeropuertos().get(env.getAeropuertoDestino());

        LocalDateTime regGMT = LocalDateTime.of(LocalDate.parse(env.getFechaRegistro(), FMT_FECHA), LocalTime.of(env.getHoraRegistro(), env.getMinutoRegistro())).minusHours(origen.getGmt());

        long horasTotales = r.getTiempoTotalMinutos() / 60;
        long minutosRestantes = r.getTiempoTotalMinutos() % 60;
        long slaHoras = (origen != null && destino != null && origen.getContinente().equals(destino.getContinente())) ? 24 : 48;

        System.out.println("-----------------------------------------------------------------------------------------");
        System.out.printf("| ENVÍO: %-10s | %s -> %s | MALETAS: %-40d |%n", env.getIdEnvio(), env.getAeropuertoOrigen(), env.getAeropuertoDestino(), env.getCantidadMaletas());
        System.out.printf("| REGISTRO: %-16s | LLEGADA: %-46s |%n", regGMT.format(FMT_DISPLAY), regGMT.plusMinutes(r.getTiempoTotalMinutos()).format(FMT_DISPLAY));
        System.out.printf("| DURACIÓN: %02dh %02dm           | SLA: %dh                                               |%n", horasTotales, minutosRestantes, slaHoras);
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

            System.out.printf("| (%d) %s->%-4s | Sale: %s | Llega: %s | Vuelo: %3d/%-3d | Almacen %s: %3d/%-3d |%n",
                    paso++, v.getOrigen(), v.getDestino(), despegue.toLocalTime(), llegada.toLocalTime(),
                    ocupacionVuelos.getOrDefault(claveV, 0), v.getCapacidad(), v.getOrigen(), ocupadoAlm, ao.getCapacidad());
            cursor = llegada;
        }
        System.out.println("-----------------------------------------------------------------------------------------\n");
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

    private static void imprimirResumenFinal(DataService dataService, ResultadoColapso colapso, LocalDateTime relojParada, long tiempoEjecucionRealMs, Map<String, List<long[]>> timelineAlmacenes, LocalDateTime relojInicio) {
        List<Ruta> rutasHistorico = dataService.getRutasPlanificadasHistorico();

        System.out.println("\n" + "=".repeat(120));
        System.out.println("   RESUMEN DEL ESCENARIO - CONSOLIDADO FINAL");
        System.out.println("=".repeat(120));
        System.out.printf(" %-12s | %-7s | %-12s | %-10s | %-6s | %-6s | %-9s | %s%n", "ENVÍO", "MALETAS", "RUTA", "TIEMPO", "% SLA", "LÍMITE", "ESTADO", "ITINERARIO");
        System.out.println("-".repeat(120));

        int totalMaletasPlanificadas = 0;
        double sumaConsumoSLA = 0;
        Map<String, Integer> usoVuelo = new HashMap<>();
        Map<String, Integer> capVuelo = new HashMap<>();

        for (Ruta r : rutasHistorico) {
            totalMaletasPlanificadas += r.getEnvio().getCantidadMaletas();
            String itinerario = r.getVuelos().stream().map(v -> v.getOrigen() + "->" + v.getDestino()).collect(Collectors.joining(", "));

            Aeropuerto o = dataService.getMapaAeropuertos().get(r.getEnvio().getAeropuertoOrigen());
            Aeropuerto d = dataService.getMapaAeropuertos().get(r.getEnvio().getAeropuertoDestino());
            long slaHoras = (o != null && d != null && o.getContinente().equals(d.getContinente())) ? 24 : 48;

            double consumoSLA = (r.getTiempoTotalMinutos() * 100.0) / (slaHoras * 60);
            sumaConsumoSLA += consumoSLA;
            String estadoSLA = (r.getTiempoTotalMinutos() <= (slaHoras * 60)) ? " OK" : " NO";

            System.out.printf(" %-12s | %-7d | %-12s | %2dh %02dm    | %5.1f%% | %2dh    | %-9s | [%s]%n",
                    r.getEnvio().getIdEnvio(), r.getEnvio().getCantidadMaletas(),
                    r.getEnvio().getAeropuertoOrigen() + "->" + r.getEnvio().getAeropuertoDestino(),
                    r.getTiempoTotalMinutos() / 60, r.getTiempoTotalMinutos() % 60, consumoSLA, slaHoras, estadoSLA, itinerario);

            if (r.getVuelos() != null) {
                for (PlanVuelo v : r.getVuelos()) {
                    String clave = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
                    usoVuelo.put(clave, usoVuelo.getOrDefault(clave, 0) + r.getEnvio().getCantidadMaletas());
                    capVuelo.put(clave, v.getCapacidad());
                }
            }
        }

        long totalMaletasEnVuelos = 0, totalCapacidadDeVuelosUsados = 0;
        for (String clave : usoVuelo.keySet()) {
            totalMaletasEnVuelos += usoVuelo.get(clave);
            totalCapacidadDeVuelosUsados += capVuelo.get(clave);
        }
        double promVuelos = totalCapacidadDeVuelosUsados == 0 ? 0 : (totalMaletasEnVuelos * 100.0) / totalCapacidadDeVuelosUsados;
        double promConsumoSLA = rutasHistorico.isEmpty() ? 0 : sumaConsumoSLA / rutasHistorico.size();

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
                eventos.sort(Comparator.comparingLong(a -> a[0]));

                long areaMaletaMinutos = 0, lastTime = inicioMin;
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

        int enviosNoPlanificados = 0, maletasNoPlanificadas = 0;
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
        double FO = (promConsumoSLA * 4 + promVuelos * 3 + promAlmacenes * 3) / 10;
        System.out.printf(" - Funcion Objetivo:               %.2f%%%n", FO);
        System.out.printf(" - Tiempo de ejecución real:       %.3f segundos%n", (tiempoEjecucionRealMs / 1000.0));

        if (colapso != null && colapso.hayColapso()) {
            System.out.println(" - [!] ESTADO: COLAPSO DETECTADO en el reloj " + relojParada.format(FMT_LOG));
        } else {
            System.out.println(" - [OK] ESTADO: Simulación completada sin interrupciones limitantes.");
        }
        System.out.println("=".repeat(80));
    }

    static class ResultadoColapso {
        boolean topologico = false, porSLA = false, porEspacioAlmacen = false, porEspacioVuelo = false, porRutaNoEncontrada = false;
        String idEnvioCausante = "N/A", rutaCausante = "N/A", ubicacionConflicto = null, detalle = "";
        int maletasCausantes = 0;

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