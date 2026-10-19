# Validación — 2026-10-17

Estado: implementado y validado automáticamente; pendiente de publicación y comprobación en dispositivo.

| Verificación | Resultado |
| --- | --- |
| Frontend Jest | 340 suites, 2.806 pruebas en verde, cero fallos/omitidas |
| TypeScript | `tsc --noEmit`: sin errores |
| Expo web | `npm run build:web`: exportación correcta |
| Backend Surefire | 6.279 pruebas; fallos 0, errores 0, omitidas 0 |
| Backend Failsafe | 406 pruebas; fallos 0, errores 0, omitidas 0 |
| Backend completo | `TZ=UTC ./mvnw -B clean verify`: BUILD SUCCESS |
| Regresión backend focalizada | 15 unitarias y 2 HTTP/PostgreSQL/Redis en verde |

## Evidencia y alcance

- Los seis escenarios iniciales de Training fallaron contra el hook anterior: cambio de día, red caída, foco, actividad, snapshot vencido y compatibilidad. Las 26 pruebas nuevas del día cubren además el render previo al efecto de foco, web suspendida, reloj manual, dispositivo en Lima con programa en Madrid, cámara/evidencia y rechazo durante el corte.
- `DiaDeHabitosTest`: Lima en madrugada UTC, Madrid y cierres de días de 23/25 horas, aun sin tracks. `DiaDeHabitosControllerTest`: contrato, no-store, array anterior, suspendido y denegación del caso de uso. Todos los roles actuales tienen USE_APP; no se inventa un rol nuevo para la prueba negativa.
- `DiaDeHabitosIT`: servidor HTTP, sesiones, PostgreSQL y Redis reales. Al pasar de 04:59:59Z a 05:00:00Z cambia el snapshot de Lima, Madrid conserva su cierre, el registro viejo recibe 409 y no otorga puntos al nuevo. También verifica acceso sin sesión y suspendido.
- Las verificaciones del backend corrieron sobre una copia aislada en `/tmp/renaser-dia-verificacion` y contenedores descartables, nunca producción. Los archivos aplicados se comparan por hash con los originales y con las fuentes probadas.
- Se ejecuta `graphify update .` en ambos repos (grafo de código). En frontend, el extractor SQL no está instalado: no se regeneró semántica de documentos ni SQL; esto no afecta las pruebas de persistencia reales.

## Incidencias de la verificación

El intento restringido bloqueó el agente de Mockito; las pruebas se ejecutaron fuera del aislamiento con Java 25. El primer clean verify tuvo un error en el fixture nuevo de autorización: esperaba que el interceptor rechazara un actor inexistente, mientras el diseño delega ese rechazo al caso de uso. Se corrigió el fixture para simular esa denegación y se conservó el caso separado del suspendido. El clean verify final pasó completo. No se cambió la política de permisos (E-609).

Los contadores anteriores salen de los resúmenes de Maven. En XML se cuentan `testcase`, no solo el atributo `tests`, por los seis casos Nested que ya documenta E-599.

## Límites

No se ejecutó emulador ni se generó APK. La cámara nativa real y la suspensión del sistema quedan como comprobación de dispositivo antes de distribuir. La prueba automatizada controla el reloj y el servidor; no identifica el recorrido histórico exacto del incidente. Hace falta publicar el backend y una versión nueva de la app/APK; no hubo despliegue.
