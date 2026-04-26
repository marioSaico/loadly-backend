"""
analisis_estadistico.py
Análisis estadístico del experimento Loadly — GA vs ACO.
 
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
print(f"Datos cargados: {df.shape[0]} filas\n")
print("Columnas:", list(df.columns))
 
def sep(titulo):
    print(f"\n{'═'*65}\n  {titulo}\n{'═'*65}")
 
# ══════════════════════════════════════════════════════════════════════
#  1. ESTADÍSTICAS DESCRIPTIVAS
# ══════════════════════════════════════════════════════════════════════
sep("1. ESTADÍSTICAS DESCRIPTIVAS")
 
# Métricas que entran en FO
print("\n--- Métricas de la Función Objetivo ---")
metricas_fo = ["fo", "planificados", "inalcanzable", "sin_ruta", "tiempo_norm_prom"]
resumen_fo = df.groupby(["dataset", "algoritmo"])[metricas_fo].agg(
    ["mean", "median", "std", "min", "max"]
).round(4)
pd.set_option("display.max_columns", None)
pd.set_option("display.width", 220)
print(resumen_fo.to_string())
 
# Métricas descriptivas (fuera de FO)
print("\n--- Métricas descriptivas (fuera de FO) ---")
metricas_desc = ["hops_prom", "tiempo_prom_min", "tiempo_computo_s"]
resumen_desc = df.groupby(["dataset", "algoritmo"])[metricas_desc].agg(
    ["mean", "median", "std"]
).round(4)
print(resumen_desc.to_string())
 
# ══════════════════════════════════════════════════════════════════════
#  2. SHAPIRO-WILK
# ══════════════════════════════════════════════════════════════════════
sep("2. PRUEBA DE NORMALIDAD — Shapiro-Wilk (α=0.05)")
print(f"\n{'Dataset':<12} {'Algoritmo':<10} {'W':<10} {'p-valor':<15} {'¿Normal?'}")
print("-"*55)
 
todos_normales = True
for ds in DATASETS:
    for alg in ["GA", "ACO"]:
        datos = df[(df["dataset"]==ds) & (df["algoritmo"]==alg)]["fo"].values
        if len(datos) < 3:
            print(f"{ds:<12} {alg:<10} {'N/A':<10} {'N/A':<15} N/A")
            continue
        W, p  = stats.shapiro(datos)
        normal = "SÍ" if p > ALPHA else "NO"
        if p <= ALPHA: todos_normales = False
        print(f"{ds:<12} {alg:<10} {W:<10.4f} {p:<15.6e} {normal}")
 
print()
if todos_normales:
    print("→ Todos normales: Mann-Whitney aplica igualmente (más robusto).")
else:
    print("→ Al menos una NO es normal: Mann-Whitney U es la prueba correcta.")
 
# ══════════════════════════════════════════════════════════════════════
#  3. MANN-WHITNEY U
# ══════════════════════════════════════════════════════════════════════
sep("3. PRUEBA DE HIPÓTESIS — Mann-Whitney U (α=0.05)")
print("H₀: No existe diferencia significativa en FO entre GA y ACO")
print("H₁: Sí existe diferencia significativa en FO entre GA y ACO\n")
print(f"{'Dataset':<12} {'U':<10} {'p-valor':<15} {'Rechaza H₀?':<14} {'Mejor':<8} {'Efecto r':<12} {'Magnitud'}")
print("-"*80)
 
for ds in DATASETS:
    ga_fo  = df[(df["dataset"]==ds) & (df["algoritmo"]=="GA") ]["fo"].values
    aco_fo = df[(df["dataset"]==ds) & (df["algoritmo"]=="ACO")]["fo"].values
 
    if len(ga_fo) == 0 or len(aco_fo) == 0:
        print(f"{ds:<12} Sin datos suficientes")
        continue
 
    U, p    = stats.mannwhitneyu(ga_fo, aco_fo, alternative="two-sided")
    rechaza = "SÍ" if p < ALPHA else "NO"
    mejor   = ("GA" if np.median(ga_fo) > np.median(aco_fo) else "ACO") if p < ALPHA else "—"
    r       = 1 - (2*U) / (len(ga_fo)*len(aco_fo))
    mag     = "pequeño" if abs(r)<0.3 else ("mediano" if abs(r)<0.5 else "grande")
 
    print(f"{ds:<12} {U:<10.1f} {p:<15.6e} {rechaza:<14} {mejor:<8} {r:<12.4f} {mag}")
 
# ══════════════════════════════════════════════════════════════════════
#  4. TABLA RESUMEN PARA EL IEN
# ══════════════════════════════════════════════════════════════════════
sep("4. TABLA RESUMEN PARA COPIAR AL IEN")
 
print(f"\n{'Dataset':<12} {'GA FO med':<12} {'ACO FO med':<12} "
      f"{'GA Planif%':<12} {'ACO Planif%':<13} "
      f"{'GA TNorm':<10} {'ACO TNorm':<11} "
      f"{'p-valor':<15} {'Conclusión'}")
print("-"*110)
 
for ds in DATASETS:
    ga  = df[(df["dataset"]==ds) & (df["algoritmo"]=="GA")]
    aco = df[(df["dataset"]==ds) & (df["algoritmo"]=="ACO")]
 
    if len(ga) == 0 or len(aco) == 0:
        continue
 
    _, p = stats.mannwhitneyu(ga["fo"].values, aco["fo"].values, alternative="two-sided")
 
    ga_planif  = (ga["planificados"] / ga["total_envios"] * 100).mean()
    aco_planif = (aco["planificados"] / aco["total_envios"] * 100).mean()
 
    conclusion = ("GA superior" if np.median(ga["fo"].values) > np.median(aco["fo"].values)
                  else "ACO superior") if p < ALPHA else "Sin dif. sig."
 
    print(f"{ds:<12} {ga['fo'].mean():<12.4f} {aco['fo'].mean():<12.4f} "
          f"{ga_planif:<12.1f} {aco_planif:<13.1f} "
          f"{ga['tiempo_norm_prom'].mean():<10.4f} {aco['tiempo_norm_prom'].mean():<11.4f} "
          f"{p:<15.6e} {conclusion}")
 
# ══════════════════════════════════════════════════════════════════════
#  5. EXPORTAR
# ══════════════════════════════════════════════════════════════════════
sep("5. EXPORTANDO ARCHIVOS")
 
resumen_export = df.groupby(["dataset","algoritmo"]).agg(
    fo_media         =("fo",               "mean"),
    fo_mediana       =("fo",               "median"),
    fo_de            =("fo",               "std"),
    planif_media     =("planificados",      "mean"),
    inalc_media      =("inalcanzable",      "mean"),
    sinruta_media    =("sin_ruta",          "mean"),
    tnorm_media      =("tiempo_norm_prom",  "mean"),
    tprom_media      =("tiempo_prom_min",   "mean"),
    hops_media       =("hops_prom",         "mean"),
    tcomputo_media   =("tiempo_computo_s",  "mean")
).round(4)
 
resumen_export.to_csv("estadisticas_descriptivas.csv")
print("✓ estadisticas_descriptivas.csv generado")
print("✓ Análisis completado")
 