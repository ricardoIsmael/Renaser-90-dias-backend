package com.renaser.os.rocks.application.services;

import com.renaser.os.rocks.application.ports.out.medicion.ConsultarMedicionDelMapaPort.MedicionDelParticipante;
import com.renaser.os.rocks.application.ports.in.rocamensual.ConsultarObjetivoDelMesUseCase.PlanMensualDelEje;
import com.renaser.os.rocks.application.ports.out.medicion.ConsultarMedicionDelMapaPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.ProgresoParticipanteRocks;
import com.renaser.os.rocks.application.ports.out.participante.ConsultarProgresoParticipanteRocksPort.RolParticipante;
import com.renaser.os.rocks.application.ports.out.rocamaestra.LoadRocaMaestraPort;
import com.renaser.os.rocks.application.ports.out.rocamensual.LoadRocaMensualPort;
import com.renaser.os.rocks.domain.model.rocamaestra.EjeObjetivo;
import com.renaser.os.rocks.domain.model.rocamaestra.MetaCuantitativa;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestra;
import com.renaser.os.rocks.domain.model.rocamaestra.RocaMaestraId;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensual;
import com.renaser.os.rocks.domain.model.rocamensual.RocaMensualId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Que la SEMANA siga al objetivo del mes que de verdad rige.
 *
 * <p>El contrato de {@code MesDelPlan} dice que lo que la persona guarda <b>manda</b> sobre el
 * calculo, y la app ya lo respetaba para el mes. La semana no: se derivaba siempre del numero
 * propuesto. Corregir el mes movia el de arriba y dejaba el de abajo apuntando a la ruta vieja —
 * dos escalones del mismo plan contradiciendose, que es el problema que E-203 cerro un nivel mas
 * arriba y que aqui volvia a aparecer.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("El objetivo de la semana sigue al mes editado")
class ObjetivoDelMesServiceTest {

    private static final UserId ACTOR = UserId.of(UUID.randomUUID());
    /** Dia 16: mes 1, tercera semana — quedan 3 semanas del mes (MesPrograma). */
    private static final int DIA_PROGRAMA = 16;

    @Mock private LoadRocaMaestraPort loadRocaMaestraPort;
    @Mock private LoadRocaMensualPort loadRocaMensualPort;
    @Mock private ConsultarMedicionDelMapaPort medicionPort;
    @Mock private ConsultarProgresoParticipanteRocksPort progresoPort;

    private ObjetivoDelMesService service;
    private RocaMaestra cuerpo;

    @BeforeEach
    void preparar() {
        service = new ObjetivoDelMesService(loadRocaMaestraPort, loadRocaMensualPort, medicionPort, progresoPort);
        // Bajar de 84 a 78 kg, sin haberse vuelto a pesar: hoy sigue en 84.
        cuerpo = RocaMaestra.rehydrate(RocaMaestraId.of(UUID.randomUUID()), ACTOR, EjeObjetivo.CUERPO,
                "Al Dia 90 pesare 78 kg",
                MetaCuantitativa.desde(new BigDecimal("84"), new BigDecimal("78"), "kg"),
                Instant.now(), Instant.now());
        when(progresoPort.deParticipante(ACTOR)).thenReturn(Optional.of(
                new ProgresoParticipanteRocks(DIA_PROGRAMA, java.time.LocalDate.of(2026, 9, 8),
                        ZoneId.of("America/Lima"), RolParticipante.TRAINEE, false, false)));
        when(medicionPort.deParticipante(ACTOR)).thenReturn(
                new MedicionDelParticipante("peso", "kg", null, null, null, null));
        when(loadRocaMaestraPort.deParticipante(ACTOR)).thenReturn(List.of(cuerpo));
    }

    private PlanMensualDelEje plan() {
        return service.misObjetivosMensuales(ACTOR).get(0);
    }

    @Test
    @DisplayName("sin editar, la semana cuelga del mes calculado (81.6 kg al cierre del mes 1)")
    void sinEditarUsaElCalculado() {
        when(loadRocaMensualPort.deParticipante(ACTOR)).thenReturn(List.of());

        var p = plan();
        assertThat(p.meses().get(0).calculado()).isNotNull();
        // 84 + (81.6 - 84) / 3 semanas = 83.2
        assertThat(p.semana().valor()).isEqualByComparingTo(new BigDecimal("83.2"));
    }

    @Test
    @DisplayName("editado el mes a 80 kg, la semana se recalcula contra ESE numero y no contra 81.6")
    void alEditarElMesSeRecalculaLaSemana() {
        when(loadRocaMensualPort.deParticipante(ACTOR)).thenReturn(List.of(mensual(new BigDecimal("80"))));

        // 84 + (80 - 84) / 3 = 82.666... -> 82.7 redondeado a un decimal
        assertThat(plan().semana().valor()).isEqualByComparingTo(new BigDecimal("82.7"));
    }

    @Test
    @DisplayName("si el mes editado quedo sin cifra, no se inventa una semana con el numero viejo")
    void mesEditadoSinCifraNoDejaSemana() {
        when(loadRocaMensualPort.deParticipante(ACTOR)).thenReturn(List.of(
                RocaMensual.rehydrate(RocaMensualId.of(UUID.randomUUID()), cuerpo.id(), 1,
                        "Sostener el habito", null, Instant.now(), Instant.now())));

        assertThat(plan().semana()).isNull();
    }

    private RocaMensual mensual(BigDecimal objetivo) {
        return RocaMensual.rehydrate(RocaMensualId.of(UUID.randomUUID()), cuerpo.id(), 1, "Mi mes",
                MetaCuantitativa.desde(new BigDecimal("84"), objetivo, "kg"), Instant.now(), Instant.now());
    }
}
