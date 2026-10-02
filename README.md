# Kampive API

Base Java 21, Spring Boot 4.1.1, Maven Wrapper 3.9.16 e PostgreSQL 18.
Monólito modular hexagonal. Cadastro, autenticação por sessão, consulta do próprio perfil e catálogo de continentes/países. Persistência por JDBC, sem entidades JPA.

## Executar

Clone `kampive-api` e `kampive-infra` lado a lado. Siga o README da infra para preparar
o `.env`, iniciar PostgreSQL e provisionar manualmente a role restrita. Na infra:

```sh
docker compose up -d --build
curl --fail http://localhost:8080/actuator/health
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

- Interface: http://localhost:8080/swagger-ui.html
- OpenAPI JSON: http://localhost:8080/v3/api-docs
- OpenAPI YAML: http://localhost:8080/v3/api-docs.yaml

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
