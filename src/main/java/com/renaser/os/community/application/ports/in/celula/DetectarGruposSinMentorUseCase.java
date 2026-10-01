package com.renaser.os.community.application.ports.in.celula;

/**
 * Barre los grupos en curso y avisa al líder de mentores de los que no tienen mentor (D-240).
 *
 * <p>Sin comando: lo dispara el barrido horario. Devuelve cuántos avisos se publicaron.
 */
public interface DetectarGruposSinMentorUseCase {

    int avisarDeLosQueNoTienenMentor();
}
