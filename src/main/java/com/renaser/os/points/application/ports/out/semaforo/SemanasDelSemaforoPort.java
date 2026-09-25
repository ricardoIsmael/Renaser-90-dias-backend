package com.renaser.os.points.application.ports.out.semaforo;

import com.renaser.os.points.domain.model.semaforo.FotoSemanal;
import com.renaser.os.shared.domain.UserId;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/** Las fotos de los cierres semanales ({@code semaforo_semanas}): append-only. */
public interface SemanasDelSemaforoPort {

    /** El viernes de la última semana cerrada de cada persona; sin clave = ninguna todavía. */
    Map<UserId, LocalDate> ultimaCerradaDe(Collection<UserId> participantes);

    /** La foto de esa semana de cada persona; sin clave = esa semana no está cerrada para ella. */
    Map<UserId, FotoSemanal> deLaSemana(Collection<UserId> participantes, LocalDate semanaHasta);

    /** Las últimas {@code cantidad} fotos de una persona, de la más vieja a la más nueva. */
    List<FotoSemanal> ultimasDe(UserId participante, int cantidad);

    /**
     * Guarda la foto si esa semana todavía no tenía una. Nunca reescribe.
     *
     * @return true si la guardó; false si ya existía (otra corrida la cerró antes)
     */
    boolean registrar(UserId participante, FotoSemanal foto);
}
