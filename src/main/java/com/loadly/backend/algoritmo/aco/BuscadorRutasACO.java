package com.loadly.backend.algoritmo.aco;
 
import com.loadly.backend.model.*;
 
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
 
/**
 * ============================================================
 * BuscadorRutasACO — Constructor de rutas para el algoritmo ACO
 * ============================================================
 *
 * A diferencia del BuscadorRutas del GA (que usa A* puro), este buscador
 * construye rutas de forma PROBABILÍSTICA, guiado por dos fuerzas:
 *
 *   1. Feromona (tau): cuánto rastro dejaron las hormigas buenas en ese vuelo.
 *      Vuelos usados por buenas soluciones tienen más feromona → más atractivos.
 *
 *   2. Heurística (eta): qué tan cerca deja ese vuelo del destino final.
 *      Se calcula con distancia geográfica (Haversine). Menor distancia = mayor eta.
 *
 * La fórmula de selección es la clásica ACO:
 *   P(vuelo_i) = [tau_i ^ ALFA * eta_i ^ BETA] / suma_de_todos_los_candidatos
 *
 * Cada hormiga llama a este método por separado con sus propias capacidades clonadas,
 * por lo que cada una construye una solución diferente de forma independiente.
 *
 * NOTA SOBRE Ruta: usa @Data de Lombok, que NO genera constructor con parámetros,
 * solo getters y setters. Por eso usamos el método auxiliar crearRuta() con setters.
 */
public class BuscadorRutasACO {
 
    // ---------- Parámetros de tiempo ----------
    /** Tiempo mínimo entre llegada a un aeropuerto y el siguiente despegue (escala). */
    private static final int TIEMPO_MINIMO_ESCALA  = 10; // minutos
 
    /** Tiempo de recojo en el aeropuerto de destino final. */
    private static final int TIEMPO_RECOJO_DESTINO = 10; // minutos
 
    /** Límite de escalas para evitar búsquedas infinitas. */
    private static final int MAX_ESCALAS           = 6;
 
    // ---------- Parámetros de la fórmula ACO ----------
    /** Peso de la feromona. Mayor ALFA = más influencia del aprendizaje colectivo. */
    private static final double ALFA = 1.0;
 
    /** Peso de la heurística. Mayor BETA = más influencia de la distancia al destino. */
    private static final double BETA = 2.0;
 
    private static final DateTimeFormatter FMT_FECHA = DateTimeFormatter.ofPattern("yyyyMMdd");
 
    // =========================================================================
    //  MÉTODO PRINCIPAL
    // =========================================================================
 
