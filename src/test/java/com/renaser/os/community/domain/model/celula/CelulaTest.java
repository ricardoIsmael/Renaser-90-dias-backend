package com.renaser.os.community.domain.model.celula;

import com.renaser.os.community.domain.model.cohorte.CohorteId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CelulaTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-08-24T10:00:00Z"));
    /** El id ya no lo sortea la factoria: entra por parametro, generado por el puerto IdGenerator. */
    private static final CelulaId ID = CelulaId.of(UUID.randomUUID());
    /** "Septiembre, del 1 al 30, se llama Fenix" — el ejemplo con el que el cliente pidio V48. */
    private static final PeriodoGrupo SEPTIEMBRE = new PeriodoGrupo(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30));

    private static Celula nueva() {
        return Celula.crear(ID, "Celula 1", CohorteId.of(UUID.randomUUID()), null, CLOCK.now());
    }

    private static Celula conPeriodo(PeriodoGrupo periodo) {
        return Celula.crear(ID, "Fenix", CohorteId.of(UUID.randomUUID()), null, periodo, CLOCK.now());
    }

    @Test
    void crearNaceSinMentor() {
        Celula c = nueva();
        assertThat(c.id()).isEqualTo(ID);
        assertThat(c.mentorId()).isNull();
    }

    @Test
    void nombreVacioEsInvalido() {
        assertThatThrownBy(() -> Celula.crear(ID, " ", CohorteId.of(UUID.randomUUID()), null, CLOCK.now()))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void asignarMentorLoDejaListo() {
        Celula c = nueva();
        UserId mentor = UserId.of(UUID.randomUUID());
        c.asignarMentor(mentor, CLOCK.now());
        assertThat(c.mentorId()).isEqualTo(mentor);
    }

    @Test
    void quitarMentorLoDejaEnNull() {
        Celula c = nueva();
        c.asignarMentor(UserId.of(UUID.randomUUID()), CLOCK.now());
        c.quitarMentor(CLOCK.now());
        assertThat(c.mentorId()).isNull();
    }

    @Test
    void programarSesionRequiereFechaNoNula() {
        Celula c = nueva();
        assertThatThrownBy(() -> c.programarSesion(null, CLOCK.now())).isInstanceOf(NullPointerException.class);
    }

    // ─── V48: el periodo del grupo programado ────────────────────────────────────────

    /**
     * La prueba que protege a las celulas que ya existian. Si {@code vencidoEn} delegara a ciegas
     * en el periodo, V48 apagaria de golpe todos los grupos anteriores a la migracion, que no
     * tienen fechas — y el alumno abriria la app sin grupo sin que nadie hubiera tocado nada.
     */
    @Test
    @DisplayName("Un grupo SIN periodo no vence nunca, ni es futuro, y siempre esta vigente")
    void sinPeriodoNoCaduca() {
        Celula c = nueva();

        assertThat(c.tienePeriodo()).isFalse();
        assertThat(c.periodo()).isNull();
        assertThat(c.vencidoEn(LocalDate.of(2099, 12, 31)))
                .as("no hay fecha que lo venza: es el comportamiento de todas las celulas previas a V48")
                .isFalse();
        assertThat(c.futuroEn(LocalDate.of(1999, 1, 1))).isFalse();
        assertThat(c.vigenteEn(LocalDate.of(2026, 9, 15))).isTrue();
    }

    @Test
    @DisplayName("Con periodo, las tres preguntas las contesta el periodo: el ultimo dia cuenta")
    void conPeriodoDelegaEnElPeriodo() {
        Celula c = conPeriodo(SEPTIEMBRE);

        assertThat(c.tienePeriodo()).isTrue();
        assertThat(c.vigenteEn(LocalDate.of(2026, 9, 30)))
                .as("el 30 todavia es del grupo: 'del 1 al 30' incluye el 30 entero")
                .isTrue();
        assertThat(c.vencidoEn(LocalDate.of(2026, 9, 30))).isFalse();
        assertThat(c.vencidoEn(LocalDate.of(2026, 10, 1))).isTrue();
        assertThat(c.futuroEn(LocalDate.of(2026, 8, 31))).isTrue();
        assertThat(c.vigenteEn(LocalDate.of(2026, 8, 31))).isFalse();
    }

    @Test
    @DisplayName("Un periodo a medias no se construye: van las dos fechas o ninguna")
    void periodoAMediasEsInvalido() {
        assertThat(Celula.periodoDe(null, null)).isNull();
        assertThat(Celula.periodoDe(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 30))).isEqualTo(SEPTIEMBRE);
        assertThatThrownBy(() -> Celula.periodoDe(LocalDate.of(2026, 9, 1), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("las dos fechas o con ninguna");
        assertThatThrownBy(() -> Celula.periodoDe(null, LocalDate.of(2026, 9, 30)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    /**
     * El escenario que motiva el {@code tocaPeriodo}: renombrar el grupo no puede borrarle las
     * fechas. Sin la distincion, un PATCH de una sola linea lo dejaria sin cierre.
     */
    @Test
    @DisplayName("Actualizar sin tocar el periodo lo deja como estaba")
    void actualizarNoBorraElPeriodoPorDescuido() {
        Celula c = conPeriodo(SEPTIEMBRE);

        c.actualizarDatos("Fenix II", null, false, CLOCK.now());

        assertThat(c.nombre()).isEqualTo("Fenix II");
        assertThat(c.periodo()).isEqualTo(SEPTIEMBRE);
    }

    @Test
    @DisplayName("Con tocaPeriodo se cambia, y con tocaPeriodo y null se borra a proposito")
    void actualizarCambiaOBorraElPeriodoCuandoSePide() {
        Celula c = conPeriodo(SEPTIEMBRE);
        PeriodoGrupo octubre = PeriodoGrupo.mesDe(LocalDate.of(2026, 10, 15));

        c.actualizarDatos(null, null, false, octubre, true, CLOCK.now());
        assertThat(c.periodo()).isEqualTo(octubre);

        c.actualizarDatos(null, null, false, null, true, CLOCK.now());
        assertThat(c.periodo()).isNull();
        assertThat(c.vencidoEn(LocalDate.of(2099, 12, 31))).isFalse();
    }

    @Test
    @DisplayName("La sobrecarga vieja de rehydrate deja el grupo sin periodo")
    void rehydrateViejoNoInventaPeriodo() {
        Celula c = Celula.rehydrate(ID, "Celula 1", null, CohorteId.of(UUID.randomUUID()), null, null,
                CLOCK.now(), CLOCK.now());

        assertThat(c.periodo()).isNull();
        assertThat(c.tienePeriodo()).isFalse();
    }
}
