# Análisis de Errores de Optimización - ERROR DE OPTIMIZACIÓN (NO SE PUDO ASIGNAR RUTA)

## Resumen Ejecutivo
Se detectaron errores de colapso en los tres archivos de salida analizados (salida9.txt, salida10.txt, salida11.txt). Todos ocurren en el mismo momento del sistema (2028-01-25 00:30) tras la fase final de optimización ACO.

---

## SALIDA9.txt

### Estado del Sistema ANTES del Error (20 líneas antes)
```
=== ACO - Iniciando Optimización ===
    EnvíAos a procesar: 464
    Hormigas:          10
    Priorización activa: hard-first con A* + topología + tamaño.
    Siembra inicial: ejecutando A* para rutas semilla...
    Siembra inicial completada.
    Fitness inicial: 0.008740
    [ACO] Iter 0 → Nuevo mejor fitness: 0.013432
=== ACO - Finalizado ===
    Iteraciones ejecutadas: 2
    Mejor Fitness:          0.013432
    [ACO] Rescate final: 61 rutas recuperadas, 13 siguen pendientes.
    [ACO] Rescate final aplicado: mejoró la solución y redujo rutas pendientes.
    Rutas PLANIFICADAS:  451
    Rutas INALCANZABLES: 1
    Rutas SIN_RUTA:      12
```

### Backlog (Envíos Pendientes) Justo ANTES del Colapso
- **Rutas Planificadas**: 451 de 464
- **Rutas Inalcanzables**: 1
- **Rutas Sin Ruta**: 12
- **Total Pendientes**: 13 envíos en backlog

### Mensaje de Error Completo
```
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
[!] ¡COLAPSO DETECTADO EN EL SISTEMA!
[!] MOMENTO DEL SISTEMA: 2028-01-25 00:30
[!] TIPO DE FALLO:       ERROR DE OPTIMIZACIÓN (NO SE PUDO ASIGNAR RUTA)
[!] ENVÍO CAUSANTE:      000135013 | RUTA: LBSF->SBBR | MALETAS: 3
[!] DETALLE TÉCNICO:     No se encontró una solución que respete los límites de tiempo y capacidad.
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
```

### Envío Específico que Falló
- **ID**: 000135013
- **Ruta Solicitada**: LBSF → SBBR
- **Maletas**: 3
- **Razón del Fallo**: No se encontró una solución que respete los límites de tiempo y capacidad

### Estado de Capacidades Críticas (líneas previas)
```
| (5) SBBR->SGAS | Sale: 22:05 | Llega: 00:00 | Vuelo: 3/360 | Almacen SBBR: 0/480 |
```
⚠️ **CAPACIDAD AGOTADA**: El almacén SBBR estaba LLENO (0/480) - es decir, 480/480 ocupado

### Patrones de INALCANZABLE
- Rutas INALCANZABLES: **1**
- Esto significa que 1 ruta no se podía alcanzar con ninguna combinación de vuelos disponibles

### Patrones de SIN_RUTA
- Rutas SIN_RUTA: **12**
- 12 envíos no tenían ninguna ruta asignada cuando ocurrió el colapso

### Estado DESPUÉS del Error (10 líneas después)
```
========================================================================================================================
   RESUMEN DEL ESCENARIO - CONSOLIDADO FINAL
========================================================================================================================
 ENVÍO        | MALETAS | RUTA         | TIEMPO     | % SLA  | LÍMITE | ESTADO    | ITINERARIO
------------------------------------------------------------------------------------------------------------------------
```

---

## SALIDA10.txt

