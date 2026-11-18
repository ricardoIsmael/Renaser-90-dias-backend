package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDelAprendizDelMentorUseCase;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.leadership.domain.model.cumplimiento.GrupoDeLaPersona;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * La semana de un aprendiz para su mentor (spec 055). Autoriza primero y lee después —el camino que termina en 403 no lee
 * nada— y arma con {@link SemanaDeSabadoAViernes}, el mismo armado que usa la ficha del líder desde la spec 056, así la semana, los
 * colores y los hábitos del mentor no pueden diferir de los del líder. Lo único propio es la puerta: acompañar hoy el grupo.
 */
@Service
public class SemanaDelAprendizService implements ConsultarSemanaDelAprendizDelMentorUseCase {

    private final AccesoDelMentorAlAprendiz acceso;
    private final ParticipacionProgramaFinder participaciones;
    private final UserSummaryFinder usuarios;
    private final AcompanamientoFinder acompanamiento;
    private final SemanaDeSabadoAViernes semana;
    private final Clock clock;

    SemanaDelAprendizService(AccesoDelMentorAlAprendiz acceso, ParticipacionProgramaFinder participaciones,
                             UserSummaryFinder usuarios, AcompanamientoFinder acompanamiento, SemanaDeSabadoAViernes semana,
                             Clock clock) {
        this.acceso = acceso;
        this.participaciones = participaciones;
        this.usuarios = usuarios;
        this.acompanamiento = acompanamiento;
        this.semana = semana;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public SemanaDelAprendizParaElMentor semanaDe(ConsultaSemanaDelAprendiz consulta) {
        Instant ahora = clock.now();
        UserId aprendizId = UserId.of(consulta.aprendizId());
        acceso.exigir(consulta.actorId(), consulta.grupoId(), aprendizId, ahora);
        UserSummary perfil = usuarios.findById(aprendizId)
                .orElseThrow(() -> new NoSuchElementException("Aprendiz no encontrado"));
        ParticipacionPrograma participacion = participaciones.deParticipante(aprendizId)
                .orElseThrow(() -> new NoSuchElementException("Aprendiz no encontrado"));
        Mirada mirada = new Mirada(new Miembro(perfil, participacion, false), grupoDe(consulta));
        return semana.armar(mirada, consulta.semanaHasta(), ahora);
    }

    private GrupoDeLaPersona grupoDe(ConsultaSemanaDelAprendiz consulta) {
        String nombre = acompanamiento.grupo(consulta.grupoId()).map(AcompanamientoFinder.GrupoBasico::nombre).orElse(null);
        return new GrupoDeLaPersona(consulta.grupoId(), nombre, consulta.actorId(), false);
    }
}
