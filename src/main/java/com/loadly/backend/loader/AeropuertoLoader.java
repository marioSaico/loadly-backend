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
                    if (partes.length > 7) {
                        String coords = partes[7].trim();
                        String[] coordSplit = coords.split("Longitude:");
                        aeropuerto.setLatitud(
                            coordSplit[0].replace("Latitude:", "").trim()
                        );
                        if (coordSplit.length > 1) {
                            aeropuerto.setLongitud(coordSplit[1].trim());
                        }
                    }

                    aeropuertos.add(aeropuerto);

                } catch (NumberFormatException e) {
                    System.err.println("Error parseando línea: " + linea);
                }
            }
        } catch (Exception e) {
            System.err.println("Error al leer archivo de aeropuertos: " + e.getMessage());
        }

        return aeropuertos;
    }
}