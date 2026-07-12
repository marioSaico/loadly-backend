package com.loadly.backend.service;
 
import com.loadly.backend.algoritmo.genetico.Individuo;
import com.loadly.backend.dto.AeropuertoDTO;
import com.loadly.backend.dto.EnvioDTO;
import com.loadly.backend.dto.PlanVueloDTO;
import com.loadly.backend.loader.*;
import com.loadly.backend.model.*;
import com.loadly.backend.service.database.AeropuertoService;
import com.loadly.backend.service.database.EnvioService;
import com.loadly.backend.service.database.PlanVueloService;
import lombok.Data;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;
 
@Service
public class DataService {
 
    private final EnvioLoader envioLoader;
    private final AeropuertoService aeropuertoService;
    private final PlanVueloService planVueloService;
    private final EnvioService envioService;
 
    private List<Aeropuerto> aeropuertos;
    private List<PlanVuelo> vuelos;
 
    private Map<String, Aeropuerto> mapaAeropuertos;
    private Map<String, List<PlanVuelo>> mapaVuelosPorOrigen;
 
    // Controladores de Capacidad Dinámica
    private Map<String, Integer> capacidadDinamicaAlmacenes;
    private Map<String, Integer> capacidadDinamicaVuelos;
 
    // Solo guarda los envíos que AÚN NO TIENEN RUTA (Backlog)
    private List<Envio> enviosEnEspera = new ArrayList<>();

    // Cola de envíos que perdieron su ruta por cancelación — tienen prioridad sobre el backlog normal
    private List<Envio> colaReplanificacion = new ArrayList<>();
 
    // Guarda el histórico de los que ya se planificaron
    private List<Ruta> rutasPlanificadasHistorico = new ArrayList<>();
 
    // La Agenda de Eventos
    private PriorityQueue<EventoLogistico> agendaEventos = new PriorityQueue<>();
 
    private static final DateTimeFormatter FORMATO_RELOJ =
            DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");
 
    public DataService(EnvioLoader envioLoader,
                       AeropuertoService aeropuertoService,
                       PlanVueloService planVueloService,
                       EnvioService envioService) {
        this.envioLoader      = envioLoader;
        this.aeropuertoService = aeropuertoService;
        this.planVueloService = planVueloService;
        this.envioService = envioService;
    }
 
    public void inicializar() {
        // Cargar aeropuertos desde base de datos e indexarlos
        this.aeropuertos = cargarAeropuertosDesdeBD();
        this.mapaAeropuertos = this.aeropuertos.stream()
                .collect(Collectors.toMap(Aeropuerto::getCodigo, a -> a));

        // Cargar vuelos desde base de datos e indexarlos
        this.vuelos = cargarVuelosDesdeBD();
 
        this.mapaVuelosPorOrigen = vuelos.stream()
                .filter(v -> !v.isCancelado())
                .collect(Collectors.groupingBy(PlanVuelo::getOrigen));
 
        this.capacidadDinamicaAlmacenes = new HashMap<>();
        for (Aeropuerto a : aeropuertos) {
            capacidadDinamicaAlmacenes.put(a.getCodigo(), a.getCapacidad());
        }
 
        this.capacidadDinamicaVuelos = new HashMap<>();
        for (PlanVuelo v : vuelos) {
            capacidadDinamicaVuelos.put(claveVuelo(v), v.getCapacidad());
        }

        this.enviosEnEspera.clear();
        this.rutasPlanificadasHistorico.clear();
        this.agendaEventos.clear();
        this.colaReplanificacion.clear();
        
        System.out.println("Aeropuertos cargados de BD: " + aeropuertos.size());
        System.out.println("Vuelos cargados e indexados: "      + vuelos.size());
        logMemoria("Inicialización");
    }

    /**
     * Carga los aeropuertos desde la base de datos y los convierte al modelo interno
     */
    public List<Aeropuerto> cargarAeropuertosDesdeBD() {
        List<AeropuertoDTO> listaDTO = aeropuertoService.obtenerTodos();
        List<Aeropuerto> nuevaLista = new ArrayList<>();

        for (AeropuertoDTO dto : listaDTO) {
            nuevaLista.add(convertirAModelo(dto));
        }
        return nuevaLista;
    }

    /**
     * Mapper de AeropuertoDTO a Aeropuerto (modelo interno)
     */
    private Aeropuerto convertirAModelo(AeropuertoDTO dto) {
        if (dto == null) return null;
        Aeropuerto model = new Aeropuerto();
        model.setId(dto.getIdAeropuerto() != null ? dto.getIdAeropuerto() : 0);
        model.setCodigo(dto.getCodigo());
        model.setCiudad(dto.getCiudad());
        model.setPais(dto.getPais());
        model.setAbreviatura(dto.getAbreviatura());
        model.setGmt(dto.getGmt() != null ? dto.getGmt() : 0);
        model.setCapacidad(dto.getCapacidad() != null ? dto.getCapacidad() : 0);
        model.setLatitud(dto.getLatitud() != null ? dto.getLatitud() : 0.0);
        model.setLongitud(dto.getLongitud() != null ? dto.getLongitud() : 0.0);
        model.setContinente(dto.getContinente());
        return model;
    }

    /**
     * Carga los vuelos desde la base de datos y los convierte al modelo interno
     */
    public List<PlanVuelo> cargarVuelosDesdeBD() {
        List<PlanVueloDTO> listaDTO = planVueloService.obtenerTodos();
        List<AeropuertoDTO> todosAeropuertos = aeropuertoService.obtenerTodos();
        
        // Mapa ID -> Código para resolver origen/destino
        Map<Integer, String> mapaIdACodigo = todosAeropuertos.stream()
                .collect(Collectors.toMap(AeropuertoDTO::getIdAeropuerto, AeropuertoDTO::getCodigo));

        List<PlanVuelo> nuevaLista = new ArrayList<>();
        for (PlanVueloDTO dto : listaDTO) {
            nuevaLista.add(convertirAModelo(dto, mapaIdACodigo));
        }
        return nuevaLista;
    }

