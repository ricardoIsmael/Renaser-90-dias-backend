package com.renaser.os.leadership.application.services;

import com.renaser.os.community.api.AcompanamientoFinder;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDelAprendizDelMentorUseCase;
import com.renaser.os.leadership.application.services.LecturaDeLaFicha.Lectura;
import com.renaser.os.leadership.application.services.PersonasDelGrupo.Miembro;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.leadership.domain.model.cumplimiento.GrupoDeLaPersona;
import com.renaser.os.leadership.domain.model.cumplimiento.SemanaDelMentor;
import com.renaser.os.leadership.domain.model.cumplimiento.VentanaDeLaFicha;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.NoSuchElementException;

/**
 * La semana de un aprendiz para su mentor (spec 055). Autoriza primero y lee después —el camino que termina en 403 no lee
 * nada— y arma con las mismas lecturas y el mismo {@link ArmadoDeLaFicha} que la ficha del líder, así la semana, los
 * colores y los hábitos del mentor no pueden diferir de los del líder. Lo único propio es la ventana
 * ({@link SemanaDelMentor}) y el Día 1 que se suma a la respuesta.
 */
@Service
public class SemanaDelAprendizService implements ConsultarSemanaDelAprendizDelMentorUseCase {

    private final AccesoDelMentorAlAprendiz acceso;
    private final ParticipacionProgramaFinder participaciones;
    private final UserSummaryFinder usuarios;
    private final AcompanamientoFinder acompanamiento;
    private final LecturaDeLaFicha lecturas;
    private final Clock clock;

    SemanaDelAprendizService(AccesoDelMentorAlAprendiz acceso, ParticipacionProgramaFinder participaciones,
                             UserSummaryFinder usuarios, AcompanamientoFinder acompanamiento, LecturaDeLaFicha lecturas,
                             Clock clock) {
        this.acceso = acceso;
        this.participaciones = participaciones;
        this.usuarios = usuarios;
        this.acompanamiento = acompanamiento;
        this.lecturas = lecturas;
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
        LocalDate hoy = ahora.atZone(participacion.zona()).toLocalDate();
        LocalDate diaUno = participacion.diaUnoElegido();
        VentanaDeLaFicha ventana = SemanaDelMentor.ventana(consulta.semanaHasta(), hoy, diaUno);
        Miembro miembro = new Miembro(perfil, participacion, false);
        Mirada mirada = new Mirada(miembro, grupoDe(consulta));
        Lectura lectura = lecturas.leer(mirada, ventana);
        return new SemanaDelAprendizParaElMentor(ArmadoDeLaFicha.armar(miembro, ventana, lectura), diaUno);
    }

    private GrupoDeLaPersona grupoDe(ConsultaSemanaDelAprendiz consulta) {
        String nombre = acompanamiento.grupo(consulta.grupoId()).map(AcompanamientoFinder.GrupoBasico::nombre).orElse(null);
        return new GrupoDeLaPersona(consulta.grupoId(), nombre, consulta.actorId(), false);
    }
}