    /**
     * Construye una ruta completa para un envío usando selección probabilística ACO.
     *
     * @param envio               El envío a planificar.
     * @param mapaVuelosPorOrigen Índice de vuelos agrupados por aeropuerto origen.
     * @param mapaAeropuertos     Datos completos de cada aeropuerto.
     * @param feromenaGrafo       Grafo de feromonas compartido (solo lectura aquí).
     * @param capVuelos           Capacidades de vuelos CLONADAS para esta hormiga.
     * @param capAlmacenes        Capacidades de almacenes CLONADAS para esta hormiga.
     * @param random              Generador de aleatoriedad para la ruleta.
     * @return Ruta con estado PLANIFICADA, INALCANZABLE o SIN_RUTA.
     */
    public Ruta construirRuta(
            Envio envio,
            Map<String, List<PlanVuelo>> mapaVuelosPorOrigen,
            Map<String, Aeropuerto> mapaAeropuertos,
            FeromenaGrafo feromenaGrafo,
            Map<String, Integer> capVuelos,
            Map<String, Integer> capAlmacenes,
            Random random) {
 
        String origen  = envio.getAeropuertoOrigen();
        String destino = envio.getAeropuertoDestino();
 
        // --- Paso 1: verificar espacio en almacén de origen ---
        int espacioAlmacen = capAlmacenes.getOrDefault(origen, 0);
        if (espacioAlmacen < envio.getCantidadMaletas()) {
            // No cabe en el almacén → retornar SIN_RUTA sin intentar buscar
            return crearRuta(envio, new ArrayList<>(), 0, EstadoRuta.SIN_RUTA);
        }
 
        // --- Paso 2: calcular SLA y deadline en GMT ---
        Aeropuerto aeroOrigen  = mapaAeropuertos.get(origen);
        Aeropuerto aeroDestino = mapaAeropuertos.get(destino);
 
        // SLA: 24h mismo continente, 48h distinto continente
        long slaMinutos = 48L * 60;
        if (aeroOrigen != null && aeroDestino != null
                && aeroOrigen.getContinente().equals(aeroDestino.getContinente())) {
            slaMinutos = 24L * 60;
        }
 
        // Hora de registro del envío convertida a GMT
        LocalDate     fechaReg       = LocalDate.parse(envio.getFechaRegistro(), FMT_FECHA);
        LocalDateTime tiempoActualGMT = LocalDateTime.of(
                fechaReg,
                LocalTime.of(envio.getHoraRegistro(), envio.getMinutoRegistro()));
        if (aeroOrigen != null) {
            tiempoActualGMT = tiempoActualGMT.minusHours(aeroOrigen.getGmt());
        }
 
        // Deadline: el envío debe llegar + ser recogido antes de este momento
        LocalDateTime deadlineGMT = tiempoActualGMT.plusMinutes(slaMinutos);
 
        // Reservar espacio en almacén de origen para esta hormiga
        capAlmacenes.put(origen, espacioAlmacen - envio.getCantidadMaletas());
 
        // --- Paso 3: construcción greedy-probabilística de la ruta ---
        List<PlanVuelo> vuelosElegidos  = new ArrayList<>();
        Set<String>     visitados       = new HashSet<>();
        visitados.add(origen);
 
        String        aeropuertoActual = origen;
        LocalDateTime tiempoLibreGMT   = tiempoActualGMT;
        long          tiempoTotalMin   = 0;
 
        for (int escala = 0; escala < MAX_ESCALAS; escala++) {
 
            // Si ya llegamos al destino, salir del bucle
            if (aeropuertoActual.equals(destino)) break;
 
            // Obtener todos los vuelos que salen del aeropuerto actual
            List<PlanVuelo> todosVuelos = mapaVuelosPorOrigen
                    .getOrDefault(aeropuertoActual, Collections.emptyList());
 
            // Filtrar vuelos factibles para esta hormiga
            List<PlanVuelo>     candidatos  = new ArrayList<>();
            List<LocalDateTime> salidasGMT  = new ArrayList<>();
            List<LocalDateTime> llegadasGMT = new ArrayList<>();
 
            for (PlanVuelo vuelo : todosVuelos) {
 
                // Ignorar vuelos cancelados
                if (vuelo.isCancelado()) continue;
 
                // Evitar ciclos: no volver a aeropuertos ya visitados,
                // excepto si ese aeropuerto es el destino final
                if (visitados.contains(vuelo.getDestino())
                        && !vuelo.getDestino().equals(destino)) {
                    continue;
                }
 
                Aeropuerto aeroVO = mapaAeropuertos.get(vuelo.getOrigen());
                Aeropuerto aeroVD = mapaAeropuertos.get(vuelo.getDestino());
                int gmtOrig = (aeroVO != null) ? aeroVO.getGmt() : 0;
                int gmtDest = (aeroVD != null) ? aeroVD.getGmt() : 0;
 
                // Calcular próxima salida disponible en GMT
                LocalDateTime salida = calcularProximaSalida(
                        vuelo.getHoraSalida(), gmtOrig, tiempoLibreGMT);
                if (salida == null) continue;
 
                // Verificar tiempo mínimo de escala (tiempo en tierra antes de embarcar)
                long minutosEspera = ChronoUnit.MINUTES.between(tiempoLibreGMT, salida);
                if (minutosEspera < TIEMPO_MINIMO_ESCALA) continue;
 
                // Calcular hora de llegada en GMT (maneja vuelos que cruzan medianoche)
                LocalDateTime llegada = calcularLlegadaGMT(
                        vuelo.getHoraLlegada(), gmtDest, salida);
 
                // Verificar que llega con tiempo para el recojo dentro del SLA
                if (llegada.plusMinutes(TIEMPO_RECOJO_DESTINO).isAfter(deadlineGMT)) continue;
 
                // Verificar capacidad disponible del vuelo para esta hormiga
                String idVuelo = vuelo.getOrigen() + "-" + vuelo.getDestino()
                        + "-" + vuelo.getHoraSalida();
                int capDisponible = capVuelos.getOrDefault(idVuelo, vuelo.getCapacidad());
                if (capDisponible < envio.getCantidadMaletas()) continue;
 
                // Vuelo pasa todos los filtros → es candidato
                candidatos.add(vuelo);
                salidasGMT.add(salida);
                llegadasGMT.add(llegada);
            }
 
            // Sin candidatos factibles → ruta imposible desde este punto
            if (candidatos.isEmpty()) {
                return crearRuta(envio, new ArrayList<>(), 0, EstadoRuta.INALCANZABLE);
            }
 
            // Selección probabilística: feromona × heurística → ruleta
            int idx = seleccionarPorRuleta(
                    candidatos, destino, mapaAeropuertos, feromenaGrafo, random);
 
            PlanVuelo     elegido = candidatos.get(idx);
            LocalDateTime salida  = salidasGMT.get(idx);
            LocalDateTime llegada = llegadasGMT.get(idx);
 
            // Descontar capacidad del vuelo elegido para esta hormiga
            String idVuelo = elegido.getOrigen() + "-" + elegido.getDestino()
                    + "-" + elegido.getHoraSalida();
            capVuelos.put(idVuelo,
                    capVuelos.getOrDefault(idVuelo, elegido.getCapacidad())
                    - envio.getCantidadMaletas());
 
            // Acumular tiempo: espera en tierra + duración del vuelo
            long espera   = ChronoUnit.MINUTES.between(tiempoLibreGMT, salida);
            long duracion = ChronoUnit.MINUTES.between(salida, llegada);
            tiempoTotalMin += espera + duracion;
 
            // Avanzar al siguiente aeropuerto
            tiempoLibreGMT   = llegada;
            aeropuertoActual = elegido.getDestino();
            visitados.add(elegido.getDestino());
            vuelosElegidos.add(elegido);
        }
 
        // --- Paso 4: verificar si llegamos al destino ---
        if (aeropuertoActual.equals(destino)) {
            tiempoTotalMin += TIEMPO_RECOJO_DESTINO;
            return crearRuta(envio, vuelosElegidos, tiempoTotalMin, EstadoRuta.PLANIFICADA);
        }
 
        // Se agotaron las escalas sin llegar al destino
        return crearRuta(envio, new ArrayList<>(), 0, EstadoRuta.INALCANZABLE);
    }
 
