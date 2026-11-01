package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarObjetivoDelMesUseCase.PlanMensualDelEje;
import com.renaser.os.rocks.application.ports.out.medicion.ConsultarMedicionDelMapaPort;
import com.renaser.os.rocks.application.ports.out.medicion.ConsultarMedicionDelMapaPort.MedicionDelParticipante;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocamensual.LoadRocaMensualPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocamensual.Magnitud;
import com.renaser.os.rocks.domain.model.rocamensual.ObjetivoDelMes;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Spec 026: el objetivo que nació del diagnóstico trae su propia meta medible (a dónde llegar, desde dónde parte, en qué unidad) y con ella el plan mensual
 * saca la cifra del mes. No hay Mapa del que leer qué se mide: la unidad lo dice. Sin meta declarada sigue «sin cifra».
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El plan mensual de un objetivo que nació del diagnóstico")
class ObjetivoDelMesDelDiagnosticoTest {

    private static final UserId ACTOR = UserId.of(UUID.randomUUID());
    private static final Instant AHORA = Instant.parse("2026-11-02T10:00:00Z");

    @Mock private LoadRocaMaestraPort loadRocaMaestraPort;
    @Mock private LoadRocaMensualPort loadRocaMensualPort;
    @Mock private ConsultarMedicionDelMapaPort medicionPort;
    @Mock private ConsultarProgresoParticipanteRocksPort progresoPort;

    private ObjetivoDelMesService service;

    @BeforeEach
    void preparar() {
        service = new ObjetivoDelMesService(loadRocaMaestraPort, loadRocaMensualPort, medicionPort, progresoPort);
        when(progresoPort.deParticipante(ACTOR)).thenReturn(Optional.of(
                new ProgresoParticipanteRocks(2, LocalDate.of(2026, 11, 1), ZoneId.of("America/Lima"), RolParticipante.TRAINEE, false, true)));
        // Quien se mide por el diagnóstico no pasó por el Mapa: no hay nada que leer de ahí.
        when(medicionPort.deParticipante(ACTOR)).thenReturn(new MedicionDelParticipante(null, null, null, null, null, null));
        when(loadRocaMensualPort.deParticipante(ACTOR)).thenReturn(List.of());
    }

    private RocaMaestra delDiagnostico(EjeObjetivo eje, MetaCuantitativa meta) {
        return RocaMaestra.delDiagnostico(RocaMaestraId.of(UUID.randomUUID()), ACTOR, eje, "Mi objetivo", meta, AHORA);
    }

    private PlanMensualDelEje planDe(RocaMaestra roca) {
        when(loadRocaMaestraPort.deParticipante(ACTOR)).thenReturn(List.of(roca));
        return service.misObjetivosMensuales(ACTOR).get(0);
    }

    private static ObjetivoDelMes.ConCifra conCifra(PlanMensualDelEje plan, int mes) {
        return (ObjetivoDelMes.ConCifra) plan.meses().get(mes - 1).calculado();
    }

    @Test
    @DisplayName("Cuerpo en kilos: baja del peso de hoy hacia la meta, mes a mes, y llega a ella en el último mes")
    void cuerpoEnKilos() {
        PlanMensualDelEje plan = planDe(delDiagnostico(EjeObjetivo.CUERPO, MetaCuantitativa.desde(new BigDecimal("92"), new BigDecimal("85"), "kg")));

        assertThat(plan.unidad()).isEqualTo("kg");
        assertThat(plan.adelante()).isFalse();
        ObjetivoDelMes.ConCifra primero = conCifra(plan, 1);
        assertThat(primero.sube()).isFalse();
        assertThat(primero.magnitud()).isEqualTo(Magnitud.NIVEL);
        assertThat(primero.valor()).isLessThan(new BigDecimal("92")).isGreaterThan(new BigDecimal("85"));
        assertThat(conCifra(plan, 3).valor()).isEqualByComparingTo("85");
    }

    @Test
    @DisplayName("Negocio en soles al mes: sube hacia la meta y la moneda va delante")
    void negocioEnSoles() {
        PlanMensualDelEje plan = planDe(delDiagnostico(EjeObjetivo.TRABAJO, MetaCuantitativa.desde(new BigDecimal("10000"), new BigDecimal("15000"), "S/ al mes")));

        assertThat(plan.unidad()).isEqualTo("S/ al mes");
        assertThat(plan.adelante()).isTrue();
        ObjetivoDelMes.ConCifra primero = conCifra(plan, 1);
        assertThat(primero.sube()).isTrue();
        assertThat(primero.valor()).isGreaterThan(new BigDecimal("10000")).isLessThan(new BigDecimal("15000"));
        assertThat(conCifra(plan, 3).valor()).isEqualByComparingTo("15000");
    }

    @Test
    @DisplayName("Relaciones es una escala del 1 al 10: cifras enteras, y llega a la meta en el último mes")
    void relacionesEsUnaEscala() {
        PlanMensualDelEje plan = planDe(delDiagnostico(EjeObjetivo.RELACIONES, MetaCuantitativa.desde(new BigDecimal("4"), new BigDecimal("8"), "/10")));

        assertThat(plan.unidad()).isEqualTo("/10");
        ObjetivoDelMes.ConCifra primero = conCifra(plan, 1);
        assertThat(primero.magnitud()).isEqualTo(Magnitud.ESCALA);
        assertThat(primero.valor().stripTrailingZeros().scale()).isLessThanOrEqualTo(0);
        assertThat(conCifra(plan, 3).valor()).isEqualByComparingTo("8");
    }

    @Test
    @DisplayName("sin meta medible el objetivo del diagnóstico queda «sin cifra», como antes")
    void sinMetaSinCifra() {
        PlanMensualDelEje plan = planDe(delDiagnostico(EjeObjetivo.CUERPO, null));

        assertThat(plan.meses()).allSatisfy(mes -> assertThat(mes.calculado()).isInstanceOf(ObjetivoDelMes.SinCifra.class));
        assertThat(plan.semana()).isNull();
    }

    @Test
    @DisplayName("un objetivo que no nació del diagnóstico sigue leyendo qué se mide del Mapa")
    void elDeSiempreNoCambia() {
        RocaMaestra delPlan = RocaMaestra.definir(RocaMaestraId.of(UUID.randomUUID()), ACTOR, EjeObjetivo.CUERPO, "Pesar 85 kg",
                MetaCuantitativa.desde(new BigDecimal("92"), new BigDecimal("85"), "kg"), AHORA);

        PlanMensualDelEje plan = planDe(delPlan);

        assertThat(plan.meses()).allSatisfy(mes -> assertThat(mes.calculado()).isInstanceOf(ObjetivoDelMes.SinCifra.class));
    }
}
