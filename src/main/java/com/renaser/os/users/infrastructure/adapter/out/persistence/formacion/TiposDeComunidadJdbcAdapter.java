package com.renaser.os.users.infrastructure.adapter.out.persistence.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.TipoDeComunidad;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.formacion.TiposDeComunidadPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;

/** Los tipos de comunidad prendidos de cada formación (V138): una columna {@code text[]} de la fila de la formación. */
@Component
class TiposDeComunidadJdbcAdapter implements TiposDeComunidadPort {

    private static final String LEER = "SELECT tipos_de_comunidad FROM renaser.formaciones WHERE codigo = :codigo";

    private static final String GUARDAR = """
            UPDATE renaser.formaciones
            SET tipos_de_comunidad = CAST(:tipos AS text[]), actualizado_en = now(), actualizado_por = :actor
            WHERE codigo = :codigo
            """;

    private final JdbcClient jdbc;

    TiposDeComunidadJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<TipoDeComunidad> prendidosDe(Formacion formacion) {
        return jdbc.sql(LEER).param("codigo", formacion.codigo())
                .query((rs, n) -> aTipos(rs.getArray("tipos_de_comunidad")))
                .optional()
                .orElseThrow(() -> new NoSuchElementException("Falta la formación " + formacion + " en el catálogo (V125)."));
    }

    @Override
    public void guardar(Formacion formacion, Set<TipoDeComunidad> prendidos, UserId actor) {
        String arreglo = "{" + prendidos.stream().map(TipoDeComunidad::codigo).sorted().collect(Collectors.joining(",")) + "}";
        jdbc.sql(GUARDAR).param("tipos", arreglo).param("actor", actor.value()).param("codigo", formacion.codigo()).update();
    }

    private static Set<TipoDeComunidad> aTipos(Array arreglo) {
        try {
            String[] valores = (String[]) arreglo.getArray();
            Set<TipoDeComunidad> tipos = EnumSet.noneOf(TipoDeComunidad.class);
            Arrays.stream(valores).map(TipoDeComunidad::desde).forEach(tipos::add);
            return tipos;
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer los tipos de comunidad de la formación.", e);
        }
    }
}
