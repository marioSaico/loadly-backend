package com.loadly.backend.experimento;
 
import com.loadly.backend.model.Aeropuerto;
import com.loadly.backend.model.Envio;
 
import java.util.*;
 
/**
 * Genera datasets sintéticos de envíos para el experimento.
 *
 * Rangos de maletas por envío calibrados para generar competencia
 * real por recursos (vuelos 150-400 maletas, almacenes 400-800):
 *   - pequeño  (20 envíos)  : 5-20  maletas → poca presión
 *   - mediano  (100 envíos) : 5-20  maletas → presión moderada
 *   - grande   (500 envíos) : 5-20  maletas → alta presión por acumulación
 *
 * Ta calibrado según tiempos reales observados:
 *   - pequeño  → Ta = 15s
 *   - mediano  → Ta = 40s
 *   - grande   → Ta = 90s  (margen para ACO que tarda más por iteración)
 *
 * Hormigas ACO calibradas para garantizar suficientes iteraciones:
 *   - pequeño  → 30 hormigas
 *   - mediano  → 30 hormigas
 *   - grande   → 10 hormigas  (reducido para permitir más iteraciones)
 */
public class GeneradorDatasets {
 
    private static final int MIN_MALETAS = 5;
    private static final int MAX_MALETAS = 20;
    private static final String FECHA_BASE = "20260101";
    private static final Random random = new Random();
 
    public static List<Envio> generar(int cantidad, List<Aeropuerto> aeropuertosReales) {
        List<Envio> dataset = new ArrayList<>();
 
        List<Aeropuerto> validos = aeropuertosReales.stream()
                .filter(a -> a.getCodigo() != null && !a.getCodigo().isBlank())
                .toList();
 
        if (validos.size() < 2) {
            throw new IllegalStateException("Se necesitan al menos 2 aeropuertos");
        }
 
        for (int i = 1; i <= cantidad; i++) {
            Aeropuerto origen = validos.get(random.nextInt(validos.size()));
            Aeropuerto destino;
            do {
                destino = validos.get(random.nextInt(validos.size()));
            } while (destino.getCodigo().equals(origen.getCodigo()));
 
            int hora   = (i - 1) * 24 / cantidad;
            int minuto = random.nextInt(60);
            int maletas = MIN_MALETAS + random.nextInt(MAX_MALETAS - MIN_MALETAS + 1);
 
            Envio envio = new Envio();
            envio.setIdEnvio("SYN-" + String.format("%04d", i));
            envio.setAeropuertoOrigen(origen.getCodigo());
            envio.setAeropuertoDestino(destino.getCodigo());
            envio.setCantidadMaletas(maletas);
            envio.setFechaRegistro(FECHA_BASE);
            envio.setHoraRegistro(hora);
            envio.setMinutoRegistro(minuto);
            envio.setIdCliente("CLI001");
 
            dataset.add(envio);
        }
 
        return dataset;
    }
 
    public static String nombreDataset(int cantidad) {
        return switch (cantidad) {
            case 20  -> "pequeño";
            case 100 -> "mediano";
            case 500 -> "grande";
            default  -> "n=" + cantidad;
        };
    }
 
    /** Ta en ms calibrado según tiempos reales observados */
    public static long taRecomendadoMs(int cantidad) {
        return switch (cantidad) {
            case 20  -> 15_000L;   // 15s — cubre rango 11-15s observado
            case 100 -> 40_000L;   // 40s — cubre rango 25-36s observado
            case 500 -> 90_000L;   // 90s — da margen al ACO (~85s observado)
            default  -> 40_000L;
        };
    }
 
    /** Hormigas ACO calibradas para garantizar suficientes iteraciones por dataset */
    public static int hormigasACO(int cantidad) {
        return switch (cantidad) {
            case 20  -> 30;   // suficiente, iteraciones rápidas
            case 100 -> 30;   // suficiente, ~5 iteraciones observadas
            case 500 -> 10;   // reducido: 30→10 para permitir más iteraciones
            default  -> 30;
        };
    }
}
 