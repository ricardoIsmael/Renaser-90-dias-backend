package com.renaser.os.users.infrastructure.adapter.out.persistence.user;

import com.renaser.os.users.application.ports.out.user.BloquearCuentasDePruebaPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Candado de transacción ({@code pg_advisory_xact_lock}) para las cuentas de prueba de «ver como» (D-273), como el de
 * los roles personalizados ({@code RolesPersonalizadosJdbcAdapter.CANDADO_DE_ESCRITURAS}) con otra clave.
 */
@Component
class CandadoDeCuentasDePruebaJdbcAdapter implements BloquearCuentasDePruebaPort {

    static final long CANDADO_DE_CUENTAS_DE_PRUEBA = 2_700_107L;

    private final JdbcClient jdbc;

    CandadoDeCuentasDePruebaJdbcAdapter(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void bloquear() {
        // La función devuelve void: se envuelve para leer un entero y no una columna sin tipo.
        jdbc.sql("SELECT 1 FROM (SELECT pg_advisory_xact_lock(:clave)) AS candado")
                .param("clave", CANDADO_DE_CUENTAS_DE_PRUEBA).query(Integer.class).single();
    }
}
