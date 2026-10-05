package com.renaser.os.habits.application.ports.out.registro;

import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.registro.DiaProgramado;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * "Que dias le tocaron a estos habitos de esta persona, y como quedo cada uno" (D-254, la racha de
 * cada habito).
 *
 * <p>Una sola consulta por pagina de fechas y por TODOS los habitos pedidos, nunca una por habito.
 * Quien llama pagina hacia atras desde hoy y solo vuelve a pedir los habitos cuya racha todavia no
 * encontro el dia que la corta ({@code RachasDeHabitos}).
 */
public interface ConsultarDiasProgramadosPort {

    /**
     * @param desde inclusivo, @param hasta inclusivo, en fechas locales del participante.
     * @return por habito, sus dias con registro en el rango, sin orden garantizado. Un habito sin
     *         registros en el rango no aparece; nunca {@code null}.
     */
    Map<HabitoId, List<DiaProgramado>> deHabitosEntre(UserId participanteId, Collection<HabitoId> habitos,
                                                      LocalDate desde, LocalDate hasta);
}
