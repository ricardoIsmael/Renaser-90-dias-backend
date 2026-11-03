package com.renaser.os.onboarding.infrastructure.adapter.in.rest.diagnostico;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.renaser.os.onboarding.application.ports.in.diagnostico.DiagnosticoDeLaPersonaUseCase.DiagnosticoAbierto;
import com.renaser.os.onboarding.application.ports.in.diagnostico.DiagnosticoDeLaPersonaUseCase.ObjetivoNacido;
import com.renaser.os.onboarding.application.ports.in.diagnostico.EditarDiagnosticoUseCase.PreguntaAdministrada;
import com.renaser.os.onboarding.application.ports.in.diagnostico.EditarDiagnosticoUseCase.PreguntaPedida;
import com.renaser.os.onboarding.application.ports.in.diagnostico.PublicarDiagnosticoUseCase.BorradorAdministrado;
import com.renaser.os.onboarding.application.ports.in.diagnostico.PublicarDiagnosticoUseCase.SeccionAdministrada;
import com.renaser.os.onboarding.domain.model.diagnostico.VersionPublicada;
import com.renaser.os.onboarding.domain.model.cuestionario.TipoPreguntaOnboarding;
import com.renaser.os.onboarding.domain.model.diagnostico.CondicionDeVisibilidad;
import com.renaser.os.onboarding.domain.model.diagnostico.DestinoDeRespuesta;
import com.renaser.os.onboarding.domain.model.diagnostico.EscalaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.OpcionDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.PreguntaDelDiagnostico;
import com.renaser.os.onboarding.domain.model.diagnostico.RespuestaEntrante;
import com.renaser.os.onboarding.domain.model.diagnostico.SeccionDelDiagnostico;
import com.renaser.os.onboarding.domain.model.respuesta.Respuesta;
import com.renaser.os.shared.domain.Formacion;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/** Los cuerpos del diagnóstico en la API (contrato §1, §4 y §5). Solo traducen: ninguna regla vive aquí. */
final class DiagnosticoDtos {

    private DiagnosticoDtos() {
    }

    // ----------------------------------------------------------------------------------------------- persona

    @JsonInclude(JsonInclude.Include.ALWAYS)
    record EstadoResponse(String ficha, String estado, Integer version, Instant completedAt) {
    }

    record OpcionResponse(String valor, String etiqueta) {
    }

    record EscalaResponse(int min, int max, String etiquetaMin, String etiquetaMax) {
        static EscalaResponse from(EscalaDelDiagnostico e) {
            return e == null ? null : new EscalaResponse(e.min(), e.max(), e.etiquetaMin(), e.etiquetaMax());
        }
    }

    record CondicionResponse(int preguntaId, String valor) {
        static CondicionResponse from(CondicionDeVisibilidad c) {
            return c == null ? null : new CondicionResponse(c.preguntaId(), c.valor());
        }
    }

    record PreguntaResponse(int id, String clave, String texto, String ayuda, String tipo, boolean requerida, Integer maximoOpciones,
                            Integer largoMaximo, List<OpcionResponse> opciones, EscalaResponse escala, CondicionResponse seMuestraSi,
                            String alimenta, String precarga) {

        static PreguntaResponse from(PreguntaDelDiagnostico p, String precarga) {
            return new PreguntaResponse(p.id(), p.clave(), p.texto(), p.ayuda(), p.tipo().name(), p.requerida(), p.maximoOpciones(), p.largoMaximo(),
                    p.opciones().stream().map(o -> new OpcionResponse(o.valor(), o.etiqueta())).toList(), EscalaResponse.from(p.escala()),
                    CondicionResponse.from(p.condicion()), p.alimenta() == null ? null : p.alimenta().name(), precarga);
        }
    }

    record SeccionResponse(String clave, String titulo, String descripcion, List<PreguntaResponse> preguntas) {
    }

    record PortadaResponse(String titulo, String mensaje) {
    }

    record FormacionResponse(String codigo, String nombre) {
    }

    record DuracionResponse(int dias, String texto) {
    }

    record RespuestaGuardadaResponse(int preguntaId, String valorTexto, BigDecimal valorNumero, Boolean valorBooleano, Short valorEscala,
                                     String valorJson) {
        static RespuestaGuardadaResponse from(Respuesta r) {
            return new RespuestaGuardadaResponse(r.preguntaId(), r.valorTexto(), r.valorNumero(), r.valorBooleano(), r.valorEscala(), r.valorJson());
        }
    }

