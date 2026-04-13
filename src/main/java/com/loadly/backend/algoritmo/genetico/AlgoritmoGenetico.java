package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class AlgoritmoGenetico {

    // ── Parámetros del GA ─────────────────────────────────────────────────
    private static final int TAMANIO_POBLACION = 50;
    private static final int MAX_GENERACIONES = 100;
    private static final double PROB_CRUCE = 0.8;
    private static final double PROB_MUTACION = 0.1;
    private static final int TAMANIO_TORNEO = 3;
    private static final int ELITISMO = 2;

    public Individuo ejecutar(List<Envio> envios, List<PlanVuelo> vuelos, Map<String, Aeropuerto> mapaAeropuertos) {
        System.out.println("=== Iniciando Algoritmo Genético ===");
        System.out.println("Envíos a planificar (Tamaño original del Cromosoma): " + envios.size());

        // 1. Inicializar la población
        Poblacion poblacion = new Poblacion(TAMANIO_POBLACION);
        poblacion.inicializar(envios, vuelos, mapaAeropuertos);

        // Instanciar el evaluador de Fitness
        Fitness evaluadorFitness = new Fitness();

        // Evaluar la población inicial
        evaluadorFitness.evaluarPoblacion(poblacion, mapaAeropuertos, vuelos);
        
        Individuo mejorIndividuo = obtenerMejorIndividuo(poblacion);
        System.out.printf("Fitness inicial mejor: %.4f\n", mejorIndividuo.getFitness());

        Random random = new Random();

        // 2. Bucle principal de generaciones
        for (int gen = 0; gen < MAX_GENERACIONES; gen++) {
            List<Individuo> nuevaGeneracion = new ArrayList<>();

            // 🛠️ ELITISMO: Guardar a los mejores directamente
            List<Individuo> poblacionOrdenada = new ArrayList<>(poblacion.getIndividuos());
            poblacionOrdenada.sort((i1, i2) -> Double.compare(i2.getFitness(), i1.getFitness()));
            
            for (int i = 0; i < ELITISMO; i++) {
                nuevaGeneracion.add(copiarIndividuo(poblacionOrdenada.get(i)));
            }

            // Llenar el resto de la población con Selección, Cruce y Mutación
            while (nuevaGeneracion.size() < TAMANIO_POBLACION) {
                // Selección por Torneo
                Individuo padre1 = seleccionTorneo(poblacion, random);
                Individuo padre2 = seleccionTorneo(poblacion, random);

                Individuo hijo = padre1; // Por defecto el hijo es igual al padre 1

                // 🛠️ CRUCE
                if (random.nextDouble() < PROB_CRUCE) {
                    hijo = cruzar(padre1, padre2, random);
                }

                // 🛠️ MUTACIÓN
                mutar(hijo, vuelos, mapaAeropuertos, random);

                nuevaGeneracion.add(hijo);
            }

            poblacion.setIndividuos(nuevaGeneracion);
            evaluadorFitness.evaluarPoblacion(poblacion, mapaAeropuertos, vuelos);

            mejorIndividuo = obtenerMejorIndividuo(poblacion);
            
            // Imprimir progreso cada 10 generaciones para no saturar la consola
            if (gen % 10 == 0) {
                System.out.printf("Generación %d -> Mejor fitness: %.4f\n", gen, mejorIndividuo.getFitness());
            }
        }

        System.out.println("=== GA finalizado ===");
        System.out.printf("Mejor fitness final: %.4f\n", mejorIndividuo.getFitness());
        imprimirResumen(mejorIndividuo);

        return mejorIndividuo;
    }

    /**
     * Selección por torneo: elige N individuos al azar y se queda con el mejor.
     */
    private Individuo seleccionTorneo(Poblacion poblacion, Random random) {
        Individuo mejorTorneo = null;
        for (int i = 0; i < TAMANIO_TORNEO; i++) {
            int idxAleatorio = random.nextInt(poblacion.getIndividuos().size());
            Individuo competidor = poblacion.getIndividuos().get(idxAleatorio);
            if (mejorTorneo == null || competidor.getFitness() > mejorTorneo.getFitness()) {
                mejorTorneo = competidor;
            }
        }
        return mejorTorneo;
    }

    /**
     * 🛠️ CRUCE CORREGIDO (Uniform Crossover)
     */
    private Individuo cruzar(Individuo padre1, Individuo padre2, Random random) {
        List<Ruta> rutasHijo = new ArrayList<>();
        
        for (int i = 0; i < padre1.getRutas().size(); i++) {
            if (random.nextBoolean()) {
                rutasHijo.add(copiarRuta(padre1.getRutas().get(i)));
            } else {
                rutasHijo.add(copiarRuta(padre2.getRutas().get(i)));
            }
        }
        return new Individuo(rutasHijo);
    }

    /**
     * 🛠️ MUTACIÓN CORREGIDA CON GESTIÓN DE ALMACENES
     */
    private void mutar(Individuo individuo, List<PlanVuelo> vuelos, Map<String, Aeropuerto> mapaAeropuertos, Random random) {
        
        // 💡 NUEVO: Reconstruimos el estado actual de los almacenes para este individuo
        // antes de empezar a mutar, así sabemos si realmente hay espacio en el origen.
        Map<String, Integer> capacidadesAlmacenes = new HashMap<>();
        for (Aeropuerto a : mapaAeropuertos.values()) {
            capacidadesAlmacenes.put(a.getCodigo(), a.getCapacidad());
        }
        // Descontamos lo que este individuo ya tiene asignado
        for (Ruta r : individuo.getRutas()) {
            if (r.getEstado() == EstadoRuta.PLANIFICADA) {
                Envio e = r.getEnvio();
                capacidadesAlmacenes.put(e.getAeropuertoOrigen(), capacidadesAlmacenes.getOrDefault(e.getAeropuertoOrigen(), 0) - e.getCantidadMaletas());
                capacidadesAlmacenes.put(e.getAeropuertoDestino(), capacidadesAlmacenes.getOrDefault(e.getAeropuertoDestino(), 0) - e.getCantidadMaletas());
            }
        }

        for (int i = 0; i < individuo.getRutas().size(); i++) {
            if (random.nextDouble() < PROB_MUTACION) {
                Ruta rutaActual = individuo.getRutas().get(i);
                Envio envio = rutaActual.getEnvio();
                
                // 💡 NUEVO: Devolvemos el espacio de la ruta vieja al almacén antes de crear la nueva
                if (rutaActual.getEstado() == EstadoRuta.PLANIFICADA) {
                    capacidadesAlmacenes.put(envio.getAeropuertoOrigen(), capacidadesAlmacenes.getOrDefault(envio.getAeropuertoOrigen(), 0) + envio.getCantidadMaletas());
                    capacidadesAlmacenes.put(envio.getAeropuertoDestino(), capacidadesAlmacenes.getOrDefault(envio.getAeropuertoDestino(), 0) + envio.getCantidadMaletas());
                }

                // Generamos una ruta completamente nueva para esta maleta pasándole las capacidades
                Ruta rutaMutada = generarRutaAleatoriaValida(envio, vuelos, mapaAeropuertos, capacidadesAlmacenes, random);
                
                // 💡 NUEVO: Si la mutación fue exitosa, descontamos el espacio de la nueva ruta
                if (rutaMutada.getEstado() == EstadoRuta.PLANIFICADA) {
                    capacidadesAlmacenes.put(envio.getAeropuertoOrigen(), capacidadesAlmacenes.getOrDefault(envio.getAeropuertoOrigen(), 0) - envio.getCantidadMaletas());
                    capacidadesAlmacenes.put(envio.getAeropuertoDestino(), capacidadesAlmacenes.getOrDefault(envio.getAeropuertoDestino(), 0) - envio.getCantidadMaletas());
                }

                // Sobrescribimos en el índice exacto para no alterar el tamaño total de envíos
                individuo.getRutas().set(i, rutaMutada); 
            }
        }
    }

    /**
     * Método auxiliar para la mutación: Crea una ruta aleatoria (Origen -> Destino)
     */
    private Ruta generarRutaAleatoriaValida(Envio envio, List<PlanVuelo> todosLosVuelos, Map<String, Aeropuerto> mapaAeropuertos, Map<String, Integer> capacidadesAlmacenes, Random random) {
        Ruta nuevaRuta = new Ruta();
        nuevaRuta.setEnvio(envio);
        nuevaRuta.setEstado(EstadoRuta.SIN_RUTA);
        List<PlanVuelo> vuelosAsignados = new ArrayList<>();
        
        String origen = envio.getAeropuertoOrigen();
        String destino = envio.getAeropuertoDestino();

        // 💡 NUEVO: Verificar si el almacén de origen tiene capacidad al momento de registrar el envío
        if (capacidadesAlmacenes.getOrDefault(origen, 0) < envio.getCantidadMaletas()) {
            return nuevaRuta; // Se devuelve vacía (SIN_RUTA) porque el origen colapsó
        }

        // 1. Intentar buscar un vuelo directo
        List<PlanVuelo> vuelosDirectos = todosLosVuelos.stream()
                .filter(v -> v.getOrigen().equals(origen) && v.getDestino().equals(destino) && !v.isCancelado())
                .collect(Collectors.toList());

        if (!vuelosDirectos.isEmpty()) {
            PlanVuelo vElegido = vuelosDirectos.get(random.nextInt(vuelosDirectos.size()));
            vuelosAsignados.add(vElegido);
            nuevaRuta.setVuelos(vuelosAsignados);
            nuevaRuta.setEstado(EstadoRuta.PLANIFICADA);
            
            // CALCULAR TIEMPO REAL: Incluyendo la espera en origen
            long tiempoEsperaOrigen = calcularTiempoEsperaOrigen(envio, vElegido.getHoraSalida());
            long duracion = calcularDuracionMinutos(vElegido, mapaAeropuertos);
            
            nuevaRuta.setTiempoTotalMinutos(tiempoEsperaOrigen + duracion + 10); // +10 min recojo
            return nuevaRuta;
        }

        // 2. Si no hay directo, intentar buscar con 1 escala
        List<PlanVuelo> vuelosDesdeOrigen = todosLosVuelos.stream()
                .filter(v -> v.getOrigen().equals(origen) && !v.isCancelado())
                .collect(Collectors.toList());

        Collections.shuffle(vuelosDesdeOrigen, random);

        for (PlanVuelo vuelo1 : vuelosDesdeOrigen) {
            String aeropuertoEscala = vuelo1.getDestino();
            
            List<PlanVuelo> vuelosDesdeEscala = todosLosVuelos.stream()
                    .filter(v -> v.getOrigen().equals(aeropuertoEscala) && v.getDestino().equals(destino) && !v.isCancelado())
                    .collect(Collectors.toList());
                    
            if (!vuelosDesdeEscala.isEmpty()) {
                PlanVuelo vuelo2 = vuelosDesdeEscala.get(random.nextInt(vuelosDesdeEscala.size()));
                vuelosAsignados.add(vuelo1);
                vuelosAsignados.add(vuelo2);
                
                nuevaRuta.setVuelos(vuelosAsignados);
                nuevaRuta.setEstado(EstadoRuta.PLANIFICADA);
                
                // CALCULAR TIEMPO REAL (Con escalas): Espera en origen + Vuelo 1 + Espera escala + Vuelo 2
                long tiempoEsperaOrigen = calcularTiempoEsperaOrigen(envio, vuelo1.getHoraSalida());
                long duracion1 = calcularDuracionMinutos(vuelo1, mapaAeropuertos);
                long tiempoEspera = calcularTiempoEspera(vuelo1.getHoraLlegada(), vuelo2.getHoraSalida());
                long duracion2 = calcularDuracionMinutos(vuelo2, mapaAeropuertos);
                
                nuevaRuta.setTiempoTotalMinutos(tiempoEsperaOrigen + duracion1 + tiempoEspera + duracion2 + 10);
                return nuevaRuta;
            }
        }

        // Si no encontró rutas, se devuelve vacía (SIN_RUTA)
        nuevaRuta.setVuelos(vuelosAsignados);
        nuevaRuta.setTiempoTotalMinutos(0);
        return nuevaRuta;
    }

    // =====================================================================
    // MÉTODOS MATEMÁTICOS PARA CALCULAR EL TIEMPO CON HUSOS HORARIOS (GMT)
    // =====================================================================

    private long calcularTiempoEsperaOrigen(Envio envio, String horaSalidaVuelo) {
        int minRegistro = (envio.getHoraRegistro() * 60) + envio.getMinutoRegistro();
        int minSalidaVuelo = convertirAMinutos(horaSalidaVuelo);

        if (minSalidaVuelo < minRegistro) {
            minSalidaVuelo += 24 * 60; // Si el vuelo sale al día siguiente
        }
        return minSalidaVuelo - minRegistro;
    }

    private long calcularDuracionMinutos(PlanVuelo vuelo, Map<String, Aeropuerto> mapaAeropuertos) {
        int minSalidaLocal = convertirAMinutos(vuelo.getHoraSalida());
        int minLlegadaLocal = convertirAMinutos(vuelo.getHoraLlegada());

        int gmtOrigen = mapaAeropuertos.get(vuelo.getOrigen()).getGmt();
        int gmtDestino = mapaAeropuertos.get(vuelo.getDestino()).getGmt();

        int minSalidaGMT = minSalidaLocal - (gmtOrigen * 60);
        int minLlegadaGMT = minLlegadaLocal - (gmtDestino * 60);

        while (minLlegadaGMT < minSalidaGMT) {
            minLlegadaGMT += 24 * 60;
        }

        return minLlegadaGMT - minSalidaGMT;
    }

    private long calcularTiempoEspera(String horaLlegada, String horaSalida) {
        int minLlegada = convertirAMinutos(horaLlegada);
        int minSalida = convertirAMinutos(horaSalida);
        if (minSalida < minLlegada) {
            minSalida += 24 * 60;
        }
        return minSalida - minLlegada;
    }

    private int convertirAMinutos(String hora) {
        String[] partes = hora.split(":");
        return Integer.parseInt(partes[0]) * 60 + Integer.parseInt(partes[1]);
    }
    
    // =====================================================================

    private Individuo obtenerMejorIndividuo(Poblacion poblacion) {
        return poblacion.getIndividuos().stream()
                .max(Comparator.comparingDouble(Individuo::getFitness))
                .orElse(poblacion.getIndividuos().get(0));
    }

    private Individuo copiarIndividuo(Individuo original) {
        List<Ruta> rutasCopia = original.getRutas().stream()
                .map(this::copiarRuta)
                .collect(Collectors.toList());
        Individuo copia = new Individuo(rutasCopia);
        copia.setFitness(original.getFitness());
        return copia;
    }

    private Ruta copiarRuta(Ruta original) {
        Ruta copia = new Ruta();
        copia.setEnvio(original.getEnvio());
        copia.setVuelos(new ArrayList<>(original.getVuelos() != null ? original.getVuelos() : new ArrayList<>()));
        copia.setTiempoTotalMinutos(original.getTiempoTotalMinutos());
        copia.setEstado(original.getEstado());
        copia.setIndiceVueloActual(original.getIndiceVueloActual());
        return copia;
    }

    private void imprimirResumen(Individuo mejor) {
        long sinRuta = mejor.getRutas().stream()
            .filter(r -> r.getEstado() == EstadoRuta.SIN_RUTA).count();
        long planificadas = mejor.getRutas().stream()
            .filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA).count();

        System.out.println("Total envíos al final (Debe ser igual al original): " + mejor.getRutas().size());
        System.out.println("Planificados: " + planificadas);
        System.out.println("Sin ruta: " + sinRuta);
    }
}