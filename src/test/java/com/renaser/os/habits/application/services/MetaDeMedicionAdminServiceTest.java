package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.application.ports.in.medicion.MetaDeMedicionAdminUseCases.FijarMetaCommand;
import com.renaser.os.habits.application.ports.in.medicion.MetaDeMedicionAdminUseCases.MetaDeMedicion;
import com.renaser.os.habits.application.ports.out.habito.LoadHabitoPort;
import com.renaser.os.habits.application.ports.out.medicion.MetaDeMedicionPort;
import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.medicion.UnidadMedicion;
import com.renaser.os.shared.domain.FixedClock;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Spec 037: Administración ve y cambia la meta diaria del hábito de kilómetros. */
@ExtendWith(MockitoExtension.class)
class MetaDeMedicionAdminServiceTest {

    private static final Instant AHORA = Instant.parse("2026-11-04T15:00:00Z");

    @Mock
    private LoadHabitoPort loadHabitoPort;
    @Mock
    private MetaDeMedicionPort metaPort;
    @Mock
    private HabitoAdminGuard guard;

    private MetaDeMedicionAdminService service;
    private final UserId admin = UserId.of(UUID.randomUUID());
    private Habito km;

    @BeforeEach
    void preparar() {
        service = new MetaDeMedicionAdminService(loadHabitoPort, metaPort, List.of(new PoliticaKilometros()), guard,
                FixedClock.at(AHORA));
        km = habito(PoliticaKilometros.CLAVE_SISTEMA);
    }

    private static Habito habito(String clave) {
        return Habito.rehydrate(HabitoId.of(UUID.randomUUID()), AmbitoHabito.SISTEMA, null, "H", null,
                TipoHabito.CHECKBOX, "CUERPO", null, clave, ExigenciaEvidencia.OBLIGATORIA, true, false, true, false,
                null, null, null, null, true, Instant.EPOCH, Instant.EPOCH);
    }

    @Test
    @DisplayName("sin meta fijada rige la del programa (5 km) y lo dice")
    void sinMetaFijada() {
        when(loadHabitoPort.byId(km.id())).thenReturn(Optional.of(km));
        when(metaPort.metaDe(km.id())).thenReturn(Optional.empty());

        MetaDeMedicion vista = service.consultar(admin, km.id());

        assertThat(vista.unidad()).isEqualTo(UnidadMedicion.KILOMETROS);
        assertThat(vista.meta()).isEqualByComparingTo("5");
        assertThat(vista.porDefecto()).isEqualByComparingTo("5");
        assertThat(vista.esLaPorDefecto()).isTrue();
        assertThat(vista.minimo()).isEqualByComparingTo("0.5");
        assertThat(vista.maximo()).isEqualByComparingTo("50");
    }

    @Test
    @DisplayName("fijar una meta la guarda con dos decimales y la devuelve como la que rige")
    void fijaLaMeta() {
        when(loadHabitoPort.byId(km.id())).thenReturn(Optional.of(km));
        when(metaPort.metaDe(km.id())).thenReturn(Optional.of(new BigDecimal("6.50")));

        MetaDeMedicion vista = service.fijar(new FijarMetaCommand(admin, km.id(), new BigDecimal("6.5")));

        verify(metaPort).fijar(eq(km.id()), eq(new BigDecimal("6.50")), eq(AHORA));
        assertThat(vista.meta()).isEqualByComparingTo("6.5");
        assertThat(vista.esLaPorDefecto()).isFalse();
    }

    @Test
    @DisplayName("una meta nula vuelve a la del programa")
    void volverALaDelPrograma() {
        when(loadHabitoPort.byId(km.id())).thenReturn(Optional.of(km));
        when(metaPort.metaDe(km.id())).thenReturn(Optional.empty());

        MetaDeMedicion vista = service.fijar(new FijarMetaCommand(admin, km.id(), null));

        verify(metaPort).fijar(km.id(), null, AHORA);
        assertThat(vista.esLaPorDefecto()).isTrue();
    }

    @Test
    @DisplayName("una meta fuera de 0,5 a 50 km es un 400 que dice el rango, y no guarda nada")
    void fueraDeRango() {
        when(loadHabitoPort.byId(km.id())).thenReturn(Optional.of(km));

        assertThatThrownBy(() -> service.fijar(new FijarMetaCommand(admin, km.id(), new BigDecimal("0.2"))))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("entre 0,5 y 50");
        assertThatThrownBy(() -> service.fijar(new FijarMetaCommand(admin, km.id(), new BigDecimal("80"))))
                .isInstanceOf(IllegalArgumentException.class);
        verify(metaPort, never()).fijar(any(), any(), any());
    }

    @Test
    @DisplayName("un hábito que no mide nada, o que no existe, es el mismo 404")
    void sinMetaEsNoEncontrado() {
        Habito otro = habito("DAILY_CLASS");
        Habito propio = habito(null);
        when(loadHabitoPort.byId(otro.id())).thenReturn(Optional.of(otro));
        when(loadHabitoPort.byId(propio.id())).thenReturn(Optional.of(propio));
        HabitoId fantasma = HabitoId.of(UUID.randomUUID());
        when(loadHabitoPort.byId(fantasma)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.consultar(admin, otro.id())).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.consultar(admin, propio.id())).isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> service.fijar(new FijarMetaCommand(admin, fantasma, BigDecimal.ONE)))
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("quien no administra el catálogo no la ve ni la cambia")
    void soloAdministracion() {
        doThrow(new NotAuthorizedException("no")).when(guard).requireAdmin(admin);

        assertThatThrownBy(() -> service.consultar(admin, km.id())).isInstanceOf(NotAuthorizedException.class);
        assertThatThrownBy(() -> service.fijar(new FijarMetaCommand(admin, km.id(), BigDecimal.ONE)))
                .isInstanceOf(NotAuthorizedException.class);
        verify(metaPort, never()).fijar(any(), any(), any());
    }
}
