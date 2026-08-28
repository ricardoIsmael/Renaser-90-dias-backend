package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.domain.model.habito.DetallesHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.PlantillaHabitoPersonal;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MisHabitosServiceTest {

    private static final Instant AHORA = Instant.parse("2026-08-28T10:00:00Z");

    @Mock
    private LoadHabitoPort loadPort;

    private final UserId actor = UserId.of(UUID.randomUUID());

    @Test
    void combinaCatalogoActivoConLosPersonalesDelActor() {
        Habito sistema = Habito.crearDeSistema("Meditar", TipoHabito.CHECKBOX,
                new DetallesHabito("desc", "MENTE", ExigenciaEvidencia.OPCIONAL, false, false), AHORA);
        Habito personal = Habito.crearPersonal(actor, "Mi reto", TipoHabito.CHECKBOX, "CUERPO",
                PlantillaHabitoPersonal.OTRO, "etiqueta", AHORA);
        when(loadPort.catalogoActivo()).thenReturn(List.of(sistema));
        when(loadPort.personalesActivosDe(actor)).thenReturn(List.of(personal));

        var service = new MisHabitosService(loadPort);
        List<Habito> resultado = service.consultar(actor);

        assertThat(resultado).containsExactlyInAnyOrder(sistema, personal);
    }

    @Test
    void sinHabitosPersonalesDevuelveSoloElCatalogo() {
        Habito sistema = Habito.crearDeSistema("Meditar", TipoHabito.CHECKBOX,
                new DetallesHabito("desc", "MENTE", ExigenciaEvidencia.OPCIONAL, false, false), AHORA);
        when(loadPort.catalogoActivo()).thenReturn(List.of(sistema));
        when(loadPort.personalesActivosDe(actor)).thenReturn(List.of());

        var service = new MisHabitosService(loadPort);

        assertThat(service.consultar(actor)).containsExactly(sistema);
    }
}
