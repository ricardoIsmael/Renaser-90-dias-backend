package com.renaser.os.rag.application.services.herramientas;

import com.renaser.os.rag.application.ports.in.propuesta.ProponerAccionUseCase;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort;
import com.renaser.os.rag.application.ports.out.enfoque.EnfoqueDiarioDelAprendizPort.DiaSinCelularDeHoy;
import com.renaser.os.rag.domain.model.herramienta.DefinicionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.InvocacionHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ParametroHerramienta;
import com.renaser.os.rag.domain.model.herramienta.ResultadoHerramienta;
import com.renaser.os.rag.domain.model.herramienta.TipoParametroHerramienta;
import com.renaser.os.shared.domain.Clock;
import com.renaser.os.shared.domain.UserId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * {@code proponer_iniciar_dia_sin_celular} (R2, propuesta, 2026-09-23): empezar una racha de "Dia
 * sin celular" con una meta en horas. No escribe: deja una propuesta; la escritura es
 * {@link IniciarDiaSinCelularConfirmable}, que llama al mismo caso de uso que la app
 * ({@code IniciarRachaUseCase}).
 *
 * <p><b>Solo iniciar.</b> Cerrar la racha pide evidencia y romperla es una declaracion de la
 * persona: las dos cosas se hacen en la app.
 *
 * <p>Valida antes de proponer con datos de {@code habits}: que hoy tenga el habito, que no haya
 * otra racha en curso (solo puede haber una) y que la meta sea una de las que acepta el dominio
 * ({@code RachaSinCelular.HITOS}, que llegan en {@code metasValidas}: aca no se repiten).
 */
@Component
@ConditionalOnProperty(name = "renaser.ia.acompanante.confirmacion-con-botones", havingValue = "true")
public class PropuestaDeIniciarDiaSinCelular implements HerramientaAgente {

    public static final String NOMBRE = "proponer_iniciar_dia_sin_celular";
    public static final String ARGUMENTO_HORAS_OBJETIVO = "horas_objetivo";
    /** Lo agrega la herramienta al guardar la propuesta; el modelo no lo manda. */
    public static final String ARGUMENTO_REGISTRO_ID = "registro_id";

    private static final Logger log = LoggerFactory.getLogger(PropuestaDeIniciarDiaSinCelular.class);

    private static final DefinicionHerramienta DEFINICION = new DefinicionHerramienta(NOMBRE,
            "Propone empezar ahora una racha de 'Dia sin celular' con una meta en horas. NO la empieza: deja una "
                    + "propuesta y la persona tiene que tocar Confirmar en la app. Solo sirve para EMPEZAR: cerrarla "
                    + "o cortarla se hace en la app, no lo ofrezcas. Pregunta la meta si no la dijo; no la elijas tu.",
            List.of(ParametroHerramienta.obligatorio(ARGUMENTO_HORAS_OBJETIVO, TipoParametroHerramienta.ENTERO,
                    "La meta en horas que eligio la persona. Si no es valida, la herramienta te dice cuales se "
                            + "aceptan.")));

    private final EnfoqueDiarioDelAprendizPort enfoquePort;
    private final ProponerAccionUseCase proponerAccion;
    private final Clock clock;

    public PropuestaDeIniciarDiaSinCelular(EnfoqueDiarioDelAprendizPort enfoquePort,
                                           ProponerAccionUseCase proponerAccion, Clock clock) {
        this.enfoquePort = enfoquePort;
        this.proponerAccion = proponerAccion;
        this.clock = clock;
    }

    @Override
    public DefinicionHerramienta definicion() {
        return DEFINICION;
    }

    @Override
    public ResultadoHerramienta ejecutar(UserId actorId, InvocacionHerramienta invocacion) {
        Optional<Integer> horas = horasDe(invocacion.argumento(ARGUMENTO_HORAS_OBJETIVO));
        if (horas.isEmpty()) {
            return ResultadoHerramienta.fallo("La meta tiene que ser un numero entero de horas.");
        }
        return LecturaDelEnfoque.con(() -> enfoquePort.diaSinCelularDeHoy(actorId), "su Dia sin celular",
                dia -> proponerSiCorresponde(actorId, dia, horas.get()));
    }

    private ResultadoHerramienta proponerSiCorresponde(UserId actorId, DiaSinCelularDeHoy dia, int horas) {
        Optional<String> impedimento = impedimentoDe(dia, horas);
        if (impedimento.isPresent()) {
            return ResultadoHerramienta.fallo(impedimento.get());
        }
        String resumen = "Empezar ahora tu '" + dia.titulo() + "' con meta de " + horas + " horas";
        try {
            proponerAccion.proponer(actorId, new InvocacionHerramienta(NOMBRE, Map.of(ARGUMENTO_REGISTRO_ID,
                    dia.registroId().toString(), ARGUMENTO_HORAS_OBJETIVO, String.valueOf(horas))), resumen);
        } catch (RuntimeException falla) {
            log.warn("[rag] no se pudo guardar la propuesta de {}", NOMBRE, falla);
            return AvisoDePropuesta.noSePudoPreparar();
        }
        return AvisoDePropuesta.creada(resumen, "Cerrarla con evidencia o cortarla se hace desde la app.");
    }

    private Optional<String> impedimentoDe(DiaSinCelularDeHoy dia, int horas) {
        if (dia.rachaEnCursoDesde() != null) {
            MomentoDelAprendiz momento = new MomentoDelAprendiz(clock.now(), dia.zona());
            return Optional.of("Ya tiene una racha sin celular en curso desde las "
                    + momento.horaDe(dia.rachaEnCursoDesde()) + " (meta " + dia.rachaEnCursoHoras() + " h): solo "
                    + "puede haber una a la vez. Lo que siga con esa racha se hace en la app.");
        }
        if (dia.registroId() == null) {
            return Optional.of("Hoy no tiene el habito 'Dia sin celular' en su dia.");
        }
        if (!dia.iniciable()) {
            return Optional.of("'" + dia.titulo() + "' de hoy ya no esta pendiente: no se puede empezar una racha.");
        }
        if (!dia.metasValidas().contains(horas)) {
            return Optional.of("La meta tiene que ser una de estas (en horas): " + dia.metasValidas().stream()
                    .map(String::valueOf).collect(Collectors.joining(", ")) + ".");
        }
        return Optional.empty();
    }

    static Optional<Integer> horasDe(String texto) {
        try {
            return texto == null ? Optional.empty() : Optional.of(Integer.valueOf(texto.trim()));
        } catch (NumberFormatException noEsUnNumero) {
            return Optional.empty();
        }
    }
}
