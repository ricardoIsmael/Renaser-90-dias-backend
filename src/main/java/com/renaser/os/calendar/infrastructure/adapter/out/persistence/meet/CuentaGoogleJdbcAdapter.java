package com.renaser.os.calendar.infrastructure.adapter.out.persistence.meet;

import com.renaser.os.calendar.application.ports.out.meet.CifradoDeSecretosPort;
import com.renaser.os.calendar.application.ports.out.meet.LoadCuentaGooglePort;
import com.renaser.os.calendar.application.ports.out.meet.SaveCuentaGooglePort;
import com.renaser.os.calendar.domain.model.meet.CuentaDeGoogle;
import com.renaser.os.calendar.domain.model.meet.EstadoDeLaCuenta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * {@code integracion_google} (V169, spec 040): la única cuenta de Google de la plataforma (fila {@code id = 1}). El permiso permanente se guarda
 * CIFRADO (AES-256-GCM, {@link CifradoDeSecretosPort}) y solo se descifra al leer la cuenta para pedir un token de acceso: nunca viaja en
 * claro a la base ni a un registro.
 */
@Component
class CuentaGoogleJdbcAdapter implements LoadCuentaGooglePort, SaveCuentaGooglePort {

    private final JdbcClient jdbcClient;
    private final CifradoDeSecretosPort cifrado;

    CuentaGoogleJdbcAdapter(JdbcClient jdbcClient, CifradoDeSecretosPort cifrado) {
        this.jdbcClient = jdbcClient;
        this.cifrado = cifrado;
    }

    @Override
    public Optional<CuentaDeGoogle> cargar() {
        return jdbcClient.sql("SELECT correo, refresh_token_cifrado, permisos, calendario_id, estado, conectada_por, conectada_en, "
                        + "ultimo_uso_en, ultimo_error, ultimo_aviso_en, version FROM renaser.integracion_google WHERE id = 1")
                .query(this::aCuenta)
                .optional();
    }

    @Override
    public CuentaDeGoogle guardar(CuentaDeGoogle cuenta) {
        jdbcClient.sql("""
                        INSERT INTO renaser.integracion_google (id, correo, refresh_token_cifrado, version_de_clave, permisos, calendario_id,
                            estado, conectada_por, conectada_en, ultimo_uso_en, ultimo_error, ultimo_aviso_en, version)
                        VALUES (1, :correo, :token, :clave, :permisos, :calendario, :estado, :por, :en, :uso, :error, :aviso, 0)
                        ON CONFLICT (id) DO UPDATE SET correo = EXCLUDED.correo, refresh_token_cifrado = EXCLUDED.refresh_token_cifrado,
                            version_de_clave = EXCLUDED.version_de_clave, permisos = EXCLUDED.permisos,
                            calendario_id = EXCLUDED.calendario_id, estado = EXCLUDED.estado, conectada_por = EXCLUDED.conectada_por,
                            conectada_en = EXCLUDED.conectada_en, ultimo_uso_en = EXCLUDED.ultimo_uso_en,
                            ultimo_error = EXCLUDED.ultimo_error, ultimo_aviso_en = EXCLUDED.ultimo_aviso_en,
                            version = integracion_google.version + 1
                        """)
                .param("correo", cuenta.correo())
                .param("token", cifrado.cifrar(cuenta.refreshToken()))
                .param("clave", cifrado.versionDeLaClave())
                .param("permisos", String.join(" ", cuenta.permisos()))
                .param("calendario", cuenta.calendarioId())
                .param("estado", cuenta.estado().name())
                .param("por", cuenta.conectadaPor() == null ? null : cuenta.conectadaPor().value())
                .param("en", utc(cuenta.conectadaEn()))
                .param("uso", utc(cuenta.ultimoUsoEn()))
                .param("error", cuenta.ultimoError())
                .param("aviso", utc(cuenta.ultimoAvisoEn()))
                .update();
        return cuenta;
    }

    @Override
    public void borrar() {
        jdbcClient.sql("DELETE FROM renaser.integracion_google WHERE id = 1").update();
    }

    private CuentaDeGoogle aCuenta(ResultSet rs, int fila) throws SQLException {
        UUID por = rs.getObject("conectada_por", UUID.class);
        Set<String> permisos = new LinkedHashSet<>(Arrays.asList(rs.getString("permisos").split(" ")));
        return CuentaDeGoogle.rehydrate(rs.getString("correo"), cifrado.descifrar(rs.getString("refresh_token_cifrado")), permisos,
                rs.getString("calendario_id"), EstadoDeLaCuenta.valueOf(rs.getString("estado")), por == null ? null : UserId.of(por),
                instante(rs, "conectada_en"), instante(rs, "ultimo_uso_en"), rs.getString("ultimo_error"),
                instante(rs, "ultimo_aviso_en"), rs.getLong("version"));
    }

    private static Instant instante(ResultSet rs, String columna) throws SQLException {
        OffsetDateTime valor = rs.getObject(columna, OffsetDateTime.class);
        return valor == null ? null : valor.toInstant();
    }

    private static OffsetDateTime utc(Instant instante) {
        return instante == null ? null : instante.atOffset(ZoneOffset.UTC);
    }
}
