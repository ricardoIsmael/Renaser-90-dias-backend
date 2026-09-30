package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.FichaDeHabito;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * {@code consultar_como_se_hace_habito} (D-236): encuentra el habito como lo nombro la persona, con
 * sus dos nombres, y avisa que los pasos salen del material, no de la descripcion.
 */
class ConsultarComoSeHaceHabitoHerramientaTest {

    private static final UserId APRENDIZ = UserId.of(UUID.randomUUID());
    private static final UUID JUGO = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID RITUAL = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final UUID RITUAL_DOMINGO = UUID.fromString("33333333-3333-3333-3333-333333333333");
    private static final UUID CAMINAR = UUID.fromString("44444444-4444-4444-4444-444444444444");

    private final GestionarPlanDeHabitosPort planPort = mock(GestionarPlanDeHabitosPort.class);
    private final ConsultarComoSeHaceHabitoHerramienta herramienta = new ConsultarComoSeHaceHabitoHerramienta(planPort);

    private static List<FichaDeHabito> fichas() {
        return List.of(
                new FichaDeHabito(JUGO, "JUGO VERDE", "Batido de papaya", "Foto del vaso con Jugo Verde", true),
                new FichaDeHabito(RITUAL, "RITUAL TIERRA - AGUA - FUEGO (mañana)", null, "Foto/video del ritual",
                        false),
                new FichaDeHabito(RITUAL_DOMINGO, "RITUAL DE MAÑANA (domingo)", null, "Foto/video del ritual", false),
                new FichaDeHabito(CAMINAR, "Caminar", null, null, false));
    }

    private String exito(String habito) {
        when(planPort.fichasDe(APRENDIZ)).thenReturn(fichas());
        InvocacionHerramienta invocacion = habito == null
                ? InvocacionHerramienta.sinArgumentos(ConsultarComoSeHaceHabitoHerramienta.NOMBRE)
                : new InvocacionHerramienta(ConsultarComoSeHaceHabitoHerramienta.NOMBRE,
                        Map.of(ConsultarComoSeHaceHabitoHerramienta.ARGUMENTO_HABITO, habito));
        return ((ResultadoHerramienta.Exito) herramienta.ejecutar(APRENDIZ, invocacion)).contenido();
    }

    @Test
    @DisplayName("'el ritual de mañana' encuentra los dos rituales de mañana, sin tildes ni mayusculas, y no los demas")
    void ritualDeManana() {
        String texto = exito("el ritual de mañana");

        assertThat(texto).contains("habito_id=" + RITUAL + " | RITUAL TIERRA - AGUA - FUEGO (mañana) | descripcion: "
                        + "Foto/video del ritual | se_puede_renombrar=no")
                .contains("habito_id=" + RITUAL_DOMINGO)
                .doesNotContain(JUGO.toString()).doesNotContain(CAMINAR.toString())
                .contains(ConsultarComoSeHaceHabitoHerramienta.SOBRE_LOS_PASOS);
    }

    @Test
    @DisplayName("E-290: 'mi jugo verde' encuentra el renombrado por el nombre del programa, y trae los dos nombres")
    void renombradoPorSuNombreDelPrograma() {
        assertThat(exito("mi jugo verde")).contains("habito_id=" + JUGO + " | Batido de papaya (JUGO VERDE del "
                + "programa) | descripcion: Foto del vaso con Jugo Verde | se_puede_renombrar=si");
        assertThat(exito("batido")).contains("habito_id=" + JUGO);
    }

    @Test
    @DisplayName("sin descripcion lo dice; sin nombre util devuelve todos")
    void sinDescripcionYSinFiltro() {
        assertThat(exito("caminar")).contains("Caminar | descripcion: sin descripcion");
        assertThat(exito(null)).contains(JUGO.toString()).contains(RITUAL.toString()).contains(CAMINAR.toString());
    }

    @Test
    @DisplayName("un habito que no tiene no se cambia por otro: lo dice y nombra los suyos")
    void noLoTiene() {
        assertThat(exito("yoga")).startsWith("No tiene ningun habito que se llame 'yoga'")
                .contains("Batido de papaya, RITUAL TIERRA - AGUA - FUEGO (mañana)")
                .doesNotContain("habito_id=");
    }

    @Test
    @DisplayName("una cuenta suspendida recibe un fallo legible, nunca la excepcion")
    void suspendida() {
        when(planPort.fichasDe(APRENDIZ)).thenThrow(new NotAuthorizedException("Cuenta suspendida"));

        ResultadoHerramienta resultado = herramienta.ejecutar(APRENDIZ,
                InvocacionHerramienta.sinArgumentos(ConsultarComoSeHaceHabitoHerramienta.NOMBRE));

        assertThat(((ResultadoHerramienta.Fallo) resultado).motivo()).contains("suspendida");
    }
}
