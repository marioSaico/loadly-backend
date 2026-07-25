package com.loadly.backend.loader;

import com.loadly.backend.dto.EnvioDTO;
import com.loadly.backend.model.Aeropuerto;
import com.loadly.backend.model.Envio;
import com.loadly.backend.service.database.EnvioService;

import org.springframework.stereotype.Component;

import java.io.File;
import java.nio.file.Files;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Component
public class EnvioLoader {

    public final EnvioService envioService;

    public EnvioLoader(EnvioService envioService) {
        this.envioService = envioService;
    }
    
    private static final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final DateTimeFormatter FORMATO_RELOJ =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");

    // Memoria caché para saber en qué posición nos quedamos en cada lista de envíos

    private final Map<String, Integer> cursorLineasPorArchivo = new ConcurrentHashMap<>();

    private void logMemoria(String tag) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        System.out.printf("[EnvioLoader - %s] RAM Usada: %dMB | Total: %dMB%n", tag, usedMemory, totalMemory);
    }

    public List<Envio> cargarPendientes(String rutaCarpeta, String fechaInicioStr, String fechaHoraLimiteStr, List<Aeropuerto> aeropuertos) {
        String[] limitePartes = fechaHoraLimiteStr.split("-");
        LocalDate limiteDate = LocalDate.parse(limitePartes[0], dateFmt);
        LocalDateTime relojGlobalGMT = LocalDateTime.of(limiteDate, LocalTime.of(Integer.parseInt(limitePartes[1]), Integer.parseInt(limitePartes[2])));

        String[] inicioPartes = fechaInicioStr.split("-");
        LocalDate inicioDate = LocalDate.parse(inicioPartes[0], dateFmt);
        LocalDateTime relojInicioGMT = LocalDateTime.of(inicioDate, LocalTime.of(Integer.parseInt(inicioPartes[1]), Integer.parseInt(inicioPartes[2])));

        Map<String, Integer> mapaGmt = aeropuertos.stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getGmt));

        // Leemos los archivos físicos directamente desde la carpeta del disco
        File carpeta = new File(rutaCarpeta);
        File[] archivos = carpeta.listFiles((dir, name) -> name.endsWith(".txt"));

        if (archivos == null) return new ArrayList<>();

        // PROCESAMIENTO PARALELO LEYENDO DESDE DISCO
        List<Envio> enviosPendientes = Arrays.stream(archivos).parallel().flatMap(archivo -> {
            String nombreArchivo = archivo.getName();
            String codigoOrigen = nombreArchivo.replace("_envios_", "").replace("_.txt", "");
            int gmtOrigen = mapaGmt.getOrDefault(codigoOrigen, 0);

            // Obtenemos en qué línea nos quedamos la última vez que leímos este archivo
            int lineasYaLeidas = cursorLineasPorArchivo.getOrDefault(nombreArchivo, 0);
            List<Envio> filtrados = new ArrayList<>();
            int nuevasLineasConsumidas = 0;

            // Abrimos un flujo de lectura directo al archivo en el disco duro
            try (Stream<String> stream = Files.lines(archivo.toPath())) {
                
                // MAGIA: Saltamos rápidamente las líneas que ya procesamos en bloques anteriores
                Iterator<String> iterator = stream.skip(lineasYaLeidas).iterator();

                while (iterator.hasNext()) {
                    String linea = iterator.next().trim();
                    nuevasLineasConsumidas++; // Avanzamos el cursor de líneas
                    
                    if (linea.isEmpty()) continue;

                    try {
                        String[] campos = linea.split("-");
                        LocalDate fechaLocal = LocalDate.parse(campos[1], dateFmt);
                        LocalDateTime tiempoLocal = LocalDateTime.of(fechaLocal, LocalTime.of(Integer.parseInt(campos[2]), Integer.parseInt(campos[3])));
                        LocalDateTime tiempoGMT = tiempoLocal.minusHours(gmtOrigen);

                        // Si es anterior al inicio absoluto del escenario, lo ignoramos (pero ya consumimos la línea)
                        if (tiempoGMT.isBefore(relojInicioGMT)) {
                            continue; 
                        }

                        // Si este registro ya es del futuro (mayor al reloj actual del bloque), PARAMOS
                        if (tiempoGMT.isAfter(relojGlobalGMT) || tiempoGMT.isEqual(relojGlobalGMT)) {
                            // ¡CLAVE! Como leímos esta línea pero no entra en este bloque, 
                            // le restamos 1 al contador para volver a leerla en la próxima llamada
                            nuevasLineasConsumidas--;
                            break; 
                        }

                        // Está en nuestro bloque de 4 horas. Lo instanciamos.
                        Envio envio = new Envio();
                        envio.setIdEnvio(campos[0]);
                        envio.setFechaRegistro(campos[1]);
                        envio.setHoraRegistro(Integer.parseInt(campos[2]));
                        envio.setMinutoRegistro(Integer.parseInt(campos[3]));
                        envio.setAeropuertoOrigen(codigoOrigen);
                        envio.setAeropuertoDestino(campos[4]);
                        envio.setCantidadMaletas(Integer.parseInt(campos[5]));
                        envio.setIdCliente(campos[6]);
                        envio.setPlanificado(false);
                        envio.setTiempoRegistroGMT(tiempoGMT);

                        filtrados.add(envio);
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // Actualizamos el cursor para la próxima vez que el planificador pida envíos
            cursorLineasPorArchivo.put(nombreArchivo, lineasYaLeidas + nuevasLineasConsumidas);
            
            return filtrados.stream();
        }).collect(Collectors.toList());
        
        // Garantizar orden total para el motor de planificación
        enviosPendientes.sort(Comparator.comparing(Envio::getTiempoRegistroGMT));
        
        logMemoria("Bloque procesado desde disco");
        return enviosPendientes;
    }

    public void reset() {
        cursorLineasPorArchivo.clear();
    }

    public List<Envio> obtenerEnviosPendientesDesdeBD(String fechaHoraActual, List<Aeropuerto> aeropuertos,
                                                    Set<String> idsYaEnMemoria) {
        List<Envio> enviosEnEspera = new ArrayList<>();
        LocalDateTime hasta = LocalDateTime.parse(fechaHoraActual, FORMATO_RELOJ);
        List<EnvioDTO> enviosNuevos = envioService.obtenerNoPlanificadosHasta(hasta);

        Map<Integer, Aeropuerto> aeropuertosPorId = aeropuertos.stream()
                .collect(Collectors.toMap(Aeropuerto::getId, aeropuerto -> aeropuerto));

        for (EnvioDTO dto : enviosNuevos) {
            String idEnvio = String.valueOf(dto.getIdArchivo());
            if (idsYaEnMemoria.contains(idEnvio)) continue;   // ← evita duplicar un envío ya en cola

            Envio envio = convertirEnvioDesdeBD(dto, aeropuertosPorId);
            if (envio != null) {
                enviosEnEspera.add(envio);
            }
        }

        enviosEnEspera.sort(Comparator.comparing(Envio::getTiempoRegistroGMT));
        return new ArrayList<>(enviosEnEspera);
    }

    /**
     * Limpia absolutamente toda la memoria de envíos cargados.
     */
    public void limpiarTodo() {
        this.cursorLineasPorArchivo.clear();
        System.gc(); 
        logMemoria("Limpieza de cursores finalizada");
    }
    private Envio convertirEnvioDesdeBD(EnvioDTO dto, Map<Integer, Aeropuerto> aeropuertosPorId) {
        Aeropuerto origen = aeropuertosPorId.get(dto.getIdAeropuertoOrigen());
        Aeropuerto destino = aeropuertosPorId.get(dto.getIdAeropuertoDestino());
        if (dto.getFechaRegistro() == null || origen == null || destino == null) return null;

        Envio envio = new Envio();
        envio.setIdEnvio(String.valueOf(dto.getIdArchivo()));
        envio.setFechaRegistro(dto.getFechaRegistro().toLocalDate().format(DateTimeFormatter.ofPattern("yyyyMMdd")));
        envio.setHoraRegistro(dto.getFechaRegistro().getHour());
        envio.setMinutoRegistro(dto.getFechaRegistro().getMinute());
        envio.setAeropuertoOrigen(origen.getCodigo());
        envio.setAeropuertoDestino(destino.getCodigo());
        envio.setCantidadMaletas(dto.getCantidadMaletas());
        envio.setIdCliente(String.valueOf(dto.getClienteIdCliente()));
        envio.setPlanificado(Boolean.TRUE.equals(dto.getPlanificado()));
        envio.setTiempoRegistroGMT(dto.getFechaRegistro().minusHours(origen.getGmt()));
        return envio;
    }

}