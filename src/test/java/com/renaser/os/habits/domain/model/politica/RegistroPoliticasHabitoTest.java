package com.renaser.os.habits.domain.model.politica;

import com.renaser.os.habits.domain.model.habito.AmbitoHabito;
import com.renaser.os.habits.domain.model.habito.ExigenciaEvidencia;
import com.renaser.os.habits.domain.model.habito.Habito;
import com.renaser.os.habits.domain.model.habito.HabitoId;
import com.renaser.os.habits.domain.model.habito.TipoHabito;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegistroPoliticasHabitoTest {

    private static final Instant AHORA = Instant.parse("2026-08-26T10:00:00Z");

    private static Habito habito(TipoHabito tipo, String claveSistema) {
        return Habito.rehydrate(HabitoId.of(UUID.randomUUID()), AmbitoHabito.SISTEMA, null, "Habito", null, tipo, null,
                null, claveSistema, ExigenciaEvidencia.OPCIONAL, false, false, false, null, null, null, null, true,
                AHORA, AHORA);
    }

    private static Habito habitoPersonal(TipoHabito tipo) {
        return Habito.rehydrate(HabitoId.of(UUID.randomUUID()), AmbitoHabito.PERSONAL, UserId.of(UUID.randomUUID()),
                "Mio", null, tipo, null, null, null, ExigenciaEvidencia.OPCIONAL, false, false, false, null, null, null,
                null, true, AHORA, AHORA);
    }

    private static PoliticaHabito politica(SelectorHabito selector, DecisionPolitica decision) {
        return new PoliticaHabito() {
            @Override
            public SelectorHabito selector() {
                return selector;
            }

            @Override
            public DecisionPolitica puedeCompletarseDirecto(Habito habito) {
                return decision;
            }
        };
    }

    @Test
    void unHabitoSinReglaPropiaCaeEnLaGenericaYSiempreProcede() {
        var registro = new RegistroPoliticasHabito(List.of());

        var politica = registro.para(habito(TipoHabito.CHECKBOX, "CUALQUIERA"));

        assertThat(politica).isSameAs(RegistroPoliticasHabito.GENERICA);
        assertThat(politica.puedeCompletarseDirecto(habito(TipoHabito.CHECKBOX, null)))
                .isInstanceOf(DecisionPolitica.Procede.class);
    }

    @Test
    void resuelvePorClaveSistema() {
        var propia = politica(SelectorHabito.porClave("PASTILLA_RENACER"), DecisionPolitica.noProcede("por su gesto"));
        var registro = new RegistroPoliticasHabito(List.of(propia));

        assertThat(registro.para(habito(TipoHabito.JOURNALING, "PASTILLA_RENACER"))).isSameAs(propia);
        assertThat(registro.para(habito(TipoHabito.JOURNALING, "OTRA"))).isSameAs(RegistroPoliticasHabito.GENERICA);
    }

    @Test
    void resuelvePorTipoCuandoNoHayReglaParaEsaClave() {
        var porTipo = politica(SelectorHabito.porTipo(TipoHabito.BLOQUEO), DecisionPolitica.noProcede("santuario"));
        var registro = new RegistroPoliticasHabito(List.of(porTipo));

        assertThat(registro.para(habito(TipoHabito.BLOQUEO, "PHONE_FREE_DAY"))).isSameAs(porTipo);
        assertThat(registro.para(habito(TipoHabito.BLOQUEO, null))).isSameAs(porTipo);
    }

    /** La regla de UN habito puntual le gana a la regla de TODA una forma de habito. */
    @Test
    void laClaveSistemaLeGanaAlTipo() {
        var porTipo = politica(SelectorHabito.porTipo(TipoHabito.BLOQUEO), DecisionPolitica.noProcede("por tipo"));
        var porClave = politica(SelectorHabito.porClave("ESPECIAL"), DecisionPolitica.noProcede("por clave"));
        var registro = new RegistroPoliticasHabito(List.of(porTipo, porClave));

        assertThat(registro.para(habito(TipoHabito.BLOQUEO, "ESPECIAL"))).isSameAs(porClave);
    }

    /** Un habito que se invento un participante no puede arrastrar una regla del catalogo. */
    @Test
    void unHabitoPersonalNuncaResuelvePorClaveSistema() {
        var porClave = politica(SelectorHabito.porClave("PASTILLA_RENACER"), DecisionPolitica.noProcede("x"));
        var registro = new RegistroPoliticasHabito(List.of(porClave));

        assertThat(registro.para(habitoPersonal(TipoHabito.CHECKBOX))).isSameAs(RegistroPoliticasHabito.GENERICA);
    }

    @Test
    void dosPoliticasParaElMismoSelectorFallanAlArrancar() {
        var una = politica(SelectorHabito.porClave("DUPLICADA"), DecisionPolitica.procede());
        var otra = politica(SelectorHabito.porClave("DUPLICADA"), DecisionPolitica.procede());

        assertThatThrownBy(() -> new RegistroPoliticasHabito(List.of(una, otra)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DUPLICADA");
    }
}
