package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import lombok.Data;
import java.util.*;
import java.util.stream.Collectors;

@Data
public class Poblacion {

    private List<Individuo> individuos;
    private int tamanio;

    // Probabilidad de usar vuelo directo si existe (70%)
    // El 30% restante genera diversidad buscando rutas con escalas
    private static final double PROB_VUELO_DIRECTO = 0.7;

    // Máximo de escalas permitidas por ruta
    private static final int MAX_ESCALAS = 3;

    // Tiempo mínimo de escala en minutos (parámetro del problema)
    private static final int TIEMPO_MINIMO_ESCALA = 10;

    // Tiempo de espera en destino final antes de ser recogida (parámetro)
    private static final int TIEMPO_RECOJO_DESTINO = 10;

    public Poblacion(int tamanio) {
        this.tamanio = tamanio;
        this.individuos = new ArrayList<>();
    }

    /**
     * Genera la población inicial con N individuos.
     * Cada individuo es un conjunto de rutas válidas para todos los envíos.
     */
    public void inicializar(List<Envio> envios,
                            List<PlanVuelo> vuelos,
                            Map<String, Aeropuerto> mapaAeropuertos) {
        for (int i = 0; i < tamanio; i++) {
            List<Ruta> rutas = generarRutasAleatorias(
                envios, vuelos, mapaAeropuertos
            );
            individuos.add(new Individuo(rutas));
        }
    }

    /**
     * Genera un conjunto de rutas aleatorias válidas para todos los envíos.
     * Mantiene mapas de capacidades de vuelos y almacenes para no excederlos.
     */
    private List<Ruta> generarRutasAleatorias(
            List<Envio> envios,
            List<PlanVuelo> vuelos,
            Map<String, Aeropuerto> mapaAeropuertos) {

        List<Ruta> rutas = new ArrayList<>();
        Random random = new Random();

        // Inicializar capacidades disponibles de vuelos
        // Clave: "ORIG-DEST-HH:MM", Valor: capacidad restante del vuelo
        Map<String, Integer> capacidadesVuelos = new HashMap<>();
        for (PlanVuelo v : vuelos) {
            capacidadesVuelos.put(claveVuelo(v), v.getCapacidad());
        }

        // Inicializar capacidades disponibles de almacenes
        // Clave: código del aeropuerto, Valor: capacidad restante del almacén
        Map<String, Integer> capacidadesAlmacenes = new HashMap<>();
        for (Aeropuerto a : mapaAeropuertos.values()) {
            capacidadesAlmacenes.put(a.getCodigo(), a.getCapacidad());
        }

        // Generar una ruta válida para cada envío
        for (Envio envio : envios) {
            Ruta ruta = generarRutaAleatoria(
                envio, vuelos, mapaAeropuertos,
                capacidadesVuelos, capacidadesAlmacenes, random
            );
            rutas.add(ruta);
        }

        return rutas;
    }