    /**
     * Mapper de PlanVueloDTO a PlanVuelo (modelo interno)
     */
    private PlanVuelo convertirAModelo(PlanVueloDTO dto, Map<Integer, String> mapaIdACodigo) {
        if (dto == null) return null;
        PlanVuelo model = new PlanVuelo();
        
        model.setOrigen(mapaIdACodigo.getOrDefault(dto.getIdAeropuertoOrigen(), "UNKNOWN"));
        model.setDestino(mapaIdACodigo.getOrDefault(dto.getIdAeropuertoDestino(), "UNKNOWN"));
        
        // Formatear LocalTime a HH:mm
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm");
        model.setHoraSalida(dto.getHoraSalida() != null ? dto.getHoraSalida().format(timeFormatter) : "00:00");
        model.setHoraLlegada(dto.getHoraLlegada() != null ? dto.getHoraLlegada().format(timeFormatter) : "00:00");
        
        model.setCapacidad(dto.getCapacidad() != null ? dto.getCapacidad() : 0);
        model.setCancelado(dto.getCancelado() != null && dto.getCancelado());
        
        return model;
    }
 
    public void guardarArchivosEnDisco(MultipartFile[] archivos) throws IOException {
        // Obtenemos la ruta raíz del usuario (Funciona igual en Windows, Mac o Linux)
        String directorioUsuario = System.getProperty("user.home");
        
        // Creamos la ruta hacia la carpeta 'simulador_envios'
        Path rutaDirectorioAlmacenamiento = Paths.get(directorioUsuario, "simulador_envios");

        // 1. VERIFICAR Y LIMPIAR LA CARPETA
        if (Files.exists(rutaDirectorioAlmacenamiento)) {
            // Si la carpeta ya existe, agarramos todos los archivos viejos y los borramos uno por uno
            File carpeta = rutaDirectorioAlmacenamiento.toFile();
            File[] archivosViejos = carpeta.listFiles();
            if (archivosViejos != null) {
                for (File archivoViejo : archivosViejos) {
                    archivoViejo.delete(); 
                }
            }
            System.out.println("[DISCO] Carpeta limpiada exitosamente de cargas anteriores.");
            System.out.println("[DISCO] Directorio de almacenamiento ubicado en: " + rutaDirectorioAlmacenamiento.toAbsolutePath() + " quedo limpio");
        } else {
            // Si no existe (es la primera vez que se usa el sistema), la creamos
            Files.createDirectories(rutaDirectorioAlmacenamiento);
            System.out.println("[DISCO] Directorio de almacenamiento creado en: " + rutaDirectorioAlmacenamiento.toAbsolutePath());
        }

        // Iteramos sobre los archivos que llegaron desde el frontend
        for (MultipartFile archivo : archivos) {
            if (archivo.isEmpty()) continue;

            String nombreArchivo = archivo.getOriginalFilename();
            if (nombreArchivo == null) continue;

            // Armamos la ruta final donde descansará este archivo de texto específico
            Path rutaDestino = rutaDirectorioAlmacenamiento.resolve(nombreArchivo);

            // Copiamos el contenido al disco. REPLACE_EXISTING sirve por si el usuario 
            // vuelve a subir la carpeta, para que se actualicen los archivos sin dar error.
            Files.copy(archivo.getInputStream(), rutaDestino, StandardCopyOption.REPLACE_EXISTING);
        }
        
        System.out.println("[DISCO] ¡Éxito! " + archivos.length + " archivos asentados permanentemente en el almacenamiento secundario.");
    }