### Estado del Sistema ANTES del Error (20 líneas antes)
```
=== ACO - Iniciando Optimización ===
    EnvíAos a procesar: 464
    Hormigas:          10
    Priorización activa: hard-first con A* + topología + tamaño.
    Siembra inicial: ejecutando A* para rutas semilla...
    Siembra inicial completada.
    Fitness inicial: 0.009306
    [ACO] Iter 0 → Nuevo mejor fitness: 0.010702
=== ACO - Finalizado ===
    Iteraciones ejecutadas: 1
    Mejor Fitness:          0.010702
    [ACO] Rescate final: 77 rutas recuperadas, 16 siguen pendientes.
    [ACO] Rescate final aplicado: mejoró la solución y redujo rutas pendientes.
    Rutas PLANIFICADAS:  448
    Rutas INALCANZABLES: 6
    Rutas SIN_RUTA:      10
```

### Backlog (Envíos Pendientes) Justo ANTES del Colapso
- **Rutas Planificadas**: 448 de 464
- **Rutas Inalcanzables**: 6
- **Rutas Sin Ruta**: 10
- **Total Pendientes**: 16 envíos en backlog

### Mensaje de Error Completo
```
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
[!] ¡COLAPSO DETECTADO EN EL SISTEMA!
[!] MOMENTO DEL SISTEMA: 2028-01-25 00:30
[!] TIPO DE FALLO:       ERROR DE OPTIMIZACIÓN (NO SE PUDO ASIGNAR RUTA)
[!] ENVÍO CAUSANTE:      000140744 | RUTA: LKPR->SEQM | MALETAS: 3
[!] DETALLE TÉCNICO:     No se encontró una solución que respete los límites de tiempo y capacidad.
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
```

### Envío Específico que Falló
- **ID**: 000140744
- **Ruta Solicitada**: LKPR → SEQM
- **Maletas**: 3
- **Razón del Fallo**: No se encontró una solución que respete los límites de tiempo y capacidad

### Patrones de INALCANZABLE
- Rutas INALCANZABLES: **6**
- Este archivo tiene el MAYOR número de rutas inalcanzables de los tres

### Patrones de SIN_RUTA
- Rutas SIN_RUTA: **10**
- 10 envíos estaban sin ruta asignada

### Estado DESPUÉS del Error (10 líneas después)
```
========================================================================================================================
   RESUMEN DEL ESCENARIO - CONSOLIDADO FINAL
========================================================================================================================
 ENVÍO        | MALETAS | RUTA         | TIEMPO     | % SLA  | LÍMITE | ESTADO    | ITINERARIO
------------------------------------------------------------------------------------------------------------------------
```

---

## SALIDA11.txt

### Estado del Sistema ANTES del Error (20 líneas antes)
```
=== ACO - Iniciando Optimización ===
    EnvíAos a procesar: 464
    Hormigas:          10
    Priorización activa: hard-first con A* + topología + tamaño.
    Siembra inicial: ejecutando A* para rutas semilla...
    Siembra inicial completada.
    Fitness inicial: 0.010263
    [ACO] Iter 0 → Nuevo mejor fitness: 0.012273
=== ACO - Finalizado ===
    Iteraciones ejecutadas: 1
    Mejor Fitness:          0.012273
    [ACO] Rescate final: 74 rutas recuperadas, 7 siguen pendientes.
    [ACO] Rescate final aplicado: mejoró la solución y redujo rutas pendientes.
    Rutas PLANIFICADAS:  457
    Rutas INALCANZABLES: 4
    Rutas SIN_RUTA:      3
```

### Backlog (Envíos Pendientes) Justo ANTES del Colapso
- **Rutas Planificadas**: 457 de 464
- **Rutas Inalcanzables**: 4
- **Rutas Sin Ruta**: 3
- **Total Pendientes**: 7 envíos en backlog

### Mensaje de Error Completo
```
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
[!] ¡COLAPSO DETECTADO EN EL SISTEMA!
[!] MOMENTO DEL SISTEMA: 2028-01-25 00:30
[!] TIPO DE FALLO:       ERROR DE OPTIMIZACIÓN (NO SE PUDO ASIGNAR RUTA)
[!] ENVÍO CAUSANTE:      000151154 | RUTA: LOWW->SCEL | MALETAS: 2
[!] DETALLE TÉCNICO:     No se encontró una solución que respete los límites de tiempo y capacidad.
!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!!
```

