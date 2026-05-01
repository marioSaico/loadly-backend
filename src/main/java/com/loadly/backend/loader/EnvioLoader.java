package com.loadly.backend.loader;

import com.loadly.backend.model.Aeropuerto;
import com.loadly.backend.model.Envio;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class EnvioLoader {

    // Memoria caché para saber en qué línea nos quedamos en cada archivo
    // Clave: Nombre del archivo, Valor: Número de línea donde nos detuvimos la última vez
    private final Map<String, Integer> cursorLineasPorArchivo = new HashMap<>();

    public List<Envio> cargarPendientes(String rutaCarpeta, String fechaInicioStr, String fechaHoraLimiteStr, List<Aeropuerto> aeropuertos) {
        List<Envio> enviosPendientes = new ArrayList<>();
        File carpeta = new File(rutaCarpeta);

        Map<String, Integer> mapaGmt = aeropuertos.stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getGmt));

        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        
        // --- PARSEO DEL LÍMITE ---
        String[] limitePartes = fechaHoraLimiteStr.split("-");
        LocalDate limiteDate = LocalDate.parse(limitePartes[0], dateFmt);
        LocalDateTime relojGlobalGMT = LocalDateTime.of(limiteDate, LocalTime.of(Integer.parseInt(limitePartes[1]), Integer.parseInt(limitePartes[2])));

        // --- PARSEO DEL INICIO (NUEVO) ---
        String[] inicioPartes = fechaInicioStr.split("-");
        LocalDate inicioDate = LocalDate.parse(inicioPartes[0], dateFmt);
        LocalDateTime relojInicioGMT = LocalDateTime.of(inicioDate, LocalTime.of(Integer.parseInt(inicioPartes[1]), Integer.parseInt(inicioPartes[2])));

        File[] archivos = carpeta.listFiles((dir, nombre) -> nombre.startsWith("_envios_") && nombre.endsWith("_.txt"));
        if (archivos == null) return enviosPendientes;

        for (File archivo : archivos) {
            String nombreArchivo = archivo.getName();
            String codigoOrigen = nombreArchivo.replace("_envios_", "").replace("_.txt", "");
            int gmtOrigen = mapaGmt.getOrDefault(codigoOrigen, 0);

            int lineaInicio = cursorLineasPorArchivo.getOrDefault(nombreArchivo, 0);
            int lineasLeidasEnEstaRonda = 0;

            try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                String linea;
                int numeroLineaActual = 0;

                while ((linea = br.readLine()) != null) {
                    numeroLineaActual++;

                    if (numeroLineaActual <= lineaInicio) {
                        continue;
                    }

                    linea = linea.trim();
                    if (linea.isEmpty()) {
                        lineasLeidasEnEstaRonda++;
                        continue;
                    }

                    String[] campos = linea.split("-");
                    LocalDate fechaLocal = LocalDate.parse(campos[1], dateFmt);
                    LocalDateTime tiempoLocal = LocalDateTime.of(fechaLocal, LocalTime.of(Integer.parseInt(campos[2]), Integer.parseInt(campos[3])));
                    LocalDateTime tiempoGMT = tiempoLocal.minusHours(gmtOrigen);

                    // 🚀 LÓGICA DE DESCARTAR BASURA ANTIGUA: 
                    // Si el paquete es de 2026 y el escenario arranca en 2027, lo saltamos pero el cursor avanza
                    if (tiempoGMT.isBefore(relojInicioGMT)) {
                        lineasLeidasEnEstaRonda++;
                        continue; 
                    }

                    // 🚀 LÓGICA DE FRENO: Si es del futuro respecto a nuestro salto actual (Sa), paramos
                    if (tiempoGMT.isAfter(relojGlobalGMT) || tiempoGMT.isEqual(relojGlobalGMT)) {
                        break; 
                    }

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

                    enviosPendientes.add(envio);
                    lineasLeidasEnEstaRonda++;
                }
                
                cursorLineasPorArchivo.put(nombreArchivo, lineaInicio + lineasLeidasEnEstaRonda);

            } catch (Exception e) {
                System.err.println("Error leyendo " + archivo.getName() + ": " + e.getMessage());
            }
        }
        
        enviosPendientes.sort(Comparator.comparing(envio -> {
            LocalDate fecha = LocalDate.parse(envio.getFechaRegistro(), dateFmt);
            LocalDateTime tiempoLocal = LocalDateTime.of(fecha, LocalTime.of(envio.getHoraRegistro(), envio.getMinutoRegistro()));
            int gmt = mapaGmt.getOrDefault(envio.getAeropuertoOrigen(), 0);
            return tiempoLocal.minusHours(gmt);
        }));
        
        return enviosPendientes;
    }
}