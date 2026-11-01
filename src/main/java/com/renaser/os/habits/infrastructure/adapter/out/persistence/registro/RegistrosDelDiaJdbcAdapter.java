package com.renaser.os.habits.infrastructure.adapter.out.persistence.registro;

import com.renaser.os.habits.api.CumplimientoDelDiaFinder.RegistroDelDia;
import com.renaser.os.habits.api.EstadoObligacion;
import com.renaser.os.habits.application.ports.out.registro.LeerRegistrosDelDiaPort;
import com.renaser.os.habits.domain.model.categoria.CodigoDeCategoria;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.infrastructure.persistence.FiltroDeFormacionSql;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * {@link LeerRegistrosDelDiaPort} con {@link JdbcClient} (spec 029): UNA consulta para todo el padrón, apoyada en
 * {@code registros_dia_idx (participante_id, fecha_ejecucion)}. Solo hábitos del catálogo y solo de las formaciones del alcance
 * (spec 023, {@link FiltroDeFormacionSql}).
 */
@Component
class RegistrosDelDiaJdbcAdapter implements LeerRegistrosDelDiaPort {

    private static final String SQL = """
            SELECT r.id AS registro_id, r.participante_id, r.habito_id, h.titulo, h.categoria_clave, h.formacion,
                   r.estado::text AS estado, r.es_opcional, r.completado_en
            FROM renaser.registros_habito r
            JOIN renaser.habitos h ON h.id = r.habito_id
            WHERE r.participante_id IN (:personas)
              AND r.fecha_ejecucion = :fecha
              AND h.ambito = 'SISTEMA'
              AND\s""" + FiltroDeFormacionSql.predicado("h.formacion", "formaciones");

    private final JdbcClient jdbc;

    RegistrosDelDiaJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<RegistroDelDia> deVariasPersonas(Collection<UserId> personas, LocalDate fecha, AlcanceDeFormacion alcance) {
        List<UUID> ids = personas.stream().map(UserId::value).distinct().toList();
        return jdbc.sql(SQL).param("personas", ids).param("fecha", fecha)
                .param("formaciones", FiltroDeFormacionSql.codigos(alcance))
                .query((rs, fila) -> {
                    Timestamp completado = rs.getTimestamp("completado_en");
                    return new RegistroDelDia(rs.getObject("registro_id", UUID.class),
                            UserId.of(rs.getObject("participante_id", UUID.class)), rs.getObject("habito_id", UUID.class),
                            rs.getString("titulo"), rs.getString("categoria_clave"), CodigoDeCategoria.deClave(rs.getString("categoria_clave")),
                            rs.getString("formacion"),
                            EstadoObligacion.valueOf(rs.getString("estado")), rs.getBoolean("es_opcional"),
                            completado == null ? null : completado.toInstant());
                }).list();
    }
}
