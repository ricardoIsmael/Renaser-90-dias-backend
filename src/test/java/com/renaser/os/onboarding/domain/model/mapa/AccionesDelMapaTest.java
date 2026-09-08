package com.renaser.os.onboarding.domain.model.mapa;

import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Los limites de V06 en el SERVIDOR. Hasta ahora vivian solo en `reglas.ts`, o sea que un `curl`
 * los salteaba: estas pruebas son las que hacen que dejen de ser una sugerencia del cliente.
 */
class AccionesDelMapaTest {

    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-09-08T02:00:00Z"));
    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());

    private static AccionMapa accion(String accionId, AreaMapa area) {
        return AccionMapa.crear(UUID.randomUUID(), APRENDIZ, accionId, area, "Caminar 40 minutos", 3,
                Set.of(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY), MomentoAccion.MANANA,
                EvidenciaAccion.FOTO, RELOJ);
    }

    @Test
    void seisAccionesEsElTecho() {
        var seis = List.of(accion("a1", AreaMapa.SALUD), accion("a2", AreaMapa.SALUD),
                accion("a3", AreaMapa.NEGOCIO_DINERO), accion("a4", AreaMapa.NEGOCIO_DINERO),
                accion("a5", AreaMapa.RELACIONES), accion("a6", AreaMapa.RELACIONES));

        assertThat(new AccionesDelMapa(seis).cantidad()).isEqualTo(6);
    }

    @Test
    void unaSeptimaAccionSeRechaza() {
        var siete = List.of(accion("a1", AreaMapa.SALUD), accion("a2", AreaMapa.SALUD),
                accion("a3", AreaMapa.NEGOCIO_DINERO), accion("a4", AreaMapa.NEGOCIO_DINERO),
                accion("a5", AreaMapa.RELACIONES), accion("a6", AreaMapa.RELACIONES),
                accion("a7", AreaMapa.SALUD));

        assertThatThrownBy(() -> new AccionesDelMapa(siete))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("6");
    }

    /** Sin este limite alguien carga las seis sobre un objetivo y deja los otros dos sin sistema. */
    @Test
    void unaTerceraAccionEnLaMismaAreaSeRechaza() {
        var tresDeSalud = List.of(accion("a1", AreaMapa.SALUD), accion("a2", AreaMapa.SALUD),
                accion("a3", AreaMapa.SALUD));

        assertThatThrownBy(() -> new AccionesDelMapa(tresDeSalud))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("salud");
    }

    @Test
    void dosAccionesConElMismoAccionIdSeRechazan() {
        var repetidas = List.of(accion("misma", AreaMapa.SALUD), accion("misma", AreaMapa.RELACIONES));

        assertThatThrownBy(() -> new AccionesDelMapa(repetidas))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("accionId");
    }

    /** El paso puede quedar a medias: el minimo se exige al activar, no al guardar. */
    @Test
    void laListaVaciaEsValida() {
        assertThat(AccionesDelMapa.vacio().cantidad()).isZero();
    }

    @Test
    void unTextoDeMenosDeCincoCaracteresSeRechaza() {
        assertThatThrownBy(() -> AccionMapa.crear(UUID.randomUUID(), APRENDIZ, "a1", AreaMapa.SALUD, "ir", 3,
                Set.of(DayOfWeek.MONDAY), null, null, RELOJ))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void unaFrecuenciaFueraDeUnoASieteSeRechaza() {
        assertThatThrownBy(() -> AccionMapa.crear(UUID.randomUUID(), APRENDIZ, "a1", AreaMapa.SALUD,
                "Caminar 40 minutos", 8, Set.of(DayOfWeek.MONDAY), null, null, RELOJ))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("frecuenciaSemanal");
    }

    /**
     * AC-07: reintentar "Activar" NO puede reapuntar la accion a un habito nuevo — asi es como se
     * terminarian creando dos habitos para la misma accion.
     */
    @Test
    void vincularUnSegundoHabitoNoPisaAlPrimero() {
        var accion = accion("a1", AreaMapa.SALUD);
        UUID primero = UUID.randomUUID();

        accion.vincularHabito(primero, RELOJ);
        accion.vincularHabito(UUID.randomUUID(), RELOJ);

        assertThat(accion.habitoId()).isEqualTo(primero);
        assertThat(accion.yaEsHabito()).isTrue();
    }
}
