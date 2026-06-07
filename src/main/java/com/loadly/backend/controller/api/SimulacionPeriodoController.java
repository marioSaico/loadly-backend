package com.loadly.backend.controller.api;

import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.dto.SimulacionEventDTO;
import com.loadly.backend.dto.SimulacionEventDTO.*;
import com.loadly.backend.model.*;
import com.loadly.backend.planificador.Planificador;
import com.loadly.backend.service.DataService;
import com.loadly.backend.service.database.AeropuertoService;
import com.loadly.backend.service.database.PlanVueloService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.Duration;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/simulacion")
@CrossOrigin(origins = "*", allowedHeaders = "*", methods = {RequestMethod.GET, RequestMethod.POST, RequestMethod.OPTIONS})
public class SimulacionPeriodoController {

    private static final DateTimeFormatter FMT_INPUT = DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");
    private static final DateTimeFormatter FMT_LOG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FMT_DISPLAY = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int DURACION_BLOQUE_DIA_A_DIA_MINUTOS = 5;

    private volatile boolean simulacionDetenida = false;
    private volatile boolean simulacionPausada  = false;
    
    private volatile SseEmitter emitterActivo = null;
    private final Map<String, List<long[]>> timelineAlmacenesGlobal = new HashMap<>();
    private final Map<String, Integer> ocupacionVuelosGlobal = new HashMap<>();

    private final Planificador planificador;
    private final DataService dataService;
    private final AeropuertoService aeropuertoService;
    private final PlanVueloService planVueloService;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    
    private ResumenFinalDTO ultimoResumen = null;

    public SimulacionPeriodoController(Planificador planificador, DataService dataService, 
                                       AeropuertoService aeropuertoService, PlanVueloService planVueloService) {
        this.planificador = planificador;
        this.dataService = dataService;
        this.aeropuertoService = aeropuertoService;
        this.planVueloService = planVueloService;
    }

    @GetMapping(value = "/periodo/iniciar", produces = MediaType.TEXT_EVENT_STREAM_VALUE)    
    public SseEmitter iniciarSimulacion(
            @RequestParam String inicioStr,
            @RequestParam String finStr,
            @RequestParam(defaultValue = "60") int taSegundos,
            @RequestParam(defaultValue = "2") int sa,
            @RequestParam(defaultValue = "120") int k,
            @RequestParam(defaultValue = "10") int tamano) {

        simulacionDetenida = false;
        simulacionPausada  = false;

        SseEmitter emitter = new SseEmitter(0L);
        this.emitterActivo = emitter;

        executor.execute(() -> {
            try {
                ejecutarEscenario(emitter, inicioStr, finStr, taSegundos, sa, k, tamano);
            } catch (Exception e) {
                emitter.completeWithError(e);
            }
        });

        return emitter;
    }
    @PostMapping("/periodo/pausar")
    public ResponseEntity<String> pausarSimulacion() {
        simulacionPausada = true;
        return ResponseEntity.ok("Simulación pausada");
    }

    @PostMapping("/periodo/reanudar")
    public ResponseEntity<String> reanudarSimulacion() {
        simulacionPausada = false;
        return ResponseEntity.ok("Simulación reanudada");
    }

    @PostMapping("/periodo/detener")
    public ResponseEntity<String> detenerSimulacion() {
        simulacionDetenida = true;
        simulacionPausada  = false; // por si estaba pausada
        dataService.resetEstado(); // Limpiar estado para evitar que siga procesando datos
        this.emitterActivo = null; // Evitar que se sigan enviando eventos
        return ResponseEntity.ok("Simulación detenida");
    }

