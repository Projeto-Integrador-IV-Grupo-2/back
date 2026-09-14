# Nexo — Back-end (API)

API do projeto Nexo. Este repositório contém a **estrutura base** do back-end:
já roda localmente e está pronta para receber os módulos de **autenticação** e
**usuários**.


## Tecnologias

| Camada | O que usamos | Versão |
|---|---|---|
| Linguagem | Java | 21 |
| Framework | Spring Boot | 3.4 |
| Web / REST | Spring Web (MVC) | — |
| Banco de dados | PostgreSQL | 16 |
| Acesso a dados | Spring Data JPA (Hibernate) | — |
| Migrations | Flyway | — |
| Segurança | Spring Security | — |
| Validação | Bean Validation | — |
| Boilerplate | Lombok | — |
| Monitoramento | Spring Boot Actuator | — |
| Build | Maven (via wrapper `./mvnw`) | — |
| Testes | JUnit 5 + Spring Test + Testcontainers | — |
| Ambiente local | Docker + Docker Compose | — |

---

## O que instalar

Só três coisas. Instale, abra um terminal novo e confira com o comando da
última coluna.

| Ferramenta | Versão | Para quê serve | Conferir |
|---|---|---|---|
| **JDK 21** | 21 | Compilar e rodar a API | `java -version` → deve mostrar `21` |
| **Docker Desktop** | atual | Subir o PostgreSQL e rodar os testes | `docker --version` |
| **Git** | qualquer | Clonar e versionar o código | `git --version` |

> **Maven não precisa ser instalado.** O projeto traz o *wrapper*: use
> `./mvnw` no Linux/macOS ou `mvnw.cmd` no Windows. Ele baixa a versão certa
> do Maven sozinho na primeira execução.