    public List<Envio> obtenerEnviosPendientes(String inicioEscenario, String fechaHoraActual, String fechaHoraLimite, int k) {
        // =====================================================================
        // NUEVO BLOQUE: CALCULAR SLA DINÁMICO PARA REPLANIFICACIONES
        // =====================================================================
        // 1. Sabemos exactamente la hora a la que el Planificador está despertando:
        DateTimeFormatter formato = DateTimeFormatter.ofPattern("yyyyMMdd-HH-mm");
        LocalDateTime relojEjecucionPlanner = LocalDateTime.parse(fechaHoraActual, formato);

        // 2. Actualizamos la "bomba de tiempo" de cada maleta afectada
        for (Envio envio : this.colaReplanificacion) {
            Aeropuerto aorig = this.mapaAeropuertos.get(envio.getAeropuertoOrigen());
            Aeropuerto adest = this.mapaAeropuertos.get(envio.getAeropuertoDestino());
            
            // SLA Base: 24h (mismo continente) o 48h (distinto continente) en minutos
            long slaTotal = (aorig != null && adest != null && 
                             aorig.getContinente().equals(adest.getContinente())) ? 24 * 60 : 48 * 60;

            // =========================================================================
            // LÓGICA DE DISPONIBILIDAD: ¿Está en el aire o en el almacén?
            // =========================================================================
            LocalDateTime tiempoDisponibilidad;
            
            if (envio.getHoraDisponibleReplanificacion() != null && 
                envio.getHoraDisponibleReplanificacion().isAfter(relojEjecucionPlanner)) {
                // Escenario B: Está en el aire. Disponible recién cuando aterrice.
                tiempoDisponibilidad = envio.getHoraDisponibleReplanificacion();
            } else {
                // Escenario A: Está en el almacén (origen o intermedio). Disponible AHORA.
                tiempoDisponibilidad = relojEjecucionPlanner;
                envio.setHoraDisponibleReplanificacion(tiempoDisponibilidad);
            }

            // =========================================================================
            // CÁLCULO PERFECTO DEL SLA
            // =========================================================================
            // Consumido: Desde el registro original hasta el momento en que podrá tomar un nuevo vuelo
            long minutosConsumidos = java.time.temporal.ChronoUnit.MINUTES.between(envio.getTiempoRegistroGMT(), tiempoDisponibilidad);
            int slaRestante = (int) (slaTotal - minutosConsumidos);
            envio.setSlaRestanteMinutos(slaRestante);

            if (slaRestante <= 0) {
                System.out.println("[ALERTA CRÍTICA - COLAPSO LOGÍSTICO] El envío " + envio.getIdEnvio() + " ya expiró en la cola de espera. SLA Restante: " + slaRestante + " min.");
            }
        }
        
        List<Envio> enviosRecienLlegados;
        if (k == 1) {
            enviosRecienLlegados = envioLoader.obtenerEnviosPendientesDesdeBD(fechaHoraActual, this.aeropuertos);
        } else {
            String directorioUsuario = System.getProperty("user.home");
            String rutaCarpetaArchivos = java.nio.file.Paths.get(directorioUsuario, "simulador_envios").toString();
            // Llamamos al loader pasándole la ruta real del disco
            enviosRecienLlegados = envioLoader.cargarPendientes(
            rutaCarpetaArchivos, // <--- AQUÍ ESTÁ LA MAGIA CORREGIDA
            inicioEscenario,
            fechaHoraLimite,
            this.aeropuertos
            );
        }
        // =====================================================================
        // CARGA DE NUEVOS ENVÍOS DESDE EL DISCO (STREAMING)
        // =====================================================================
        // Obtenemos la ruta dinámica donde guardamos los archivos en la carga inicial
        

        if (!enviosRecienLlegados.isEmpty()) {
            this.enviosEnEspera.addAll(enviosRecienLlegados);
        }

        // Construir lista final: replanificaciones primero (ordenadas por SLA restante ASC),
        // luego backlog normal
        List<Envio> resultado = new ArrayList<>();

        List<Envio> replanOrdenadas = new ArrayList<>(this.colaReplanificacion);
        replanOrdenadas.sort(Comparator.comparingInt(e ->
            e.getSlaRestanteMinutos() != null ? e.getSlaRestanteMinutos() : Integer.MAX_VALUE
        ));
        resultado.addAll(replanOrdenadas);
        resultado.addAll(this.enviosEnEspera);

        return resultado;
    }

    

    public void marcarEnviosPlanificadosEnBD(Individuo plan) {
        if (plan == null || plan.getRutas() == null) return;

        for (Ruta ruta : plan.getRutas()) {
            if (ruta.getEstado() == EstadoRuta.PLANIFICADA) {
                envioService.marcarPlanificado(ruta.getEnvio().getIdEnvio());
            }
        }
    }


