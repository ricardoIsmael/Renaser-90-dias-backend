# Especificación de requisitos recibida del cliente

`Especificacion_Requisitos_Renaser_OS.docx` — v2.0 "Senior Baseline de Producción", 26 de agosto de 2026.

## Qué es

Ingeniería inversa del **backend viejo** (Next.js 15 + Prisma 6 + Supabase + Vercel Cron + Gemini 1.5). Contiene: 45 requisitos funcionales (RF-01 a RF-45), la matriz de los 5 roles, 7 historias de usuario en Gherkin, diagramas de casos de uso y de actividades en Mermaid, y 6 "Leyes Maestras del Sistema".

## Cómo usarla (importante)

**Sí es fuente de reglas de negocio.** Fases del programa, umbrales del semáforo, fórmula de coherencia, ciclos de intoxicación, penalizaciones de puntos, cupos. Eso es producto y se respeta. De hecho responde varias preguntas que estaban abiertas en los `MODULO_*.md`.

**No es fuente de arquitectura ni de implementación.** Todo lo que describa Prisma, Vercel Cron, Supabase Realtime, RLS o el middleware `requireRole` describe el sistema **que estamos reemplazando**. El objetivo del proyecto es mejorar y optimizar bajo SOLID y clean code, no replicar (`CLAUDE.md` §0).

**En al menos un punto el documento es menos preciso que el código viejo del que migramos** (la degradación de puntos dentro de la ventana de gracia). Ante una contradicción entre el documento y el código fuente original, gana el código — pero se pregunta antes de cambiar nada.

## Análisis de cumplimiento

El contraste requisito por requisito contra el backend Java real está en [`../CUMPLIMIENTO_REQUISITOS.md`](../CUMPLIMIENTO_REQUISITOS.md). Ahí está el veredicto por RF, la verificación de las 6 Leyes, y la re-priorización del plan que surge de leerlo.

Se conserva el `.docx` original en vez de transcribirlo a Markdown para no introducir errores de transcripción en un documento normativo.
