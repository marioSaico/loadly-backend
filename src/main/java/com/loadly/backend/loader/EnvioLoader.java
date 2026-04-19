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

    public List<Envio> cargarPendientes(String rutaCarpeta, String fechaHoraLimiteStr, List<Aeropuerto> aeropuertos) {
        List<Envio> enviosPendientes = new ArrayList<>();
        File carpeta = new File(rutaCarpeta);

        Map<String, Integer> mapaGmt = aeropuertos.stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getGmt));

        String[] limitePartes = fechaHoraLimiteStr.split("-");
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate limiteDate = LocalDate.parse(limitePartes[0], dateFmt);
        int limiteHora = Integer.parseInt(limitePartes[1]);
        int limiteMin = Integer.parseInt(limitePartes[2]);
        
        LocalDateTime relojGlobalGMT = LocalDateTime.of(limiteDate, LocalTime.of(limiteHora, limiteMin));

        File[] archivos = carpeta.listFiles(
            (dir, nombre) -> nombre.startsWith("_envios_") && nombre.endsWith("_.txt")
        );

        if (archivos == null) return enviosPendientes;

        for (File archivo : archivos) {
            String nombreArchivo = archivo.getName();
            String codigoOrigen = nombreArchivo.replace("_envios_", "").replace("_.txt", "");
            int gmtOrigen = mapaGmt.getOrDefault(codigoOrigen, 0);

            // 🚀 Recuperamos la línea en la que nos quedamos la última vez (por defecto 0)
            int lineaInicio = cursorLineasPorArchivo.getOrDefault(nombreArchivo, 0);
            int lineasLeidasEnEstaRonda = 0;

            try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                String linea;
                int numeroLineaActual = 0;

                while ((linea = br.readLine()) != null) {
                    numeroLineaActual++;

                    // 🚀 Saltamos las líneas que ya procesamos en intervalos de tiempo anteriores
                    if (numeroLineaActual <= lineaInicio) {
                        continue;
                    }

                    linea = linea.trim();
                    if (linea.isEmpty()) {
                        lineasLeidasEnEstaRonda++; // Contamos las vacías también para no volver a leerlas
                        continue;
                    }

                    String[] campos = linea.split("-");
                    
                    LocalDate fechaLocal = LocalDate.parse(campos[1], dateFmt);
                    int horaLocal = Integer.parseInt(campos[2]);
                    int minLocal = Integer.parseInt(campos[3]);
                    LocalDateTime tiempoLocal = LocalDateTime.of(fechaLocal, LocalTime.of(horaLocal, minLocal));
                    LocalDateTime tiempoGMT = tiempoLocal.minusHours(gmtOrigen);

                    //  LÓGICA DE FRENO: Si encontramos un envío del futuro, paramos de leer este archivo
                    if (tiempoGMT.isAfter(relojGlobalGMT) || tiempoGMT.isEqual(relojGlobalGMT)) {
                        break; // Salimos del while de lectura de este archivo en particular
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
                
                //  Guardamos en la libreta hasta qué línea avanzamos para el siguiente salto de reloj
                cursorLineasPorArchivo.put(nombreArchivo, lineaInicio + lineasLeidasEnEstaRonda);

            } catch (Exception e) {
                System.err.println("Error leyendo " + archivo.getName() + ": " + e.getMessage());
            }
        }
        // Al final de cargarPendientes, antes del return:
        enviosPendientes.sort(Comparator.comparing(envio -> {
            LocalDate fecha = LocalDate.parse(envio.getFechaRegistro(), dateFmt);
            LocalDateTime tiempoLocal = LocalDateTime.of(fecha, 
                LocalTime.of(envio.getHoraRegistro(), envio.getMinutoRegistro()));
            int gmt = mapaGmt.getOrDefault(envio.getAeropuertoOrigen(), 0);
            return tiempoLocal.minusHours(gmt);
        }));
        return enviosPendientes;
    }
}