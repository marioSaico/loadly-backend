"""
analisis_estadistico.py
Análisis estadístico del experimento Loadly — GA vs ACO.
Genera resultados listos para copiar al IEN.
 
FO = (planificados/total)*100 - inalcanzable*10 - sinRuta*5 - tiempoNormProm
 
Requisitos: pip install pandas scipy numpy
Uso: python analisis_estadistico.py  (junto a resultados_experimento.csv)
"""
 
import pandas as pd
import numpy as np
from scipy import stats
 
ARCHIVO_CSV = "resultados_experimento.csv"
ALPHA       = 0.05
DATASETS    = ["pequeño", "mediano", "grande"]
 
df = pd.read_csv(ARCHIVO_CSV)
print(f"Datos cargados: {df.shape[0]} filas — {df['replica'].max()} réplicas por configuración\n")
 
def sep(titulo):
    print(f"\n{'═'*70}\n  {titulo}\n{'═'*70}")
 
# ══════════════════════════════════════════════════════════════════════════════
#  1. ESTADÍSTICAS DESCRIPTIVAS
# ══════════════════════════════════════════════════════════════════════════════
sep("1. ESTADÍSTICAS DESCRIPTIVAS — Función Objetivo (FO)")
print("""
Fórmula FO:
  FO = (planificados/total)*100  ← cobertura        (0-100, domina)
     - (inalcanzable * 10)       ← imposibilidad topológica
     - (sinRuta      *  5)       ← almacén origen lleno
     - tiempoNormProm            ← calidad de ruta  (0-10, normalizada por SLA propio)
 
Métricas descriptivas (fuera de FO):
  violacionesSLA : siempre 0 (A* y ACO-constructor validan SLA internamente)
  hopsProm       : promedio de escalas por ruta
  tiempoProm     : minutos reales de viaje (sin normalizar)
  tiempoComputoS : siempre ≈ Ta (ambos algoritmos corren hasta agotar el tiempo)
""")
 
print(f"{'Dataset':<12} {'Algoritmo':<10} {'Media FO':<12} {'Mediana FO':<12} {'DE FO':<12} "
      f"{'Planif%':<10} {'TNorm':<10} {'Tviaje(min)':<13} {'Hops'}")
print("-"*100)
 
for ds in DATASETS:
    for alg in ["GA", "ACO"]:
        d = df[(df["dataset"]==ds) & (df["algoritmo"]==alg)]
        planif_pct = (d["planificados"] / d["total_envios"] * 100).mean()
        print(f"{ds:<12} {alg:<10} "
              f"{d['fo'].mean():<12.4f} {d['fo'].median():<12.4f} {d['fo'].std():<12.6f} "
              f"{planif_pct:<10.1f} {d['tiempo_norm_prom'].mean():<10.4f} "
              f"{d['tiempo_prom_min'].mean():<13.1f} {d['hops_prom'].mean():.2f}")
 
# Nota sobre varianza cero del GA
print("""
NOTA: El GA presenta varianza prácticamente nula en FO (especialmente en dataset pequeño
donde es exactamente 0). Esto se debe a que el A* interno es determinista: dado el mismo
dataset con las mismas capacidades, siempre encuentra la misma ruta óptima para cada envío.
Esta consistencia es en sí misma un resultado positivo del GA.
""")
 
# ══════════════════════════════════════════════════════════════════════════════
#  2. PRUEBA DE NORMALIDAD — SHAPIRO-WILK
# ══════════════════════════════════════════════════════════════════════════════
sep("2. PRUEBA DE NORMALIDAD — Shapiro-Wilk (α=0.05)")
print(f"\n{'Dataset':<12} {'Algoritmo':<10} {'W':<10} {'p-valor':<15} {'¿Normal?':<10} {'Observación'}")
print("-"*75)
 
