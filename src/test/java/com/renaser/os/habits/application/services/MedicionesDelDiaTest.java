package com.renaser.os.habits.application.services;

import com.renaser.os.habits.application.politica.PoliticaKilometros;
import com.renaser.os.habits.application.ports.in.registro.ConsultarTracksDelDiaConCatalogoUseCase.MedicionDelTrack;
import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoDia;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.habits.domain.model.medicion.MedicionDiaria;
import com.renaser.os.habits.domain.model.medicion.UnidadMedicion;
import com.renaser.os.habits.domain.model.registro.RegistroHabito;
import com.renaser.os.habits.domain.model.registro.RegistroHabitoId;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** D-226: la agenda del día trae la unidad, el número del día y el total acumulado del hábito de km. */
class MedicionesDelDiaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 29);
    private final UserId ana = UserId.of(UUID.randomUUID());
    private final List<String> consultas = new ArrayList<>();

    private MedicionesDelDia mediciones(BigDecimal total) {
        return new MedicionesDelDia(List.of(new PoliticaKilometros()), (participantes, clave, hasta) -> {
            consultas.add(clave + "@" + hasta);
            return total == null ? Map.of() : Map.of(ana, total);
        });
    }

    private static Habito habito(String clave) {
        return Habito.rehydrate(HabitoId.of(UUID.randomUUID()), AmbitoHabito.SISTEMA, null, "H", null,
                TipoHabito.CHECKBOX, "CUERPO", null, clave, ExigenciaEvidencia.OPCIONAL, true, false, true, false,
                null, null, null, null, true, Instant.EPOCH, Instant.EPOCH);
    }

    private RegistroHabito registroDe(Habito habito) {
        return RegistroHabito.generar(RegistroHabitoId.of(UUID.randomUUID()), ana, habito.id(), HOY, 10,
                TipoDia.TODOS, true, Instant.EPOCH);
    }

    @Test
    @DisplayName("solo el hábito de km trae medición, con el total hasta hoy; los demás no, y sin consulta extra")
    void soloElDeKmTraeMedicion() {
        Habito km = habito(PoliticaKilometros.CLAVE_SISTEMA);
        Habito otro = habito("DAILY_CLASS");
        RegistroHabito deKm = registroDe(km);
        RegistroHabito delOtro = registroDe(otro);

        Map<RegistroHabito, MedicionDelTrack> resultado = mediciones(new BigDecimal("12.50"))
                .de(ana, List.of(deKm, delOtro), Map.of(km.id(), km, otro.id(), otro), HOY);

        assertThat(resultado).containsOnlyKeys(deKm);
        assertThat(resultado.get(deKm)).isEqualTo(
                new MedicionDelTrack(UnidadMedicion.KILOMETROS, null, new BigDecimal("12.50")));
        assertThat(consultas).containsExactly("DAILY_KM@" + HOY);
    }

    @Test
    @DisplayName("ya completado trae el número del día; sin km registrados el total es 0,00")
    void completadoTraeSuNumeroYSinHistoriaEsCero() {
        Habito km = habito(PoliticaKilometros.CLAVE_SISTEMA);
        RegistroHabito hecho = registroDe(km);
        hecho.completar(10, null, null, null, MedicionDiaria.manualSiHay(new BigDecimal("3.4")), Instant.EPOCH);

        MedicionDelTrack medicion = mediciones(null).de(ana, List.of(hecho), Map.of(km.id(), km), HOY).get(hecho);

        assertThat(medicion.valorDelDia()).isEqualByComparingTo("3.40");
        assertThat(medicion.total()).isEqualByComparingTo("0").hasScaleOf(2);
    }

    @Test
    @DisplayName("un día sin hábitos medibles no consulta la base")
    void sinMediblesNoConsulta() {
        Habito otro = habito(null);

        assertThat(mediciones(BigDecimal.TEN).de(ana, List.of(registroDe(otro)), Map.of(otro.id(), otro), HOY))
                .isEmpty();
        assertThat(consultas).isEmpty();
    }
}
