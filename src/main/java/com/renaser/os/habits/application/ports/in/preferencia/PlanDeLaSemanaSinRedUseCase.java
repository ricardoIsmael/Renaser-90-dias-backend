package com.renaser.os.habits.application.ports.in.preferencia;

import com.renaser.os.habits.domain.model.preferencia.CambioDeFecha;
import com.renaser.os.habits.application.ports.in.preferencia.RepetirSemanaUseCase.ResultadoDeRepeticion;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.preferencia.SemanaCalendaria;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.UserId;

import java.util.List;

/**
 * Spec 048 (FR-019, fase 3): «solo esta semana», repetir la semana y deshacerla SIN RED, con la hora real. Mismas reglas que con red y la
 * regla de choques D13: si alguna fecha propia que toca cambió en el servidor después de lo que hiciste, gana el servidor
 * ({@code CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR}); un hábito que ya no existe es {@code RECURSO_YA_NO_EXISTE}.
 */
public interface PlanDeLaSemanaSinRedUseCase {

    void guardar(UserId actor, HabitoId habito, SemanaCalendaria semana, List<CambioDeFecha> cambios, HechoSinRed sinRed);

    void volverAlPlanDeSiempre(UserId actor, HabitoId habito, SemanaCalendaria semana, HechoSinRed sinRed);

    ResultadoDeRepeticion repetir(UserId actor, SemanaCalendaria modelo, SemanaCalendaria destino, boolean reemplazar, HechoSinRed sinRed);

    int deshacerSemana(UserId actor, SemanaCalendaria semana, HechoSinRed sinRed);
}
