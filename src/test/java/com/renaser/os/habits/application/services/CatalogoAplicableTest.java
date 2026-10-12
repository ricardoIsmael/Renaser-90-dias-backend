package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.audiencia.HistorialDeRolPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.audiencia.Audiencia;
import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.habits.domain.model.audiencia.HistorialDeRol;
import com.renaser.os.habits.domain.model.audiencia.TramoDeRol;
import com.renaser.os.habits.domain.model.audiencia.VersionDeAudiencia;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Spec 001 (D-264): el filtro único de hábitos globales por rol, con vigencia estricta en la zona de la persona. */
@ExtendWith(MockitoExtension.class)
class CatalogoAplicableTest {

    private static final Instant CREACION = Instant.parse("2026-09-01T15:00:00Z");
    /** 2026-10-13T04:30Z = 2026-10-12 23:30 en Lima: un cambio del día 12 local. */
    private static final Instant CAMBIO_DE_NOCHE = Instant.parse("2026-10-13T04:30:00Z");
    private static final LocalDate DIA_DEL_CAMBIO = LocalDate.parse("2026-10-12");
    private static final LocalDate DIA_SIGUIENTE = LocalDate.parse("2026-10-13");
    private static final UserId PERSONA = UserId.of(UUID.randomUUID());
    private static final UserId ADMIN = UserId.of(UUID.randomUUID());

    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private HistorialDeRolPort historialDeRolPort;
    private final AudienciasEnMemoria audiencias = new AudienciasEnMemoria();
    private CatalogoAplicable catalogo;

    private final Habito paraTodos = habito("Para todos");
    private final Habito deMentores = habito("De mentores");

    @BeforeEach
    void preparar() {
        catalogo = new CatalogoAplicable(loadHabitoPort, audiencias, historialDeRolPort);
        when(loadHabitoPort.catalogoActivo()).thenReturn(List.of(paraTodos, deMentores));
        lenient().when(historialDeRolPort.de(any())).thenReturn(HistorialDeRol.vacio());
    }

    @Test
    @DisplayName("sin audiencias, todo el catálogo activo: los hábitos de antes siguen igual (SC-003)")
    void sinAudienciasTodoAplica() {
        assertThat(catalogo.globalesPara(PERSONA, progreso(RolParticipante.TRAINEE), DIA_DEL_CAMBIO))
                .containsExactly(paraTodos, deMentores);
    }

    @Test
    @DisplayName("restringido a MENTOR: el aprendiz no lo recibe y el mentor sí")
    void restringidoAMentor() {
        versionDesde(deMentores, Audiencia.PARA_TODOS, AudienciasDelCatalogo.VERSION_BASE);
        versionDesde(deMentores, soloMentores(), CREACION);

        assertThat(catalogo.globalesPara(PERSONA, progreso(RolParticipante.TRAINEE), DIA_DEL_CAMBIO))
                .containsExactly(paraTodos);
        assertThat(catalogo.globalesPara(PERSONA, progreso(RolParticipante.MENTOR), DIA_DEL_CAMBIO))
                .containsExactly(paraTodos, deMentores);
        assertThat(catalogo.globalesQueNoLeTocan(PERSONA, progreso(RolParticipante.TRAINEE), DIA_DEL_CAMBIO))
                .containsExactly(deMentores.id());
    }

    @Test
    @DisplayName("un cambio de las 23:30 de Lima rige recién el día local siguiente")
    void cambioDeNocheRigeMañana() {
        versionDesde(deMentores, Audiencia.PARA_TODOS, AudienciasDelCatalogo.VERSION_BASE);
        versionDesde(deMentores, soloMentores(), CAMBIO_DE_NOCHE);

        assertThat(catalogo.globalesPara(PERSONA, progreso(RolParticipante.TRAINEE), DIA_DEL_CAMBIO))
                .contains(deMentores);
        assertThat(catalogo.globalesPara(PERSONA, progreso(RolParticipante.TRAINEE), DIA_SIGUIENTE))
                .doesNotContain(deMentores);
    }

    @Test
    @DisplayName("cambio de rol hoy: hoy recibe los de su rol anterior, mañana los del nuevo (US4)")
    void cambioDeRol() {
        versionDesde(deMentores, soloMentores(), CREACION);
        when(historialDeRolPort.de(PERSONA)).thenReturn(HistorialDeRol.de(List.of(
                new TramoDeRol(CodigoDeRol.of("TRAINEE"), TramoDeRol.DESDE_SIEMPRE),
                new TramoDeRol(CodigoDeRol.of("MENTOR"), CAMBIO_DE_NOCHE))));

        // El progreso ya dice MENTOR (rol actual), pero el día del cambio sigue el de aprendiz.
        assertThat(catalogo.globalesPara(PERSONA, progreso(RolParticipante.MENTOR), DIA_DEL_CAMBIO))
                .doesNotContain(deMentores);
        assertThat(catalogo.globalesPara(PERSONA, progreso(RolParticipante.MENTOR), DIA_SIGUIENTE))
                .contains(deMentores);
    }

    @Test
    @DisplayName("un rol que no existe hoy funciona igual, sin tocar el filtro (US5)")
    void rolFuturo() {
        versionDesde(deMentores, Audiencia.de(Set.of(CodigoDeRol.of("COACH"))), CREACION);
        when(historialDeRolPort.de(PERSONA)).thenReturn(HistorialDeRol.de(List.of(
                new TramoDeRol(CodigoDeRol.of("COACH"), TramoDeRol.DESDE_SIEMPRE))));

        assertThat(catalogo.globalesPara(PERSONA, progreso(RolParticipante.TRAINEE), DIA_DEL_CAMBIO))
                .contains(deMentores);
    }

    private void versionDesde(Habito habito, Audiencia audiencia, Instant desde) {
        audiencias.agregarVersion(new VersionDeAudiencia(habito.id(), audiencia, desde, ADMIN, desde));
    }

    private static Audiencia soloMentores() {
        return Audiencia.de(Set.of(CodigoDeRol.of("MENTOR")));
    }

    private static ProgresoParticipanteHabits progreso(RolParticipante rol) {
        return new ProgresoParticipanteHabits(15, "America/Lima", rol, false, true);
    }

    private static Habito habito(String titulo) {
        return Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), titulo, TipoHabito.CHECKBOX,
                new DetallesHabito("desc", "CUERPO", ExigenciaEvidencia.OPCIONAL, false, false), CREACION);
    }
}
