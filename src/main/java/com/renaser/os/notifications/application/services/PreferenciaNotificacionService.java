package com.renaser.os.notifications.application.services;

import com.renaser.os.notifications.application.ports.in.preferencia.GestionarPreferenciasUseCase;
import com.renaser.os.notifications.application.ports.out.preferencia.LoadPreferenciasPort;
import com.renaser.os.notifications.application.ports.out.preferencia.SavePreferenciaPort;
import com.renaser.os.notifications.domain.model.notificacion.TipoNotificacion;
import com.renaser.os.notifications.domain.model.preferencia.PreferenciaNotificacion;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Autoservicio: los dos casos de uso solo reciben {@code actorId} — ver javadoc de
 * {@link GestionarPreferenciasUseCase}, el blindaje es de firma, no un chequeo en runtime.
 */
@Service
public class PreferenciaNotificacionService implements GestionarPreferenciasUseCase {

    private final LoadPreferenciasPort loadPreferenciasPort;
    private final SavePreferenciaPort savePreferenciaPort;
    private final ActorNotificacionesGuard actorGuard;
    private final Clock clock;

    public PreferenciaNotificacionService(LoadPreferenciasPort loadPreferenciasPort,
                                           SavePreferenciaPort savePreferenciaPort,
                                           ActorNotificacionesGuard actorGuard, Clock clock) {
        this.loadPreferenciasPort = loadPreferenciasPort;
        this.savePreferenciaPort = savePreferenciaPort;
        this.actorGuard = actorGuard;
        this.clock = clock;
    }

    @Override
    public List<PreferenciaNotificacion> consultar(UserId actorId) {
        actorGuard.requireActivo(actorId);
        Map<TipoNotificacion, PreferenciaNotificacion> guardadas = new LinkedHashMap<>();
        for (PreferenciaNotificacion pref : loadPreferenciasPort.porUsuario(actorId)) {
            guardadas.put(pref.tipo(), pref);
        }
        return conTodosLosTipos(actorId, guardadas);
    }

    @Override
    @Transactional
    public List<PreferenciaNotificacion> actualizar(ActualizarPreferenciasCommand command) {
        actorGuard.requireActivo(command.actorId());
        for (ItemPreferencia item : command.preferencias()) {
            savePreferenciaPort.upsert(
                    PreferenciaNotificacion.de(command.actorId(), item.tipo(), item.habilitada(), clock));
        }
        return consultar(command.actorId());
    }

    /** Completa con el default HABILITADA los tipos sin fila propia — mismo criterio que
     * {@code profile/service.ts:getNotificationPreferences} del repo viejo. */
    private List<PreferenciaNotificacion> conTodosLosTipos(UserId actorId,
                                                             Map<TipoNotificacion, PreferenciaNotificacion> guardadas) {
        List<PreferenciaNotificacion> resultado = new ArrayList<>(TipoNotificacion.values().length);
        for (TipoNotificacion tipo : TipoNotificacion.values()) {
            resultado.add(guardadas.getOrDefault(tipo,
                    PreferenciaNotificacion.de(actorId, tipo, PreferenciaNotificacion.DEFAULT_HABILITADA, clock)));
        }
        return resultado;
    }
}
