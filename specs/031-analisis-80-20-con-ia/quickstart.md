# Quickstart: Análisis 80/20 con IA

Local, con la base clonada (nunca producción) y sin gastar IA real: el barrido se prueba con el puerto `GenerarAnalisis8020Port` en un doble.

## Pruebas automáticas

```bash
~/.config/renaser-e2e/herramientas/pesado.sh ./mvnw -B test -Dtest='ArmadoDel8020Test,CalendarioDelAnalisisTest,ValidadorDelTextoTest,TextoDelAnalisisTest,AnalisisDelPeriodoTest,ReglasDelAnalisisTest,ResumenDelCodigoRenaserTest,ArmadoDelPedidoTest,GeneracionDeAnalisis8020ServiceTest,BarridoDeAnalisis8020ServiceTest,ConsultaDelAnalisis8020ServiceTest,AnalisisDelAlumnoServiceTest,RegistroDelAnalisisTest,ConsultarAnalisis8020HerramientaTest,GoogleGenAiAnalisis8020AdapterTest,Analisis8020ControllerTest,AnalisisDelAlumnoControllerTest,AnalisisDeLaPersonaControllerTest,AnalisisDePersonaServiceTest,AnalisisListoNotificationListenerTest,CatalogoDeFuncionalidadesTest,AnalisisSoloSaleConLosPermisosDeLaFichaTest'
~/.config/renaser-e2e/herramientas/pesado.sh ./mvnw -B verify -Dit.test='Analisis8020IT,AnalisisDelLiderIT' -Dtest=NoExiste -Dsurefire.failIfNoSpecifiedTests=false
TZ=UTC ~/.config/renaser-e2e/herramientas/pesado.sh ./mvnw -B clean verify    # completo, antes de reportar
```

## A mano (backend local)

1. Una persona con el Día 1 elegido hace 15 días y 8 acciones agendadas esta semana, 5 cumplidas.
2. `GET /api/v1/analisis-80-20?periodo=SEMANA` → `estado: EN_CURSO`, cifras de ahora, `analisis: null`.
3. Dejar pasar el domingo (o mover el Día 1 con «Cambiar día del programa») y esperar el minuto :15: la fila pasa a `LISTO`, llega el aviso «Tu análisis de la semana está listo» y `GET` devuelve las cuatro partes.
4. Con `ia.analisis` apagada en la consola de Soporte TI: el barrido difiere (`intentos` no sube) y `GET` sigue dando cifras.
5. Con menos de 5 acciones: fila `SIN_DATOS` con `falta: ["ACCIONES"]` y ninguna llamada a la IA.
6. Con `radar.fichas` apagada: el mentor recibe 404 y Administración 200; el líder deja constancia en `consultas_de_ficha`.
7. Eliminar la cuenta: la fila desaparece.

## App

`npx tsc --noEmit`, Jest completo y los flujos de `.maestro/analisis-80-20/` (escritos, sin correr).
