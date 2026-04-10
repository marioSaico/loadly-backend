package com.loadly.backend.loader;

import com.loadly.backend.model.Envio;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

@Component
public class EnvioLoader {

    public List<Envio> cargar(String rutaCarpeta) {
        List<Envio> envios = new ArrayList<>();
        File carpeta = new File(rutaCarpeta);

        // Busca todos los archivos de envios en la carpeta
        File[] archivos = carpeta.listFiles(
            (dir, nombre) -> nombre.startsWith("_envios_") && nombre.endsWith("_.txt")
        );

        if (archivos == null) return envios;

        for (File archivo : archivos) {
            // Extrae el código del aeropuerto origen del nombre del archivo
            // Ejemplo: _envios_EBCI_.txt → EBCI
            String nombreArchivo = archivo.getName();
            String codigoOrigen = nombreArchivo
                .replace("_envios_", "")
                .replace("_.txt", "");

            try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    linea = linea.trim();
                    if (linea.isEmpty()) continue;

                    String[] partes = linea.split("-");
                    if (partes.length < 7) continue;

                    Envio envio = new Envio();
                    envio.setIdEnvio(partes[0]);
                    envio.setFechaRegistro(partes[1]);
                    envio.setHoraRegistro(partes[2]);
                    envio.setMinutoRegistro(partes[3]);
                    envio.setAeropuertoOrigen(codigoOrigen);
                    envio.setAeropuertoDestino(partes[4]);
                    envio.setCantidadMaletas(Integer.parseInt(partes[5]));
                    envio.setIdCliente(partes[6]);
                    envio.setPlanificado(false);

                    envios.add(envio);
                }
            } catch (Exception e) {
                System.err.println("Error al leer archivo " + archivo.getName() 
                    + ": " + e.getMessage());
            }
        }

        return envios;
    }
}