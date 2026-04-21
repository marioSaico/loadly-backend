package com.loadly.backend;

import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.model.*;
//import com.loadly.backend.planificador.Planificador;
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

    static class EventoSimulacion {
        LocalDateTime tiempoGMT;
        String aeropuerto;
        int variacionCapacidad;
        String mensaje;

        public EventoSimulacion(LocalDateTime tiempo, String aeropuerto, int variacion, String mensaje) {
            this.tiempoGMT = tiempo;
            this.aeropuerto = aeropuerto;
            this.variacionCapacidad = variacion;
            this.mensaje = mensaje;
        }
    }

    public static void main(String[] args) {
        ApplicationContext context = SpringApplication.run(BackendApplication.class, args);
        PlanificadorACO planificador = context.getBean(PlanificadorACO.class);
        DataService dataService = context.getBean(DataService.class);

        // =========================================================================================
        // 🚀 PANEL DE CONTROL DE ESCENARIOS
        // =========================================================================================

        //PARA GENETICO

        // 1️⃣ ESCENARIO: DIA A DIA
        // Configuracion : Ta=2s | Sa=10min | K=1 | Poblacion=50
        //ejecutarEscenario("DIA A DIA", "20260101-00-00", "20260102-00-00", 2, 10, 1, 50, planificador, dataService);
        // 2️⃣ ESCENARIO: PERIODO 5 DIAS
        // Configuracion : Ta=15s | Sa=40min | K=6 | Poblacion=100
        //ejecutarEscenario("PERIODO (5 DIAS)", "20260101-00-00", "20260106-00-00", 15, 40, 6, 100, planificador, dataService);

        // 3️⃣ ESCENARIO: COLAPSO
        // Configuracion: Ta=15s | Sa=40min | K=6  | Poblacion=100
        //ejecutarEscenario("COLAPSO", "20260101-00-00", "20260102-00-00", 1, 30, 1, 5, planificador, dataService);
    
        //PARA ACO

        // 1️⃣ ESCENARIO: DIA A DIA
        // Configuracion : Ta=2s | Sa=10min | K=1 | Poblacion=50
        //ejecutarEscenario("DIA A DIA - ACO", "20260102-00-00", "20260102-06-00", 5, 10, 1, 50, planificador, dataService);
        // 2️⃣ ESCENARIO: PERIODO 5 DIAS
        // Configuracion : Ta=15s | Sa=40min | K=6 | Poblacion=100
        //ejecutarEscenario("PERIODO (5 DIAS) - ACO", "20260102-00-00", "20260107-00-00", 5, 120, 1, 30, planificador, dataService);        // 3️⃣ ESCENARIO: COLAPSO
        // Configuracion: Ta=15s | Sa=40min | K=6  | Poblacion=100
        ejecutarEscenario("COLAPSO - ACO", "20260102-00-00", "20260102-12-00", 1, 5, 1, 5, planificador, dataService);
    }

    public static void ejecutarEscenario(String nombre, String inicioStr, String finStr, 
                                         int taSegundos, int sa, int k, int tamanoPoblacion, 
                                         PlanificadorACO planificador, DataService dataService) {
        
        DateTimeFormatter fmtInput = DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");
        DateTimeFormatter fmtLog = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        
        LocalDateTime relojSimulado = LocalDateTime.parse(inicioStr, fmtInput);
        LocalDateTime finSimulacion = LocalDateTime.parse(finStr, fmtInput);
        
        int sc = sa * k; 
        long tiempoLimiteMs = taSegundos * 1000L; 

        System.out.println("\n" + "=".repeat(80));
        System.out.println("   INICIANDO ESCENARIO: " + nombre);
        System.out.println("   Parametros -> Ta: " + taSegundos + "s | Sa: " + sa + " min | K: " + k + " | Sc: " + sc + " min");
        
        long diasSimulacion = ChronoUnit.DAYS.between(relojSimulado, finSimulacion);
        System.out.println("   Tiempo a simular: " + diasSimulacion + " dias (De " + inicioStr + " a " + finStr + ")");
        System.out.println("=".repeat(80));

        Map<String, Integer> capacidadOriginal = dataService.getAeropuertos().stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getCapacidad));
        Map<String, Integer> mapaGmt = dataService.getAeropuertos().stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getGmt));
        
        List<EventoSimulacion> bitacoraGlobal = new ArrayList<>();
        boolean colapsoDetectado = false;
        LocalDateTime limiteLecturaDatos = relojSimulado;

        // 💡 CORRECCIÓN 1: Este mapa vive fuera del bucle para recordar la ocupación de cada vuelo en el tiempo
        Map<String, Integer> usoVuelosGlobal = new HashMap<>();

        // --- BUCLE PRINCIPAL DE SIMULACION ---
        while ((relojSimulado.isBefore(finSimulacion) || (relojSimulado.isEqual(finSimulacion))) && !colapsoDetectado) {
            
            String limiteLecturaStr = limiteLecturaDatos.format(fmtInput);
            String relojActualStr = relojSimulado.format(fmtInput);
            
            System.out.println("\n>>> [RELOJ: " + relojSimulado.format(fmtLog) + "] Planificando pedidos acumulados hasta las " + limiteLecturaDatos.format(fmtLog) + "...");

            dataService.procesarEventosDelReloj(relojActualStr);
            Individuo resultado = planificador.planificar(limiteLecturaStr, tamanoPoblacion, tiempoLimiteMs);

            if (resultado != null) {
                long maletasSinRutaTemporal = 0;
                boolean colapsoTopologico = false;
                
                for (Ruta r : resultado.getRutas()) {
                    if (r.getEstado() == EstadoRuta.INALCANZABLE) colapsoTopologico = true;
                    if (r.getEstado() == EstadoRuta.SIN_RUTA) maletasSinRutaTemporal++;
                }

                boolean colapsoPorTiempo = false;
                boolean colapsoPorEspacio = false;
                
                List<Envio> enEspera = dataService.getEnviosEnEspera();
                Map<String, Integer> ocupacionBacklog = new HashMap<>();

                for (Envio env : enEspera) {
                    long maxHoras = 48;
                    Aeropuerto o = dataService.getMapaAeropuertos().get(env.getAeropuertoOrigen());
                    Aeropuerto d = dataService.getMapaAeropuertos().get(env.getAeropuertoDestino());
                    if (o != null && d != null && o.getContinente().equals(d.getContinente())) maxHoras = 24;
                    
                    LocalDateTime horaReg = LocalDateTime.of(
                        Integer.parseInt(env.getFechaRegistro().substring(0,4)),
                        Integer.parseInt(env.getFechaRegistro().substring(4,6)),
                        Integer.parseInt(env.getFechaRegistro().substring(6,8)),
                        env.getHoraRegistro(), env.getMinutoRegistro()
                    );
                    
                    long horasEsperando = ChronoUnit.HOURS.between(horaReg, relojSimulado);
                    if (horasEsperando > maxHoras) colapsoPorTiempo = true;

                    ocupacionBacklog.put(env.getAeropuertoOrigen(), ocupacionBacklog.getOrDefault(env.getAeropuertoOrigen(), 0) + env.getCantidadMaletas());
                }

                Map<String, Integer> capDinamica = dataService.getCapacidadDinamicaAlmacenes();
                for (Map.Entry<String, Integer> entry : ocupacionBacklog.entrySet()) {
                    int espacioLibre = capDinamica.getOrDefault(entry.getKey(), 0);
                    if (entry.getValue() > espacioLibre) {
                        colapsoPorEspacio = true;
                    }
                }

                if (colapsoTopologico || colapsoPorTiempo || colapsoPorEspacio) {
                    System.out.println("    [!] ¡ALERTA DE COLAPSO LOGÍSTICO!");
                    if (colapsoTopologico) System.out.println("        -> [Topológico] No existen vuelos físicos posibles para llegar al destino.");
                    if (colapsoPorTiempo) System.out.println("        -> [Tiempo] Uno o más envíos excedieron el plazo límite (24h/48h) en tierra.");
                    if (colapsoPorEspacio) System.out.println("        -> [Espacio] El almacén se desbordó debido a la acumulación de envíos en espera.");
                    
                    colapsoDetectado = true;
                    break;
                } else {
                    int planificados = resultado.getRutas().size() - (int)maletasSinRutaTemporal;
                    int maletasTotales = resultado.getRutas().stream()
                            .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA)
                            .mapToInt(r -> r.getEnvio().getCantidadMaletas()).sum();

                    System.out.println("\n======================================================================");
                    System.out.println(" [RELOJ SIMULADO: " + relojSimulado.format(fmtLog) + "]");
                    System.out.println(" Límite de lectura de datos: " + limiteLecturaDatos.format(fmtLog));
                    System.out.println("======================================================================");
                    System.out.println(" -> Envíos planificados en este salto: " + planificados);
                    System.out.println(" -> Maletas procesadas en este salto: " + maletasTotales);
                    System.out.printf(" -> Generaciones GA evaluadas por %ds | Mejor Fitness: %.6f\n", taSegundos, resultado.getFitness());
                    if (planificados > 0) {
                        System.out.println(" >>> REPORTE DE RUTAS ASIGNADAS <<<");

                        Map<String, Integer> usoAlmacenLocal = new HashMap<>();

                        // Ordenar por hora de salida GMT del primer vuelo (orden operativo)
                        resultado.getRutas().stream()
                            .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA
                                    && r.getVuelos() != null
                                    && !r.getVuelos().isEmpty())
                            .sorted(Comparator.comparingInt(r -> {
                                PlanVuelo pv = r.getVuelos().get(0);
                                Aeropuerto aero = dataService.getMapaAeropuertos().get(pv.getOrigen());
                                int gmt = (aero != null) ? aero.getGmt() : 0;
                                String[] partes = pv.getHoraSalida().split(":");
                                int minSalidaLocal = Integer.parseInt(partes[0]) * 60 
                                                + Integer.parseInt(partes[1]);
                                // Convertir a GMT para comparar correctamente entre aeropuertos
                                return minSalidaLocal - (gmt * 60);
                            }))
                            .forEach(r -> imprimirDetalleRuta(
                                    r, dataService, usoVuelosGlobal, usoAlmacenLocal, capDinamica));
                    }
                    System.out.println("======================================================================\n");
                }
            } else {
                System.out.println("    [-] No hay envios nuevos ni rezagados en este intervalo.");
            }
            limiteLecturaDatos = limiteLecturaDatos.plusMinutes(sc);
            relojSimulado = relojSimulado.plusMinutes(sa);
        }

        // --- CONSTRUCCION DE LA BITACORA ---
        int totalEnviosProcesados = 0;
        List<Ruta> rutasHistorico = dataService.getRutasPlanificadasHistorico();
        
        for (Ruta ruta : rutasHistorico) {
            totalEnviosProcesados++;
            procesarRutaEnBitacora(ruta, mapaGmt, bitacoraGlobal);
        }

        imprimirBitacoraFinal(bitacoraGlobal, capacidadOriginal, totalEnviosProcesados, colapsoDetectado, relojSimulado, fmtLog, rutasHistorico);
    }

    private static void procesarRutaEnBitacora(Ruta ruta, Map<String, Integer> mapaGmt, List<EventoSimulacion> bitacora) {
        Envio envio = ruta.getEnvio();
        int gmtOrigen = mapaGmt.getOrDefault(envio.getAeropuertoOrigen(), 0);

        LocalDateTime regLocal = LocalDateTime.of(
            Integer.parseInt(envio.getFechaRegistro().substring(0, 4)),
            Integer.parseInt(envio.getFechaRegistro().substring(4, 6)),
            Integer.parseInt(envio.getFechaRegistro().substring(6, 8)),
            envio.getHoraRegistro(), envio.getMinutoRegistro()
        );
        LocalDateTime regGMT = regLocal.minusHours(gmtOrigen);
        LocalDateTime finGMT = regGMT.plusMinutes(ruta.getTiempoTotalMinutos());

        bitacora.add(new EventoSimulacion(regGMT, envio.getAeropuertoOrigen(), -envio.getCantidadMaletas(), "[CHECK-IN]   Envio " + envio.getIdEnvio()));

        LocalDateTime cursor = regGMT;
        if (ruta.getVuelos() != null) {
            for (PlanVuelo v : ruta.getVuelos()) {
                int gmtVOrig = mapaGmt.getOrDefault(v.getOrigen(), 0);
                int gmtVDest = mapaGmt.getOrDefault(v.getDestino(), 0);
                
                LocalTime sGMT = LocalTime.parse(v.getHoraSalida()).minusHours(gmtVOrig);
                LocalTime lGMT = LocalTime.parse(v.getHoraLlegada()).minusHours(gmtVDest);

                LocalDateTime salida = cursor.with(sGMT);
                if (salida.isBefore(cursor)) salida = salida.plusDays(1);
                LocalDateTime llegada = salida.with(lGMT);
                if (llegada.isBefore(salida)) llegada = llegada.plusDays(1);

                bitacora.add(new EventoSimulacion(salida, v.getOrigen(), envio.getCantidadMaletas(), "[DESPEGUE]   " + v.getOrigen() + "->" + v.getDestino() + " (" + envio.getIdEnvio() + ")"));
                bitacora.add(new EventoSimulacion(llegada, v.getDestino(), -envio.getCantidadMaletas(), "[ATERRIZAJE] " + v.getOrigen() + "->" + v.getDestino() + " (" + envio.getIdEnvio() + ")"));
                cursor = llegada;
            }
        }

        bitacora.add(new EventoSimulacion(finGMT, envio.getAeropuertoDestino(), envio.getCantidadMaletas(), "[RECOJO]     Envio " + envio.getIdEnvio()));
    }

    private static void imprimirDetalleRuta(Ruta r, DataService dataService, 
                                            Map<String, Integer> usoVuelosGlobal, 
                                            Map<String, Integer> usoAlmacenLocal, 
                                            Map<String, Integer> capDinamicaActual) {
        Envio env = r.getEnvio();
        Aeropuerto origen = dataService.getMapaAeropuertos().get(env.getAeropuertoOrigen());
        Aeropuerto destino = dataService.getMapaAeropuertos().get(env.getAeropuertoDestino());

        long limiteSlaHoras = (origen != null && destino != null && origen.getContinente().equals(destino.getContinente())) ? 24 : 48;
        long limiteSlaMin = limiteSlaHoras * 60;
        
        long horasReales = r.getTiempoTotalMinutos() / 60;
        long minReales = r.getTiempoTotalMinutos() % 60;
        
        String estado = (r.getTiempoTotalMinutos() <= limiteSlaMin) ? "[OK] A TIEMPO" : "[!] FUERA DE PLAZO";
        String contOrigen = (origen != null) ? origen.getContinente() : "Desconocido";
        String contDestino = (destino != null) ? destino.getContinente() : "Desconocido";

        System.out.println("----------------------------------------------------------------------");
        System.out.printf("ENVÍO ID: %s | ESTADO: %s\n", env.getIdEnvio(), estado);
        System.out.printf("Origen: %s (%s) -> Destino: %s (%s)\n", env.getAeropuertoOrigen(), contOrigen, env.getAeropuertoDestino(), contDestino);
        System.out.printf("Límite del SLA: %d horas | Tiempo Real: %dh %dm\n", limiteSlaHoras, horasReales, minReales);
        System.out.println("Ruta asignada:");
        
        int paso = 1;
        int maletas = env.getCantidadMaletas();

        for (PlanVuelo v : r.getVuelos()) {
            Aeropuerto aeroOrig = dataService.getMapaAeropuertos().get(v.getOrigen());
            Aeropuerto aeroDest = dataService.getMapaAeropuertos().get(v.getDestino());

            int gmtOrig = (aeroOrig != null) ? aeroOrig.getGmt() : 0;
            int gmtDest = (aeroDest != null) ? aeroDest.getGmt() : 0;

            LocalTime salidaLocal = LocalTime.parse(v.getHoraSalida());
            LocalTime llegadaLocal = LocalTime.parse(v.getHoraLlegada());

            LocalTime salidaGMT = salidaLocal.minusHours(gmtOrig);
            LocalTime llegadaGMT = llegadaLocal.minusHours(gmtDest);

            // Uso de vuelos GLOBAL (persiste en toda la simulación)
            String idVuelo = v.getOrigen() + "-" + v.getDestino() + "-" + v.getHoraSalida();
            int ocupadoVuelo = usoVuelosGlobal.getOrDefault(idVuelo, 0) + maletas;
            usoVuelosGlobal.put(idVuelo, ocupadoVuelo);

            // Uso de almacén REAL (capacidad histórica + lo que añade este envío)
            int capAlmacenMax = (aeroOrig != null) ? aeroOrig.getCapacidad() : 0;
            int espacioLibre = capDinamicaActual.getOrDefault(v.getOrigen(), capAlmacenMax);
            int ocupacionReal = (capAlmacenMax - espacioLibre) + maletas;

            System.out.printf("   (%d) %s -> %s | Salida(GMT): %s | Llegada(GMT): %s | Vuelo: %d/%d | Almacén %s: %d/%d\n",
                paso++, v.getOrigen(), v.getDestino(),
                salidaGMT.toString(), llegadaGMT.toString(),
                ocupadoVuelo, v.getCapacidad(),
                v.getOrigen(), ocupacionReal, capAlmacenMax);
        }
    }

    private static void imprimirBitacoraFinal(List<EventoSimulacion> bitacora, Map<String, Integer> capsOrig, int total, boolean colapso, LocalDateTime relojParada, DateTimeFormatter fmt, List<Ruta> rutasHistorico) {
        
        if (!rutasHistorico.isEmpty()) {
            System.out.println("\n" + "=".repeat(100));
            System.out.println("   MAPA DE RUTAS ASIGNADAS CONSOLIDADO (TOTAL HISTÓRICO)");
            System.out.println("=".repeat(100));
            System.out.printf(" %-10s | %-7s | %-15s | %-10s | %s\n", "ENVÍO", "MALETAS", "ORIGEN->DESTINO", "TIEMPO", "ITINERARIO (ESCALAS)");
            System.out.println("-".repeat(100));
            for (Ruta r : rutasHistorico) {
                String origDest = r.getEnvio().getAeropuertoOrigen() + " -> " + r.getEnvio().getAeropuertoDestino();
                String tiempoStr = String.format("%dh %02dm", r.getTiempoTotalMinutos() / 60, r.getTiempoTotalMinutos() % 60);
                String itinerario = r.getVuelos().stream()
                        .map(v -> v.getOrigen() + "->" + v.getDestino())
                        .collect(Collectors.joining(", "));
                
                System.out.printf(" %-10s | %-7d | %-15s | %-10s | [%s]\n", 
                        r.getEnvio().getIdEnvio(), 
                        r.getEnvio().getCantidadMaletas(), 
                        origDest, 
                        tiempoStr, 
                        itinerario);
            }
        }

        System.out.println("\n\n" + "=".repeat(80));
        System.out.println("       BITACORA CRONOLOGICA DE EVENTOS (TODOS LOS ALMACENES)");
        System.out.println("=".repeat(80));
        
        bitacora.sort(Comparator.comparing(e -> e.tiempoGMT));
        Map<String, Integer> capsDin = new HashMap<>(capsOrig);

        for (EventoSimulacion e : bitacora) {
            int nueva = capsDin.getOrDefault(e.aeropuerto, 0) + e.variacionCapacidad;
            capsDin.put(e.aeropuerto, nueva);
            int ocupado = capsOrig.get(e.aeropuerto) - nueva;
            System.out.printf("[%s GMT] %-35s | %s %d | Almacen %s: %d/%d\n",
                e.tiempoGMT.format(fmt), e.mensaje,
                (e.variacionCapacidad > 0 ? "LIBERA" : "OCUPA "),
                Math.abs(e.variacionCapacidad),
                e.aeropuerto, ocupado, capsOrig.get(e.aeropuerto));
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println(" RESUMEN DEL ESCENARIO");
        System.out.println(" - Envios unicos exitosamente planificados: " + total);
        if (colapso) {
            System.out.println(" - [!] ESTADO: COLAPSO. El sistema se detuvo en: " + relojParada.format(fmt));
        } else {
            System.out.println(" - [OK] La simulacion completo su periodo exitosamente.");
        }
        System.out.println("=".repeat(80) + "\n");
    }
}