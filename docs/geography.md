# Catálogo geográfico

Consultas públicas e somente leitura para a descoberta por países e a seleção de
país no cadastro. Sem novas tabelas, migrations, seeds automáticos ou alterações no frontend.

| Método | Endpoint | Resultado |
| --- | --- | --- |
| GET | /api/v1/continents | Lista de UUID e nome em ordem alfabética |
| GET | /api/v1/countries | Página de países com filtros opcionais |
| GET | /api/v1/countries/{countryUuid} | País ou 404 |

## Filtros e paginação

`continentUuid`: filtra por UUID. Um UUID válido sem países resulta em página vazia.
`query`: busca literal por trecho do nome ou código ISO, sem diferenciar maiúsculas;
no máximo 120 caracteres, espaços externos removidos. Não remove acentos. Caracteres
como `%` e `_` não viram curingas de SQL.
`page`: índice a partir de zero, padrão 0, máximo 10000.
`size`: tamanho da página, padrão 20, mínimo 1, máximo 100.

Exemplo: `/api/v1/countries?query=br&page=0&size=20`.
A resposta possui `items`, `page`, `size`, `totalElements`, `totalPages`.
Página além da última retorna items vazio, preservando o total filtrado.
Ordenação fixa por nome sem diferenciar maiúsculas e UUID como desempate.

Cada país contém `uuid`, `name`, `isoCode`, `imageUrl` (pode ser null),
`continentUuid`, `continentName` e `campsiteCount`. IDs internos não são expostos.
A contagem usa a view `vw_country_campsite_summary` e considera somente campings
publicados associados a cidades do país. Campings em rascunho não são contados.
Os nomes retornados são os cadastrados no banco; não há tradução automática.

UUID malformado ou paginação/busca inválida retorna 400 com ProblemDetail genérico.
Os endpoints não permitem criação, edição ou exclusão, mesmo com sessão autenticada.
Os demais controles de acesso e CSRF permanecem ativos.

## Dados e validação

O catálogo deve ser provisionado manualmente no banco por um administrador.
Um banco sem continentes e países retorna listas vazias; não são criados dados de
exemplo automaticamente. Divisões administrativas e cidades permanecem no schema,
mas seus endpoints não fazem parte desta etapa.

O build Docker executa testes de HTTP, casos de uso e arquitetura. O comando
`python3 scripts/verify-auth.py` também verifica este módulo com PostgreSQL isolado:
catálogo vazio, ordenação, páginas, filtros, ausência de curingas/injeção, UUIDs e
contagem de campings publicados. Os dados e containers de teste são descartados.
