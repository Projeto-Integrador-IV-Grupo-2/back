package com.nexo.service.documento;

import com.nexo.config.DocumentosProperties;
import com.nexo.dto.arquivo.ArquivoResponse;
import com.nexo.entity.Arquivo;
import com.nexo.entity.Conteudo;
import com.nexo.entity.Perfil;
import com.nexo.entity.TipoArquivo;
import com.nexo.event.ArquivoEnviadoEvent;
import com.nexo.exception.CampoInvalidoException;
import com.nexo.exception.ResourceNotFoundException;
import com.nexo.repository.ArquivoRepository;
import com.nexo.repository.ConteudoRepository;
import com.nexo.repository.UsuarioRepository;
import com.nexo.security.UsuarioAutenticado;
import com.nexo.service.armazenamento.ArmazenamentoDeArquivos;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

/**
 * Arquivos anexados aos conteúdos: upload, listagem e download (issue #55).
 *
 * <p><strong>Visibilidade:</strong> a mesma do conteúdo — ADMIN e GESTOR veem
 * os arquivos de rascunhos e publicados; COLABORADOR só os de conteúdo
 * publicado. Arquivo ou conteúdo de outro tenant, ou rascunho para o
 * colaborador, responde 404. Quem pode enviar é decidido no controller.</p>
 *
 * <p><strong>Armazenamento:</strong> o binário vai para o
 * {@link ArmazenamentoDeArquivos} com uma chave gerada aqui
 * ({@code tenant/uuid}); o nome enviado só é guardado para o download, nunca
 * vira caminho. Se a gravação no banco não for confirmada, o binário é
 * apagado, para não sobrar arquivo órfão.</p>
 *
 * <p>Depois de gravar, publica {@link ArquivoEnviadoEvent} — a indexação da
 * IA escuta e reindexa o conteúdo. Bean sem estado (Singleton do Spring).</p>
 */
@Service
public class ArquivoService {

    private static final Logger log = LoggerFactory.getLogger(ArquivoService.class);

    private static final int NOME_MAXIMO = 255;

    private final ArquivoRepository arquivoRepository;
    private final ConteudoRepository conteudoRepository;
    private final UsuarioRepository usuarioRepository;
    private final ArmazenamentoDeArquivos armazenamento;
    private final DetectorDeTipoDeArquivo detector;
    private final ApplicationEventPublisher publisher;
    private final long tamanhoMaximo;

    public ArquivoService(ArquivoRepository arquivoRepository,
                          ConteudoRepository conteudoRepository,
                          UsuarioRepository usuarioRepository,
                          ArmazenamentoDeArquivos armazenamento,
                          DetectorDeTipoDeArquivo detector,
                          ApplicationEventPublisher publisher,
                          DocumentosProperties properties) {
        this.arquivoRepository = arquivoRepository;
        this.conteudoRepository = conteudoRepository;
        this.usuarioRepository = usuarioRepository;
        this.armazenamento = armazenamento;
        this.detector = detector;
        this.publisher = publisher;
        this.tamanhoMaximo = properties.tamanhoMaximo().toBytes();
    }

    /** Anexa o arquivo ao conteúdo, depois de conferir tamanho e tipo. */
    @Transactional
    public ArquivoResponse enviar(UUID conteudoId, MultipartFile enviado, UsuarioAutenticado requisitante) {
        Conteudo conteudo = conteudoRepository.findByIdAndTenantId(conteudoId, requisitante.getTenantId())
                .orElseThrow(() -> conteudoNaoEncontrado(conteudoId));
        if (enviado == null || enviado.isEmpty()) {
            throw new CampoInvalidoException(DetectorDeTipoDeArquivo.CAMPO, "Envie um arquivo com conteúdo");
        }
        if (enviado.getSize() > tamanhoMaximo) {
            throw new CampoInvalidoException(DetectorDeTipoDeArquivo.CAMPO,
                    "O arquivo passa do tamanho máximo de " + descreverTamanho(tamanhoMaximo));
        }
        String nome = nomeSeguro(enviado.getOriginalFilename());
        byte[] bytes = lerBytes(enviado);
        TipoArquivo tipo = detector.detectar(nome, bytes);

        String chave = requisitante.getTenantId() + "/" + UUID.randomUUID();
        guardar(chave, bytes);
        apagarSeNaoConfirmar(chave);

        Arquivo arquivo = arquivoRepository.saveAndFlush(Arquivo.novo(
                conteudo, nome, tipo, bytes.length, chave,
                usuarioRepository.getReferenceById(requisitante.getId())));
        publisher.publishEvent(new ArquivoEnviadoEvent(arquivo.getId(), conteudo.getId(), requisitante.getTenantId()));
        return ArquivoResponse.de(arquivo);
    }