    record DiagnosticoResponse(int version, String estado, String titulo, PortadaResponse portada, FormacionResponse formacion,
                               DuracionResponse duracion, Integer paso, List<SeccionResponse> secciones, List<RespuestaGuardadaResponse> respuestas) {

        static DiagnosticoResponse from(DiagnosticoAbierto d) {
            List<SeccionResponse> secciones = d.ficha().secciones().stream().filter(s -> !s.esPortada())
                    .map(s -> new SeccionResponse(s.clave(), s.titulo(), s.descripcion(),
                            s.preguntas().stream().map(p -> PreguntaResponse.from(p, d.precargas().get(p.id()))).toList()))
                    .toList();
            return new DiagnosticoResponse(d.version(), d.estado(), d.titulo(), new PortadaResponse(d.tituloDeLaPortada(), d.mensajeDeLaPortada()),
                    d.formacionCodigo() == null ? null : new FormacionResponse(d.formacionCodigo(), d.formacionNombre()),
                    new DuracionResponse(d.duracionDias(), d.duracionTexto()), d.paso(), secciones,
                    d.respuestas().values().stream().sorted(Comparator.comparingInt(Respuesta::preguntaId)).map(RespuestaGuardadaResponse::from).toList());
        }
    }

    record RespuestaRequest(@NotNull Integer preguntaId, String valorTexto, BigDecimal valorNumero, Boolean valorBooleano, Short valorEscala,
                            String valorJson) {
        RespuestaEntrante aEntrante() {
            return new RespuestaEntrante(preguntaId, valorTexto, valorNumero, valorBooleano, valorEscala, valorJson);
        }
    }

    record GuardarRespuestasRequest(@NotNull @Valid List<@Valid RespuestaRequest> respuestas) {
    }

    record GuardadasResponse(int guardadas) {
    }

    record ObjetivoResponse(String eje, String texto) {
        static ObjetivoResponse from(ObjetivoNacido o) {
            return new ObjetivoResponse(o.eje(), o.texto());
        }
    }

    record TerminarResponse(String estado, List<ObjetivoResponse> objetivos, String prioridad, String primeraAccion) {
    }

    record ResumenResponse(boolean completado, String prioridad, String primeraAccion, List<ObjetivoResponse> objetivos) {
    }

    // ------------------------------------------------------------------------------------------ administración

    record EscalaRequest(int min, int max, String etiquetaMin, String etiquetaMax) {
    }

    record OpcionRequest(String valor, @NotBlank String etiqueta) {
    }

    record PreguntaRequest(@NotNull Integer seccionId, @NotBlank String texto, String ayuda, @NotBlank String tipo, boolean requerida,
                           Integer maximoOpciones, List<@Valid OpcionRequest> opciones, @Valid EscalaRequest escala,
                           @Valid CondicionRequest seMuestraSi, String alimenta, List<String> formaciones, String precarga) {

        PreguntaPedida aPedida() {
            return new PreguntaPedida(seccionId, texto, ayuda, TipoPreguntaOnboarding.valueOf(tipo.trim().toUpperCase()), requerida, maximoOpciones,
                    opciones == null ? List.of() : opciones.stream().map(o -> new OpcionDelDiagnostico(o.valor(), o.etiqueta())).toList(),
                    escala == null ? null : new EscalaDelDiagnostico(escala.min(), escala.max(), escala.etiquetaMin(), escala.etiquetaMax()),
                    seMuestraSi == null ? null : new CondicionDeVisibilidad(seMuestraSi.preguntaId(), seMuestraSi.valor()),
                    alimenta == null || alimenta.isBlank() ? null : DestinoDeRespuesta.valueOf(alimenta.trim().toUpperCase()),
                    formaciones == null ? Set.of() : formaciones.stream().map(Formacion::desde).collect(Collectors.toSet()), precarga);
        }
    }

    record CondicionRequest(@NotNull Integer preguntaId, @NotBlank String valor) {
    }

