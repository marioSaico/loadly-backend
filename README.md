# Loadly Backend

Backend del sistema Loadly para la gestión y planificación de rutas de maletas - Tasf.B2B

## Requisitos previos

- Java 21
- Git

## Configuración inicial (solo la primera vez)

### Clonar el proyecto

    git clone https://github.com/marioSaico/loadly-backend.git
    cd loadly-backend

### Levantar el proyecto

    mvnw spring-boot:run

## Estructura del proyecto

    com.loadly.backend
    ├── model
    │   ├── Aeropuerto.java
    │   ├── PlanVuelo.java
    │   ├── Envio.java
    │   └── Ruta.java
    ├── loader
    │   ├── AeropuertoLoader.java
    │   ├── PlanVueloLoader.java
    │   └── EnvioLoader.java
    ├── algoritmo
    │   ├── genetico
    │   │   ├── Individuo.java
    │   │   ├── Poblacion.java
    │   │   ├── Fitness.java
    │   │   └── AlgoritmoGenetico.java
    │   └── aco
    │       └── AlgoritmoACO.java
    ├── planificador
    │   └── Planificador.java
    └── controller
        └── PlanificadorController.java

## Flujo de trabajo

## Flujo de trabajo

### Primera vez (configuración inicial de tu rama)

1. Clonar el proyecto
    git clone https://github.com/marioSaico/loadly-backend.git
    cd loadly-backend

2. Crear tu rama personal
    git checkout -b feature/tu-nombre

Por ejemplo:
    git checkout -b feature/mario
    git checkout -b feature/diego
    git checkout -b feature/marcos

---

### Desde la segunda vez en adelante

1. Jalar los últimos cambios de main por si tus compañeros fusionaron algo
    git checkout main
    git pull origin main
    git checkout feature/tu-nombre
    git merge main

### 5. Guardar tus cambios en tu rama

    git add .
    git commit -m "feat: descripción de lo que hiciste"
    git push origin feature/tu-nombre

### 6. Fusionar tu rama con main cuando termines una funcionalidad

Una vez que hayas hecho el push de tus cambios, ve al repositorio en GitHub.
Aparecerá un mensaje que dice "feature/tu-nombre had recent pushes".
Haz clic en "Compare & pull request", escribe una descripción de lo que hiciste
y luego haz clic en "Create pull request". Finalmente haz clic en "Merge pull request"
y confirmas. Listo, tus cambios ya están en main.
