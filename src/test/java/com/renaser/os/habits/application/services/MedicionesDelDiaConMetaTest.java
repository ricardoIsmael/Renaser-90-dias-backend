package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.MedicionDelTrack;
import com.renaser.os.habits.application.ports.out.medicion.MetaDeMedicionPort;
import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.medicion.DispositivoDeMedicion;
import com.renaser.os.habits.domain.model.medicion.LecturaAutomatica;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.medicion.OrigenMedicion;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Spec 037: la agenda de hoy trae la meta que rige y de dónde salió lo registrado ese día. */
class MedicionesDelDiaConMetaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 11, 4);
    private final UserId ana = UserId.of(UUID.randomUUID());

    private static MetaDeMedicionPort metas(BigDecimal fijada) {
        return new MetaDeMedicionPort() {
            @Override
            public Optional<BigDecimal> metaDe(HabitoId habitoId) {
                return Optional.ofNullable(fijada);
            }

            @Override
            public void fijar(HabitoId habitoId, BigDecimal meta, Instant ahora) {
                throw new UnsupportedOperationException();
            }
        };
    }

    private MedicionesDelDia mediciones(BigDecimal metaFijada) {
        return new MedicionesDelDia(List.of(new PoliticaKilometros()), (participantes, clave, hasta) -> Map.of(ana,
                new BigDecimal("10")), metas(metaFijada));
    }

    private static Habito km() {
        return Habito.rehydrate(HabitoId.of(UUID.randomUUID()), AmbitoHabito.SISTEMA, null, "KM", null,
                TipoHabito.CHECKBOX, "CUERPO", null, PoliticaKilometros.CLAVE_SISTEMA, ExigenciaEvidencia.OBLIGATORIA,
                true, false, true, false, null, null, null, null, true, Instant.EPOCH, Instant.EPOCH);
    }

    private RegistroHabito registro(Habito habito) {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), ana, habito.id(), HOY, 10, TipoDia.TODOS,
                true, Instant.EPOCH);
    }

    @Test
    @DisplayName("sin meta fijada trae los 5 km del programa; con una fijada por Administración, esa")
    void trae_la_meta_que_rige() {
        Habito km = km();
        RegistroHabito pendiente = registro(km);

        MedicionDelTrack porDefecto = mediciones(null).de(ana, List.of(pendiente), Map.of(km.id(), km), HOY).get(pendiente);
        MedicionDelTrack fijada = mediciones(new BigDecimal("3.50")).de(ana, List.of(pendiente), Map.of(km.id(), km), HOY)
                .get(pendiente);

        assertThat(porDefecto.meta()).isEqualByComparingTo("5");
        assertThat(fijada.meta()).isEqualByComparingTo("3.50");
        assertThat(porDefecto.origenDelDia()).isNull();
        assertThat(porDefecto.fuenteDelDia()).isNull();
    }

    @Test
    @DisplayName("lo cumplido por Health Connect trae su origen y su fuente; lo cumplido con la captura, solo el origen")
    void trae_el_origen_del_dia() {
        Habito km = km();
        RegistroHabito automatico = registro(km);
        automatico.completar(8, null, null, null, MedicionDiaria.automatica(new BigDecimal("5.4"),
                new LecturaAutomatica(7120, "Galaxy Watch", DispositivoDeMedicion.RELOJ, Instant.EPOCH, false)),
                Instant.EPOCH);
        RegistroHabito manual = registro(km);
        manual.completar(8, null, null, null, MedicionDiaria.manualSiHay(new BigDecimal("4")), Instant.EPOCH);

        MedicionDelTrack deHealthConnect = mediciones(null).de(ana, List.of(automatico), Map.of(km.id(), km), HOY)
                .get(automatico);
        MedicionDelTrack deLaCaptura = mediciones(null).de(ana, List.of(manual), Map.of(km.id(), km), HOY).get(manual);

        assertThat(deHealthConnect.origenDelDia()).isEqualTo(OrigenMedicion.HEALTH_CONNECT);
        assertThat(deHealthConnect.fuenteDelDia()).isEqualTo("Galaxy Watch");
        assertThat(deLaCaptura.origenDelDia()).isEqualTo(OrigenMedicion.MANUAL);
        assertThat(deLaCaptura.fuenteDelDia()).isNull();
    }
}
