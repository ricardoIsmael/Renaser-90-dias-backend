package com.renaser.os.academy.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * Lo que {@code academy} borra o anonimiza de una persona cuando su cuenta se borra para siempre (D-243):
 * sus cursos asignados, su progreso, su pertenencia a grupos de cursos y sus recomendaciones. Donde figura
 * como quien asignó un curso a OTRA persona ({@code asignaciones_curso.asignada_por}), la asignación es de
 * la otra persona y sobrevive sin autor. El catálogo (cursos, lecciones, recursos) no es de nadie y no se
 * toca: tampoco hay archivos por persona en este módulo.
 */
@Component
@Order(70)
class BorradoDeCuentaEnAcademyAdapter implements BorradoDeDatosDeCuenta {

    private static final List<String> BORRADOS = List.of(
            "DELETE FROM renaser.asignaciones_curso WHERE usuario_id = :id",
            "UPDATE renaser.asignaciones_curso SET asignada_por = NULL WHERE asignada_por = :id",
            "DELETE FROM renaser.progreso_lecciones WHERE usuario_id = :id",
            "DELETE FROM renaser.miembros_grupo WHERE usuario_id = :id",
            "DELETE FROM renaser.recomendaciones_academia WHERE participante_id = :id");

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnAcademyAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        BORRADOS.forEach(sql -> jdbc.update(sql, Map.of("id", cuenta.value())));
    }
}
