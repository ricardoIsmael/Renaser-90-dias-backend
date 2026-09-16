package com.renaser.os.users.infrastructure.adapter.out.persistence.participante;

import com.renaser.os.shared.domain.FixedClock;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * <b>A QUIENES le pide el padron a la base</b> (E-191, 2026-09-16).
 *
 * <p>La pantalla "Personas" mostraba solo aprendices porque el WHERE compartido arrancaba con
 * {@code u.rol = 'APRENDIZ'}: las 7 cuentas de staff no aparecian nunca. El dueno lo pidio al
 * reves — "tambien los mentores hacen el recorrido".
 *
 * <p><b>Y a quienes NO.</b> El filtro de rol no se borro: se mudo adentro de la rama de
 * {@code soloSinGrupo}, que es la cola operativa "a quien hay que ubicar en un grupo". Si esa rama
 * empezara a contar staff, el numero del panel de inicio no bajaria nunca — a un miembro de staff
 * no se le puede asignar celula ({@code ComposicionDeCelulaService.requireAprendizElegible}). Las
 * dos mitades de la decision se fijan aca juntas a proposito: separadas, la proxima persona puede
 * "simplificar" una sin ver la otra.
 *
 * <p>Va aparte de {@link ConsultarResumenParticipacionPersistenceAdapterTest} por la misma razon
 * que {@link OrdenDelPadronTest}: ese levanta Spring y un Postgres de Testcontainers, y este guard
 * tiene que correr SIEMPRE, haya o no runtime de contenedores. <b>Lo que este test NO puede ver</b>
 * es si el SQL es valido y si las etiquetas {@code ?n} estan bien numeradas —un doble del
 * {@code EntityManager} no valida nada de eso, ya paso una vez— de eso se encarga el otro contra
 * Postgres de verdad.
 */
class AlcanceDelPadronTest {

    /** La hora no influye en el WHERE; se fija una cualquiera porque el puerto exige un reloj. */
    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-09-16T02:00:00Z"));

    @Test
    @DisplayName("el listado no recorta por rol: en Personas tienen que salir los cinco")
    void elPadronNoRecortaPorRol() {
        String sql = sqlDelListado();

        assertThat(filtrosDeRolDe(sql))
                .as("todo filtro por rol tiene que estar dentro de la rama de ?2 (solo sin grupo), "
                        + "y no aplicarse siempre — si no, el staff no aparece nunca:\n" + sql)
                .allSatisfy(linea -> assertThat(linea).contains("?2"));
    }

    @Test
    @DisplayName("la fila trae el rol: con los cinco roles mezclados ya no se deduce de la lista")
    void cadaFilaDiceDeQueRolEs() {
        String sql = sqlDelListado();
        String proyeccion = sql.substring(0, sql.indexOf("FROM renaser.usuarios"));

        assertThat(proyeccion)
                .as("sin u.rol en el SELECT la pantalla no puede distinguir un mentor de un aprendiz:\n" + sql)
                .contains("u.rol");
    }

    @Test
    @DisplayName("la cola 'sin grupo' sigue siendo SOLO de aprendices, o el contador no baja nunca")
    void laColaDeSinGrupoSigueSiendoDeAprendices() {
        for (String sql : List.of(sqlDelListado(), sqlDelConteo())) {
            assertThat(filtrosDeRolDe(sql))
                    .as("la rama de ?2 tiene que seguir exigiendo APRENDIZ:\n" + sql)
                    .isNotEmpty()
                    .allSatisfy(linea -> assertThat(linea).contains("APRENDIZ").contains("?2"));
        }
    }

    /** Las lineas del SQL que comparan el rol contra un valor — el SELECT de {@code u.rol} no cuenta. */
    private static List<String> filtrosDeRolDe(String sql) {
        return sql.lines().map(String::trim).filter(linea -> linea.contains("u.rol =")).toList();
    }

    private String sqlDelListado() {
        return sqlCapturado(adaptador -> adaptador.listarAprendices(0, 20, null, false));
    }

    private String sqlDelConteo() {
        return sqlCapturado(adaptador -> adaptador.contarAprendices(null, true));
    }

    /** Captura el SQL que el adaptador le manda al {@code EntityManager}. */
    private String sqlCapturado(java.util.function.Consumer<ConsultarResumenParticipacionPersistenceAdapter> uso) {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());
        when(query.getSingleResult()).thenReturn(0L);

        uso.accept(new ConsultarResumenParticipacionPersistenceAdapter(entityManager, RELOJ));

        ArgumentCaptor<String> capturado = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(entityManager).createNativeQuery(capturado.capture());
        return capturado.getValue();
    }
}
