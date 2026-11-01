# Anexo — El diagnóstico inicial del dueño, como punto de partida

Este anexo recoge **lo que se pudo leer del formulario de Google** que el dueño compartió el 2026-10-31
(`INSTITUTO RENASER | DIAGNÓSTICO INICIAL — Diplomado Riqueza con Propósito: Maestría de Vida`) y lo que la ficha
nueva tendrá que soportar para recibirlo. Es insumo de la spec `026-ficha-con-diagnostico`; no la reemplaza.

## Cómo se leyó el formulario (sin credenciales)

- Google Forms **no ofrece exportar las preguntas** de un formulario (solo las respuestas, a una hoja de cálculo). Sus alternativas —la
  API de Forms y Apps Script— piden entrar con la cuenta dueña del formulario.
- La **página pública** del formulario (`…/viewform`) enseña a simple vista solo la primera sección, pero lleva dentro toda su estructura: con ella se
  leyeron, sin ninguna credencial, **las 6 páginas y las 27 preguntas** (23 numeradas más 13A a 13D). Eso sirvió para escribir esta spec, **no** se propone como
  función del producto: depende de un formato interno de Google, que puede cambiar sin aviso.
- Por eso el contenido quedó en **`diagnostico-inicial.csv`** (en esta misma carpeta), con las columnas de la **plantilla de importación** que la spec propone. Es la
  prueba de que la plantilla alcanza para cargar el formulario completo, y es el archivo con el que el equipo carga el diagnóstico la primera vez.

## Estructura

| # | Sección | Preguntas | Notas |
|---|---|---|---|
| 1 | Conociéndote | 1 a 4 | Nombre, edad, ciudad y país, a qué se dedica |
| 2 | Tu vida hoy | 5 a 13 | Satisfacción por áreas, relaciones, sueño, actividad física, salud, ingresos, economía, horas de trabajo, si tiene negocio |
| 3 | Tu negocio *(solo si la 13 es «Sí»)* | 13A a 13D | A qué se dedica, facturación, utilidad, cuántas personas |
| 4 | Tu mente, tus decisiones y tus resultados | 14 y 15 | Qué pasa cuando no actúa; una situación real de los últimos 30 días |
| 5 | Tu visión y materialización | 16 a 20 | Resultado concreto en **Relaciones**, **Salud y bienestar** y **Riqueza y negocios**; prioridad principal; el cambio a 12 meses |
| 6 | De la decisión a la acción | 21 a 23 | Primera acción de los próximos 7 días; tiempo semanal; declaración de responsabilidad y consentimiento |

Tipos de pregunta que aparecen: texto corto, párrafo, opción única, casillas **con máximo de respuestas** (la 14, «como máximo 2»), **cuadrícula de escala 1 a 5**
(la 5, siete filas), una pregunta **condicional** (la 13 muestra la sección 3) y un **consentimiento** obligatorio (la 23). Cada sección y varias preguntas tienen texto de ayuda
y ejemplos.

## Lo que la ficha tiene que poder hacer para recibirlo

1. Secciones con título y descripción; preguntas con texto de ayuda y ejemplo.
2. Respuesta obligatoria u opcional.
3. Casillas con un máximo de opciones (la 14).
4. Cuadrícula de escala (la 5): varias filas con la misma escala. Si no se soporta tal cual, se parte en una escala por fila.
5. Preguntas y secciones que **solo se muestran si** otra respuesta es una en concreto (la 13 y la sección 3).
6. Un **consentimiento** que bloquea el avance mientras no se marque.
7. Marcar qué respuestas **alimentan** algo de la app (ver abajo).
8. Que un texto pueda nombrar **la duración de la persona** («en 90 días», «en 1 año»), porque el formulario dice «próximos 90 - 365 días» y «primeros 90 días de RENASER».

## Qué respuestas alimentan la app

| Pregunta | Alimenta |
|---|---|
| 16 · Relaciones | El **objetivo** del eje Relaciones en Plan |
| 17 · Salud y bienestar | El **objetivo** del eje Cuerpo en Plan |
| 18 · Riqueza y negocios | El **objetivo** del eje Negocio en Plan |
| 19 · Prioridad principal | El eje que va **primero** en Plan (lo que hoy decide el Mapa) |
| 21 · Primera acción | Una **acción sugerida** para empezar, en el eje de la prioridad principal |

## Propuestas de adaptación a la app (a confirmar por el dueño)

Son sugerencias para que el formulario se sienta como una app y no como una web; **ninguna cambia el sentido de las preguntas**.

1. **Pasos cortos**: una sección larga se parte en pasos de 1 a 3 preguntas, con barra de avance y el botón siempre a la vista, como la Ficha de hoy.
2. **No preguntar lo que ya se sabe**: el nombre (1) y la ciudad (3) vienen de la solicitud; se muestran precargados para confirmarlos.
3. **La edad (2) como número**, no como texto libre.
4. **Partir cada resultado (16, 17, 18) en dos respuestas cortas**: «Hoy mi situación es» y «En {duración} quiero haber conseguido». La segunda es la que alimenta el objetivo
   (Plan limita el objetivo a 500 caracteres). Hoy las dos frases van en un solo párrafo y no se pueden separar.
5. **Cómo dirigirnos a la persona**: la ficha estándar pregunta un dato (hoy «sexo») que usan SER y los avisos para hablarle bien. El diagnóstico no lo trae: se propone sumar una pregunta corta
   («¿Cómo prefieres que nos dirijamos a ti?»). Sin ella, el trato queda neutro.
6. **Teléfono / WhatsApp**: viene de la solicitud; no se vuelve a pedir.
7. **La declaración de responsabilidad y consentimiento (23)**: el formulario solo trae la casilla «He leído y acepto la declaración…». **El texto de la declaración no está en el formulario**:
   hace falta que el dueño lo entregue para mostrarlo encima de la casilla.
8. **Guardado y retomar**: cada sección se guarda al avanzar; si la persona cierra la app, vuelve al mismo paso con lo respondido.
9. **Tiempo estimado**: la portada dice «15 minutos»; la app lo muestra y la barra de avance lo cumple.

## Lo que no se encontró

- El formulario no tiene lógica de salto aparte de la sección 3, ni preguntas con archivos, fechas u horas.
- No se pudo ver ninguna **respuesta**: el diagnóstico es del dueño y las respuestas de las personas son privadas.