    /**
     * Cancela un vuelo y gestiona el impacto en rutas ya planificadas.
     *
     * Lógica:
     * - Si la ocurrencia de HOY ya despegó: solo se cancelan días futuros.
     * - Para cada envío cuya ruta usa ese vuelo en un tramo AÚN NO DESPEGADO:
     *   1. Determina desde qué aeropuerto replanificar (destino del último tramo ya despegado)
     *   2. Revierte capacidades de todos los tramos futuros (vuelos + almacenes)
     *   3. Cancela eventos de agendaEventos correspondientes
     * - Marca el vuelo como cancelado en mapaVuelosPorOrigen para futuros A*
     *
     * @param claveVuelo         Formato "ORIG-DEST-HH:mm"
     * @param relojSimuladoActual Momento actual del reloj simulado (GMT)
     */
    public ResultadoCancelacion  cancelarVuelo(String claveVuelo, LocalDateTime relojSimuladoActual) {
        List<Envio> enviosAfectados = new ArrayList<>();
        Map<String, Integer> indicesAfectados = new HashMap<>();

        System.out.println("[CANCELACION DEBUG] Buscando vuelo con clave: " + claveVuelo);
        System.out.println("[CANCELACION DEBUG] Total vuelos en lista: " + vuelos.size());

        // Buscar vuelos que contengan OPKC o EDDI
        List<String> vuelosCoincidentes = vuelos.stream()
            .filter(v -> claveVuelo(v).contains("OPKC") || claveVuelo(v).contains("EDDI"))
            .map(this::claveVuelo)
            .collect(java.util.stream.Collectors.toList());
        //System.out.println("[CANCELACION DEBUG] Vuelos con OPKC o EDDI: " + vuelosCoincidentes);

        // 1. Encontrar el PlanVuelo correspondiente a la clave
        PlanVuelo vueloCancelado = null;
        for (PlanVuelo v : vuelos) {
            if (claveVuelo(v).equals(claveVuelo)) {
                vueloCancelado = v;
                break;
            }
        }
        if (vueloCancelado == null) {
            System.out.println("[CANCELACION DEBUG] Vuelo no encontrado con clave exacta: " + claveVuelo);
            return new ResultadoCancelacion(enviosAfectados, indicesAfectados); // vuelo no encontrado
        }
        System.out.println("[CANCELACION DEBUG] Vuelo encontrado: " + claveVuelo(vueloCancelado));

        // 2. Determinar si la ocurrencia de HOY ya despegó
        // Convertir horaSalida del vuelo a minutos GMT
        int gmtOrigen = mapaAeropuertos.get(vueloCancelado.getOrigen()) != null
            ? mapaAeropuertos.get(vueloCancelado.getOrigen()).getGmt() : 0;
        int minSalidaLocal = convertirAMinutos(vueloCancelado.getHoraSalida());
        int minSalidaGMT = ((minSalidaLocal - (gmtOrigen * 60)) % 1440 + 1440) % 1440;
        // Normalizar a minutos del día
        int minRelojActualGMT = relojSimuladoActual.getHour() * 60 + relojSimuladoActual.getMinute();
        // La ocurrencia de hoy ya despegó si el reloj actual (en minutos del día) supera la salida GMT
        boolean hoyYaDespego = minRelojActualGMT > minSalidaGMT;

        // 3. Marcar vuelo como cancelado en el modelo y en el mapa
        vueloCancelado.setCancelado(true);
        planVueloService.marcarComoCancelado(
            vueloCancelado.getOrigen(),
            vueloCancelado.getDestino(),
            vueloCancelado.getHoraSalida()
        );
        // Eliminar de mapaVuelosPorOrigen para que el A* no lo use
        List<PlanVuelo> vuelosDelOrigen = mapaVuelosPorOrigen.get(vueloCancelado.getOrigen());
        if (vuelosDelOrigen != null) {
            vuelosDelOrigen.removeIf(v -> claveVuelo(v).equals(claveVuelo));
        }

        // 4. Revisar todas las rutas planificadas en busca de envíos afectados
        List<Ruta> rutasAEliminarDelHistorico = new ArrayList<>();

        for (Ruta ruta : rutasPlanificadasHistorico) {
            if (ruta.getEstado() != EstadoRuta.PLANIFICADA) continue;

            List<PlanVuelo> vuelosRuta = ruta.getVuelos();
            Envio envio = ruta.getEnvio();

            // Calcular despegues reales de cada tramo para saber cuáles ya ocurrieron
            LocalDateTime[] despegues = calcularDespeguesRuta(envio, vuelosRuta);

            // Encontrar el índice del tramo que usa el vuelo cancelado y aún no despegó
            int indiceAfectado = -1;
            for (int i = 0; i < vuelosRuta.size(); i++) {
                boolean esElVuelo = claveVuelo(vuelosRuta.get(i)).equals(claveVuelo);
                //System.out.printf(claveVuelo + " vs " + claveVuelo(vuelosRuta.get(i)) + " -> esElVuelo: " + esElVuelo + "\n");
                boolean aunNoDespego = despegues[i].isAfter(relojSimuladoActual);
                //System.out.printf("Tramo %d: despegue=%s, relojActual=%s, aunNoDespego=%b%n",
                //        i, despegues[i], relojSimuladoActual, aunNoDespego);

                // Si el vuelo de hoy ya despegó, solo nos interesan los usos en días futuros
                if (esElVuelo && aunNoDespego) {
                    if (hoyYaDespego) {
                        // Solo afecta si el despegue de ese tramo es en un día distinto al de hoy
                        if (!despegues[i].toLocalDate().equals(relojSimuladoActual.toLocalDate())) {
                            indiceAfectado = i;
                            break;
                        }
                    } else {
                        indiceAfectado = i;
                        break;
                    }
                }
            }
            if (indiceAfectado > 0) {
                // Recalcular la llegada al aeropuerto desde donde se replanifica
                // Es simplemente el despegue del tramo anterior + su duración
                LocalDateTime llegadaAlIntermedio = despegues[indiceAfectado - 1]
                    .plusMinutes(calcularDuracionVueloGMT(vuelosRuta.get(indiceAfectado - 1)));
                envio.setHoraDisponibleReplanificacion(llegadaAlIntermedio);
            } else {
                envio.setHoraDisponibleReplanificacion(null);
            }

            if (indiceAfectado == -1) continue; // esta ruta no se ve afectada

            // 5. Determinar desde qué aeropuerto replanificar
            // Es el destino del último tramo que YA despegó antes del tramo afectado
            String aeropuertoDesde;
            if (indiceAfectado == 0) {
                // Ningún tramo anterior despegó → desde el origen original
                aeropuertoDesde = envio.getAeropuertoOrigen();
            } else {
                // El tramo anterior ya despegó o está en el aire → desde su destino
                aeropuertoDesde = vuelosRuta.get(indiceAfectado - 1).getDestino();
            }

            // 6. Revertir capacidades de los tramos futuros (desde indiceAfectado en adelante)
            // También hay que eliminar los eventos de agenda correspondientes
            for (int i = indiceAfectado; i < vuelosRuta.size(); i++) {
                PlanVuelo v = vuelosRuta.get(i);
                String claveV = claveVuelo(v);

                // Restaurar capacidad del vuelo
                int capActual = capacidadDinamicaVuelos.getOrDefault(claveV, 0);
                int capMax = v.getCapacidad();
                capacidadDinamicaVuelos.put(claveV, Math.min(capActual + envio.getCantidadMaletas(), capMax));
            }

            // 7. Eliminar eventos de agenda de tramos futuros
            final int indiceFinal = indiceAfectado;
            final Envio envioFinal = envio;
            final List<PlanVuelo> vuelosRutaFinal = vuelosRuta;

            agendaEventos.removeIf(evento -> {
                if (!evento.getHoraEvento().isAfter(relojSimuladoActual)) return false;
                if (evento.getCantidad() != envioFinal.getCantidadMaletas()) return false;

                for (int i = indiceFinal; i < vuelosRutaFinal.size(); i++) {
                    PlanVuelo v = vuelosRutaFinal.get(i);
                    boolean esTramoEnAire = (i == indiceFinal && indiceFinal > 0);

                    if (esTramoEnAire) {
                        // Las maletas VAN A LLEGAR a v.getOrigen() (tramo anterior en el aire)
                        // → proteger RECIBE_CARGA en v.getOrigen() (esa llegada va a ocurrir)
                        // → SÍ eliminar LIBERA_ALMACEN en v.getOrigen() (no va a despegar)
                        // → SÍ eliminar RECIBE_CARGA en v.getDestino() (nunca va a llegar)
                        // → SÍ eliminar LIBERA_ALMACEN en v.getDestino()
                        // → SÍ eliminar RESET_VUELO de este tramo

                        boolean esLlegadaProtegida = "RECIBE_CARGA".equals(evento.getTipo())
                            && evento.getCodigo().equals(v.getOrigen());
                        if (esLlegadaProtegida) continue; // NO eliminar

                        if (evento.getCodigo().equals(v.getOrigen()) ||
                            evento.getCodigo().equals(v.getDestino()) ||
                            evento.getCodigo().equals(claveVuelo(v))) {
                            return true;
                        }
                    } else {
                        // Tramo completamente futuro → eliminar todo
                        if (evento.getCodigo().equals(v.getOrigen()) ||
                            evento.getCodigo().equals(v.getDestino()) ||
                            evento.getCodigo().equals(claveVuelo(v))) {
                            return true;
                        }
                    }
                }
                return false;
            });

            // 8. Preparar envío para replanificación
            envio.setSlaRestanteMinutos(null);
            envio.setAeropuertoReplanificacionDesde(aeropuertoDesde);
            envio.setPlanificado(false);

            String claveEnvio = envio.getClaveUnica(); // incluye el lote, evita colisiones entre pedazos
            indicesAfectados.put(claveEnvio, indiceAfectado);
            colaReplanificacion.add(envio);
            rutasAEliminarDelHistorico.add(ruta);
            enviosAfectados.add(envio);
        }

        // Limpiar del histórico las rutas que se van a replanificar
        rutasPlanificadasHistorico.removeAll(rutasAEliminarDelHistorico);

        System.out.printf("[CANCELACION] Vuelo %s cancelado. Envíos afectados: %d%n",
                claveVuelo, enviosAfectados.size());

        return new ResultadoCancelacion(enviosAfectados, indicesAfectados);
    }

