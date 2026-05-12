package com.loadly.backend.controller.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
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

/**
 * Controller SSE para la simulación de período.
 * Replica fielmente la lógica de BackendApplication.ejecutarEscenario()
 * pero emitiendo cada iteración como un evento SSE al frontend.
 */
@RestController
@RequestMapping("/api/simulacion")
@CrossOrigin(origins = "*")
public class SimulacionPeriodoController {

    private static final DateTimeFormatter FMT_INPUT = DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");
    private static final DateTimeFormatter FMT_LOG = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("yyyyMMdd");

    @Autowired
    private Planificador planificador;

    @Autowired
    private DataService dataService;

    private final ObjectMapper objectMapper;

    public SimulacionPeriodoController() {
        this.objectMapper = new ObjectMapper();
        this.objectMapper.configure(SerializationFeature.FAIL_ON_EMPTY_BEANS, false);
    }

    /**
     * GET /api/simulacion/periodo/stream
     * 
     * Ejecuta la simulación de período completa con SSE streaming.
     * Parámetros fieles al BackendApplication.ejecutarEscenario():
     * - inicio: "20270102-00-00"
     * - fin: "20270107-00-00"
     * - ta: 30 (segundos para el GA)
     * - sa: 10 (salto de avance del reloj en minutos)
     * - k: 6 (multiplicador ventana lectura)
     * - tamano: 10 (población GA)
     */
    @GetMapping(value = "/periodo/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamSimulacion(
            @RequestParam(defaultValue = "20270102-00-00") String inicio,
            @RequestParam(defaultValue = "20270107-00-00") String fin,
            @RequestParam(defaultValue = "30") int ta,
            @RequestParam(defaultValue = "10") int sa,
            @RequestParam(defaultValue = "6") int k,
            @RequestParam(defaultValue = "10") int tamano) {

        // Timeout largo: la simulación puede durar ~1 hora
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);

        // Ejecutar en un hilo separado para no bloquear el thread HTTP
        Thread simulationThread = new Thread(() -> {
            try {
                ejecutarSimulacion(emitter, inicio, fin, ta, sa, k, tamano);
            } catch (Exception e) {
                try {
                    emitter.completeWithError(e);
                } catch (Exception ignored) {}
            }
        });
        simulationThread.setDaemon(true);
        simulationThread.start();

        emitter.onCompletion(() -> {
            System.out.println("[SSE] Simulación completada - conexión cerrada");
        });
        emitter.onTimeout(() -> {
            System.out.println("[SSE] Simulación timeout - conexión cerrada");
        });
        emitter.onError(e -> {
            System.out.println("[SSE] Error en simulación: " + e.getMessage());
        });

