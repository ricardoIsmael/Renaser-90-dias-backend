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

    /**
     * Fase 2 (D-153): al prender la confirmacion con botones cambia la descripcion de la que
     * escribe y NADA mas — el nombre y el parametro viajan en conversaciones vivas.
     */
    @Test
    @DisplayName("la variante con confirmacion solo cambia la descripcion de marcar_habito_completado")
    void varianteConConfirmacion() {
        var normales = CatalogoHerramientasAgente.definiciones();
        var conConfirmacion = CatalogoHerramientasAgente.definicionesConConfirmacion();

        assertThat(conConfirmacion).extracting(DefinicionHerramienta::nombre)
                .containsExactlyElementsOf(normales.stream().map(DefinicionHerramienta::nombre).toList());
        assertThat(conConfirmacion).extracting(DefinicionHerramienta::parametros)
                .containsExactlyElementsOf(normales.stream().map(DefinicionHerramienta::parametros).toList());
        assertThat(conConfirmacion.subList(0, 2)).isEqualTo(normales.subList(0, 2));
        assertThat(conConfirmacion.get(2).descripcion()).isNotEqualTo(normales.get(2).descripcion())
                .startsWith("Propone").contains("Confirmar");
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