    /**
     * Genera una ruta válida para un envío específico.
     * Primero intenta vuelo directo, si no busca con escalas.
     * Valida: capacidad de vuelos, capacidad de almacenes,
     * tiempo mínimo de escala y plazo máximo de entrega.
     */
    private Ruta generarRutaAleatoria(
            Envio envio,
            List<PlanVuelo> todosLosVuelos,
            Map<String, Aeropuerto> mapaAeropuertos,
            Map<String, Integer> capacidadesVuelos,
            Map<String, Integer> capacidadesAlmacenes,
            Random random) {

        // Inicializar ruta vacía
        Ruta ruta = new Ruta();
        ruta.setEnvio(envio);
        ruta.setEstado(EstadoRuta.SIN_RUTA);
        ruta.setIndiceVueloActual(0);

        // 💡 NUEVO: 1. Verificar si el almacén de origen tiene capacidad al momento de registrar el envío
        // Si el mostrador ya está lleno, el cliente no puede dejar sus maletas y se queda sin ruta inicial
        if (capacidadesAlmacenes.getOrDefault(envio.getAeropuertoOrigen(), 0) < envio.getCantidadMaletas()) {
            ruta.setVuelos(new ArrayList<>());
            ruta.setTiempoTotalMinutos(0);
            ruta.setEstado(EstadoRuta.SIN_RUTA);
            return ruta; // Lo atrapamos antes de que intente buscar vuelos
        }

        // Determinar plazo máximo según continente de origen y destino
        // Mismo continente: 1 día (1440 min), distinto: 2 días (2880 min)
        Aeropuerto aeropOrigen = mapaAeropuertos.get(
            envio.getAeropuertoOrigen()
        );
        Aeropuerto aeropDestino = mapaAeropuertos.get(
            envio.getAeropuertoDestino()
        );

        // Por defecto 2 días si no se encuentra el aeropuerto
        long plazoMaximoMinutos = 48 * 60;
        if (aeropOrigen != null && aeropDestino != null) {
            if (aeropOrigen.getContinente()
                    .equals(aeropDestino.getContinente())) {
                plazoMaximoMinutos = 24 * 60; // 1 día mismo continente
            }
        }

        // Restar los 10 minutos de recojo en destino del plazo disponible
        // ya que ese tiempo siempre se consume al final
        long plazoDisponible = plazoMaximoMinutos - TIEMPO_RECOJO_DESTINO;

        // ── INTENTO 1: Vuelo directo ──────────────────────────────────────
        // Buscar vuelos que vayan directamente del origen al destino
        // y que cumplan con capacidad de vuelo y almacén de destino
        List<PlanVuelo> vuelosDirectos = todosLosVuelos.stream()
            .filter(v ->
                v.getOrigen().equals(envio.getAeropuertoOrigen())
                && v.getDestino().equals(envio.getAeropuertoDestino())
                && !v.isCancelado()
                // Verificar capacidad del vuelo
                && capacidadesVuelos.getOrDefault(
                    claveVuelo(v), v.getCapacidad()
                ) >= envio.getCantidadMaletas()
                // Verificar capacidad del almacén de destino
                && capacidadesAlmacenes.getOrDefault(
                    v.getDestino(), 0
                ) >= envio.getCantidadMaletas()
            )
            .collect(Collectors.toList());

        // Con probabilidad PROB_VUELO_DIRECTO usar vuelo directo si existe
        if (!vuelosDirectos.isEmpty()
                && random.nextDouble() < PROB_VUELO_DIRECTO) {

            PlanVuelo vueloDirecto = vuelosDirectos
                .get(random.nextInt(vuelosDirectos.size()));

            // Calcular duración del vuelo directo
            long duracion = calcularDuracionMinutos(vueloDirecto, mapaAeropuertos);
            
            // Calculamos cuánto esperó la maleta en el mostrador antes de volar
            long tiempoEsperaOrigen = calcularTiempoEsperaOrigen(envio, vueloDirecto.getHoraSalida());

            // Verificar que la duración (incluyendo la espera) no exceda el plazo disponible
            if ((duracion + tiempoEsperaOrigen) <= plazoDisponible) {

                // Descontar capacidad del vuelo usado
                String claveV = claveVuelo(vueloDirecto);
                capacidadesVuelos.put(
                    claveV,
                    capacidadesVuelos.getOrDefault(
                        claveV, vueloDirecto.getCapacidad()
                    ) - envio.getCantidadMaletas()
                );

                // 💡 NUEVO: 2. Descontar capacidad del almacén de ORIGEN
                // porque la maleta estuvo ahí esperando a que salga el avión
                capacidadesAlmacenes.put(
                    envio.getAeropuertoOrigen(),
                    capacidadesAlmacenes.getOrDefault(
                        envio.getAeropuertoOrigen(), 0
                    ) - envio.getCantidadMaletas()
                );

                // Descontar capacidad del almacén de destino
                // (las maletas llegan y esperan 10 min hasta ser recogidas)
                capacidadesAlmacenes.put(
                    vueloDirecto.getDestino(),
                    capacidadesAlmacenes.getOrDefault(
                        vueloDirecto.getDestino(), 0
                    ) - envio.getCantidadMaletas()
                );

                // Tiempo total = Espera en origen + duración vuelo + 10 min recojo destino
                long tiempoTotal = tiempoEsperaOrigen + duracion + TIEMPO_RECOJO_DESTINO;

                ruta.setVuelos(new ArrayList<>(List.of(vueloDirecto)));
                ruta.setTiempoTotalMinutos(tiempoTotal);
                ruta.setEstado(EstadoRuta.PLANIFICADA);
                return ruta;
            }
        }

        // ── INTENTO 2: Ruta con escalas ───────────────────────────────────
        List<PlanVuelo> vuelosRuta = new ArrayList<>();
        String aeropuertoActual = envio.getAeropuertoOrigen();
        int escalas = 0;

        // tiempoAcumulado incluye: duración vuelos + esperas en escalas + espera en origen
        long tiempoAcumulado = 0;

        // Conjunto de aeropuertos ya visitados para evitar ciclos
        Set<String> aeropuertosVisitados = new HashSet<>();
        aeropuertosVisitados.add(aeropuertoActual);

        while (!aeropuertoActual.equals(envio.getAeropuertoDestino())
                && escalas < MAX_ESCALAS) {

            final String actual = aeropuertoActual;
            final List<PlanVuelo> vuelosActuales = vuelosRuta;

            // Buscar vuelos disponibles desde el aeropuerto actual
            List<PlanVuelo> vuelosDisponibles = todosLosVuelos.stream()
                .filter(v ->
                    v.getOrigen().equals(actual)
                    && !v.isCancelado()
                    // No visitar aeropuertos ya visitados (evitar ciclos)
                    && !aeropuertosVisitados.contains(v.getDestino())
                    // Verificar capacidad del vuelo
                    && capacidadesVuelos.getOrDefault(
                        claveVuelo(v), v.getCapacidad()
                    ) >= envio.getCantidadMaletas()
                    // Verificar capacidad del almacén del aeropuerto destino
                    && capacidadesAlmacenes.getOrDefault(
                        v.getDestino(), 0
                    ) >= envio.getCantidadMaletas()
                    // Si hay vuelo anterior, verificar tiempo mínimo de escala
                    && (vuelosActuales.isEmpty() || cumpleTiempoEscala(
                        vuelosActuales.get(vuelosActuales.size() - 1), v
                    ))
                )
                .collect(Collectors.toList());

            if (vuelosDisponibles.isEmpty()) break;

            // Elegir un vuelo al azar de los disponibles
            PlanVuelo vueloElegido = vuelosDisponibles
                .get(random.nextInt(vuelosDisponibles.size()));

            // Calcular duración del vuelo elegido
            long duracionVuelo = calcularDuracionMinutos(vueloElegido, mapaAeropuertos);

            long tiempoEspera = 0;
            if (!vuelosRuta.isEmpty()) {
                // Calcular tiempo de espera en escala (tiempo entre llegada
                // del vuelo anterior y salida del vuelo elegido)
                PlanVuelo ultimoVuelo = vuelosRuta.get(vuelosRuta.size() - 1);
                tiempoEspera = calcularTiempoEspera(
                    ultimoVuelo.getHoraLlegada(),
                    vueloElegido.getHoraSalida()
                );
            } else {
                // Si es el PRIMER vuelo de la ruta con escalas, 
                // la "espera" es el tiempo desde que se registró la maleta.
                tiempoEspera = calcularTiempoEsperaOrigen(envio, vueloElegido.getHoraSalida());
            }

            // Sumar duración del vuelo + tiempo de espera (ya sea origen o escala)
            long tiempoSegmento = duracionVuelo + tiempoEspera;

            // Verificar que el tiempo acumulado no exceda el plazo disponible
            if (tiempoAcumulado + tiempoSegmento > plazoDisponible) break;

            // Agregar el vuelo a la ruta
            vuelosRuta.add(vueloElegido);
            aeropuertosVisitados.add(vueloElegido.getDestino());
            aeropuertoActual = vueloElegido.getDestino();
            tiempoAcumulado += tiempoSegmento;
            escalas++;
        }

        // Verificar si la ruta llegó al destino
        if (aeropuertoActual.equals(envio.getAeropuertoDestino())
                && !vuelosRuta.isEmpty()) {

            // 💡 NUEVO: 3. Descontar capacidad del almacén de ORIGEN 
            // ya que completamos exitosamente una ruta con escalas
            capacidadesAlmacenes.put(
                envio.getAeropuertoOrigen(),
                capacidadesAlmacenes.getOrDefault(
                    envio.getAeropuertoOrigen(), 0
                ) - envio.getCantidadMaletas()
            );

            // Descontar capacidades de vuelos y almacenes usados (escalas y destino)
            for (PlanVuelo v : vuelosRuta) {
                // Descontar capacidad del vuelo
                String claveV = claveVuelo(v);
                capacidadesVuelos.put(
                    claveV,
                    capacidadesVuelos.getOrDefault(
                        claveV, v.getCapacidad()
                    ) - envio.getCantidadMaletas()
                );

                // Descontar capacidad del almacén de cada aeropuerto
                // que la maleta pasa (escala o destino final)
                // porque al aterrizar siempre ingresa al almacén
                capacidadesAlmacenes.put(
                    v.getDestino(),
                    capacidadesAlmacenes.getOrDefault(
                        v.getDestino(), 0
                    ) - envio.getCantidadMaletas()
                );
            }

            // Tiempo total = tiempo acumulado + 10 min recojo en destino
            long tiempoTotal = tiempoAcumulado + TIEMPO_RECOJO_DESTINO;

            ruta.setVuelos(vuelosRuta);
            ruta.setTiempoTotalMinutos(tiempoTotal);
            ruta.setEstado(EstadoRuta.PLANIFICADA);

        } else {
            // No se encontró ruta válida para este envío
            ruta.setVuelos(new ArrayList<>());
            ruta.setTiempoTotalMinutos(0);
            ruta.setEstado(EstadoRuta.SIN_RUTA);
        }

        return ruta;
    }