    /**
     * Calcula los despegues reales GMT de cada tramo de una ruta.
     */
    private LocalDateTime[] calcularDespeguesRuta(Envio envio, List<PlanVuelo> vuelosRuta) {
        int n = vuelosRuta.size();
        LocalDateTime[] despegues = new LocalDateTime[n];
        LocalDateTime[] llegadas  = new LocalDateTime[n];

        if (envio.getHoraDisponibleReplanificacion() != null) {
            // Ya está en GMT (así se guarda siempre este campo)
            LocalDateTime cursorGMT = envio.getHoraDisponibleReplanificacion();
            long espera = calcularEsperaEnEscala(cursorGMT, vuelosRuta.get(0).getHoraSalida());
            despegues[0] = cursorGMT.plusMinutes(espera);
        } else {
            String origenReal = (envio.getAeropuertoReplanificacionDesde() != null)
                ? envio.getAeropuertoReplanificacionDesde()
                : envio.getAeropuertoOrigen();
            Aeropuerto aeroOrigen = mapaAeropuertos.get(origenReal);
            int gmtOrigen = (aeroOrigen != null) ? aeroOrigen.getGmt() : 0;

            LocalDateTime fechaRegistroGMT = LocalDateTime.of(
                LocalDate.parse(envio.getFechaRegistro(), DateTimeFormatter.ofPattern("yyyyMMdd")),
                LocalTime.of(envio.getHoraRegistro(), envio.getMinutoRegistro())
            ).minusHours(gmtOrigen); // ← el fix

            long esperaPrimero = calcularMinutosHastaVuelo(
                envio.getHoraRegistro(), envio.getMinutoRegistro(),
                vuelosRuta.get(0).getHoraSalida()
            );
            despegues[0] = fechaRegistroGMT.plusMinutes(esperaPrimero);
        }
        llegadas[0]  = despegues[0].plusMinutes(calcularDuracionVueloGMT(vuelosRuta.get(0)));

        for (int i = 1; i < n; i++) {
            long espera = calcularEsperaEnEscala(llegadas[i-1], vuelosRuta.get(i).getHoraSalida());
            despegues[i] = llegadas[i-1].plusMinutes(espera);
            llegadas[i]  = despegues[i].plusMinutes(calcularDuracionVueloGMT(vuelosRuta.get(i)));
        }

        return despegues;
    }

    // =========================================================================
    // 2. CONFIRMACIÓN Y RESERVA DE CAPACIDADES
    // =========================================================================
 
