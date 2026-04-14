package com.loadly.backend;

import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.model.*;
import com.loadly.backend.planificador.Planificador;
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
        Planificador planificador = context.getBean(Planificador.class);
        DataService dataService = context.getBean(DataService.class);

        // =========================================================================================
        // 🚀 PANEL DE CONTROL DE ESCENARIOS
        // Parametros: (Nombre, Inicio, Fin, Ta(segundos), Sa(min), K, TamanoPoblacion, planificador, dataService)
        // =========================================================================================

        // 1️⃣ ESCENARIO: DIA A DIA
        // Ta = 2 segundos reales de procesamiento por iteracion
        // Sa = 10 minutos (cada 10 min despierta la IA)
        // K = 1 (Por lo tanto, Sc = 10 minutos de datos a leer)
        // NOTA: Fecha fin ajustada a 23:59 para simular exactamente 1 dia.
        //ejecutarEscenario("DIA A DIA", "20260101-00-00", "20260102-00-00", 2, 10, 1, 50, planificador, dataService);

        // 2️⃣ ESCENARIO: PERIODO 5 DIAS
        // Configuracion matematica: Ta=30s | Sa=60min | K=24 | Poblacion=300
        ejecutarEscenario("PERIODO (5 DIAS)", "20260101-00-00", "20260106-00-00", 45, 60, 24, 100, planificador, dataService);

        // 3️⃣ ESCENARIO: COLAPSO (Ejemplo)
        // Ta = 3 segundos, Sa = 10 minutos, K = 50 (Sobrecarga extrema de lectura para forzar el colapso)
        // ejecutarEscenario("COLAPSO", "20260101-00-00", "20260130-23-59", 3, 10, 50, 100, planificador, dataService);
    }

    public static void ejecutarEscenario(String nombre, String inicioStr, String finStr, 
                                        int taSegundos, int sa, int k, int tamanoPoblacion, 
                                        Planificador planificador, DataService dataService) {
        
        DateTimeFormatter fmtInput = DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");
        DateTimeFormatter fmtLog = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        
        LocalDateTime relojSimulado = LocalDateTime.parse(inicioStr, fmtInput);
        LocalDateTime finSimulacion = LocalDateTime.parse(finStr, fmtInput);
        
        // CÁLCULOS TEÓRICOS
        int sc = sa * k; // Salto de consumo
        long tiempoLimiteMs = taSegundos * 1000L; // Ta convertido a milisegundos para el cronometro

        System.out.println("\n" + "=".repeat(80));
        System.out.println("   INICIANDO ESCENARIO: " + nombre);
        System.out.println("   Parametros -> Ta: " + taSegundos + "s | Sa: " + sa + " min | K: " + k + " | Sc: " + sc + " min");
        
        // 💡 CORRECCIÓN: Sumamos 1 para que el calculo de dias (ej. 01 al 05) de exactamente 5
        long diasSimulacion = ChronoUnit.DAYS.between(relojSimulado, finSimulacion);
        System.out.println("   Tiempo a simular: " + diasSimulacion + " dias (De " + inicioStr + " a " + finStr + ")");
        System.out.println("=".repeat(80));

        Map<String, Integer> capacidadOriginal = dataService.getAeropuertos().stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getCapacidad));
        Map<String, Integer> mapaGmt = dataService.getAeropuertos().stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getGmt));
        
        List<EventoSimulacion> bitacoraGlobal = new ArrayList<>();
        boolean colapsoDetectado = false;
        
        Individuo mejorPlanGlobal = null;

        // --- BUCLE PRINCIPAL DE SIMULACION ---
        while (relojSimulado.isBefore(finSimulacion) && !colapsoDetectado) {
            
            LocalDateTime limiteLecturaDatos = relojSimulado.plusMinutes(sc);
            String limiteLecturaStr = limiteLecturaDatos.format(fmtInput);
            
            System.out.println("\n>>> [RELOJ: " + relojSimulado.format(fmtLog) + "] Planificando pedidos acumulados hasta las " + limiteLecturaDatos.format(fmtLog) + "...");

            Individuo resultado = planificador.planificar(limiteLecturaStr, tamanoPoblacion, tiempoLimiteMs);

            if (resultado != null && !resultado.getRutas().isEmpty()) {
                
                mejorPlanGlobal = resultado;

                // ⚠️ VERIFICACION DE COLAPSO
                long maletasSinRuta = resultado.getRutas().stream().filter(r -> r.getEstado() == EstadoRuta.SIN_RUTA).count();
                if (maletasSinRuta > 0) {
                    System.out.println("    [!] ¡ALERTA DE COLAPSO! El sistema no pudo encontrar ruta para " + maletasSinRuta + " envios.");
                    colapsoDetectado = true;
                    // 💡 CORRECCIÓN: Rompemos el bucle inmediatamente para que el reloj no avance
                    break;
                } else {
                    System.out.printf("    [OK] El GA ejecutado por %ds. %d envíos asegurados. | Fitness del plan: %.6f\n", 
                                      taSegundos, resultado.getRutas().size(), resultado.getFitness());
                }
            } else {
                System.out.println("    [-] No hay envios en este intervalo.");
            }

            // El reloj avanza Sa minutos solo si no hubo colapso
            relojSimulado = relojSimulado.plusMinutes(sa);
        }

        // --- CONSTRUCCION DE LA BITACORA ---
        int totalEnviosProcesados = 0;
        if (mejorPlanGlobal != null) {
            for (Ruta ruta : mejorPlanGlobal.getRutas()) {
                if (ruta.getEstado() == EstadoRuta.PLANIFICADA) {
                    totalEnviosProcesados++;
                    procesarRutaEnBitacora(ruta, mapaGmt, bitacoraGlobal);
                }
            }
        }

        // --- REPORTE FINAL DEL ESCENARIO ---
        imprimirBitacoraFinal(bitacoraGlobal, capacidadOriginal, totalEnviosProcesados, colapsoDetectado, relojSimulado, fmtLog, mejorPlanGlobal);
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

        // 1. Registro
        bitacora.add(new EventoSimulacion(regGMT, envio.getAeropuertoOrigen(), -envio.getCantidadMaletas(), "[CHECK-IN]   Envio " + envio.getIdEnvio()));

        // 2. Vuelos
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

        // 3. Recojo
        bitacora.add(new EventoSimulacion(finGMT, envio.getAeropuertoDestino(), envio.getCantidadMaletas(), "[RECOJO]     Envio " + envio.getIdEnvio()));
    }

    private static void imprimirBitacoraFinal(List<EventoSimulacion> bitacora, Map<String, Integer> capsOrig, int total, boolean colapso, LocalDateTime relojParada, DateTimeFormatter fmt, Individuo mejorPlanGlobal) {
        
        // Bloque que imprime la ruta humana legible por cada envío
        if (mejorPlanGlobal != null && !mejorPlanGlobal.getRutas().isEmpty()) {
            System.out.println("\n" + "=".repeat(80));
            System.out.println("   MAPA DE RUTAS ASIGNADAS POR EL GA.");
            System.out.println("=".repeat(80));
            for (Ruta r : mejorPlanGlobal.getRutas()) {
                if (r.getEstado() == EstadoRuta.PLANIFICADA) {
                    String itinerario = r.getVuelos().stream()
                            .map(v -> String.format("[%s -> %s | Salida: %s | Llegada: %s]", v.getOrigen(), v.getDestino(), v.getHoraSalida(), v.getHoraLlegada()))
                            .collect(Collectors.joining(" -> "));
                    System.out.printf("  Envío %-4s (%d maletas) | Tiempo total: %d min | Ruta: %s\n", 
                                      r.getEnvio().getIdEnvio(), r.getEnvio().getCantidadMaletas(), r.getTiempoTotalMinutos(), itinerario);
                }
            }
        }

        System.out.println("\n\n" + "=".repeat(80));
        System.out.println("       BITACORA CRONOLOGICA DE ALMACENES - RESULTADO FINAL");
        System.out.println("=".repeat(80));
        
        bitacora.sort(Comparator.comparing(e -> e.tiempoGMT));
        Map<String, Integer> capsDin = new HashMap<>(capsOrig);

        for (EventoSimulacion e : bitacora) {
            int nueva = capsDin.getOrDefault(e.aeropuerto, 0) + e.variacionCapacidad;
            capsDin.put(e.aeropuerto, nueva);
            System.out.printf("[%s GMT] %-35s | %s %d | Almacen %s: %d/%d\n",
                    e.tiempoGMT.format(fmt), e.mensaje, (e.variacionCapacidad > 0 ? "LIBERA":"OCUPA "), 
                    Math.abs(e.variacionCapacidad), e.aeropuerto, nueva, capsOrig.get(e.aeropuerto));
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println(" RESUMEN DEL ESCENARIO");
        System.out.println(" - Envios unicos exitosamente planificados: " + total);
        if (colapso) {
            System.out.println(" - [!] ESTADO: COLAPSO. El sistema se detuvo en: " + relojParada.format(fmt));
        } else {
            System.out.println(" - [OK] La simulacion completo su periodo sin colapsos.");
        }
        System.out.println("=".repeat(80) + "\n");
    }
}