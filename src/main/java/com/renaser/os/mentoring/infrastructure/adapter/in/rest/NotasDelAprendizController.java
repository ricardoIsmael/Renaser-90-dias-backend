package com.renaser.os.mentoring.infrastructure.adapter.in.rest;

import com.renaser.os.mentoring.api.NotaDelAprendiz;
import com.renaser.os.mentoring.application.ports.in.ConsultarNotasDelAprendizUseCase;
import com.renaser.os.mentoring.application.ports.in.ConsultarNotasDelAprendizUseCase.ConsultaNotasDelAprendiz;
import com.renaser.os.mentoring.application.ports.in.CorregirNotaDelAprendizUseCase;
import com.renaser.os.mentoring.application.ports.in.CorregirNotaDelAprendizUseCase.CorregirNota;
import com.renaser.os.mentoring.application.ports.in.EscribirNotaDelAprendizUseCase;
import com.renaser.os.mentoring.application.ports.in.EscribirNotaDelAprendizUseCase.EscribirNota;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.Funcionalidad;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/**
 * Las notas privadas del mentor sobre un aprendiz (spec 055):
 * <ul>
 *   <li>{@code GET  /api/v1/mentor/groups/{groupId}/learners/{userId}/notas} — todas las del aprendiz, de la más nueva a la más vieja;</li>
 *   <li>{@code POST …/notas} con {@code { texto }} — escribe una (201); vacía o de más de 2000 caracteres es 400 {@code NOTA_INVALIDA};</li>
 *   <li>{@code PATCH …/notas/{notaId}} con {@code { texto }} — su autor la corrige el mismo día (409 {@code YA_NO_SE_PUEDE_CORREGIR} después,
 *       403 {@code NO_ES_EL_AUTOR} si no la escribió).</li>
 * </ul>
 *
 * <p>El permiso declarado ({@code USE_APP}) no protege nada: MENTOR pasa el interceptor sin que se le mire nada (A-1). Lo que protege es
 * el guard del servicio: acompañar el grupo HOY con la cuenta activa y que el aprendiz sea de él; si no, 403 {@code NO_ACOMPANA}. Son
 * datos privados: nada se guarda en caché.
 */
@RestController
@RequestMapping("/api/v1/mentor/groups/{groupId}/learners/{userId}/notas")
@Funcionalidad("mentor.mi_grupo")
public class NotasDelAprendizController {

    private static final String GUARD = "AccesoAVistasDelSemaforo: acompanante VIGENTE del grupo con cuenta activa Y el aprendiz del grupo";

    private final ConsultarNotasDelAprendizUseCase consultar;
    private final EscribirNotaDelAprendizUseCase escribir;
    private final CorregirNotaDelAprendizUseCase corregir;

    public NotasDelAprendizController(ConsultarNotasDelAprendizUseCase consultar, EscribirNotaDelAprendizUseCase escribir,
                                      CorregirNotaDelAprendizUseCase corregir) {
        this.consultar = consultar;
        this.escribir = escribir;
        this.corregir = corregir;
    }

    @RequiresPermission(value = Permission.USE_APP, scope = GUARD)
    @GetMapping
    public ResponseEntity<NotasResponse> notas(@ActorAutenticado UserId actorId, @PathVariable UUID groupId,
                                               @PathVariable UUID userId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new NotasResponse(consultar.notasDelMentor(new ConsultaNotasDelAprendiz(actorId, groupId, userId))));
    }

    @RequiresPermission(value = Permission.USE_APP, scope = GUARD)
    @PostMapping
    public ResponseEntity<NotaDelAprendiz> escribir(@ActorAutenticado UserId actorId, @PathVariable UUID groupId,
                                                    @PathVariable UUID userId, @RequestBody TextoDeLaNota cuerpo) {
        NotaDelAprendiz nota = escribir.escribir(new EscribirNota(actorId, groupId, userId, cuerpo.texto()));
        return ResponseEntity.status(HttpStatus.CREATED).cacheControl(CacheControl.noStore()).body(nota);
    }

    @RequiresPermission(value = Permission.USE_APP, scope = GUARD + "; solo su autor, y el mismo día")
    @PatchMapping("/{notaId}")
    public ResponseEntity<NotaDelAprendiz> corregir(@ActorAutenticado UserId actorId, @PathVariable UUID groupId,
                                                    @PathVariable UUID userId, @PathVariable UUID notaId,
                                                    @RequestBody TextoDeLaNota cuerpo) {
        NotaDelAprendiz nota = corregir.corregir(new CorregirNota(actorId, groupId, userId, notaId, cuerpo.texto()));
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(nota);
    }

    /** @param texto la nota; se recorta, y no puede quedar vacía ni pasar de 2000 caracteres (la valida el dominio) */
    public record TextoDeLaNota(String texto) {
    }

    public record NotasResponse(List<NotaDelAprendiz> notas) {
    }
}
