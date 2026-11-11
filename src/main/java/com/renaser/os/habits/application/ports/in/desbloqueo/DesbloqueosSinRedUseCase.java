package com.renaser.os.habits.application.ports.in.desbloqueo;

import com.renaser.os.habits.application.ports.in.desbloqueo.CambiarEstadoHabitoDelPlanUseCase.CambiarEstadoHabitoCommand;
import com.renaser.os.habits.application.ports.in.desbloqueo.ElegirHabitoUseCase.ElegirHabitoCommand;
import com.renaser.os.habits.application.ports.in.desbloqueo.QuitarHabitoDelPlanUseCase.QuitarHabitoCommand;
import com.renaser.os.habits.domain.model.desbloqueo.DesbloqueoHabito;
import com.renaser.os.shared.domain.HechoSinRed;

/**
 * Spec 048 (FR-019, fase 3): elegir, pausar o sacar un hábito del plan SIN RED, con la hora real. Mismas reglas que con red y la regla de
 * choques D13: si el hábito cambió en el servidor después de lo que hiciste, gana el servidor. Un hábito que ya no está en el plan es
 * {@code RECURSO_YA_NO_EXISTE}; sacar lo que ya no está se aplica sin más.
 */
public interface DesbloqueosSinRedUseCase {

    DesbloqueoHabito elegir(ElegirHabitoCommand command, HechoSinRed sinRed);

    DesbloqueoHabito cambiarEstado(CambiarEstadoHabitoCommand command, HechoSinRed sinRed);

    void quitar(QuitarHabitoCommand command, HechoSinRed sinRed);
}
