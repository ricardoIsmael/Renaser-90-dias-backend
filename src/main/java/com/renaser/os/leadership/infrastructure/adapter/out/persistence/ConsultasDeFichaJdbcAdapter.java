package com.renaser.os.leadership.infrastructure.adapter.out.persistence;

import com.renaser.os.leadership.application.ports.out.GuardarConsultaDeFichaPort;
import com.renaser.os.leadership.domain.model.consulta.ConsultaDeFicha;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;

/**
 * {@code consultas_de_ficha} (V109) con {@link JdbcClient}: solo INSERT. La tabla es append-only y la base lo hace
 * cumplir con un trigger, así que ni siquiera existe un método que edite o borre. Sin entidad JPA: es una fila de
 * ids y de un instante, y no hay un agregado que cargar.
 *
 * <p>{@code ON CONFLICT (id) DO NOTHING}: la constancia de una lectura del diagnóstico viaja en un evento que el outbox puede
 * reentregar, y su id sale del evento, así que reentregarlo no duplica la fila. No es un UPDATE: el trigger append-only no se entera.
 */
@Component
class ConsultasDeFichaJdbcAdapter implements GuardarConsultaDeFichaPort {

    private static final String INSERTAR = """
            INSERT INTO renaser.consultas_de_ficha
                (id, consultor_id, rol_consultor, persona_id, grupo_id, mentor_id, semana_desde, consultada_en, tipo)
            VALUES (:id, :consultor, :rol, :persona, :grupo, :mentor, :semana, :consultadaEn, :tipo)
            ON CONFLICT (id) DO NOTHING
            """;

    private final JdbcClient jdbcClient;

    ConsultasDeFichaJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public ConsultaDeFicha insertar(ConsultaDeFicha consulta) {
        jdbcClient.sql(INSERTAR)
                .param("id", consulta.id())
                .param("consultor", consulta.consultorId().value())
                .param("rol", consulta.rolConsultor().name())
                .param("persona", consulta.personaId().value())
                .param("grupo", consulta.grupoId())
                .param("mentor", consulta.mentorId() == null ? null : consulta.mentorId().value())
                .param("semana", consulta.semanaDesde())
                .param("consultadaEn", Timestamp.from(consulta.consultadaEn()))
                .param("tipo", consulta.tipo().name())
                .update();
        return consulta;
    }
}
