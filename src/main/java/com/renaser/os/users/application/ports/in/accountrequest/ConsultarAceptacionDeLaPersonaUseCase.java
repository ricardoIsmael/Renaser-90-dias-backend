package com.renaser.os.users.application.ports.in.accountrequest;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.domain.model.accountrequest.EleccionDeIngreso;

import java.time.Instant;
import java.util.Optional;

/**
 * Lo que se decidió al aceptar a una persona, para su ficha en Administración (spec 025, US4 escenario 1): la misma foto que dice la lista de «Aceptadas»
 * —formación, duración, rol, con o sin diagnóstico—, quién la aceptó y cuándo. Solo ADMIN y ALCHEMIST.
 */
public interface ConsultarAceptacionDeLaPersonaUseCase {

    /** Vacío si la cuenta no entró por una solicitud (la invitó Administración o es anterior al panel) o si su solicitud aún no se acepta. */
    Optional<AceptacionDeLaPersona> de(UserId actor, UserId persona);

    /** @param aceptadaPorNombre quién la aceptó; {@code null} si esa cuenta ya no existe */
    record AceptacionDeLaPersona(EleccionDeIngreso eleccion, String aceptadaPorNombre, Instant aceptadaEn) {
    }
}
