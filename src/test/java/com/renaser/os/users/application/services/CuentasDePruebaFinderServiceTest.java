package com.renaser.os.users.application.services;

import com.renaser.os.shared.domain.UserId;
import com.renaser.os.users.application.ports.out.user.LoadUserPort;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/** La marca {@code es_cuenta_de_prueba} (V117) decide; los nombres de la spec 005 responden lo mismo que los de D-273. */
class CuentasDePruebaFinderServiceTest {

    private final LoadUserPort loadUserPort = mock(LoadUserPort.class);
    private final CuentasDePruebaFinderService finder = new CuentasDePruebaFinderService(loadUserPort);

    @Test
    @DisplayName("de unos ids devuelve solo los de cuentas de prueba, en lote o de a una, con los dos nombres")
    void soloLasMarcadas() {
        UserId ana = UserId.of(UUID.randomUUID());
        UserId prueba = UserId.of(UUID.randomUUID());
        when(loadUserPort.cuentasDePruebaEntre(any())).thenAnswer(inv -> {
            Set<UserId> marcadas = new HashSet<>(inv.getArgument(0));
            marcadas.retainAll(Set.of(prueba));
            return marcadas;
        });

        assertThat(finder.dePruebaEntre(List.of(ana, prueba))).containsExactly(prueba);
        assertThat(finder.entre(List.of(ana, prueba))).containsExactly(prueba);
        assertThat(finder.esDePrueba(prueba)).isTrue();
        assertThat(finder.esCuentaDePrueba(ana)).isFalse();
    }

    @Test
    @DisplayName("sin ids no consulta la base")
    void sinIdsNoConsulta() {
        assertThat(finder.dePruebaEntre(List.of())).isEmpty();
        verifyNoInteractions(loadUserPort);
    }
}
