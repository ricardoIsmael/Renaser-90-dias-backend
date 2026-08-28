package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.audioterapiaadmin.ActualizarDuracionAudioterapiaUseCase;
import com.renaser.os.habits.application.ports.out.audioterapia.AudioterapiaCatalogPort.Audioterapia;
import com.renaser.os.habits.application.ports.out.audioterapia.SaveAudioterapiaPort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Panel admin del catálogo de audioterapias -- solo ADMIN/ALCHEMIST, mismo guard que el resto del hueco #11. */
@Service
class AudioterapiaAdminService implements ActualizarDuracionAudioterapiaUseCase {

    private final SaveAudioterapiaPort savePort;
    private final HabitoAdminGuard guard;

    AudioterapiaAdminService(SaveAudioterapiaPort savePort, HabitoAdminGuard guard) {
        this.savePort = savePort;
        this.guard = guard;
    }

    @Override
    @Transactional
    public AudioterapiaActualizada actualizar(ActualizarDuracionAudioterapiaCommand command) {
        guard.requireAdmin(command.actorId());
        Audioterapia actualizada = savePort.actualizarDuracion(command.semana(), command.duracionDias());
        return new AudioterapiaActualizada(actualizada.semana(), actualizada.titulo(), actualizada.duracionDias());
    }
}
