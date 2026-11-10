package com.renaser.os.habits.application.services;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarSinRedUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase;
import com.renaser.os.habits.application.ports.in.radar.ConfigurarMiRadarUseCase.ConfigurarMiRadarCommand;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarSinRedUseCase;
import com.renaser.os.habits.application.ports.in.radar.RegistrarCheckInRadarUseCase.RegistrarCheckInRadarCommand;
import com.renaser.os.shared.domain.CodigoNoAplicado;
import com.renaser.os.shared.domain.HechoSinRed;
import com.renaser.os.shared.domain.NoAplicadoSinRedException;
import com.renaser.os.shared.domain.OrigenDeLaHora;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * El Código Renaser y Mi Radar hechos sin red contra Postgres de verdad (spec 048, fase 3, FR-019, V191). La persona responde el
 * lunes 9 a las 11:30 de Lima (16:30 UTC) y la app lo envía el martes 10 a las 08:00 de Lima. Fija lo que ningún doble puede: la hora
 * real en {@code creado_en}, que el índice único por hora no se viole nunca y que {@code mi_radar_actualizado_en} se mueva (R1).
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("Código Renaser y Mi Radar sin red: lo que se guarda de verdad (spec 048, fase 3)")
class CodigoRenaserSinRedIT {

    private static final Instant HECHO = Instant.parse("2026-11-09T16:30:00Z");
    private static final Instant LLEGA = Instant.parse("2026-11-10T13:00:00Z");

    @Autowired private RegistrarCheckInRadarSinRedUseCase registrarSinRed;
    @Autowired private ConfigurarMiRadarSinRedUseCase configurarSinRed;
    @Autowired private ConfigurarMiRadarUseCase configurar;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RelojDePrueba reloj;

    private UserId persona;

