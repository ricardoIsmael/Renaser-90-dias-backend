package com.renaser.os.leadership.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lo que {@code leadership} borra de una persona cuando su cuenta se borra para siempre (D-243): las
 * observaciones del Líder de Mentores SOBRE ella ({@code mentor_id}), las que ella ESCRIBIÓ ({@code autor_id}) y,
 * desde la spec 005 (V109), las que tratan DE ella ({@code persona_id}): su texto la nombra aunque el destinatario sea
 * su mentor. {@code consultas_de_ficha} no se toca: es un registro de auditoría de solo ids, sin FK y de solo agregar.
 *
 * <p><b>Por qué se borran las que escribió y no solo pierden el autor.</b> Decisión de D-243: son texto
 * suyo, y {@code autor_id} es {@code NOT NULL} con FK {@code ON DELETE RESTRICT} (V89) que la app del Líder
 * lee como obligatorio. Volverlo nullable para conservarlas cambiaba el contrato de esa pantalla; borrarlas
 * acá, antes del DELETE de {@code usuarios}, hace que la FK nunca se dispare (V90 lo explica).
 *
 * <p><b>Los documentos semanales (V199, spec 056).</b> Se guardan para siempre, pero no más allá de la persona de quien hablan: el documento de
 * un aprendiz o de un mentor lo nombra y trae lo que escribió, así que se va con su cuenta, igual que el archivo guardado en el almacenamiento
 * ({@link #archivosDe}: {@code users} lo borra del bucket). Se van también las descargas de esos documentos y las que la persona hizo de los de otros;
 * el documento de otra persona que ella generó se queda: su {@code generado_por} es un id sin clave foránea, como en {@code consultas_de_ficha}.
 * En el reporte del mes la «persona» es quien lo pidió, así que también se va con ella.
 */
@Component
@Order(60)
class BorradoDeCuentaEnLeadershipAdapter implements BorradoDeDatosDeCuenta {

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnLeadershipAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Las rutas de los documentos de la persona: cada una lleva su id, así que ninguna fila de otra persona las sigue usando. */
    @Override
    public Set<String> archivosDe(UserId cuenta) {
        return new HashSet<>(jdbc.queryForList("SELECT ruta FROM renaser.documentos_semanales WHERE persona_id = :id",
                Map.of("id", cuenta.value()), String.class));
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        Map<String, Object> id = Map.of("id", cuenta.value());
        jdbc.update("DELETE FROM renaser.observaciones_mentor WHERE mentor_id = :id OR autor_id = :id OR persona_id = :id", id);
        jdbc.update("DELETE FROM renaser.descargas_de_documentos WHERE actor_id = :id OR documento_id IN "
                + "(SELECT id FROM renaser.documentos_semanales WHERE persona_id = :id)", id);
        jdbc.update("DELETE FROM renaser.documentos_semanales WHERE persona_id = :id", id);
    }
}
