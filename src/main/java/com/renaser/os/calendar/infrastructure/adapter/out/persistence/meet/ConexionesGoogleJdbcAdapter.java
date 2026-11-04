package com.renaser.os.calendar.infrastructure.adapter.out.persistence.meet;

import com.renaser.os.calendar.application.ports.out.meet.CifradoDeSecretosPort;
import com.renaser.os.calendar.application.ports.out.meet.ConexionesEnCursoPort;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code integracion_google_conexiones} (V169, spec 040, FR-010): el {@code state} de OAuth es de UN solo uso. Consumirlo es un
 * {@code DELETE ... RETURNING}: dos regresos con el mismo {@code state} no pueden usarlo los dos. Se guarda el hash del {@code state} y el
 * verificador de PKCE cifrado.
 */
@Component
class ConexionesGoogleJdbcAdapter implements ConexionesEnCursoPort {

    private final JdbcClient jdbcClient;
    private final CifradoDeSecretosPort cifrado;

    ConexionesGoogleJdbcAdapter(JdbcClient jdbcClient, CifradoDeSecretosPort cifrado) {
        this.jdbcClient = jdbcClient;
        this.cifrado = cifrado;
    }

    @Override
    @Transactional
    public void guardar(String hashDelState, UserId actor, String codeVerifier, Instant venceEn) {
        // De paso se limpian las que vencieron hace más de un día y nadie volvió a usar.
        jdbcClient.sql("DELETE FROM renaser.integracion_google_conexiones WHERE vence_en < :limite")
                .param("limite", venceEn.minus(Duration.ofDays(1)).atOffset(ZoneOffset.UTC))
                .update();
        jdbcClient.sql("INSERT INTO renaser.integracion_google_conexiones (hash_del_state, actor_id, code_verifier_cifrado, vence_en) "
                        + "VALUES (:hash, :actor, :verificador, :vence)")
                .param("hash", hashDelState)
                .param("actor", actor.value())
                .param("verificador", cifrado.cifrar(codeVerifier))
                .param("vence", venceEn.atOffset(ZoneOffset.UTC))
                .update();
    }

    @Override
    @Transactional
    public Optional<ConexionEnCurso> consumir(String hashDelState, Instant ahora) {
        return jdbcClient.sql("DELETE FROM renaser.integracion_google_conexiones WHERE hash_del_state = :hash "
                        + "RETURNING actor_id, code_verifier_cifrado, vence_en")
                .param("hash", hashDelState)
                .query((rs, n) -> new Fila(rs.getObject("actor_id", UUID.class), rs.getString("code_verifier_cifrado"),
                        rs.getObject("vence_en", OffsetDateTime.class).toInstant()))
                .optional()
                .filter(fila -> fila.venceEn().isAfter(ahora))
                .map(fila -> new ConexionEnCurso(UserId.of(fila.actor()), cifrado.descifrar(fila.verificadorCifrado())));
    }

    private record Fila(UUID actor, String verificadorCifrado, Instant venceEn) {
    }
}