    @GetMapping(value = "/periodo/resumen", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResumenFinalDTO> obtenerResumenFinal() {
        if (ultimoResumen == null) {
            return ResponseEntity.noContent().build(); 
        }
        return ResponseEntity.ok(ultimoResumen);
    }

    @PostMapping("/vuelos/cancelar")
    public ResponseEntity<String> cancelarVuelo(
            @RequestParam String claveVuelo,
            @RequestParam String relojSimuladoActual) {
        try {
            LocalDateTime reloj = LocalDateTime.parse(relojSimuladoActual, FMT_INPUT);

            List<Ruta> rutasAntes = new ArrayList<>(dataService.getRutasPlanificadasHistorico());

            DataService.ResultadoCancelacion resultado = dataService.cancelarVuelo(claveVuelo, reloj);

            if (!resultado.enviosAfectados.isEmpty()) {
                Set<String> clavesAfectadas = resultado.enviosAfectados.stream()
                    .map(e -> e.getIdEnvio() + "|" + e.getIdCliente() + "|"
                            + e.getAeropuertoOrigen() + "|" + e.getAeropuertoDestino())
                    .collect(Collectors.toSet());

                limpiarTimelineDeRutasCanceladas(rutasAntes, clavesAfectadas, resultado.indicesAfectados);

                if (emitterActivo != null) {
                    List<SimulacionEventDTO.EnvioAfectadoDTO> afectadosDTO = resultado.enviosAfectados.stream()
                        .map(e -> SimulacionEventDTO.EnvioAfectadoDTO.builder()
                            .idEnvio(e.getIdEnvio())
                            .idCliente(e.getIdCliente())
                            .origen(e.getAeropuertoOrigen())
                            .destino(e.getAeropuertoDestino())
                            .build())
                        .collect(Collectors.toList());

                    emitterActivo.send(SimulacionEventDTO.builder()
                        .tipo("CANCELACION")
                        .relojSimulado(relojSimuladoActual)
                        .vueloCancelado(claveVuelo)
                        .enviosAfectadosCancelacion(afectadosDTO)
                        .build());
                }
            }

            return ResponseEntity.ok(String.format(
                "Vuelo %s cancelado. Envíos afectados: %d", claveVuelo, resultado.enviosAfectados.size()));

        } catch (Exception e) {
            return ResponseEntity.internalServerError()
                .body("Error al cancelar vuelo: " + e.getMessage());
        }
    }

    private void limpiarTimelineDeRutasCanceladas(
            List<Ruta> rutasAntes, 
            Set<String> clavesAfectadas,
            Map<String, Integer> indicesAfectados) { // clave → indiceAfectado
        
        for (Ruta ruta : rutasAntes) {
            Envio envio = ruta.getEnvio();
            String claveEnvio = envio.getIdEnvio() + "|" + envio.getIdCliente() + "|"
                            + envio.getAeropuertoOrigen() + "|" + envio.getAeropuertoDestino();
            if (!clavesAfectadas.contains(claveEnvio)) continue;
            if (ruta.getEstado() != EstadoRuta.PLANIFICADA || ruta.getVuelos() == null) continue;

            int indiceAfectado = indicesAfectados.getOrDefault(claveEnvio, 0);

            int gmtO = dataService.getMapaAeropuertos().get(envio.getAeropuertoOrigen()).getGmt();
            LocalDateTime regGMT = LocalDateTime.of(
                LocalDate.parse(envio.getFechaRegistro(), FMT_FECHA),
                LocalTime.of(envio.getHoraRegistro(), envio.getMinutoRegistro())
            ).minusHours(gmtO);

            // El evento de llegada al almacén origen (+maletas) NUNCA se revierte
            // porque el envío ya estaba físicamente ahí desde que se registró

            LocalDateTime cursor = regGMT;
            for (int i = 0; i < ruta.getVuelos().size(); i++) {
                PlanVuelo v = ruta.getVuelos().get(i);
                String claveVuelo = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
                int gmtVO = dataService.getMapaAeropuertos().get(v.getOrigen()).getGmt();
                int gmtVD = dataService.getMapaAeropuertos().get(v.getDestino()).getGmt();

                int minSGMT = (convertirAMinutos(v.getHoraSalida()) - gmtVO * 60 + 1440) % 1440;
                LocalDateTime despegue = cursor.with(minutosALocalTime(minSGMT));
                if (despegue.isBefore(cursor)) despegue = despegue.plusDays(1);

                int duracion = (convertirAMinutos(v.getHoraLlegada()) - gmtVD * 60)
                            - (convertirAMinutos(v.getHoraSalida()) - gmtVO * 60);
                if (duracion < 0) duracion += 1440;
                LocalDateTime llegada = despegue.plusMinutes(duracion);

                if (i >= indiceAfectado) {
                    boolean esTramoEnAire = (i == indiceAfectado && i > 0);

                    if (esTramoEnAire) {
                        // Maletas VAN A LLEGAR a v.getOrigen() porque el tramo anterior
                        // ya está en el aire → NO quitamos [+maletas en v.getOrigen()]
                        // SÍ quitamos el despegue porque ese vuelo fue cancelado
                        quitarEventoTimeline(timelineAlmacenesGlobal,
                            v.getOrigen(), despegue, -envio.getCantidadMaletas());
                        // SÍ quitamos la llegada al destino (nunca va a llegar)
                        quitarEventoTimeline(timelineAlmacenesGlobal,
                            v.getDestino(), llegada, +envio.getCantidadMaletas());
                        ocupacionVuelosGlobal.merge(claveVuelo, -envio.getCantidadMaletas(), Integer::sum);
                    } else {
                        // Tramo completamente futuro → quitar todo
                        quitarEventoTimeline(timelineAlmacenesGlobal,
                            v.getOrigen(), despegue, -envio.getCantidadMaletas());
                        quitarEventoTimeline(timelineAlmacenesGlobal,
                            v.getDestino(), llegada, +envio.getCantidadMaletas());
                        ocupacionVuelosGlobal.merge(claveVuelo, -envio.getCantidadMaletas(), Integer::sum);
                    }
                }

                cursor = llegada;
            }

            // Recojo en destino final siempre se quita
            quitarEventoTimeline(timelineAlmacenesGlobal,
                envio.getAeropuertoDestino(), cursor.plusMinutes(10), -envio.getCantidadMaletas());
        }
    }

    private void quitarEventoTimeline(Map<String, List<long[]>> timeline,
                                        String aero, LocalDateTime t, int delta) {
        List<long[]> eventos = timeline.get(aero);
        if (eventos == null) return;
        long minuto = t.toEpochSecond(ZoneOffset.UTC) / 60;
        eventos.removeIf(ev -> ev[0] == minuto && (int) ev[1] == delta);
    }

    private void ejecutarEscenario(SseEmitter emitter, String inicioStr, String finStr, int taSegundos, int sa, int k, int tamano) throws Exception {
        if (k == 0) {
            ejecutarEscenarioDiaADia(emitter, inicioStr, finStr, taSegundos, tamano);
            return;
        }
        
        // Cargar aeropuertos y planes de vuelo desde BD
        //dataService.inicializar();

        LocalDateTime relojSimulado      = LocalDateTime.parse(inicioStr, FMT_INPUT);
        LocalDateTime finSimulacion      = LocalDateTime.parse(finStr,    FMT_INPUT);

        int  sc             = sa * k;
        LocalDateTime limiteLecturaDatos = relojSimulado.plusMinutes(sc); // -> NUEVO CAMBIO
        long TA_MAX_MS = taSegundos * 1000L; //tope estandarizado (Ta real del GA + margen)
        long tiempoLimiteMs = 25_000L;  // Ta real del GA (lo que realmente tarda en planificar)

        long saMs = (long) sa * 60 * 1000L;

        boolean colapsoDetectado = false;
        ResultadoColapso colapsoFinal = null;
        this.timelineAlmacenesGlobal.clear();
        this.ocupacionVuelosGlobal.clear();

        long inicioEscenarioMs = System.currentTimeMillis();

        while ((limiteLecturaDatos.isBefore(finSimulacion) || limiteLecturaDatos.isEqual(finSimulacion)) && !colapsoDetectado) {

            // ── Chequeo detención ──────────────────────────────────────────
            if (simulacionDetenida) {
                emitter.send(SimulacionEventDTO.builder()
                        .tipo("DETENIDA")
                        .relojSimulado(relojSimulado.format(FMT_LOG))
                        .build());
                emitter.complete();
                return;
            }

            // ── Chequeo pausa ──────────────────────────────────────────────
            while (simulacionPausada && !simulacionDetenida) {
                Thread.sleep(500);
            }
            
            // Si mientras pausaba se pidió detener
            if (simulacionDetenida) {
                emitter.send(SimulacionEventDTO.builder()
                        .tipo("DETENIDA")
                        .relojSimulado(relojSimulado.format(FMT_LOG))
                        .build());
                emitter.complete();
                return;
            }

            long iteracionInicioMs = System.currentTimeMillis();

            String limiteLecturaStr = limiteLecturaDatos.format(FMT_INPUT);

            // =====================================================================
            // CALCULO EXACTO: El inicio de la ventana actual de planificación (S_c)
            // =====================================================================
            LocalDateTime fechaHoraActualReal = limiteLecturaDatos.minusMinutes(sc);
            String fechaHoraActualStr = fechaHoraActualReal.format(FMT_INPUT);
            
            System.out.printf("    [DEBUG] Llamando a planificar con tamano=%d, tiempoLimiteMs=%d%n", tamano, tiempoLimiteMs);
            Individuo resultado = planificador.planificar(inicioStr, fechaHoraActualStr, limiteLecturaStr, tamano, tiempoLimiteMs);
            System.out.printf("    [DEBUG] Procesando eventos del reloj: %s%n", limiteLecturaStr);
            dataService.procesarEventosDelReloj(limiteLecturaStr);
            System.out.printf("    [DEBUG] Planificador retornó: %s%n", (resultado != null ? "INDIVIDUO" : "NULL"));
            
            if (resultado != null) {
                ResultadoColapso colapso = detectarColapso(resultado, dataService, relojSimulado, timelineAlmacenesGlobal, ocupacionVuelosGlobal);
                
                if (colapso.hayColapso()) {
                    colapsoDetectado = true;
                    colapsoFinal = colapso;
                    enviarColapso(emitter, colapso, relojSimulado, limiteLecturaDatos);
                } else {
                    // ── Esperar hasta TA_MAX antes de enviar al frontend ───────────
                    long tiempoUsadoMs = System.currentTimeMillis() - iteracionInicioMs;
                    System.out.printf("    [Tiempo real iteracion: %.1fs | TA_MAX: %.1fs]%n",
                            tiempoUsadoMs / 1000.0, TA_MAX_MS / 1000.0);
                    if (tiempoUsadoMs < TA_MAX_MS) {
                        long tiempoRestanteMs = TA_MAX_MS - tiempoUsadoMs;
                        long finEspera = System.currentTimeMillis() + tiempoRestanteMs;
                        
                        while (System.currentTimeMillis() < finEspera) {
                            if (simulacionDetenida) {
                                emitter.send(SimulacionEventDTO.builder()
                                        .tipo("DETENIDA")
                                        .relojSimulado(relojSimulado.format(FMT_LOG))
                                        .build());
                                emitter.complete();
                                return;
                            }
                            if (simulacionPausada) {
                                // Guarda cuánto falta y espera reanudación
                                long tiempoFaltante = finEspera - System.currentTimeMillis();
                                while (simulacionPausada && !simulacionDetenida) {
                                    Thread.sleep(500);
                                }
                                if (simulacionDetenida) {
                                    emitter.send(SimulacionEventDTO.builder()
                                            .tipo("DETENIDA")
                                            .relojSimulado(relojSimulado.format(FMT_LOG))
                                            .build());
                                    emitter.complete();
                                    return;
                                }
                                // Al reanudar, recalcula el fin con el tiempo que faltaba
                                finEspera = System.currentTimeMillis() + tiempoFaltante;
                            }
                            Thread.sleep(500);
                        }
                    }
                    // ── Recién aquí se envía el bloque al frontend ─────────────────
                    System.out.printf("    Bloque planificado enviado a FRONTEND%n");
                    enviarIteracion(emitter, resultado, dataService, timelineAlmacenesGlobal, ocupacionVuelosGlobal, relojSimulado, limiteLecturaDatos);
                }
            }

            if (!colapsoDetectado) {
                limiteLecturaDatos = limiteLecturaDatos.plusMinutes(sc);
                relojSimulado      = relojSimulado.plusMinutes(sa);
                // ── Pausa restante para completar Sa (siempre fija = Sa - TA_MAX) ─
                long tiempoEsperaMs = saMs - TA_MAX_MS;
                if (resultado == null) tiempoEsperaMs = saMs; // Si no se planificó nada, esperar todo el Sa para simular que avanzó el reloj sin eventos 
                if (tiempoEsperaMs > 500) {
                    System.out.printf("    [Pausa: %.1fs para completar Sa=%dmin en tiempo real]%n",
                            tiempoEsperaMs / 1000.0, sa);
                    try {
                        long finSa = System.currentTimeMillis() + tiempoEsperaMs;
                        while (System.currentTimeMillis() < finSa) {
                            if (simulacionDetenida) {
                                emitter.send(SimulacionEventDTO.builder()
                                        .tipo("DETENIDA")
                                        .relojSimulado(relojSimulado.format(FMT_LOG))
                                        .build());
                                emitter.complete();
                                return;
                            }
                            if (simulacionPausada) {
                                long tiempoFaltante = finSa - System.currentTimeMillis();
                                while (simulacionPausada && !simulacionDetenida) {
                                    Thread.sleep(500);
                                }
                                if (simulacionDetenida) {
                                    emitter.send(SimulacionEventDTO.builder()
                                            .tipo("DETENIDA")
                                            .relojSimulado(relojSimulado.format(FMT_LOG))
                                            .build());
                                    emitter.complete();
                                    return;
                                }
                                // Recalcula con el tiempo que faltaba
                                finSa = System.currentTimeMillis() + tiempoFaltante;
                            }
                            Thread.sleep(500);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        emitter.completeWithError(e);
                        return;
                    }
                }
            }
        }

        long tiempoEjecucionRealMs = System.currentTimeMillis() - inicioEscenarioMs;
        generarYGuardarResumen(dataService, colapsoFinal, limiteLecturaDatos, tiempoEjecucionRealMs, timelineAlmacenesGlobal, LocalDateTime.parse(inicioStr, FMT_INPUT), ocupacionVuelosGlobal);
        
        dataService.resetEstado();
        this.timelineAlmacenesGlobal.clear();
        this.ocupacionVuelosGlobal.clear();
        this.emitterActivo = null;
        emitter.complete(); 
    }

    private void ejecutarEscenarioDiaADia(SseEmitter emitter,
                                           String inicioStr,
                                           String finStr,
                                           int taSegundos,
                                           int tamano) throws Exception {
        dataService.inicializar();
        dataService.setUsarEnviosDesdeBD(true);
        this.timelineAlmacenesGlobal.clear();
        this.ocupacionVuelosGlobal.clear();

        LocalDateTime inicioSolicitado = LocalDateTime.parse(inicioStr, FMT_INPUT);
        LocalDateTime ahoraSinSegundos = LocalDateTime.now().truncatedTo(ChronoUnit.MINUTES);
        LocalDateTime inicioBloque = inicioSolicitado.isAfter(ahoraSinSegundos)
                ? inicioSolicitado
                : ahoraSinSegundos;
        LocalDateTime finOperacion = LocalDateTime.parse(finStr, FMT_INPUT);
        long taMaxMs = taSegundos * 1000L;
        long tiempoLimiteMs = 25_000L;

        while (inicioBloque.isBefore(finOperacion)) {
            LocalDateTime finBloque = inicioBloque.plusMinutes(DURACION_BLOQUE_DIA_A_DIA_MINUTOS);
            if (finBloque.isAfter(finOperacion)) {
                finBloque = finOperacion;
            }

            if (!esperarHasta(emitter, finBloque)) {
                return;
            }

            long inicioPlanificacionMs = System.currentTimeMillis();
            String inicioBloqueStr = inicioBloque.format(FMT_INPUT);
            String finBloqueStr = finBloque.format(FMT_INPUT);

            dataService.procesarEventosDelReloj(finBloqueStr);
            Individuo resultado = planificador.planificar(
                    inicioStr,
                    inicioBloqueStr,
                    finBloqueStr,
                    tamano,
                    tiempoLimiteMs
            );

            if (resultado != null) {
                ResultadoColapso colapso = detectarColapso(
                        resultado,
                        dataService,
                        finBloque,
                        timelineAlmacenesGlobal,
                        ocupacionVuelosGlobal
                );

                if (colapso.hayColapso()) {
                    enviarColapso(emitter, colapso, finBloque, finBloque);
                    dataService.resetEstado();
                    emitter.complete();
                    return;
                }

                long tiempoUsadoMs = System.currentTimeMillis() - inicioPlanificacionMs;
                if (tiempoUsadoMs < taMaxMs && !esperarMilisegundos(emitter, taMaxMs - tiempoUsadoMs)) {
                    return;
                }

                enviarIteracion(
                        emitter,
                        resultado,
                        dataService,
                        timelineAlmacenesGlobal,
                        ocupacionVuelosGlobal,
                        finBloque,
                        finBloque
                );
            }

            inicioBloque = finBloque;
        }

        dataService.resetEstado();
        this.timelineAlmacenesGlobal.clear();
        this.ocupacionVuelosGlobal.clear();
        this.emitterActivo = null;
        emitter.complete();
    }

    private boolean esperarHasta(SseEmitter emitter, LocalDateTime objetivo) throws Exception {
        while (LocalDateTime.now().isBefore(objetivo)) {
            long restanteMs = Duration.between(LocalDateTime.now(), objetivo).toMillis();
            if (!esperarMilisegundos(emitter, Math.min(restanteMs, 500L))) {
                return false;
            }
        }
        return true;
    }

    private boolean esperarMilisegundos(SseEmitter emitter, long milisegundos) throws Exception {
        long finEspera = System.currentTimeMillis() + Math.max(0L, milisegundos);

        while (System.currentTimeMillis() < finEspera) {
            if (simulacionDetenida) {
                dataService.setUsarEnviosDesdeBD(false);
                emitter.send(SimulacionEventDTO.builder()
                        .tipo("DETENIDA")
                        .relojSimulado(LocalDateTime.now().format(FMT_LOG))
                        .build());
                emitter.complete();
                return false;
            }

            while (simulacionPausada && !simulacionDetenida) {
                Thread.sleep(500);
            }

            long restante = finEspera - System.currentTimeMillis();
            if (restante > 0) {
                Thread.sleep(Math.min(restante, 500L));
            }
        }
        return true;
    }

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
        

        for (Ruta r : rutasOrdenadas) {
            Envio envio = r.getEnvio();

            LocalDateTime cursor;

            if (envio.getHoraDisponibleReplanificacion() != null) {
                // Replanificación desde aeropuerto intermedio con tramo en el aire
                // Las maletas llegan solas a ese aeropuerto → NO agregar evento en origen
                // El cursor arranca desde cuando llegan al aeropuerto intermedio
                cursor = envio.getHoraDisponibleReplanificacion();
            } else {
                // Envío normal o replanificación desde origen original
                int gmtO = dataService.getMapaAeropuertos().get(envio.getAeropuertoOrigen()).getGmt();
                LocalDateTime regGMT = LocalDateTime.of(
                    LocalDate.parse(envio.getFechaRegistro(), FMT_FECHA),
                    LocalTime.of(envio.getHoraRegistro(), envio.getMinutoRegistro())
                ).minusHours(gmtO);
                // Solo agregar evento en almacén origen si las maletas están físicamente ahí
                agregarEventoTimeline(timelineAlmacenesGlobal, envio.getAeropuertoOrigen(), regGMT, +envio.getCantidadMaletas());
                cursor = regGMT;
            }

            for (PlanVuelo v : r.getVuelos()) {
                String clave = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
                ocupacionVuelosGlobal.merge(clave, envio.getCantidadMaletas(), Integer::sum);

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
            
            cursor = cursor.plusMinutes(10);
            agregarEventoTimeline(timelineAlmacenesGlobal, envio.getAeropuertoDestino(), cursor, -envio.getCantidadMaletas());
        }

        List<RutaPlanificadaDTO> rutasDTO = new ArrayList<>();
        
        for (Ruta r : rutasOrdenadas) {
            Envio envio = r.getEnvio();
            LocalDateTime cursor;
            LocalDateTime regGMT; // para el DTO de la ruta

            int gmtO = dataService.getMapaAeropuertos().get(envio.getAeropuertoOrigen()).getGmt();
            regGMT = LocalDateTime.of(
                LocalDate.parse(envio.getFechaRegistro(), FMT_FECHA),
                LocalTime.of(envio.getHoraRegistro(), envio.getMinutoRegistro())
            ).minusHours(gmtO);

            if (envio.getHoraDisponibleReplanificacion() != null) {
                cursor = envio.getHoraDisponibleReplanificacion();
            } else {
                cursor = regGMT;
            }
            List<VueloPlanificadoDTO> tramosDTO = new ArrayList<>();
            int paso = 1;

            for (PlanVuelo v : r.getVuelos()) {
                String clave = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();

                Aeropuerto ao = dataService.getMapaAeropuertos().get(v.getOrigen());
                Aeropuerto ad = dataService.getMapaAeropuertos().get(v.getDestino());

                int minSGMT = (convertirAMinutos(v.getHoraSalida()) - ao.getGmt() * 60 + 1440) % 1440;
                LocalDateTime despegue = cursor.with(minutosALocalTime(minSGMT));
                if (despegue.isBefore(cursor)) despegue = despegue.plusDays(1);

                int duracion = (convertirAMinutos(v.getHoraLlegada()) - ad.getGmt() * 60) - (convertirAMinutos(v.getHoraSalida()) - ao.getGmt() * 60);
                if (duracion < 0) duracion += 1440;
                LocalDateTime llegada = despegue.plusMinutes(duracion);

                // Como la línea de tiempo se llenó en el Bucle 1, ahora esto reflejará TODAS las maletas
                int ocupadoAlmOrig = getOcupacionAlmacen(timelineAlmacenesGlobal, v.getOrigen(), despegue);
                int ocupadoAlmDest = getOcupacionAlmacen(timelineAlmacenesGlobal, v.getDestino(), llegada);

                tramosDTO.add(VueloPlanificadoDTO.builder()
                        .orden(paso++)
                        .origen(v.getOrigen())
                        .destino(v.getDestino())
                        .sale(despegue.toLocalTime().toString())
                        .llega(llegada.toLocalTime().toString())
                        .maletasVuelo(ocupacionVuelosGlobal.getOrDefault(clave, 0))
                        .capacidadVuelo(v.getCapacidad())
                        .ocupacionAlmacenOrigen(ocupadoAlmOrig)
                        .capacidadAlmacenOrigen(ao.getCapacidad())
                        .ocupacionAlmacenDestino(ocupadoAlmDest)
                        .capacidadAlmacenDestino(ad.getCapacidad())
                        .build());
                
                cursor = llegada;
            }
            
            Aeropuerto origen = dataService.getMapaAeropuertos().get(envio.getAeropuertoOrigen());
            Aeropuerto destino = dataService.getMapaAeropuertos().get(envio.getAeropuertoDestino());
            long horasTotales = r.getTiempoTotalMinutos() / 60;
            long minutosRestantes = r.getTiempoTotalMinutos() % 60;
            long slaHoras = (origen != null && destino != null && origen.getContinente().equals(destino.getContinente())) ? 24 : 48;
            LocalDateTime recojoGMT = regGMT.plusMinutes(r.getTiempoTotalMinutos());
            int ocupadoRegistro = getOcupacionAlmacen(timelineAlmacenesGlobal, envio.getAeropuertoOrigen(), regGMT);
            int ocupadoRecojo   = getOcupacionAlmacen(timelineAlmacenesGlobal, envio.getAeropuertoDestino(), recojoGMT);

            rutasDTO.add(RutaPlanificadaDTO.builder()
                    .idEnvio(envio.getIdEnvio())
                    .idCliente(envio.getIdCliente())
                    .origen(envio.getAeropuertoOrigen())
                    .destino(envio.getAeropuertoDestino())
                    .maletas(envio.getCantidadMaletas())
                    .fechaRegistro(regGMT.format(FMT_DISPLAY))
                    .fechaRecojo(recojoGMT.format(FMT_DISPLAY))
                    .ocupacionAlmacenRegistro(ocupadoRegistro)
                    .capacidadAlmacenRegistro(origen != null ? origen.getCapacidad() : 0)
                    .ocupacionAlmacenRecojo(ocupadoRecojo)
                    .capacidadAlmacenRecojo(destino != null ? destino.getCapacidad() : 0)
                    .duracion(String.format("%02dh %02dm", horasTotales, minutosRestantes))
                    .sla(slaHoras + "h")
                    .esReplanificacion(envio.getAeropuertoReplanificacionDesde() != null)
                    .replanificadoDesde(envio.getAeropuertoReplanificacionDesde())
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
                LocalDateTime cursor;
                if (env.getHoraDisponibleReplanificacion() != null) {
                    cursor = env.getHoraDisponibleReplanificacion();
                } else {
                    Aeropuerto o = ds.getMapaAeropuertos().get(env.getAeropuertoOrigen());
                    cursor = LocalDateTime.of(
                        LocalDate.parse(env.getFechaRegistro(), FMT_FECHA),
                        LocalTime.of(env.getHoraRegistro(), env.getMinutoRegistro())
                    ).minusHours(o.getGmt());
                }
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
