package com.renaser.os.rocks.infrastructure.adapter.out.persistence.rocadiaria;

import com.renaser.os.rocks.domain.model.cierre.CierreDeAccion;
import com.renaser.os.rocks.domain.model.cierre.MotivoDeAccionPendiente;
import com.renaser.os.rocks.domain.model.cierre.PlanDeManana;
import com.renaser.os.rocks.domain.model.rocadiaria.RocaDiaria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.cumplida;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.pendiente;
import static com.renaser.os.rocks.domain.model.cierre.RocasDeCierreDePrueba.respuesta;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

/**
 * La respuesta de cierre en la capa de persistencia (spec 017), SIN base: el mapeo de ida y vuelta y, sobre todo, que guardarla
 * use el UPDATE dirigido a las cuatro columnas y NUNCA reescriba la fila entera (que pisaría {@code completada} y los puntos).
 */
class RocaDiariaCierrePersistenciaTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 28);
    private static final Instant AHORA = Instant.parse("2026-10-29T03:00:00Z");

    private final RocaDiariaPersistenceMapper mapper = new RocaDiariaPersistenceMapper();
    private final SpringDataRocaDiariaRepository repositorio = mock(SpringDataRocaDiariaRepository.class);
    private final RocaDiariaPersistenceAdapter adaptador = new RocaDiariaPersistenceAdapter(repositorio, mapper);

    private static RocaDiaria conRespuesta() {
        RocaDiaria roca = pendiente(HOY, 1, "Revisar las facturas", LocalTime.of(20, 30));
        roca.cerrarConRespuesta(respuesta(MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.OTRA_HORA, "Se alargó la reunión"), AHORA);
        return roca;
    }

    @Test
    @DisplayName("la respuesta de cierre va y vuelve por las cuatro columnas")
    void idaYVuelta() {
        RocaDiariaJpaEntity entidad = mapper.toEntity(conRespuesta());

        assertThat(entidad.getCierreMotivo()).isEqualTo("SIN_TIEMPO");
        assertThat(entidad.getCierreManana()).isEqualTo("OTRA_HORA");
        assertThat(entidad.getCierreNota()).isEqualTo("Se alargó la reunión");
        assertThat(entidad.getCierreEn()).isEqualTo(AHORA);
        assertThat(mapper.toDomain(entidad).cierre()).isEqualTo(new CierreDeAccion(
                respuesta(MotivoDeAccionPendiente.SIN_TIEMPO, PlanDeManana.OTRA_HORA, "Se alargó la reunión"), AHORA));
    }

    @Test
    @DisplayName("una roca sin respuesta (todas las filas de antes de V121) no trae cierre")
    void sinRespuesta() {
        RocaDiariaJpaEntity entidad = mapper.toEntity(pendiente(HOY, 1, "Algo", null));

        assertThat(entidad.getCierreMotivo()).isNull();
        assertThat(entidad.getCierreEn()).isNull();
        assertThat(mapper.toDomain(entidad).cierre()).isNull();
    }

    @Test
    @DisplayName("una fila con un valor roto no tumba la lectura de la accion: se lee sin respuesta")
    void valorRoto() {
        RocaDiariaJpaEntity entidad = mapper.toEntity(conRespuesta());
        entidad.setCierreMotivo("YA_NO_EXISTE");

        RocaDiaria leida = mapper.toDomain(entidad);

        assertThat(leida.cierre()).isNull();
        assertThat(leida.titulo()).isEqualTo("Revisar las facturas");
    }

    @Test
    @DisplayName("guardar el cierre escribe SOLO las cuatro columnas: UPDATE dirigido por roca, nunca saveAndFlush de la fila")
    void guardarSoloElCierre() {
        RocaDiaria con = conRespuesta();
        RocaDiaria sin = cumplida(HOY, 2, "Ya cumplida", null, 8);

        adaptador.guardarSoloElCierre(List.of(con, sin));

        verify(repositorio).guardarElCierre(con.id().value(), "SIN_TIEMPO", "OTRA_HORA", "Se alargó la reunión", AHORA);
        verify(repositorio).quitarElCierre(sin.id().value());
        verify(repositorio, never()).saveAndFlush(any());
        verify(repositorio, never()).saveAllAndFlush(any());
        verify(repositorio, never()).save(any());
        verifyNoMoreInteractions(repositorio);
    }

    @Test
    @DisplayName("sin nada que guardar no toca la base")
    void nadaQueGuardar() {
        adaptador.guardarSoloElCierre(List.of());
        verifyNoMoreInteractions(repositorio);
    }

    @Test
    @DisplayName("el UPDATE de la respuesta solo actualiza una accion pendiente (completada = false en el WHERE)")
    void soloSiSigueConPendiente() throws NoSuchMethodException {
        String consulta = SpringDataRocaDiariaRepository.class
                .getDeclaredMethod("guardarElCierre", java.util.UUID.class, String.class, String.class, String.class, Instant.class)
                .getAnnotation(org.springframework.data.jpa.repository.Query.class).value();

        assertThat(consulta).contains("r.completada = false").doesNotContain("r.completada = true").doesNotContain("puntosOtorgados");
    }
}
