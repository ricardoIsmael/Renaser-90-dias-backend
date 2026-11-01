package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.in.formacion.CambiarDuracionDelProgramaUseCase;
import com.renaser.os.users.application.ports.out.ajustediaprograma.SaveAjusteDiaProgramaPort;
import com.renaser.os.users.application.ports.out.formacion.CatalogoDeFormacionesPort;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.participante.SaveParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.ajustediaprograma.AjusteDiaPrograma;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.NoSuchElementException;

/**
 * Ver {@link CambiarDuracionDelProgramaUseCase}. El recurso se carga primero y el guard de Administración después (E-42). La bitácora se escribe en la
 * MISMA transacción que el cambio (D-82): un cambio de duración sin rastro de quién lo hizo es justo lo que esa tabla viene a evitar.
 */
@Service
class CambioDeDuracionDelProgramaService implements CambiarDuracionDelProgramaUseCase {

    private final RequireAdminGuard requireAdminGuard;
    private final LoadUserPort usuarios;
    private final LoadParticipacionProgramaPort participaciones;
    private final SaveParticipacionProgramaPort guardarParticipacion;
    private final SaveAjusteDiaProgramaPort bitacora;
    private final CatalogoDeFormacionesPort catalogo;
    private final IdGenerator ids;
    private final Clock clock;

    CambioDeDuracionDelProgramaService(RequireAdminGuard requireAdminGuard, LoadUserPort usuarios, LoadParticipacionProgramaPort participaciones,
                                       SaveParticipacionProgramaPort guardarParticipacion, SaveAjusteDiaProgramaPort bitacora,
                                       CatalogoDeFormacionesPort catalogo, IdGenerator ids, Clock clock) {
        this.requireAdminGuard = requireAdminGuard;
        this.usuarios = usuarios;
        this.participaciones = participaciones;
        this.guardarParticipacion = guardarParticipacion;
        this.bitacora = bitacora;
        this.catalogo = catalogo;
        this.ids = ids;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void cambiar(CambiarDuracionCommand command) {
        // El recurso se carga primero y el guard después (E-42). El rol se comprueba antes que la participación: el personal no tiene programa que
        // cambiar y eso es un 409 del contrato (E2E P4-I), no el 404 de «no inscripto».
        UserRole rol = usuarios.byId(command.aprendiz()).map(u -> u.role())
                .orElseThrow(() -> new NoSuchElementException("Usuario no encontrado: " + command.aprendiz()));
        requireAdminGuard.requireAdminActivo(command.actor());
        exigirAprendiz(rol);
        ParticipacionPrograma participacion = participaciones.byParticipanteId(command.aprendiz())
                .orElseThrow(() -> new NoSuchElementException("Participante no inscripto en el programa: " + command.aprendiz()));
        exigirMotivo(command.motivo());
        var oferta = catalogo.de(participacion.formacion());
        if (!oferta.ofrece(command.duracionDias())) {
            throw new IllegalArgumentException("«" + oferta.nombre() + "» no ofrece un programa de " + command.duracionDias() + " días.");
        }
        int anterior = participacion.duracionDias();
        participacion.cambiarDuracion(command.duracionDias(), clock);
        guardarParticipacion.save(participacion);
        bitacora.save(AjusteDiaPrograma.registrarCambioDeDuracion(ids.newId(), command.aprendiz(), participacion.diaVigente(clock),
                participacion.diasAjuste(), anterior, command.duracionDias(), command.motivo(), command.actor(), clock));
    }

    private static void exigirAprendiz(UserRole rol) {
        if (rol != UserRole.TRAINEE) {
            throw new IllegalStateException("Solo se cambia la duración de los aprendices. El personal no tiene una duración propia.");
        }
    }

    private static void exigirMotivo(String motivo) {
        String limpio = motivo == null ? "" : motivo.trim();
        if (limpio.length() < 3 || limpio.length() > AjusteDiaPrograma.MAX_MOTIVO) {
            throw new IllegalArgumentException("Escribe el motivo del cambio de duración (de 3 a " + AjusteDiaPrograma.MAX_MOTIVO + " letras).");
        }
    }
}
