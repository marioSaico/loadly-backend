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

### 1. Antes de empezar a trabajar, siempre jalar cambios

    git pull

### 2. Crear tu rama personal (solo la primera vez)

    git checkout -b feature/tu-nombre

Por ejemplo:

    git checkout -b feature/mario
    git checkout -b feature/diego
    git checkout -b feature/marcos

### 3. Cambiarte a tu rama antes de trabajar

    git checkout feature/tu-nombre

### 4. Verificar en qué rama estás

    git branch

Verás un asterisco * en la rama activa. Por ejemplo:

    * feature/mario
      main

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

## Convención de commits

- feat: cuando agregas algo nuevo. Ejemplo: feat: agregar modelo PlanVuelo
- fix: cuando corriges un error. Ejemplo: fix: corregir parseo de hora
- refactor: cuando reorganizas código. Ejemplo: refactor: mover clases a paquete algoritmo
- docs: cuando actualizas documentación. Ejemplo: docs: actualizar README