    /**
     * Confirma el mejor plan del GA y actualiza todas las capacidades dinámicas.
     *
     * Para cada ruta PLANIFICADA:
     *  - Decrementa el almacén ORIGEN inmediatamente (las maletas ya están ahí).
     *  - Agenda LIBERA_ALMACEN en ORIGEN cuando despega el primer vuelo.
     *
     *  Por cada vuelo de la ruta:
     *  - Decrementa la capacidad del vuelo.
     *  - Agenda RESET_VUELO 24h después del despegue (el mismo vuelo al día
     *    siguiente tiene capacidad fresca).
     *  - Decrementa el almacén DESTINO del vuelo (escala o destino final).
     *  - Si es escala: agenda LIBERA_ALMACEN cuando despega el siguiente vuelo.
     *  - Si es destino final: agenda LIBERA_ALMACEN 10 min después de llegar
     *    (tiempo de recojo por el cliente).
     */
    public void confirmarPlanYActualizarCapacidades(Individuo mejorPlan,
                                                     String fechaHoraReloj) {
        LocalDateTime relojActual = LocalDateTime.parse(fechaHoraReloj, FORMATO_RELOJ);
        List<Envio> enviosPlanificadosEnEstaRonda = new ArrayList<>();
        Map<String, Integer> maletasCubiertasPorEnvio = new HashMap<>();
        
        for (Ruta ruta : mejorPlan.getRutas()) {
            if (ruta.getEstado() != EstadoRuta.PLANIFICADA) continue;
 
            Envio envio = ruta.getEnvio();
            maletasCubiertasPorEnvio.merge(envio.getClaveBase(), envio.getCantidadMaletas(), Integer::sum);
            enviosPlanificadosEnEstaRonda.add(envio);
            rutasPlanificadasHistorico.add(ruta);
 
            List<PlanVuelo> vuelosRuta = ruta.getVuelos();
            int n = vuelosRuta.size();
 
            // ── Calcular datetimes reales de despegue y llegada por vuelo ──
            LocalDateTime[] despegues = new LocalDateTime[n];
            LocalDateTime[] llegadas  = new LocalDateTime[n];
 
            LocalDateTime fechaDisponible;
            long esperaPrimero;

            if (envio.getHoraDisponibleReplanificacion() != null) {
                // Replanificación desde aeropuerto intermedio
                // El envío llega a ese aeropuerto en horaDisponibleReplanificacion
                fechaDisponible = envio.getHoraDisponibleReplanificacion();
                long espera = calcularEsperaEnEscala(fechaDisponible, vuelosRuta.get(0).getHoraSalida());
                despegues[0] = fechaDisponible.plusMinutes(espera);
            } else {
                // Envío normal o replanificación desde origen original
                String origenReal = (envio.getAeropuertoReplanificacionDesde() != null)
                        ? envio.getAeropuertoReplanificacionDesde()
                        : envio.getAeropuertoOrigen();
                Aeropuerto aeroOrigen = mapaAeropuertos.get(origenReal);
                int gmtOrigen = (aeroOrigen != null) ? aeroOrigen.getGmt() : 0;

                fechaDisponible = LocalDateTime.of(
                    LocalDate.parse(envio.getFechaRegistro(), DateTimeFormatter.ofPattern("yyyyMMdd")),
                    LocalTime.of(envio.getHoraRegistro(), envio.getMinutoRegistro())
                ).minusHours(gmtOrigen); // ← el fix

                esperaPrimero = calcularMinutosHastaVuelo(
                    envio.getHoraRegistro(), envio.getMinutoRegistro(),
                    vuelosRuta.get(0).getHoraSalida());
                despegues[0] = fechaDisponible.plusMinutes(esperaPrimero);
            }
            llegadas[0]  = despegues[0].plusMinutes(
                    calcularDuracionVueloGMT(vuelosRuta.get(0)));
 
            // Vuelos siguientes: espera desde la llegada del vuelo anterior
            for (int i = 1; i < n; i++) {
                long espera = calcularEsperaEnEscala(
                        llegadas[i - 1], vuelosRuta.get(i).getHoraSalida());
                despegues[i] = llegadas[i - 1].plusMinutes(espera);
                llegadas[i]  = despegues[i].plusMinutes(
                        calcularDuracionVueloGMT(vuelosRuta.get(i)));
            }
 
            // ── 1. Almacén ORIGEN ─────────────────────────────────────────
            // Decrementa ahora (las maletas ocupan espacio hasta que despega)
            if (envio.getHoraDisponibleReplanificacion() == null) {
                // Envío normal O replanificación desde origen original (indiceAfectado == 0)
                // Las maletas están físicamente en el almacén origen → decrementar
                String almacenInicio = (envio.getAeropuertoReplanificacionDesde() != null)
                    ? envio.getAeropuertoReplanificacionDesde()
                    : envio.getAeropuertoOrigen();
                decrementarAlmacen(almacenInicio, envio.getCantidadMaletas());
                int capMaxOrigen = capacidadOriginalAlmacen(almacenInicio);
                agendaEventos.add(new EventoLogistico(
                    despegues[0], "LIBERA_ALMACEN",
                    almacenInicio, envio.getCantidadMaletas(), capMaxOrigen));
            } else {
                // Replanificación desde aeropuerto intermedio con tramo en el aire
                // El RECIBE_CARGA ya fue agendado cuando se confirmó la ruta original
                // Solo agendamos el LIBERA_ALMACEN cuando despegue el nuevo primer vuelo
                String almacenIntermedio = envio.getAeropuertoReplanificacionDesde();
                int capMaxIntermedio = capacidadOriginalAlmacen(almacenIntermedio);
                agendaEventos.add(new EventoLogistico(
                    despegues[0], "LIBERA_ALMACEN",
                    almacenIntermedio, envio.getCantidadMaletas(), capMaxIntermedio));
            }
 
            // ── 2. Vuelos y almacenes intermedios / destino ───────────────
            for (int i = 0; i < n; i++) {
                PlanVuelo vuelo  = vuelosRuta.get(i);
                String    claveV = claveVuelo(vuelo);

                // Decrementar capacidad del vuelo
                int capActualVuelo = capacidadDinamicaVuelos
                        .getOrDefault(claveV, vuelo.getCapacidad());
                capacidadDinamicaVuelos.put(claveV,
                        capActualVuelo - envio.getCantidadMaletas());

                // RESET_VUELO: el mismo vuelo mañana tiene capacidad fresca.
                // Se programa 24h después del despegue de hoy.
                agendaEventos.add(new EventoLogistico(
                        despegues[i].plusHours(24), "RESET_VUELO",
                        claveV,
                        envio.getCantidadMaletas(), vuelo.getCapacidad()));

                // IMPORTANTE: No decrementamos el almacén DESTINO ahora.
                // Ese almacén recibirá espacio CUANDO el envío llegue (evento RECIBE_CARGA).
                // Así evitamos bloquear capacidad antes de que el envío realmente llegue.
                int capMaxDestino = capacidadOriginalAlmacen(vuelo.getDestino());

                if (i < n - 1) {
                    // Almacén INTERMEDIO: 
                    // 1. RECIBE_CARGA cuando llega (decrementa capacidad disponible)
                    // 2. LIBERA_ALMACEN cuando despega el siguiente vuelo (devuelve espacio)
                    agendaEventos.add(new EventoLogistico(
                            llegadas[i], "RECIBE_CARGA",
                            vuelo.getDestino(),
                            envio.getCantidadMaletas(), capMaxDestino));
                    agendaEventos.add(new EventoLogistico(
                            despegues[i + 1], "LIBERA_ALMACEN",
                            vuelo.getDestino(),
                            envio.getCantidadMaletas(), capMaxDestino));
                } else {
                    // Almacén DESTINO FINAL:
                    // 1. RECIBE_CARGA cuando llega (decrementa capacidad disponible)
                    // 2. LIBERA_ALMACEN 10 min después de llegar (cliente recolecta)
                    agendaEventos.add(new EventoLogistico(
                            llegadas[i], "RECIBE_CARGA",
                            vuelo.getDestino(),
                            envio.getCantidadMaletas(), capMaxDestino));
                    agendaEventos.add(new EventoLogistico(
                            llegadas[i].plusMinutes(10), "LIBERA_ALMACEN",
                            vuelo.getDestino(),
                            envio.getCantidadMaletas(), capMaxDestino));
                }
            }
        }
 
        Set<String> clavesTocadas = new HashSet<>();
        for (Ruta ruta : mejorPlan.getRutas()) clavesTocadas.add(ruta.getEnvio().getClaveBase());

        for (String clave : clavesTocadas) {
            Envio original = buscarPorClaveBase(clave); // en enviosEnEspera o colaReplanificacion
            if (original == null) continue;

            int cubiertas = maletasCubiertasPorEnvio.getOrDefault(clave, 0);
            if (cubiertas >= original.getCantidadMaletas()) {
                enviosEnEspera.remove(original);
                colaReplanificacion.remove(original);
            } else if (cubiertas > 0) {
                // Cubierto a medias: se reduce lo pendiente y se reintenta el resto en la siguiente iteración
                original.setCantidadMaletas(original.getCantidadMaletas() - cubiertas);
            }
            // si cubiertas == 0, el envío se queda igual en el backlog, como hoy
        }
        
        logMemoria("Planificación Finalizada");
    }
 
