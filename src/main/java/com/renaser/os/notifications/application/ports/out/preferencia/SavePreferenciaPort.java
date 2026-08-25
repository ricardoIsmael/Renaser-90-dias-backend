package com.renaser.os.notifications.application.ports.out.preferencia;

import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;

public interface SavePreferenciaPort {

    void upsert(PreferenciaNotificacion preferencia);
}
