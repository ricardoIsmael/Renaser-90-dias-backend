package com.renaser.os.mentoring.application.services;

import com.renaser.os.mentoring.api.EvaluacionDeMentorFinder;
import com.renaser.os.mentoring.application.ports.in.ConsultarEvaluacionPropiaUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarEvaluacionPropiaUseCase.EvaluacionPropia;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Service;

import java.time.YearMonth;

/**
 * Implementación de {@link EvaluacionDeMentorFinder}: delega en la evaluación propia del mentor. No
 * copia su cálculo — si se copiara, el líder y el mentor podrían leer notas distintas del mismo mes.
 *
 * <p>{@code ConsultarEvaluacionPropiaUseCase} se arma desde los tramos del usuario que recibe y no
 * autoriza nada por sí mismo; quien expone esto por HTTP decide quién puede pedir la de otro.
 */
@Service
public class EvaluacionDeMentorService implements EvaluacionDeMentorFinder {

    private final ConsultarEvaluacionPropiaUseCase evaluacionPropia;

    EvaluacionDeMentorService(ConsultarEvaluacionPropiaUseCase evaluacionPropia) {
        this.evaluacionPropia = evaluacionPropia;
    }

    @Override
    public EvaluacionDeMentor evaluacionDe(UserId mentorId, YearMonth mes) {
        EvaluacionPropia propia = evaluacionPropia.evaluacionDe(mentorId, mes);
        return new EvaluacionDeMentor(propia.mes(), propia.zona(), propia.porcentaje(), propia.entregadas(),
                propia.esperadas(), propia.alumnosEvaluados(), propia.estado(), propia.versionFormula(),
                propia.corteEn());
    }
}
