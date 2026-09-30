package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort;
import com.renaser.os.rag.application.ports.out.plan.GestionarPlanDeHabitosPort.FichaDeHabito;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.NotAuthorizedException;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Collectors;

/**
 * {@code consultar_como_se_hace_habito} (D-236, pedido del dueño el 2026-09-30: «si una persona le
 * pide "dame como hacer el ritual", ¿tambien le puede ayudar?»). Devuelve, de los habitos de la
 * persona que coinciden con como lo nombro, sus dos nombres y la descripcion del programa.
 *
 * <p>Los pasos NO salen de aca: la descripcion del catalogo casi siempre dice solo que evidencia se
 * manda ("Foto/video del ritual"). Lo que ensena como se hace es el material del programa, que ya
 * llega recuperado en el prompt para esa misma pregunta. El resultado lo dice, para que el modelo
 * no convierta "foto del vaso" en una receta, ni invente pasos si el material no los trae.
 *
 * <p>Solo lee: no necesita el flag de botones. Trae el habito_id para poder seguir con
 * {@code proponer_renombrar_habito}.
 */
@Component
public class ConsultarComoSeHaceHabitoHerramienta implements HerramientaAgente {

    public static final String NOMBRE = "consultar_como_se_hace_habito";
    public static final String ARGUMENTO_HABITO = "habito";

    private static final Logger log = LoggerFactory.getLogger(ConsultarComoSeHaceHabitoHerramienta.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Busca entre los habitos de la persona el que nombro y devuelve sus nombres (el suyo y el del "
                    + "programa), su descripcion del programa y si se le puede cambiar el nombre. Usala cuando "
                    + "pregunte como se hace un habito o un ritual, o antes de proponer cambiarle el nombre.",
            List.of(new ParametroHerramienta(ARGUMENTO_HABITO, TipoParametroHerramienta.TEXTO,
                    "El habito como lo nombro la persona, por ejemplo 'ritual de mañana' o 'jugo verde'. Omitelo "
                            + "para ver todos.", false)));

    static final String SOBRE_LOS_PASOS = "La descripcion es la del programa y a veces solo dice que evidencia se "
            + "manda: no son los pasos. Los pasos salen del contenido del programa recuperado para esta pregunta; "
            + "si ahi no estan, dile en una linea que no tienes los pasos en el material, sin inventarlos.";

    private final GestionarPlanDeHabitosPort planPort;

    public ConsultarComoSeHaceHabitoHerramienta(GestionarPlanDeHabitosPort planPort) {
        this.planPort = planPort;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        List<FichaDeHabito> fichas;
        try {
            fichas = planPort.fichasDe(actorId);
        } catch (NoSuchElementException sinPrograma) {
            return ResultadoHerramienta.fallo("No encontre un programa activo para esta cuenta.");
        } catch (NotAuthorizedException suspendida) {
            return ResultadoHerramienta.fallo("La cuenta esta suspendida: no puedo ver sus habitos.");
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudieron leer las fichas de habitos", falla);
            return ResultadoHerramienta.fallo("No pude consultar sus habitos en este momento.");
        }
        return ResultadoHerramienta.exito(textoPara(fichas, invocacion.argumento(ARGUMENTO_HABITO)));
    }

    private static String textoPara(List<FichaDeHabito> fichas, String comoLoNombro) {
        List<FichaDeHabito> encontrados = HabitoNombradoPorLaPersona.entre(fichas, comoLoNombro);
        if (encontrados.isEmpty()) {
            return "No tiene ningun habito que se llame '" + comoLoNombro + "'. No respondas sobre otro habito "
                    + "sin decirlo. Sus habitos: " + fichas.stream().map(FichaDeHabito::tituloVisible)
                    .collect(Collectors.joining(", ")) + ".";
        }
        return encontrados.stream().map(ConsultarComoSeHaceHabitoHerramienta::lineaDe)
                .collect(Collectors.joining("\n")) + "\n" + SOBRE_LOS_PASOS;
    }

    static String lineaDe(FichaDeHabito ficha) {
        String nombres = ficha.renombrado()
                ? ficha.tituloPersonal() + " (" + ficha.tituloDelPrograma() + " del programa)"
                : ficha.tituloDelPrograma();
        String descripcion = ficha.descripcion() == null || ficha.descripcion().isBlank() ? "sin descripcion"
                : ficha.descripcion().strip();
        return "habito_id=" + ficha.habitoId() + " | " + nombres + " | descripcion: " + descripcion
                + " | se_puede_renombrar=" + (ficha.renombrable() ? "si" : "no");
    }
}
