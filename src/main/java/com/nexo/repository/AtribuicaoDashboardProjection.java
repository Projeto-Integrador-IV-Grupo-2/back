package com.nexo.repository;

import com.nexo.entity.StatusAtribuicao;
import java.time.LocalDate;
import java.util.UUID;

/** Campos mínimos usados pelo service para calcular as métricas de gestão. */
public interface AtribuicaoDashboardProjection {
    UUID getAtribuicaoId();
    StatusAtribuicao getStatus();
    LocalDate getPrazo();
    UUID getAreaId();
    String getAreaNome();
}