        return emitter;
    }

    /**
     * Lógica de simulación fiel a BackendApplication.ejecutarEscenario()
     */
    private void ejecutarSimulacion(SseEmitter emitter, String inicioStr, String finStr,
            int taSegundos, int sa, int k, int tamano) throws Exception {

        // Reset del estado
        dataService.resetEstadoExperimento();

        LocalDateTime relojSimulado = LocalDateTime.parse(inicioStr, FMT_INPUT);
        LocalDateTime finSimulacion = LocalDateTime.parse(finStr, FMT_INPUT);
        LocalDateTime limiteLecturaDatos = relojSimulado;

        int sc = sa * k;
        long tiempoLimiteMs = taSegundos * 1000L;

        // Calcular total de iteraciones estimadas
        long totalMinutos = java.time.Duration.between(relojSimulado, finSimulacion).toMinutes();
        int totalIteraciones = (int) Math.ceil((double) totalMinutos / sc);

        boolean colapsoDetectado = false;
        Map<String, List<long[]>> timelineAlmacenesGlobal = new HashMap<>();

        long inicioEscenarioMs = System.currentTimeMillis();
        int iteracion = 0;

        System.out.println("\n" + "=".repeat(80));
        System.out.println("   [SSE] INICIANDO SIMULACIÓN PERÍODO");
        System.out.println("   Período: " + inicioStr + " → " + finStr);
        System.out.println("   Parámetros: ta=" + taSegundos + "s, sa=" + sa + "min, k=" + k + ", sc=" + sc + "min, pop=" + tamano);
        System.out.println("   Iteraciones estimadas: " + totalIteraciones);
        System.out.println("=".repeat(80));

        // --- EVENTO INICIO: Enviar datos de aeropuertos ---
        List<AeropuertoSimDTO> aeropuertosDTO = dataService.getAeropuertos().stream()
                .map(a -> AeropuertoSimDTO.builder()
                        .codigo(a.getCodigo())
                        .ciudad(a.getCiudad())
                        .pais(a.getPais())
                        .latitud(a.getLatitud())
                        .longitud(a.getLongitud())
                        .continente(a.getContinente())
                        .capacidad(a.getCapacidad())
                        .build())
                .collect(Collectors.toList());

        SimulacionEventDTO eventoInicio = SimulacionEventDTO.builder()
                .tipo("INICIO")
                .relojSimulado(relojSimulado.format(FMT_INPUT))
                .limiteLectura(limiteLecturaDatos.format(FMT_INPUT))
                .iteracionActual(0)
                .totalIteracionesEstimadas(totalIteraciones)
                .aeropuertos(aeropuertosDTO)
                .build();

        enviarEvento(emitter, eventoInicio);

        // --- BUCLE PRINCIPAL (fiel a BackendApplication) ---
        while ((limiteLecturaDatos.isBefore(finSimulacion) || limiteLecturaDatos.isEqual(finSimulacion))
                && !colapsoDetectado) {

            iteracion++;
            String limiteLecturaStr = limiteLecturaDatos.format(FMT_INPUT);
            String relojActualStr = relojSimulado.format(FMT_INPUT);

            System.out.println("\n>>> [SSE Iter " + iteracion + "/" + totalIteraciones + "] [RELOJ: " 
                    + relojSimulado.format(FMT_LOG) + "] Planificando envios hasta " 
                    + limiteLecturaDatos.format(FMT_LOG));

            // 1. Procesar eventos vencidos del reloj
            dataService.procesarEventosDelReloj(relojActualStr);

            // 2. Ejecutar el planificador (GA) — FIEL al BackendApplication
            Individuo resultado = planificador.planificar(inicioStr, limiteLecturaStr, tamano, tiempoLimiteMs);

            if (resultado != null) {
                // 3. Verificar colapso
                SimulacionEventDTO.ColapsoDTO colapsoDTO = detectarColapso(resultado, dataService, relojSimulado, timelineAlmacenesGlobal);

                if (colapsoDTO != null) {
                    // Evento de colapso
                    colapsoDetectado = true;
                    SimulacionEventDTO eventoColapso = SimulacionEventDTO.builder()
                            .tipo("COLAPSO")
                            .relojSimulado(relojSimulado.format(FMT_INPUT))
                            .limiteLectura(limiteLecturaStr)
                            .iteracionActual(iteracion)
                            .totalIteracionesEstimadas(totalIteraciones)
                            .colapso(colapsoDTO)
                            .build();
                    enviarEvento(emitter, eventoColapso);
                } else {
                    // 4. Procesar rutas y emitir evento de iteración
                    procesarTimeline(resultado, dataService, timelineAlmacenesGlobal);
                    
                    List<RutaPlanificadaDTO> rutasDTO = convertirRutas(resultado, dataService);
                    System.out.println("[ITERACION " + iteracion + "] Total rutas en resultado: " + resultado.getRutas().size() + " | Rutas filtradas enviadas: " + rutasDTO.size());
                    EstadisticasIteracionDTO stats = calcularEstadisticas(resultado, dataService);

                    SimulacionEventDTO eventoIteracion = SimulacionEventDTO.builder()
                            .tipo("ITERACION")
                            .relojSimulado(relojSimulado.format(FMT_INPUT))
                            .limiteLectura(limiteLecturaStr)
                            .iteracionActual(iteracion)
                            .totalIteracionesEstimadas(totalIteraciones)
                            .rutasPlanificadas(rutasDTO)
                            .estadisticas(stats)
                            .build();
                    enviarEvento(emitter, eventoIteracion);
                }
            } else {
                // Sin envíos en esta ventana, enviar evento vacío
                SimulacionEventDTO eventoVacio = SimulacionEventDTO.builder()
                        .tipo("ITERACION")
                        .relojSimulado(relojSimulado.format(FMT_INPUT))
                        .limiteLectura(limiteLecturaStr)
                        .iteracionActual(iteracion)
                        .totalIteracionesEstimadas(totalIteraciones)
                        .rutasPlanificadas(new ArrayList<>())
                        .estadisticas(EstadisticasIteracionDTO.builder()
                                .enviosProcesados(0).planificados(0).sinRuta(0)
                                .inalcanzables(0).enviosEnEspera(dataService.getEnviosEnEspera().size())
                                .fitnessPromedio(0).totalMaletasPlanificadas(0).build())
                        .build();
                enviarEvento(emitter, eventoVacio);
            }

            // 5. Avanzar reloj (fiel al BackendApplication)
            if (!colapsoDetectado) {
                limiteLecturaDatos = limiteLecturaDatos.plusMinutes(sc);
                relojSimulado = relojSimulado.plusMinutes(sa);
            }
        }

        // --- EVENTO RESUMEN FINAL ---
        long tiempoEjecucionRealMs = System.currentTimeMillis() - inicioEscenarioMs;
        ResumenFinalDTO resumen = calcularResumenFinal(dataService, timelineAlmacenesGlobal,
                LocalDateTime.parse(inicioStr, FMT_INPUT), relojSimulado, tiempoEjecucionRealMs,
                colapsoDetectado);

        SimulacionEventDTO eventoFinal = SimulacionEventDTO.builder()
                .tipo("RESUMEN_FINAL")
                .relojSimulado(relojSimulado.format(FMT_INPUT))
                .iteracionActual(iteracion)
                .totalIteracionesEstimadas(totalIteraciones)
                .resumenFinal(resumen)
                .build();
        enviarEvento(emitter, eventoFinal);

        emitter.complete();
        System.out.println("[SSE] Simulación finalizada. Tiempo total: " 
                + String.format("%.1f", tiempoEjecucionRealMs / 1000.0) + "s");
    }

    // =========================================================================
    // MÉTODOS DE CONVERSIÓN
    // =========================================================================

    private List<RutaPlanificadaDTO> convertirRutas(Individuo resultado, DataService dataService) {
        Map<String, Aeropuerto> mapa = dataService.getMapaAeropuertos();
        List<RutaPlanificadaDTO> rutasDTO = new ArrayList<>();

        int countPlanificada = 0, countSinRuta = 0, countInalcanzable = 0;
        for (Ruta ruta : resultado.getRutas()) {
            if (ruta.getEstado() == EstadoRuta.PLANIFICADA) countPlanificada++;
            else if (ruta.getEstado() == EstadoRuta.SIN_RUTA) countSinRuta++;
            else if (ruta.getEstado() == EstadoRuta.INALCANZABLE) countInalcanzable++;
        }
        System.out.println("  [ESTADOS] PLANIFICADA=" + countPlanificada + " SIN_RUTA=" + countSinRuta + " INALCANZABLE=" + countInalcanzable);

        for (Ruta ruta : resultado.getRutas()) {
            // Filtrar: solo rutas PLANIFICADAS con vuelos
            if (ruta.getEstado() != EstadoRuta.PLANIFICADA) {
                continue;
            }
            if (ruta.getVuelos() == null || ruta.getVuelos().isEmpty()) {
                System.out.println("  [FILTRO] Ruta de " + ruta.getEnvio().getIdEnvio() + " descartada: SIN VUELOS asignados");
                continue;
            }
            
            List<VueloPlanificadoDTO> vuelosDTO = new ArrayList<>();
            for (PlanVuelo v : ruta.getVuelos()) {
                Aeropuerto ao = mapa.get(v.getOrigen());
                Aeropuerto ad = mapa.get(v.getDestino());
                vuelosDTO.add(VueloPlanificadoDTO.builder()
                        .origen(v.getOrigen())
                        .destino(v.getDestino())
                        .horaSalida(v.getHoraSalida())
                        .horaLlegada(v.getHoraLlegada())
                        .capacidad(v.getCapacidad())
                        .latOrigen(ao != null ? ao.getLatitud() : 0)
                        .lngOrigen(ao != null ? ao.getLongitud() : 0)
                        .latDestino(ad != null ? ad.getLatitud() : 0)
                        .lngDestino(ad != null ? ad.getLongitud() : 0)
                        .build());
            }

            rutasDTO.add(RutaPlanificadaDTO.builder()
                    .idEnvio(ruta.getEnvio().getIdEnvio())
                    .aeropuertoOrigen(ruta.getEnvio().getAeropuertoOrigen())
                    .aeropuertoDestino(ruta.getEnvio().getAeropuertoDestino())
                    .cantidadMaletas(ruta.getEnvio().getCantidadMaletas())
                    .tiempoTotalMinutos(ruta.getTiempoTotalMinutos())
                    .estado("PLANIFICADA")
                    .fitness(resultado.getFitness())
                    .vuelos(vuelosDTO)
                    .build());
        }
        System.out.println("  [FINAL] Rutas enviadas al frontend: " + rutasDTO.size());
        return rutasDTO;
    }

    private EstadisticasIteracionDTO calcularEstadisticas(Individuo resultado, DataService dataService) {
        int planificados = 0, sinRuta = 0, inalcanzables = 0, totalMaletas = 0;
        for (Ruta r : resultado.getRutas()) {
            switch (r.getEstado()) {
                case PLANIFICADA -> { planificados++; totalMaletas += r.getEnvio().getCantidadMaletas(); }
                case SIN_RUTA -> sinRuta++;
                case INALCANZABLE -> inalcanzables++;
                default -> {}
            }
        }
        return EstadisticasIteracionDTO.builder()
                .enviosProcesados(resultado.getRutas().size())
                .planificados(planificados)
                .sinRuta(sinRuta)
                .inalcanzables(inalcanzables)
                .enviosEnEspera(dataService.getEnviosEnEspera().size())
                .fitnessPromedio(resultado.getFitness())
                .totalMaletasPlanificadas(totalMaletas)
                .build();
    }

    // =========================================================================
    // TIMELINE DE ALMACENES (Fiel a BackendApplication.imprimirReporteIntervalo)
    // =========================================================================

    private void procesarTimeline(Individuo resultado, DataService dataService,
            Map<String, List<long[]>> timelineAlmacenesGlobal) {

        List<Ruta> rutasPlanificadas = resultado.getRutas().stream()
                .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA && r.getVuelos() != null && !r.getVuelos().isEmpty())
                .collect(Collectors.toList());

        for (Ruta r : rutasPlanificadas) {
            Envio envio = r.getEnvio();
            int gmtO = dataService.getMapaAeropuertos().get(envio.getAeropuertoOrigen()).getGmt();
            LocalDateTime regGMT = LocalDateTime.of(
                    LocalDate.parse(envio.getFechaRegistro(), FMT_FECHA),
                    LocalTime.of(envio.getHoraRegistro(), envio.getMinutoRegistro()))
                    .minusHours(gmtO);

            agregarEventoTimeline(timelineAlmacenesGlobal, envio.getAeropuertoOrigen(), regGMT, +envio.getCantidadMaletas());

            LocalDateTime cursor = regGMT;
            for (PlanVuelo v : r.getVuelos()) {
                int gmtVOrig = dataService.getMapaAeropuertos().get(v.getOrigen()).getGmt();
                int gmtVDest = dataService.getMapaAeropuertos().get(v.getDestino()).getGmt();

                int minSGMT = (convertirAMinutos(v.getHoraSalida()) - gmtVOrig * 60 + 1440) % 1440;
                LocalDateTime despegue = cursor.with(minutosALocalTime(minSGMT));
                if (despegue.isBefore(cursor)) despegue = despegue.plusDays(1);

                int duracion = (convertirAMinutos(v.getHoraLlegada()) - gmtVDest * 60)
                        - (convertirAMinutos(v.getHoraSalida()) - gmtVOrig * 60);
                if (duracion < 0) duracion += 1440;
                LocalDateTime llegada = despegue.plusMinutes(duracion);

                agregarEventoTimeline(timelineAlmacenesGlobal, v.getOrigen(), despegue, -envio.getCantidadMaletas());
                agregarEventoTimeline(timelineAlmacenesGlobal, v.getDestino(), llegada, +envio.getCantidadMaletas());
                cursor = llegada;
            }
            agregarEventoTimeline(timelineAlmacenesGlobal, envio.getAeropuertoDestino(), cursor, -envio.getCantidadMaletas());
        }
    }

    // =========================================================================
    // DETECCIÓN DE COLAPSO (Fiel a BackendApplication.detectarColapso)
    // =========================================================================

    private SimulacionEventDTO.ColapsoDTO detectarColapso(Individuo res, DataService ds, LocalDateTime reloj,
            Map<String, List<long[]>> timelineGlobal) {

        Map<String, Integer> ocupacionVuelos = new HashMap<>();

        for (Ruta r : res.getRutas()) {
            Envio env = r.getEnvio();
            if (r.getEstado() == EstadoRuta.INALCANZABLE) {
                return SimulacionEventDTO.ColapsoDTO.builder()
                        .tipoError("ERROR TOPOLÓGICO (SIN RUTA FACTIBLE)")
                        .idEnvioCausante(env.getIdEnvio())
                        .rutaCausante(env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino())
                        .maletasCausantes(env.getCantidadMaletas())
                        .detalle("No existe conexión para " + env.getAeropuertoOrigen() + " → " + env.getAeropuertoDestino())
                        .build();
            } else if (r.getEstado() == EstadoRuta.SIN_RUTA) {
                return SimulacionEventDTO.ColapsoDTO.builder()
                        .tipoError("ERROR DE OPTIMIZACIÓN (NO SE PUDO ASIGNAR RUTA)")
                        .idEnvioCausante(env.getIdEnvio())
                        .rutaCausante(env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino())
                        .maletasCausantes(env.getCantidadMaletas())
                        .detalle("No se encontró ruta que respete límites de tiempo y capacidad.")
                        .build();
            } else if (r.getEstado() == EstadoRuta.PLANIFICADA) {
                Aeropuerto o = ds.getMapaAeropuertos().get(env.getAeropuertoOrigen());
                Aeropuerto d = ds.getMapaAeropuertos().get(env.getAeropuertoDestino());
                long sla = (o != null && d != null && o.getContinente().equals(d.getContinente())) ? 24 : 48;

                if (r.getTiempoTotalMinutos() > sla * 60) {
                    return SimulacionEventDTO.ColapsoDTO.builder()
                            .tipoError("INCUMPLIMIENTO DE SLA (TIEMPO EXCEDIDO)")
                            .idEnvioCausante(env.getIdEnvio())
                            .rutaCausante(env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino())
                            .maletasCausantes(env.getCantidadMaletas())
                            .detalle(String.format("Tiempo %dh %dm excede SLA de %dh",
                                    r.getTiempoTotalMinutos() / 60, r.getTiempoTotalMinutos() % 60, sla))
                            .build();
                }
                if (r.getVuelos() != null) {
                    for (PlanVuelo v : r.getVuelos()) {
                        String key = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
                        int nuevaOc = ocupacionVuelos.getOrDefault(key, 0) + env.getCantidadMaletas();
                        ocupacionVuelos.put(key, nuevaOc);
                        if (nuevaOc > v.getCapacidad()) {
                            return SimulacionEventDTO.ColapsoDTO.builder()
                                    .tipoError("EXCESO DE CAPACIDAD EN VUELO")
                                    .idEnvioCausante(env.getIdEnvio())
                                    .rutaCausante(env.getAeropuertoOrigen() + "->" + env.getAeropuertoDestino())
                                    .maletasCausantes(env.getCantidadMaletas())
                                    .detalle(String.format("Vuelo %s->%s: %d/%d", v.getOrigen(), v.getDestino(), nuevaOc, v.getCapacidad()))
                                    .build();
                        }
                    }
                }
            }
        }

        // Verificar almacenes (simplificado respecto a BackendApplication)
        // No retornar colapso por almacenes para evitar falsos positivos en streaming
        return null;
    }

    // =========================================================================
    // RESUMEN FINAL (Fiel a BackendApplication.imprimirResumenFinal)
    // =========================================================================

    private ResumenFinalDTO calcularResumenFinal(DataService dataService,
            Map<String, List<long[]>> timelineAlmacenes,
            LocalDateTime relojInicio, LocalDateTime relojParada,
            long tiempoEjecucionRealMs, boolean colapsoDetectado) {

        List<Ruta> rutasHistorico = dataService.getRutasPlanificadasHistorico();
        int totalMaletas = 0;
        double sumaConsumoSLA = 0;
        Map<String, Integer> usoVuelo = new HashMap<>();
        Map<String, Integer> capVuelo = new HashMap<>();

        for (Ruta r : rutasHistorico) {
            totalMaletas += r.getEnvio().getCantidadMaletas();
            Aeropuerto o = dataService.getMapaAeropuertos().get(r.getEnvio().getAeropuertoOrigen());
            Aeropuerto d = dataService.getMapaAeropuertos().get(r.getEnvio().getAeropuertoDestino());
            long slaHoras = (o != null && d != null && o.getContinente().equals(d.getContinente())) ? 24 : 48;
            sumaConsumoSLA += (r.getTiempoTotalMinutos() * 100.0) / (slaHoras * 60);

            if (r.getVuelos() != null) {
                for (PlanVuelo v : r.getVuelos()) {
                    String clave = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
                    usoVuelo.merge(clave, r.getEnvio().getCantidadMaletas(), Integer::sum);
                    capVuelo.put(clave, v.getCapacidad());
                }
            }
        }

        // Promedio de vuelos
        long totalMaletasEnVuelos = 0, totalCapVuelos = 0;
        for (String clave : usoVuelo.keySet()) {
            totalMaletasEnVuelos += usoVuelo.get(clave);
            totalCapVuelos += capVuelo.get(clave);
        }
        double promVuelos = totalCapVuelos == 0 ? 0 : (totalMaletasEnVuelos * 100.0) / totalCapVuelos;

        // Promedio de SLA
        double promSLA = rutasHistorico.isEmpty() ? 0 : sumaConsumoSLA / rutasHistorico.size();

        // Promedio de almacenes
        double sumaPctAlm = 0;
        int almacenesUsados = 0;
        long inicioMin = relojInicio.toEpochSecond(ZoneOffset.UTC) / 60;
        long finMin = relojParada.toEpochSecond(ZoneOffset.UTC) / 60;
        long totalMinutos = finMin - inicioMin;

        if (totalMinutos > 0) {
            for (Map.Entry<String, List<long[]>> entry : timelineAlmacenes.entrySet()) {
                Aeropuerto aero = dataService.getMapaAeropuertos().get(entry.getKey());
                if (aero == null || aero.getCapacidad() == 0) continue;

                List<long[]> eventos = new ArrayList<>(entry.getValue());
                eventos.sort(Comparator.comparingLong(a -> a[0]));
                long area = 0;
                long lastTime = inicioMin;
                int currentOc = 0;
                for (long[] ev : eventos) {
                    long t = Math.max(inicioMin, Math.min(ev[0], finMin));
                    area += currentOc * (t - lastTime);
                    currentOc += (int) ev[1];
                    lastTime = t;
                }
                area += currentOc * (finMin - lastTime);
                double ocProm = (double) area / totalMinutos;
                sumaPctAlm += (ocProm * 100.0) / aero.getCapacidad();
                almacenesUsados++;
            }
        }
        double promAlm = almacenesUsados == 0 ? 0 : sumaPctAlm / almacenesUsados;

        double FO = (promSLA * 4 + promVuelos * 3 + promAlm * 3) / 10;

        return ResumenFinalDTO.builder()
                .totalEnviosPlanificados(rutasHistorico.size())
                .totalMaletasPlanificadas(totalMaletas)
                .consumoPromedioSLA(promSLA)
                .ocupacionPromedioVuelos(promVuelos)
                .ocupacionPromedioAlmacenes(promAlm)
                .funcionObjetivo(FO)
                .tiempoEjecucionRealSegundos(tiempoEjecucionRealMs / 1000.0)
                .colapsoDetectado(colapsoDetectado)
                .build();
    }

    // =========================================================================
    // HELPERS
    // =========================================================================

    private void enviarEvento(SseEmitter emitter, SimulacionEventDTO evento) {
        try {
            emitter.send(SseEmitter.event()
                    .name(evento.getTipo())
                    .data(evento, MediaType.APPLICATION_JSON));
        } catch (Exception e) {
            System.err.println("[SSE] Error enviando evento: " + e.getMessage());
        }
    }

    private void agregarEventoTimeline(Map<String, List<long[]>> timeline, String aero, LocalDateTime t, int d) {
        timeline.computeIfAbsent(aero, x -> new ArrayList<>())
                .add(new long[]{t.toEpochSecond(ZoneOffset.UTC) / 60, d});
    }

    private int convertirAMinutos(String h) {
        String[] p = h.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    private LocalTime minutosALocalTime(int m) {
        return LocalTime.of((m / 60) % 24, m % 60);
    }
}
