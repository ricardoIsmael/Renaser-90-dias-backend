package com.renaser.os.habits.infrastructure.adapter.out.persistence.habito;

import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.UserId;
import org.springframework.stereotype.Component;

@Component
class HabitoPersistenceMapper {

    Habito toDomain(HabitoJpaEntity e) {
        return Habito.rehydrate(HabitoId.of(e.getId()), toDomainAmbito(e.getAmbito()),
                e.getParticipanteId() != null ? UserId.of(e.getParticipanteId()) : null, e.getTitulo(),
                e.getDescripcion(), toDomainTipo(e.getTipo()), e.getCategoriaClave(), e.getIconoClave(),
                e.getClaveSistema(), toDomainExigencia(e.getExigenciaEvidencia()), e.isEsOpcional(),
                e.isObligatorioEnIntoxicacion(), e.isEleccionDiaSemanal(),
                e.getHorasExtraEvidencia() != null ? e.getHorasExtraEvidencia().intValue() : null,
                e.getDiaLimiteEdicionLibre() != null ? e.getDiaLimiteEdicionLibre().intValue() : null,
                toDomainPlantilla(e.getPlantillaClave()), e.getEtiquetaMeta(), e.isActivo(), e.getCreadoEn(),
                e.getActualizadoEn());
    }

    HabitoJpaEntity toEntity(Habito h) {
        return new HabitoJpaEntity(h.id().value(), toJpaAmbito(h.ambito()),
                h.participanteId() != null ? h.participanteId().value() : null, h.titulo(), h.descripcion(),
                toJpaTipo(h.tipo()), h.categoriaClave(), h.iconoClave(), h.claveSistema(),
                toJpaExigencia(h.exigenciaEvidencia()), h.esOpcional(), h.obligatorioEnIntoxicacion(),
                h.eleccionDiaSemanal(), h.horasExtraEvidencia() != null ? h.horasExtraEvidencia().shortValue() : null,
                h.diaLimiteEdicionLibre() != null ? h.diaLimiteEdicionLibre().shortValue() : null,
                toJpaPlantilla(h.plantillaClave()), h.etiquetaMeta(), h.activo(), h.creadoEn(), h.actualizadoEn());
    }

    private AmbitoHabitoJpa toJpaAmbito(AmbitoHabito a) {
        return a == AmbitoHabito.SISTEMA ? AmbitoHabitoJpa.SISTEMA : AmbitoHabitoJpa.PERSONAL;
    }

    private AmbitoHabito toDomainAmbito(AmbitoHabitoJpa a) {
        return a == AmbitoHabitoJpa.SISTEMA ? AmbitoHabito.SISTEMA : AmbitoHabito.PERSONAL;
    }

    private TipoHabitoJpa toJpaTipo(TipoHabito t) {
        return switch (t) {
            case CHECKBOX -> TipoHabitoJpa.CHECKBOX;
            case JOURNALING -> TipoHabitoJpa.JOURNALING;
            case CALIFICACION -> TipoHabitoJpa.CALIFICACION;
            case BLOQUEO -> TipoHabitoJpa.BLOQUEO;
        };
    }

    private TipoHabito toDomainTipo(TipoHabitoJpa t) {
        return switch (t) {
            case CHECKBOX -> TipoHabito.CHECKBOX;
            case JOURNALING -> TipoHabito.JOURNALING;
            case CALIFICACION -> TipoHabito.CALIFICACION;
            case BLOQUEO -> TipoHabito.BLOQUEO;
        };
    }

    private ExigenciaEvidenciaJpa toJpaExigencia(ExigenciaEvidencia e) {
        return e == ExigenciaEvidencia.OPCIONAL ? ExigenciaEvidenciaJpa.OPCIONAL : ExigenciaEvidenciaJpa.OBLIGATORIA;
    }

    private ExigenciaEvidencia toDomainExigencia(ExigenciaEvidenciaJpa e) {
        return e == ExigenciaEvidenciaJpa.OPCIONAL ? ExigenciaEvidencia.OPCIONAL : ExigenciaEvidencia.OBLIGATORIA;
    }

    private PlantillaHabitoPersonalJpa toJpaPlantilla(PlantillaHabitoPersonal p) {
        if (p == null) {
            return null;
        }
        return switch (p) {
            case GIMNASIO -> PlantillaHabitoPersonalJpa.GIMNASIO;
            case CORRER -> PlantillaHabitoPersonalJpa.CORRER;
            case OTRO -> PlantillaHabitoPersonalJpa.OTRO;
        };
    }

    private PlantillaHabitoPersonal toDomainPlantilla(PlantillaHabitoPersonalJpa p) {
        if (p == null) {
            return null;
        }
        return switch (p) {
            case GIMNASIO -> PlantillaHabitoPersonal.GIMNASIO;
            case CORRER -> PlantillaHabitoPersonal.CORRER;
            case OTRO -> PlantillaHabitoPersonal.OTRO;
        };
    }
}
