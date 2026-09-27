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
 *       APK de producción y en el nuevo. Todo Android con token registrado lo tiene. Va siempre.</li>
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
 */
final class CanalAndroidExpo {

    static final String ACOMPANAMIENTO = "avisos-acompanamiento";
    static final String HABITOS = "recordatorios-habitos";
    static final String EVENTOS = "recordatorios-eventos";

    private final boolean canalesDeRecordatorios;

    CanalAndroidExpo(boolean canalesDeRecordatorios) {
        this.canalesDeRecordatorios = canalesDeRecordatorios;
    }

    /** Vacío = sin {@code channelId}: el teléfono usa su canal por defecto. */
    Optional<String> para(TipoNotificacion tipo) {
        if (tipo == null) {
            return Optional.empty();
        }
        return switch (tipo) {
            case ACOMPANAMIENTO_ALUMNO -> Optional.of(ACOMPANAMIENTO);
            case RECORDATORIO_HABITO -> soloConCanalesDeRecordatorios(HABITOS);
            case RECORDATORIO_EVENTO -> soloConCanalesDeRecordatorios(EVENTOS);
            default -> Optional.empty();
        };
    }

    private Optional<String> soloConCanalesDeRecordatorios(String canal) {
        return canalesDeRecordatorios ? Optional.of(canal) : Optional.empty();
    }
}
