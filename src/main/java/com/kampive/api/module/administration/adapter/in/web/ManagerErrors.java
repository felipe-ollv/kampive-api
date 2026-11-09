package com.kampive.api.module.administration.adapter.in.web;

import java.sql.SQLException;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ProblemDetail;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@Order(-2)
@RestControllerAdvice(assignableTypes=ManagerController.class)
public class ManagerErrors {
    @ExceptionHandler(DataAccessException.class)
    ProblemDetail database(DataAccessException error) {
        String code="";
        for(Throwable cause=error;cause!=null;cause=cause.getCause()) if(cause instanceof SQLException sql) code=sql.getSQLState();
        return switch(code) {
            case "42501" -> ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN,"Acesso administrativo necessário.");
            case "P0002" -> ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND,"Registro não encontrado.");
            case "40001","40P01","23505" -> ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,"O registro mudou ou a operação conflita com o estado atual. Atualize os dados antes de tentar novamente.");
            case "22023","23503","23514","23502" -> ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,"Confira os dados e as referências. Para publicar, informe cidade oficial, endereço, coordenadas, descrição, contato e foto.");
            default -> ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,"Não foi possível concluir a operação.");
        };
    }
}
