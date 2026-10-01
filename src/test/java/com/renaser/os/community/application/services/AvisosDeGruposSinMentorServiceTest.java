package com.renaser.os.community.application.services;

import com.renaser.os.community.api.FaltaArmarGrupoEvent;
import com.renaser.os.community.domain.model.acompanamiento.CadenciaRotacion;
import com.renaser.os.community.domain.model.acompanamiento.FuncionAcompanamiento;
import com.renaser.os.community.domain.model.acompanamiento.PoliticaMentoria;
import com.renaser.os.community.domain.model.acompanamiento.TipoCelula;
import com.renaser.os.community.domain.model.celula.AvisoDeArmadoDeGrupos;
import com.renaser.os.community.domain.model.celula.CelulaId;
import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** El aviso al líder de un grupo en curso sin mentor (D-240, E-478). */
class AvisosDeGruposSinMentorServiceTest {

    /** 1 de octubre, 04:30 UTC = 30 de setiembre, 23:30 en Lima: el día UTC ya es otro (E-91). */
    private static final Instant NOCHE_DEL_30_EN_LIMA = Instant.parse("2026-10-01T04:30:00Z");
    /** 1 de octubre, 15:00 UTC = 10:00 en Lima. */
    private static final Instant MANANA_DEL_1 = Instant.parse("2026-10-01T15:00:00Z");

    private static final CohorteId COHORTE = CohorteId.of(UUID.randomUUID());
    private static final CelulaId DE_SETIEMBRE = CelulaId.of(UUID.fromString("00000000-0000-0000-0000-000000000009"));
    private static final CelulaId DE_OCTUBRE = CelulaId.of(UUID.fromString("00000000-0000-0000-0000-000000000010"));
    private static final CelulaId CON_MENTOR = CelulaId.of(UUID.fromString("00000000-0000-0000-0000-000000000011"));
    private static final CelulaId RECEPCION = CelulaId.of(UUID.fromString("00000000-0000-0000-0000-0000000000aa"));
    private static final UserId MENTOR = UserId.of(UUID.randomUUID());

    private AcompanamientoEnMemoria banco;

    @BeforeEach
    void preparar() {
        banco = new AcompanamientoEnMemoria();
        banco.cohorte(COHORTE, PoliticaMentoria.rehydrate(COHORTE, 10, CadenciaRotacion.MENSUAL, "America/Lima",
                8, 3, RECEPCION, 1));
        banco.grupo(RECEPCION, COHORTE, TipoCelula.RECEPCION, null, MANANA_DEL_1);
        banco.grupoConPeriodo(DE_SETIEMBRE, COHORTE, null, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30),
                MANANA_DEL_1);
        banco.grupoConPeriodo(DE_OCTUBRE, COHORTE, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31),
                MANANA_DEL_1);
        banco.grupoConPeriodo(CON_MENTOR, COHORTE, MENTOR, LocalDate.of(2026, 9, 1), LocalDate.of(2026, 10, 31),
                MANANA_DEL_1);
        banco.asignar(CON_MENTOR, MENTOR, FuncionAcompanamiento.MENTOR, MANANA_DEL_1.minusSeconds(864_000), null);
    }

    private int barrerA(Instant ahora) {
        return new AvisosDeGruposSinMentorService(banco.cargaCelulas, banco.cargaAsignaciones, banco.cargaPolitica,
                banco.publicador, FixedClock.at(ahora)).avisarDeLosQueNoTienenMentor();
    }

    @Test
    @DisplayName("avisa solo del grupo regular EN CURSO sin mentor: no del programado, ni del que tiene, ni de la recepcion")
    void soloElEnCursoSinMentor() {
        assertThat(barrerA(MANANA_DEL_1)).isEqualTo(1);

        assertThat(banco.eventos).singleElement().isInstanceOfSatisfying(FaltaArmarGrupoEvent.class, aviso -> {
            assertThat(aviso.celulaId()).isEqualTo(DE_OCTUBRE.value());
            assertThat(aviso.motivo()).isEqualTo(FaltaArmarGrupoEvent.Motivo.GRUPO_SIN_MENTOR);
            assertThat(aviso.rutaApp()).isEqualTo("/admin/cells/" + DE_OCTUBRE.value());
            assertThat(aviso.claveDeduplicacion()).isEqualTo(
                    AvisoDeArmadoDeGrupos.claveGrupoSinMentor(DE_OCTUBRE.value(), LocalDate.of(2026, 10, 1)));
        });
    }

    @Test
    @DisplayName("a las 23:30 del 30 en Lima (04:30 UTC del 1) el grupo en curso es el de setiembre, con la clave del 30")
    void elDiaEsElDeLaCohorte() {
        assertThat(barrerA(NOCHE_DEL_30_EN_LIMA)).isEqualTo(1);

        FaltaArmarGrupoEvent aviso = (FaltaArmarGrupoEvent) banco.eventos.getFirst();
        assertThat(aviso.celulaId()).isEqualTo(DE_SETIEMBRE.value());
        assertThat(aviso.claveDeduplicacion()).isEqualTo(
                AvisoDeArmadoDeGrupos.claveGrupoSinMentor(DE_SETIEMBRE.value(), LocalDate.of(2026, 9, 30)));
    }

    @Test
    @DisplayName("antes de las 07:00 locales no avisa: el push no le llega al lider de madrugada")
    void noAvisaDeMadrugada() {
        // 1 de octubre 10:00 UTC = 05:00 en Lima.
        assertThat(barrerA(Instant.parse("2026-10-01T10:00:00Z"))).isZero();
        assertThat(banco.eventos).isEmpty();
    }

    @Test
    @DisplayName("dos corridas el mismo dia publican la misma clave: la bandeja guarda un aviso, no dos")
    void mismaClaveEnElMismoDia() {
        barrerA(MANANA_DEL_1);
        barrerA(MANANA_DEL_1.plusSeconds(3_600));

        assertThat(banco.eventos).extracting(e -> ((FaltaArmarGrupoEvent) e).claveDeduplicacion()).containsOnly(
                AvisoDeArmadoDeGrupos.claveGrupoSinMentor(DE_OCTUBRE.value(), LocalDate.of(2026, 10, 1)));
    }
}