    // =========================================================================
    // 3. LA AGENDA DE EVENTOS (Control del Tiempo)
    // =========================================================================
 
    /**
     * Procesa todos los eventos vencidos hasta el reloj actual.
     *
     * LIBERA_ALMACEN → devuelve espacio al almacén correspondiente.
     * RESET_VUELO    → restaura la capacidad del vuelo para el día siguiente.
     */
    public void procesarEventosDelReloj(String fechaHoraRelojActual) {
        LocalDateTime relojActual =
                LocalDateTime.parse(fechaHoraRelojActual, FORMATO_RELOJ);
 
        while (!agendaEventos.isEmpty() &&
               !agendaEventos.peek().getHoraEvento().isAfter(relojActual)) {

            EventoLogistico evento = agendaEventos.poll();

            if ("LIBERA_ALMACEN".equals(evento.getTipo())) {
                int capActual = capacidadDinamicaAlmacenes
                        .getOrDefault(evento.getCodigo(), 0);
                int nueva = Math.min(
                        capActual + evento.getCantidad(),
                        evento.getCapacidadMaxima());
                capacidadDinamicaAlmacenes.put(evento.getCodigo(), nueva);

            } else if ("RECIBE_CARGA".equals(evento.getTipo())) {
                // Cuando el envío llega a un almacén, decrementa su capacidad disponible.
                // Esto refleja que ahora ocupa espacio físico en el almacén.
                int capActual = capacidadDinamicaAlmacenes
                        .getOrDefault(evento.getCodigo(), 0);
                int nueva = Math.max(0, capActual - evento.getCantidad());
                capacidadDinamicaAlmacenes.put(evento.getCodigo(), nueva);

            } else if ("RESET_VUELO".equals(evento.getTipo())) {
                // Restaura la cantidad decrementada sin pasarse de la cap máx
                int capActual = capacidadDinamicaVuelos
                        .getOrDefault(evento.getCodigo(), 0);
                int nueva = Math.min(
                        capActual + evento.getCantidad(),
                        evento.getCapacidadMaxima());
                capacidadDinamicaVuelos.put(evento.getCodigo(), nueva);
            }
        }
    }
 
    // =========================================================================
    // 4. RESET ESTADO
    // =========================================================================
 
    public void resetEstado() {
        this.capacidadDinamicaAlmacenes.clear();
        this.capacidadDinamicaVuelos.clear();
        this.enviosEnEspera.clear();
        this.rutasPlanificadasHistorico.clear();
        this.agendaEventos.clear();
        this.colaReplanificacion.clear();
        // Restaurar vuelos cancelados durante la simulación
        planVueloService.desmarcarTodosCancelados();
        this.envioLoader.limpiarTodo();
        System.out.println(">>> MEMORIA LIBERADA: Simulación/Planificación finalizada. Objetos eliminados del Heap.");
        logMemoria("Post-Limpieza");
    }
 
    // =========================================================================
    // 5. CLASE INTERNA — EVENTO LOGÍSTICO
    // =========================================================================
 
    @Data
    private static class EventoLogistico implements Comparable<EventoLogistico> {
        private LocalDateTime horaEvento;
        private String tipo;           // LIBERA_ALMACEN | RESET_VUELO
        private String codigo;         // código aeropuerto o clave vuelo
        private int    cantidad;       // maletas a liberar / restaurar
        private int    capacidadMaxima; // cap máx para no pasarse al restaurar
 
        public EventoLogistico(LocalDateTime horaEvento, String tipo,
                               String codigo, int cantidad, int capacidadMaxima) {
            this.horaEvento      = horaEvento;
            this.tipo            = tipo;
            this.codigo          = codigo;
            this.cantidad        = cantidad;
            this.capacidadMaxima = capacidadMaxima;
        }
 
