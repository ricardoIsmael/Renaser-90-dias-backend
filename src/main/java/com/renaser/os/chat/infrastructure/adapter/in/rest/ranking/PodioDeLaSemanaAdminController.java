package com.renaser.os.chat.infrastructure.adapter.in.rest.ranking;

import com.renaser.os.chat.application.ports.in.ranking.PublicarPodioDeLaSemanaUseCase;
import com.renaser.os.chat.application.ports.in.ranking.VerPodioDeLaSemanaUseCase;
import com.renaser.os.shared.domain.Permission;
import com.renaser.os.shared.domain.UserId;
import com.renaser.os.shared.web.security.ActorAutenticado;
import com.renaser.os.shared.web.security.RequiresPermission;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * El podio semanal del grupo general, para que Administración lo pruebe sin esperar al lunes (D-262): la vista
 * previa de la última semana cerrada (imagen y texto, sin publicar) y publicarla ahora (idempotente: la misma
 * semana no sale dos veces).
 *
 * <p>El permiso declarado es {@link Permission#ADJUST_POINTS}, el mismo que la regeneración del ranking
 * ({@code RegeneracionRankingAdminController}): es el mismo subsistema y el mismo operador, y quién puede tener
 * cada permiso es una decisión del dueño. Quien lo hace cumplir de verdad es el servicio: ADMIN y ALCHEMIST con
 * la cuenta activa (para MENTOR, ADMIN y ALCHEMIST el interceptor todavía deja pasar todo, A-1).
 */
@RestController
@RequestMapping("/api/v1/admin/ranking-semanal")
public class PodioDeLaSemanaAdminController {

    private static final String SOLO_ADMIN = "PodioDeLaSemanaService: solo ADMIN/ALCHEMIST activos";

    private final VerPodioDeLaSemanaUseCase verUseCase;
    private final PublicarPodioDeLaSemanaUseCase publicarUseCase;

    public PodioDeLaSemanaAdminController(VerPodioDeLaSemanaUseCase verUseCase,
                                          PublicarPodioDeLaSemanaUseCase publicarUseCase) {
        this.verUseCase = verUseCase;
        this.publicarUseCase = publicarUseCase;
    }

    @RequiresPermission(value = Permission.ADJUST_POINTS, scope = SOLO_ADMIN)
    @GetMapping("/vista-previa")
    public VistaPreviaDelPodioResponse vistaPrevia(@ActorAutenticado UserId actorId) {
        return VistaPreviaDelPodioResponse.from(verUseCase.vistaPrevia(actorId));
    }

    @RequiresPermission(value = Permission.ADJUST_POINTS, scope = SOLO_ADMIN)
    @PostMapping("/publicar")
    public PublicacionDelPodioResponse publicar(@ActorAutenticado UserId actorId) {
        return PublicacionDelPodioResponse.from(publicarUseCase.publicarAhora(actorId));
    }
}
