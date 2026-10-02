package com.renaser.os.phasecontracts.infrastructure.adapter.out.persistence.cuenta;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.BorradoDeDatosDeCuenta;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Lo que {@code phasecontracts} borra de una persona cuando su cuenta se borra para siempre (D-243): sus
 * Pactos firmados y la imagen de cada firma. La clave de la firma es determinística con el id del dueño
 * ({@code ContratoFase.rutaFirma}) y el cliente ni la manda, así que ninguna fila ajena la nombra.
 */
@Component
@Order(100)
class BorradoDeCuentaEnPhasecontractsAdapter implements BorradoDeDatosDeCuenta {

    private final NamedParameterJdbcTemplate jdbc;

    BorradoDeCuentaEnPhasecontractsAdapter(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Set<String> archivosDe(UserId cuenta) {
        return new HashSet<>(jdbc.queryForList("SELECT ruta_firma FROM renaser.contratos_fase "
                + "WHERE participante_id = :id AND ruta_firma IS NOT NULL", Map.of("id", cuenta.value()), String.class));
    }

    @Override
    public void borrarDatosDe(UserId cuenta) {
        jdbc.update("DELETE FROM renaser.contratos_fase WHERE participante_id = :id", Map.of("id", cuenta.value()));
    }
}
