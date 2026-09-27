package com.renaser.os.notifications.infrastructure.adapter.out.push;

import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;

import java.util.Optional;

/**
 * El canal de Android por el que sale un push de Expo, según el tipo del aviso (D-188, E-9 de la
 * retroalimentación del 26/09).
 *
 * <p>En Android 8+ el sonido y el silencio son del CANAL. Sin {@code channelId} todo cae al canal
 * por defecto y la persona no puede callar los recordatorios de eventos sin callar todo lo demás.
 *
 * <h2>Por qué no se manda un canal que el teléfono puede no tener</h2>
 *
 * <p>La documentación de Expo (docs.expo.dev/push-notifications/sending-notifications, campo
 * {@code channelId}) dice: <i>"If an ID is specified but the corresponding channel does not exist
 * on the device (that has not yet been created by your app), the notification will not be displayed
 * to the user."</i> El código de {@code expo-notifications} 57 ({@code BaseNotificationBuilder})
 * cae al canal de respaldo cuando el que se pide no existe, pero eso cubre solo los mensajes que
 * arma la librería; la documentación oficial promete lo contrario y no hay forma de probarlo acá.
 * Ante la duda se toma la lectura que no pierde avisos: <b>solo se nombra un canal que el teléfono
 * seguro tiene</b>.
 *
 * <ul>
 *   <li>{@code avisos-acompanamiento}: lo crea {@code pushNativo.ts} ANTES de pedir el token, en el
 *       APK de producción y en el nuevo. Todo Android con token registrado lo tiene; aun así
 *       va apagado (ver abajo).</li>
 *   <li>{@code recordatorios-habitos}: el APK de producción lo crea recién al programar la primera
 *       alarma local de un hábito; quien nunca programó una no lo tiene.</li>
 *   <li>{@code recordatorios-eventos}: el APK de producción no lo crea nunca.</li>
 * </ul>
 *
 * <p>Los dos últimos los crea el APK nuevo (rama {@code eventos-app}, {@code AbridorDeEventos})
 * al entrar, con su id BASE (sin el sufijo {@code -campana}/{@code -vibrar} del sonido elegido).
 * Se mandan solo con {@code renaser.notifications.expo-push.canales-de-recordatorios=true}, que se
 * enciende cuando el APK nuevo sea el único en uso. Hasta entonces esos avisos siguen saliendo por
 * el canal por defecto, como hoy.
 *
 * <h2>Por qué el de acompañamiento también va apagado</h2>
 *
 * <p>La app crea {@code avisos-acompanamiento} con importancia {@code DEFAULT}: suena pero no sale
 * como banner emergente. Hoy ese aviso cae al canal por defecto, que puede ser {@code HIGH} (el de
 * respaldo de {@code expo-notifications} lo es) y salir como banner. Mandarlo por su canal podría
 * quitarle el banner al mentor sin que nadie lo pidiera. Se enciende aparte, con
 * {@code renaser.notifications.expo-push.canal-de-acompanamiento=true}, cuando se decida.
 *
 * <p><b>Con las dos propiedades apagadas (el default) el mensaje es idéntico al de antes de D-188.</b>
 */
final class CanalAndroidExpo {

    static final String ACOMPANAMIENTO = "avisos-acompanamiento";
    static final String HABITOS = "recordatorios-habitos";
    static final String EVENTOS = "recordatorios-eventos";

    private final boolean canalDeAcompanamiento;
    private final boolean canalesDeRecordatorios;

    CanalAndroidExpo(boolean canalDeAcompanamiento, boolean canalesDeRecordatorios) {
        this.canalDeAcompanamiento = canalDeAcompanamiento;
        this.canalesDeRecordatorios = canalesDeRecordatorios;
    }

    /** Vacío = sin {@code channelId}: el teléfono usa su canal por defecto. */
    Optional<String> para(TipoNotificacion tipo) {
        if (tipo == null) {
            return Optional.empty();
        }
        return switch (tipo) {
            case ACOMPANAMIENTO_ALUMNO -> siEstaEncendido(canalDeAcompanamiento, ACOMPANAMIENTO);
            case RECORDATORIO_HABITO -> siEstaEncendido(canalesDeRecordatorios, HABITOS);
            case RECORDATORIO_EVENTO -> siEstaEncendido(canalesDeRecordatorios, EVENTOS);
            default -> Optional.empty();
        };
    }

    private static Optional<String> siEstaEncendido(boolean encendido, String canal) {
        return encendido ? Optional.of(canal) : Optional.empty();
    }
}
