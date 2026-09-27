package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.out.notificacion.LoadNotificacionPort;
import com.renaser.os.notifications.application.ports.out.notificacion.SaveNotificacionPort;
import com.renaser.os.notifications.application.ports.out.preferencia.LoadPreferenciasPort;
import com.renaser.os.notifications.application.ports.out.push.DesactivarTokenPushPort;
import com.renaser.os.notifications.application.ports.out.push.PushPort;
import com.renaser.os.notifications.application.ports.out.tokenpush.LoadTokenPushPort;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Arma un {@link NotificacionService} real para las pruebas de los listeners, que viven en otro
 * paquete y no ven {@link ActorNotificacionesGuard} (package-private a proposito).
 */
public final class NotificacionServiceDePrueba {

    private NotificacionServiceDePrueba() {
    }

    public record Puertos(LoadNotificacionPort loadNotificacionPort, SaveNotificacionPort saveNotificacionPort,
                          LoadPreferenciasPort loadPreferenciasPort, LoadTokenPushPort loadTokenPushPort,
                          PushPort pushPort, DesactivarTokenPushPort desactivarTokenPushPort,
                          UserSummaryFinder userSummaryFinder, PlatformTransactionManager transactionManager) {
    }

    public static NotificacionService con(Puertos puertos, Clock clock) {
        return new NotificacionService(puertos.loadNotificacionPort(), puertos.saveNotificacionPort(),
                puertos.loadPreferenciasPort(), puertos.loadTokenPushPort(), puertos.pushPort(),
                puertos.desactivarTokenPushPort(), new ActorNotificacionesGuard(puertos.userSummaryFinder()), clock,
                puertos.transactionManager());
    }
}
