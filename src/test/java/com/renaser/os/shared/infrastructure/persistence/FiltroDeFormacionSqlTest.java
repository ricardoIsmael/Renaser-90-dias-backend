package com.renaser.os.shared.infrastructure.persistence;

import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** El filtro por formación de las consultas SQL (spec 022): un solo lugar que arma el predicado y los códigos. */
class FiltroDeFormacionSqlTest {

    @Test
    @DisplayName("El predicado es «columna IN (:parámetro)» con nombre o con posición")
    void predicado() {
        assertThat(FiltroDeFormacionSql.predicado("c.formacion", "formaciones")).isEqualTo("c.formacion IN (:formaciones)");
        assertThat(FiltroDeFormacionSql.predicadoPosicional("pp.formacion", 4)).isEqualTo("pp.formacion IN (?4)");
    }

    @Test
    @DisplayName("Los códigos salen ordenados; un alcance vacío liga un valor que ninguna formación tiene (no trae nada)")
    void codigos() {
        assertThat(FiltroDeFormacionSql.codigos(AlcanceDeFormacion.todas())).containsExactly("CERTIFICACION", "DIPLOMADO", "ESCUELA");
        assertThat(FiltroDeFormacionSql.codigos(AlcanceDeFormacion.de(Formacion.DIPLOMADO))).containsExactly("DIPLOMADO");
        assertThat(FiltroDeFormacionSql.codigos(AlcanceDeFormacion.ninguna())).containsExactly(FiltroDeFormacionSql.NINGUNA);
        for (Formacion formacion : Formacion.values()) {
            assertThat(formacion.codigo()).isNotEqualTo(FiltroDeFormacionSql.NINGUNA);
        }
    }
}
