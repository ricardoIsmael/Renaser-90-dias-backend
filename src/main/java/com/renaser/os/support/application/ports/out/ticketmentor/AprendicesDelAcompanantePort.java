package com.renaser.os.support.application.ports.out.ticketmentor;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Set;

/**
 * A quiénes acompaña hoy un mentor o una guía: de ellos son los tickets de mentoría que puede ver. La respuesta es la de `community`
 * (la misma que decide qué grupo puede abrir y a qué aprendices lista), no una regla de este módulo.
 */
public interface AprendicesDelAcompanantePort {

    /** Vacío si no acompaña a nadie en ese instante. */
    Set<UserId> aprendicesQueAcompana(UserId acompanante, Instant instante);
}
