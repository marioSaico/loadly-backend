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
                linea = linea.trim();
                if (linea.isEmpty() || linea.startsWith("*") || 
                    linea.startsWith("America") || linea.startsWith("Europa") || 
                    linea.startsWith("Asia") || linea.startsWith("PDDS")) {
                    continue;
                }

                String[] partes = linea.trim().split("\\s+");
                if (partes.length < 7) continue;

                Aeropuerto aeropuerto = new Aeropuerto();
                aeropuerto.setId(Integer.parseInt(partes[0]));
                aeropuerto.setCodigo(partes[1]);
                aeropuerto.setCiudad(partes[2]);
                aeropuerto.setPais(partes[3]);
                aeropuerto.setAbreviatura(partes[4]);
                aeropuerto.setGmt(Integer.parseInt(partes[5]));
                aeropuerto.setCapacidad(Integer.parseInt(partes[6]));

                // Latitud y longitud son el resto de la línea
                if (partes.length > 7) {
                    StringBuilder coords = new StringBuilder();
                    for (int i = 7; i < partes.length; i++) {
                        coords.append(partes[i]).append(" ");
                    }
                    String[] coordSplit = coords.toString().split("Longitude:");
                    aeropuerto.setLatitud(coordSplit[0].replace("Latitude:", "").trim());
                    if (coordSplit.length > 1) {
                        aeropuerto.setLongitud(coordSplit[1].trim());
                    }
                }

                aeropuertos.add(aeropuerto);
            }
        } catch (Exception e) {
            System.err.println("Error al leer archivo de aeropuertos: " + e.getMessage());
        }

        return aeropuertos;
    }
}