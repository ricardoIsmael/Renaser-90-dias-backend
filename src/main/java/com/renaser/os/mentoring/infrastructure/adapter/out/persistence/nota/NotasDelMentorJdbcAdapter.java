package com.renaser.os.mentoring.infrastructure.adapter.out.persistence.nota;

import com.renaser.os.mentoring.application.ports.out.GuardarNotaDelMentorPort;
import com.renaser.os.mentoring.application.ports.out.LeerNotasDelMentorPort;
import com.renaser.os.mentoring.domain.model.nota.NotaDelMentor;
import com.renaser.os.mentoring.domain.model.nota.VersionDeLaNota;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * {@code notas_del_mentor} y {@code notas_del_mentor_versiones} (V198) con {@link JdbcClient}. No hay DELETE desde el código: una
 * corrección actualiza el texto de la nota y deja el anterior en las versiones, las dos escrituras en la transacción de quien llama.
 * Mapper a mano entre el dominio y las filas: el agregado no lleva anotaciones de persistencia.
 */
@Component
class NotasDelMentorJdbcAdapter implements GuardarNotaDelMentorPort, LeerNotasDelMentorPort {

    private static final String COLUMNAS = "id, aprendiz_id, autor_id, texto, dia_del_autor, creado_en, corregido_en";

    private final JdbcClient jdbc;

    NotasDelMentorJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertar(NotaDelMentor nota) {
        jdbc.sql("""
                        INSERT INTO renaser.notas_del_mentor (id, aprendiz_id, autor_id, texto, dia_del_autor, creado_en)
                        VALUES (:id, :aprendiz, :autor, :texto, :dia, :creadoEn)
                        """)
                .param("id", nota.id()).param("aprendiz", nota.aprendizId().value())
                .param("autor", nota.autorId().value()).param("texto", nota.texto())
                .param("dia", nota.diaDelAutor()).param("creadoEn", Timestamp.from(nota.creadoEn()))
                .update();
    }

    @Override
    public void corregir(NotaDelMentor nota, VersionDeLaNota anterior) {
        jdbc.sql("""
                        INSERT INTO renaser.notas_del_mentor_versiones (nota_id, texto_anterior, reemplazado_en)
                        VALUES (:nota, :anterior, :cuando)
                        """)
                .param("nota", anterior.notaId()).param("anterior", anterior.textoAnterior())
                .param("cuando", Timestamp.from(anterior.reemplazadoEn()))
                .update();
        jdbc.sql("UPDATE renaser.notas_del_mentor SET texto = :texto, corregido_en = :cuando WHERE id = :id")
                .param("texto", nota.texto()).param("cuando", Timestamp.from(nota.corregidoEn()))
                .param("id", nota.id())
                .update();
    }

    @Override
    public List<NotaDelMentor> delAprendiz(UserId aprendiz) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM renaser.notas_del_mentor WHERE aprendiz_id = :aprendiz "
                        + "ORDER BY creado_en DESC, id")
                .param("aprendiz", aprendiz.value())
                .query(NotasDelMentorJdbcAdapter::aNota).list();
    }

    @Override
    public Optional<NotaDelMentor> porId(UUID id) {
        return jdbc.sql("SELECT " + COLUMNAS + " FROM renaser.notas_del_mentor WHERE id = :id")
                .param("id", id).query(NotasDelMentorJdbcAdapter::aNota).optional();
    }

    private static NotaDelMentor aNota(ResultSet rs, int fila) throws SQLException {
        Timestamp corregida = rs.getTimestamp("corregido_en");
        return NotaDelMentor.rehidratar(rs.getObject("id", UUID.class), UserId.of(rs.getObject("aprendiz_id", UUID.class)),
                UserId.of(rs.getObject("autor_id", UUID.class)), rs.getString("texto"),
                rs.getObject("dia_del_autor", LocalDate.class), rs.getTimestamp("creado_en").toInstant(),
                corregida == null ? null : corregida.toInstant());
    }
}
