# Loadly Backend

Backend del sistema Loadly para la gestión y planificación de rutas de maletas - Tasf.B2B

## Requisitos previos

- Java 21
- Git

## Configuración inicial (solo la primera vez)

### Clonar el proyecto

    git clone https://github.com/marioSaico/loadly-backend.git
    cd loadly-backend

### Levantar el proyecto (cuando quieras correr lo que codees)

    mvnw spring-boot:run
### Crear tu rama personal

    git checkout -b feature/tu-nombre

Por ejemplo:

    git checkout -b feature/mario
    git checkout -b feature/diego
    git checkout -b feature/marcos

---

## Estructura del proyecto

    com.loadly.backend
    ├── model
    │   ├── Aeropuerto.java        → representa un aeropuerto de la red
    │   ├── PlanVuelo.java         → representa un vuelo disponible
    │   ├── Envio.java             → representa un pedido de maletas
    │   ├── Ruta.java              → representa una ruta completa (secuencia de vuelos)
    │   └── EstadoRuta.java        → enum: PLANIFICADA, EN_TRANSITO, ENTREGADA, RETRASADA, SIN_RUTA
    ├── loader
    │   ├── AeropuertoLoader.java  → lee y parsea el archivo de aeropuertos
    │   ├── PlanVueloLoader.java   → lee y parsea el archivo de planes de vuelo
    │   └── EnvioLoader.java       → lee envíos filtrados por fecha/hora límite (Sc)
    ├── service
    │   └── DataService.java       → carga aeropuertos y vuelos al inicio, provee envíos por Sc
    ├── algoritmo
    │   ├── genetico
    │   │   ├── Individuo.java         → cromosoma (conjunto de rutas para todos los envíos)
    │   │   ├── Poblacion.java         → conjunto de individuos
    │   │   ├── Fitness.java           → calcula qué tan buena es una solución
    │   │   └── AlgoritmoGenetico.java → motor principal del GA
    │   └── aco
    │       └── AlgoritmoACO.java      → motor principal del ACO
    ├── planificador
    │   └── Planificador.java      → orquesta todo usando planificación programada fija
    └── controller
        └── PlanificadorController.java → API REST para conectar con el frontend

## Flujo de trabajo

### Desde la segunda vez en adelante

Jalar los últimos cambios de main por si alguno de nosotros fusiono algo: (se hace cada vez que quieras emepezar a codear)

Cada vez que empieces a trabajar, por si algún compañero hizo un merge mientras tú no estabas trabajando. Así evitas conflictos.
    
    git checkout main
    git pull origin main
    git checkout feature/tu-nombre
    git merge main

### Guardar tus cambios en tu rama

    git add .
    git commit -m "feat: descripción de lo que hiciste"
    git push origin feature/tu-nombre

### Fusionar tu rama con main cuando termines una funcionalidad

Una vez que hayas hecho el push de tus cambios, ve al repositorio en GitHub.
Aparecerá un mensaje que dice "feature/tu-nombre had recent pushes".
Haz clic en "Compare & pull request", escribe una descripción de lo que hiciste
y luego haz clic en "Create pull request". Finalmente haz clic en "Merge pull request"
y confirmas. Listo, tus cambios ya están en main.
