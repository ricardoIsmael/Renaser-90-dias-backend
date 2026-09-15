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
 * <b>Con que ORDEN le pide el padron a la base</b> (E-185, 2026-09-15).
 *
 * <p>Va aparte de {@link ConsultarResumenParticipacionPersistenceAdapterTest} a proposito: ese
 * levanta Spring y un Postgres de Testcontainers, y esta comprobacion no necesita ninguno de los
 * dos. Se queda sin contenedor para que el guard corra SIEMPRE, incluso cuando no hay runtime de
 * contenedores a mano.
 *
 * <p><b>Que cubre y que no.</b> Cubre el criterio de orden, que es la regla que se decidio. NO
 * cubre que el SQL sea valido ni que las etiquetas {@code ?n} esten bien numeradas: un doble del
 * {@code EntityManager} no valida nada de eso —ya paso, y por eso existe el comentario de
 * {@code FILTRO_APRENDICES}—. De eso se encarga
 * {@code ConsultarResumenParticipacionPersistenceAdapterTest#elPadronEmpiezaPorElAltaMasReciente},
 * que corre el mismo ORDER BY contra Postgres de verdad.
 */
class OrdenDelPadronTest {

    /** La hora no influye en el ORDER BY; se fija una cualquiera porque el puerto la exige. */
    private static final FixedClock RELOJ = FixedClock.at(Instant.parse("2026-09-15T02:00:00Z"));

    @Test
    @DisplayName("listarAprendices(): pide las altas mas recientes primero, con desempate deterministico")
    void elListadoDelPadronPideLasAltasMasRecientesPrimero() {
        String sql = sqlDelListado();

        String orden = sql.lines()
                .map(String::trim)
                .filter(linea -> linea.startsWith("ORDER BY"))
                .findFirst()
                .orElseThrow(() -> new AssertionError("El listado del padron no lleva ORDER BY:\n" + sql));

        // Lo que el dueno reporto como "no cargan los usuarios nuevos": el alta reciente tiene que
        // salir en la primera tanda, no donde la deje el abecedario.
        assertThat(orden)
                .as("la primera clave de orden es el alta mas reciente")
                .startsWith("ORDER BY u.creado_en DESC");
        assertThat(orden)
                .as("ordenar por nombre es justo lo que escondia a las altas nuevas")
                .doesNotContain("nombre_completo");
        // Sin segunda clave, dos paginas seguidas pueden repetir a alguien y saltearse a otro:
        // es la premisa de la que depende E16 en el repo frontend.
        assertThat(orden)
                .as("el desempate por clave primaria hace la paginacion estable")
                .endsWith("u.id");
    }

    /** Captura el SQL que el adaptador le manda al {@code EntityManager} para el listado. */
    private String sqlDelListado() {
        EntityManager entityManager = mock(EntityManager.class);
        Query query = mock(Query.class);
        when(entityManager.createNativeQuery(anyString())).thenReturn(query);
        when(query.setParameter(anyInt(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        new ConsultarResumenParticipacionPersistenceAdapter(entityManager, RELOJ)
                .listarAprendices(0, 20, null, false);

        ArgumentCaptor<String> capturado = ArgumentCaptor.forClass(String.class);
        org.mockito.Mockito.verify(entityManager).createNativeQuery(capturado.capture());
        return capturado.getValue();
    }
}
