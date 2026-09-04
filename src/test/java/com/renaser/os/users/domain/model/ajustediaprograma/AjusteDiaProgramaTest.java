package com.renaser.os.users.domain.model.ajustediaprograma;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Bitacora de ajustes del dia del programa (V21, D-82). */
class AjusteDiaProgramaTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-09-03T15:00:00Z"));

    private static final UUID ID = UUID.fromString("00000000-0000-4000-8000-000000000001");

    private static AjusteDiaPrograma ajuste(String motivo) {
        return AjusteDiaPrograma.registrar(ID, UserId.of(UUID.randomUUID()), 40, 34, 0, 6, motivo,
                UserId.of(UUID.randomUUID()), CLOCK);
    }

    @Test
    void registrarGuardaElAntesYElDespuesDelDiaYDelOffset() {
        AjusteDiaPrograma a = ajuste("Viaje 03/09-09/09, aviso al volver");

        assertThat(a.diaAnterior()).isEqualTo(40);
        assertThat(a.diaNuevo()).isEqualTo(34);
        assertThat(a.diasAjusteAnterior()).isZero();
        assertThat(a.diasAjusteNuevo()).isEqualTo(6);
        assertThat(a.motivo()).isEqualTo("Viaje 03/09-09/09, aviso al volver");
        assertThat(a.ajustadoEn()).isEqualTo(CLOCK.now());
        assertThat(a.id()).isEqualTo(ID);
    }

    @Test
    void diasMovidosEsNegativoCuandoSeRetrocede() {
        assertThat(ajuste("viaje").diasMovidos()).isEqualTo(-6);
    }

    /**
     * El endpoint ya existia sin `motivo` y el panel admin todavia no lo manda: un ajuste
     * sin explicacion se guarda igual, con una marca explicita. Perder la bitacora entera
     * por un campo que el cliente viejo no conoce seria el peor resultado.
     */
    @Test
    void motivoVacioONuloSeGuardaComoNoRegistradoEnVezDeExplotar() {
        assertThat(ajuste(null).motivo()).isEqualTo(AjusteDiaPrograma.MOTIVO_NO_REGISTRADO);
        assertThat(ajuste("   ").motivo()).isEqualTo(AjusteDiaPrograma.MOTIVO_NO_REGISTRADO);
    }

    @Test
    void motivoSeRecortaAlTopeDeLaColumnaEnVezDeRomperElInsert() {
        String largo = "x".repeat(AjusteDiaPrograma.MAX_MOTIVO + 50);

        assertThat(ajuste(largo).motivo()).hasSize(AjusteDiaPrograma.MAX_MOTIVO);
    }

    @Test
    void motivoSeGuardaSinEspaciosAlBorde() {
        assertThat(ajuste("  viaje  ").motivo()).isEqualTo("viaje");
    }

    @Test
    void registrarRechazaIdParticipanteOAutorNulos() {
        UserId alguien = UserId.of(UUID.randomUUID());

        assertThatThrownBy(() -> AjusteDiaPrograma.registrar(null, alguien, 40, 34, 0, 6, "x", alguien, CLOCK))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AjusteDiaPrograma.registrar(ID, null, 40, 34, 0, 6, "x", alguien, CLOCK))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> AjusteDiaPrograma.registrar(ID, alguien, 40, 34, 0, 6, "x", null, CLOCK))
                .isInstanceOf(NullPointerException.class);
    }
}
