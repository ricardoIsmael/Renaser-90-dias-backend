package com.renaser.os.users.infrastructure.adapter.in.rest.accountrequest;

import com.renaser.os.users.application.ports.in.accountrequest.ListAccountRequestsUseCase.PaginaAccountRequests;

import java.util.List;

public record AccountRequestPageResponse(List<AccountRequestResponse> content, long total, int page, int size) {

    public static AccountRequestPageResponse from(PaginaAccountRequests pagina) {
        List<AccountRequestResponse> content = pagina.contenido().stream().map(AccountRequestResponse::from).toList();
        return new AccountRequestPageResponse(content, pagina.total(), pagina.page(), pagina.size());
    }
}
