package com.loadly.backend.loader;

import com.loadly.backend.model.Aeropuerto; // 💡 NUEVO
import com.loadly.backend.model.Envio;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.time.LocalDate; // 💡 NUEVO
import java.time.LocalDateTime; // 💡 NUEVO
import java.time.LocalTime; // 💡 NUEVO
import java.time.format.DateTimeFormatter; // 💡 NUEVO
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class EnvioLoader {

    // 💡 NUEVO: Agregamos el parámetro List<Aeropuerto>
    public List<Envio> cargarPendientes(String rutaCarpeta, String fechaHoraLimiteStr, List<Aeropuerto> aeropuertos) {
        List<Envio> enviosPendientes = new ArrayList<>();
        File carpeta = new File(rutaCarpeta);

        // 💡 NUEVO 1: Crear un "diccionario" rápido para saber el GMT de cada aeropuerto
        Map<String, Integer> mapaGmt = aeropuertos.stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, Aeropuerto::getGmt));

        // 💡 NUEVO 2: Convertimos tu string "20260102-01-10" a un Objeto de Fecha Real en Java
        String[] limitePartes = fechaHoraLimiteStr.split("-");
        DateTimeFormatter dateFmt = DateTimeFormatter.ofPattern("yyyyMMdd");
        LocalDate limiteDate = LocalDate.parse(limitePartes[0], dateFmt);
        int limiteHora = Integer.parseInt(limitePartes[1]);
        int limiteMin = Integer.parseInt(limitePartes[2]);
        
        // Este es nuestro Reloj Global
        LocalDateTime relojGlobalGMT = LocalDateTime.of(limiteDate, LocalTime.of(limiteHora, limiteMin));

        File[] archivos = carpeta.listFiles(
            (dir, nombre) -> nombre.startsWith("_envios_") && nombre.endsWith("_.txt")
        );

        if (archivos == null) return enviosPendientes;

        for (File archivo : archivos) {
            String nombreArchivo = archivo.getName();
            String codigoOrigen = nombreArchivo.replace("_envios_", "").replace("_.txt", "");

            // Buscamos el GMT de este archivo en particular
            int gmtOrigen = mapaGmt.getOrDefault(codigoOrigen, 0);

            try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    linea = linea.trim();
                    if (linea.isEmpty()) continue;

                    String[] campos = linea.split("-");
                    
                    // 💡 NUEVO 3: Reconstruimos la hora LOCAL en la que el cliente dejó la maleta
                    LocalDate fechaLocal = LocalDate.parse(campos[1], dateFmt);
                    int horaLocal = Integer.parseInt(campos[2]);
                    int minLocal = Integer.parseInt(campos[3]);
                    LocalDateTime tiempoLocal = LocalDateTime.of(fechaLocal, LocalTime.of(horaLocal, minLocal));

                    // 💡 NUEVO 4: Convertimos la hora Local a hora Global (GMT)
                    // ¿Cómo? Restándole sus horas de ventaja. 
                    // Ej: Lima (10:00 GMT-5) -> 10:00 - (-5) = 15:00 en GMT.
                    LocalDateTime tiempoGMT = tiempoLocal.minusHours(gmtOrigen);

                    // 💡 NUEVO 5: Ahora sí comparamos peras con peras (GMT vs GMT)
                    // Si el cliente llegó DESPUÉS de que nuestro Reloj Global haya pasado, lo ignoramos
                    if (tiempoGMT.isAfter(relojGlobalGMT)) {
                        continue; // Es del futuro para la simulación, no lo cargamos todavía
                    }

                    // Si pasó el filtro, lo armamos y lo mandamos a planificar
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
                }
            } catch (Exception e) {
                System.err.println("Error leyendo " + archivo.getName() + ": " + e.getMessage());
            }
        }

        return enviosPendientes;
    }
}