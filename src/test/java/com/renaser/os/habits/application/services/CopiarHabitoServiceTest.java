package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.habitoadmin.CopiarHabitoAOtraFormacionUseCase.CopiarHabitoCommand;
import com.renaser.os.habits.application.ports.in.habitoadmin.HabitoDelCatalogo;
import com.renaser.os.habits.application.ports.out.categoria.LoadCategoriaHabitoPort;
import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort;
import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort.Accion;
import com.renaser.os.habits.application.ports.out.categoria.RegistrarCambioDelCatalogoPort.CambioDelCatalogo;
import com.renaser.os.habits.application.ports.out.guia.LoadGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.guia.SaveGuiaHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.habito.SaveHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.LoadHorarioHabitoPort;
import com.renaser.os.habits.application.ports.out.horario.SaveHorarioHabitoPort;
import com.renaser.os.habits.domain.model.audiencia.Audiencia;
import com.renaser.os.habits.domain.model.audiencia.CodigoDeRol;
import com.renaser.os.habits.domain.model.categoria.CategoriaHabito;
import com.renaser.os.habits.domain.model.guia.GuiaHabito;
import com.renaser.os.habits.domain.model.guia.GuiaHabitoId;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabito;
import com.renaser.os.habits.domain.model.horario.HorarioHabitoId;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.IdGenerator;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.UserRole;
import com.renaser.os.users.api.UserStatus;
import com.renaser.os.users.api.UserSummary;
import com.renaser.os.users.api.UserSummaryFinder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spec 023, historia 3: «Copiar a otra formación» crea un hábito independiente, con su horario, su guía y su audiencia. */
@ExtendWith(MockitoExtension.class)
class CopiarHabitoServiceTest {

    private static final FixedClock CLOCK = FixedClock.at(Instant.parse("2026-10-31T15:00:00Z"));

    @Mock
    private LoadHabitoPort loadPort;
    @Mock
    private SaveHabitoPort savePort;
    @Mock
    private LoadHorarioHabitoPort loadHorarioPort;
    @Mock
    private SaveHorarioHabitoPort saveHorarioPort;
    @Mock
    private LoadGuiaHabitoPort loadGuiaPort;
    @Mock
    private SaveGuiaHabitoPort saveGuiaPort;
    @Mock
    private LoadCategoriaHabitoPort categoriasPort;
    @Mock
    private RegistrarCambioDelCatalogoPort auditoria;
    @Mock
    private UserSummaryFinder userSummaryFinder;
    @Mock
    private IdGenerator idGenerator;
    private final AudienciasEnMemoria audiencias = new AudienciasEnMemoria();

    private CopiarHabitoService service;

    private final UserId admin = UserId.of(UUID.randomUUID());
    private final UserId mentor = UserId.of(UUID.randomUUID());
    private Habito original;

