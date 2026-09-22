# Pendientes — al 2026-09-22

Lista de trabajo, no informe. El relato de lo que pasó está en
[`parte-del-dia-2026-09-21.md`](parte-del-dia-2026-09-21.md); esto es lo que queda por hacer.

**Estado de los repos:** backend en `026cc97`, frontend en `a72214b`. Los dos en `master`, al día
con `origin`, y **ya desplegados** — el CD del backend publica solo al empujar a master.

---

## A. Te toca a ti, no a un agente

Son observaciones sobre el despliegue. Ninguna se puede resolver leyendo el código: el hecho que
falta vive fuera del repositorio. El detalle y el plan exacto de cada una están en
`~/security-audit-skill/Renaser-90-dias-backend/run-1/NEEDS-VALIDATION.md`.

### A.1 — La de mejor rendimiento: cinco minutos, cierra tres

En la EC2:

```bash
docker inspect redis --format '{{.Args}} {{json .HostConfig.PortBindings}}'
docker network inspect renaser --format '{{json .Containers}}'
```

La primera dice si hay `--requirepass` y si el 6379 está publicado al host. La segunda, qué
contenedores comparten la red y por lo tanto quién más puede escribir en Redis.

De esa respuesta dependen **tres** hallazgos:

- [ ] Si el chat en vivo se puede falsificar — quien pueda publicar en el canal fabrica mensajes
      atribuidos a quien quiera, **que nadie puede desmentir después** porque el reenvío no persiste
      nada.
- [ ] Las credenciales en claro en Redis (sesiones de 30 días, tokens de reset y de verificación).
- [ ] El segundo consumidor de Pub/Sub, donde el **nombre de la clave es el token**.

Cómo leerlo: `requirepass` puesto, sin puerto publicado y solo `backend` en la red significa que los
tres bajan a endurecimiento. Cualquier otra combinación y son reales.

### A.2 — Las otras seis

- [ ] **Facebook.** Carga un correo **sin verificar** en una cuenta de prueba de Meta y mira si
      `GET /me?fields=email` lo devuelve igual. Si lo devuelve, el control que exige correo
      verificado es un no-op para ese proveedor.
- [ ] **El 8080 en HTTP plano.** Comprueba si el security group deja llegar al 8080 desde fuera de
      la VPC. Fuera de `/api/v1/**` no hay ninguna identidad exigida.
- [ ] **La cabecera reenviada.** Los cinco contadores por IP se apoyan en `X-Forwarded-For`. Hay que
      confirmar si CloudFront la **añade** o la **reemplaza**: si la añade, los cinco límites son
      falsificables.
- [ ] **Web Push y el 303.** El destino se valida una sola vez y no se vuelve a mirar tras una
      redirección.
- [ ] **El panel de administración.** Abre las herramientas de desarrollo en `http://127.0.0.1:8788`
      y mira cómo quedó `renaser_csrf_local`: sin el prefijo `__Host-`, cualquier proceso en
      loopback puede ensombrecerla con un `Path` más específico.
- [ ] **La escritura del agente de IA.** Entre la decisión del modelo y la escritura en la cuenta
      del aprendiz no hay confirmación humana.

---

## B. Decisiones de producto — no son bugs

- [ ] **Anonimizar los testimonios al purgar una cuenta.** Hoy la purga **no borra** la foto ni el
      avatar que un testimonio referencia, a propósito, para no romperle la vitrina a nadie. La
      consecuencia es que la vitrina sigue sirviendo material de un ex usuario con su nombre al
      lado. O se anonimiza la fila, o se acepta.
- [ ] **¿Guardar semanas incompletas?** Ya no te obliga a llenar los cuatro pasos para navegarlos,
      pero **guardar** sigue exigiendo los tres ejes completos con sus tres acciones: lo impone el
      backend (`CrearPlanSemanalUseCase` con `@Size(min=3,max=3)`, `RocaSemanal.requireAccionesValidas`
      y `requireTitulo`). Aflojarlo son cuatro puntos del Java.
- [ ] **Los tres contadores de Comunidad** ("12 conversaciones · 3 eventos · 2 mentorías") son
      valores fijos: ningún endpoint los calcula. O se cablean, o se quitan.
- [ ] **El renombre de "roca" quedó a medias.** El tab `Hoy` sigue diciendo "roca" en tres sitios
      (`HoyScreen.tsx` ~522, ~693, ~702). No estaba autorizado, así que no se tocó.

---

## C. Probar a mano, en tu Xiaomi

No hay emulador ni dispositivo en el entorno donde se trabajó, así que **ninguno de los seis cambios
de interfaz se vio correr**.

- [ ] **La firma del Pacto** (`Yo` → Pacto). Es lo más importante de esta lista: esa pantalla era una
      maqueta y ahora firma de verdad. Comprueba que el dedo dibuje en Android y que el trazo no se
      borre al desplazar.
- [ ] **El teclado del registro.** Que no tape Contraseña ni Confirmar contraseña, en las dos
      pestañas.
