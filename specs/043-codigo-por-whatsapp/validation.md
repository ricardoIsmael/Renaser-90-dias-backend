# Validación de la spec 043 — el código de verificación también por WhatsApp

Revisada requisito por requisito con `node scripts/trazabilidad-specs.mjs --spec 043 --backend .claude/worktrees/043-codigo-por-whatsapp ../Renaser-90-dias-frontend-/.claude/worktrees/043-codigo-por-whatsapp`.
Cada FR y SC lleva su marca `spec 043 FR-NNN` en una prueba que falla si el requisito se rompe.

## Sin prueba automática (exentos, con su motivo en la spec)

| Requisito | Por qué | Qué se hizo |
|---|---|---|
| SC-001 | El recorrido completo en el teléfono | E2E en el emulador contra el backend local (base `renaser_e2e_043`, copia de `renaser_e2e_final`) y un YCloud falso en 127.0.0.1; capturas en el PR. |
| SC-004 | Requiere la plantilla aprobada por Meta y saldo en YCloud | **No probado.** Lo hace el dueño con un teléfono de prueba, nunca contra producción. |

## Cubierto, con lo que NO prueba

- **El proveedor real.** `YCloudAdapterCodigoTest` y `CodigoPorWhatsAppIT` usan un YCloud falso: prueban la forma del pedido (el código en el cuerpo y en el botón `url` índice 0, como documenta Meta para «Copy code»), no que YCloud acepte la plantilla.
- **El tiempo de respuesta de la recuperación.** Se prueba que el envío no se espera (`enviarSinEsperar` en otro hilo) y que la respuesta es idéntica; no se mide el tiempo. Lo que sí corre en el hilo del pedido (leer la cuenta y su ficha) es del mismo orden que `/forgot`.

## Decisiones tomadas al implementar (el dueño las puede cambiar)

- **Mismo código, no uno nuevo** (ver la spec): no reinicia los intentos y no deja dos códigos válidos.
- **La app ofrece el enlace a todos en la recuperación** y no muestra ningún número: decir si la cuenta tiene WhatsApp sería revelar que el correo tiene cuenta.
- Los números de los límites (10 / 3 / 3 / 300 por hora, 60 s de espera).
- El WhatsApp es obligatorio en «Solicitar acceso» solo cuando el canal está disponible.