    @BeforeEach
    void preparar() {
        service = new CopiarHabitoService(loadPort, savePort, loadHorarioPort, saveHorarioPort, loadGuiaPort, saveGuiaPort,
                categoriasPort, auditoria, new AudienciasDelCatalogo(audiencias, audiencias, userSummaryFinder, CLOCK),
                new HabitoAdminGuard(userSummaryFinder), CLOCK, idGenerator);
        lenient().when(userSummaryFinder.findById(admin))
                .thenReturn(Optional.of(new UserSummary(admin, "Admin", null, UserRole.ADMIN, UserStatus.ACTIVE)));
        lenient().when(userSummaryFinder.findById(mentor))
                .thenReturn(Optional.of(new UserSummary(mentor, "Mentor", null, UserRole.MENTOR, UserStatus.ACTIVE)));
        lenient().when(idGenerator.newId()).thenAnswer(inv -> UUID.randomUUID());
        lenient().when(savePort.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(loadHorarioPort.porHabito(any())).thenReturn(List.of());
        lenient().when(loadGuiaPort.porHabito(any())).thenReturn(List.of());
        original = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), Formacion.ESCUELA, "Despertar", TipoHabito.CHECKBOX,
                new DetallesHabito("desc", "CUERPO", ExigenciaEvidencia.OBLIGATORIA, false, true), CLOCK.now());
        lenient().when(loadPort.byId(original.id())).thenReturn(Optional.of(original));
        lenient().when(loadPort.catalogoCompleto()).thenReturn(List.of(original));
        lenient().when(categoriasPort.byClave("DIPLOMADO_ESTUDIO")).thenReturn(Optional.of(
                CategoriaHabito.rehydrate("DIPLOMADO_ESTUDIO", Formacion.DIPLOMADO, "Estudio", 1, null, true, CLOCK.now(), CLOCK.now())));
    }

    private CopiarHabitoCommand copiar() {
        return new CopiarHabitoCommand(admin, original.id(), Formacion.DIPLOMADO, "DIPLOMADO_ESTUDIO");
    }

    @Test
    @DisplayName("la copia es otro hábito, de la formación elegida y de su categoría, con el mismo contenido")
    void copia() {
        HabitoDelCatalogo copia = service.copiar(copiar());

        assertThat(copia.habito().id()).isNotEqualTo(original.id());
        assertThat(copia.habito().formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(copia.habito().categoriaClave()).isEqualTo("DIPLOMADO_ESTUDIO");
        assertThat(copia.habito().titulo()).isEqualTo("Despertar");
        assertThat(copia.habito().exigenciaEvidencia()).isEqualTo(ExigenciaEvidencia.OBLIGATORIA);
        assertThat(copia.habito().obligatorioEnIntoxicacion()).isTrue();
        // El original no se tocó (FR-023: sin ningún vínculo posterior).
        assertThat(original.formacion()).isEqualTo(Formacion.ESCUELA);
        assertThat(original.categoriaClave()).isEqualTo("CUERPO");
        verify(savePort).save(any());
    }

    @Test
    @DisplayName("se copian los horarios y la guía (textos) y la audiencia por rol; nada del original se reutiliza por id")
    void copiaHorariosGuiasYAudiencia() {
        HorarioHabito horario = HorarioHabito.crear(HorarioHabitoId.of(UUID.randomUUID()), original.id(), 8, 40, TipoDia.TODOS,
                LocalTime.of(6, 0), LocalTime.of(7, 0), CLOCK.now());
        GuiaHabito guia = GuiaHabito.crear(GuiaHabitoId.of(UUID.randomUUID()), original.id(), 1, CLOCK.now());
        when(loadHorarioPort.porHabito(original.id())).thenReturn(List.of(horario));
        when(loadGuiaPort.porHabito(original.id())).thenReturn(List.of(guia));
        audiencias.agregarVersion(new com.renaser.os.habits.domain.model.audiencia.VersionDeAudiencia(original.id(),
                Audiencia.de(Set.of(CodigoDeRol.of("MENTOR"))), Instant.EPOCH, admin, Instant.EPOCH));

        HabitoDelCatalogo copia = service.copiar(copiar());

        ArgumentCaptor<HorarioHabito> horarios = ArgumentCaptor.forClass(HorarioHabito.class);
        verify(saveHorarioPort).save(horarios.capture());
        assertThat(horarios.getValue().id()).isNotEqualTo(horario.id());
        assertThat(horarios.getValue().habitoId()).isEqualTo(copia.habito().id());
        assertThat(horarios.getValue().diaInicio()).isEqualTo(8);
        assertThat(horarios.getValue().diaFin()).isEqualTo(40);
        assertThat(horarios.getValue().horaDisparo()).isEqualTo(LocalTime.of(6, 0));
        ArgumentCaptor<GuiaHabito> guias = ArgumentCaptor.forClass(GuiaHabito.class);
        verify(saveGuiaPort).save(guias.capture());
        assertThat(guias.getValue().habitoId()).isEqualTo(copia.habito().id());
        assertThat(guias.getValue().id()).isNotEqualTo(guia.id());
        assertThat(copia.audienciaGuardada().roles()).extracting(CodigoDeRol::valor).containsExactly("MENTOR");
    }

    @Test
    @DisplayName("deja constancia de la copia: quién, desde qué formación y a cuál")
    void constancia() {
        HabitoDelCatalogo copia = service.copiar(copiar());

        ArgumentCaptor<CambioDelCatalogo> cambio = ArgumentCaptor.forClass(CambioDelCatalogo.class);
        verify(auditoria).registrar(cambio.capture());
        assertThat(cambio.getValue().accion()).isEqualTo(Accion.COPIADO);
        assertThat(cambio.getValue().actor()).isEqualTo(admin);
        assertThat(cambio.getValue().formacion()).isEqualTo(Formacion.DIPLOMADO);
        assertThat(cambio.getValue().clave()).isEqualTo(copia.habito().id().value().toString());
        assertThat(cambio.getValue().detalle()).containsEntry("desde", "ESCUELA");
    }

    @Test
    @DisplayName("solo ADMIN/ALQUIMISTA copian; otro rol, 403 sin escribir nada")
    void sinPermiso() {
        assertThatThrownBy(() -> service.copiar(new CopiarHabitoCommand(mentor, original.id(), Formacion.DIPLOMADO, "DIPLOMADO_ESTUDIO")))
                .isInstanceOf(NotAuthorizedException.class);
        verify(savePort, never()).save(any());
    }

    @Test
    @DisplayName("copiar a la misma formación se rechaza: se elige otra")
    void aLaMismaFormacion() {
        assertThatThrownBy(() -> service.copiar(new CopiarHabitoCommand(admin, original.id(), Formacion.ESCUELA, "CUERPO")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("otra formación");
    }

    @Test
    @DisplayName("la categoría destino debe existir, ser de la formación elegida y estar activa")
    void categoriaDestino() {
        when(categoriasPort.byClave("CUERPO")).thenReturn(Optional.of(
                CategoriaHabito.rehydrate("CUERPO", Formacion.ESCUELA, "Cuerpo", 1, null, true, CLOCK.now(), CLOCK.now())));
        when(categoriasPort.byClave("DIPLOMADO_VIEJA")).thenReturn(Optional.of(
                CategoriaHabito.rehydrate("DIPLOMADO_VIEJA", Formacion.DIPLOMADO, "Vieja", 2, null, false, CLOCK.now(), CLOCK.now())));
        when(categoriasPort.byClave("NO_EXISTE")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.copiar(new CopiarHabitoCommand(admin, original.id(), Formacion.DIPLOMADO, "CUERPO")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no es de la formación");
        assertThatThrownBy(() -> service.copiar(new CopiarHabitoCommand(admin, original.id(), Formacion.DIPLOMADO, "DIPLOMADO_VIEJA")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("desactivada");
        assertThatThrownBy(() -> service.copiar(new CopiarHabitoCommand(admin, original.id(), Formacion.DIPLOMADO, "NO_EXISTE")))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("no existe");
        verify(savePort, never()).save(any());
    }

    @Test
    @DisplayName("si la formación destino ya tiene un hábito con ese título (sin importar mayúsculas ni tildes) se rechaza con su motivo")
    void tituloRepetido() {
        Habito yaEsta = Habito.crearDeSistema(HabitoId.of(UUID.randomUUID()), Formacion.DIPLOMADO, "DESPERTAR", TipoHabito.CHECKBOX,
                new DetallesHabito(null, "DIPLOMADO_ESTUDIO", ExigenciaEvidencia.OPCIONAL, false, false), CLOCK.now());
        when(loadPort.catalogoCompleto()).thenReturn(List.of(original, yaEsta));

        assertThatThrownBy(() -> service.copiar(copiar())).isInstanceOf(IllegalStateException.class).hasMessageContaining("título");
        verify(savePort, never()).save(any());
    }

    @Test
    @DisplayName("un hábito de flujo propio no se copia a una formación que ya tiene uno con la misma función")
    void claveRepetida() {
        Habito conClave = Habito.rehydrate(HabitoId.of(UUID.randomUUID()), com.renaser.os.habits.domain.model.habito.AmbitoHabito.SISTEMA, null,
                Formacion.ESCUELA, "Pastilla Renacer", null, TipoHabito.CHECKBOX, "ESPIRITU", null, "PASTILLA_RENACER",
                ExigenciaEvidencia.OPCIONAL, false, false, true, false, null, null, null, null, true, CLOCK.now(), CLOCK.now());
        Habito yaEnDestino = Habito.rehydrate(HabitoId.of(UUID.randomUUID()), com.renaser.os.habits.domain.model.habito.AmbitoHabito.SISTEMA, null,
                Formacion.DIPLOMADO, "Otra pastilla", null, TipoHabito.CHECKBOX, "DIPLOMADO_ESTUDIO", null, "PASTILLA_RENACER",
                ExigenciaEvidencia.OPCIONAL, false, false, true, false, null, null, null, null, true, CLOCK.now(), CLOCK.now());
        when(loadPort.byId(conClave.id())).thenReturn(Optional.of(conClave));
        when(loadPort.catalogoCompleto()).thenReturn(List.of(conClave, yaEnDestino));

        assertThatThrownBy(() -> service.copiar(new CopiarHabitoCommand(admin, conClave.id(), Formacion.DIPLOMADO, "DIPLOMADO_ESTUDIO")))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("misma función");
    }

    @Test
    @DisplayName("un hábito que no existe (o es personal) es un 404")
    void inexistente() {
        HabitoId otro = HabitoId.of(UUID.randomUUID());
        when(loadPort.byId(otro)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.copiar(new CopiarHabitoCommand(admin, otro, Formacion.DIPLOMADO, "DIPLOMADO_ESTUDIO")))
                .isInstanceOf(NoSuchElementException.class);
    }
}
