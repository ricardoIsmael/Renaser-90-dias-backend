package com.renaser.os.points.infrastructure.adapter.out.persistence.semaforo;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.points.domain.model.semaforo.CumplimientoDelDia;
import com.renaser.os.points.domain.model.semaforo.FotoSemanal;
import com.renaser.os.points.domain.model.semaforo.PausaDeMedicion;
import com.renaser.os.points.domain.model.semaforo.PausaId;
import com.renaser.os.points.domain.model.semaforo.ReglaDelSemaforo;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Los tres adaptadores del semáforo contra Postgres real con la migración V68 aplicada: que el
 * upsert no reescriba un día sin cambios, que la foto semanal sea append-only y que las pausas
 * vayan y vuelvan enteras.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
class SemaforoJdbcAdaptersTest {

    private static final Instant AHORA = Instant.parse("2026-09-26T05:25:00Z");
    private static final LocalDate LUNES = LocalDate.of(2026, 9, 21);
    private static final LocalDate MARTES = LocalDate.of(2026, 9, 22);
    private static final LocalDate VIERNES_18 = LocalDate.of(2026, 9, 18);
    private static final LocalDate VIERNES_25 = LocalDate.of(2026, 9, 25);

    @Autowired
    private DiasDelSemaforoJdbcAdapter dias;
    @Autowired
    private SemanasDelSemaforoJdbcAdapter semanas;
    @Autowired
    private PausasDelSemaforoJdbcAdapter pausas;
    @Autowired
    private JdbcClient jdbcClient;

    private UserId ana;
    private UserId beto;

    @BeforeEach
    void participantes() {
        ana = participante();
        beto = participante();
    }

    private UserId participante() {
        UUID id = UUID.randomUUID();
        jdbcClient.sql("""
                        INSERT INTO renaser.usuarios (id, email, nombre_completo, rol, estado)
                        VALUES (:id, :email, 'Fixture semaforo', 'APRENDIZ', 'ACTIVO')
                        """)
                .param("id", id).param("email", id + "@renaser.test").update();
        jdbcClient.sql("INSERT INTO renaser.participantes_programa (usuario_id, dia_programa) VALUES (:id, 10)")
                .param("id", id).update();
        return UserId.of(id);
    }

    private static FotoSemanal foto(LocalDate viernes, String porcentaje) {
        return new FotoSemanal(viernes, viernes.minusDays(6), porcentaje == null ? null : new BigDecimal(porcentaje),
                porcentaje == null ? 0 : 5, 7, ReglaDelSemaforo.VERSION_FORMULA, AHORA);
    }

    @Test
    void unDiaSinCambiosNoSeReescribe() {
        List<CumplimientoDelDia> semana = List.of(new CumplimientoDelDia(LUNES, 5, 4, 3, 1),
                new CumplimientoDelDia(MARTES, 0, 0, 0, 0));

        assertThat(dias.guardar(ana, semana, AHORA)).isEqualTo(2);
        assertThat(dias.guardar(ana, semana, AHORA.plusSeconds(3600))).isZero();
        assertThat(dias.guardar(ana, List.of(new CumplimientoDelDia(LUNES, 5, 5, 3, 1)), AHORA.plusSeconds(7200)))
                .isEqualTo(1);

        var leidos = dias.entre(List.of(ana, beto), LUNES, MARTES);
        assertThat(leidos).containsOnlyKeys(ana);
        assertThat(leidos.get(ana).get(LUNES).habitosCumplidos()).isEqualTo(5);
        assertThat(leidos.get(ana).get(MARTES).conDatos()).isFalse();
        assertThat(dias.ultimoCalculoDe(ana)).contains(AHORA.plusSeconds(7200));
        assertThat(dias.ultimoCalculoDe(beto)).isEmpty();
    }

    @Test
    void laFotoSemanalNoSePisa() {
        assertThat(semanas.registrar(ana, foto(VIERNES_18, "70.0"))).isTrue();
        assertThat(semanas.registrar(ana, foto(VIERNES_25, null))).isTrue();
        assertThat(semanas.registrar(ana, foto(VIERNES_25, "99.0"))).isFalse();

        assertThat(semanas.ultimaCerradaDe(List.of(ana, beto))).containsOnlyKeys(ana).containsEntry(ana, VIERNES_25);
        assertThat(semanas.deLaSemana(List.of(ana), VIERNES_25).get(ana).porcentaje()).isNull();
        assertThat(semanas.ultimasDe(ana, 8)).extracting(FotoSemanal::semanaHasta)
                .containsExactly(VIERNES_18, VIERNES_25);
        assertThat(semanas.ultimasDe(ana, 1)).extracting(FotoSemanal::semanaHasta).containsExactly(VIERNES_25);
    }

    @Test
    void lasPausasVanYVuelvenEnteras() {
        PausaDeMedicion pausa = PausaDeMedicion.iniciar(PausaId.of(UUID.randomUUID()), beto, LUNES,
                LUNES.plusDays(4), AHORA);
        pausas.guardar(pausa);
        pausa.reanudar(MARTES, AHORA.plusSeconds(86_400));
        pausas.guardar(pausa);

        var leidas = pausas.de(List.of(ana, beto));
        assertThat(leidas).containsOnlyKeys(beto);
        PausaDeMedicion leida = leidas.get(beto).getFirst();
        assertThat(leida.id()).isEqualTo(pausa.id());
        assertThat(leida.reanudadaEl()).isEqualTo(MARTES);
        assertThat(leida.cubre(LUNES)).isTrue();
        assertThat(leida.cubre(MARTES)).isFalse();
    }
}