    /** Arquivos do conteúdo, na ordem de envio. Mesma visibilidade do conteúdo. */
    @Transactional(readOnly = true)
    public List<ArquivoResponse> listar(UUID conteudoId, UsuarioAutenticado requisitante) {
        Conteudo conteudo = conteudoRepository.findByIdAndTenantId(conteudoId, requisitante.getTenantId())
                .filter(encontrado -> encontrado.isPublicado() || podeVerRascunho(requisitante))
                .orElseThrow(() -> conteudoNaoEncontrado(conteudoId));
        return arquivoRepository
                .findByTenantIdAndConteudoIdOrderByCriadoEmAsc(requisitante.getTenantId(), conteudo.getId())
                .stream()
                .map(ArquivoResponse::de)
                .toList();
    }

    /** Abre o arquivo para download, com o nome original. */
    @Transactional(readOnly = true)
    public ArquivoParaDownload baixar(UUID arquivoId, UsuarioAutenticado requisitante) {
        Arquivo arquivo = arquivoRepository.findByIdAndTenantId(arquivoId, requisitante.getTenantId())
                .filter(encontrado -> encontrado.getConteudo().isPublicado() || podeVerRascunho(requisitante))
                .orElseThrow(() -> new ResourceNotFoundException("Arquivo não encontrado: " + arquivoId));
        try {
            return new ArquivoParaDownload(arquivo.getNomeOriginal(), arquivo.getMimeType(),
                    arquivo.getTamanhoBytes(), armazenamento.abrir(arquivo.getChaveArmazenamento()));
        } catch (IOException ex) {
            // Registro sem binário: para quem baixa, o arquivo não existe.
            log.error("Arquivo {} sem binário no armazenamento", arquivoId);
            throw new ResourceNotFoundException("Arquivo não encontrado: " + arquivoId);
        }
    }

    private void guardar(String chave, byte[] bytes) {
        try {
            armazenamento.salvar(chave, new ByteArrayInputStream(bytes));
        } catch (IOException ex) {
            throw new UncheckedIOException("Não foi possível guardar o arquivo", ex);
        }
    }

    /**
     * Se a transação não for confirmada (erro ao gravar o registro, por
     * exemplo), o binário já guardado é apagado.
     */
    private void apagarSeNaoConfirmar(String chave) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED) {
                    try {
                        armazenamento.remover(chave);
                    } catch (IOException | RuntimeException ex) {
                        log.warn("Não foi possível apagar o arquivo órfão {}", chave);
                    }
                }
            }
        });
    }

    private static byte[] lerBytes(MultipartFile enviado) {
        try {
            return enviado.getBytes();
        } catch (IOException ex) {
            throw new UncheckedIOException("Não foi possível ler o arquivo enviado", ex);
        }
    }

    /**
     * Só o nome do arquivo, sem pastas (alguns navegadores mandam o caminho
     * inteiro) e sem caracteres de controle. É usado no download, nunca no disco.
     */
    static String nomeSeguro(String original) {
        String nome = original == null ? "" : original;
        nome = nome.substring(Math.max(nome.lastIndexOf('/'), nome.lastIndexOf('\\')) + 1);
        nome = nome.replaceAll("\\p{Cntrl}", "").strip();
        if (nome.isEmpty()) {
            throw new CampoInvalidoException(DetectorDeTipoDeArquivo.CAMPO, "O arquivo precisa ter um nome");
        }
        if (nome.length() > NOME_MAXIMO) {
            int ponto = nome.lastIndexOf('.');
            String extensao = ponto > 0 && nome.length() - ponto <= 10 ? nome.substring(ponto) : "";
            nome = nome.substring(0, NOME_MAXIMO - extensao.length()) + extensao;
        }
        return nome;
    }

    /** "10 MB" ou "512 KB" — para a mensagem de erro do limite. */
    static String descreverTamanho(long bytes) {
        long mega = 1024L * 1024L;
        return bytes >= mega && bytes % mega == 0 ? bytes / mega + " MB" : Math.max(1, bytes / 1024) + " KB";
    }

    private static ResourceNotFoundException conteudoNaoEncontrado(UUID conteudoId) {
        return new ResourceNotFoundException("Conteúdo não encontrado: " + conteudoId);
    }

    private static boolean podeVerRascunho(UsuarioAutenticado requisitante) {
        return requisitante.getPapel() == Perfil.ADMIN || requisitante.getPapel() == Perfil.GESTOR;
    }
}
