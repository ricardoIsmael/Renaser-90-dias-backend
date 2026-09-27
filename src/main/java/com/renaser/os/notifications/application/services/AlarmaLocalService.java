package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.alarmalocal.ConsultarAlarmaLocalUseCase;
import com.renaser.os.notifications.application.ports.out.tokenpush.LoadTokenPushPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

/**
 * "Activo" es "la fila existe": un token que Expo o el navegador rechazan se borra al instante
 * ({@code DesactivarTokenPushPort}), y los de una cuenta suspendida se revocan en bloque.
 */
@Service
public class AlarmaLocalService implements ConsultarAlarmaLocalUseCase {

    private final LoadTokenPushPort loadTokenPushPort;

    public AlarmaLocalService(LoadTokenPushPort loadTokenPushPort) {
        this.loadTokenPushPort = loadTokenPushPort;
    }

    @Override
    public boolean tieneAlarmaLocal(UserId usuarioId) {
        return loadTokenPushPort.tokensDe(usuarioId).stream()
                .anyMatch(token -> token.plataforma() != null && token.plataforma().programaAlarmaLocal());
    }
}