    // =========================================================================
    //  MÉTODOS PRIVADOS DE APOYO
    // =========================================================================
 
    /**
     * Crea un objeto Ruta usando setters.
     *
     * Ruta usa @Data de Lombok, que genera getters, setters, equals, hashCode y toString,
     * pero NO genera un constructor con todos los campos como parámetros.
     * Por eso NO se puede hacer: new Ruta(envio, vuelos, tiempo).
     * En cambio se usa el constructor vacío + setters, encapsulado aquí para no repetir código.
     */
    private Ruta crearRuta(Envio envio, List<PlanVuelo> vuelos,
                            long tiempoTotalMin, EstadoRuta estado) {
        Ruta ruta = new Ruta();
        ruta.setEnvio(envio);
        ruta.setVuelos(vuelos);
        ruta.setTiempoTotalMinutos(tiempoTotalMin);
        ruta.setEstado(estado);
        ruta.setIndiceVueloActual(0); // siempre inicia en el primer vuelo
        return ruta;
    }
 
    /**
     * Selección por ruleta (roulette wheel) usando la fórmula ACO:
     *   peso_i = tau_i ^ ALFA  *  eta_i ^ BETA
     *   P_i    = peso_i / suma_pesos
     *
     * tau = feromona del vuelo (aprendizaje colectivo acumulado entre iteraciones).
     * eta = inversa de la distancia al destino (heurística geográfica Haversine).
     *
     * @return Índice del vuelo seleccionado en la lista de candidatos.
     */
    private int seleccionarPorRuleta(
            List<PlanVuelo> candidatos,
            String destinoFinal,
            Map<String, Aeropuerto> mapaAeropuertos,
            FeromenaGrafo feromenaGrafo,
            Random random) {
 
        double[] pesos = new double[candidatos.size()];
        double   suma  = 0.0;
 
        for (int i = 0; i < candidatos.size(); i++) {
            PlanVuelo v = candidatos.get(i);
 
            // tau: feromona acumulada en este vuelo, elevada a ALFA
            double tau = Math.pow(feromenaGrafo.getFeromona(v), ALFA);
 
            // eta: heurística = qué tan cerca deja ese vuelo del destino final
            // Distancia menor → eta mayor → vuelo más atractivo para la hormiga
            double distancia = calcularDistanciaHaversine(
                    v.getDestino(), destinoFinal, mapaAeropuertos);
            double eta = Math.pow(1.0 / (distancia + 1.0), BETA);
 
            pesos[i] = tau * eta;
            suma     += pesos[i];
        }
 
        // Caso extremo: todos los pesos son 0 → elegir uniformemente al azar
        if (suma <= 0.0) {
            return random.nextInt(candidatos.size());
        }
 
        // Girar la ruleta
        double ruleta = random.nextDouble() * suma;
        double acum   = 0.0;
        for (int i = 0; i < pesos.length; i++) {
            acum += pesos[i];
            if (acum >= ruleta) return i;
        }
 
        return candidatos.size() - 1; // fallback (no debería ocurrir)
    }
 
