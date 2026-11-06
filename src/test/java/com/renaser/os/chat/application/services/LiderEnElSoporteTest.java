package com.renaser.os.chat.application.services;

import com.renaser.os.chat.application.ports.out.conversacion.LoadConversacionPort;
import com.renaser.os.chat.application.ports.out.conversacion.SaveConversacionPort;
import com.renaser.os.chat.application.ports.out.participante.AgregarParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.EsParticipantePort;
import com.renaser.os.chat.application.ports.out.participante.QuitarParticipantePort;
import com.renaser.os.chat.domain.model.conversacion.Conversacion;
import com.renaser.os.chat.domain.model.conversacion.ConversacionId;
import com.renaser.os.chat.domain.model.conversacion.Participante;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import com.renaser.os.users.api.FasePrograma;
import com.renaser.os.users.api.ParticipacionPrograma;
import com.renaser.os.users.api.ParticipacionProgramaFinder;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Adicional 11 del dueño en la spec 044: «en el grupo de soporte también está el líder de mentores». Hoy no estaba (solo ADMIN y ALCHEMIST);
 * ahora entra a los soportes de los aprendices de SU formación, y sale si deja de ser líder o cambia de formación.
 */
class LiderEnElSoporteTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-11-06T15:00:00Z"));
    private static final UserId ANA = UserId.of(UUID.randomUUID());
    private static final UserId DIEGO_DEL_DIPLOMADO = UserId.of(UUID.randomUUID());
    private static final UserId LIDER = UserId.of(UUID.randomUUID());
    private static final UserId ADMINA = UserId.of(UUID.randomUUID());
    private static final UserId MENTOR = UserId.of(UUID.randomUUID());

    private final LoadConversacionPort conversaciones = mock(LoadConversacionPort.class);
    private final SaveConversacionPort guardar = mock(SaveConversacionPort.class);
    private final AgregarParticipantePort agregar = mock(AgregarParticipantePort.class);
    private final QuitarParticipantePort quitar = mock(QuitarParticipantePort.class);
    private final EsParticipantePort esParticipante = mock(EsParticipantePort.class);
    private final UserSummaryFinder usuarios = mock(UserSummaryFinder.class);
    private final ParticipacionProgramaFinder participaciones = mock(ParticipacionProgramaFinder.class);
    private final IdGenerator ids = mock(IdGenerator.class);
    private final Set<Formacion> formacionesDelLider = new HashSet<>(Set.of(Formacion.ESCUELA));
    private ConversacionSoporteService servicio;

    private final AlcanceDeFormacionFinder formaciones = new AlcanceDeFormacionFinder() {
        @Override
        public AlcanceDeFormacion de(UserId actor) {
            return AlcanceDeFormacion.todas();
        }

        @Override
        public Optional<Formacion> formacionDe(UserId persona) {
            return Optional.of(Formacion.ESCUELA);
        }

        @Override
        public Set<Formacion> formacionesDe(UserId persona) {
            return persona.equals(LIDER) ? formacionesDelLider : EnumSet.allOf(Formacion.class);
        }

        @Override
        public Set<UserId> dentroDelAlcance(Collection<UserId> personas, AlcanceDeFormacion alcance) {
            return new HashSet<>(personas);
        }

        @Override
        public void exigirQueLaVea(UserId actor, UserId objetivo) {
        }

        @Override
        public void exigirQueLaVea(UserId actor, Formacion formacionDelObjeto) {
        }
    };

    @BeforeEach
    void armar() {
        servicio = new ConversacionSoporteService(conversaciones, guardar, agregar, quitar, esParticipante, usuarios, participaciones, CLOCK, ids,
                mock(PlatformTransactionManager.class), mock(org.springframework.context.ApplicationEventPublisher.class), new VisibilidadPermisiva(),
                formaciones);
        when(ids.newId()).thenReturn(UUID.randomUUID());
        when(guardar.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(participaciones.usuariosActivosConRol(Set.of(UserRole.ADMIN, UserRole.ALCHEMIST))).thenReturn(List.of(ADMINA));
        when(participaciones.usuariosActivosConRol(Set.of(UserRole.MENTOR_LEAD))).thenReturn(List.of(LIDER));
    }

    private static Conversacion soporteDe(UserId aprendiz, Formacion formacion) {
        return Conversacion.crearSoporte(ConversacionId.of(UUID.randomUUID()), aprendiz, "Soporte", CLOCK.now(), formacion);
    }

    private static ParticipacionPrograma participacion(UserId id, UserRole rol, Formacion formacion) {
        return new ParticipacionPrograma(id, true, 3, LocalDate.of(2026, 11, 1), ZoneId.of("America/Lima"), FasePrograma.values()[0], null, null,
                rol, false, true, formacion, 90);
    }

    private List<UserId> agregados() {
        ArgumentCaptor<Participante> c = ArgumentCaptor.forClass(Participante.class);
        verify(agregar, org.mockito.Mockito.atLeast(0)).agregar(c.capture());
        return c.getAllValues().stream().map(Participante::usuarioId).toList();
    }

    // spec 044 FR-037
    @Test
    @DisplayName("FR-037: el soporte de una aprendiz de la Escuela nace con la aprendiz, Administración y el líder de la Escuela")
    void nuevoSoporteConElLider() {
        when(participaciones.deParticipante(ANA)).thenReturn(Optional.of(participacion(ANA, UserRole.TRAINEE, Formacion.ESCUELA)));
        when(usuarios.findById(ANA)).thenReturn(Optional.of(new UserSummary(ANA, "Ana Perez", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(conversaciones.porClaveDirecta(any())).thenReturn(Optional.empty());

        servicio.incorporar(ANA);

        assertThat(agregados()).containsExactlyInAnyOrder(ANA, ADMINA, LIDER);
    }

    // spec 044 FR-037, SC-010
    @Test
    @DisplayName("FR-037: el líder de la Escuela no entra al soporte de un aprendiz del Diplomado")
    void noEntraAOtraFormacion() {
        when(participaciones.deParticipante(DIEGO_DEL_DIPLOMADO))
                .thenReturn(Optional.of(participacion(DIEGO_DEL_DIPLOMADO, UserRole.TRAINEE, Formacion.DIPLOMADO)));
        when(usuarios.findById(DIEGO_DEL_DIPLOMADO))
                .thenReturn(Optional.of(new UserSummary(DIEGO_DEL_DIPLOMADO, "Diego", null, UserRole.TRAINEE, UserStatus.ACTIVE)));
        when(conversaciones.porClaveDirecta(any())).thenReturn(Optional.empty());

        servicio.incorporar(DIEGO_DEL_DIPLOMADO);

        assertThat(agregados()).containsExactlyInAnyOrder(DIEGO_DEL_DIPLOMADO, ADMINA);
    }

    // spec 044 FR-037
    @Test
    @DisplayName("FR-037: quien pasa a ser líder entra a los soportes que ya existen de su formación, y solo a esos")
    void elNuevoLiderEntraALosQueYaExisten() {
        Conversacion deAna = soporteDe(ANA, Formacion.ESCUELA);
        Conversacion deDiego = soporteDe(DIEGO_DEL_DIPLOMADO, Formacion.DIPLOMADO);
        when(participaciones.deParticipante(LIDER)).thenReturn(Optional.of(participacion(LIDER, UserRole.MENTOR_LEAD, Formacion.ESCUELA)));
        when(conversaciones.deSoporte()).thenReturn(List.of(deAna, deDiego));

        servicio.incorporar(LIDER);

        ArgumentCaptor<Participante> c = ArgumentCaptor.forClass(Participante.class);
        verify(agregar).agregar(c.capture());
        assertThat(c.getValue().conversacionId()).isEqualTo(deAna.id());
        assertThat(c.getValue().usuarioId()).isEqualTo(LIDER);
    }

    // spec 044 FR-039
    @Test
    @DisplayName("FR-039: el líder que pasa de formación sale de los soportes que ya no son de la suya")
    void cambiaDeFormacion() {
        Conversacion deAna = soporteDe(ANA, Formacion.ESCUELA);
        Conversacion deDiego = soporteDe(DIEGO_DEL_DIPLOMADO, Formacion.DIPLOMADO);
        formacionesDelLider.clear();
        formacionesDelLider.add(Formacion.DIPLOMADO);
        when(usuarios.findById(LIDER)).thenReturn(Optional.of(new UserSummary(LIDER, "Rocío", null, UserRole.MENTOR_LEAD, UserStatus.ACTIVE)));
        when(conversaciones.deSoporte()).thenReturn(List.of(deAna, deDiego));
        when(esParticipante.esParticipante(deAna.id(), LIDER)).thenReturn(true);

        servicio.retirarPorBajaDeStaff(LIDER);

        verify(quitar).quitar(deAna.id(), LIDER);
        verify(quitar, never()).quitar(deDiego.id(), LIDER);
    }

    // spec 044 FR-039
    @Test
    @DisplayName("FR-039: quien deja de ser líder sale de todos los soportes ajenos")
    void dejaDeSerLider() {
        Conversacion deAna = soporteDe(ANA, Formacion.ESCUELA);
        when(usuarios.findById(LIDER)).thenReturn(Optional.of(new UserSummary(LIDER, "Rocío", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        when(conversaciones.deSoporte()).thenReturn(List.of(deAna));
        when(esParticipante.esParticipante(deAna.id(), LIDER)).thenReturn(true);

        servicio.retirarPorBajaDeStaff(LIDER);

        verify(quitar).quitar(deAna.id(), LIDER);
    }

    // spec 044 FR-038, FR-041
    @Test
    @DisplayName("FR-038/FR-041: acompañan el soporte ADMIN, ALCHEMIST y el líder; el mentor y el aprendiz, no")
    void quienAcompana() {
        assertThat(StaffDelSoporte.acompana(UserRole.MENTOR_LEAD)).isTrue();
        assertThat(StaffDelSoporte.acompana(UserRole.ADMIN)).isTrue();
        assertThat(StaffDelSoporte.acompana(UserRole.ALCHEMIST)).isTrue();
        assertThat(StaffDelSoporte.acompana(UserRole.MENTOR)).isFalse();
        assertThat(StaffDelSoporte.acompana(UserRole.TRAINEE_GUIDE)).isFalse();
        assertThat(StaffDelSoporte.acompana(UserRole.TRAINEE)).isFalse();
        assertThat(MENTOR).isNotNull();
    }
}