    record PreguntaAdminResponse(int id, String clave, int seccionId, int orden, String texto, String ayuda, String tipo, boolean requerida,
                                 Integer maximoOpciones, List<OpcionResponse> opciones, EscalaResponse escala, CondicionResponse seMuestraSi,
                                 String alimenta, List<String> formaciones, String precarga, String estado, boolean protegida, int respuestas) {

        static PreguntaAdminResponse from(PreguntaAdministrada a) {
            PreguntaDelDiagnostico p = a.pregunta();
            return new PreguntaAdminResponse(p.id(), p.clave(), p.seccionId(), p.orden(), p.texto(), p.ayuda(), p.tipo().name(), p.requerida(),
                    p.maximoOpciones(), p.opciones().stream().map(o -> new OpcionResponse(o.valor(), o.etiqueta())).toList(),
                    EscalaResponse.from(p.escala()), CondicionResponse.from(p.condicion()), p.alimenta() == null ? null : p.alimenta().name(),
                    p.formaciones().stream().map(Formacion::codigo).sorted().toList(), p.precarga(), p.estaRetirada() ? "RETIRADA" : "ACTIVA",
                    a.protegida(), a.respuestas());
        }
    }

    record SeccionAdminResponse(int id, String clave, String titulo, String descripcion, int orden, boolean esPortada,
                                List<PreguntaAdminResponse> preguntas) {

        static SeccionAdminResponse from(SeccionAdministrada s) {
            SeccionDelDiagnostico x = s.seccion();
            return new SeccionAdminResponse(x.id(), x.clave(), x.titulo(), x.descripcion(), x.orden(), x.esPortada(),
                    s.preguntas().stream().map(PreguntaAdminResponse::from).toList());
        }

        static SeccionAdminResponse from(SeccionDelDiagnostico x) {
            return new SeccionAdminResponse(x.id(), x.clave(), x.titulo(), x.descripcion(), x.orden(), x.esPortada(), List.of());
        }
    }

    record QuienPublicoResponse(String id, String nombre) {
    }

    record VersionResponse(int version, Instant publicadaEn, QuienPublicoResponse publicadaPor, int preguntas) {
        static VersionResponse from(VersionPublicada v) {
            return v == null ? null : new VersionResponse(v.version(), v.publicadaEn(),
                    v.publicadaPor() == null ? null : new QuienPublicoResponse(v.publicadaPor().toString(), v.publicadaPorNombre()),
                    v.ficha().preguntas().size());
        }
    }

    record PuedePublicarResponse(boolean ok, List<String> motivos) {
    }

    record TotalesResponse(int secciones, int preguntas) {
    }

    record BorradorResponse(VersionResponse publicada, boolean hayCambiosSinPublicar, PuedePublicarResponse puedePublicar,
                            List<SeccionAdminResponse> secciones, List<PreguntaAdminResponse> retiradas, TotalesResponse totales) {

        static BorradorResponse from(BorradorAdministrado b) {
            return new BorradorResponse(VersionResponse.from(b.publicada()), b.hayCambiosSinPublicar(),
                    new PuedePublicarResponse(b.motivosParaNoPublicar().isEmpty(), b.motivosParaNoPublicar()),
                    b.secciones().stream().map(SeccionAdminResponse::from).toList(), b.retiradas().stream().map(PreguntaAdminResponse::from).toList(),
                    new TotalesResponse(b.secciones().size(), b.totalPreguntas()));
        }
    }

    record SeccionRequest(@NotBlank String titulo, String descripcion) {
    }

    record OrdenRequest(@NotEmpty List<Integer> ids) {
    }

    record MoverRequest(@NotNull Integer seccionId, @NotNull Integer posicion) {
    }

    /** {@code quitarDependientes} es opcional (sin él, no se quitan las que dependen: el servidor avisa cuáles son); un primitivo ausente haría fallar el cuerpo. */
    record QuitarRequest(@NotEmpty List<Integer> ids, Boolean quitarDependientes) {

        boolean conDependientes() {
            return Boolean.TRUE.equals(quitarDependientes);
        }
    }

    record QuitarResponse(int eliminadas, int retiradas, int conRespuestas) {
    }

    record PublicarRequest(@NotNull Integer versionBase) {
    }

    record ImportarRequest(@NotNull String texto, String modo) {
    }

    record AsignarFichaRequest(@NotEmpty List<String> ids, @NotBlank String ficha) {
    }

    record OmitidaResponse(String id, String nombre, String motivo) {
    }

    record AsignadasResponse(int asignadas, List<OmitidaResponse> omitidas) {
    }
}
