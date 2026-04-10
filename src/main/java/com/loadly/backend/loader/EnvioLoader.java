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
                    envio.setHoraRegistro(Integer.parseInt(partes[2]));
                    envio.setMinutoRegistro(Integer.parseInt(partes[3]));
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
    public List<Envio> cargarPendientes(String rutaCarpeta, String fechaHoraLimite) {
        List<Envio> enviosPendientes = new ArrayList<>();
        
        // fechaHoraLimite viene en formato "aaaammdd-hh-mm"
        // Ejemplo: "20260102-01-10"
        String[] partes = fechaHoraLimite.split("-");
        String fechaLimite = partes[0];    // 20260102
        int horaLimite = Integer.parseInt(partes[1]);   // 01
        int minutoLimite = Integer.parseInt(partes[2]); // 10

        File carpeta = new File(rutaCarpeta);
        File[] archivos = carpeta.listFiles(
            (dir, nombre) -> nombre.startsWith("_envios_") && nombre.endsWith("_.txt")
        );

        if (archivos == null) return enviosPendientes;

        for (File archivo : archivos) {
            String codigoOrigen = archivo.getName()
                .replace("_envios_", "")
                .replace("_.txt", "");

            try (BufferedReader br = new BufferedReader(new FileReader(archivo))) {
                String linea;
                while ((linea = br.readLine()) != null) {
                    linea = linea.trim();
                    if (linea.isEmpty()) continue;

                    String[] campos = linea.split("-");
                    if (campos.length < 7) continue;

                    String fechaEnvio = campos[1];
                    int horaEnvio = Integer.parseInt(campos[2]);
                    int minutoEnvio = Integer.parseInt(campos[3]);

                    // Verificar si el envío está dentro del rango
                    boolean dentroDelRango = false;

                    if (fechaEnvio.compareTo(fechaLimite) < 0) {
                        // La fecha es anterior al límite
                        dentroDelRango = true;
                    } else if (fechaEnvio.equals(fechaLimite)) {
                        // Misma fecha, comparar hora y minuto
                        if (horaEnvio < horaLimite) {
                            dentroDelRango = true;
                        } else if (horaEnvio == horaLimite && minutoEnvio <= minutoLimite) {
                            dentroDelRango = true;
                        }
                    }

                    if (!dentroDelRango) continue;

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
                System.err.println("Error leyendo " + archivo.getName() 
                    + ": " + e.getMessage());
            }
        }

        return enviosPendientes;
    }
}