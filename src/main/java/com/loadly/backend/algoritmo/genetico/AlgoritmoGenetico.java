package com.loadly.backend.algoritmo.genetico;

import com.loadly.backend.model.*;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.stream.Collectors;

@Component
public class AlgoritmoGenetico {

    // -- Parametros de la Metaheuristica --
    private static final double PROB_CRUCE = 0.85; 
    private static final double PROB_MUTACION = 0.15; 
    private static final int TAMANIO_TORNEO = 3;
    private static final int ELITISMO = 2; 
    private static final int MAX_ESCALAS = 3; 

    public Individuo ejecutar(List<Envio> envios, List<PlanVuelo> vuelos, Map<String, Aeropuerto> mapaAeropuertos,  Map<String, List<PlanVuelo>> mapaVuelosPorOrigen , int tamanoPoblacion, long tiempoLimiteMs) {

        System.out.println("=== GA - Iniciando Optimizacion ===");
        System.out.println("    Envios a procesar: " + envios.size());

        // 1. Inicializacion de la Poblacion (Usando nuestra clase estricta)
        Poblacion poblacion = new Poblacion(tamanoPoblacion);
        poblacion.inicializar(envios, vuelos, mapaAeropuertos); 

        Fitness evaluadorFitness = new Fitness();
        evaluadorFitness.evaluarPoblacion(poblacion, mapaAeropuertos, vuelos);
        
        Individuo mejorIndividuo = obtenerMejorIndividuo(poblacion);
        
        Random random = new Random();
        long tiempoInicio = System.currentTimeMillis();
        int generacionesContadas = 0;

        // 2. Bucle Evolutivo
        while ((System.currentTimeMillis() - tiempoInicio) < tiempoLimiteMs) {
            List<Individuo> nuevaGeneracion = new ArrayList<>();

            // --- ELITISMO ---
            List<Individuo> poblacionOrdenada = new ArrayList<>(poblacion.getIndividuos());
            poblacionOrdenada.sort((i1, i2) -> Double.compare(i2.getFitness(), i1.getFitness()));
            
            for (int i = 0; i < ELITISMO && i < tamanoPoblacion; i++) {
                nuevaGeneracion.add(copiarIndividuo(poblacionOrdenada.get(i)));
            }

            // --- REPRODUCCION ---
            while (nuevaGeneracion.size() < tamanoPoblacion) {
                Individuo padre1 = seleccionTorneo(poblacion, random);
                Individuo padre2 = seleccionTorneo(poblacion, random);

                Individuo hijo;
                if (random.nextDouble() < PROB_CRUCE) {
                    hijo = cruzar(padre1, padre2, random);
                } else {
                    hijo = copiarIndividuo(padre1);
                }

                mutarOptimizado(hijo, mapaVuelosPorOrigen, mapaAeropuertos, random);
                nuevaGeneracion.add(hijo);
            }

            poblacion.setIndividuos(nuevaGeneracion);
            evaluadorFitness.evaluarPoblacion(poblacion, mapaAeropuertos, vuelos);

            Individuo mejorActual = obtenerMejorIndividuo(poblacion);
            if (mejorActual.getFitness() > mejorIndividuo.getFitness()) {
                mejorIndividuo = copiarIndividuo(mejorActual);
            }

            generacionesContadas++;
        }

        System.out.println("=== GA - Finalizado ===");
        System.out.println("    Generaciones procesadas: " + generacionesContadas);
        imprimirResumen(mejorIndividuo);

        return mejorIndividuo;
    }

    // --- LÓGICA DE MUTACIÓN Y BÚSQUEDA ---
    
    private void mutarOptimizado(Individuo individuo, Map<String, List<PlanVuelo>> mapaVuelos, Map<String, Aeropuerto> mapaAeropuertos, Random random) {
        for (int i = 0; i < individuo.getRutas().size(); i++) {
            if (random.nextDouble() < PROB_MUTACION) {
                Envio envio = individuo.getRutas().get(i).getEnvio();
                // 💡 Al mutar, generamos una ruta cronológicamente válida
                individuo.getRutas().set(i, generarRutaMultiEscala(envio, mapaVuelos, mapaAeropuertos, random));
            }
        }
    }

