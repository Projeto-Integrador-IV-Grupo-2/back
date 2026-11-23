package com.nexo.controller;

import com.nexo.dto.empresa.CriarEmpresaRequest;
import com.nexo.dto.empresa.EmpresaResponse;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.EmpresaService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Cadastro de empresas (tenants). Fica sob {@code /api/admin/**}, então já
 * exige ADMIN no {@code SecurityConfig}; o {@link EmpresaService} ainda exige
 * que o ADMIN seja da empresa dona da plataforma.
 */
@RestController
@RequestMapping("/api/admin/empresas")
public class EmpresaController {

    private final EmpresaService empresaService;

    public EmpresaController(EmpresaService empresaService) {
        this.empresaService = empresaService;
    }

    @GetMapping
    public List<EmpresaResponse> listar(@AuthenticationPrincipal UsuarioAutenticado requisitante) {
        return empresaService.listar(requisitante);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EmpresaResponse criar(@Valid @RequestBody CriarEmpresaRequest request,
                                 @AuthenticationPrincipal UsuarioAutenticado requisitante) {
        return empresaService.criar(request, requisitante);
    }
}