    // =========================================================================
    // MÉTODOS AUXILIARES
    // =========================================================================

    /**
     * Calcula el tiempo desde que el cliente registra la maleta 
     * hasta que sale el primer vuelo.
     */
    private long calcularTiempoEsperaOrigen(Envio envio, String horaSalidaVuelo) {
        int minRegistro = (envio.getHoraRegistro() * 60) + envio.getMinutoRegistro();
        int minSalidaVuelo = convertirAMinutos(horaSalidaVuelo);

        // Si el vuelo sale "antes" (en el reloj) que la hora de registro,
        // significa que el vuelo sale al día siguiente.
        if (minSalidaVuelo < minRegistro) {
            minSalidaVuelo += 24 * 60;
        }

        return minSalidaVuelo - minRegistro;
    }

    /**
     * Verifica que el tiempo entre la llegada del vuelo anterior
     * y la salida del vuelo siguiente sea al menos TIEMPO_MINIMO_ESCALA.
     */
    private boolean cumpleTiempoEscala(PlanVuelo vueloAnterior, PlanVuelo vueloSiguiente) {
        int minLlegada = convertirAMinutos(vueloAnterior.getHoraLlegada());
        int minSalida = convertirAMinutos(vueloSiguiente.getHoraSalida());

        // Si el vuelo siguiente sale al día siguiente
        if (minSalida < minLlegada) {
            minSalida += 24 * 60;
        }

        return (minSalida - minLlegada) >= TIEMPO_MINIMO_ESCALA;
    }

