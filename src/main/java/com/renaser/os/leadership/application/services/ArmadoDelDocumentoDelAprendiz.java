package com.renaser.os.leadership.application.services;

import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.DiaDeLaFicha;
import com.renaser.os.leadership.application.ports.in.ConsultarSemanaDePersonaUseCase.SemanaDeLaPersona;
import com.renaser.os.leadership.application.services.PersonasDelPrograma.Mirada;
import com.renaser.os.leadership.domain.model.cumplimiento.SemanaDelMentor;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Encabezado;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.EvaluacionDelMentor;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.TablaDePilares;
import com.renaser.os.leadership.domain.model.documento.DatosDelAprendiz.Titular;
import com.renaser.os.mentoring.api.EvaluacionDeMentorFinder;
import com.renaser.os.mentoring.api.EvaluacionDeMentorFinder.EvaluacionDeMentor;
import com.renaser.os.shared.domain.RechazoConCodigoException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.FormacionesFinder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;

/**
 * Reúne los datos del documento semanal de una persona (spec 056, FR-035): su semana de sábado a viernes —la misma {@link SemanaDeSabadoAViernes}
 * que arma la ficha del mentor y la del líder—, la semana anterior para comparar, su formación y su día, y la evaluación del mes de su mentor.
 * Es también la semana propia de un mentor que sigue su programa (FR-036).
 *
 * <p><b>No autoriza ni deja registro</b>: quien lo llama ya decidió que puede verla ({@link AccesoAlDocumento}). No lee las notas privadas del
 * mentor: ningún dato del documento sale de ahí (FR-037). El plazo es el de la formación de la persona ({@code duracionDias}), nunca un texto fijo.
 */
@Component
class ArmadoDelDocumentoDelAprendiz {

    private static final Logger log = LoggerFactory.getLogger(ArmadoDelDocumentoDelAprendiz.class);

    private final SemanaDeSabadoAViernes semana;
    private final FormacionesFinder formaciones;
    private final EvaluacionDeMentorFinder evaluaciones;

    ArmadoDelDocumentoDelAprendiz(SemanaDeSabadoAViernes semana, FormacionesFinder formaciones, EvaluacionDeMentorFinder evaluaciones) {
        this.semana = semana;
        this.formaciones = formaciones;
        this.evaluaciones = evaluaciones;
    }

    /**
     * @param semanaHasta el viernes que cierra la semana; null = la semana en curso de la persona
     * @throws RechazoConCodigoException {@code SEMANA_INVALIDA} o {@code FUERA_DEL_PROGRAMA}
     */
    DatosDelAprendiz armar(Mirada mirada, LocalDate semanaHasta, Instant ahora) {
        SemanaDeLaPersona actual = semana.armar(mirada, semanaHasta, ahora).semana();
        SemanaDeLaPersona anterior = anteriorA(mirada, actual, ahora);
        ZoneId zona = ZoneId.of(actual.zona());
        LocalDate hoy = ahora.atZone(zona).toLocalDate();
        TablaDePilares tabla = ResumenDeLaSemana.tabla(actual);
        return new DatosDelAprendiz(encabezado(mirada, actual, hoy), new Titular(actual.color(), actual.porcentaje()),
                ResumenDeLaSemana.cifras(actual, anterior), tabla, ResumenDeLaSemana.evidencias(actual, zona),
                ResumenDeLaSemana.palabras(actual), evaluacionDelMentor(actual, hoy), ResumenDeLaSemana.foco(tabla),
                ResumenDeLaSemana.detalle(actual));
    }

    /** La semana de antes, para comparar; null si es anterior a su Día 1: no hay nada con qué comparar y no es un error. */
    private SemanaDeLaPersona anteriorA(Mirada mirada, SemanaDeLaPersona actual, Instant ahora) {
        try {
            return semana.armar(mirada, actual.hasta().minusDays(7), ahora).semana();
        } catch (RechazoConCodigoException e) {
            if (SemanaDelMentor.FUERA_DEL_PROGRAMA.equals(e.codigo())) {
                return null;
            }
            throw e;
        }
    }

    private Encabezado encabezado(Mirada mirada, SemanaDeLaPersona actual, LocalDate hoy) {
        ParticipacionPrograma participacion = mirada.miembro().participacion();
        return new Encabezado(actual.nombre(), formaciones.de(participacion.formacion()).nombre(), diaAl(actual, hoy),
                participacion.duracionDias(), actual.mentor() == null ? null : actual.mentor().nombre(),
                mirada.grupo() == null ? null : mirada.grupo().nombre(), actual.desde(), actual.hasta(), actual.zona());
    }

    /** Su día del programa al cerrar la semana, o el de hoy si la semana corre; null mientras no haya un día del programa. */
    private static Integer diaAl(SemanaDeLaPersona actual, LocalDate hoy) {
        Integer dia = null;
        for (DiaDeLaFicha delaSemana : actual.dias()) {
            if (!delaSemana.fecha().isAfter(hoy) && delaSemana.diaDePrograma() != null) {
                dia = delaSemana.diaDePrograma();
            }
        }
        return dia;
    }

    /** La evaluación del mes de su mentor; null si no tiene mentor o si esa fuente no respondió (no se inventa un cero). */
    private EvaluacionDelMentor evaluacionDelMentor(SemanaDeLaPersona actual, LocalDate hoy) {
        if (actual.mentor() == null) {
            return null;
        }
        YearMonth mes = YearMonth.from(actual.hasta().isAfter(hoy) ? hoy : actual.hasta());
        try {
            EvaluacionDeMentor evaluacion = evaluaciones.evaluacionDe(UserId.of(actual.mentor().id()), mes);
            return new EvaluacionDelMentor(mes.toString(), evaluacion.porcentaje(), evaluacion.estado());
        } catch (RuntimeException e) {
            log.warn("[leadership] no se pudo leer la evaluación del mentor para el documento: {}", e.toString());
            return null;
        }
    }
}
