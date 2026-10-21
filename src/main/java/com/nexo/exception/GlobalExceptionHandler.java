package com.nexo.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduz exceções da aplicação para respostas {@link ApiError} consistentes.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(ResourceNotFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, ex.getMessage(), request);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldValidationError> fields = ex.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Falha de validação",
                request.getRequestURI(),
                fields);
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiError> handleConstraintViolation(ConstraintViolationException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiError> handleAccessDenied(AccessDeniedException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Acesso negado", request);
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ApiError> handleBadCredentials(BadCredentialsException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "E-mail ou senha inválidos", request);
    }

    @ExceptionHandler(DisabledException.class)
    public ResponseEntity<ApiError> handleDisabled(DisabledException ex, HttpServletRequest request) {
        return build(HttpStatus.FORBIDDEN, "Usuário inativo", request);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ApiError> handleAuthentication(AuthenticationException ex, HttpServletRequest request) {
        return build(HttpStatus.UNAUTHORIZED, "Falha na autenticação", request);
    }

    /** Regra de negócio violada (ex.: e-mail duplicado, auto-desativação). */
    @ExceptionHandler(NegocioException.class)
    public ResponseEntity<ApiError> handleNegocio(NegocioException ex, HttpServletRequest request) {
        return build(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    /**
     * Corpo da requisição malformado ou com valor incompatível (ex.: um
     * perfil/situação inexistente no enum). Sem este handler, essa exceção
     * cairia no catch-all genérico abaixo e viraria HTTP 500 — um erro
     * causado pelo cliente nunca deve aparentar ser uma falha do servidor.
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadableBody(HttpMessageNotReadableException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Corpo da requisição inválido ou malformado", request);
    }

    /** Path variable ou query param com tipo/valor incompatível (ex.: UUID ou enum inválido). */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiError> handleTypeMismatch(MethodArgumentTypeMismatchException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Parâmetro '" + ex.getName() + "' com valor inválido", request);
    }

    /**
     * Falha ao converter query params em um objeto via {@code @ModelAttribute}
     * (ex.: {@code GET /api/usuarios?papel=NAO_EXISTE} — {@code UsuarioFiltro}
     * é resolvido assim). Esse mecanismo de bind lança {@link BindException},
     * não {@link MethodArgumentTypeMismatchException} — sem este handler, cairia
     * no catch-all genérico abaixo e viraria HTTP 500. Como
     * {@link MethodArgumentNotValidException} é uma subclasse de
     * {@code BindException}, o handler mais específico dela continua sendo
     * escolhido primeiro para o caso de {@code @Valid @RequestBody}; este
     * cobre o restante (bind de {@code @ModelAttribute}).
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<ApiError> handleBind(BindException ex, HttpServletRequest request) {
        List<ApiError.FieldValidationError> fields = ex.getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                "Parâmetros de requisição inválidos",
                request.getRequestURI(),
                fields);
        return ResponseEntity.badRequest().body(body);
    }

    /** Sem esta regra o handler genérico abaixo transformaria rota inexistente em 500. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiError> handleNoResource(NoResourceFoundException ex, HttpServletRequest request) {
        return build(HttpStatus.NOT_FOUND, "Recurso não encontrado", request);
    }

    @ExceptionHandler(ParametroInvalidoException.class)
    public ResponseEntity<ApiError> handleParametroInvalido(ParametroInvalidoException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, ex.getMessage(), request);
    }

    /** Campo recusado por regra de negócio (ex.: link do Vimeo inválido) — vai no {@code fieldErrors}. */
    @ExceptionHandler(CampoInvalidoException.class)
    public ResponseEntity<ApiError> handleCampoInvalido(CampoInvalidoException ex, HttpServletRequest request) {
        ApiError body = new ApiError(
                Instant.now(),
                HttpStatus.BAD_REQUEST.value(),
                HttpStatus.BAD_REQUEST.getReasonPhrase(),
                ex.getMessage(),
                request.getRequestURI(),
                List.of(new ApiError.FieldValidationError(ex.getCampo(), ex.getMessage())));
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(ServicoExternoIndisponivelException.class)
    public ResponseEntity<ApiError> handleServicoExterno(ServicoExternoIndisponivelException ex,
                                                         HttpServletRequest request) {
        log.warn("Serviço externo indisponível em {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, ex.getMessage(), request);
    }

    /** Assistente de IA sem chave, fora do ar ou no limite de uso. A mensagem nunca traz o prompt. */
    @ExceptionHandler(IaIndisponivelException.class)
    public ResponseEntity<ApiError> handleIaIndisponivel(IaIndisponivelException ex, HttpServletRequest request) {
        log.warn("Assistente de IA indisponível em {}: {}", request.getRequestURI(), ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), request);
    }

    /** Ex.: {@code DELETE} numa rota que só aceita {@code GET}. Sem isto viraria 500. */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex,
                                                             HttpServletRequest request) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, "Método " + ex.getMethod() + " não suportado nesta rota", request);
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex,
                                                                HttpServletRequest request) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Envie o corpo como application/json", request);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiError> handleMissingParameter(MissingServletRequestParameterException ex,
                                                           HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Parâmetro obrigatório ausente: " + ex.getParameterName(), request);
    }

    /** {@code ?sort=campoQueNaoExiste} numa listagem paginada. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiError> handlePropertyReference(PropertyReferenceException ex, HttpServletRequest request) {
        return build(HttpStatus.BAD_REQUEST, "Campo de ordenação inválido: " + ex.getPropertyName(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Erro não tratado em {}", request.getRequestURI(), ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Erro interno inesperado", request);
    }

    private ApiError.FieldValidationError toFieldError(FieldError fieldError) {
        return new ApiError.FieldValidationError(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private ResponseEntity<ApiError> build(HttpStatus status, String message, HttpServletRequest request) {
        ApiError body = ApiError.of(status.value(), status.getReasonPhrase(), message, request.getRequestURI());
        return ResponseEntity.status(status).body(body);
    }
}
