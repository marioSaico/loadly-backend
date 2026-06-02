package com.loadly.backend.loader;

import com.loadly.backend.model.Aeropuerto;
import com.loadly.backend.model.Envio;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Component
public class EnvioLoader {

    private static final DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd");

    // Memoria caché para saber en qué posición nos quedamos en cada lista de envíos
    private final Map<String, Integer> cursorEnviosPorArchivo = new ConcurrentHashMap<>();
    
    private final Map<String, List<Envio>> enviosPorArchivo = new ConcurrentHashMap<>();

    private void logMemoria(String tag) {
        Runtime runtime = Runtime.getRuntime();
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;
        System.out.printf("[EnvioLoader - %s] RAM Usada: %dMB | Total: %dMB%n", tag, usedMemory, totalMemory);
    }

    /**
     * Carga y parsea archivos en memoria de forma optimizada y en paralelo.
     */
    public void setArchivosEnMemoriaFiltrados(MultipartFile[] archivos, List<Aeropuerto> aeropuertos, 
                                          LocalDateTime rangoInicioGMT, LocalDateTime rangoFinGMT) {
        logMemoria("ANTES de parsear filtrado");
        this.enviosPorArchivo.clear();
        this.cursorEnviosPorArchivo.clear();

        Map<String, Integer> mapaGmt = aeropuertos.stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getGmt));

        // Procesamos en paralelo para usar todos los núcleos del procesador
        Arrays.stream(archivos).parallel().forEach(archivo -> {
            String nombreArchivo = archivo.getOriginalFilename();
            if (nombreArchivo == null) return;
            
            String codigoOrigen = nombreArchivo.replace("_envios_", "").replace("_.txt", "");
            int gmtOrigen = mapaGmt.getOrDefault(codigoOrigen, 0);
            List<Envio> listaEnvios = new ArrayList<>();

            try (BufferedReader br = new BufferedReader(new InputStreamReader(archivo.getInputStream()))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    linea = linea.trim();
                    if (linea.isEmpty()) continue;

                    try {
                        String[] campos = linea.split("-");
                        
                        // --- OPTIMIZACIÓN CRÍTICA: Calcular fecha antes de crear todo el objeto ---
                        LocalDate fechaLocal = LocalDate.parse(campos[1], dateFmt);
                        LocalDateTime tiempoLocal = LocalDateTime.of(fechaLocal, LocalTime.of(Integer.parseInt(campos[2]), Integer.parseInt(campos[3])));
                        LocalDateTime tiempoGMT = tiempoLocal.minusHours(gmtOrigen);

                        // SI NO ESTÁ EN EL RANGO DE LOS 5 DÍAS, SE IGNORA DE INMEDIATO
                        if (tiempoGMT.isBefore(rangoInicioGMT) || tiempoGMT.isAfter(rangoFinGMT)) {
                            continue; // Salta a la siguiente línea del archivo de texto, liberando este String
                        }

                        // Si pasó el filtro, recién creamos el objeto Envio y lo guardamos
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

                        listaEnvios.add(envio);
                    } catch (Exception ignored) {}
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            
            // Ordenamos solo los envíos que sí entraron en la simulación
            listaEnvios.sort(Comparator.comparing(Envio::getTiempoRegistroGMT));
            this.enviosPorArchivo.put(nombreArchivo, listaEnvios);
        });
        
        logMemoria("DESPUÉS de parsear filtrado");
    }

    public List<Envio> cargarPendientes(String rutaCarpeta, String fechaInicioStr, String fechaHoraLimiteStr, List<Aeropuerto> aeropuertos) {
        // --- PARSEO DE FECHAS LÍMITE ---
        String[] limitePartes = fechaHoraLimiteStr.split("-");
        LocalDate limiteDate = LocalDate.parse(limitePartes[0], dateFmt);
        LocalDateTime relojGlobalGMT = LocalDateTime.of(limiteDate, LocalTime.of(Integer.parseInt(limitePartes[1]), Integer.parseInt(limitePartes[2])));

        String[] inicioPartes = fechaInicioStr.split("-");
        LocalDate inicioDate = LocalDate.parse(inicioPartes[0], dateFmt);
        LocalDateTime relojInicioGMT = LocalDateTime.of(inicioDate, LocalTime.of(Integer.parseInt(inicioPartes[1]), Integer.parseInt(inicioPartes[2])));

        // PROCESAMIENTO PARALELO: Filtramos los envíos que caen en la ventana de tiempo actual
        List<Envio> enviosPendientes = enviosPorArchivo.entrySet().parallelStream()
            .flatMap(entry -> {
                String nombreArchivo = entry.getKey();
                List<Envio> envios = entry.getValue();
                
                int cursorIdx = cursorEnviosPorArchivo.getOrDefault(nombreArchivo, 0);
                List<Envio> filtrados = new ArrayList<>();
                int nuevosConsumidos = 0;

                for (int i = cursorIdx; i < envios.size(); i++) {
                    Envio e = envios.get(i);
                    LocalDateTime tGmt = e.getTiempoRegistroGMT();

                    // Descartar si es anterior al inicio del escenario
                    if (tGmt.isBefore(relojInicioGMT)) {
                        nuevosConsumidos++;
                        continue; 
                    }

                    // Si ya llegamos al tiempo del reloj simulado actual, paramos para este archivo
                    if (tGmt.isAfter(relojGlobalGMT) || tGmt.isEqual(relojGlobalGMT)) {
                        break; 
                    }

                    filtrados.add(e);
                    nuevosConsumidos++;
                }
                
                // Actualizar cursor de forma atómica para no volver a procesar estos envíos
                cursorEnviosPorArchivo.put(nombreArchivo, cursorIdx + nuevosConsumidos);
                
                // --- OPTIMIZACIÓN: Limpieza de RAM ---
                // Si ya procesamos una cantidad considerable del archivo, removemos los elementos de la lista
                if (nuevosConsumidos > 0) {
                    List<Envio> listaOriginal = enviosPorArchivo.get(nombreArchivo);
                    if (listaOriginal != null && cursorIdx + nuevosConsumidos > 0) {
                        // Creamos una nueva sublista con lo que falta para liberar los objetos viejos
                        // Esto ayuda al GC a recolectar los Envíos que ya "pasaron" en el tiempo
                        int totalProcesados = cursorIdx + nuevosConsumidos;
                        if (totalProcesados < listaOriginal.size()) {
                            List<Envio> restante = new ArrayList<>(listaOriginal.subList(totalProcesados, listaOriginal.size()));
                            enviosPorArchivo.put(nombreArchivo, restante);
                            cursorEnviosPorArchivo.put(nombreArchivo, 0); // Reset cursor porque la lista ahora empieza de 0
                        } else {
                            enviosPorArchivo.remove(nombreArchivo);
                            cursorEnviosPorArchivo.remove(nombreArchivo);
                        }
                        logMemoria("Limpieza RAM - Archivo: " + nombreArchivo + " | Quitados: " + nuevosConsumidos);
                    }
                }

                return filtrados.stream();
            })
            .collect(Collectors.toList());
        
        // Garantizar orden total para el motor de planificación
        enviosPendientes.sort(Comparator.comparing(Envio::getTiempoRegistroGMT));
        return enviosPendientes;
    }

    public void reset() {
        cursorEnviosPorArchivo.clear();
    }

    /**
     * Limpia absolutamente toda la memoria de envíos cargados.
     */
    public void limpiarTodo() {
        this.enviosPorArchivo.clear();
        this.cursorEnviosPorArchivo.clear();
        System.gc(); // Sugerir limpieza inmediata
        logMemoria("Limpieza total");
    }
}