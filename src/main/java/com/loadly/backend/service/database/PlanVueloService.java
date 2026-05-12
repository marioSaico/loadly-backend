package com.loadly.backend.service.database;

import com.loadly.backend.database.config.DatabaseManager;
import com.loadly.backend.dto.PlanVueloDTO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Servicio para operaciones de Planes de Vuelo
 */
@Service
public class PlanVueloService {

    @Autowired
    private DatabaseManager databaseManager;

    /**
     * Obtiene todos los planes de vuelo
     */
    public List<PlanVueloDTO> obtenerTodos() {
        String sql = "SELECT id_plan_vuelo, idAeropuertoOrigen, idAeropuertoDestino, horaSalida, horaLlegada, capacidad, cancelado FROM plan_vuelo";
        
        return databaseManager.getPrimaryDb().query(sql, (rs, rowNum) -> new PlanVueloDTO(
            rs.getInt("id_plan_vuelo"),
            rs.getInt("idAeropuertoOrigen"),
            rs.getInt("idAeropuertoDestino"),
            rs.getTime("horaSalida") != null ? rs.getTime("horaSalida").toLocalTime() : null,
            rs.getTime("horaLlegada") != null ? rs.getTime("horaLlegada").toLocalTime() : null,
            rs.getInt("capacidad"),
            rs.getBoolean("cancelado")
        ));
    }

    /**
     * Obtiene un plan de vuelo por ID
     */
    public PlanVueloDTO obtenerPorId(Integer id) {
        String sql = "SELECT id_plan_vuelo, idAeropuertoOrigen, idAeropuertoDestino, horaSalida, horaLlegada, capacidad, cancelado FROM plan_vuelo WHERE id_plan_vuelo = ?";
        
        try {
            return databaseManager.getPrimaryDb().queryForObject(sql, new Object[]{id}, (rs, rowNum) -> new PlanVueloDTO(
                rs.getInt("id_plan_vuelo"),
                rs.getInt("idAeropuertoOrigen"),
                rs.getInt("idAeropuertoDestino"),
                rs.getTime("horaSalida") != null ? rs.getTime("horaSalida").toLocalTime() : null,
                rs.getTime("horaLlegada") != null ? rs.getTime("horaLlegada").toLocalTime() : null,
                rs.getInt("capacidad"),
                rs.getBoolean("cancelado")
            ));
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Obtiene el total de planes de vuelo
     */
    public Long obtenerTotal() {
        String sql = "SELECT COUNT(*) FROM plan_vuelo";
        return databaseManager.getPrimaryDb().queryForObject(sql, Long.class);
    }

    /**
     * Carga planes de vuelo desde archivo TXT
     * Formato: ORIGEN-DESTINO-SALIDA-LLEGADA-CAPACIDAD
     * Ejemplo: SKBO-SEQM-03:34-04:21-0300
     */
    public int cargarDesdeArchivo(String contenido) {
        try {
            List<PlanVueloDTO> planes = parsearArchivoTxt(contenido);
            return insertarMasivo(planes);
        } catch (Exception e) {
            System.err.println("Error al cargar archivo de planes: " + e.getMessage());
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * Parsea el archivo TXT y extrae planes de vuelo
     */
    private List<PlanVueloDTO> parsearArchivoTxt(String contenido) {
        List<PlanVueloDTO> planes = new ArrayList<>();
        String[] lineas = contenido.split("\n");

        for (String linea : lineas) {
            linea = linea.trim();
            if (linea.isEmpty()) continue;

            try {
                PlanVueloDTO plan = parsearLinea(linea);
                if (plan != null) {
                    planes.add(plan);
                }
            } catch (Exception e) {
                System.err.println("Error parsing línea: " + linea + " - " + e.getMessage());
            }
        }

        return planes;
    }

    /**
     * Parsea una línea individual y extrae los datos del plan de vuelo
     * Formato esperado: ORIGEN-DESTINO-HH:MM-HH:MM-CAPACIDAD
     */
    private PlanVueloDTO parsearLinea(String linea) {
        try {
            // Patrón: CODIGO-CODIGO-HH:MM-HH:MM-CAPACIDAD
            Pattern pattern = Pattern.compile("([A-Z]{4})-([A-Z]{4})-(\\d{2}:\\d{2})-(\\d{2}:\\d{2})-(\\d{4})");
            Matcher matcher = pattern.matcher(linea);

            if (!matcher.find()) {
                System.err.println("Línea no coincide con patrón: " + linea);
                return null;
            }

            String codigoOrigen = matcher.group(1);
            String codigoDestino = matcher.group(2);
            String horaSalida = matcher.group(3);
            String horaLlegada = matcher.group(4);
            String capacidadStr = matcher.group(5);

            // Obtener IDs de aeropuertos por código
            Integer idOrigen = obtenerIdAeropuertoPorCodigo(codigoOrigen);
            Integer idDestino = obtenerIdAeropuertoPorCodigo(codigoDestino);

            if (idOrigen == null || idDestino == null) {
                System.err.println("Aeropuerto no encontrado: " + codigoOrigen + " o " + codigoDestino);
                return null;
            }

            LocalTime salida = LocalTime.parse(horaSalida);
            LocalTime llegada = LocalTime.parse(horaLlegada);
            Integer capacidad = Integer.parseInt(capacidadStr);

            return new PlanVueloDTO(
                null, // ID será generado
                idOrigen,
                idDestino,
                salida,
                llegada,
                capacidad,
                false // No cancelado
            );

        } catch (Exception e) {
            System.err.println("Error al parsear línea '" + linea + "': " + e.getMessage());
            return null;
        }
    }

    /**
     * Obtiene el ID del aeropuerto por su código IATA
     */
    private Integer obtenerIdAeropuertoPorCodigo(String codigo) {
        try {
            String sql = "SELECT IdAeropuerto FROM aeropuerto WHERE codigo = ?";
            return databaseManager.getPrimaryDb().queryForObject(sql, new Object[]{codigo}, Integer.class);
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Inserta múltiples planes de vuelo en la base de datos
     */
    private int insertarMasivo(List<PlanVueloDTO> planes) {
        int insertados = 0;

        for (PlanVueloDTO plan : planes) {
            try {
                // Obtener el próximo ID
                Integer nextId = databaseManager.getPrimaryDb()
                    .queryForObject("SELECT COALESCE(MAX(id_plan_vuelo), 0) + 1 FROM plan_vuelo", Integer.class);

                String sql = "INSERT INTO plan_vuelo (id_plan_vuelo, idAeropuertoOrigen, idAeropuertoDestino, horaSalida, horaLlegada, capacidad, cancelado) VALUES (?, ?, ?, ?, ?, ?, ?)";
                
                databaseManager.getPrimaryDb().update(sql,
                    nextId,
                    plan.getIdAeropuertoOrigen(),
                    plan.getIdAeropuertoDestino(),
                    plan.getHoraSalida(),
                    plan.getHoraLlegada(),
                    plan.getCapacidad(),
                    plan.getCancelado() ? 1 : 0
                );

                insertados++;
                System.out.println("Plan insertado: " + nextId + " - " + plan.getIdAeropuertoOrigen() + " -> " + plan.getIdAeropuertoDestino());

            } catch (Exception e) {
                System.err.println("Error al insertar plan: " + e.getMessage());
            }
        }

        return insertados;
    }
}
