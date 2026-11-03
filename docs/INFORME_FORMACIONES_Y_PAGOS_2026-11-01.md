# Informe del 01/11/2026: formaciones, pagos y Hoy/Plan, integrados y probados

Este informe reemplaza al estado de las 04:10 (`ESTADO_FORMACIONES_2026-11-01.md`, que queda como historia de la prueba nocturna).

**Dónde está:** la rama `integracion-formaciones` de los dos repos, con un PR en borrador en cada uno. **No se fusionó nada a `master` y nada tocó
producción.** Fusionar el backend a `master` **despliega a producción**: lo decide el dueño, después de la regresión del lunes.

## 1. Qué entra

| Bloque | Qué es |
|---|---|
| Formaciones (specs 022–031, 033 y 034) | Tres formaciones aisladas: Escuela (90 días), Diplomado (3 meses o 1 año) y Certificación (90 días). Hábitos, comunidad, ranking, testimonios y términos por formación; aceptar una solicitud eligiendo formación, duración, rol y ficha; ficha con diagnóstico (reemplaza al Mapa); planificar el mes y «solo esta semana»; el líder ve todos los hábitos; Código Renaser con voz; 80/20 con IA; «Cierra tu día» en Plan; el interruptor del Código Renaser en las fichas |
| Pagos (spec 032, V158, D-307) | Al aceptar se elige si la persona paga **sin cronograma** (pagó completo) o **con cronograma** (cuotas con fecha y monto en S/ o US$). Administración y Alquimista marcan cada cuota **Pagado/Pendiente a mano**: no hay pasarela ni cobro. Yo → «Mi ingreso» sigue la maqueta del dueño, y los recordatorios de pago son alarmas locales configurables. Nunca se bloquea una cuenta |
| Hoy y Plan (app) | La frase del día sale siempre, también sin programa. La entrada al programa propio es una fila compacta entre la frase y el fénix. Quien espera su Día 1 ve «Tu programa empieza el …» (#72). En Plan, quien no tiene programa ve una tarjeta con «Elegir mi Día 1» |
| Arreglos de las dos jornadas de E2E | App #72–#91; backend #53–#55, incluida la carrera que creaba dos veces la comunidad general de una formación (#55). Cada uno con su issue y su commit |
| Decisiones del dueño del 01/11 | La cabecera de Administración pasa a dos renglones con letra grande. Las pantallas cortas («Mi ingreso» sin cronograma, Plan sin programa) quedan con la tarjeta arriba. El Diplomado de 1 año planifica 12 meses |

**Migraciones:** V125 a V158, seguidas y sin números repetidos. V159 y V160 quedan libres.

## 2. Verificación

- **Backend:** `TZ=UTC ./mvnw -B clean verify` con BUILD SUCCESS: **8643 unitarias y 772 de integración** en verde (HEAD `5784c76f`, más este documento).
- **App:** `tsc --noEmit` con **0 errores** y Jest completo con **695 suites y 7526 tests** en verde (HEAD `4a4f50d`).
  - Después se sumó solo la cabecera en dos renglones (`f18117a`): tsc 0 y sus pruebas en verde.

## 3. E2E

**Primera jornada (noche del 31/10 al 01/11):**
- API: 355 de 367 escenarios. Los 12 errores que fallaron se corrigieron.
- Aislamiento entre formaciones: **0 fugas** en 1.066 listas y 875 pedidos por id.
- Pantalla: 38 casos.

**Segunda jornada (01/11, en el emulador)**, todo PASÓ:
- **Aceptar (025):** una persona, Diplomado de 1 año con diagnóstico, mentor en dos formaciones, tres a la vez, cambiar la duración y la ficha que dice cómo entró.
- **Ficha con diagnóstico (026):** editor y vista previa, asignar a quien ya estaba dentro, completar el diagnóstico con la meta medible, objetivos por confirmar en Plan y no publicar sin el texto de la Declaración (026-12).
- **Términos por formación (027):** v2 pide aceptar de nuevo, con firma. Por API, sin términos publicados no se puede aceptar a nadie en esa formación (027-03).
- **Hábitos por formación (023):** categoría y hábito en el Diplomado, y copiar un hábito a la Certificación.
- **Comunidad por formación (024):** comunidad, ranking y testimonios por formación, y tipos de comunidad (apagar Directos los esconde y da 403).
- **Formaciones del personal (022):** personal con dos formaciones, personal sin formación y traspaso de la Escuela al Diplomado de 1 año.
- **Planificación (028):** planificar el mes (12 meses en un programa de 1 año) y «solo esta semana». «Repetir la semana» pasó por API.
- **Resto de specs:**
  - 029: el líder ve todos los hábitos y el mentor no.
  - 030: Código Renaser con Escuchar y Dictar.
  - 031: el 80/20 en sus tres estados.
  - 033: «Cierra tu día» en Plan.
  - 034: el interruptor del Código Renaser en las fichas.
- **Pagos (032):**
  - aceptar con y sin cronograma;
  - en la ficha, marcar pagado y volver a pendiente (queda quién y cuándo), agregar, editar y asignar;
  - «Mi ingreso»: confirmar, y la invalidación cuando cambian los montos;
  - **el recordatorio sonó** a su hora y abrió «Mi ingreso»;
  - mentor, líder, guía, aprendices y Soporte TI reciben 403 sobre pagos ajenos.
- **Hoy y Plan** en cuatro estados: personal sin programa, con programa sin Día 1, esperando su Día 1 y aprendiz con programa. En claro y oscuro, sin huecos ni parpadeo.

El detalle, caso por caso y con capturas, está en `~/Imágenes/e2e-2026-11-01-final/jornada2/resultados.md` (local).

## 4. Pendiente para el lunes 02/11

1. **Regresión completa de Maestro**: `regresion/`, `training-por-hora/`, `mi-radar/`, `diseno-31/` y el resto. Además `pagos/01–09`, con los flujos corregidos en `5a528a3`.
2. **«Repetir la semana» por pantalla.** El domingo no se puede; por API ya pasó.
3. **031-03**: que el aviso del 80/20 abra «Semana». En local no llega push; hace falta un enlace directo o un APK con push real.
4. **Dictado y voz del Código Renaser en un teléfono real**, porque el emulador no reproduce el audio.
5. **Dos casos para repetir:**
   - J2-7 con una persona de 1 año sin Día 1;
   - copiar un hábito (obs. 4) con el arreglo final.
6. **Flujos de Maestro que dependen de los datos**: `index: 1` en `solicitud-con-formacion/01–03` y «Por revisar» en `terminos-editables/01,03`.
7. **Lo que todavía no se probó:**
   - la IA real del 80/20 y SER leyendo el diagnóstico (sin clave del modelo en local);
   - el aviso «a todos» de un evento con push real;
   - «Ver como» de Soporte TI con el aislamiento de la formación;
   - publicar en el Muro eligiendo la formación.
8. **Prueba final**: `clean verify` del backend y tsc + Jest de la app sobre los HEAD finales.

## 5. Lo que decide el dueño

1. El texto de la **Declaración** del diagnóstico. Sin ese texto no se puede publicar.
2. **Publicar en producción los términos** del Diplomado y de la Certificación. Sin ellos no se puede aceptar a nadie ahí.
3. La **clave del modelo de IA** en producción, para el 80/20.
4. **Supuestos a revisar:**
   - 031 (D-306): los mínimos de datos, los reintentos y los meses en bloques de 4 semanas.
   - 026: la meta de Negocio como nivel; las lecturas del diagnóstico fuera del resumen de las 20:00; la lista de quién vio el diagnóstico, todavía sin pantalla.
   - 032 (D-307):
     - el personal con «sin cronograma» no anota nada;
     - solo existen «Pagado» y «Pendiente», sin «vencida»;
     - una cuota pagada no se edita;
     - si cambian los montos, se pide reconfirmar;
     - de cada cuota solo queda el último cambio.
5. **Detalles de pantalla:**
   - la Caja Renaser en Yo del Diplomado y la Certificación;
   - «Tu posición · Comunidad Renaser» en el ranking de esas formaciones;
   - si el servidor debe rechazar «solo esta semana» fuera del programa.
6. **Bug antiguo, fuera de formaciones:** sumar, quitar y volver a sumar a un aprendiz a un grupo lo deja fuera (clave única de V45 en `ComposicionDeCelulaService`). Antes de arreglarlo hay que abrir su issue.
7. **Cuándo se publica.**

## 6. Orden de publicación

1. **Backend**, todo junto (Flyway V125 a V158). Fusionar a `master` despliega a producción.
2. **La Política de Privacidad en Vercel.**
3. **El APK nuevo.** Es solo JavaScript, sin código nativo nuevo, pero la app no se actualiza por aire.

Con el backend nuevo y la app anterior no se rompe nada, porque todos los campos nuevos son opcionales.

## 7. Cómo retomar

- **Base local:** `renaser_e2e_final`, solo para pruebas, con V158 ya aplicada.
- **Backend:** `servicio.sh 2G python3 ~/.config/renaser-e2e/levantar-backend-e2e-final.py`.
- **Metro:** desde el worktree `integracion` de la app.
- **Cuentas de prueba:** `ef-*@renaser.test`, más las `e2e-j2-*` y `e2e-j3-*` de la segunda jornada. Las claves viven solo en `~/.config/renaser-e2e/`.
- **Memoria de la laptop:** lo pesado va con `pesado.sh`, que usa un candado. Nunca se compila con el emulador prendido, ni se levantan dos backends a la vez con el emulador.
