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

## Flujo de trabajo diario

### 1. Antes de empezar a trabajar, siempre jalar cambios
```bash
git pull
```

### 2. Crear una rama para tu funcionalidad
```bash
git checkout -b feature/nombre-de-lo-que-vas-a-hacer
```

### 3. Guardar tus cambios
```bash
git add .
git commit -m "feat: descripción de lo que hiciste"
git push origin nombre-de-la-rama
```

### 4. Fusionar tu rama con main cuando termines
```bash
git checkout main
git pull
git merge nombre-de-la-rama
git push
```

### 5. Eliminar la rama una vez fusionada
```bash
git branch -d nombre-de-la-rama
```

## Convención de commits

| Prefijo | Cuándo usarlo | Ejemplo |
|---|---|---|
| feat: | Cuando agregas algo nuevo | feat: agregar modelo PlanVuelo |
| fix: | Cuando corriges un error | fix: corregir parseo de hora |
| refactor: | Cuando reorganizas código | refactor: mover clases a paquete algoritmo |
| docs: | Cuando actualizas documentación | docs: actualizar README |

## Convención de ramas

| Prefijo | Cuándo usarlo |
|---|---|
| feature/ | Para nuevas funcionalidades |
| fix/ | Para correcciones |
| docs/ | Para documentación |