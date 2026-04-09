# Loadly Backend

Backend del sistema Loadly para la gestión y planificación de rutas de maletas - Tasf.B2B

## Requisitos previos

- Java 21
- Git

## Configuración inicial (solo la primera vez)

### Clonar el proyecto
```bash
git clone https://github.com/marioSaico/loadly-backend.git
cd loadly-backend
```

### Levantar el proyecto
```bash
mvnw spring-boot:run
```

## Flujo de trabajo

### 1. Antes de empezar a trabajar, siempre jalar cambios
```bash
git pull
```

### 2. Crear tu rama personal (solo la primera vez)
```bash
git checkout -b feature/tu-nombre
```
Por ejemplo:
```bash
git checkout -b feature/mario
git checkout -b feature/diego
git checkout -b feature/marcos
```

### 3. Cambiarte a tu rama antes de trabajar
```bash
git checkout feature/tu-nombre
```

### 4. Guardar tus cambios en tu rama
```bash
git add .
git commit -m "feat: descripción de lo que hiciste"
git push origin feature/tu-nombre
```

### 5. Fusionar tu rama con main cuando termines una funcionalidad
```bash
git checkout main
git pull
git merge feature/tu-nombre
git push
```