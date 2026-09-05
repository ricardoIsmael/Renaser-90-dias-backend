package com.renaser.os.rag.domain.model.herramienta;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * El catalogo es un contrato con el modelo: los nombres viajan dentro de conversaciones vivas y
 * cambiarlos rompe una charla a mitad de camino. Estos casos los fijan.
 */
class CatalogoHerramientasAgenteTest {

    @Test
    @DisplayName("las tres herramientas existen con nombres estables y descripcion no vacia")
    void lasTresHerramientas() {
        assertThat(CatalogoHerramientasAgente.definiciones())
                .extracting(DefinicionHerramienta::nombre)
                .containsExactly("consultar_habitos_del_dia", "consultar_puntos_en_juego",
                        "marcar_habito_completado");
        assertThat(CatalogoHerramientasAgente.definiciones())
                .allSatisfy(definicion -> assertThat(definicion.descripcion()).isNotBlank());
    }

    @Test
    @DisplayName("solo marcar_habito_completado pide argumentos, y el suyo es obligatorio")
    void soloLaQueEscribePideArgumentos() {
        assertThat(CatalogoHerramientasAgente.porNombre(CatalogoHerramientasAgente.CONSULTAR_HABITOS_DEL_DIA))
                .get().extracting(DefinicionHerramienta::parametros).asInstanceOf(
                        org.assertj.core.api.InstanceOfAssertFactories.LIST).isEmpty();

        DefinicionHerramienta marcar = CatalogoHerramientasAgente
                .porNombre(CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO).orElseThrow();
        assertThat(marcar.parametros()).singleElement()
                .satisfies(parametro -> {
                    assertThat(parametro.nombre()).isEqualTo(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID);
                    assertThat(parametro.obligatorio()).isTrue();
                    assertThat(parametro.tipo()).isEqualTo(TipoParametroHerramienta.IDENTIFICADOR);
                });
    }

    @Test
    @DisplayName("una herramienta que el modelo se invento no esta en el catalogo")
    void herramientaInventada() {
        assertThat(CatalogoHerramientasAgente.porNombre("borrar_la_cuenta")).isEmpty();
    }

    @Test
    @DisplayName("detecta los obligatorios que faltan, incluido el que viene en blanco")
    void detectaObligatoriosFaltantes() {
        DefinicionHerramienta marcar = CatalogoHerramientasAgente
                .porNombre(CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO).orElseThrow();

        assertThat(marcar.obligatoriosFaltantesEn(
                InvocacionHerramienta.sinArgumentos(CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO)))
                .containsExactly(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID);

        assertThat(marcar.obligatoriosFaltantesEn(new InvocacionHerramienta(
                CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO,
                Map.of(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID, "   "))))
                .containsExactly(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID);

        assertThat(marcar.obligatoriosFaltantesEn(new InvocacionHerramienta(
                CatalogoHerramientasAgente.MARCAR_HABITO_COMPLETADO,
                Map.of(CatalogoHerramientasAgente.ARGUMENTO_REGISTRO_ID, "un-valor"))))
                .isEmpty();
    }
}