    private Ruta generarRutaMultiEscala(Envio envio, Map<String, List<PlanVuelo>> mapaVuelos, Map<String, Aeropuerto> mapaAeropuertos, Random random) {
        Ruta ruta = new Ruta();
        ruta.setEnvio(envio);
        ruta.setEstado(EstadoRuta.SIN_RUTA);

        String origen = envio.getAeropuertoOrigen();
        String destino = envio.getAeropuertoDestino();

        // 💡 Pasamos "null" como vuelo anterior porque estamos en el aeropuerto de origen
        List<PlanVuelo> camino = encontrarCaminoAleatorio(origen, destino, 0, new HashSet<>(), mapaVuelos, random, null);

        if (camino != null && !camino.isEmpty()) {
            ruta.setVuelos(camino);
            ruta.setEstado(EstadoRuta.PLANIFICADA);
            ruta.setTiempoTotalMinutos(calcularTiempoTotalRuta(envio, camino, mapaAeropuertos));
        }

        return ruta;
    }

    // 💡 AHORA RECIBE "vueloAnterior"
    private List<PlanVuelo> encontrarCaminoAleatorio(String actual, String destino, int profundidad, Set<String> visitados, Map<String, List<PlanVuelo>> mapaVuelos, Random random, PlanVuelo vueloAnterior) {
        if (profundidad > MAX_ESCALAS) return null;
        if (!mapaVuelos.containsKey(actual)) return null;

        List<PlanVuelo> opciones = new ArrayList<>(mapaVuelos.get(actual));
        Collections.shuffle(opciones); 

        // Primero intentar vuelos directos al destino desde aquí
        for (PlanVuelo v : opciones) {
            // 💡 VALIDACIÓN CRUCIAL: Respetar 10 min de escala y evitar viajes al pasado
            if (vueloAnterior != null && !cumpleTiempoEscala(vueloAnterior, v)) {
                continue; 
            }
            if (v.getDestino().equals(destino)) {
                List<PlanVuelo> r = new ArrayList<>();
                r.add(v);
                return r;
            }
        }

        // Si no hay directo, intentar escalas
        visitados.add(actual);
        for (PlanVuelo v : opciones) {
            // 💡 OTRA VEZ: Validar tiempo antes de probar este camino
            if (vueloAnterior != null && !cumpleTiempoEscala(vueloAnterior, v)) {
                continue; 
            }

            if (!visitados.contains(v.getDestino())) {
                // 💡 PASAMOS "v" COMO EL VUELO ANTERIOR PARA LA SIGUIENTE LLAMADA
                List<PlanVuelo> subCamino = encontrarCaminoAleatorio(v.getDestino(), destino, profundidad + 1, visitados, mapaVuelos, random, v);
                if (subCamino != null) {
                    List<PlanVuelo> resultado = new ArrayList<>();
                    resultado.add(v);
                    resultado.addAll(subCamino);
                    return resultado;
                }
            }
        }
        visitados.remove(actual); // Buena práctica: retroceder (backtracking)
        return null;
    }

    // --- MÉTODOS DE APOYO DE TIEMPOS ---

    // 💡 NUEVO MÉTODO IMPORTADO PARA BLOQUEAR VIAJES EN EL TIEMPO
    private boolean cumpleTiempoEscala(PlanVuelo vueloAnterior, PlanVuelo vueloSiguiente) {
        int minLlegada = convertirAMinutos(vueloAnterior.getHoraLlegada());
        int minSalida = convertirAMinutos(vueloSiguiente.getHoraSalida());

        if (minSalida < minLlegada) {
            minSalida += 1440; // Cruzó la medianoche
        }
        return (minSalida - minLlegada) >= 10;
    }

