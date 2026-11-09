# Kampive API

Base Java 21, Spring Boot 4.1.1, Maven Wrapper 3.9.16 e PostgreSQL 18.
Monólito modular hexagonal. Cadastro, autenticação por sessão, consulta do próprio perfil e catálogo de continentes/países. Persistência por JDBC, sem entidades JPA.

## Executar

Clone `kampive-api` e `kampive-infra` lado a lado. Siga o README da infra para preparar
o `.env`, iniciar PostgreSQL e provisionar manualmente a role restrita. Na infra:

```sh
docker compose up -d --build
curl --cacert certs/kampive-local-root.crt --fail https://localhost:8443/actuator/health
docker compose logs -f api
```

`GET /actuator/health` é público, sem detalhes internos. No perfil local, a documentação também é pública. Não é necessário
instalar Java ou Maven no host. O build usa Java 21 no Docker; o runtime usa JRE 21.

## Testes

Na raiz da API:

```sh
docker build --target build --progress=plain -t kampive-api-tests:local .
```

O build executa `./mvnw -B -ntp verify` (arquitetura, segurança HTTP e erros).
Adicione `--no-cache` para forçar nova execução sem mudanças no código. Os testes
não precisam de PostgreSQL e não alteram bancos. Com JDK 21 local, `./mvnw verify`
também funciona. Não há hot reload: reconstrua a imagem após alterações.

Para execução fora do Docker, defina `DB_URL`, `DB_USERNAME`, `DB_PASSWORD` e use
`./mvnw spring-boot:run`. Spring não carrega `.env` automaticamente; o Compose faz
a leitura na infra.

## Organização

- `configuration`: composição e configurações Spring transversais.
- `shared`: contratos e infraestrutura compartilhados, sem regras específicas.
- `module`: identity, user, geography, campsite, review, favorite, notification.
- Cada módulo possui domain, application, adapter e configuration documentados
  por package-info.java. Classes serão adicionadas conforme os casos de uso.

Veja [arquitetura](docs/architecture.md) e [segurança](docs/security.md).

## Banco

Sem Flyway, Liquibase ou execução automática de SQL. O schema manual fica em kampive-infra/sql/schema.sql.
Hibernate usa validate, SQL init está desabilitado e Open Session in View desligado.
Como ainda não há entidades, validate não comprova a existência do modelo de negócio.
O DDL é aplicado manualmente por um administrador. A API utiliza `kampive_app`,
sem DDL. Não há JWT, uploads, push ou dados de exemplo nesta entrega.

## OpenAPI e Swagger UI

No Docker Compose (perfil `local`):

- Interface: https://localhost:8443/swagger-ui.html
- OpenAPI JSON: https://localhost:8443/v3/api-docs
- OpenAPI YAML: https://localhost:8443/v3/api-docs.yaml

Integração com [springdoc-openapi 3.1.1](https://springdoc.org/), compatível com Spring Boot 4.
A documentação fica desabilitada por padrão e é habilitada no perfil local por
`app.documentation.enabled`. Somente GET nas rotas de documentação é liberado;
CSRF permanece ativo; somente as rotas explicitamente implementadas são liberadas.
A autenticação usa cookie HttpOnly de sessão, sem esquema Bearer.
A especificação inclui autenticação e perfil. Novos controllers serão
incluídos automaticamente; mantenha anotações OpenAPI nos adapters web, fora do núcleo.
Actuator não faz parte da especificação. O Swagger usa recursos locais e não envia
a especificação a um validador externo. Reconstrua a imagem após alterações.

Veja [fluxo de autenticação, contratos e testes](docs/authentication.md).

Veja [contratos e filtros do catálogo geográfico](docs/geography.md).

O Compose usa HTTPS via Caddy. Siga o README da infra para exportar a CA pública e confiar nela no simulador.

## Campings: indicação e propriedade

O catálogo permite campings sem proprietário vinculado. Usuários enviam indicações; a equipe cadastra e valida os dados. Reivindicações de propriedade só liberam gestão depois de revisão administrativa.

Antes de executar esta versão, aplicar manualmente `docs/sql/campsite-community.sql` e depois `docs/sql/campsite-registration.sql` após o schema base da infra. Não há execução automática de DDL. Vínculos antigos precisam de revisão; `uuid_owner` sozinho não concede acesso.

Consulte [contratos, preparação do banco e procedimentos de revisão](docs/campsite-community.md).

O catálogo público, filtros, paginação, fotos HTTPS e a gestão de rascunhos por proprietários aprovados permanecem disponíveis. Criação direta por usuários foi substituída por `POST /api/v1/campsite-suggestions`; solicitações de propriedade usam `POST /api/v1/campsites/{uuid}/claims`.

## Dados da comunidade

Favoritos, caixa de notificações e avaliações agora usam o banco e a sessão da conta. Consulte [contratos e testes Docker](docs/community-data.md). Não há dados de demonstração nem DDL adicional para esses três módulos.

## Acompanhamento de solicitações

Lista e detalhes privados de indicações e pedidos de propriedade, com respostas públicas e notificações transacionais da moderação. Aplicar `docs/sql/request-tracking.sql` antes de atualizar a API. Consulte [contratos e procedimento administrativo](docs/request-tracking.md).

## Templates de e-mail

Recuperação e confirmação de alteração de senha têm HTML responsivo e texto simples, com versões no banco. Aplicar `docs/sql/email-templates.sql` como administrador. Consulte [variáveis, publicação e limitações](emails/README.md). A porta interna `EmailTemplates` renderiza o conteúdo; o provedor e o fluxo de envio ainda serão integrados.

## Recuperação de senha

Endpoints, tokens de uso único, fila transacional e invalidação de sessões implementados. Aplicar `docs/sql/password-recovery.sql`. O adapter de provedor permanece indisponível até a escolha do serviço. Veja [fluxo, configuração e testes](docs/password-recovery.md).

A confirmação de e-mail, seus endpoints e a transição de contas existentes estão documentados em [docs/email-verification.md](docs/email-verification.md).
