package com.loadly.backend.experimento;

import com.loadly.backend.model.Aeropuerto;
import com.loadly.backend.model.Envio;

import java.util.*;

/**
 * Genera datasets sintéticos de envíos para el experimento.
 *
 * Los envíos reales tienen 1-2 maletas → poca presión al sistema.
 * Los datasets sintéticos usan 5-20 maletas por envío para estresar
 * los algoritmos y producir diferencias estadísticamente significativas.
 *
 * Formato del Envio generado:
 *   - aeropuertoOrigen: código de un aeropuerto real aleatorio
 *   - aeropuertoDestino: código de otro aeropuerto real aleatorio (distinto continente o mismo)
 *   - cantidadMaletas: entre MIN_MALETAS y MAX_MALETAS
 *   - fechaRegistro: fija "20260101"
 *   - horaRegistro / minutoRegistro: distribuidos a lo largo del día
 *   - idEnvio: generado secuencialmente
 *   - idCliente: fijo "CLI001" (no afecta al algoritmo)
 */
public class GeneradorDatasets {

    // Rangos de maletas por envío — más alto que los datos reales para estresar el sistema
    private static final int MIN_MALETAS = 5;
    private static final int MAX_MALETAS = 20;

    private static final String FECHA_BASE = "20260101";
    private static final Random random = new Random();

    /**
     * Genera un dataset sintético de N envíos usando los aeropuertos reales cargados.
     *
     * @param cantidad          Número de envíos a generar (20, 100 o 500)
     * @param aeropuertosReales Lista de aeropuertos reales del sistema (para usar códigos válidos)
     * @return Lista de Envio listos para pasarse directamente al algoritmo
     */
    public static List<Envio> generar(int cantidad, List<Aeropuerto> aeropuertosReales) {
        List<Envio> dataset = new ArrayList<>();

        // Filtramos aeropuertos con código válido
        List<Aeropuerto> validos = aeropuertosReales.stream()
                .filter(a -> a.getCodigo() != null && !a.getCodigo().isBlank())
                .toList();

        if (validos.size() < 2) {
            throw new IllegalStateException("Se necesitan al menos 2 aeropuertos para generar datasets");
        }

        for (int i = 1; i <= cantidad; i++) {
            // Seleccionar origen y destino distintos
            Aeropuerto origen  = validos.get(random.nextInt(validos.size()));
            Aeropuerto destino;
            do {
                destino = validos.get(random.nextInt(validos.size()));
            } while (destino.getCodigo().equals(origen.getCodigo()));

            // Distribuir los envíos a lo largo del día (0-23h, 0-59min)
            int hora   = (i - 1) * 24 / cantidad;       // distribución uniforme
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

    /** Nombre legible del tamaño del dataset */
    public static String nombreDataset(int cantidad) {
        return switch (cantidad) {
            case 20  -> "pequeño";
            case 100 -> "mediano";
            case 500 -> "grande";
            default  -> "n=" + cantidad;
        };
    }

    /** Ta recomendado en ms según el tamaño del dataset */
    public static long taRecomendadoMs(int cantidad) {
        return switch (cantidad) {
            case 20  -> 10_000L;   // 10 segundos
            case 100 -> 25_000L;   // 25 segundos
            case 500 -> 60_000L;   // 60 segundos
            default  -> 25_000L;
        };
    }
}
