package com.loadly.backend.service.database;

import com.loadly.backend.database.config.DatabaseManager;
import com.loadly.backend.dto.AeropuertoDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio para operaciones de Aeropuertos
 */
@Service
public class AeropuertoService {

    @Autowired
    private DatabaseManager databaseManager;

    /**
     * Obtiene todos los aeropuertos
     */
    public List<AeropuertoDTO> obtenerTodos() {
        String sql = "SELECT IdAeropuerto, codigo, ciudad, pais, abreviatura, gmt, capacidad, latitud, longitud, continente FROM aeropuerto";
        
        return databaseManager.getPrimaryDb().query(sql, (rs, rowNum) -> new AeropuertoDTO(
            rs.getInt("IdAeropuerto"),
            rs.getString("codigo"),
            rs.getString("ciudad"),
            rs.getString("pais"),
            rs.getString("abreviatura"),
            rs.getInt("gmt"),
            rs.getInt("capacidad"),
            rs.getDouble("latitud"),
            rs.getDouble("longitud"),
            rs.getString("continente")
        ));
    }

    /**
     * Obtiene un aeropuerto por ID
     */
    public AeropuertoDTO obtenerPorId(Integer id) {
        String sql = "SELECT IdAeropuerto, codigo, ciudad, pais, abreviatura, gmt, capacidad, latitud, longitud, continente FROM aeropuerto WHERE IdAeropuerto = ?";
        
        try {
            return databaseManager.getPrimaryDb().queryForObject(sql, new Object[]{id}, (rs, rowNum) -> new AeropuertoDTO(
                rs.getInt("IdAeropuerto"),
                rs.getString("codigo"),
                rs.getString("ciudad"),
                rs.getString("pais"),
                rs.getString("abreviatura"),
                rs.getInt("gmt"),
                rs.getInt("capacidad"),
                rs.getDouble("latitud"),
                rs.getDouble("longitud"),
                rs.getString("continente")
            ));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtiene el total de aeropuertos
     */
    public Long obtenerTotal() {
        String sql = "SELECT COUNT(*) FROM aeropuerto";
        return databaseManager.getPrimaryDb().queryForObject(sql, Long.class);
    }

    /**
     * Inserta múltiples aeropuertos de forma masiva
     */
    public int insertarMasivo(List<AeropuertoDTO> aeropuertos) {
        int insertados = 0;
        
        // Obtener el máximo ID actual
        String maxIdSql = "SELECT COALESCE(MAX(IdAeropuerto), 0) as maxId FROM aeropuerto";
        Integer maxId = databaseManager.getPrimaryDb().queryForObject(maxIdSql, Integer.class);
        if (maxId == null) maxId = 0;
        
        String sql = "INSERT INTO aeropuerto (IdAeropuerto, codigo, ciudad, pais, abreviatura, gmt, capacidad, latitud, longitud, continente) " +
                     "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        
        int nextId = maxId + 1;
        for (AeropuertoDTO aero : aeropuertos) {
            try {
                int resultado = databaseManager.getPrimaryDb().update(sql,
                    nextId++,
                    aero.getCodigo(),
                    aero.getCiudad(),
                    aero.getPais(),
                    aero.getAbreviatura(),
                    aero.getGmt(),
                    aero.getCapacidad(),
                    aero.getLatitud(),
                    aero.getLongitud(),
                    aero.getContinente()
                );
                insertados += resultado;
            } catch (Exception e) {
                // Continuar con el siguiente si hay error
                System.err.println("Error al insertar aeropuerto " + aero.getCodigo() + ": " + e.getMessage());
            }
        }
        return insertados;
    }

    /**
     * Parsea un archivo TXT con formato de aeropuertos
     * Retorna una lista de AeropuertoDTO
     */
    public List<AeropuertoDTO> parsearArchivoTxt(String contenido) {
        List<AeropuertoDTO> aeropuertos = new ArrayList<>();
        
        if (contenido == null || contenido.trim().isEmpty()) {
            return aeropuertos;
        }

        String[] lineas = contenido.split("\n");
        
        for (String linea : lineas) {
            // Saltar líneas vacías y cabeceras
            linea = linea.trim();
            if (linea.isEmpty() || linea.startsWith("*") || linea.contains("America del Sur") || 
                linea.contains("Europa") || linea.contains("Asia")) {
                continue;
            }

            try {
                AeropuertoDTO aero = parsearLinea(linea);
                if (aero != null) {
                    aeropuertos.add(aero);
                }
            } catch (Exception e) {
                System.err.println("Error al parsear línea: " + linea + " - " + e.getMessage());
            }
        }
        
        return aeropuertos;
    }

    /**
     * Parsea una línea individual del archivo TXT
     * Formato: 01   SKBO   Bogota              Colombia        bogo    -5     430     Latitude: 04° 42' 05" N   Longitude:  74° 08' 49" W
     */
    private AeropuertoDTO parsearLinea(String linea) {
        try {
            // Extraer Latitude y Longitude primero (contienen paréntesis característicos)
            Double latitud = 0.0;
            Double longitud = 0.0;
            
            if (linea.contains("Latitude:")) {
                int latStart = linea.indexOf("Latitude:") + 9;
                int latEnd = linea.indexOf("Longitude:");
                if (latEnd > latStart) {
                    String coordLat = linea.substring(latStart, latEnd).trim();
                    latitud = parsearCoordenada(coordLat);
                }
            }
            
            if (linea.contains("Longitude:")) {
                int lonStart = linea.indexOf("Longitude:") + 10;
                String coordLon = linea.substring(lonStart).trim();
                longitud = parsearCoordenada(coordLon);
            }
            
            // Extraer el resto de la línea (sin coordenadas)
            String restoLinea = linea;
            if (linea.contains("Latitude:")) {
                restoLinea = linea.substring(0, linea.indexOf("Latitude:"));
            }
            
            // Dividir por 2+ espacios para obtener campos base
            String[] partes = restoLinea.split("\\s{2,}");
            
            if (partes.length < 7) {
                System.err.println("No hay suficientes campos en la línea: " + linea + " (campos: " + partes.length + ")");
                return null;
            }
            
            // Partes: [0]=número, [1]=código, [2]=ciudad, [3]=país, [4]=abreviatura, [5]=GMT, [6]=capacidad
            String codigo = partes[1].trim();
            String ciudad = partes[2].trim();
            String pais = partes[3].trim();
            String abreviatura = partes[4].trim();
            
            Integer gmt;
            Integer capacidad;
            
            try {
                gmt = Integer.parseInt(partes[5].trim());
                capacidad = Integer.parseInt(partes[6].trim());
            } catch (NumberFormatException e) {
                System.err.println("Error al parsear GMT o capacidad en línea: " + linea);
                return null;
            }
            
            String continente = determinarContinente(pais);

            return new AeropuertoDTO(
                null,
                codigo,
                ciudad,
                pais,
                abreviatura,
                gmt,
                capacidad,
                latitud,
                longitud,
                continente
            );
            
        } catch (Exception e) {
            System.err.println("Error al parsear línea: " + linea + " - " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Convierte coordenadas en formato "04° 42' 05" N" a decimal
     */
    private Double parsearCoordenada(String coord) {
        if (coord == null || coord.isEmpty()) {
            return 0.0;
        }

        // Extraer grados, minutos, segundos
        Pattern pattern = Pattern.compile("(\\d+)°\\s*(\\d+)'\\s*([\\d.]+)\"\\s*([NSEW])");
        Matcher matcher = pattern.matcher(coord);

        if (!matcher.find()) {
            return 0.0;
        }

        double grados = Double.parseDouble(matcher.group(1));
        double minutos = Double.parseDouble(matcher.group(2));
        double segundos = Double.parseDouble(matcher.group(3));
        String direccion = matcher.group(4);

        double decimal = grados + (minutos / 60.0) + (segundos / 3600.0);

        // Ajustar signo según dirección
        if (direccion.equals("S") || direccion.equals("W")) {
            decimal = -decimal;
        }

        return decimal;
    }

    /**
     * Determina el continente basado en el país
     */
    private String determinarContinente(String pais) {
        String paisLower = pais.toLowerCase();
        
        if (paisLower.contains("colombia") || paisLower.contains("ecuador") || 
            paisLower.contains("venezuela") || paisLower.contains("brasil") || 
            paisLower.contains("perú") || paisLower.contains("bolivia") || 
            paisLower.contains("chile") || paisLower.contains("argentina") || 
            paisLower.contains("paraguay") || paisLower.contains("uruguay")) {
            return "America del Sur";
        } else if (paisLower.contains("albania") || paisLower.contains("alemania") ||
                   paisLower.contains("austria") || paisLower.contains("belgica") ||
                   paisLower.contains("bielorrusia") || paisLower.contains("bulgaria") ||
                   paisLower.contains("checa") || paisLower.contains("croacia") ||
                   paisLower.contains("dinamarca") || paisLower.contains("holanda")) {
            return "Europa";
        } else if (paisLower.contains("india") || paisLower.contains("siria") ||
                   paisLower.contains("arabia saudita") || paisLower.contains("emiratos") ||
                   paisLower.contains("afganistan") || paisLower.contains("oman") ||
                   paisLower.contains("yemen") || paisLower.contains("pakistan") ||
                   paisLower.contains("azerbaiyan") || paisLower.contains("jordania")) {
            return "Asia";
        }
        
        return "Otros";
    }

    /**
     * Carga aeropuertos desde contenido de archivo TXT
     * Parsea el archivo y los inserta en la base de datos
     */
    public int cargarDesdeArchivo(String contenido) {
        List<AeropuertoDTO> aeropuertos = parsearArchivoTxt(contenido);
        return insertarMasivo(aeropuertos);
    }
}
