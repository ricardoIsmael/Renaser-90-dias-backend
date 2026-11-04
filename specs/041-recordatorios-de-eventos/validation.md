# Validación de la spec 041 — recordatorios de eventos por el chat y por WhatsApp

Revisada requisito por requisito con `node scripts/trazabilidad-specs.mjs --spec 041` (backend y app). Cada FR y SC lleva su marca `spec 041 FR-NNN` en una prueba que falla si el requisito se rompe.

## Sin prueba automática (exentos, con su motivo en la spec)

| Requisito | Por qué | Qué se hizo |
|---|---|---|
| FR-042 | Que el enlace del mensaje del chat se pueda tocar depende de cómo dibuja el chat los enlaces | **Hallazgo, sin arreglar**: leyendo `src/features/chat/components` de la app, la burbuja del chat dibuja el texto como texto plano (no hay `dataDetectorType` ni `Linking.openURL`), así que el enlace «Entra aquí: https://…» **se ve pero no se toca**. Hacerlo tocable cambia el chat de Comunidad para todos los mensajes (un `dataDetectorType="link"` en el `Text` de la burbuja) y ese tab necesita autorización explícita del dueño: se deja como decisión. Falta confirmarlo en el emulador. |
| SC-015 | Requiere el número nuevo conectado en YCloud, la plantilla aprobada por Meta y saldo | **No probado.** Lo hace el dueño con un teléfono de prueba, nunca contra producción; el resultado se anota en el PR |

## Cubierto, con lo que NO prueba

- **El proveedor real.** `YCloudAdapterTest` usa un servidor HTTP falso: prueba la forma del pedido y qué hace el adaptador con cada respuesta, no que YCloud acepte el cuerpo. El texto de los errores de YCloud no está documentado en la spec: la causa «plantilla» y «saldo» se reconoce por palabras claras (`CausaDeFalla.segun`); todo lo demás es «otra» y solo queda anotado.
- **La firma del webhook** se prueba con el algoritmo descrito por RenaserOs (HMAC-SHA256 de `t.<cuerpo>`, secreto tal cual): coincide con su lectura de la documentación de YCloud, pero no se comprobó contra un envío real de YCloud.
- **FR-040** (formulario sin cambios) es un requisito retirado que se conserva como «no cambia»: lo prueban `FormularioDelEventoSinWhatsAppTest` y la comprobación de columnas de `eventos`.
- **FR-044** (la ficha dice para qué se usa el WhatsApp): se prueba el texto de la ficha y de la política; que el asesor legal lo apruebe es del dueño.

## Decisiones tomadas al implementar (el dueño las puede cambiar)

- Con `eventos.whatsapp` apagado o sin configuración del proveedor **no se deja ninguna fila** `OMITIDO` (la spec lista «interruptor apagado» entre los motivos): serían miles de filas inútiles mientras el canal nace apagado.
- La baja por texto exige `context.id` (responder a un mensaje nuestro); no se acepta el «número con un recordatorio en los últimos 7 días» de RenaserOs, porque la tabla no guarda el número (FR-030).
- El resumen cuenta mensajes (enviados, entregados, leídos, fallaron) y **personas** (sin número).

## Qué corrió y qué se tuvo que ajustar en pruebas que ya existían

- Unitarias de `calendar`, `chat`, `notifications` y `plataforma`, más `ArchitectureTest`, `FuncionalidadDeclaradaTest` y `EndpointAuthorizationDeclarationTest`: en verde (1 730 pruebas).
- De integración, contra Postgres de verdad: `RecordatorioPersistenceAdapterTest`, `EntregasDeWhatsAppJdbcAdapterIT`, `RecordatoriosPorWhatsAppIT`, `BorradoDeCuentaEnLosModulosIT`, `ClavesCompartidasAlBorrarCuentaIT` y los de plataforma, eventos y avisos que tocan lo compartido: en verde.
- Ajustes a pruebas ya existentes, por dos efectos esperados de la spec (no por un defecto): (1) `eventos.whatsapp` nace apagada para todos, así que aparece en `noDisponibles` de `GET /funcionalidades/mias` y el catálogo pasa de 56 a 57 entradas (`ConsolaDeFuncionalidadesServiceTest`, `MisFuncionalidadesServiceTest`, `ReglaDeDespliegueTest`, `ReglaDeRolPersonalizadoTest`, `ConsolaSoporteTiIT`, `DespliegueProgresivoIT`, `RolPersonalizadoDePuntaAPuntaIT`); (2) cada aviso de evento se publica ahora una vez por oyente (bandeja, chat y WhatsApp), así que `AvisosDeEventoEnMasaIT` espera 3 publicaciones del *outbox* por aviso en vez de 1 (lo que protege E-360, que ninguno quede sin guardar, no cambia).
- La suite completa del backend (`./mvnw verify`, todos los módulos) **no se corrió** en esta entrega: solo los módulos afectados. Conviene que corra en el CI del PR.
