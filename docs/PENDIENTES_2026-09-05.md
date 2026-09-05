# Qué quedó abierto al cerrar el 2026-09-05

Lo verificado contra el backend real y el frontend web, con cuenta de aprendiz. No es una lista de
ideas: cada punto se vio fallar o se comprobó que falta.

---

## 1. Bugs abiertos

### E-110 — El acompañante desaparece de toda la app · **regresión de hoy**
Salir del chat de Comunidad por la barra de pestañas (no por la flecha) esconde el botón flotante
de Renasia en Hoy, Plan, Training y Yo, y no vuelve. Causa y arreglo propuesto en
`BITACORA_ERRORES.md` E-110. **Es el más urgente: lo introdujimos hoy y afecta el uso normal.**

### El Pacto no se firma en ninguna parte del alta
`PactoScreen` tiene el lienzo real, captura el PNG y lo sube a S3 — **y nunca se monta**.
`OnboardingFlow` tiene exactamente tres pasos (`ficha → terminos → activar-programa`); el Pacto,
`BienvenidaScreen` y `GongVictoriaScreen` son código muerto.

Consecuencia directa: `pactSignedAt` queda `null` para todos, así que la etapa 1 de "Tu proceso
completo" nunca se marca completada, aunque el dato ya esté cableado.

**Pregunta abierta para el dueño:** ¿el Pacto se firma durante el alta, o después desde YO?

### `YoScreen` certifica una firma que no muestra
El bloque de firma del Pacto renderiza el NOMBRE del aprendiz en cursiva y debajo dice
**"FIRMA DIGITAL REGISTRADA & SELLADA"**. No es la firma trazada: es texto generado por la app, y se
ve igual para alguien que nunca firmó. Además dice *"— Firma con tu dedo —"* en una pantalla donde
no se puede firmar.

Lo correcto es mostrar la imagen real de S3, o decir "Todavía no firmaste el Pacto".
(Origen: commit `2e85ad6`, 31-ago. No es de hoy.)

### `.env` del frontend con una IP que ya no existe
`EXPO_PUBLIC_API_URL=http://192.168.1.39:8080` — la IP actual de la máquina es `192.168.18.22`.
**En el teléfono, ninguna llamada llega al backend.** Vuelve a pasar en cada cambio de red.

---

## 2. Datos falsos que siguen en pantalla

Verificados en vivo, con cuenta real:

| Dónde | Qué muestra | Realidad |
|---|---|---|
| Hoy / Yo | `COHERENCIA 100%` | `RegistrarCoherenciaDiariaUseCase` no tiene invocador: es el valor por defecto de la tabla |
| Hoy / Yo | `RACHA 0 / récord 0` | misma causa |
| Comunidad | `12 conversaciones · 3 eventos · 2 mentorías` | mock |
| Training | tarjeta "Consistencia de la Dimensión" (37 días / 70% / 94%) | literal en el JSX; el backend no modela dimensiones ni tiene ese umbral |
| Yo | 3 insignias `DESBLOQUEADO` | `unlocked: true` fijo. `GET /profile/logros` ya devuelve el dato real y nadie lo llama |
| Plan | vista OBJETIVOS ($30.000, 65%, tareas tachadas) | mock completo; "guardar" no llama a ningún endpoint |
| Plan | fases 1-30 / 31-60 / 61-90 | el backend tiene 4 fases: 1-7 / 8-34 / 35-64 / 65-90 |

**Decisión del dueño:** coherencia/racha y la tarjeta de Consistencia se dejan como están por ahora.

---

## 3. Definiciones que faltan

1. **Los números de crisis** que usa la IA: `113 opción 5` (MINSA), `106` (SAMU), `105` (policía).
   Los puse yo, **están sin validar** y marcados como pendientes dentro del propio prompt. Un número
   equivocado en una crisis es peor que no dar ninguno.
2. **¿Cuántos minutos antes avisar?** Hoy 15 antes de empezar y 30 antes de vencer — provisorios.
3. **¿Perder un hábito descuenta puntos?** Hoy no: se dejan de ganar. `MotivoPuntos.MISSED_HABIT`
   existe en el enum y no lo usa nadie.
4. **La evidencia desde el chat además cierra el registro** y otorga puntos. Si no se quiere, es
   quitar una línea.
5. **Marcar una etapa del onboarding como completada** necesita una marca por etapa en el backend.
   No se agregó a propósito: las etapas 3, 4 y 5 no existen y van a necesitar el mismo mecanismo —
   conviene hacerlo una vez.

---

## 4. Para producción (AWS)

En orden, porque hay dependencias:

1. **Extensión `pgvector` en RDS** antes de correr Flyway.
2. **`STORAGE_PROVEEDOR=s3` + `AWS_REGION`** — sin esto no se sube ninguna foto, audio, evidencia ni
   avatar. **Es el bucle central del producto.** Falta además la bucket policy de lectura pública
   sobre `avatares/*` (D-55).
3. **`EMAIL_PROVEEDOR=smtp`** + las cuatro variables de SMTP, o nadie puede registrarse.
4. `CORS_ORIGENES` con el dominio real de la app.
5. `GOOGLE_OAUTH_CLIENT_ID` — sin él no anda el login social.
6. Push (`PushPort` solo tiene `NoOpPushAdapter`): **los avisos solo se ven dentro de la app**, así
   que justo el que no la abre no se entera.

---

## 5. Seguridad — fuera del código

El repositorio del frontend (organización `RENASER-LAB`) recibió un **ataque de cadena de suministro
el 2026-09-05**: force push con un `.vscode/tasks.json` que auto-ejecutaba un dropper de Node
disfrazado de fuente, con C2 sobre blockchain. Las cuatro ramas fueron restauradas.

**Sigue abierto, y no es trabajo de código:**
- revocar el acceso de la cuenta que hizo el force push y revisar el audit log de la organización;
- activar el ruleset que bloquea force push y borrado de ramas;
- pedir a GitHub Support que purgue el objeto `11b7fd4`, todavía accesible por SHA directo;
- rotar credenciales de quien haya podido ejecutarlo.
