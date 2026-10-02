package com.renaser.os.evidence.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lo que {@code evidence} borra de una persona cuando su cuenta se borra para siempre (D-243): sus
 * evidencias y sus archivos. Va antes que {@code rocks} y {@code habits} porque {@code evidencias} apunta
 * a {@code registros_habito}, {@code rocas_diarias} y {@code registros_espiritu}.
 *
 * <p>La clave la arma el servidor con el id del dueño y {@code EvidenciaRegistroService.exigirClavePropia}
 * rechaza una ajena: ninguna fila que sobreviva la nombra. Si una evidencia se publicó en el Muro, la
 * publicación guarda su propia clave {@code muro/...} y la responde {@code community}.
 */
@Component
@Order(110)
class BorradoDeCuentaEnEvidenceAdapter implements BorradoDeDatosDeCuenta {

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnEvidenceAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> archivosDe(UserId cuenta) {
        return new HashSet<>(jdbc.queryForList("SELECT ruta_storage FROM renaser.evidencias "
                + "WHERE participante_id = :id AND ruta_storage IS NOT NULL", Map.of("id", cuenta.value()), String.class));
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        jdbc.update("DELETE FROM renaser.evidencias WHERE participante_id = :id", Map.of("id", cuenta.value()));
    }
}
