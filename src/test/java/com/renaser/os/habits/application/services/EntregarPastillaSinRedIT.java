package com.renaser.os.habits.application.services;

import com.renaser.os.RelojDePrueba;
import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.habits.application.ports.in.espiritu.CompletarPastillaRenacerUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.ConsultarEstadoEspirituUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituSinRedUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituSinRedUseCase.ResultadoEntregaSinRed;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase;
import com.renaser.os.habits.application.ports.in.espiritu.EntregarResumenEspirituUseCase.EntregarResumenEspirituCommand;
import com.renaser.os.habits.application.ports.out.registro.SaveRegistroHabitoPort;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
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
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * La Pastilla Renacer entregada sin red contra Postgres de verdad (spec 048, issue #186, FR-008, FR-010, FR-015). La persona entrega
 * el lunes 9 de noviembre a las 11:00 de Lima (16:00Z, antes del mediodía, el plazo del audio y de su hábito) y la app lo envía a las
 * 13:00 de ese día o al otro día. Fija lo que ningún doble puede: que el audio quede ENTREGADO con su hora real, que el hábito
 * «Pastilla Renacer» se cierre con ella y pague una sola vez, que un reintento sea uno solo (también con pedidos a la vez) y que sin
 * las cabeceras todo siga exactamente como antes.
 *
 * <p>Usa el hábito real del catálogo (V4: la Pastilla va de 07:00 a 12:00 desde el día 8, con la extensión de 3 h de siempre): hecha a las
 * 11:00 paga sus 10 puntos. A las 13:00 el hábito todavía los pagaría con la hora de llegada (está en la extensión), así que lo que
 * distingue la hora real es, ahí, el estado del audio (ENTREGADO y no tardía); al día siguiente el cierre de siempre ya rechazaría el
 * hábito de ayer (D-259) y solo la hora real lo cierra con sus puntos. Sin {@code @Transactional} de clase: cada paso abre su
 * transacción, como en producción.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@DisplayName("Pastilla Renacer sin red: lo que se guarda de verdad (spec 048, #186)")
class EntregarPastillaSinRedIT {

    private static final String LIMA = "America/Lima";
    private static final LocalDate LUNES = LocalDate.of(2026, 11, 9);
    /** 07:05 de Lima del lunes: el audio se desbloqueó y su plazo es el mediodía de ese día. */
    private static final Instant DESBLOQUEO = Instant.parse("2026-11-09T12:05:00Z");
    private static final Instant MEDIODIA = Instant.parse("2026-11-09T17:00:00Z");
    /** 11:00 de Lima del lunes, dentro del plazo. */
    private static final Instant HECHO = Instant.parse("2026-11-09T16:00:00Z");
    private static final Instant LLEGA_EL_MISMO_DIA = Instant.parse("2026-11-09T18:00:00Z");
    private static final Instant LLEGA_AL_OTRO_DIA = Instant.parse("2026-11-10T13:00:00Z");
    private static final int DIA_PROGRAMA = 20;
    /** El audio del día de programa 20 es el 13 (se desbloquea 7 días después). */
    private static final int DIA_DEL_AUDIO = 13;
    private static final int SALDO_INICIAL = 100;
    private static final int PUNTOS_DEL_HABITO = 10;
    private static final String RESUMEN = "Entendí que la calma se entrena y que respirar antes de contestar cambia mi día";

    @Autowired private EntregarResumenEspirituSinRedUseCase entregarSinRed;
    @Autowired private EntregarResumenEspirituUseCase entregar;
    @Autowired private ConsultarEstadoEspirituUseCase consultar;
    @Autowired private SaveRegistroHabitoPort saveRegistroPort;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RelojDePrueba reloj;

    private UserId persona;
    private RegistroHabitoId habito;

    @BeforeEach
    void sembrar() {
        reloj.fijar(LLEGA_EL_MISMO_DIA);
        persona = UserId.of(UUID.randomUUID());
        jdbc.update("INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado) VALUES (?, ?, 'Fixture Pastilla sin red', 'APRENDIZ', 'ACTIVO')",
                persona.value(), persona + "@renaser.test");
        jdbc.update("INSERT INTO renaser.participantes_programa (usuario_id, fecha_inicio, dia_programa, programa_activado_en, timezone) "
                + "VALUES (?, ?, ?, now(), ?)", persona.value(), LUNES.minusDays(DIA_PROGRAMA - 1L), DIA_PROGRAMA, LIMA);
        jdbc.update("INSERT INTO renaser.registros_espiritu (id, participante_id, dia, desbloqueado_en, fecha_limite, estado) "
                        + "VALUES (gen_random_uuid(), ?, ?, ?, ?, 'PENDIENTE'::renaser.estado_registro_espiritu)",
                persona.value(), DIA_DEL_AUDIO, Timestamp.from(DESBLOQUEO), Timestamp.from(MEDIODIA));
        HabitoId pastilla = HabitoId.of(jdbc.queryForObject("SELECT id FROM renaser.habitos WHERE clave_sistema = ?", UUID.class,
                CompletarPastillaRenacerUseCase.CLAVE_SISTEMA_PASTILLA_RENACER));
        habito = RegistroHabitoId.of(UUID.randomUUID());
        saveRegistroPort.save(RegistroHabito.generar(habito, persona, pastilla, LUNES, DIA_PROGRAMA, TipoDia.delDia(LUNES), false, DESBLOQUEO));
    }

    @AfterEach
    void limpiar() {
        // ON DELETE CASCADE arrastra la participación, los registros, los envíos y el ledger.
        jdbc.update("DELETE FROM renaser.usuarios WHERE id = ?", persona.value());
    }

    private static HechoSinRed envio(UUID id, Instant hechoEn) {
        return new HechoSinRed(hechoEn, id, OrigenDeLaHora.RELOJ_DEL_SISTEMA);
    }

    private EntregarResumenEspirituCommand comando() {
        return new EntregarResumenEspirituCommand(persona, DIA_DEL_AUDIO, RESUMEN);
    }

    private Map<String, Object> audio() {
        return jdbc.queryForMap("SELECT estado::text AS estado, entregado_en, resumen_texto FROM renaser.registros_espiritu "
                + "WHERE participante_id = ? AND dia = ?", persona.value(), DIA_DEL_AUDIO);
    }

    private Map<String, Object> habitoDelDia() {
        return jdbc.queryForMap("SELECT estado::text AS estado, puntos_otorgados, completado_en, respuesta_texto FROM renaser.registros_habito WHERE id = ?",
                habito.value());
    }

    private int asientos() {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.ajustes_puntos_liga WHERE participante_id = ?", Integer.class, persona.value());
    }

    private int saldo() {
        return jdbc.queryForObject("SELECT puntos_liga FROM renaser.puntajes_participante WHERE participante_id = ?", Integer.class, persona.value());
    }

    private int envios() {
        return jdbc.queryForObject("SELECT count(*) FROM renaser.envios_sin_red WHERE participante_id = ?", Integer.class, persona.value());
    }

    private static Instant instante(Object timestamp) {
        return ((Timestamp) timestamp).toInstant();
    }

    @Test
    @DisplayName("la entregada a las 11:00 que llega a las 13:00 queda a tiempo y su hábito paga con la hora real; hoy contaba tardía")
    void llegaALasUnaPeroSeHizoALasOnce() { // spec 048 FR-010
        ResultadoEntregaSinRed resultado = entregarSinRed.entregar(comando(), envio(UUID.randomUUID(), HECHO));

        assertThat(resultado.aTiempo()).isTrue();
        assertThat(resultado.motivoSinPuntos()).isNull();
        assertThat(audio()).containsEntry("estado", "ENTREGADO").containsEntry("resumen_texto", RESUMEN);
        assertThat(instante(audio().get("entregado_en"))).isEqualTo(HECHO);
        assertThat(habitoDelDia()).containsEntry("estado", "COMPLETADO").containsEntry("respuesta_texto", RESUMEN);
        assertThat(((Number) habitoDelDia().get("puntos_otorgados")).intValue()).isEqualTo(PUNTOS_DEL_HABITO);
        assertThat(instante(habitoDelDia().get("completado_en"))).isEqualTo(HECHO);
        assertThat(saldo()).isEqualTo(SALDO_INICIAL + PUNTOS_DEL_HABITO);
        assertThat(asientos()).isEqualTo(1);
    }

    @Test
    @DisplayName("sin las cabeceras todo sigue como antes: a las 13:00 es tardía, queda pendiente con la hora de llegada y el envío no deja fila")
    void sinCabecerasEsComoAntes() { // spec 048 FR-008
        var resultado = entregar.entregar(comando());

        assertThat(resultado.aTiempo()).isFalse();
        assertThat(audio()).containsEntry("estado", "PENDIENTE").containsEntry("resumen_texto", RESUMEN);
        assertThat(instante(audio().get("entregado_en"))).isEqualTo(LLEGA_EL_MISMO_DIA);
        assertThat(envios()).isZero();
    }

    @Test
    @DisplayName("si llega al día siguiente con el audio ya PERDIDO por la lectura del servidor, queda a tiempo (hoy: 409 «…: PERDIDO»), y el siguiente audio se desbloquea")
    void llegaAlOtroDiaConElAudioYaPerdido() { // spec 048 FR-010
        reloj.fijar(LLEGA_AL_OTRO_DIA);
        consultar.consultar(persona);
        assertThat(audio()).containsEntry("estado", "PERDIDO");

        ResultadoEntregaSinRed resultado = entregarSinRed.entregar(comando(), envio(UUID.randomUUID(), HECHO));

        assertThat(resultado.aTiempo()).isTrue();
        assertThat(audio()).containsEntry("estado", "ENTREGADO");
        assertThat(instante(audio().get("entregado_en"))).isEqualTo(HECHO);
        assertThat(habitoDelDia()).containsEntry("estado", "COMPLETADO");
        assertThat(instante(habitoDelDia().get("completado_en"))).isEqualTo(HECHO);
        assertThat(saldo()).isEqualTo(SALDO_INICIAL + PUNTOS_DEL_HABITO);
        consultar.consultar(persona);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM renaser.registros_espiritu WHERE participante_id = ? AND dia = ?", Integer.class,
                persona.value(), DIA_DEL_AUDIO + 1)).isEqualTo(1);
    }

    @Test
    @DisplayName("el mismo envío dos veces es uno solo: una entrega, un pago, una fila de envío, y los dos responden lo mismo")
    void reintentoEsUnaSolaVez() { // spec 048 FR-008
        UUID id = UUID.randomUUID();

        var primero = entregarSinRed.entregar(comando(), envio(id, HECHO));
        var segundo = entregarSinRed.entregar(comando(), envio(id, HECHO));

        assertThat(segundo).isEqualTo(primero);
        assertThat(asientos()).isEqualTo(1);
        assertThat(envios()).isEqualTo(1);
        assertThat(jdbc.queryForMap("SELECT tipo, resultado, recurso FROM renaser.envios_sin_red WHERE id_cliente = ?", id))
                .containsEntry("tipo", "ENTREGAR_PASTILLA").containsEntry("resultado", "APLICADO").containsEntry("recurso", "pastilla:13");
    }

    @Test
    @DisplayName("cuatro pedidos a la vez con el mismo envío tienen un solo efecto (el candado por X-Id-Cliente)")
    void pedidosConcurrentes() throws Exception { // spec 048 FR-008
        UUID id = UUID.randomUUID();
        int hilos = 4;
        CyclicBarrier salida = new CyclicBarrier(hilos);
        ExecutorService pool = Executors.newFixedThreadPool(hilos);
        try {
            List<Future<ResultadoEntregaSinRed>> futuros = new ArrayList<>();
            for (int i = 0; i < hilos; i++) {
                Callable<ResultadoEntregaSinRed> pedido = () -> {
                    salida.await(10, TimeUnit.SECONDS);
                    return entregarSinRed.entregar(comando(), envio(id, HECHO));
                };
                futuros.add(pool.submit(pedido));
            }
            for (Future<ResultadoEntregaSinRed> futuro : futuros) {
                assertThat(futuro.get(60, TimeUnit.SECONDS)).isEqualTo(new ResultadoEntregaSinRed(true, null));
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(asientos()).isEqualTo(1);
        assertThat(saldo()).isEqualTo(SALDO_INICIAL + PUNTOS_DEL_HABITO);
        assertThat(envios()).isEqualTo(1);
    }

    @Test
    @DisplayName("regla 02: hecha a las 21:40 de Lima (02:40Z, ya martes en UTC) es del lunes: se aplica, tardía; no es FUERA_DE_SU_DIA")
    void laMadrugadaUtcEsElDiaLocalAnterior() { // spec 048 FR-010
        reloj.fijar(Instant.parse("2026-11-10T03:00:00Z"));
        Instant nocheDeLima = Instant.parse("2026-11-10T02:40:00Z");

        ResultadoEntregaSinRed resultado = entregarSinRed.entregar(comando(), envio(UUID.randomUUID(), nocheDeLima));

        assertThat(resultado.aTiempo()).isFalse();
        assertThat(audio()).containsEntry("estado", "PENDIENTE").containsEntry("resumen_texto", RESUMEN);
        assertThat(instante(audio().get("entregado_en"))).isEqualTo(nocheDeLima);
    }

    @Test
    @DisplayName("D29: hecha el martes (el día de ese audio ya cerró) no se aplica: FUERA_DE_SU_DIA, queda anotado y no toca nada")
    void elDiaYaCerro() { // spec 048 FR-016
        reloj.fijar(LLEGA_AL_OTRO_DIA);
        UUID id = UUID.randomUUID();

        assertThatThrownBy(() -> entregarSinRed.entregar(comando(), envio(id, Instant.parse("2026-11-10T12:00:00Z"))))
                .isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                        e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.FUERA_DE_SU_DIA));

        assertThat(audio()).containsEntry("estado", "PENDIENTE").containsEntry("resumen_texto", null);
        assertThat(habitoDelDia()).containsEntry("estado", "PENDIENTE");
        assertThat(asientos()).isZero();
        assertThat(jdbc.queryForMap("SELECT resultado, codigo FROM renaser.envios_sin_red WHERE id_cliente = ?", id))
                .containsEntry("resultado", "NO_APLICADO").containsEntry("codigo", "FUERA_DE_SU_DIA");
    }

    @Test
    @DisplayName("llegar a más de 48 h solo quita los puntos: la Pastilla queda a tiempo, el hábito sin puntos y la respuesta lo dice")
    void masDeCuarentaYOchoHoras() { // spec 048 FR-015
        reloj.fijar(Instant.parse("2026-11-12T13:00:00Z"));
        UUID id = UUID.randomUUID();

        ResultadoEntregaSinRed resultado = entregarSinRed.entregar(comando(), envio(id, HECHO));

        assertThat(resultado).isEqualTo(new ResultadoEntregaSinRed(true, "LLEGO_DESPUES_DE_48_H"));
        assertThat(audio()).containsEntry("estado", "ENTREGADO");
        assertThat(habitoDelDia()).containsEntry("estado", "COMPLETADO");
        assertThat(((Number) habitoDelDia().get("puntos_otorgados")).intValue()).isZero();
        assertThat(asientos()).isZero();
        assertThat(entregarSinRed.entregar(comando(), envio(id, HECHO))).isEqualTo(resultado);
        assertThat(jdbc.queryForObject("SELECT motivo_sin_puntos FROM renaser.envios_sin_red WHERE id_cliente = ?", String.class, id))
                .isEqualTo("LLEGO_DESPUES_DE_48_H");
    }

    @Test
    @DisplayName("un día ya entregado (aquí, con red) es YA_ESTABA_HECHO y no se toca ni se paga otra vez")
    void yaEntregadaConRed() { // spec 048 FR-016
        reloj.fijar(Instant.parse("2026-11-09T16:30:00Z"));
        entregar.entregar(comando());
        int saldoAntes = saldo();
        reloj.fijar(LLEGA_EL_MISMO_DIA);

        assertThatThrownBy(() -> entregarSinRed.entregar(new EntregarResumenEspirituCommand(persona, DIA_DEL_AUDIO, "otra cosa"),
                envio(UUID.randomUUID(), HECHO.plusSeconds(3600)))).isInstanceOfSatisfying(NoAplicadoSinRedException.class,
                e -> assertThat(e.codigo()).isEqualTo(CodigoNoAplicado.YA_ESTABA_HECHO));

        assertThat(audio()).containsEntry("resumen_texto", RESUMEN);
        assertThat(saldo()).isEqualTo(saldoAntes);
    }
}
