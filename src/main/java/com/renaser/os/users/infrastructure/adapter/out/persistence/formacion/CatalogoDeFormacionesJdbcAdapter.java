package com.renaser.os.users.infrastructure.adapter.out.persistence.formacion;

import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.FormacionConfigurada;
import com.renaser.os.users.application.ports.out.formacion.CatalogoDeFormacionesPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.Array;
import java.sql.SQLException;
import java.util.Arrays;
import java.util.List;

/** El catálogo de las tres formaciones (V125): tres filas, se leen enteras. */
@Component
class CatalogoDeFormacionesJdbcAdapter implements CatalogoDeFormacionesPort {

    private static final String TODAS = """
            SELECT codigo, nombre, descripcion, activa, duraciones_dias, version
            FROM renaser.formaciones ORDER BY orden
            """;

    private static final String GUARDAR = """
            UPDATE renaser.formaciones
            SET nombre = :nombre, descripcion = :descripcion, activa = :activa, duraciones_dias = CAST(:duraciones AS smallint[]),
                version = version + 1, actualizado_en = now(), actualizado_por = :actor
            WHERE codigo = :codigo AND version = :versionEsperada
            """;

    private final JdbcClient jdbc;

    CatalogoDeFormacionesJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public List<FormacionConfigurada> todas() {
        return jdbc.sql(TODAS).query((rs, n) -> new FormacionConfigurada(Formacion.desde(rs.getString("codigo")),
                rs.getString("nombre"), rs.getString("descripcion"), rs.getBoolean("activa"),
                duraciones(rs.getArray("duraciones_dias")), rs.getInt("version"))).list();
    }

    @Override
    public FormacionConfigurada de(Formacion formacion) {
        return todas().stream().filter(f -> f.formacion() == formacion).findFirst()
                .orElseThrow(() -> new IllegalStateException("Falta la formación " + formacion + " en el catálogo (V125)."));
    }

    @Override
    public boolean guardar(FormacionConfigurada nueva, int versionEsperada, UserId actor) {
        int filas = jdbc.sql(GUARDAR)
                .param("nombre", nueva.nombre())
                .param("descripcion", nueva.descripcion())
                .param("activa", nueva.activa())
                .param("duraciones", "{" + String.join(",", nueva.duracionesDias().stream().map(String::valueOf).toList()) + "}")
                .param("actor", actor.value())
                .param("codigo", nueva.formacion().codigo())
                .param("versionEsperada", versionEsperada)
                .update();
        return filas == 1;
    }

    private static List<Integer> duraciones(Array arreglo) {
        try {
            Number[] valores = (Number[]) arreglo.getArray();
            return Arrays.stream(valores).map(Number::intValue).sorted().toList();
        } catch (SQLException e) {
            throw new IllegalStateException("No se pudo leer las duraciones de la formación.", e);
        }
    }
}