    private long calcularTiempoTotalRuta(Envio envio, List<PlanVuelo> vuelos, Map<String, Aeropuerto> mapaAeropuertos) {
        long tiempo = 0;
        String horaReferencia = null;

        for (int i = 0; i < vuelos.size(); i++) {
            PlanVuelo v = vuelos.get(i);
            if (i == 0) {
                tiempo += calcularTiempoEsperaOrigen(envio, v.getHoraSalida());
            } else {
                tiempo += calcularTiempoEspera(horaReferencia, v.getHoraSalida());
            }
            tiempo += calcularDuracionMinutos(v, mapaAeropuertos);
            horaReferencia = v.getHoraLlegada();
        }
        return tiempo + 10; // +10 por recojo en destino
    }

    private long calcularTiempoEsperaOrigen(Envio envio, String horaSalidaVuelo) {
        int minRegistro = (envio.getHoraRegistro() * 60) + envio.getMinutoRegistro();
        int minSalida = convertirAMinutos(horaSalidaVuelo);
        if (minSalida < minRegistro) minSalida += 1440; 
        return minSalida - minRegistro;
    }

    private long calcularDuracionMinutos(PlanVuelo vuelo, Map<String, Aeropuerto> mapaAeropuertos) {
        int minSalidaLoc = convertirAMinutos(vuelo.getHoraSalida());
        int minLlegadaLoc = convertirAMinutos(vuelo.getHoraLlegada());
        int gmtO = mapaAeropuertos.get(vuelo.getOrigen()).getGmt();
        int gmtD = mapaAeropuertos.get(vuelo.getDestino()).getGmt();
        int minSalidaGMT = minSalidaLoc - (gmtO * 60);
        int minLlegadaGMT = minLlegadaLoc - (gmtD * 60);
        while (minLlegadaGMT < minSalidaGMT) minLlegadaGMT += 1440;
        return minLlegadaGMT - minSalidaGMT;
    }

    private long calcularTiempoEspera(String horaLlegada, String horaSalida) {
        int minLleg = convertirAMinutos(horaLlegada);
        int minSal = convertirAMinutos(horaSalida);
        if (minSal < minLleg) minSal += 1440;
        return minSal - minLleg;
    }

    private int convertirAMinutos(String hora) {
        String[] p = hora.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }

    // --- OPERADORES GENÉTICOS ---

    private Individuo seleccionTorneo(Poblacion poblacion, Random random) {
        Individuo mejorTorneo = null;
        for (int i = 0; i < TAMANIO_TORNEO; i++) {
            int idx = random.nextInt(poblacion.getIndividuos().size());
            Individuo competidor = poblacion.getIndividuos().get(idx);
            if (mejorTorneo == null || competidor.getFitness() > mejorTorneo.getFitness()) {
                mejorTorneo = competidor;
            }
        }
        return mejorTorneo;
    }

    private Individuo cruzar(Individuo padre1, Individuo padre2, Random random) {
        List<Ruta> rutasHijo = new ArrayList<>();
        for (int i = 0; i < padre1.getRutas().size(); i++) {
            rutasHijo.add(copiarRuta(random.nextBoolean() ? padre1.getRutas().get(i) : padre2.getRutas().get(i)));
        }
        return new Individuo(rutasHijo);
    }
    
    private Individuo obtenerMejorIndividuo(Poblacion poblacion) {
        return poblacion.getIndividuos().stream()
                .max(Comparator.comparingDouble(Individuo::getFitness))
                .orElse(poblacion.getIndividuos().get(0));
    }

    private Individuo copiarIndividuo(Individuo original) {
        List<Ruta> rutasCopia = original.getRutas().stream()
                .map(this::copiarRuta).collect(Collectors.toList());
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
        long planificadas = mejor.getRutas().stream().filter(r -> r.getEstado() == EstadoRuta.PLANIFICADA).count();
        System.out.println("    Planificados: " + planificadas + " |    Sin ruta: " + (mejor.getRutas().size() - planificadas));
    }
}