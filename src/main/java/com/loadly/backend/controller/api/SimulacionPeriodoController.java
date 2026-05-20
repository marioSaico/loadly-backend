package com.loadly.backend.controller.api;

import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.dto.SimulacionEventDTO;
import com.loadly.backend.dto.SimulacionEventDTO.*;
import com.loadly.backend.model.*;
import com.loadly.backend.planificador.Planificador;
import com.loadly.backend.service.DataService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/simulacion")
@CrossOrigin(origins = "*")
public class SimulacionPeriodoController {

    private static final DateTimeFormatter FMT_INPUT = DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");
    private static final DateTimeFormatter FMT_LOG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FMT_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("yyyyMMdd");

    private final Planificador planificador;
    private final DataService dataService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    // AQUÍ GUARDAMOS EL ÚLTIMO RESUMEN GENERADO
    private ResumenFinalDTO ultimoResumen = null;

    public SimulacionPeriodoController(Planificador planificador, DataService dataService) {
        this.planificador = planificador;
        this.dataService = dataService;
    }

    // =========================================================================
    // 1. API DE SIMULACIÓN (SSE) - SOLO PLANIFICACIÓN Y COLAPSOS
    // =========================================================================
    @GetMapping(value = "/periodo/iniciar", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter iniciarSimulacion(
            @RequestParam String inicioStr,
            @RequestParam String finStr,
            @RequestParam(defaultValue = "30") int taSegundos,
            @RequestParam(defaultValue = "10") int sa,
            @RequestParam(defaultValue = "6") int k,
            @RequestParam(defaultValue = "10") int tamano) {

        SseEmitter emitter = new SseEmitter(0L); // Timeout infinito para que no se corte

        // Aquí usamos el hilo secundario para "bombear" datos al Front-end
        executor.execute(() -> {
            try {
                ejecutarEscenario(emitter, inicioStr, finStr, taSegundos, sa, k, tamano);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter; // Retornamos el tubo inmediatamente
    }

    // =========================================================================
    // 2. NUEVA API: OBTENER EL RESUMEN FINAL
    // =========================================================================
    @GetMapping(value = "/periodo/resumen", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResumenFinalDTO> obtenerResumenFinal() {
        if (ultimoResumen == null) {
            // Si nadie ha corrido la simulación aún, devolvemos un 204 No Content
            return ResponseEntity.noContent().build(); 
        }
        // Devolvemos el JSON limpio y directo
        return ResponseEntity.ok(ultimoResumen);
    }

    // =========================================================================
    // LÓGICA DEL MOTOR
    // =========================================================================
    private void ejecutarEscenario(SseEmitter emitter, String inicioStr, String finStr, int taSegundos, int sa, int k, int tamano) throws Exception {

        LocalDateTime relojSimulado      = LocalDateTime.parse(inicioStr, FMT_INPUT);
        LocalDateTime finSimulacion      = LocalDateTime.parse(finStr,    FMT_INPUT);
        LocalDateTime limiteLecturaDatos = relojSimulado;

        int  sc             = sa * k;
        long tiempoLimiteMs = taSegundos * 1000L;

        boolean colapsoDetectado = false;
        ResultadoColapso colapsoFinal = null;
        Map<String, List<long[]>> timelineAlmacenesGlobal = new HashMap<>();
        Map<String, Integer> ocupacionVuelosGlobal = new HashMap<>();

        long inicioEscenarioMs = System.currentTimeMillis();

        while ((limiteLecturaDatos.isBefore(finSimulacion) || limiteLecturaDatos.isEqual(finSimulacion)) && !colapsoDetectado) {

            String limiteLecturaStr = limiteLecturaDatos.format(FMT_INPUT);
            String relojActualStr   = relojSimulado.format(FMT_INPUT);

            dataService.procesarEventosDelReloj(limiteLecturaStr);
            Individuo resultado = planificador.planificar(inicioStr, limiteLecturaStr, tamano, tiempoLimiteMs);

            if (resultado != null) {
                ResultadoColapso colapso = detectarColapso(resultado, dataService, relojSimulado, timelineAlmacenesGlobal, ocupacionVuelosGlobal);
                
                if (colapso.hayColapso()) {
                    colapsoDetectado = true;
                    colapsoFinal = colapso;
                    enviarColapso(emitter, colapso, relojSimulado, limiteLecturaDatos);
                } else {
                    enviarIteracion(emitter, resultado, dataService, timelineAlmacenesGlobal, ocupacionVuelosGlobal, relojSimulado, limiteLecturaDatos);
                }
            }

            if (!colapsoDetectado) {
                limiteLecturaDatos = limiteLecturaDatos.plusMinutes(sc);
                relojSimulado      = relojSimulado.plusMinutes(sa);
            }
        }

        long tiempoEjecucionRealMs = System.currentTimeMillis() - inicioEscenarioMs;
        generarYGuardarResumen(dataService, colapsoFinal, limiteLecturaDatos, tiempoEjecucionRealMs, timelineAlmacenesGlobal, LocalDateTime.parse(inicioStr, FMT_INPUT), ocupacionVuelosGlobal);
        emitter.complete(); // Cerramos la conexión elegantemente
    }

    // =========================================================================
    // MÉTODOS DE MAPEO (Reemplazan a los System.out.println)
    // =========================================================================

    private void enviarIteracion(SseEmitter emitter, Individuo resultado, DataService dataService, 
                                 Map<String, List<long[]>> timelineAlmacenesGlobal, 
                                 Map<String, Integer> ocupacionVuelosGlobal, 
                                 LocalDateTime relojSimulado, LocalDateTime limiteLecturaDatos) throws Exception {

        List<Ruta> rutasOrdenadas = resultado.getRutas().stream()
                .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA && r.getVuelos() != null && !r.getVuelos().isEmpty())
                .sorted(Comparator.comparing(r -> {
                    Envio e = r.getEnvio();
                    int gmt = dataService.getMapaAeropuertos().get(e.getAeropuertoOrigen()).getGmt();
                    return LocalDateTime.of(LocalDate.parse(e.getFechaRegistro(), FMT_FECHA),
                            LocalTime.of(e.getHoraRegistro(), e.getMinutoRegistro())).minusHours(gmt);
                }))
                .collect(Collectors.toList());
        
        List<RutaPlanificadaDTO> rutasDTO = new ArrayList<>();

        for (Ruta r : rutasOrdenadas) {
            Envio envio = r.getEnvio();
            int gmtO = dataService.getMapaAeropuertos().get(envio.getAeropuertoOrigen()).getGmt();
            LocalDateTime regGMT = LocalDateTime.of(LocalDate.parse(envio.getFechaRegistro(), FMT_FECHA),
                    LocalTime.of(envio.getHoraRegistro(), envio.getMinutoRegistro())).minusHours(gmtO);

            agregarEventoTimeline(timelineAlmacenesGlobal, envio.getAeropuertoOrigen(), regGMT, +envio.getCantidadMaletas());

            List<VueloPlanificadoDTO> tramosDTO = new ArrayList<>();
            LocalDateTime cursor = regGMT;
            int paso = 1;

            for (PlanVuelo v : r.getVuelos()) {
                String clave = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
                ocupacionVuelosGlobal.merge(clave, envio.getCantidadMaletas(), Integer::sum);

                Aeropuerto ao = dataService.getMapaAeropuertos().get(v.getOrigen());
                Aeropuerto ad = dataService.getMapaAeropuertos().get(v.getDestino());

                int minSGMT = (convertirAMinutos(v.getHoraSalida()) - ao.getGmt() * 60 + 1440) % 1440;
                LocalDateTime despegue = cursor.with(minutosALocalTime(minSGMT));
                if (despegue.isBefore(cursor)) despegue = despegue.plusDays(1);

                int duracion = (convertirAMinutos(v.getHoraLlegada()) - ad.getGmt() * 60) - (convertirAMinutos(v.getHoraSalida()) - ao.getGmt() * 60);
                if (duracion < 0) duracion += 1440;
                LocalDateTime llegada = despegue.plusMinutes(duracion);

                agregarEventoTimeline(timelineAlmacenesGlobal, v.getOrigen(), despegue, -envio.getCantidadMaletas());
                agregarEventoTimeline(timelineAlmacenesGlobal, v.getDestino(), llegada, +envio.getCantidadMaletas());

                int ocupadoAlmOrig = getOcupacionAlmacen(timelineAlmacenesGlobal, v.getOrigen(), despegue);
                int ocupadoAlmDest = getOcupacionAlmacen(timelineAlmacenesGlobal, v.getDestino(), llegada);

                tramosDTO.add(VueloPlanificadoDTO.builder()
                        .orden(paso++)
                        .origen(v.getOrigen())
                        .destino(v.getDestino())
                        .sale(despegue.toLocalTime().toString())
                        .llega(llegada.toLocalTime().toString())
                        .maletasVuelo(ocupacionVuelosGlobal.get(clave))
                        .capacidadVuelo(v.getCapacidad())
                        .ocupacionAlmacenOrigen(ocupadoAlmOrig)
                        .capacidadAlmacenOrigen(ao.getCapacidad())
                        .ocupacionAlmacenDestino(ocupadoAlmDest)
                        .capacidadAlmacenDestino(ad.getCapacidad())
                        .build());
                
                cursor = llegada;
            }
            
            cursor = cursor.plusMinutes(10);
            agregarEventoTimeline(timelineAlmacenesGlobal, envio.getAeropuertoDestino(), cursor, -envio.getCantidadMaletas());

            Aeropuerto origen = dataService.getMapaAeropuertos().get(envio.getAeropuertoOrigen());
            Aeropuerto destino = dataService.getMapaAeropuertos().get(envio.getAeropuertoDestino());
            long horasTotales = r.getTiempoTotalMinutos() / 60;
            long minutosRestantes = r.getTiempoTotalMinutos() % 60;
            long slaHoras = (origen != null && destino != null && origen.getContinente().equals(destino.getContinente())) ? 24 : 48;

            rutasDTO.add(RutaPlanificadaDTO.builder()
                    .idEnvio(envio.getIdEnvio())
                    .origen(envio.getAeropuertoOrigen())
                    .destino(envio.getAeropuertoDestino())
                    .maletas(envio.getCantidadMaletas())
                    .fechaRegistro(regGMT.format(FMT_DISPLAY))
                    .fechaLlegada(regGMT.plusMinutes(r.getTiempoTotalMinutos()).format(FMT_DISPLAY))
                    .duracion(String.format("%02dh %02dm", horasTotales, minutosRestantes))
                    .sla(slaHoras + "h")
                    .tramos(tramosDTO)
                    .build());
        }

        SimulacionEventDTO evento = SimulacionEventDTO.builder()
                .tipo("ITERACION")
                .relojSimulado(relojSimulado.format(FMT_LOG))
                .limiteLectura(limiteLecturaDatos.format(FMT_LOG))
                .rutasPlanificadas(rutasDTO)
                .build();

        emitter.send(evento);
    }

    private void enviarColapso(SseEmitter emitter, ResultadoColapso colapso, LocalDateTime relojSimulado, LocalDateTime limiteLecturaDatos) throws Exception {
        ColapsoDTO colapsoDTO = ColapsoDTO.builder()
                .tipoError(colapso.getTipoError())
                .idEnvioCausante(colapso.idEnvioCausante)
                .rutaCausante(colapso.rutaCausante)
                .maletasCausantes(colapso.maletasCausantes)
                .ubicacionConflicto(colapso.ubicacionConflicto)
                .detalle(colapso.detalle)
                .relojColapso(relojSimulado.format(FMT_LOG))
                .build();

        emitter.send(SimulacionEventDTO.builder()
                .tipo("COLAPSO")
                .relojSimulado(relojSimulado.format(FMT_LOG))
                .limiteLectura(limiteLecturaDatos.format(FMT_LOG))
                .colapso(colapsoDTO)
                .build());
    }

    private void generarYGuardarResumen(DataService dataService, ResultadoColapso colapso, LocalDateTime relojParada, long tiempoEjecucionRealMs, Map<String, List<long[]>> timelineAlmacenes, LocalDateTime relojInicio, Map<String, Integer> ocupacionVuelosGlobal) {
        
        List<Ruta> rutasHistorico = dataService.getRutasPlanificadasHistorico();
        int totalMaletasPlanificadas = 0;
        double sumaConsumoSLA = 0;
        Map<String, Integer> capVuelo = new HashMap<>();

        for (Ruta r : rutasHistorico) {
            totalMaletasPlanificadas += r.getEnvio().getCantidadMaletas();
            Aeropuerto o = dataService.getMapaAeropuertos().get(r.getEnvio().getAeropuertoOrigen());
            Aeropuerto d = dataService.getMapaAeropuertos().get(r.getEnvio().getAeropuertoDestino());
            long slaHoras = (o != null && d != null && o.getContinente().equals(d.getContinente())) ? 24 : 48;
            
            sumaConsumoSLA += (r.getTiempoTotalMinutos() * 100.0) / (slaHoras * 60);

            if (r.getVuelos() != null) {
                for (PlanVuelo v : r.getVuelos()) {
                    String clave = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
                    capVuelo.put(clave, v.getCapacidad());
                }
            }
        }

        long totalMaletasEnVuelos = 0;
        long totalCapacidadDeVuelosUsados = 0;
        for (String clave : ocupacionVuelosGlobal.keySet()) {
            totalMaletasEnVuelos += ocupacionVuelosGlobal.get(clave);
            totalCapacidadDeVuelosUsados += capVuelo.getOrDefault(clave, 0);
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
        double FO = (promConsumoSLA*4 + promVuelos*3 + promAlmacenes*3)/10;

        // Guardamos en la variable global en lugar de hacer emitter.send()
        this.ultimoResumen = ResumenFinalDTO.builder()
                .totalEnviosPlanificados(rutasHistorico.size())
                .totalMaletasPlanificadas(totalMaletasPlanificadas)
                .consumoPromedioSLA(promConsumoSLA)
                .ocupacionPromedioVuelos(promVuelos)
                .ocupacionPromedioAlmacenes(promAlmacenes)
                .funcionObjetivo(FO)
                .tiempoEjecucionSegundos(tiempoEjecucionRealMs / 1000.0)
                .estadoFinal(colapso != null && colapso.hayColapso() ? "COLAPSO DETECTADO" : "SIMULACION EXITOSA")
                .build();

    }

    // =========================================================================
    // HELPERS COPIADOS LITERALMENTE DE TU MAIN
    // =========================================================================

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

    static class EventoForense {
        long minuto; int delta; String idEnvio;
        EventoForense(long m, int d, String id) { this.minuto = m; this.delta = d; this.idEnvio = id; }
    }

    private static ResultadoColapso detectarColapso(Individuo res, DataService ds, LocalDateTime reloj, Map<String, List<long[]>> timelineGlobal, Map<String, Integer> ocupacionVuelosGlobal) {
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
            } 
            else if (r.getEstado() == EstadoRuta.SIN_RUTA) {
                rc.porRutaNoEncontrada = true; 
                rc.idEnvioCausante = env.getIdEnvio();
                rc.rutaCausante = env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino();
                rc.maletasCausantes = env.getCantidadMaletas();
                rc.detalle = "No se encontró una solución que respete los límites de tiempo y capacidad.";
                return rc;
            }
            else if (r.getEstado() == EstadoRuta.PLANIFICADA) {
                Aeropuerto o = ds.getMapaAeropuertos().get(env.getAeropuertoOrigen());
                Aeropuerto d = ds.getMapaAeropuertos().get(env.getAeropuertoDestino());
                long sla = (o != null && d != null && o.getContinente().equals(d.getContinente())) ? 24 : 48;
                
                if (r.getTiempoTotalMinutos() > sla * 60) {
                    rc.porSLA = true; rc.idEnvioCausante = env.getIdEnvio();
                    rc.rutaCausante = env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino();
                    rc.maletasCausantes = env.getCantidadMaletas();
                    rc.detalle = String.format("El tiempo calculado (%dh %dm) excede el SLA de %dh", r.getTiempoTotalMinutos()/60, r.getTiempoTotalMinutos()%60, sla);
                    return rc;
                }
                if (r.getVuelos() != null) {
                    for (PlanVuelo v : r.getVuelos()) {
                        String key = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
                        int historico = ocupacionVuelosGlobal.getOrDefault(key, 0);
                        int nuevaOc = ocupacionVuelosActuales.getOrDefault(key, 0) + env.getCantidadMaletas();
                        ocupacionVuelosActuales.put(key, nuevaOc);
                        if (historico + nuevaOc > v.getCapacidad()) {
                            rc.porEspacioVuelo = true; rc.idEnvioCausante = env.getIdEnvio();
                            rc.rutaCausante = env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino();
                            rc.maletasCausantes = env.getCantidadMaletas();
                            rc.detalle = String.format("El Vuelo %s->%s ha superado su capacidad física. Requerido: %d | Máximo: %d", v.getOrigen(), v.getDestino(), historico + nuevaOc, v.getCapacidad());
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
                
                cursor = cursor.plusMinutes(10);
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