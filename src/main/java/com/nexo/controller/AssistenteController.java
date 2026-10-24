package com.nexo.controller;

import com.nexo.dto.assistente.CriarSessaoRequest;
import com.nexo.dto.assistente.PerguntaRequest;
import com.nexo.dto.assistente.RespostaAssistenteResponse;
import com.nexo.dto.assistente.SessaoResponse;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.assistente.ChatSessaoService;
import com.nexo.service.assistente.ConsultaAssistenteService;
import jakarta.validation.Valid;
import java.net.URI;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Assistente de IA (issues #59 e #60). Qualquer usuário autenticado conversa,
 * sempre nas próprias sessões.
 */
@RestController
@RequestMapping("/api/assistente/sessoes")
public class AssistenteController {

    private final ChatSessaoService chatSessaoService;
    private final ConsultaAssistenteService consultaAssistenteService;

    public AssistenteController(ChatSessaoService chatSessaoService,
                                ConsultaAssistenteService consultaAssistenteService) {
        this.chatSessaoService = chatSessaoService;
        this.consultaAssistenteService = consultaAssistenteService;
    }

    @PostMapping
    public ResponseEntity<SessaoResponse> criarSessao(@RequestBody(required = false) CriarSessaoRequest request,
                                                      @AuthenticationPrincipal UsuarioAutenticado usuario,
                                                      UriComponentsBuilder uriBuilder) {
        SessaoResponse response = chatSessaoService.criar(request == null ? null : request.contexto(), usuario);
        URI location = uriBuilder.path("/api/assistente/sessoes/{id}").buildAndExpand(response.id()).toUri();
        return ResponseEntity.created(location).body(response);
    }

    @PostMapping("/{id}/mensagens")
    public RespostaAssistenteResponse perguntar(@PathVariable UUID id,
                                                @Valid @RequestBody PerguntaRequest request,
                                                @AuthenticationPrincipal UsuarioAutenticado usuario) {
        return consultaAssistenteService.perguntar(id, request.pergunta(), usuario);
    }
}
