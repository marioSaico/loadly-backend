Backend Loadly

Guía completa para el README — Flujo de trabajo con GitHub
1. Clonar el proyecto (solo la primera vez)
bashgit clone https://github.com/marioSaico/loadly-backend.git
cd loadly-backend
2. Antes de empezar a trabajar cada día
Siempre jalar los últimos cambios antes de tocar código:
bashgit pull
3. Crear una rama para trabajar
Nunca trabajar directamente en main. Cada funcionalidad o algoritmo va en su propia rama:
bashgit checkout -b nombre-de-la-rama
Por ejemplo:
bashgit checkout -b feature/algoritmo-genetico
git checkout -b feature/algoritmo-aco
git checkout -b feature/modelo-aeropuerto
4. Guardar cambios en tu rama
bashgit add .
git commit -m "descripción breve de lo que hiciste"
git push origin nombre-de-la-rama
5. Fusionar tu rama con main cuando termines
bashgit checkout main
git pull
git merge nombre-de-la-rama
git push
6. Eliminar la rama una vez fusionada
bashgit branch -d nombre-de-la-rama

Convención de nombres para commits
Para que todos escriban commits de manera consistente, te recomiendo usar estos prefijos:

feat: cuando agregas algo nuevo → feat: agregar modelo PlanVuelo
fix: cuando corriges un error → fix: corregir parseo de hora en PlanVuelo
refactor: cuando reorganizas código → refactor: mover clases a paquete algoritmo
docs: cuando actualizas documentación → docs: actualizar README


Convención de nombres para ramas

feature/nombre → para nuevas funcionalidades
fix/nombre → para correcciones
docs/nombre → para documentación
