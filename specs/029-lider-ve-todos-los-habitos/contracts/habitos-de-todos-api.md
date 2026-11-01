# Contrato: Hábitos de todos (spec 029)

Solo lectura; MENTOR_LEAD, ADMIN o ALCHEMIST con la cuenta activa (mentor, guía, aprendiz, Soporte TI y cuenta suspendida → **403**). Misma funcionalidad de Soporte TI que el
cumplimiento del líder (`lider.cumplimiento`). `Cache-Control: no-store`. **No registra consulta.** Nunca devuelve el correo.

## `GET /api/v1/leadership/habits?date=&group=&formation=&category=`
- `date` `YYYY-MM-DD`, opcional (hoy); fuera de [hoy−7, hoy] → **400**.
- `group`: un id de grupo o `none` (sin grupo). `formation`: `ESCUELA|DIPLOMADO|CERTIFICACION`; solo acota el alcance de quien pide (una ajena da una lista vacía, sin pistas).
  `category`: la clave de la categoría (la dimensión).
```json
{ "date": "2026-10-30", "isToday": true, "earliest": "2026-10-23", "latest": "2026-10-30",
  "totals": { "students": 120, "withHabits": 118, "expected": 1500, "done": 940, "pending": 400, "missed": 160 },
  "habits": [ { "habitId": "…", "title": "Despertar", "category": "BODY", "categoryKey": "CUERPO", "formation": "ESCUELA",
                "expected": 100, "done": 41, "pending": 50, "missed": 9 } ] }
```
Los hábitos con menos cumplimiento primero. Un hábito que a nadie le toca ese día no aparece.

## `GET /api/v1/leadership/habits/{habitId}/people?date=&group=&formation=&q=&page=0&size=30`
```json
{ "date": "2026-10-30", "isToday": true, "habit": { …como arriba… }, "page": 0, "size": 30, "total": 100,
  "people": [ { "personId": "…", "name": "Ana Pérez", "photoPath": null, "groupId": "…", "groupName": "Fénix",
                "status": "CUMPLIDO", "reason": null, "plannedTime": "06:00:00", "doneAt": "06:42", "evidenceId": "…" } ] }
```
`status`: `SIN_CUMPLIR`, `PENDIENTE`, `CUMPLIDO`, `SIN_DATOS` (en ese orden en la lista); `reason` solo en `SIN_DATOS`: `NO_EXIGIBLE`, `NO_LE_TOCABA`, `ANTES_DEL_DIA_1`.
`doneAt` es la hora local en la zona de la persona. `evidenceId` abre la hoja de evidencia de siempre (`GET /evidence/{id}`, que sí deja constancia). Un hábito que nadie
tiene ese día en el alcance, o de otra formación → **404**.