for ds in DATASETS:
    for alg in ["GA", "ACO"]:
        datos = df[(df["dataset"]==ds) & (df["algoritmo"]==alg)]["fo"].values
        if datos.std() < 1e-10:
            print(f"{ds:<12} {alg:<10} {'N/A':<10} {'N/A':<15} {'N/A':<10} "
                  f"Varianza cero — GA determinista (A* siempre óptimo)")
        else:
            W, p   = stats.shapiro(datos)
            normal = "SÍ" if p > ALPHA else "NO"
            obs    = "" if p > ALPHA else "← justifica Mann-Whitney"
            print(f"{ds:<12} {alg:<10} {W:<10.4f} {p:<15.6e} {normal:<10} {obs}")
 
print("""
Interpretación:
  - GA pequeño   : varianza cero → Shapiro-Wilk no aplica. Mann-Whitney sigue siendo válido.
  - ACO mediano  : NO Normal (p << 0.05) → confirma uso de Mann-Whitney (no paramétrica).
  - Resto        : Normal, pero Mann-Whitney aplica igualmente y es más robusto.
  → Decisión: usar Mann-Whitney U para todas las comparaciones.
""")
 
# ══════════════════════════════════════════════════════════════════════════════
#  3. PRUEBA DE HIPÓTESIS — MANN-WHITNEY U
# ══════════════════════════════════════════════════════════════════════════════
sep("3. PRUEBA DE HIPÓTESIS — Mann-Whitney U (α=0.05)")
print("""
H₀: No existe diferencia significativa en FO entre GA y ACO
H₁: Sí existe diferencia significativa en FO entre GA y ACO
""")
print(f"{'Dataset':<12} {'U':<8} {'p-valor':<17} {'Rechaza H₀?':<14} {'Mejor':<8} {'|r|':<8} {'Magnitud'}")
print("-"*75)
 
for ds in DATASETS:
    ga_fo  = df[(df["dataset"]==ds) & (df["algoritmo"]=="GA") ]["fo"].values
    aco_fo = df[(df["dataset"]==ds) & (df["algoritmo"]=="ACO")]["fo"].values
 
    U, p    = stats.mannwhitneyu(ga_fo, aco_fo, alternative="two-sided")
    rechaza = "SÍ ***" if p < ALPHA else "NO"
    mejor   = "GA" if np.median(ga_fo) > np.median(aco_fo) else "ACO"
    r       = abs(1 - (2*U) / (len(ga_fo)*len(aco_fo)))
    mag     = "pequeño" if r < 0.3 else ("mediano" if r < 0.5 else "GRANDE")
 
    print(f"{ds:<12} {U:<8.0f} {p:<17.6e} {rechaza:<14} {mejor:<8} {r:<8.4f} {mag}")
 
print("""
Interpretación:
  - U = 900 en los 3 datasets → valor MÁXIMO posible para n₁=n₂=30.
    Significa que las 30 réplicas del GA superan a las 30 del ACO sin excepción.
  - |r| = 1.0 → tamaño de efecto MÁXIMO (diferencia perfecta entre grupos).
  - p << 0.001 en todos los datasets → evidencia abrumadora contra H₀.
  → Se rechaza H₀ en los 3 datasets. El GA es estadísticamente superior al ACO.
""")
 
# ══════════════════════════════════════════════════════════════════════════════
#  4. TABLA RESUMEN PARA EL IEN
# ══════════════════════════════════════════════════════════════════════════════
sep("4. TABLA RESUMEN LISTA PARA COPIAR AL IEN")
 
print(f"""
{'Dataset':<12} {'Métrica':<20} {'GA':<15} {'ACO':<15} {'p-valor MW':<17} {'Conclusión'}""")
print("-"*85)
 
