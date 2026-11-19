package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.leadership.domain.model.cumplimiento.SemanaDelMentor;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz;
import com.renaser.os.leadership.domain.model.periodo.MesDelReporte;
import com.renaser.os.shared.domain.RechazoConCodigoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.NoSuchElementException;

/**
 * El programa propio de un mentor, para su documento (spec 056, FR-036): en qué zona se dice su semana y, si sigue su programa, esa semana
 * armada como la de un aprendiz. Quien no lo sigue —no lo activó, o la semana es anterior a su Día 1— no tiene «su propia semana», y eso no es un
 * error: el documento trae solo lo suyo como mentor.
 */
@Component
class ProgramaDelMentor {

    private final ParticipacionProgramaFinder participaciones;
    private final PersonasDelPrograma personas;
    private final ArmadoDelDocumentoDelAprendiz aprendiz;

    ProgramaDelMentor(ParticipacionProgramaFinder participaciones, PersonasDelPrograma personas, ArmadoDelDocumentoDelAprendiz aprendiz) {
        this.participaciones = participaciones;
        this.personas = personas;
        this.aprendiz = aprendiz;
    }

    /** La zona de su programa; la del programa (Lima) si no tiene uno: el «hoy» y las horas del documento son de la persona (regla 02). */
    ZoneId zonaDe(UserId mentor) {
        return participaciones.deParticipante(mentor).map(ParticipacionPrograma::zona).orElse(MesDelReporte.ZONA_DEL_PROGRAMA);
    }

    /** @return su semana como la de un aprendiz; null si no sigue su programa o si la semana es anterior a su Día 1 */
    DatosDelAprendiz suSemana(UserId actor, UserId mentor, LocalDate viernes, Instant ahora) {
        try {
            Mirada mirada = personas.mirar(actor, mentor, ahora);
            return aprendiz.armar(mirada, viernes, ahora);
        } catch (NoSuchElementException noLoSigue) {
            return null;
        } catch (RechazoConCodigoException e) {
            if (SemanaDelMentor.FUERA_DEL_PROGRAMA.equals(e.codigo())) {
                return null;
            }
            throw e;
        }
    }
}
