package com.renaser.os.onboarding.infrastructure.adapter.out.persistence.cuestionario;

import com.renaser.os.TestcontainersConfiguration;
import com.renaser.os.onboarding.domain.model.cuestionario.OpcionPregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Pregunta;
import com.renaser.os.onboarding.domain.model.cuestionario.Seccion;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
@Transactional
@DirtiesContext
class CuestionarioPersistenceAdapterTest {

    @Autowired
    private CuestionarioPersistenceAdapter adapter;

    @Autowired
    private EntityManager entityManager;

    private short insertarSeccion(String flujo, String clave, short orden) {
        return ((Number) entityManager.createNativeQuery("""
                        INSERT INTO renaser.secciones_onboarding (flujo, clave_seccion, titulo, orden)
                        VALUES (:flujo, :clave, :titulo, :orden) RETURNING id
                        """)
                .setParameter("flujo", flujo)
                .setParameter("clave", clave)
                .setParameter("titulo", "Titulo " + clave)
                .setParameter("orden", orden)
                .getSingleResult()).shortValue();
    }

    private int insertarPregunta(short seccionId, String clave, String tipo, short orden) {
        return ((Number) entityManager.createNativeQuery("""
                        INSERT INTO renaser.preguntas_onboarding (seccion_id, clave_pregunta, texto, tipo, orden)
                        VALUES (:seccionId, :clave, :texto, CAST(:tipo AS renaser.tipo_pregunta_onboarding), :orden)
                        RETURNING id
                        """)
                .setParameter("seccionId", seccionId)
                .setParameter("clave", clave)
                .setParameter("texto", "Texto " + clave)
                .setParameter("tipo", tipo)
                .setParameter("orden", orden)
                .getSingleResult()).intValue();
    }

    private void insertarOpcion(int preguntaId, short orden, String valor, String etiqueta) {
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.opciones_pregunta (pregunta_id, orden, valor, etiqueta)
                        VALUES (:preguntaId, :orden, :valor, :etiqueta)
                        """)
                .setParameter("preguntaId", preguntaId)
                .setParameter("orden", orden)
                .setParameter("valor", valor)
                .setParameter("etiqueta", etiqueta)
                .executeUpdate();
    }

    @Test
    @DisplayName("seccionesDeFlujo(): ordenadas por orden, solo del flujo pedido")
    void seccionesDeFlujoOrdenadasYFiltradas() {
        insertarSeccion("v90", "seccion-b", (short) 1);
        insertarSeccion("v90", "seccion-a", (short) 0);
        insertarSeccion("otro-flujo", "seccion-x", (short) 0);

        List<Seccion> secciones = adapter.seccionesDeFlujo("v90");

        assertThat(secciones).hasSize(2);
        assertThat(secciones.get(0).claveSeccion()).isEqualTo("seccion-a");
        assertThat(secciones.get(1).claveSeccion()).isEqualTo("seccion-b");
    }

    @Test
    @DisplayName("preguntasDeSeccion() + opcionesDePregunta(): ordenadas por orden")
    void preguntasYOpcionesOrdenadas() {
        short seccionId = insertarSeccion("v90", "seccion-1", (short) 0);
        int p2 = insertarPregunta(seccionId, "clave-2", "TEXTO", (short) 1);
        int p1 = insertarPregunta(seccionId, "clave-1", "SELECCION_UNICA", (short) 0);
        insertarOpcion(p1, (short) 1, "b", "Opcion B");
        insertarOpcion(p1, (short) 0, "a", "Opcion A");

        List<Pregunta> preguntas = adapter.preguntasDeSeccion(seccionId);
        assertThat(preguntas).extracting(Pregunta::clavePregunta).containsExactly("clave-1", "clave-2");

        List<OpcionPregunta> opciones = adapter.opcionesDePregunta(p1);
        assertThat(opciones).extracting(OpcionPregunta::valor).containsExactly("a", "b");
    }

    @Test
    @DisplayName("porId(): encuentra por id, vacio si no existe")
    void porIdEncuentraOVacio() {
        short seccionId = insertarSeccion("v90", "seccion-1", (short) 0);
        int preguntaId = insertarPregunta(seccionId, "clave-1", "NUMERO", (short) 0);

        assertThat(adapter.porId(preguntaId)).isPresent();
        assertThat(adapter.porId(preguntaId).get().tipo()).isEqualTo(TipoPreguntaOnboarding.NUMERO);
        assertThat(adapter.porId(999_999)).isEmpty();
    }

    @ParameterizedTest
    @EnumSource(TipoPreguntaOnboarding.class)
    @DisplayName("los 11 valores de tipo_pregunta_onboarding hacen roundtrip identico")
    void todosLosTiposHacenRoundtrip(TipoPreguntaOnboarding tipo) {
        short seccionId = insertarSeccion("v90-" + tipo, "seccion-" + tipo, (short) 0);
        int preguntaId = insertarPregunta(seccionId, "clave-" + tipo, tipo.name(), (short) 0);

        assertThat(adapter.porId(preguntaId)).get().extracting(Pregunta::tipo).isEqualTo(tipo);
    }

    @Test
    @DisplayName("esCondicional(): true cuando pregunta_padre_id no es null")
    void preguntaCondicionalSeReflejaEnElDominio() {
        short seccionId = insertarSeccion("v90", "seccion-1", (short) 0);
        int padreId = insertarPregunta(seccionId, "padre", "CASILLA", (short) 0);
        entityManager.createNativeQuery("""
                        INSERT INTO renaser.preguntas_onboarding (seccion_id, clave_pregunta, texto, tipo, orden, pregunta_padre_id)
                        VALUES (:seccionId, 'hijo', 'texto hijo', CAST('TEXTO' AS renaser.tipo_pregunta_onboarding), 1, :padreId)
                        """)
                .setParameter("seccionId", seccionId)
                .setParameter("padreId", padreId)
                .executeUpdate();

        List<Pregunta> preguntas = adapter.preguntasDeSeccion(seccionId);
        Pregunta hijo = preguntas.stream().filter(p -> p.clavePregunta().equals("hijo")).findFirst().orElseThrow();

        assertThat(hijo.esCondicional()).isTrue();
        assertThat(hijo.preguntaPadreId()).isEqualTo(padreId);
    }
}
