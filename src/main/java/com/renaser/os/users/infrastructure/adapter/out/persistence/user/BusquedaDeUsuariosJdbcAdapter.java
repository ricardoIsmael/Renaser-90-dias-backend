package com.renaser.os.users.infrastructure.adapter.out.persistence.user;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.user.BuscarUsuariosPorTextoPort;
import com.renaser.os.users.domain.model.user.TextoDeBusqueda;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Búsqueda por nombre o correo sin tildes ni mayúsculas (D-249), con {@link JdbcClient}.
 *
 * <p>La columna se normaliza con {@code translate(lower(...))} y no con {@code unaccent}: built-in e
 * IMMUTABLE, sin extensión (mismo criterio que V19). Se traducen también las mayúsculas acentuadas porque
 * {@code lower()} no las baja si la base corre con {@code LC_CTYPE=C}.
 *
 * <p>Sin índice propio, a propósito: {@code LIKE '%texto%'} no usa un B-tree, y uno de trigramas pediría
 * {@code pg_trgm}. Con el padrón de este programa (cientos a pocos miles de cuentas) el recorrido entero
 * de {@code usuarios} mide menos de un milisegundo (EXPLAIN ANALYZE en D-249).
 */
@Component
class BusquedaDeUsuariosJdbcAdapter implements BuscarUsuariosPorTextoPort {

    private static final String CON_TILDE = "áàäâãéèëêíìïîóòöôõúùüûñçÁÀÄÂÃÉÈËÊÍÌÏÎÓÒÖÔÕÚÙÜÛÑÇ";
    private static final String SIN_TILDE = "aaaaaeeeeiiiiooooouuuuncaaaaaeeeeiiiiooooouuuunc";

    static final String CONSULTA = """
            SELECT id FROM renaser.usuarios
            WHERE translate(lower(nombre_completo), '%1$s', '%2$s') LIKE :patron ESCAPE '\\'
               OR translate(lower(email), '%1$s', '%2$s') LIKE :patron ESCAPE '\\'
            """.formatted(CON_TILDE, SIN_TILDE);

    private final JdbcClient jdbcClient;

    BusquedaDeUsuariosJdbcAdapter(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    @Override
    public Set<UserId> coincidenCon(TextoDeBusqueda texto) {
        return jdbcClient.sql(CONSULTA)
                .param("patron", "%" + sinComodines(texto.normalizado()) + "%")
                .query(UUID.class)
                .list().stream().map(UserId::of).collect(Collectors.toUnmodifiableSet());
    }

    /** Un «%» o «_» escrito por la persona se busca tal cual, no como comodín. */
    private static String sinComodines(String texto) {
        return texto.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
