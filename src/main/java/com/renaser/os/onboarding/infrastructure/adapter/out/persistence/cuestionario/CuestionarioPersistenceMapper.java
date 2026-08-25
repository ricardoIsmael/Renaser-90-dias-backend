package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.cuestionario;

import com.renaser.os.onboarding.domain.model.cuestionario.OpcionPregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Seccion;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import org.springframework.stereotype.Component;

@Component
class CuestionarioPersistenceMapper {

    Seccion toDomain(SeccionOnboardingJpaEntity e) {
        return new Seccion(e.getId(), e.getFlujo(), e.getClaveSeccion(), e.getTitulo(), e.getDescripcion(),
                e.getOrden(), e.getCreadoEn());
    }

    Pregunta toDomain(PreguntaOnboardingJpaEntity e) {
        return new Pregunta(e.getId(), e.getSeccionId(), e.getClavePregunta(), e.getTexto(),
                toDomainTipo(e.getTipo()), e.getConfigEscala(), e.isRequerida(), e.getOrden(),
                e.getReglasValidacion(), e.getPreguntaPadreId(), e.getCreadoEn());
    }

    OpcionPregunta toDomain(OpcionPreguntaJpaEntity e) {
        return new OpcionPregunta(e.getPreguntaId(), e.getOrden(), e.getValor(), e.getEtiqueta());
    }

    private TipoPreguntaOnboarding toDomainTipo(TipoPreguntaOnboardingJpa jpa) {
        return switch (jpa) {
            case TEXTO -> TipoPreguntaOnboarding.TEXTO;
            case AREA_TEXTO -> TipoPreguntaOnboarding.AREA_TEXTO;
            case NUMERO -> TipoPreguntaOnboarding.NUMERO;
            case ESCALA -> TipoPreguntaOnboarding.ESCALA;
            case SELECCION_UNICA -> TipoPreguntaOnboarding.SELECCION_UNICA;
            case SELECCION_MULTIPLE -> TipoPreguntaOnboarding.SELECCION_MULTIPLE;
            case AUDIO -> TipoPreguntaOnboarding.AUDIO;
            case FIRMA -> TipoPreguntaOnboarding.FIRMA;
            case CASILLA -> TipoPreguntaOnboarding.CASILLA;
            case FECHA -> TipoPreguntaOnboarding.FECHA;
            case ARCHIVO -> TipoPreguntaOnboarding.ARCHIVO;
        };
    }
}
