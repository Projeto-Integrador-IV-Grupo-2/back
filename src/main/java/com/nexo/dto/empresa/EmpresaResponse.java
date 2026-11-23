package com.nexo.dto.empresa;

import com.nexo.entity.Tenant;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Empresa (tenant) na listagem da plataforma — nunca a entidade.
 *
 * @param totalUsuarios usuários cadastrados na empresa, ativos ou não
 * @param plataforma    {@code true} para a empresa da própria plataforma (Nexo)
 */
public record EmpresaResponse(
        UUID id,
        String nome,
        String subdominio,
        String cnpj,
        boolean ativo,
        OffsetDateTime criadoEm,
        long totalUsuarios,
        boolean plataforma) {

    public static EmpresaResponse de(Tenant tenant, long totalUsuarios) {
        return new EmpresaResponse(tenant.getId(), tenant.getNome(), tenant.getSubdominio(), tenant.getCnpj(),
                tenant.isAtivo(), tenant.getCriadoEm(), totalUsuarios, tenant.isPlataforma());
    }
}
