package com.renaser.os.mentoring.application.ports.out;

import com.renaser.os.mentoring.domain.model.huecos.FotoDeHuecos;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Optional;
import java.util.Set;

/**
 * Las fotos semanales de los huecos de horario (spec 015, V120). APPEND-ONLY: se registra una vez y nunca se
 * actualiza ni se borra desde el código (constitución IV); la cuenta se lleva sus fotos al borrarse.
 */
public interface FotosDeHuecosPort {

    Optional<FotoDeHuecos> deLaSemana(UserId participante, LocalDate lunes);

    /** De estas personas, las que ya tienen la foto de la semana que empieza en {@code lunes}. En UNA consulta. */
    Set<UserId> conFotoDe(Collection<UserId> participantes, LocalDate lunes);

    /**
     * Guarda la foto si todavía no hay una de esa persona y esa semana.
     *
     * @return {@code true} si la guardó; {@code false} si ya existía (otra corrida o otra instancia ganó): no la pisa
     */
    boolean registrar(FotoDeHuecos foto);
}
