package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort;
import com.renaser.os.rag.application.ports.out.academia.ClaseDiariaDelAprendizPort.ClaseDeHoy;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.UserId;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * {@code proponer_entregar_clase_de_hoy} (R2, 2026-09-23): PROPONE entregar el resumen de la Clase
 * Diaria de hoy. La escritura la hace {@link EntregarClaseDeHoyConfirmable} cuando la persona toca
 * "Confirmar" (D-153), con el mismo caso de uso que {@code POST /api/v1/classroom/clase-diaria}.
 *
 * <p>Entregar <b>otorga puntos</b> y cierra el habito {@code DAILY_CLASS}: por eso el resumen que
 * ve la persona lo dice y muestra el texto exacto que se va a guardar. El texto tiene que ser de la
 * persona; la descripcion se lo exige al modelo.
 *
 * <p>Antes de proponer: largo del resumen (el mismo minimo y maximo que valida {@code academy},
 * leidos del puerto, no repetidos aca) y que haya clase disponible hoy. La leccion y el dia de
 * programa se guardan en la propuesta, no los elige el modelo: al confirmar se verifica que la
 * clase de hoy siga siendo esa.
 *
 * <p>Solo se ofrece con {@code renaser.ia.acompanante.confirmacion-con-botones=true}.
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeEntregarClaseDeHoy implements HerramientaAgente {

    public static final String NOMBRE = "proponer_entregar_clase_de_hoy";
    public static final String ARGUMENTO_RESUMEN = "resumen";
    static final String ARGUMENTO_LECCION_ID = "leccion_id";
    static final String ARGUMENTO_DIA_PROGRAMA = "dia_programa";

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "PROPONE entregar la Clase Diaria de hoy con el resumen de la persona. Entregarla cierra el habito de "
                    + "la Clase Diaria y SUMA PUNTOS. NO entrega nada: deja una propuesta que la persona confirma "
                    + "con un boton; nunca digas que ya quedo entregada. Llama antes a consultar_clase_de_hoy. El "
                    + "resumen tiene que ser de la persona, con sus palabras: ayudala a ordenar lo que ella te "
                    + "conto, pero NUNCA lo escribas tu si ella no te dio el contenido. Usala solo cuando la persona "
                    + "pida entregar su resumen.",
            List.of(ParametroHerramienta.obligatorio(ARGUMENTO_RESUMEN, TipoParametroHerramienta.TEXTO,
                    "El resumen de lo que la persona entendio de la clase, con sus palabras, del largo que indica "
                            + "consultar_clase_de_hoy. Muestraselo antes de proponerlo.")));

    private final ClaseDiariaDelAprendizPort claseDiaria;
    private final ProponerAccionUseCase proponerAccion;

    public PropuestaDeEntregarClaseDeHoy(ClaseDiariaDelAprendizPort claseDiaria, ProponerAccionUseCase proponerAccion) {
        this.claseDiaria = claseDiaria;
        this.proponerAccion = proponerAccion;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        String resumen = textoDe(invocacion.argumento(ARGUMENTO_RESUMEN));
        return LecturaDeAcademia.consultar(NOMBRE, () -> proponer(actorId, resumen, claseDiaria.claseDeHoy(actorId)));
    }

    private ResultadoHerramienta proponer(UserId actorId, String resumen, ClaseDeHoy clase) {
        if (!clase.disponible()) {
            return ResultadoHerramienta.fallo("Hoy no hay una Clase Diaria disponible para entregar: "
                    + ConsultarClaseDeHoyHerramienta.textoDe(clase));
        }
        if (resumen.length() < clase.resumenMinimo() || resumen.length() > clase.resumenMaximo()) {
            return ResultadoHerramienta.fallo(largoInvalido(resumen, clase));
        }
        EntregaPedida entrega = new EntregaPedida(resumen, clase.leccionId(), clase.diaPrograma());
        return PropuestaPendiente.registrar(proponerAccion, actorId, entrega.invocacion(), resumenDe(entrega, clase));
    }

    /** Se valida y se guarda el texto sin espacios alrededor: lo que se muestra es lo que se entrega. */
    private static String textoDe(String resumen) {
        return resumen == null ? "" : resumen.strip();
    }

    private static String largoInvalido(String resumen, ClaseDeHoy clase) {
        String motivo = "El resumen tiene " + resumen.length() + " caracteres y tiene que tener entre "
                + clase.resumenMinimo() + " y " + clase.resumenMaximo() + ". ";
        return motivo + (resumen.length() < clase.resumenMinimo()
                ? "Pidele que cuente con sus palabras un poco mas de lo que entendio; no lo completes tu."
                : "Pidele que lo acorte; no lo recortes tu sin mostrarselo.");
    }

    /** Lo que ve la persona junto a los botones: que se entrega, que otorga y el texto exacto. */
    static String resumenDe(EntregaPedida entrega, ClaseDeHoy clase) {
        return "Entregar la Clase Diaria de hoy (dia " + clase.diaPrograma() + "): '" + clase.leccionTitulo()
                + "'. Cierra el habito de la Clase Diaria, suma sus puntos y marca la leccion como vista. "
                + "Tu resumen: \"" + entrega.resumen() + "\".";
    }

    /** La forma canonica que se guarda y se ejecuta al confirmar. */
    record EntregaPedida(String resumen, String leccionId, int diaPrograma) {

        /** @throws PropuestaImposibleException si la propuesta guardada no tiene la forma esperada */
        static EntregaPedida de(InvocacionHerramienta invocacion) {
            String resumen = invocacion.argumento(ARGUMENTO_RESUMEN);
            String leccionId = invocacion.argumento(ARGUMENTO_LECCION_ID);
            String dia = invocacion.argumento(ARGUMENTO_DIA_PROGRAMA);
            if (resumen == null || resumen.isBlank() || leccionId == null || leccionId.isBlank() || dia == null) {
                throw new PropuestaImposibleException("La propuesta guardada no es valida.");
            }
            try {
                return new EntregaPedida(resumen, leccionId, Integer.parseInt(dia));
            } catch (NumberFormatException diaInvalido) {
                throw new PropuestaImposibleException("La propuesta guardada no es valida.");
            }
        }

        InvocacionHerramienta invocacion() {
            return new InvocacionHerramienta(NOMBRE, Map.of(ARGUMENTO_RESUMEN, resumen,
                    ARGUMENTO_LECCION_ID, leccionId, ARGUMENTO_DIA_PROGRAMA, Integer.toString(diaPrograma)));
        }
    }
}
