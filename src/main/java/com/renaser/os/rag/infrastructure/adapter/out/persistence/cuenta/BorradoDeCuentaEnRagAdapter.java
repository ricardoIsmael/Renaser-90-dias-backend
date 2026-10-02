package com.renaser.os.rag.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Lo que {@code rag} borra de una persona cuando su cuenta se borra para siempre (D-243): su conversación
 * con RenasIA (mensajes y sus fuentes), lo que RenasIA recuerda de ella (memoria compactada y recuerdos),
 * su agenda ocupada, sus informes del Espejo de la Sombra con sus preguntas, y las propuestas del
 * acompañante. Todo es de ella sola. La base de conocimiento es catálogo y no se toca; no hay archivos.
 */
@Component
@Order(80)
class BorradoDeCuentaEnRagAdapter implements BorradoDeDatosDeCuenta {

    /** Hijos antes que padres. */
    private static final List<String> BORRADOS = List.of(
            "DELETE FROM renaser.fuentes_mensaje_renasia WHERE mensaje_id IN "
                    + "(SELECT id FROM renaser.mensajes_renasia WHERE usuario_id = :id)",
            "DELETE FROM renaser.mensajes_renasia WHERE usuario_id = :id",
            "DELETE FROM renaser.conversaciones_renasia WHERE usuario_id = :id",
            "DELETE FROM renaser.memorias_renasia WHERE participante_id = :id",
            "DELETE FROM renaser.recuerdos_renasia WHERE participante_id = :id",
            "DELETE FROM renaser.agenda_ocupada WHERE participante_id = :id",
            "DELETE FROM renaser.preguntas_confrontacion WHERE informe_id IN "
                    + "(SELECT id FROM renaser.informes_espejo_sombra WHERE participante_id = :id)",
            "DELETE FROM renaser.informes_espejo_sombra WHERE participante_id = :id",
            "DELETE FROM renaser.propuestas_acompanante WHERE participante_id = :id");

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnRagAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        BORRADOS.forEach(sql -> jdbc.update(sql, Map.of("id", cuenta.value())));
    }
}
