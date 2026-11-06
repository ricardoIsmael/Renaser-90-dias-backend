# Validación de la spec 044 — eventos para quien tú elijas, y el líder en el chat de soporte

Revisada requisito por requisito con `node scripts/trazabilidad-specs.mjs --spec 044 --backend . <app>`: 52 requisitos, 51 cubiertos y 1 exento.
Cada FR y SC lleva su marca `spec 044 FR-NNN` en una prueba que falla si el requisito se rompe.

## Sin prueba automática (exento, con su motivo en la spec)

| Requisito | Por qué | Qué se hizo |
|---|---|---|
| SC-009 | El número sale de la ficha con el código de la 041, que no cambia; el envío real necesita el número conectado en YCloud, la plantilla aprobada y saldo | Se comprueba en el E2E local con un YCloud falso (qué número recibe y que nadie más). El envío real lo prueba el dueño con un teléfono de prueba, nunca contra producción |

## Cubierto, con lo que NO prueba

- **El semáforo** se lee con `SemaforoFinder.vigenteDe` (una lectura para toda la parte, SC-007); que el color de cada persona sea el correcto es de la spec del semáforo, no de esta.
- **«Sus mentores» del líder (P7)**: quien creó el evento actúa como mentor (MENTOR o TRAINEE_GUIDE) en una formación que el líder ve. No se lee el árbol de grupos del líder: un líder de una formación gestiona los eventos de todos los mentores de esa formación.
- **El aviso de «evento nuevo»** a quien entra a la audiencia después (un aprendiz que pasa a rojo antes de la foto nueva) vale solo dentro de las 24 h de la 041; pasado eso, recibe los recordatorios pero no el aviso de que el evento existe.

## Decisiones tomadas al implementar (el dueño las puede cambiar)

- **P9**: «Encuentro espontáneo» es el tipo `ESPONTANEO`, que sí se ofrece al mentor y al líder; se esconden «Mentoría del Alquimista» y «Semana de manifestación».
- Administración y Alquimista que eligen una audiencia (SELECCION) también necesitan `eventos.convocar` prendido, además de `eventos.gestion`.
- Un pedido fuera de alcance responde 403 `FUERA_DE_TU_ALCANCE` con los ids rechazados (no 404), para que la app diga qué quitar.
- El líder entra a los soportes **de su formación** (FR-037): si cambia de formación se lo retira de los de la anterior y se lo suma a los de la nueva.

## Qué corrió

- Unitarias de `calendar`, `chat`, `notifications`, `plataforma` y `users`, más `ArchitectureTest` y `EndpointAuthorizationDeclarationTest`.
- `EventosPorAudienciaIT` contra Postgres de verdad (las tablas V172/V173, el filtro de la lista, el «No voy», el interruptor, la edición sin audiencia y los eventos de antes).
- `./mvnw verify` completo: ver el PR.
