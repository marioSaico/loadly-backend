package com.loadly.backend;
 
import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.model.*;
import com.loadly.backend.planificador.Planificador;
import com.loadly.backend.planificador.PlanificadorACO;
import com.loadly.backend.service.DataService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ApplicationContext;
 
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;
 
@SpringBootApplication
public class BackendApplication {
 
    // =========================================================================
    // FORMATEADORES GLOBALES
    // =========================================================================
    private static final DateTimeFormatter FMT_INPUT = DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");
    private static final DateTimeFormatter FMT_LOG   = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
 
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
        String aeropuerto;
        int    variacionCapacidad;
        String mensaje;
 
        EventoSimulacion(LocalDateTime tiempo, String aeropuerto,
                         int variacion, String mensaje) {
            this.tiempoGMT         = tiempo;
            this.aeropuerto        = aeropuerto;
            this.variacionCapacidad = variacion;
            this.mensaje           = mensaje;
        }
    }
 
    // =========================================================================
    // MAIN
    // =========================================================================
    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(BackendApplication.class, args);
        Planificador    planificador    = context.getBean(Planificador.class);
        PlanificadorACO planificadorACO = context.getBean(PlanificadorACO.class);
        DataService     dataService     = context.getBean(DataService.class);
 
        // ── ESCENARIOS GA ────────────────────────────────────────────────────
        // 1️⃣  DÍA A DÍA        Ta=2s | Sa=10min | K=1  | Población=50
        ejecutarEscenario("DIA A DIA", "20260101-00-00", "20260102-00-00",
                2, 10, 1, 5, "GA",
                (lim, tam, ms) -> planificador.planificar(lim, tam, ms),
                dataService);
 
        // 2️⃣  PERÍODO 5 DÍAS   Ta=25s | Sa=10min | K=5  | Población=100
        // ejecutarEscenario("PERIODO (5 DIAS)", "20260101-00-00", "20260106-00-00",
        //         25, 10, 5, 100, "GA",
        //         (lim, tam, ms) -> planificador.planificar(lim, tam, ms),
        //         dataService);
 
        // 3️⃣  COLAPSO          Ta=1s  | Sa=30min | K=1  | Población=5
        // ejecutarEscenario("COLAPSO", "20260101-00-00", "20260102-00-00",
        //         1, 30, 1, 5, "GA",
        //         (lim, tam, ms) -> planificador.planificar(lim, tam, ms),
        //         dataService);
 
        // ── ESCENARIOS ACO ───────────────────────────────────────────────────
        // 1️⃣  DÍA A DÍA        Ta=2s | Sa=10min | K=1  | Hormigas=30
        // ejecutarEscenario("DIA A DIA - ACO", "20260101-00-00", "20260102-00-00",
        //         2, 10, 1, 30, "ACO",
        //         (lim, tam, ms) -> planificadorACO.planificar(lim, tam, ms),
        //         dataService);
 
        // 2️⃣  PERÍODO 5 DÍAS   Ta=25s | Sa=10min | K=5  | Hormigas=30
        // ejecutarEscenario("PERIODO (5 DIAS) - ACO", "20260101-00-00", "20260106-00-00",
        //         25, 10, 5, 30, "ACO",
        //         (lim, tam, ms) -> planificadorACO.planificar(lim, tam, ms),
        //         dataService);
    }
 
    // =========================================================================
    // MÉTODO PRINCIPAL DE SIMULACIÓN — sirve para GA y ACO
    // =========================================================================
 
    /**
     * Ejecuta un escenario de simulación completo.
     *
     * @param nombre         Nombre del escenario (para logs)
     * @param inicioStr      Fecha/hora de inicio en formato yyyyMMdd-HH-mm
     * @param finStr         Fecha/hora de fin
     * @param taSegundos     Tiempo de ejecución del algoritmo (Ta)
     * @param sa             Salto del planificador en minutos (Sa)
     * @param k              Constante de proporcionalidad (Sc = K × Sa)
     * @param tamano         Tamaño de población (GA) o número de hormigas (ACO)
     * @param tipoAlgoritmo  "GA" o "ACO" (solo afecta el log)
     * @param planFunc       Función que invoca al planificador correspondiente
     * @param dataService    Servicio de datos
     */
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
 
        // Datos estáticos para la bitácora
        Map<String, Integer> capacidadOriginal = dataService.getAeropuertos().stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getCapacidad));
        Map<String, Integer> mapaGmt = dataService.getAeropuertos().stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getGmt));
 
        List<EventoSimulacion> bitacoraGlobal = new ArrayList<>();
        boolean colapsoDetectado = false;
 
        System.out.println("\n" + "=".repeat(80));
        System.out.println("   INICIANDO ESCENARIO [" + tipoAlgoritmo + "]: " + nombre);
        System.out.println("   Parámetros -> Ta: " + taSegundos + "s | Sa: " + sa
                + " min | K: " + k + " | Sc: " + sc + " min | Tamaño: " + tamano);
        System.out.println("   Período: " + inicioStr + " → " + finStr
                + " (" + ChronoUnit.DAYS.between(relojSimulado, finSimulacion) + " días)");
        System.out.println("=".repeat(80));
 
        // ── BUCLE PRINCIPAL ──────────────────────────────────────────────────
        while ((limiteLecturaDatos.isBefore(finSimulacion)
                || limiteLecturaDatos.isEqual(finSimulacion))
               && !colapsoDetectado) {
 
            String limiteLecturaStr = limiteLecturaDatos.format(FMT_INPUT);
            String relojActualStr   = relojSimulado.format(FMT_INPUT);
 
            System.out.println("\n>>> [RELOJ: " + relojSimulado.format(FMT_LOG)
                    + "] Planificando envíos hasta " + limiteLecturaDatos.format(FMT_LOG) + "...");
 
            // 1. Liberar almacenes y resetear vuelos según la agenda de eventos
            dataService.procesarEventosDelReloj(relojActualStr);
 
            // 2. Ejecutar el algoritmo planificador (GA o ACO)
            Individuo resultado = planFunc.planificar(limiteLecturaStr, tamano, tiempoLimiteMs);
 
            if (resultado == null) {
                System.out.println("    [-] No hay envíos nuevos ni rezagados en este intervalo.");
            } else {
                // 3. Detectar colapso
                ResultadoColapso colapso = detectarColapso(resultado, dataService, relojSimulado);
 
                if (colapso.hayColapso()) {
                    imprimirAlertas(colapso);
                    colapsoDetectado = true;
                } else {
                    // 4. Imprimir reporte del intervalo
                    imprimirReporteIntervalo(resultado, relojSimulado, limiteLecturaDatos,
                            taSegundos, tipoAlgoritmo, dataService);
                }
            }
 
            limiteLecturaDatos = limiteLecturaDatos.plusMinutes(sc);
            relojSimulado      = relojSimulado.plusMinutes(sa);
        }
 
        // ── BITÁCORA FINAL ───────────────────────────────────────────────────
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
 
    /** Agrupa los tres tipos de colapso en un objeto resultado. */
    static class ResultadoColapso {
        boolean topologico;
        boolean porTiempo;
        boolean porEspacio;
 
        boolean hayColapso() { return topologico || porTiempo || porEspacio; }
    }
 
    /**
     * Verifica si hay algún tipo de colapso logístico:
     *  - Topológico: algún envío quedó INALCANZABLE (no hay ruta física posible)
     *  - Por tiempo:  algún envío en backlog superó su SLA sin ser planificado
     *  - Por espacio: el backlog acumulado supera la capacidad de algún almacén
     */
    private static ResultadoColapso detectarColapso(
            Individuo resultado, DataService dataService, LocalDateTime relojSimulado) {
 
        ResultadoColapso rc = new ResultadoColapso();
 
        // Colapso topológico
        for (Ruta r : resultado.getRutas()) {
            if (r.getEstado() == EstadoRuta.INALCANZABLE) {
                rc.topologico = true;
                break;
            }
        }
 
        // Colapso por tiempo y por espacio (basados en el backlog)
        Map<String, Integer> ocupacionBacklog = new HashMap<>();
        for (Envio env : dataService.getEnviosEnEspera()) {
            // Calcular SLA del envío
            long maxHoras = 48;
            Aeropuerto o = dataService.getMapaAeropuertos().get(env.getAeropuertoOrigen());
            Aeropuerto d = dataService.getMapaAeropuertos().get(env.getAeropuertoDestino());
            if (o != null && d != null && o.getContinente().equals(d.getContinente())) maxHoras = 24;
 
            // Verificar si superó el SLA
            LocalDateTime horaReg = LocalDateTime.of(
                    Integer.parseInt(env.getFechaRegistro().substring(0, 4)),
                    Integer.parseInt(env.getFechaRegistro().substring(4, 6)),
                    Integer.parseInt(env.getFechaRegistro().substring(6, 8)),
                    env.getHoraRegistro(), env.getMinutoRegistro());
 
            if (ChronoUnit.HOURS.between(horaReg, relojSimulado) > maxHoras) {
                rc.porTiempo = true;
            }
 
            // Acumular maletas en backlog por aeropuerto
            ocupacionBacklog.merge(env.getAeropuertoOrigen(),
                    env.getCantidadMaletas(), Integer::sum);
        }
 
        // Verificar si el backlog desborda algún almacén
        Map<String, Integer> capDinamica = dataService.getCapacidadDinamicaAlmacenes();
        for (Map.Entry<String, Integer> entry : ocupacionBacklog.entrySet()) {
            if (entry.getValue() > capDinamica.getOrDefault(entry.getKey(), 0)) {
                rc.porEspacio = true;
                break;
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
            DataService dataService) {
 
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
 
        if (planificados > 0) {
            System.out.println(" >>> REPORTE DE RUTAS ASIGNADAS <<<");
            System.out.println("-".repeat(70));
 
            // Ordenar por hora de salida GMT del primer vuelo
            resultado.getRutas().stream()
                    .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA
                            && r.getVuelos() != null
                            && !r.getVuelos().isEmpty())
                    .sorted(Comparator.comparingInt(r -> {
                        PlanVuelo pv = r.getVuelos().get(0);
                        Aeropuerto aero = dataService.getMapaAeropuertos().get(pv.getOrigen());
                        int gmt = (aero != null) ? aero.getGmt() : 0;
                        String[] p = pv.getHoraSalida().split(":");
                        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]) - gmt * 60;
                    }))
                    .forEach(r -> imprimirDetalleRuta(r, dataService));
        }
        System.out.println("=".repeat(70) + "\n");
    }
 
    // =========================================================================
    // IMPRESIÓN DE DETALLE DE RUTA
    // =========================================================================
 
    /**
     * Imprime el detalle de una ruta planificada.
     *
     * La ocupación de vuelos y almacenes se lee directamente de las capacidades
     * dinámicas del DataService, que ya fueron decrementadas al confirmar el plan.
     * Esto evita el doble conteo que ocurría cuando se sumaban las maletas
     * manualmente sobre una capacidad que ya había sido decrementada.
     */
    private static void imprimirDetalleRuta(Ruta r, DataService dataService) {
        Envio      env     = r.getEnvio();
        Aeropuerto origen  = dataService.getMapaAeropuertos().get(env.getAeropuertoOrigen());
        Aeropuerto destino = dataService.getMapaAeropuertos().get(env.getAeropuertoDestino());
 
        long limiteSlaHoras = (origen != null && destino != null
                && origen.getContinente().equals(destino.getContinente())) ? 24 : 48;
        String estado = (r.getTiempoTotalMinutos() <= limiteSlaHoras * 60)
                ? "[OK] A TIEMPO" : "[!] FUERA DE PLAZO";
 
        System.out.printf("ENVÍO ID: %s | ESTADO: %s%n", env.getIdEnvio(), estado);
        System.out.printf("Origen: %s (%s) -> Destino: %s (%s)%n",
                env.getAeropuertoOrigen(),
                (origen  != null ? origen.getContinente()  : "?"),
                env.getAeropuertoDestino(),
                (destino != null ? destino.getContinente() : "?"));
        System.out.printf("Límite SLA: %dh | Tiempo real: %dh %dm%n",
                limiteSlaHoras,
                r.getTiempoTotalMinutos() / 60,
                r.getTiempoTotalMinutos() % 60);
        System.out.println("Ruta asignada:");
 
        Map<String, Integer> capDinamicaVuelos    = dataService.getCapacidadDinamicaVuelos();
        Map<String, Integer> capDinamicaAlmacenes = dataService.getCapacidadDinamicaAlmacenes();
 
        int paso = 1;
        for (PlanVuelo v : r.getVuelos()) {
            Aeropuerto aeroOrig = dataService.getMapaAeropuertos().get(v.getOrigen());
            Aeropuerto aeroDest = dataService.getMapaAeropuertos().get(v.getDestino());
 
            int gmtOrig = (aeroOrig != null) ? aeroOrig.getGmt() : 0;
            int gmtDest = (aeroDest != null) ? aeroDest.getGmt() : 0;
 
            // Convertir horas locales a GMT para el display
            LocalTime salidaGMT  = LocalTime.parse(v.getHoraSalida()).minusHours(gmtOrig);
            LocalTime llegadaGMT = LocalTime.parse(v.getHoraLlegada()).minusHours(gmtDest);
 
            // Ocupación del vuelo: capacidad original - capacidad dinámica actual
            // DataService ya decrementó capDinamicaVuelos al confirmar el plan
            String claveVuelo    = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
            int capMaxVuelo      = v.getCapacidad();
            int capRestanteVuelo = capDinamicaVuelos.getOrDefault(claveVuelo, capMaxVuelo);
            int ocupadoVuelo     = capMaxVuelo - capRestanteVuelo;
 
            // Ocupación del almacén origen del vuelo
            // DataService ya decrementó capDinamicaAlmacenes al confirmar el plan
            int capMaxAlmacen      = (aeroOrig != null) ? aeroOrig.getCapacidad() : 0;
            int capRestanteAlmacen = capDinamicaAlmacenes.getOrDefault(v.getOrigen(), capMaxAlmacen);
            int ocupadoAlmacen     = capMaxAlmacen - capRestanteAlmacen;
 
            System.out.printf("   (%d) %s -> %s | Salida(GMT): %s | Llegada(GMT): %s"
                    + " | Vuelo: %d/%d | Almacén %s: %d/%d%n",
                    paso++,
                    v.getOrigen(), v.getDestino(),
                    salidaGMT, llegadaGMT,
                    ocupadoVuelo, capMaxVuelo,
                    v.getOrigen(), ocupadoAlmacen, capMaxAlmacen);
        }
        System.out.println("-".repeat(70));
    }
 
    // =========================================================================
    // BITÁCORA CRONOLÓGICA
    // =========================================================================
 
    private static void procesarRutaEnBitacora(
            Ruta ruta, Map<String, Integer> mapaGmt,
            List<EventoSimulacion> bitacora) {
 
        Envio envio    = ruta.getEnvio();
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
    // IMPRESIÓN DE BITÁCORA FINAL Y RESUMEN
    // =========================================================================
 
    private static void imprimirBitacoraFinal(
            List<EventoSimulacion> bitacora,
            Map<String, Integer> capsOrig,
            int total, boolean colapso,
            LocalDateTime relojParada,
            List<Ruta> rutasHistorico) {
 
        // Mapa de rutas consolidado
        if (!rutasHistorico.isEmpty()) {
            System.out.println("\n" + "=".repeat(100));
            System.out.println("   MAPA DE RUTAS ASIGNADAS CONSOLIDADO (TOTAL HISTÓRICO)");
            System.out.println("=".repeat(100));
            System.out.printf(" %-10s | %-7s | %-20s | %-10s | %s%n",
                    "ENVÍO", "MALETAS", "ORIGEN->DESTINO", "TIEMPO", "ITINERARIO");
            System.out.println("-".repeat(100));
 
            for (Ruta r : rutasHistorico) {
                String origDest  = r.getEnvio().getAeropuertoOrigen()
                        + " -> " + r.getEnvio().getAeropuertoDestino();
                String tiempoStr = String.format("%dh %02dm",
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
 
        // Bitácora cronológica
        System.out.println("\n\n" + "=".repeat(80));
        System.out.println("       BITÁCORA CRONOLÓGICA DE EVENTOS (TODOS LOS ALMACENES)");
        System.out.println("=".repeat(80));
 
        bitacora.sort(Comparator.comparing(e -> e.tiempoGMT));
        Map<String, Integer> capsDin = new HashMap<>(capsOrig);
 
        for (EventoSimulacion e : bitacora) {
            int nueva  = capsDin.getOrDefault(e.aeropuerto, 0) + e.variacionCapacidad;
            capsDin.put(e.aeropuerto, nueva);
            int ocupado = capsOrig.getOrDefault(e.aeropuerto, 0) - nueva;
            System.out.printf("[%s GMT] %-40s | %s %d | Almacén %s: %d/%d%n",
                    e.tiempoGMT.format(FMT_LOG), e.mensaje,
                    (e.variacionCapacidad > 0 ? "LIBERA" : "OCUPA "),
                    Math.abs(e.variacionCapacidad),
                    e.aeropuerto, ocupado,
                    capsOrig.getOrDefault(e.aeropuerto, 0));
        }
 
        // Resumen final
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
 