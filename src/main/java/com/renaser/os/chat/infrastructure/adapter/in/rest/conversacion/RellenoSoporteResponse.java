package com.renaser.os.chat.infrastructure.adapter.in.rest.conversacion;

import com.renaser.os.chat.application.ports.in.conversacion.RellenarConversacionesDeSoporteUseCase.ResultadoRelleno;

/**
 * Lo que devolvio el relleno de chats de soporte. Cuatro numeros y no un "ok": quien lo dispara
 * tiene que poder ver que paso sin ir a mirar la base — sobre todo {@code failed}, que si no es
 * cero significa que la corrida quedo incompleta y hay que repetirla.
 */
public record RellenoSoporteResponse(int traineesReviewed, int created, int alreadyExisted, int failed) {

    public static RellenoSoporteResponse from(ResultadoRelleno resultado) {
        return new RellenoSoporteResponse(resultado.aprendicesRevisados(), resultado.creadas(),
                resultado.yaExistian(), resultado.fallidas());
    }
}
