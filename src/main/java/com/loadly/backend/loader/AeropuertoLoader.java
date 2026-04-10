package com.loadly.backend.loader;

import com.loadly.backend.model.Aeropuerto;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

@Component
public class AeropuertoLoader {

    public List<Aeropuerto> cargar(String rutaArchivo) {
        List<Aeropuerto> aeropuertos = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {

                // Eliminar BOM y caracteres extraños al inicio
                linea = linea.replace("\uFEFF", "")
                             .replace("\u0000", "")
                             .trim();

                if (linea.isEmpty()) continue;

                // Ignorar líneas que no son datos
                if (!Character.isDigit(linea.charAt(0))) continue;

                // Separar por múltiples espacios
                String[] partes = linea.trim().split("\\s{2,}");

                if (partes.length < 7) continue;

                try {
                    Aeropuerto aeropuerto = new Aeropuerto();
                    aeropuerto.setId(Integer.parseInt(partes[0].trim()));
                    aeropuerto.setCodigo(partes[1].trim());
                    aeropuerto.setCiudad(partes[2].trim());
                    aeropuerto.setPais(partes[3].trim());
                    aeropuerto.setAbreviatura(partes[4].trim());
                    aeropuerto.setGmt(Integer.parseInt(partes[5].trim()));
                    aeropuerto.setCapacidad(Integer.parseInt(partes[6].trim()));

                    // Latitud y longitud
                    if (linea.contains("Latitude:")) {

                        String latStr = linea.split("Latitude:")[1].split("Longitude:")[0].trim();
                        double lat = convertirDecimal(latStr);
                        aeropuerto.setLatitud(lat);

                        if (linea.contains("Longitude:")) {
                            String lonStr = linea.split("Longitude:")[1].trim();
                            double lon = convertirDecimal(lonStr);
                            aeropuerto.setLongitud(lon);
                        }
                    }

                    aeropuertos.add(aeropuerto);

                } catch (Exception e) {
                    System.err.println("Error en línea: " + linea);
                    System.err.println("Motivo: " + e.getMessage());
                }
            }
        } catch (Exception e) {
            System.err.println("Error al leer archivo de aeropuertos: " + e.getMessage());
        }

        return aeropuertos;
    }

    private double convertirDecimal(String coord) {

        try {
            // 🔥 extraer SOLO números
            java.util.regex.Matcher m = java.util.regex.Pattern
                    .compile("(\\d+)")
                    .matcher(coord);

            double grados = 0, minutos = 0, segundos = 0;

            if (m.find()) grados = Double.parseDouble(m.group());
            if (m.find()) minutos = Double.parseDouble(m.group());
            if (m.find()) segundos = Double.parseDouble(m.group());

            // 🔹 dirección (última letra válida)
            char direccion = coord.toUpperCase().replaceAll("[^NSEW]", "")
                                                .charAt(0);

            double decimal = grados + (minutos / 60.0) + (segundos / 3600.0);

            if (direccion == 'S' || direccion == 'W') {
                decimal *= -1;
            }

            return decimal;

        } catch (Exception e) {
            throw new RuntimeException("Error convirtiendo: " + coord);
        }
    }
}