Links: [JDK 21 (Temurin)](https://adoptium.net/temurin/releases/?version=21) ·
[Docker Desktop](https://www.docker.com/products/docker-desktop/)

---

## Como rodar

Tenha o **Docker Desktop aberto** e o **JDK 21** instalado
(ver [O que instalar](#o-que-instalar)).

Abra um terminal na pasta do projeto e rode **os dois comandos, nesta ordem**:

```bash
docker compose up -d       # 1. liga o banco de dados
./mvnw spring-boot:run     # 2. liga a API (fica rodando neste terminal)
```

Pronto. A API está em **http://localhost:8080**.

Você **não precisa configurar nada** — o `docker compose up -d` já cria o banco
com estes valores (definidos no [`docker-compose.yml`](docker-compose.yml)):

| Item | Valor |
|---|---|
| Banco | `nexo` |
| Usuário | `nexo` |
| Senha | `nexo` |
| Host / porta | `localhost:5432` |

Funciona igual em **Windows, macOS e Linux** — basta ter o Docker Desktop
aberto e o JDK 21. Cada integrante roda os mesmos dois comandos, sem ajustar
arquivo nenhum. (Só cria um `.env` quem quiser mudar algum desses valores;
veja [Variáveis de ambiente](#variáveis-de-ambiente).)

Para conferir, abra **outro terminal** e rode:

```bash
curl http://localhost:8080/api/health
```

Deve responder com código **200** e `{"status":"UP", ...}`.

### Para desligar

1. No terminal da API: aperte **`Ctrl+C`**.
2. Depois: `docker compose down` (desliga o banco; os dados continuam salvos).

### Comandos de Docker

| Comando | O que faz |
|---|---|
| `docker compose up -d` | Liga o banco |
| `docker compose down` | Desliga o banco (mantém os dados) |
| `docker compose ps` | Mostra se o banco está no ar |
| `docker compose logs -f db` | Mostra os logs do banco (`Ctrl+C` sai do log, não desliga) |
| `docker compose down -v` | Desliga e **apaga os dados** (para recomeçar do zero) |

<details>
<summary><b>Por que preciso do Docker?</b></summary>

<br>

O banco **PostgreSQL roda dentro do Docker**, então você não precisa instalar
PostgreSQL na máquina. O comando `docker compose up -d` lê o
[`docker-compose.yml`](docker-compose.yml) e sobe esse banco na porta `5432`
(usuário/senha/banco = `nexo`/`nexo`/`nexo`).

A **API roda fora do Docker** (com `./mvnw`) e se conecta nesse banco. Por isso
a ordem: banco primeiro, API depois. No start, o Flyway cria as tabelas
automaticamente.

Os dados ficam num volume chamado `nexo-db-data` e sobrevivem ao
`docker compose down` — só somem com `docker compose down -v`.

</details>

---

## Rodar os testes

```bash
./mvnw verify
```

- Compila o projeto e roda todos os testes.
- **Não precisa** rodar `docker compose up` antes: os testes sobem um
  **PostgreSQL descartável** sozinhos (Testcontainers), rodam as migrations de
  verdade e apagam tudo no fim.
- Só precisa do **Docker Desktop aberto**.

---

## Gerar o `.jar` (build final)

```bash
./mvnw clean package
java -jar target/nexo-api-0.0.1-SNAPSHOT.jar
```

Gera um `.jar` executável com a aplicação e todas as dependências dentro.

---

## O que vai em cada pasta

Código em [`src/main/java/com/nexo/`](src/main/java/com/nexo/). A organização
segue o padrão MVC em camadas:

| Pasta | O que colocar aqui | Exemplo |
|---|---|---|
| `config` | Configurações da aplicação: `@Configuration`, beans, CORS, leitura de *properties*. | `CorsConfig.java` |
| `controller` | Endpoints REST. Recebe a requisição HTTP, chama o `service` e devolve a resposta. **Sem regra de negócio.** | `UserController.java` (`POST /api/users`) |
| `dto` | Formato dos dados que **entram e saem** da API (corpo de request/response). Nunca expor a `entity` direto. | `CreateUserRequest.java`, `UserResponse.java` |
| `service` | **Regra de negócio.** Valida, decide, coordena os repositórios. É onde fica a lógica. | `UserService.java` |
| `repository` | Interfaces de acesso ao banco (Spring Data JPA). Só a assinatura; o Spring implementa. | `interface UserRepository extends JpaRepository<User, UUID>` |
| `entity` | Classes `@Entity` que representam as tabelas do banco. | `User.java` (tabela `usuario`) |
| `security` | Configuração do Spring Security e filtros de autenticação (ex.: JWT). | `SecurityConfig.java`, futuro `JwtAuthFilter.java` |
| `exception` | Exceções próprias + o tratador global que transforma erro em JSON padronizado. | `ResourceNotFoundException.java`, `GlobalExceptionHandler.java` |

> As pastas `entity`, `repository` e `service` estão vazias por enquanto (só com
> um arquivo `.gitkeep` para o Git manter a pasta). Os próximos módulos preenchem.

### Como as camadas conversam

```
Requisição HTTP
      │
      ▼
  controller   ── valida formato, converte DTO
      │
      ▼
   service     ── regra de negócio
      │
      ▼
  repository   ── consulta / grava
      │
      ▼
 banco (Postgres)
```

A resposta volta pelo caminho inverso: `entity` → o `service` monta um `dto` →
o `controller` devolve como JSON.

### Outras pastas do projeto

| Caminho | Para quê |
|---|---|
| `src/main/resources/application.yml` | Configuração da app + perfis `dev` e `test` |
| `src/main/resources/db/migration/` | Migrations do Flyway (`V1__init.sql` … `V5__seed_initial_admin.sql`) |
| `src/test/java/com/nexo/` | Testes automatizados |
| `.github/workflows/` | Pipeline de CI (build + testes) |
| `.github/pull_request_template.md` | Checklist que aparece ao abrir um PR |

---

## Lombok

O projeto usa [Lombok](https://projectlombok.org/) para não escrever à mão
construtor de injeção, logger e builder. O que está em uso hoje:

| Anotação | Onde | O que ela substitui |
|---|---|---|
| `@RequiredArgsConstructor` | `HealthController` | Construtor que recebe os campos `final` |
| `@Slf4j` | `GlobalExceptionHandler` | `private static final Logger log = LoggerFactory.getLogger(...)` |
| `@Builder` | `ApiError` | Construtor com seis argumentos posicionais |
| `@StandardException` | `ResourceNotFoundException` | Os quatro construtores padrão de exceção |

O [`lombok.config`](lombok.config) na raiz do projeto manda o Lombok copiar
`@Value` e `@Qualifier` do campo para o parâmetro do construtor gerado — é isso
que faz `@RequiredArgsConstructor` funcionar junto com injeção de propriedade.

**No IDE:** instale o plugin do Lombok (IntelliJ: *Settings → Plugins → Lombok*;
VS Code: extensão *Lombok Annotations Support*) e deixe o *annotation
processing* ligado. Sem o plugin o editor acusa erro em métodos gerados, mesmo
com o `./mvnw` compilando normalmente. A dependência é `optional` e não vai
dentro do `.jar` final.

---

## Variáveis de ambiente

Nenhum segredo fica no código. A aplicação lê tudo de **variáveis de ambiente**.
Os valores abaixo são apenas *defaults* de desenvolvimento — em outros ambientes,
define-se a variável e ela prevalece.

| Variável | Default (dev) | Descrição |
|---|---|---|
| `SPRING_PROFILES_ACTIVE` | `dev` | Perfil ativo (`dev` ou `test`) |
| `SERVER_PORT` | `8080` | Porta HTTP da API |
| `DB_HOST` | `localhost` | Host do PostgreSQL |
| `DB_PORT` | `5432` | Porta do PostgreSQL |
| `DB_NAME` | `nexo` | Nome do banco |
| `DB_USERNAME` | `nexo` | Usuário do banco |
| `DB_PASSWORD` | `nexo` | Senha do banco |
| `DB_POOL_MAX` | `10` | Tamanho máximo do pool de conexões (HikariCP) |
| `CORS_ALLOWED_ORIGINS` | `http://localhost:5173,http://localhost:3000` | Origens do frontend liberadas no CORS (separadas por vírgula) |
| `CORS_ALLOWED_METHODS` | `GET,POST,PUT,PATCH,DELETE,OPTIONS` | Métodos HTTP liberados no CORS |
| `CORS_ALLOWED_HEADERS` | `*` | Cabeçalhos liberados no CORS |
| `CORS_ALLOW_CREDENTIALS` | `true` | Permite cookies/`Authorization` vindos do browser |

Para o Docker Compose, copie o modelo (opcional — há defaults):

```bash
cp .env.example .env
```

> `.env` está no `.gitignore` e **nunca** é versionado. Só o `.env.example`
> (sem valores reais) fica no repositório.

**CORS:** ajuste `CORS_ALLOWED_ORIGINS` para o endereço em que o front roda.
Use origens explícitas (não `*`), porque o envio de credenciais está habilitado.

---

## Quando o CI roda

Pipeline: [`.github/workflows/backend-ci.yml`](.github/workflows/backend-ci.yml).

**Dispara quando:**

- é aberto ou atualizado um **Pull Request** para `dev` ou `main`;
- há **push** para `dev` ou `main` (ou seja, quando um PR é mergeado).

O dia a dia acontece em `dev`: você abre PR da sua branch (`feat/...`) para
`dev`, e o CI roda nesse PR. `main` recebe `dev` de tempos em tempos (versão
estável), também via PR.

**O que ele faz:**

1. Baixa o código.
2. Instala o **JDK 21** (com cache do Maven).
3. Roda `./mvnw verify` — compila e executa **todos os testes** (o Docker do
   runner cobre o Testcontainers).

**Se falhar:** o check fica vermelho. Com a
[proteção das branches](#proteção-das-branches) ativa, isso **bloqueia o merge**
do PR. O workflow **não usa segredos**.

---

## Migrations (Flyway)

- Local: `src/main/resources/db/migration/`
- Nome: `V<número>__descricao_curta.sql` — ex.: `V4__create_users.sql`
- Rodam **sozinhas** no start da API e no início dos testes.
- **Nunca edite** uma migration já aplicada — crie sempre a próxima versão.
- O schema é propriedade do Flyway: o JPA roda com `ddl-auto: validate`, então
  toda `@Entity` precisa bater exatamente com a tabela migrada, senão a
  aplicação nem sobe.

### O que já existe no banco

| Versão | Arquivo | O que cria |
|---|---|---|
| V1 | `V1__init.sql` | Extensão `pgcrypto` (habilita `gen_random_uuid()`) |
| V2 | `V2__create_tenant_and_catalogs.sql` | Tabelas `tenant`, `area` e `cargo` |
| V3 | `V3__create_user_profiles.sql` | Enum `papel_usuario` (`ADMIN`, `GESTOR`, `COLABORADOR`) |
| V4 | `V4__create_users.sql` | Tabela `usuario` + índices de login e de filtro |
| V5 | `V5__seed_initial_admin.sql` | Semeia o tenant `nexo` e o admin `admin@nexo.com` (idempotente) |

Regras do modelo que o código precisa respeitar:

- **Multi-tenant:** `area`, `cargo` e `usuario` têm `tenant_id` obrigatório. O
  e-mail é único **por tenant** (`uq_usuario_tenant_email`), não global — toda
  consulta de domínio filtra por tenant.
- **Senha:** `usuario.senha_hash` tem um CHECK que só aceita hash BCrypt (com o
  prefixo `{bcrypt}` opcional). Senha em texto puro é recusada pelo banco.
- **Hierarquia:** `usuario.gestor_id` é auto-referência (o gestor é outro usuário).
- **Nomes:** tabelas e colunas em **português e no singular** (`usuario`,
  `nome_completo`, `criado_em`).
- `MigrationSchemaTest` valida o enum, o seed, a unicidade por tenant, o CHECK de
  BCrypt e os índices contra um PostgreSQL real. Migration nova que mude esses
  contratos quebra o teste.

> O hash do admin semeado é um BCrypt de exemplo, público, só para o ambiente
> local. Troque a senha antes de subir qualquer ambiente que não seja a máquina
> do desenvolvedor.

---

## Fluxo de contribuição

O fluxo é **o mesmo para back-end e front-end**.

1. Atualize sua cópia local de `dev`.
2. Crie uma branch a partir de `dev` (ver [padrão de branches](#padrão-de-branches)).
3. Faça commits pequenos em [Conventional Commits](#padrão-de-commits).
4. Abra um **Pull Request** para `dev` com o template preenchido.
5. Garanta **CI verde** e resolva os comentários da revisão.
6. Com **pelo menos 1 aprovação**, faça **Squash and merge** e apague a branch.

Ninguém dá push direto em `dev` ou `main` — as duas são
[protegidas](#proteção-das-branches). `main` só recebe código vindo de `dev`,
também via PR (é a versão estável).

### Padrão de branches

Formato: `<tipo>/<descrição-curta-em-kebab-case>`

| Prefixo | Quando usar |
|---|---|
| `feat/` | nova funcionalidade |
| `fix/` | correção de bug |
| `refactor/` | refatoração sem mudar comportamento |
| `docs/` | documentação |
| `test/` | apenas testes |
| `chore/` | build, CI, dependências, manutenção |
| `hotfix/` | correção urgente |

Exemplos: `feat/autenticacao-jwt` · `fix/health-check-timestamp` ·
`chore/configura-flyway` · `feat/42-cadastro-de-usuario` (com o nº da issue).

### Padrão de commits

[Conventional Commits](https://www.conventionalcommits.org/):

```
<tipo>(escopo opcional): descrição no imperativo

[corpo opcional explicando o porquê]

[rodapé opcional: BREAKING CHANGE: ... / Refs #123]
```

**Tipos:** `feat`, `fix`, `docs`, `style`, `refactor`, `perf`, `test`, `build`,
`ci`, `chore`, `revert`.

**Regras do assunto:** verbo no imperativo ("adiciona", não "adicionado");
começa em minúscula; sem ponto final; até ~72 caracteres.

**Exemplos:**

```
feat(auth): adiciona geração de token JWT no login
fix(cors): libera o header Authorization para o front
docs: documenta as variáveis de ambiente no README
ci: roda mvnw verify em pull requests para dev
```

### Pull Request

- Abra o PR **para `dev`** (a promoção `dev` → `main` também é via PR).
- O **template é carregado automaticamente** — preencha todas as seções e o
  checklist de DoD abaixo.
- 1 PR = 1 assunto. PRs pequenos são revisados mais rápido.
- Exigido para o merge (imposto pela proteção da branch):
  - check **Backend CI** verde;
  - **pelo menos 1 aprovação** de outra pessoa;
  - nenhuma conversa de revisão em aberto;
  - branch atualizada com `dev`.
- Use **Squash and merge** e **apague a branch** depois.

### Definition of Done (DoD)

A tarefa só está concluída quando:

- [ ] O código está na pasta certa (`config`, `controller`, `service`,
      `repository`, `entity`, `dto`, `security`, `exception`).
- [ ] `./mvnw verify` passa localmente (build + testes).
- [ ] Novos comportamentos têm testes automatizados.
- [ ] Nenhum segredo, senha, token ou `.env` foi versionado.
- [ ] Config de ambiente está fora do código Java (`application.yml` / env vars).
- [ ] Migrations novas são incrementais; nenhuma migration aplicada foi editada.
- [ ] README atualizado se o setup mudou.
- [ ] Branch e commits seguem os padrões acima.
- [ ] CI verde e PR aprovado.

---

## Proteção das branches

`dev` e `main` precisam exigir **Pull Request revisado e CI verde** antes de
qualquer merge; push direto fica bloqueado. A configuração é feita por alguém
com permissão de **admin** no repositório, na interface do GitHub.
**Aplique a mesma coisa no repositório do front-end.**

**Pré-requisitos:** a branch já existe no GitHub com pelo menos 1 commit, e o
workflow **Backend CI** já rodou uma vez (abra um PR qualquer) para o status
check aparecer pelo nome na lista.

### Como verificar que ficou ativo

- `git push` direto em `dev` ou `main` é rejeitado.
- PR sem aprovação não habilita o botão de merge.
- PR com **Backend CI** vermelho não pode ser mergeado.

---

## Problemas comuns

| Sintoma | Causa provável / solução |
|---|---|
| `Connection refused` na porta 5432 | O banco não subiu → `docker compose up -d` |
| Testes falham com erro de Docker | Docker Desktop não está aberto/rodando |
| `UnsupportedClassVersionError` | JDK errado → use o **21** (`java -version`) |
| Porta 8080 já em uso | Defina outra em `SERVER_PORT` |
| `./mvnw: Permission denied` (Linux/macOS) | `chmod +x ./mvnw` |
| Front recebe erro de CORS no browser | Inclua a origem do front em `CORS_ALLOWED_ORIGINS` |
