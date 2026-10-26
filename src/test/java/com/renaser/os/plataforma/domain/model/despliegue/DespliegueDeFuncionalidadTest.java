package com.renaser.os.plataforma.domain.model.despliegue;

import com.renaser.os.plataforma.domain.model.catalogo.CatalogoDeFuncionalidades;
import com.renaser.os.plataforma.domain.model.catalogo.DefinicionDeFuncionalidad;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Quién está dentro de un despliegue (spec 002, US4; contrato fase 2 §3.2). Sin Spring. */
class DespliegueDeFuncionalidadTest {

    private static final Instant AHORA = Instant.parse("2026-10-14T04:30:00Z");
    private static final UserId SOPORTE = UserId.of(UUID.randomUUID());
    private static final List<UserId> MIL_PERSONAS = IntStream.range(0, 1000)
            .mapToObj(i -> UserId.of(UUID.nameUUIDFromBytes(("persona-" + i).getBytes()))).toList();

    @Test
    @DisplayName("1000 personas × 10 lecturas al 25 %: nadie alterna entre verla y no verla (SC-004)")
    void sinOscilaciones() {
        DespliegueDeFuncionalidad despliegue = alPorcentaje("eventos", 25);
        for (UserId persona : MIL_PERSONAS) {
            boolean primera = despliegue.incluye(persona);
            for (int lectura = 0; lectura < 10; lectura++) {
                assertThat(despliegue.incluye(persona)).isEqualTo(primera);
            }
        }
    }

    @Test
    @DisplayName("subir 5 → 25 → 50 → 99 no saca a nadie: los grupos crecen, no se rearman (US4, escenario 2)")
    void subirNoSacaANadie() {
        Set<UserId> antes = dentro(alPorcentaje("eventos", 0));
        for (int porcentaje : new int[]{5, 25, 50, 99}) {
            Set<UserId> ahora = dentro(alPorcentaje("eventos", porcentaje));
            assertThat(ahora).as("al %d %% siguen todos los de antes", porcentaje).containsAll(antes);
            antes = ahora;
        }
        assertThat(antes.size()).as("al 99 %% casi todos").isGreaterThan(950);
    }

    @Test
    @DisplayName("bajar de 50 a 25 saca solo a quienes estaban entre 25 y 50")
    void bajarSacaALasCubetasAltas() {
        Set<UserId> al50 = dentro(alPorcentaje("eventos", 50));
        Set<UserId> al25 = dentro(alPorcentaje("eventos", 25));

        assertThat(al50).containsAll(al25);
        assertThat(al50.stream().filter(persona -> !al25.contains(persona)))
                .allSatisfy(persona -> assertThat(Cubeta.de("eventos", persona)).isBetween(25, 49));
    }

    @Test
    @DisplayName("una probadora la ve con 0 %; nadie más (US4, escenario 3)")
    void probadoraConCero() {
        UserId probadora = MIL_PERSONAS.getFirst();
        DespliegueDeFuncionalidad despliegue = alPorcentaje("eventos", 0).conProbadora(probadora);

        assertThat(despliegue.incluye(probadora)).isTrue();
        assertThat(dentro(despliegue)).containsExactly(probadora);
        assertThat(despliegue.sinProbadora(probadora).incluye(probadora)).isFalse();
    }

    @Test
    @DisplayName("el 100 % no es un escalón: es «prender para todos» y deja de haber despliegue")
    void cienNoEsUnEscalon() {
        assertThatThrownBy(() -> alPorcentaje("eventos", 100)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> alPorcentaje("eventos", -1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("cambiar de escalón conserva cuándo empezó y los probadores")
    void cambiarDeEscalon() {
        UserId probadora = MIL_PERSONAS.getFirst();
        DespliegueDeFuncionalidad despliegue = alPorcentaje("eventos", 5).conProbadora(probadora);

        DespliegueDeFuncionalidad nuevo = despliegue.conPorcentaje(25, 9, SOPORTE, AHORA.plusSeconds(60));

        assertThat(nuevo.iniciadoEn()).isEqualTo(AHORA);
        assertThat(nuevo.actualizadoEn()).isEqualTo(AHORA.plusSeconds(60));
        assertThat(nuevo.probadores()).containsExactly(probadora);
        assertThat(nuevo.version()).isEqualTo(9);
    }

    @Test
    @DisplayName("la foto de la auditoría: {despliegue: {porcentaje, probadores}} o {despliegue: null}")
    void fotoDeLaAuditoria() {
        DespliegueDeFuncionalidad despliegue = alPorcentaje("eventos", 25).conProbadora(MIL_PERSONAS.getFirst());

        assertThat(despliegue.paraAuditoria()).isEqualTo(Map.of("despliegue", Map.of("porcentaje", 25,
                "probadores", 1)));
        assertThat(DespliegueDeFuncionalidad.sinDespliegueParaAuditoria()).containsKey("despliegue")
                .containsEntry("despliegue", null);
    }

    @Test
    @DisplayName("admite despliegue solo lo que se usa con cuenta: lo que en el catálogo no tiene roles (registro) no")
    void admiteSoloConCuenta() {
        List<String> sinCuenta = CatalogoDeFuncionalidades.inicial().todas().stream()
                .filter(definicion -> !DespliegueDeFuncionalidad.admite(definicion))
                .map(DefinicionDeFuncionalidad::clave).toList();

        assertThat(sinCuenta).as("sale de los roles del catálogo, no de un nombre fijo").containsExactly("registro");
        assertThat(CatalogoDeFuncionalidades.inicial().definicion("chat.grupos").map(DespliegueDeFuncionalidad::admite))
                .contains(true);
    }

    private static DespliegueDeFuncionalidad alPorcentaje(String clave, int porcentaje) {
        return DespliegueDeFuncionalidad.empezar(clave, porcentaje, 1, SOPORTE, AHORA);
    }

    private static Set<UserId> dentro(DespliegueDeFuncionalidad despliegue) {
        return MIL_PERSONAS.stream().filter(despliegue::incluye).collect(Collectors.toSet());
    }
}
