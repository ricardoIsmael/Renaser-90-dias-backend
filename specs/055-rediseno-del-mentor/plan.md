# Plan de implementación: Rediseño del rol de mentor (fase 1)

**Rama**: `055-rediseno-del-mentor` | **Fecha**: 2026-11-17 | **Spec**: [spec.md](spec.md)

## Resumen

Ordenar el recorrido del mentor en Mis grupos → Mi grupo → ficha. La ficha muestra la semana (sábado a viernes) y el mes con el color de cada día, los hábitos del día por pilar con su evidencia, y una nota privada. Del lado del servidor se agrega poco: dos lecturas del mentor (la semana, que reutiliza el armado de la ficha del líder, y los colores de los días), el campo `pilar` en las obligaciones y las notas privadas. Todo lo demás reutiliza endpoints y componentes que ya existen (ver [research.md](research.md)).

## Contexto técnico

**Lenguaje**: Java 25 + Spring Boot (backend); TypeScript + React Native / Expo 57 (app)
**Dependencias**: las del proyecto; ninguna nueva
**Almacenamiento**: PostgreSQL, migración Flyway nueva (V197 o la siguiente libre al fusionar), solo las dos tablas de notas
**Pruebas**: JUnit 5 + Testcontainers (backend); Jest + react-test-renderer (app); Maestro + mobile-mcp (E2E)
**Plataforma**: Android e iOS (la app); backend en EC2
**Tipo**: móvil + API
**Rendimiento**: elegir una semana ya vista o la anterior adelantada se ve al instante (SC-004); el mes, un solo pedido
**Restricciones**: arquitectura hexagonal (dependencias hacia el dominio); el tiempo es del participante (fechas en la zona del aprendiz; la corrección de una nota, en la del autor); textos en español de Perú con tuteo; la app instalada no se rompe (solo cambios aditivos en respuestas existentes)
**Escala**: un mentor tiene hasta ~3 grupos de ~10; un aprendiz, 90 días

## Chequeo de la constitución

| Principio | Cómo se cumple |
|---|---|
| I. Dependencias hacia el dominio | Notas: dominio puro (`NotaDelMentor` y sus reglas de corrección), servicio, puertos y adaptadores JPA y REST. La semana del mentor reutiliza el caso de uso del líder a través de un puerto, sin llamar a su adaptador. |
| II. No se inventan reglas de negocio | El color del día usa la regla existente del semáforo; quién lee las notas, cuándo se corrigen y el orden de la lista son decisiones del dueño (grilling y aclarar del 17/11). |
| III. Nada terminado sin pruebas en verde | Pruebas primero por cada FR, con la marca `spec 055 FR-NNN`; `verificar` en los dos repos, Maestro y logs. |
| IV. La base guarda historia | Las notas no se borran; cada corrección guarda el texto anterior en `notas_del_mentor_versiones`. |
| V. El tiempo es del participante | Semana y días en la zona del aprendiz; «el mismo día» de la nota, en la zona del autor (decisión documentada). |
| VI. Los cinco tabs no se tocan sin autorización | Cambia la fila del mentor en Yo y su vista en Comunidad → Tribu: autorizado por el dueño el 17/11 con los bocetos; se anota en `docs/decisiones/liderazgo-y-administracion.md`. |
| VII. Documentar en el mismo cambio | Spec, decisión del dueño, `MODULO_MENTORING` (o el doc del módulo), bitácora si aparece un error y `trazabilidad.json`. |

Resultado: **pasa**, sin excepciones que justificar.

## Estructura

### Documentación

```text
specs/055-rediseno-del-mentor/
├── spec.md · plan.md · research.md · data-model.md · quickstart.md
├── contracts/api.md
├── checklists/requirements.md
└── tasks.md            (lo crea speckit-tasks)
```

### Código

```text
Backend (Renaser-90-dias-backend)
src/main/java/com/renaser/os/
├── mentoring/
│   ├── domain/model/nota/            NotaDelMentor, reglas de corrección (puras)
│   ├── application/ports/in|out/     casos de uso de notas, semana y días del mentor
│   ├── application/services/         NotasDelMentorService, SemanaDelAprendizService, DiasDelAprendizService
│   └── infrastructure/adapter/
│       ├── in/rest/                   NotasDelAprendizController, SemanaDelAprendizController, DiasDelAprendizController
│       └── out/persistence/nota/      JPA de notas y versiones
├── leadership/infrastructure/adapter/in/rest/   GET /people/{id}/notas
├── mentoring/infrastructure/adapter/in/rest/    GET /admin/trainees/{id}/notas
└── habits/api/ObligacionHabito + mentoring/api/ObligacionDelDia   campo pilar
src/main/resources/db/migration/V197__notas_del_mentor.sql
src/test/java/...                    pruebas de dominio, servicio, integración (Testcontainers) y por rol

App (Renaser-90-dias-frontend-)
src/features/mentor/
├── screens/  MisGruposScreen (nueva), MiCelulaScreen (rehecha), AlumnoScreen (rehecha)
├── components/ FilaDeAprendiz, SemanaDeCirculos, MesDeColores, PilarDeHabitos, HabitoDelDia, HojaDeNotas
├── hooks/    useSemanasDelAprendiz (caché por semana + adelanto), useDiasDelMes, useNotasDelAprendiz, useAvisosDelAprendiz
├── api/      mentorApi (semana, días, notas) + esquemas
└── utils/    ordenDeAprendices, estadoDelHabito, agruparPorPilar (puras, con pruebas)
src/features/paneles-de-rol/utils/vistasDeLosPaneles.ts   vista misGrupos
src/features/mentor/utils/vistasDelMentor.ts              vista misGrupos
src/features/lider-mentores/screens/PersonaDelLiderScreen.tsx   lápiz de lectura
src/features/admin/screens/FichaAprendizScreen.tsx               lápiz de lectura
.maestro/rediseno-del-mentor/                                      flujos E2E
```

**Decisión de estructura**: dos repos como siempre; lo nuevo del servidor vive en `mentoring`, salvo las dos puertas de lectura del líder y de Administración, que van con sus controladores de siempre.

## Orden de entrega

1. Backend: `pilar` en las obligaciones → semana y días del mentor → notas (dominio, migración, servicio, REST, lecturas del líder y de Administración) → pruebas por rol.
2. App: utils puros → hooks → componentes → pantallas → lápiz en las fichas del líder y de Administración → navegación (`misGrupos`).
3. E2E en el emulador de Android, logs, docs y PR en cada repo (el de la app espera al despliegue del backend).

## Seguimiento de complejidad

Sin violaciones de la constitución que justificar.
