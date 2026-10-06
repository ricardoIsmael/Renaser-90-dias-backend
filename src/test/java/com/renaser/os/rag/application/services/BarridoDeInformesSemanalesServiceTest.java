package com.renaser.os.rag.application.services;

import com.renaser.os.rag.application.ports.in.espejosombra.GenerarInformeEspejoSombraUseCase;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.ProgramasActivadosFinder;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * E-560. El reloj se fija en la madrugada UTC (regla 03): el lunes 05:00 UTC es la medianoche del domingo de Lima,
 * el corte que fijo el dueño el 2026-10-06 (antes era el lunes 03:00 UTC = domingo 22:00 de Lima).
 */
class BarridoDeInformesSemanalesServiceTest {

    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final ZoneId LOS_ANGELES = ZoneId.of("America/Los_Angeles");
    private static final LocalDate SEMANA_DEL_28_SEP = LocalDate.of(2026, 9, 28);
    private static final Instant LUNES_0500_UTC = Instant.parse("2026-10-05T05:00:00Z");

    private final List<Generado> generados = new ArrayList<>();
    private final List<UserId> aQuienesFalla = new ArrayList<>();
    private final Map<UserId, ZoneId> zonas = new HashMap<>();
    private final FakeParticipacionProgramaFinder padron = new FakeParticipacionProgramaFinder();
    private final List<Integer> tamanosDeLote = new ArrayList<>();

    private record Generado(UserId participante, LocalDate semana) {
    }

    private BarridoDeInformesSemanalesService servicioA(Instant ahora) {
        GenerarInformeEspejoSombraUseCase generar = (participante, semana) -> {
            if (aQuienesFalla.contains(participante)) {
                throw new IllegalStateException("la IA se cayo");
            }
            generados.add(new Generado(participante, semana));
        };
        return new BarridoDeInformesSemanalesService(generar, padron, new ProgramasEnMemoria(), FixedClock.at(ahora));
    }

    private UserId inscrito(ZoneId zona) {
        UserId id = UserId.of(UUID.randomUUID());
        padron.conParticipanteActivo(id);
        if (zona != null) {
            zonas.put(id, zona);
        }
        return id;
    }

    @Test
    void limaElLunes0500UtcGeneraLaSemanaPasadaDeTodosLosInscritos() {
        UserId a = inscrito(LIMA);
        UserId b = inscrito(LIMA);

        servicioA(LUNES_0500_UTC).generarLosQueTocan();

        assertThat(generados).containsExactly(new Generado(a, SEMANA_DEL_28_SEP),
                new Generado(b, SEMANA_DEL_28_SEP));
    }

    @Test
    void unMinutoAntesDeLaMedianocheDeLimaNoSeGeneraNada() {
        inscrito(LIMA);

        servicioA(Instant.parse("2026-10-05T04:59:00Z")).generarLosQueTocan();

        assertThat(generados).isEmpty();
    }

    @Test
    void alLunes0500UtcSoloLimaYaCortoYLosAngelesTodaviaEsDomingoALas2100() {
        UserId lima = inscrito(LIMA);
        inscrito(LOS_ANGELES);

        servicioA(LUNES_0500_UTC).generarLosQueTocan();

        assertThat(generados).containsExactly(new Generado(lima, SEMANA_DEL_28_SEP));
    }

    @Test
    void alLunes0700UtcLeTocaAlDeLosAngelesYLimaSigueEnSuMargenDePuestaAlDia() {
        UserId lima = inscrito(LIMA);
        UserId losAngeles = inscrito(LOS_ANGELES);

        servicioA(Instant.parse("2026-10-05T07:00:00Z")).generarLosQueTocan();

        assertThat(generados).containsExactlyInAnyOrder(new Generado(lima, SEMANA_DEL_28_SEP),
                new Generado(losAngeles, SEMANA_DEL_28_SEP));
    }

    @Test
    void correrloDosVecesPideLoMismoYElCasoDeUsoIdempotenteNoDuplica() {
        UserId a = inscrito(LIMA);
        BarridoDeInformesSemanalesService servicio = servicioA(LUNES_0500_UTC);

        servicio.generarLosQueTocan();
        servicio.generarLosQueTocan();

        assertThat(generados).containsOnly(new Generado(a, SEMANA_DEL_28_SEP));
    }

    @Test
    void correrloTardeDespuesDeUnaNocheSinServidorPoneLaSemanaAlDia() {
        UserId a = inscrito(LIMA);

        servicioA(Instant.parse("2026-10-05T14:00:00Z")).generarLosQueTocan();

        assertThat(generados).containsExactly(new Generado(a, SEMANA_DEL_28_SEP));
    }

    @Test
    void unParticipanteQueFallaNoDetieneAlResto() {
        UserId roto = inscrito(LIMA);
        UserId sano = inscrito(LIMA);
        aQuienesFalla.add(roto);

        servicioA(LUNES_0500_UTC).generarLosQueTocan();

        assertThat(generados).containsExactly(new Generado(sano, SEMANA_DEL_28_SEP));
    }

    @Test
    void quienNoActivoSuProgramaSeTrataComoDeLimaQueEsElDefaultDeSuColumna() {
        UserId sinActivar = inscrito(null);

        servicioA(LUNES_0500_UTC).generarLosQueTocan();

        assertThat(generados).containsExactly(new Generado(sinActivar, SEMANA_DEL_28_SEP));
    }

    @Test
    void elPadronSeRecorreEnLotesYLaZonaSePideUnaVezPorLote() {
        for (int i = 0; i < BarridoDeInformesSemanalesService.TAMANO_LOTE * 2 + 1; i++) {
            inscrito(LIMA);
        }

        servicioA(LUNES_0500_UTC).generarLosQueTocan();

        assertThat(generados).hasSize(BarridoDeInformesSemanalesService.TAMANO_LOTE * 2 + 1);
        assertThat(tamanosDeLote).containsExactly(BarridoDeInformesSemanalesService.TAMANO_LOTE,
                BarridoDeInformesSemanalesService.TAMANO_LOTE, 1);
    }

    private final class ProgramasEnMemoria implements ProgramasActivadosFinder {

        @Override
        public List<ProgramaActivado> pagina(int offset, int limite) {
            return List.of();
        }

        @Override
        public Optional<ProgramaActivado> de(UserId participanteId) {
            return Optional.empty();
        }

        @Override
        public Map<UserId, ProgramaActivado> deVarios(Collection<UserId> participantes) {
            tamanosDeLote.add(participantes.size());
            Map<UserId, ProgramaActivado> resultado = new HashMap<>();
            for (UserId id : participantes) {
                if (zonas.containsKey(id)) {
                    resultado.put(id, new ProgramaActivado(id, zonas.get(id), LocalDate.of(2026, 9, 1),
                            LocalDate.of(2026, 11, 29)));
                }
            }
            return resultado;
        }
    }
}
