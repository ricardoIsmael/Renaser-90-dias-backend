package com.renaser.os.notifications.domain.model.habito;

import com.renaser.os.notifications.domain.model.notificacion.EntregaPush;

/**
 * Regla de D-184: a que dispositivos se empuja un aviso automatico de habito, sabiendo lo que el
 * aprendiz configuro para ese habito en la app.
 *
 * <p><b>La regla.</b> El telefono hace sonar su alarma local; el push del servidor es el respaldo
 * para lo que el telefono no cubre. En concreto:
 * <ol>
 *   <li><b>Recordatorio apagado</b> ({@code recordatorio_activo = false}) → ningun push, ni de
 *   inicio ni de vencimiento. Queda la fila en la bandeja. "Apagado" tiene que significar que no
 *   suena nada.</li>
 *   <li><b>Aviso de inicio con alarma local</b> (recordatorio encendido y con
 *   {@code minutos_recordatorio} elegido: es exactamente cuando la app programa la alarma en el
 *   dispositivo, {@code PlanificarDimensionModal.guardarHora}) → push al navegador y a cada telefono
 *   que no confirmo sus alarmas en las ultimas 26 h ({@link EntregaPush#RESPALDO_DE_ALARMA_LOCAL},
 *   D-217). Al telefono que si confirmo seria el mismo aviso dos veces, con minutos de diferencia;
 *   el navegador no tiene alarma local y para el sigue siendo el unico aviso.</li>
 *   <li><b>Todo lo demas</b> → push a todos, como antes. Incluye el aviso de vencimiento (la app no
 *   programa ninguna alarma para el plazo de entrega) y los habitos que el aprendiz nunca configuro
 *   (sin preferencia no hay alarma local, y los dos avisos automaticos son un pedido del dueno del
 *   2026-09-05 que esta regla no revoca).</li>
 * </ol>
 *
 * <p>Por que no "sin push nunca que haya alarma local": la antelacion de la alarma es la mas
 * temprana de las que eligio el aprendiz, pero el servidor no sabe si el telefono sigue teniendo
 * la app instalada o los permisos de notificacion. Cortar tambien el push al navegador dejaria sin
 * aviso a quien usa la app web, que es justamente quien no tiene alarma.
 *
 * <p><b>Cambiado 2026-09-28 (D-217).</b> El punto 2 decia «push solo al navegador» ({@code SOLO_NAVEGADOR}):
 * se confiaba en que la alarma local siempre estaba. Probado en el emulador, detener la app a la fuerza
 * borra la alarma de mañana hasta que se vuelve a abrir («Jugo verde mañana, antes: 1 / tras detenerla
 * a la fuerza: 0»), y ese dia no sonaba nada. Ahora la app confirma sus alarmas al servidor cada vez que
 * las rearma, y el telefono que no confirmo en 26 h —o nunca, como el APK anterior a D-217— recibe
 * tambien el push.
 */
public final class EntregaDelAvisoDeHabito {

    /** Espejo de {@code habits.TipoAvisoHabito.INICIO}; viaja como String en el evento. */
    static final String AVISO_INICIO = "INICIO";

    private EntregaDelAvisoDeHabito() {
    }

    public static EntregaPush para(String tipoAviso, Boolean recordatorioActivo, Integer minutosRecordatorio) {
        if (Boolean.FALSE.equals(recordatorioActivo)) {
            return EntregaPush.NINGUNO;
        }
        boolean telefonoTieneAlarma = Boolean.TRUE.equals(recordatorioActivo) && minutosRecordatorio != null;
        if (AVISO_INICIO.equals(tipoAviso) && telefonoTieneAlarma) {
            return EntregaPush.RESPALDO_DE_ALARMA_LOCAL;
        }
        return EntregaPush.TODOS;
    }
}