- [ ] La rueda de 1 a 60 del recordatorio, y que un valor viejo fuera de rango (p. ej. "1 h 30
      antes") siga sonando.
- [ ] La pestaña **Tribu** con letra del sistema al máximo, y que el desplegable de integrantes no
      empuje la bandeja.
- [ ] Que el objetivo mensual muestre cifra cuando toca y **no** la muestre cuando el ritmo es irreal.

---

## D. Código pendiente

### D.1 — Seis hallazgos confirmados de severidad baja, sin tocar

- [ ] **La superficie de error escribe el mensaje de dominio en el log.** El más feo de los seis:
      incluye el correo y el nombre completo de un tercero, que es exactamente lo que
      `CLAUDE.MD` §5.4.9 prohíbe, nombrando ese mismo sumidero. Ojo: el arreglo **no** puede apoyarse
      en el `traceId` del MDC, porque no existe (ver D.3).
- [ ] `/diagnostico` del panel autentica pero no autoriza.
- [ ] Dos lecturas de comunidad sin guard de actor (comentarios del Muro y ranking de grupos).
- [ ] El horario semanal de un hábito personal ajeno se lee poniendo su id en la ruta.
- [ ] Borrar una solicitud pendiente deja viva la cuenta que el alta creó.
- [ ] La FK `ON DELETE RESTRICT` de `ajustes_dia_programa.ajustado_por` bloquea la purga para siempre.

### D.2 — Encontrado al pasar, no reportado por la auditoría

- [ ] **`EspirituService.entregar`** — el mismo cerrojo tardío una capa más arriba, y ahí el
      repositorio de espíritu **no tiene ni un `@Lock`**. Dos envíos simultáneos pasan los dos la
      guarda de `PENDIENTE`. No es dinero, pero rompe el 409 del contrato — y es lo que hace
      alcanzable la carrera de abajo que sí se arregló.
- [ ] **El bug de la coma en el Mapa de Renacimiento**: `"78,5"` se lee como **78,5** para la Roca
      Maestra y como **785** para la validación, los hitos y el cálculo del objetivo mensual.
- [ ] **El nivel mensual ya existe en el backend** (`/api/v1/rocks/monthly`, tabla `rocas_mensuales`
      de la V36) y el frontend **no lo consume**. La cifra que ahora se calcula tiene dónde
      guardarse sin backend nuevo. Cuidado con el `CHECK meta > 0`, que rechazaría una meta mensual
      de 0 (saldar una deuda entera).
- [ ] **`MisCelulasService.integrantesDe`** tiene el mismo hueco de periodo que se cerró en
      `AcompanamientoService`. Ahí corresponde `esIntegranteVigente`, no `acompanaVigente`.

### D.3 — Deuda que hace daño callado

- [ ] **Dos pruebas fijan como correcto el comportamiento defectuoso** y van a bloquear sus arreglos:
      `ConsultaEmailServiceTest.noConsumeCuota` y
      `AccountRequestServiceTest.eliminarAceptaAdminActivo`.
- [ ] **La prueba de humo del panel no puede ver su propio fallo**: su backend de mentira responde
      `role: 'ADMIN'` para cualquier sesión que reconoce, justo al revés que el real.
- [ ] **La prueba que vigila el voseo tiene la regex incompleta** (`objetivoMensual.test.ts`): no
      incluye `intentá`, `ingresá` ni `confirmá`, que eran las formas más repetidas. Da verde con
      voseo adentro.
- [ ] **El `traceId` en el MDC no está implementado.** `CLAUDE.MD` §5.4.9 lo llama "la regla más
      importante de esta subsección" y no hay ni un `MDC.put` en `src/main/java`, ni
      `micrometer-tracing` en el `pom.xml`.
- [ ] **`V55` no tiene explicación en ninguna parte** del repositorio, a diferencia de `V7`. Un hueco
      sin explicar invita a rellenarlo, y Flyway rechaza el arranque si alguien lo hace.
- [ ] **El `README` del panel está desactualizado**: afirma que las rutas de altas siguen en
      `permitAll()`.
- [ ] **8 coincidencias de voseo** en `docs/` y `specs/` del frontend. Son documentos de
      planificación, no texto de la app.

---

## E. La ola 3 de la auditoría

Cinco unidades de cobertura en estado `deferred`. **`deferred` significa "nadie la miró"**, no "está
bien". Las propuso el crítico de la ola 2 con archivo y línea que él mismo abrió.

- [ ] **La superficie REST propia del chat** — `GET /api/v1/chat/members`, el alta de un mensaje
      directo, el roster de la conversación GLOBAL. Se cruzaron los archivos revisados contra los 90
      controllers y `ConversacionController`, `MiembroController` y `PresenciaController` **no
      aparecen en ninguna unidad**. Ahí vive el directorio del que sale con quién puede hablar
      cualquier aprendiz.
- [ ] **La maquinaria que mueve gente entre células** — `RotacionService`, `TrasladoService`,
      `AvisosDeVencimientoService`: 544 líneas de lógica de pertenencia. Su insumo lo escribe el
      servicio que la auditoría confirmó sin guard de rol.
- [ ] **Censo de columnas de ruta de objeto del esquema**, empezando por las tres que hoy no tienen
      lector: audio del diario nocturno, evidencia del Santuario y adjunto de guía de hábito. Las
      tres aceptan la clave que manda el cliente sin validar.
- [ ] **El segundo consumidor de Pub/Sub de Redis** — Spring Session con `repository-type: indexed`.
      Al arrancar hace `CONFIG SET notify-keyspace-events` sobre el **servidor entero**.
- [ ] **Cota de tamaño de petición** — no hay ni una clave de tope en `application.yaml`, y de 118
      DTO de request solo 25 mencionan `@Size`.

Para cerrarla y poder afirmar cobertura completa harían falta 5 cazadores, su crítico y sus
verificadores, y que ese crítico devuelva `stop: true`.

---

## F. Recomendación de proceso

- [ ] **`master` despliega directo a producción.** El 21 salió bien porque detrás de cada cambio
      hubo 3178 pruebas y verificación doble — pero uno de los cambios llegó igual con un fallo, y
      no había nada entre el push y la EC2. Una rama con PR corta esa clase de error de raíz.
