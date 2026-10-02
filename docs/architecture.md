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

Os módulos começam com documentação de pacote, sem serviços fictícios ou CRUD genérico.
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
única por proprietário. Estes limites serão implementados nos casos de uso e no DDL;
nenhum modelo de negócio é criado nesta base.

## Configuração

application.yml: datasource obrigatório, schema kampive, Hibernate validate, SQL init
desabilitado. application-local.yml: logs INFO. application-test.yml fica apenas em
src/test/resources e exclui a autoconfiguração de banco dos testes HTTP.
O perfil de teste não integra a imagem de produção.
