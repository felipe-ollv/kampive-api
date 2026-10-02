# Arquitetura

## Dependências

`adapter/in/web -> application/port/in -> application/usecase -> domain`.
Casos de uso dependem de portas de saída; adapters de persistência e storage as
implementam. Spring conecta as implementações em configuration. Domain e application
não importam Spring, JPA, Servlet ou adapters. Transações futuras serão aplicadas
por decorators/configuração externos ao núcleo.

Entidades JPA, domínio e DTOs HTTP são separados. Controllers validam formato;
casos de uso verificam autorização por recurso; domínio mantém invariantes.

## Módulos

| Módulo | Responsabilidade |
| --- | --- |
| identity | Autenticação, credenciais e identidades externas |
| user | Perfil, preferências e aceites |
| geography | Continentes, países, divisões administrativas e cidades |
| campsite | Campings, categorias, ambientes, comodidades, contatos e fotos |
| review | Avaliações, destaques e fotos |
| favorite | Campings salvos por usuário |
| notification | Mensagens, destinatários e leitura |

Identity, user e geography já possuem portas, casos de uso e adapters JDBC. Os demais módulos mantêm documentação de pacote.
Para cada funcionalidade, criar application/port/{in,out}, application/{usecase,command,query,result},
adapter/in/web/{controller,request,response,mapper} e adapter/out/persistence/{entity,repository,mapper}
conforme necessário. Não compartilhar repositories ou entidades entre módulos.
Consumir outros módulos apenas por portas de entrada e seus contratos.

## Modelo aprovado

Nomes em inglês, singular e snake_case. id BIGINT IDENTITY é a PK interna;
uuid_<table_name> é NOT NULL UNIQUE. FKs e contratos públicos usam UUID.
UUID não substitui autorização.

Seguir 1FN, 2FN e 3FN. Fotos, comodidades e destaques são relações. Médias, contagens
e leitura são derivadas. Limites de 9 fotos por camping e 3 por avaliação, com posição
única por proprietário. Os limites já estão no DDL manual; seus futuros casos de uso também devem verificá-los.

## Configuração

application.yml: datasource obrigatório, schema kampive, Hibernate validate, SQL init
desabilitado. application-local.yml: logs INFO. application-test.yml fica apenas em
src/test/resources e exclui a autoconfiguração de banco dos testes HTTP.
O perfil de teste não integra a imagem de produção.

## Identidade e usuários

IdentityService depende de UserAccounts (porta de entrada de user), CredentialStore
(porta de saída de identity) e PasswordHasher. UserService delega ao UserRepository.
Adapters JDBC usam parâmetros nomeados; nenhum ID numérico é exposto.
IdentityConfiguration aplica a transação externa ao núcleo, cobrindo app_user,
user_credential e document_acceptance. Violações de integridade viram erro de
cadastro genérico, inclusive conflitos concorrentes de e-mail.
O módulo identity administra documentos legais, credenciais e seus aceites;
user é o único que escreve app_user. O controller cria a sessão somente após autenticação.

## Catálogo geográfico

GeographyCatalog é a porta de entrada; GeographyService valida os contratos de
paginação e usa GeographyRepository. O adapter JDBC lê países/continentes e a
view de contagem de campings publicados, sem acessar adapters de outro módulo.
O controller expõe somente GET e os contratos públicos não incluem IDs internos.