    /**
     * Calcula el tiempo de espera en minutos entre la llegada
     * de un vuelo y la salida del siguiente.
     * Maneja el caso en que el vuelo siguiente sale al día siguiente.
     */
    private long calcularTiempoEspera(String horaLlegada, String horaSalida) {
        int minLlegada = convertirAMinutos(horaLlegada);
        int minSalida = convertirAMinutos(horaSalida);

        // Si la salida es anterior a la llegada, cruza la medianoche
        if (minSalida < minLlegada) {
            minSalida += 24 * 60;
        }

        return minSalida - minLlegada;
    }

    /**
     * Convierte una hora en formato "HH:MM" a minutos totales.
     */
    private int convertirAMinutos(String hora) {
        String[] partes = hora.split(":");
        return Integer.parseInt(partes[0]) * 60
             + Integer.parseInt(partes[1]);
    }

    /**
     * Genera una clave única para identificar un vuelo específico.
     * Formato: "ORIG-DEST-HH:MM"
     */
    private String claveVuelo(PlanVuelo vuelo) {
        return vuelo.getOrigen() + "-" + vuelo.getDestino()
             + "-" + vuelo.getHoraSalida();
    }

    /**
     * Calcula la duración de un vuelo en minutos.
     * Maneja el caso en que el vuelo cruza la medianoche y convierte las horas
     * locales a hora global (GMT) para obtener la duración real del vuelo.
     */
    private long calcularDuracionMinutos(PlanVuelo vuelo, Map<String, Aeropuerto> mapaAeropuertos) {
        int minSalidaLocal = convertirAMinutos(vuelo.getHoraSalida());
        int minLlegadaLocal = convertirAMinutos(vuelo.getHoraLlegada());

        // Obtener el GMT (huso horario) del aeropuerto de origen y destino
        int gmtOrigen = mapaAeropuertos.get(vuelo.getOrigen()).getGmt();
        int gmtDestino = mapaAeropuertos.get(vuelo.getDestino()).getGmt();

        // Convertir ambas horas locales a la misma referencia global (GMT 0)
        int minSalidaGMT = minSalidaLocal - (gmtOrigen * 60);
        int minLlegadaGMT = minLlegadaLocal - (gmtDestino * 60);

        // Si la hora de llegada en GMT es menor que la de salida en GMT, 
        // significa que el vuelo aterrizó al día siguiente (cruzó la medianoche global).
        while (minLlegadaGMT < minSalidaGMT) {
            minLlegadaGMT += 24 * 60;
        }

        return minLlegadaGMT - minSalidaGMT;
    }

    /**
     * Obtener el mejor individuo de la población actual
     * basándose en el valor de fitness más alto.
     */
    public Individuo getMejorIndividuo() {
        return individuos.stream()
            .max(Comparator.comparingDouble(Individuo::getFitness))
            .orElse(null);
    }
}