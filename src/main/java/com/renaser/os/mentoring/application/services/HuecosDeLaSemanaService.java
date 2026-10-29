package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.api.HuecosDeHorarioFinder;
import com.renaser.os.mentoring.api.HuecosDeLaSemana;
import com.renaser.os.mentoring.application.ports.out.FotosDeHuecosPort;
import com.renaser.os.mentoring.domain.model.huecos.FotoDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.SemanaDeHuecos;
import com.renaser.os.mentoring.domain.model.huecos.SemanaPedida;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.NoSuchElementException;
import java.util.Optional;

/**
 * Los huecos de horario de una persona en una semana (spec 015). La semana en curso se calcula al pedirla; una semana
 * ya terminada sale de la foto que se guardó al cerrarla (FR-018), con el horario que regía entonces. Una semana
 * terminada sin foto (anterior a esta función, o que el barrido todavía no cerró) se calcula igual, con el horario de
 * hoy, y se entrega como no cerrada.
 *
 * <p>No autoriza: quien llama (el guard del mentor o el de liderazgo) ya lo hizo. Rechaza a la persona suspendida, como
 * el horario de sus hábitos.
 */
@Service
public class HuecosDeLaSemanaService implements HuecosDeHorarioFinder {

    private final ParticipacionProgramaFinder participaciones;
    private final CalculoDeLosHuecos calculo;
    private final FotosDeHuecosPort fotos;
    private final Clock clock;

    HuecosDeLaSemanaService(ParticipacionProgramaFinder participaciones, CalculoDeLosHuecos calculo,
                            FotosDeHuecosPort fotos, Clock clock) {
        this.participaciones = participaciones;
        this.calculo = calculo;
        this.fotos = fotos;
        this.clock = clock;
    }

    @Override
    @Transactional(readOnly = true)
    public HuecosDeLaSemana semanaDe(UserId personaId, LocalDate lunesPedido) {
        ParticipacionPrograma persona = participaciones.deParticipante(personaId)
                .orElseThrow(() -> new NoSuchElementException("Persona no encontrada: " + personaId));
        if (persona.suspendido()) {
            throw new NotAuthorizedException("La cuenta esta suspendida");
        }
        // El «hoy» de la persona, no el del servidor (regla 02, E-91).
        LocalDate hoy = clock.now().atZone(persona.zona()).toLocalDate();
        LocalDate primeraSemana = primeraSemanaDe(persona);
        LocalDate lunes = SemanaPedida.resolver(lunesPedido, hoy, primeraSemana);
        Optional<FotoDeHuecos> foto = lunes.isBefore(SemanaPedida.lunesDe(hoy))
                ? fotos.deLaSemana(personaId, lunes) : Optional.empty();
        if (foto.isPresent()) {
            return ArmadoDeLosHuecos.armar(foto.get().semana(), primeraSemana, true);
        }
        SemanaDeHuecos semana = calculo.calcular(persona, lunes, hoy);
        return ArmadoDeLosHuecos.armar(semana, primeraSemana, false);
    }

    /** El lunes de la semana de su Día 1; {@code null} mientras no lo eligió (su fecha de inicio es provisional). */
    private static LocalDate primeraSemanaDe(ParticipacionPrograma persona) {
        LocalDate diaUno = persona.diaUnoElegido();
        return diaUno == null ? null : SemanaPedida.lunesDe(diaUno);
    }
}
