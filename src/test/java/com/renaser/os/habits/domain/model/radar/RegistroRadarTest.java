package com.renaser.os.habits.domain.model.radar;

import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Dominio puro — sin Spring, sin Postgres (CLAUDE.MD §5.1). */
class RegistroRadarTest {

    private static final Instant AHORA = Instant.parse("2026-08-24T14:00:00Z");
    private static final UserId PARTICIPANTE = UserId.of(UUID.randomUUID());

    @Test
    void registrarCreaUnCheckInValido() {
        RegistroRadar registro = registrar("haciendo algo", "pensando algo", "sintiendo algo", 7, "evitando algo");

        assertThat(registro.id()).isNotNull();
        assertThat(registro.participanteId()).isEqualTo(PARTICIPANTE);
        assertThat(registro.queHago()).isEqualTo("haciendo algo");
        assertThat(registro.quePienso()).isEqualTo("pensando algo");
        assertThat(registro.queSiento()).isEqualTo("sintiendo algo");
        assertThat(registro.nivelEnergia()).isEqualTo(7);
        assertThat(registro.queEvito()).isEqualTo("evitando algo");
        assertThat(registro.creadoEn()).isEqualTo(AHORA);
    }

    @Test
    void registrarRecortaEspaciosDeLosTextos() {
        RegistroRadar registro = registrar("  haciendo  ", "  pensando  ", "  sintiendo  ", 5, "  evitando  ");

        assertThat(registro.queHago()).isEqualTo("haciendo");
        assertThat(registro.quePienso()).isEqualTo("pensando");
        assertThat(registro.queSiento()).isEqualTo("sintiendo");
        assertThat(registro.queEvito()).isEqualTo("evitando");
    }

    @Test
    void registrarRechazaNivelEnergiaMenorAUno() {
        assertThatThrownBy(() -> registrar("h", "p", "s", 0, "e"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nivelEnergia");
    }

    @Test
    void registrarRechazaNivelEnergiaMayorADiez() {
        assertThatThrownBy(() -> registrar("h", "p", "s", 11, "e"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("nivelEnergia");
    }

    @Test
    void registrarAceptaLosBordesDelRangoDeEnergia() {
        assertThat(registrar("h", "p", "s", 1, "e").nivelEnergia()).isEqualTo(1);
        assertThat(registrar("h", "p", "s", 10, "e").nivelEnergia()).isEqualTo(10);
    }

    @Test
    void registrarRechazaCampoDeTextoVacio() {
        assertThatThrownBy(() -> registrar("", "p", "s", 5, "e"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queHago");
    }

    @Test
    void registrarRechazaCampoDeTextoEnBlanco() {
        assertThatThrownBy(() -> registrar("h", "   ", "s", 5, "e"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("quePienso");
    }

    @Test
    void registrarRechazaCampoDeTextoNulo() {
        assertThatThrownBy(() -> RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), PARTICIPANTE, "h", "p",
                null, 5, "e", AHORA))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queSiento");
    }

    @Test
    void registrarRechazaTextoQueSuperaElMaximo() {
        String demasiadoLargo = "x".repeat(RegistroRadar.TEXTO_MAX_LENGTH + 1);

        assertThatThrownBy(() -> registrar(demasiadoLargo, "p", "s", 5, "e"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("queHago")
                .hasMessageContaining("maximo");
    }

    @Test
    void registrarAceptaTextoJustoEnElMaximo() {
        String justoEnElLimite = "x".repeat(RegistroRadar.TEXTO_MAX_LENGTH);

        RegistroRadar registro = registrar(justoEnElLimite, "p", "s", 5, "e");

        assertThat(registro.queHago()).hasSize(RegistroRadar.TEXTO_MAX_LENGTH);
    }

    @Test
    void registrarRechazaParticipanteNulo() {
        assertThatThrownBy(() -> RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), null, "h", "p", "s", 5,
                "e", AHORA))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void dosRegistrosSonIgualesSoloSiComparteId() {
        RegistroRadar uno = registrar("h", "p", "s", 5, "e");
        RegistroRadar otro = registrar("h", "p", "s", 5, "e");

        assertThat(uno).isNotEqualTo(otro);
        assertThat(uno).isEqualTo(uno);
    }

    @Test
    void rehydrateNoRevalidaRangosNiRecorta() {
        RegistroRadarId id = RegistroRadarId.of(UUID.randomUUID());

        RegistroRadar registro = RegistroRadar.rehydrate(id, PARTICIPANTE, "  h  ", "p", "s", 5, "e", AHORA);

        assertThat(registro.id()).isEqualTo(id);
        assertThat(registro.queHago()).isEqualTo("  h  ");
    }

    private static RegistroRadar registrar(String queHago, String quePienso, String queSiento, int nivelEnergia,
                                            String queEvito) {
        return RegistroRadar.registrar(RegistroRadarId.of(UUID.randomUUID()), PARTICIPANTE, queHago, quePienso,
                queSiento, nivelEnergia, queEvito, AHORA);
    }
}
