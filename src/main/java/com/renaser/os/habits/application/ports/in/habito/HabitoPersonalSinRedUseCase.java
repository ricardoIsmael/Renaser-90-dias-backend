package com.renaser.os.habits.application.ports.in.habito;

import com.renaser.os.habits.application.ports.in.habito.CrearHabitoPersonalUseCase.CrearHabitoPersonalCommand;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.shared.domain.HechoSinRed;

/**
 * Spec 048 (FR-019, fase 3): crear un hábito personal SIN RED. Es un alta: no choca con nada y es idempotente por {@code idCliente} (un
 * reintento devuelve el mismo hábito, sin duplicarlo).
 */
public interface HabitoPersonalSinRedUseCase {

    Habito crear(CrearHabitoPersonalCommand command, HechoSinRed sinRed);
}
