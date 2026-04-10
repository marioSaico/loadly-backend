package com.loadly.backend.loader;

import com.loadly.backend.model.PlanVuelo;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.List;

@Component
public class PlanVueloLoader {

    public List<PlanVuelo> cargar(String rutaArchivo) {
        List<PlanVuelo> vuelos = new ArrayList<>();

        try (BufferedReader br = new BufferedReader(new FileReader(rutaArchivo))) {
            String linea;
            while ((linea = br.readLine()) != null) {
                linea = linea.trim();
                if (linea.isEmpty()) continue;

                String[] partes = linea.split("-");
                if (partes.length < 5) continue;

                PlanVuelo vuelo = new PlanVuelo();
                vuelo.setOrigen(partes[0]);
                vuelo.setDestino(partes[1]);
                vuelo.setHoraSalida(partes[2]);
                vuelo.setHoraLlegada(partes[3]);
                vuelo.setCapacidad(Integer.parseInt(partes[4].trim()));
                vuelo.setCancelado(false);

                vuelos.add(vuelo);
            }
        } catch (Exception e) {
            System.err.println("Error al leer archivo de planes de vuelo: " + e.getMessage());
        }

        return vuelos;
    }
}