### Envío Específico que Falló
- **ID**: 000151154
- **Ruta Solicitada**: LOWW → SCEL
- **Maletas**: 2
- **Razón del Fallo**: No se encontró una solución que respete los límites de tiempo y capacidad

### Patrones de INALCANZABLE
- Rutas INALCANZABLES: **4**
- Este archivo tiene el MENOR número de rutas inalcanzables

### Patrones de SIN_RUTA
- Rutas SIN_RUTA: **3**
- MENOR cantidad de envíos sin ruta (mejor desempeño general)

### Estado DESPUÉS del Error (10 líneas después)
```
========================================================================================================================
   RESUMEN DEL ESCENARIO - CONSOLIDADO FINAL
========================================================================================================================
 ENVÍO        | MALETAS | RUTA         | TIEMPO     | % SLA  | LÍMITE | ESTADO    | ITINERARIO
------------------------------------------------------------------------------------------------------------------------
```

---

## Análisis Comparativo

### Tabla de Desempeño ACO Pre-Colapso

| Métrica | salida9.txt | salida10.txt | salida11.txt |
|---------|------------|-------------|-------------|
| **Envíos a Procesar** | 464 | 464 | 464 |
| **Rutas Planificadas** | 451 (97.2%) | 448 (96.6%) | 457 (98.5%) ✓ MEJOR |
| **Inalcanzables** | 1 | 6 ✗ PEOR | 4 |
| **Sin Ruta** | 12 | 10 | 3 ✓ MEJOR |
| **Total Backlog** | 13 | 16 ✗ PEOR | 7 ✓ MEJOR |
| **Iteraciones ACO** | 2 | 1 | 1 |
| **Mejor Fitness** | 0.013432 | 0.010702 ✓ MEJOR | 0.012273 |

### Patrones Identificados

#### 1. **Momento del Colapso**
- Todos los colapsos ocurren en el mismo momento: **2028-01-25 00:30**
- Se activan DESPUÉS de la fase de rescate final de ACO
- Ocurren cuando el algoritmo intenta procesar el siguiente envío pendiente

#### 2. **Razón Común de Fallo**
- Todos comparten el mismo patrón: `No se encontró una solución que respete los límites de tiempo y capacidad`
- **Problema de Capacidad**: Los almacenes están LLENOS
- **Problema de Tiempo**: No hay ventanas temporales disponibles

#### 3. **Progresión del Backlog**
- **salida11.txt** (7 pendientes) → Mejor rendimiento, menos pendientes
- **salida9.txt** (13 pendientes) → Rendimiento medio
- **salida10.txt** (16 pendientes) → **Peor rendimiento**, más pendientes

#### 4. **Correlación: Mayor Backlog = Colapso más Inminente**
- salida10.txt tiene el backlog más grande (16) y 6 rutas inalcanzables
- A medida que crece el backlog sin poder asignarse, eventualmente se dispara el error
- El sistema puede continuar hasta cierto umbral, luego falla

---

## Conclusiones Clave

1. **El Colapso es Determinista**: Ocurre siempre en el mismo momento (00:30), sugeriendo que la red de transporte llega a su capacidad máxima

2. **Capacidades Agotadas**: El fallo específico ocurre porque los almacenes y vuelos no tienen capacidad disponible para aceptar más envíos

3. **Envíos Inalcanzables**: El algoritmo detecta de 1-6 rutas que físicamente no pueden ser alcanzadas con las limitaciones de tiempo y capacidad

4. **El Rescate ACO es Efectivo Pero Limitado**:
   - Recupera entre 61-77 rutas en la fase final
   - Reduce significativamente las rutas pendientes
   - Pero no puede evitar el colapso cuando la capacidad está agotada

5. **Mejora Potencial**:
   - salida11.txt muestra mejor rendimiento (7 pendientes vs 16)
   - Esto sugiere que optimizaciones en el algoritmo PUEDEN reducir el backlog
   - Pero el colapso sigue siendo inevitable en la situación de saturación total