    @BeforeEach
    void sembrar() {
        reloj.fijar(LLEGA);
        persona = UserId.of(UUID.randomUUID());
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) VALUES (?, ?, 'Prueba', 'APRENDIZ', 'ACTIVO')",
                persona.value(), persona + "@prueba.test");
    }

    @AfterEach
    void limpiar() {
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", persona.value());
    }

    private void participanteEnElDia(int dia) {
        // El día del programa se deriva de las fechas (regla 02): la llegada es el 10 de noviembre y ese es el día «dia».
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, programa_activado_en, timezone) "
                + "VALUES (?, ?, ?, now(), 'America/Lima')", persona.value(), java.time.LocalDate.of(2026, 11, 10).minusDays(dia - 1L), dia);
    }

    private static HechoSinRed envio(UUID id, Instant hechoEn) {
        return new HechoSinRed(hechoEn, id, OrigenDeLaHora.RELOJ_DEL_SISTEMA);
    }

    private RegistrarCheckInRadarCommand comando() {
        return new RegistrarCheckInRadarCommand(persona, persona, "escribiendo", "pensando", "tranquilo", 6, "nada", false);
    }

    private int registros() {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.registros_radar WHERE participante_id = ?", Integer.class, persona.value());
    }

    @Test
    @DisplayName("días 1 a 7: el registro queda con la hora real (creado_en) y la hora local prevista de ese momento, no la de llegada")
    void seGuardaConLaHoraReal() { // spec 048 FR-019
        participanteEnElDia(5);
        UUID envio = UUID.randomUUID();

        var registro = registrarSinRed.registrar(comando(), envio(envio, HECHO));

        assertThat(registro.creadoEn()).isEqualTo(HECHO);
        assertThat(registro.horaPrevista()).isEqualTo(11);
        assertThat(registros()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT creado_en FROM renaser.registros_radar WHERE participante_id = ?", Timestamp.class,
                persona.value()).toInstant()).isEqualTo(HECHO);
        assertThat(jdbc.queryForObject("SELECT hora_prevista FROM renaser.registros_radar WHERE participante_id = ?", Short.class,
                persona.value())).isEqualTo((short) 11);
        assertThat(jdbc.queryForMap("SELECT tipo, resultado FROM renaser.envios_sin_red WHERE id_cliente = ?", envio))
                .containsEntry("tipo", "CODIGO_RENASER").containsEntry("resultado", "APLICADO");
    }

    @Test
    @DisplayName("P4: una hora que el servidor ya tiene respondida se queda con la del servidor (YA_ESTABA_HECHO) y nunca hay dos por hora")
    void nuncaDosPorHora() { // spec 048 FR-019
        participanteEnElDia(5);
        jdbc.update("INSERT INTO renaser.registros_radar (id, participante_id, que_hago, que_pienso, que_siento, nivel_energia, que_evito, creado_en) "
                        + "VALUES (gen_random_uuid(), ?, 'del servidor', 'x', 'y', 5, 'z', ?)", persona.value(),
                Timestamp.from(HECHO.minusSeconds(900)));

        assertThatThrownBy(() -> registrarSinRed.registrar(comando(), envio(UUID.randomUUID(), HECHO)))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.YA_ESTABA_HECHO));

        assertThat(registros()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT que_hago FROM renaser.registros_radar WHERE participante_id = ?", String.class,
                persona.value())).isEqualTo("del servidor");
    }

    @Test
    @DisplayName("el mismo envío dos veces guarda un solo registro y los dos responden lo mismo")
    void repetirElEnvio() { // spec 048 FR-008 FR-019
        participanteEnElDia(5);
        UUID envio = UUID.randomUUID();

        var primero = registrarSinRed.registrar(comando(), envio(envio, HECHO));
        var segundo = registrarSinRed.registrar(comando(), envio(envio, HECHO));

        assertThat(segundo.id()).isEqualTo(primero.id());
        assertThat(registros()).isEqualTo(1);
    }

    @Test
    @DisplayName("desde el día 8 sin Mi Radar encendido no se aplica (MI_RADAR_NO_APLICA); si Mi Radar cambió después de lo que hiciste, gana el servidor")
    void desdeElOchoSinMiRadar() { // spec 048 FR-019
        participanteEnElDia(9);

        assertThatThrownBy(() -> registrarSinRed.registrar(comando(), envio(UUID.randomUUID(), HECHO)))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.MI_RADAR_NO_APLICA));

        jdbc.update("UPDATE renaser.participantes_programa SET mi_radar_actualizado_en = ? WHERE usuario_id = ?",
                Timestamp.from(HECHO.plusSeconds(60)), persona.value());
        assertThatThrownBy(() -> registrarSinRed.registrar(comando(), envio(UUID.randomUUID(), HECHO)))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));
        assertThat(registros()).isZero();
    }

    @Test
    @DisplayName("Mi Radar sin red deja su hora lógica en la hora real; uno más viejo que ella ya no se aplica (D13)")
    void miRadarSinRed() { // spec 048 FR-019
        participanteEnElDia(9);
        var cambio = new ConfigurarMiRadarCommand(persona, persona, true, List.of());

        configurarSinRed.configurar(cambio, envio(UUID.randomUUID(), HECHO));

        assertThat(jdbc.queryForObject("SELECT mi_radar_encendido FROM renaser.participantes_programa WHERE usuario_id = ?", Boolean.class,
                persona.value())).isTrue();
        assertThat(jdbc.queryForObject("SELECT mi_radar_actualizado_en FROM renaser.participantes_programa WHERE usuario_id = ?",
                Timestamp.class, persona.value()).toInstant()).isEqualTo(HECHO);
        UUID masViejo = UUID.randomUUID();
        assertThatThrownBy(() -> configurarSinRed.configurar(new ConfigurarMiRadarCommand(persona, persona, false, List.of()),
                envio(masViejo, HECHO.minusSeconds(3600)))).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.CAMBIO_MAS_NUEVO_EN_EL_SERVIDOR));
        assertThat(jdbc.queryForObject("SELECT mi_radar_encendido FROM renaser.participantes_programa WHERE usuario_id = ?", Boolean.class,
                persona.value())).isTrue();
    }

    @Test
    @DisplayName("R1: guardar Mi Radar CON red también mueve mi_radar_actualizado_en (a la hora del servidor)")
    void miRadarConRedMueveLaHoraLogica() { // spec 048 FR-019
        participanteEnElDia(9);

        configurar.configurar(new ConfigurarMiRadarCommand(persona, persona, true, List.of()));

        assertThat(jdbc.queryForObject("SELECT mi_radar_actualizado_en FROM renaser.participantes_programa WHERE usuario_id = ?",
                Timestamp.class, persona.value()).toInstant()).isEqualTo(LLEGA);
    }
}