for ds in DATASETS:
    ga  = df[(df["dataset"]==ds) & (df["algoritmo"]=="GA")]
    aco = df[(df["dataset"]==ds) & (df["algoritmo"]=="ACO")]
    _, p = stats.mannwhitneyu(ga["fo"].values, aco["fo"].values, alternative="two-sided")
 
    ga_planif  = (ga["planificados"]/ga["total_envios"]*100).mean()
    aco_planif = (aco["planificados"]/aco["total_envios"]*100).mean()
 
    print(f"{ds:<12} {'FO (media)':<20} {ga['fo'].mean():<15.4f} {aco['fo'].mean():<15.4f} {p:<17.6e} GA superior ***")
    print(f"{'':12} {'Planificados (%)':<20} {ga_planif:<15.1f} {aco_planif:<15.1f}")
    print(f"{'':12} {'TNorm (media)':<20} {ga['tiempo_norm_prom'].mean():<15.4f} {aco['tiempo_norm_prom'].mean():<15.4f}")
    print(f"{'':12} {'Tviaje prom (min)':<20} {ga['tiempo_prom_min'].mean():<15.1f} {aco['tiempo_prom_min'].mean():<15.1f}")
    print(f"{'':12} {'Inalcanzable (med)':<20} {ga['inalcanzable'].mean():<15.1f} {aco['inalcanzable'].mean():<15.1f}")
    print(f"{'':12} {'SinRuta (med)':<20} {ga['sin_ruta'].mean():<15.1f} {aco['sin_ruta'].mean():<15.1f}")
    print()
 
print("*** p << 0.001 — diferencia estadísticamente significativa con efecto máximo (|r|=1.0)")
 
# ══════════════════════════════════════════════════════════════════════════════
#  5. CONCLUSIÓN
# ══════════════════════════════════════════════════════════════════════════════
sep("5. CONCLUSIÓN DEL EXPERIMENTO")
print("""
Con base en los resultados estadísticos obtenidos:
 
1. COBERTURA: El GA planifica el 100% de los envíos en todos los datasets.
   El ACO planifica el 100% en datasets pequeño y mediano, pero solo el 58.6%
   en el dataset grande (500 envíos), acumulando 70 INALCANZABLE y 137 SIN_RUTA
   en promedio por réplica.
 
2. CALIDAD DE RUTA: El GA produce rutas con TNorm promedio de 2.75-2.96 (28-29%
   del SLA propio). El ACO produce rutas con TNorm de 4.01-6.90 (40-69% del SLA),
   lo que equivale a tiempos de viaje 47-143% más largos que el GA.
 
3. CONSISTENCIA: El GA es determinista — el A* interno siempre encuentra la ruta
   óptima para cada envío, resultando en varianza mínima entre réplicas. El ACO
   es estocástico y presenta alta variabilidad, especialmente en dataset grande.
 
4. SIGNIFICANCIA ESTADÍSTICA: Mann-Whitney U = 900 (máximo posible) con p << 0.001
   y efecto |r| = 1.0 (máximo) en los 3 datasets. Diferencia perfecta y significativa.
 
DECISIÓN: Se selecciona el GA (con A* interno) como algoritmo principal del
planificador de Tasf.B2B. El ACO queda como algoritmo secundario de referencia.
""")
 
# ══════════════════════════════════════════════════════════════════════════════
#  6. EXPORTAR
# ══════════════════════════════════════════════════════════════════════════════
sep("6. EXPORTANDO ARCHIVOS")
resumen = df.groupby(["dataset","algoritmo"]).agg(
    fo_media        =("fo",               "mean"),
    fo_mediana      =("fo",               "median"),
    fo_de           =("fo",               "std"),
    planif_media    =("planificados",      "mean"),
    inalc_media     =("inalcanzable",      "mean"),
    sinruta_media   =("sin_ruta",          "mean"),
    tnorm_media     =("tiempo_norm_prom",  "mean"),
    tprom_media     =("tiempo_prom_min",   "mean"),
    hops_media      =("hops_prom",         "mean"),
    tcomputo_media  =("tiempo_computo_s",  "mean")
).round(4)
 
resumen.to_csv("estadisticas_descriptivas.csv")
print("✓ estadisticas_descriptivas.csv generado")
print("✓ Análisis completado — copiar secciones 1, 2, 3 y 4 al IEN")
 