    /**
     * Calcula la próxima hora de salida en GMT a partir de tiempoBase.
     * Como los vuelos son diarios, si la hora de hoy ya pasó en GMT, usa el día siguiente.
     *
     * @param horaSalidaLocal Hora local de salida del vuelo (formato "HH:mm").
     * @param gmtOffset       Offset GMT del aeropuerto de origen.
     * @param tiempoBase      Tiempo actual GMT desde el que se busca la próxima salida.
     * @return LocalDateTime con la próxima salida en GMT, o null si falla el parse.
     */
    private LocalDateTime calcularProximaSalida(String horaSalidaLocal, int gmtOffset,
                                                 LocalDateTime tiempoBase) {
        try {
            LocalTime     localTime = LocalTime.parse(horaSalidaLocal);
            LocalTime     gmtTime   = localTime.minusHours(gmtOffset);
            LocalDateTime candidato = tiempoBase.toLocalDate().atTime(gmtTime);
            // Si esa hora ya pasó hoy en GMT, usar la del día siguiente
            if (!candidato.isAfter(tiempoBase)) {
                candidato = candidato.plusDays(1);
            }
            return candidato;
        } catch (Exception e) {
            return null;
        }
    }
 
    /**
     * Calcula la hora de llegada en GMT.
     * Maneja el caso en que el vuelo cruza la medianoche (llegada antes que salida en el día).
     *
     * @param horaLlegadaLocal Hora local de llegada (formato "HH:mm").
     * @param gmtOffset        Offset GMT del aeropuerto de destino.
     * @param salidaGMT        Hora de salida ya convertida a GMT.
     * @return LocalDateTime con la llegada en GMT.
     */
    private LocalDateTime calcularLlegadaGMT(String horaLlegadaLocal, int gmtOffset,
                                              LocalDateTime salidaGMT) {
        LocalTime     localTime = LocalTime.parse(horaLlegadaLocal);
        LocalTime     gmtTime   = localTime.minusHours(gmtOffset);
        LocalDateTime llegada   = salidaGMT.toLocalDate().atTime(gmtTime);
        // Si la llegada queda antes que la salida → el vuelo cruza medianoche → sumar un día
        if (llegada.isBefore(salidaGMT)) {
            llegada = llegada.plusDays(1);
        }
        return llegada;
    }
 
    /**
     * Distancia geográfica entre dos aeropuertos usando la fórmula de Haversine (en km).
     * Usada como heurística eta: vuelos que dejan más cerca del destino son más atractivos.
     * Devuelve 15000 km como valor alto por defecto si algún aeropuerto no se encuentra.
     */
    private double calcularDistanciaHaversine(String codigoA, String codigoB,
                                               Map<String, Aeropuerto> mapaAeropuertos) {
        Aeropuerto a = mapaAeropuertos.get(codigoA);
        Aeropuerto b = mapaAeropuertos.get(codigoB);
        if (a == null || b == null) return 15000.0;
 
        double R    = 6371.0;
        double dLat = Math.toRadians(b.getLatitud() - a.getLatitud());
        double dLon = Math.toRadians(b.getLongitud() - a.getLongitud());
        double sinLat = Math.sin(dLat / 2);
        double sinLon = Math.sin(dLon / 2);
        double h = sinLat * sinLat
                 + Math.cos(Math.toRadians(a.getLatitud()))
                 * Math.cos(Math.toRadians(b.getLatitud()))
                 * sinLon * sinLon;
        return R * 2 * Math.asin(Math.sqrt(h));
    }
}
