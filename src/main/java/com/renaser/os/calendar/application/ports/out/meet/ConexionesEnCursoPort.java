package com.renaser.os.calendar.application.ports.out.meet;

import com.renaser.os.shared.domain.UserId;

import java.time.Instant;
import java.util.Optional;

/**
 * Las conexiones con Google que alguien empezó y todavía no volvieron (spec 040, FR-010): el {@code state} es de UN solo uso, dura 15 minutos y
 * ata la respuesta a quien inició la conexión. Se guarda el hash del {@code state}, nunca el {@code state}.
 */
public interface ConexionesEnCursoPort {

    record ConexionEnCurso(UserId actor, String codeVerifier) {
    }

    void guardar(String hashDelState, UserId actor, String codeVerifier, Instant venceEn);

    /** Devuelve y borra la conexión si existe y no venció; una segunda vez con el mismo {@code state} ya no está. */
    Optional<ConexionEnCurso> consumir(String hashDelState, Instant ahora);
}