        @Override
        public int compareTo(EventoLogistico o) {
            return this.horaEvento.compareTo(o.horaEvento);
        }
    }
 
    // =========================================================================
    // 6. MÉTODOS AUXILIARES
    // =========================================================================
 
    /** Decrementa la capacidad dinámica de un almacén. */
    private void decrementarAlmacen(String codigoAeropuerto, int maletas) {
        int capActual = capacidadDinamicaAlmacenes.getOrDefault(codigoAeropuerto, 0);
        capacidadDinamicaAlmacenes.put(codigoAeropuerto, capActual - maletas);
    }
 
    /** Devuelve la capacidad física original de un aeropuerto. */
    private int capacidadOriginalAlmacen(String codigoAeropuerto) {
        Aeropuerto a = mapaAeropuertos.get(codigoAeropuerto);
        return (a != null) ? a.getCapacidad() : Integer.MAX_VALUE;
    }
 
    /**
     * Calcula la duración real del vuelo en minutos (en GMT),
     * corrigiendo diferencia de husos horarios entre origen y destino.
     */
    private long calcularDuracionVueloGMT(PlanVuelo vuelo) {
        int minSalidaLoc  = convertirAMinutos(vuelo.getHoraSalida());
        int minLlegadaLoc = convertirAMinutos(vuelo.getHoraLlegada());
        Aeropuerto ao = mapaAeropuertos.get(vuelo.getOrigen());
        Aeropuerto ad = mapaAeropuertos.get(vuelo.getDestino());
        int gmtO = (ao != null) ? ao.getGmt() : 0;
        int gmtD = (ad != null) ? ad.getGmt() : 0;
        int minSalidaGMT  = minSalidaLoc  - (gmtO * 60);
        int minLlegadaGMT = minLlegadaLoc - (gmtD * 60);
        while (minLlegadaGMT < minSalidaGMT) minLlegadaGMT += 1440;
        return minLlegadaGMT - minSalidaGMT;
    }
 
    /**
     * Calcula los minutos de espera en una escala:
     * desde la hora de llegada del vuelo anterior hasta la salida del siguiente.
     */
    private long calcularEsperaEnEscala(LocalDateTime horaLlegada,
                                         String horaSalidaSiguiente) {
        int minLlegada = horaLlegada.getHour() * 60 + horaLlegada.getMinute();
        int minSalida  = convertirAMinutos(horaSalidaSiguiente);
        if (minSalida < minLlegada) minSalida += 1440;
        return minSalida - minLlegada;
    }
 
    /** Calcula minutos desde la hora de registro del envío hasta la salida del vuelo. */
    private long calcularMinutosHastaVuelo(int horaReg, int minReg,
                                            String horaSalidaVuelo) {
        int minRegistroAbs = (horaReg * 60) + minReg;
        int minSalidaAbs   = convertirAMinutos(horaSalidaVuelo);
        if (minSalidaAbs < minRegistroAbs) minSalidaAbs += 1440;
        return minSalidaAbs - minRegistroAbs;
    }
 
    private int convertirAMinutos(String hora) {
        String[] p = hora.split(":");
        return Integer.parseInt(p[0]) * 60 + Integer.parseInt(p[1]);
    }
 
    private String claveVuelo(PlanVuelo vuelo) {
        return vuelo.getOrigen() + "-" + vuelo.getDestino() + "-" + vuelo.getHoraSalida();
    }

    private Envio buscarPorClaveBase(String clave) {
        for (Envio e : enviosEnEspera) if (e.getClaveBase().equals(clave)) return e;
        for (Envio e : colaReplanificacion) if (e.getClaveBase().equals(clave)) return e;
        return null;
    }
 
    // =========================================================================
    // 7. GETTERS
    // =========================================================================
 
    public List<Aeropuerto>              getAeropuertos()                { return aeropuertos; }
    public List<PlanVuelo>               getVuelos()                     { return vuelos; }
    public Map<String, Aeropuerto>       getMapaAeropuertos()            { return mapaAeropuertos; }
    public Map<String, List<PlanVuelo>>  getMapaVuelosPorOrigen()        { return mapaVuelosPorOrigen; }
    public Map<String, Integer>          getCapacidadDinamicaAlmacenes() { return capacidadDinamicaAlmacenes; }
    public Map<String, Integer>          getCapacidadDinamicaVuelos()    { return capacidadDinamicaVuelos; }
    public List<Envio>                   getEnviosEnEspera()             { return enviosEnEspera; }
    public List<Ruta>                    getRutasPlanificadasHistorico()  { return rutasPlanificadasHistorico; }
    public List<Envio>                   getColaReplanificacion()        { return colaReplanificacion; }

    // =========================================================================
    // 8. LOGS DE MEMORIA
    // =========================================================================

    public void logMemoria(String tag) {
        Runtime runtime = Runtime.getRuntime();
        long maxMemory = runtime.maxMemory() / (1024 * 1024);
        long totalMemory = runtime.totalMemory() / (1024 * 1024);
        long freeMemory = runtime.freeMemory() / (1024 * 1024);
        long usedMemory = totalMemory - freeMemory;

        System.out.printf("[%s] RAM Usada: %dMB | RAM Total: %dMB | RAM Max: %dMB | Envíos Pendientes: %d | Rutas Histórico: %d%n",
                tag, usedMemory, totalMemory, maxMemory, enviosEnEspera.size() + colaReplanificacion.size(), rutasPlanificadasHistorico.size());
    }

    public static class ResultadoCancelacion {
    public final List<Envio> enviosAfectados;
    public final Map<String, Integer> indicesAfectados; // clave compuesta → indiceAfectado

    public ResultadoCancelacion(List<Envio> enviosAfectados, Map<String, Integer> indicesAfectados) {
        this.enviosAfectados = enviosAfectados;
        this.indicesAfectados = indicesAfectados;
    }
}
}
