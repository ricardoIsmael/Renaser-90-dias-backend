package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.in.categoria.ConsultarCategoriasDeMiFormacionUseCase.CategoriasDeLaFormacion;
import com.renaser.os.habits.application.ports.out.categoria.LoadCategoriaHabitoPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.ProgresoParticipanteHabits;
import com.renaser.os.habits.application.ports.out.participante.ConsultarProgresoParticipanteHabitsPort.RolParticipante;
import com.renaser.os.habits.domain.model.categoria.CategoriaHabito;
import com.renaser.os.shared.domain.AlcanceDeFormacion;
import com.renaser.os.shared.domain.Formacion;
import com.renaser.os.shared.domain.ObjetoDeOtraFormacionException;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.api.AlcanceDeFormacionFinder;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/** Spec 023, historia 4: las dimensiones de Training salen de las categorías de la formación de la persona. */
@ExtendWith(MockitoExtension.class)
class CategoriasDeLaFormacionServiceTest {

    @Mock
    private LoadCategoriaHabitoPort loadPort;
    @Mock
    private ConsultarProgresoParticipanteHabitsPort progresoPort;
    @Mock
    private AlcanceDeFormacionFinder alcanceFinder;

    private final UserId persona = UserId.of(UUID.randomUUID());

    private CategoriasDeLaFormacionService servicio() {
        return new CategoriasDeLaFormacionService(loadPort, progresoPort, alcanceFinder);
    }

    private static CategoriaHabito categoria(String clave, Formacion formacion) {
        return CategoriaHabito.rehydrate(clave, formacion, clave, 1, null, true, Instant.EPOCH, Instant.EPOCH);
    }

    private void programaEn(Formacion formacion) {
        when(progresoPort.deParticipante(persona)).thenReturn(Optional.of(
                new ProgresoParticipanteHabits(5, "America/Lima", RolParticipante.TRAINEE, false, true, null, formacion, 90)));
    }

    @Test
    @DisplayName("un aprendiz del Diplomado recibe las categorías activas del Diplomado, y nada de la Escuela")
    void aprendizDelDiplomado() {
        programaEn(Formacion.DIPLOMADO);
        when(alcanceFinder.de(persona)).thenReturn(AlcanceDeFormacion.de(Formacion.DIPLOMADO));
        when(loadPort.activasDe(Formacion.DIPLOMADO)).thenReturn(List.of(categoria("DIPLOMADO_ESTUDIO", Formacion.DIPLOMADO)));

        CategoriasDeLaFormacion r = servicio().consultar(persona, null);

        assertThat(r.formacion()).contains(Formacion.DIPLOMADO);
        assertThat(r.categorias()).extracting(CategoriaHabito::clave).containsExactly("DIPLOMADO_ESTUDIO");
    }

    @Test
    @DisplayName("el personal que hace su programa en una formación recibe las de ESA aunque tenga activa otra")
    void personalConProgramaEnOtra() {
        programaEn(Formacion.ESCUELA);
        lenient().when(alcanceFinder.de(persona)).thenReturn(AlcanceDeFormacion.de(Formacion.DIPLOMADO));
        when(loadPort.activasDe(Formacion.ESCUELA)).thenReturn(List.of(categoria("CUERPO", Formacion.ESCUELA)));

        CategoriasDeLaFormacion r = servicio().consultar(persona, null);

        assertThat(r.formacion()).contains(Formacion.ESCUELA);
        assertThat(r.categorias()).extracting(CategoriaHabito::clave).containsExactly("CUERPO");
    }

    @Test
    @DisplayName("quien no hace programa usa la formación que tiene activa; sin ninguna, no hay categorías (no un error)")
    void sinPrograma() {
        when(progresoPort.deParticipante(persona)).thenReturn(Optional.empty());
        when(alcanceFinder.de(persona)).thenReturn(AlcanceDeFormacion.de(Formacion.CERTIFICACION));
        when(loadPort.activasDe(Formacion.CERTIFICACION)).thenReturn(List.of(categoria("CERTIFICACION_X", Formacion.CERTIFICACION)));

        assertThat(servicio().consultar(persona, null).categorias()).hasSize(1);

        when(alcanceFinder.de(persona)).thenReturn(AlcanceDeFormacion.ninguna());
        CategoriasDeLaFormacion vacia = servicio().consultar(persona, null);
        assertThat(vacia.formacion()).isEmpty();
        assertThat(vacia.categorias()).isEmpty();
    }

    @Test
    @DisplayName("quien ve las tres puede pedir una; cualquier otro que pida una ajena recibe un 404")
    void pedirOtraFormacion() {
        when(alcanceFinder.de(persona)).thenReturn(AlcanceDeFormacion.todas());
        when(loadPort.activasDe(Formacion.DIPLOMADO)).thenReturn(List.of());
        assertThat(servicio().consultar(persona, Formacion.DIPLOMADO).formacion()).contains(Formacion.DIPLOMADO);

        when(alcanceFinder.de(persona)).thenReturn(AlcanceDeFormacion.de(Formacion.ESCUELA));
        assertThatThrownBy(() -> servicio().consultar(persona, Formacion.DIPLOMADO)).isInstanceOf(ObjetoDeOtraFormacionException.class);
    }
}
