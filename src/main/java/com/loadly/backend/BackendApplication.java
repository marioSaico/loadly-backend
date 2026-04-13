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
        // Descomenta el escenario que desees ejecutar. (Se recomienda ejecutar uno a la vez).
        // =========================================================================================

        // 1️⃣ ESCENARIO: DÍA A DÍA (Ejemplo: Simula 1 día. Lee cada 10 min lo de los últimos 10 min)
        ejecutarEscenario("DÍA A DÍA", "20260101-00-00", "20260102-00-00", 10, 10, 50, 100, planificador, dataService);

        // 2️⃣ ESCENARIO: PERIODO 5 DÍAS (Ejemplo: Simula 5 días. Lee de golpe bloques grandes para optimizar)
        // ejecutarEscenario("PERIODO (5 DÍAS)", "20260101-00-00", "20260105-23-59", 120, 60, planificador, dataService);

        // 3️⃣ ESCENARIO: COLAPSO (Ejemplo: Loop largo con sobrecarga de datos Sc >> Sa)
        // ejecutarEscenario("COLAPSO", "20260101-00-00", "20260130-00-00", 120, 10, planificador, dataService);
    }

    public static void ejecutarEscenario(String nombre, String inicioStr, String finStr, int sc, int sa, int tamanoPoblacion, int maxGeneraciones, 
                                        Planificador planificador, DataService dataService) {
        
        DateTimeFormatter fmtInput = DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");
        DateTimeFormatter fmtLog = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");
        
        LocalDateTime relojSimulado = LocalDateTime.parse(inicioStr, fmtInput);
        LocalDateTime finSimulacion = LocalDateTime.parse(finStr, fmtInput);

        System.out.println("\n" + "=".repeat(80));
        System.out.println(" 📊 INICIANDO ESCENARIO: " + nombre);
        System.out.println(" ⚙️  Parámetros -> Salto Consumo (Sc): " + sc + " min | Salto Reloj (Sa): " + sa + " min");
        long diasSimulacion = ChronoUnit.DAYS.between(relojSimulado, finSimulacion);
        System.out.println(" ⏱️  Tiempo a simular: " + diasSimulacion + " días (De " + inicioStr + " a " + finStr + ")");
        System.out.println("=".repeat(80));

        Map<String, Integer> capacidadOriginal = dataService.getAeropuertos().stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getCapacidad));
        Map<String, Integer> mapaGmt = dataService.getAeropuertos().stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getGmt));
        
        List<EventoSimulacion> bitacoraGlobal = new ArrayList<>();
        int totalEnviosProcesados = 0;
        boolean colapsoDetectado = false;

        // --- BUCLE PRINCIPAL DE SIMULACIÓN ---
        while (relojSimulado.isBefore(finSimulacion) && !colapsoDetectado) {
            
            // 💡 EL CORAZÓN DE LA TEORÍA: El algoritmo lee datos hasta 'Sc' minutos en el futuro
            LocalDateTime limiteLecturaDatos = relojSimulado.plusMinutes(sc);
            String limiteLecturaStr = limiteLecturaDatos.format(fmtInput);
            
            System.out.println("\n>>> [RELOJ: " + relojSimulado.format(fmtLog) + "] Planificando pedidos hasta las " + limiteLecturaDatos.format(fmtLog) + "...");

            // Llamamos a la IA pasándole el límite de lectura
            Individuo resultado = planificador.planificar(limiteLecturaStr, tamanoPoblacion, maxGeneraciones);

            if (resultado != null && !resultado.getRutas().isEmpty()) {
                
                // ⚠️ VERIFICACIÓN DE COLAPSO: ¿Hay maletas que la IA no pudo asignar?
                long maletasSinRuta = resultado.getRutas().stream().filter(r -> r.getEstado() == EstadoRuta.SIN_RUTA).count();
                if (maletasSinRuta > 0) {
                    System.out.println("    [!] ¡ALERTA DE COLAPSO! El sistema no pudo encontrar ruta para " + maletasSinRuta + " envíos.");
                    colapsoDetectado = true;
                    // Rompe el bucle si es el escenario de colapso
                }

                for (Ruta ruta : resultado.getRutas()) {
                    if (ruta.getEstado() == EstadoRuta.PLANIFICADA) {
                        totalEnviosProcesados++;
                        procesarRutaEnBitacora(ruta, mapaGmt, bitacoraGlobal);
                    }
                }
                System.out.println("    [OK] " + resultado.getRutas().size() + " envíos procesados en esta iteración.");
            } else {
                System.out.println("    [-] No hay envíos nuevos en este intervalo.");
            }

            // 💡 EL CORAZÓN DE LA TEORÍA: Terminada la ejecución, el reloj avanza 'Sa' minutos
            relojSimulado = relojSimulado.plusMinutes(sa);
        }

        // --- REPORTE FINAL DEL ESCENARIO ---
        imprimirBitacoraFinal(bitacoraGlobal, capacidadOriginal, totalEnviosProcesados, colapsoDetectado, relojSimulado, fmtLog);
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
        bitacora.add(new EventoSimulacion(regGMT, envio.getAeropuertoOrigen(), -envio.getCantidadMaletas(), "[CHECK-IN]   Envío " + envio.getIdEnvio()));

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
        bitacora.add(new EventoSimulacion(finGMT, envio.getAeropuertoDestino(), envio.getCantidadMaletas(), "[RECOJO]     Envío " + envio.getIdEnvio()));
    }

    private static void imprimirBitacoraFinal(List<EventoSimulacion> bitacora, Map<String, Integer> capsOrig, int total, boolean colapso, LocalDateTime relojParada, DateTimeFormatter fmt) {
        System.out.println("\n\n" + "=".repeat(80));
        System.out.println("       BITÁCORA CRONOLÓGICA DE ALMACENES - RESULTADO FINAL");
        System.out.println("=".repeat(80));
        
        bitacora.sort(Comparator.comparing(e -> e.tiempoGMT));
        Map<String, Integer> capsDin = new HashMap<>(capsOrig);

        for (EventoSimulacion e : bitacora) {
            int nueva = capsDin.get(e.aeropuerto) + e.variacionCapacidad;
            capsDin.put(e.aeropuerto, nueva);
            System.out.printf("[%s GMT] %-35s | %s %d | Almacén %s: %d/%d\n",
                    e.tiempoGMT.format(fmt), e.mensaje, (e.variacionCapacidad > 0 ? "LIBERA":"OCUPA "), 
                    Math.abs(e.variacionCapacidad), e.aeropuerto, nueva, capsOrig.get(e.aeropuerto));
        }

        System.out.println("\n" + "=".repeat(80));
        System.out.println(" RESUMEN DEL ESCENARIO");
        System.out.println(" - Envíos exitosamente planificados y entregados: " + total);
        if (colapso) {
            System.out.println(" - [!] EL SISTEMA COLAPSÓ en el instante: " + relojParada.format(fmt));
        } else {
            System.out.println(" - [OK] La simulación completó su periodo sin colapsos.");
        }
        System.out.println("=".repeat(80) + "\n");
    }
}