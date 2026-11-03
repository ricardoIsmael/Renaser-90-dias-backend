package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FechasDelProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.application.ports.out.participante.LoadParticipacionProgramaPort;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import com.renaser.os.users.domain.model.participante.ParticipacionPrograma;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Implementa {@link FechasDelProgramaFinder} (spec 039): las fechas salen del agregado, que es el único que sabe qué es «mañana» en la zona de la persona y cómo el
 * ajuste de días corre el último día. Una lectura.
 *
 * <p>Solo el aprendiz promete un inicio antes de activar: su programa se activa solo al firmar (spec 036). El personal con una fila sin activar (la de «hacer mi
 * programa» es opcional) no ve fechas: no se le promete un inicio que no eligió.
 */
@Service
class FechasDelProgramaService implements FechasDelProgramaFinder {

    private final LoadParticipacionProgramaPort participaciones;
    private final LoadUserPort usuarios;
    private final Clock clock;

    FechasDelProgramaService(LoadParticipacionProgramaPort participaciones, LoadUserPort usuarios, Clock clock) {
        this.participaciones = participaciones;
        this.usuarios = usuarios;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<FechasDelPrograma> de(UserId persona) {
        return usuarios.byId(persona).filter(usuario -> usuario.hasAccess()).flatMap(usuario -> participaciones.byParticipanteId(persona)
                .map(participacion -> fechas(participacion, usuario.role() == UserRole.TRAINEE)));
    }

    private FechasDelPrograma fechas(ParticipacionPrograma participacion, boolean activaSolaAlFirmar) {
        boolean conFechas = participacion.estaActivado() || activaSolaAlFirmar;
        return new FechasDelPrograma(participacion.formacion(), participacion.duracionDias(), participacion.estaActivado(),
                conFechas ? participacion.inicioQueSeMuestra(clock) : null, conFechas ? participacion.finQueSeMuestra(clock) : null);
    }
}
