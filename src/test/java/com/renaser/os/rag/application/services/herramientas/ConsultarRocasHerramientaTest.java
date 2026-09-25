package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.ObjetivoDelMes;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.PlanDeManana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocaDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocaDelDia;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDeLaSemana;
import com.renaser.os.rag.application.ports.out.rocas.ConsultarRocasDelAprendizPort.RocasDelDia;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * {@code consultar_rocas}: la herramienta no calcula "hoy" ni la ventana — eso es de {@code rocks}
 * y esta probado en {@code RocasDelAprendizServiceTest} con un reloj a las 03:00 UTC. Aca se prueba
 * lo que SI decide: el argumento, la traduccion de fallos y el texto que ve el modelo.
 */
class ConsultarRocasHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final LocalDate HOY = LocalDate.of(2026, 9, 23);
    private static final PlanDeManana PLAN_PENDIENTE = new PlanDeManana(false, 0, true, LocalTime.of(18, 0), true);

    private final ConsultarRocasDelAprendizPort puerto = mock(ConsultarRocasDelAprendizPort.class);
    private final ConsultarRocasHerramienta herramienta = new ConsultarRocasHerramienta(puerto);

    @Test
    @DisplayName("sin alcance es hoy: rocas con franja, estado, evidencia, Pareto y el plan de manana")
    void hoyPorDefecto() {
        when(puerto.deHoy(APRENDIZ)).thenReturn(new RocasDelDia(HOY, List.of(
                new RocaDelDia("CUERPO", 1, "VERDE", "Correr 5 km", LocalTime.of(7, 0), LocalTime.of(8, 0), true,
                        false),
                new RocaDelDia("CUERPO", 2, "AMARILLA", "Estirar", null, null, false, true)), PLAN_PENDIENTE));

        var invocacion = InvocacionHerramienta.sinArgumentos(ConsultarRocasHerramienta.NOMBRE);
        String texto = texto(herramienta.ejecutar(APRENDIZ, invocacion));

        assertThat(herramienta.definicion().obligatoriosFaltantesEn(invocacion)).isEmpty();
        assertThat(texto).contains("Rocas de hoy (2026-09-23)")
                .contains("CUERPO #1 VERDE | Correr 5 km | inicio=07:00 fin=08:00 | estado=completada | evidencia=entregada")
                .contains("#2 AMARILLA | Estirar | sin hora fija | estado=pendiente | evidencia=pendiente | bloqueada=si")
                .contains("Plan de manana: todavia no esta creado")
                .contains("abre a las 18:00 (hora local) y ahora esta abierta")
                .contains("La app si le deja crear");
    }

    @Test
    @DisplayName("'mañana' con enie es manana; sin rocas lo dice y reporta el plan creado")
    void manana() {
        when(puerto.deManana(APRENDIZ)).thenReturn(new RocasDelDia(HOY.plusDays(1), List.of(),
                new PlanDeManana(false, 0, false, LocalTime.of(18, 0), false)));

        String texto = texto(herramienta.ejecutar(APRENDIZ, conAlcance("Mañana")));

        assertThat(texto).contains("Rocas de manana (2026-09-24)").contains("No tiene rocas planificadas para manana")
                .contains("ahora esta cerrada").contains("La app no le deja");
        verify(puerto).deManana(APRENDIZ);
    }

    @Test
    @DisplayName("semana: objetivos con obstaculo, contingencia y si se pueden editar")
    void semana() {
        when(puerto.deLaSemana(APRENDIZ)).thenReturn(new RocasDeLaSemana(4, LocalDate.of(2026, 9, 21),
                LocalDate.of(2026, 9, 27), List.of(new RocaDeLaSemana("TRABAJO", "Cerrar 2 ventas", "Poco tiempo",
                "Llamar a primera hora", false, true))));

        String texto = texto(herramienta.ejecutar(APRENDIZ, conAlcance("semana")));

        assertThat(texto).contains("semana 4 del programa (2026-09-21 al 2026-09-27)")
                .contains("TRABAJO | Cerrar 2 ventas | obstaculo=Poco tiempo | contingencia=Llamar a primera hora")
                .contains("editable=no").contains("revision_de_cierre=hecha");
    }

    @Test
    @DisplayName("mes: la cifra con su unidad donde va, y el motivo cuando no hay cifra")
    void mes() {
        when(puerto.delMes(APRENDIZ)).thenReturn(List.of(
                new ObjetivoDelMes("TRABAJO", 1, 30, null, new BigDecimal("1500.00"), "S/", true, false, null),
                new ObjetivoDelMes("CUERPO", 1, 30, null, new BigDecimal("78"), "kg", false, false, null),
                new ObjetivoDelMes("RELACIONES", 1, 30, null, null, null, false, false, "SIN_DATOS")));

        String texto = texto(herramienta.ejecutar(APRENDIZ, conAlcance("MES")));

        assertThat(texto).contains("TRABAJO | mes 1 (cierra el dia 30 del programa) | cifra=S/ 1500")
                .contains("cifra=78 kg").contains("sin cifra, motivo=SIN_DATOS");
    }

    @Test
    @DisplayName("un alcance que no existe es un fallo legible y no consulta nada")
    void alcanceInvalido() {
        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ, conAlcance("ayer"));

        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Fallo.class);
        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("hoy, manana, semana o mes");
        verifyNoInteractions(puerto);
    }

    @Test
    @DisplayName("sin programa de rocas o suspendido: fallo legible, sin el mensaje de la excepcion")
    void fallosTraducidos() {
        when(puerto.deHoy(APRENDIZ)).thenThrow(new NotAuthorizedException("Solo un aprendiz opera sus propias rocas"));
        when(puerto.deLaSemana(APRENDIZ)).thenThrow(new NoSuchElementException("Participante no encontrado"));

        var suspendido = herramienta.ejecutar(APRENDIZ, conAlcance("hoy"));
        var inexistente = herramienta.ejecutar(APRENDIZ, conAlcance("semana"));

        assertThat(((ResultadoHerramienta.Fallo) suspendido).motivo()).contains("suspendida")
                .doesNotContain("Solo un aprendiz");
        assertThat(((ResultadoHerramienta.Fallo) inexistente).motivo()).contains("programa activo");
    }

    private static InvocacionHerramienta conAlcance(String alcance) {
        return new InvocacionHerramienta(ConsultarRocasHerramienta.NOMBRE,
                Map.of(ConsultarRocasHerramienta.ARGUMENTO_ALCANCE, alcance));
    }

    private static String texto(ResultadoHerramienta resultado) {
        assertThat(resultado).isInstanceOf(ResultadoHerramienta.Exito.class);
        return ((ResultadoHerramienta.Exito) resultado).contenido();
    }
}
