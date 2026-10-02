# Autenticação e usuários

Todos os caminhos abaixo começam com `/api/v1`. Use o Swagger local para consultar
schemas, corpos e o parâmetro `X-CSRF-TOKEN` dos POSTs.

| Método | Caminho | Comportamento |
| --- | --- | --- |
| GET | /auth/csrf | Cria/usa a sessão e retorna headerName e token |
| GET | /auth/legal-documents | Termos e política de privacidade publicados mais recentes |
| POST | /auth/register | Cria usuário, hash de senha e dois aceites em uma transação; 201 |
| POST | /auth/login | Autentica e troca o ID da sessão; 200 com perfil |
| GET | /users/me | Perfil do usuário da sessão; 401 sem sessão ativa |
| POST | /auth/logout | Invalida sessão e cookie; 204 |

## Fluxo para clientes e Swagger

1. Execute GET /auth/csrf e preserve o cookie JSESSIONID. Copie o token retornado.
2. Consulte /auth/legal-documents. O cadastro exige os dois UUIDs atuais em
   acceptedDocumentUuids; se não houver os dois documentos publicados, será recusado.
3. Cadastre nome (3–150), email (até 254) e senha (15–128 caracteres). countryUuid
   e adventurerStyleUuid são opcionais, mas precisam existir quando informados.
   Envie o token no header X-CSRF-TOKEN e preserve o cookie em todos os pedidos.
4. Faça login com e-mail e senha. Cadastro não autentica automaticamente.
5. Obtenha outro token CSRF após login, pois o anterior foi invalidado; o cliente
   deve aceitar o cookie com o novo ID. Use /users/me e POST /auth/logout.
6. Após logout, obtenha uma nova sessão/token antes de novo login.

No Swagger, o navegador administra o cookie no mesmo domínio; não é necessário
colar um bearer token. Preencha X-CSRF-TOKEN nos POSTs com o valor do passo 1/5.
A API não habilita CORS irrestrito. Integração mobile e armazenamento seguro do
cliente serão tratados em etapa posterior; o frontend ainda é demonstrativo.

## Documentos legais

Não há seeds automáticos ou textos jurídicos fictícios. Publique manualmente em
kampive.legal_document os documentos reais revisados (terms_of_use e privacy_policy),
com versão, URL e published_at. Novas versões são novas linhas; não altere versões
já aceitas. O cadastro persiste o UUID exato e o horário do aceite. Documentos futuros
não são elegíveis. O endpoint retorna uma lista vazia em um banco sem publicações.

## Limites da entrega

Sessões ficam na memória de uma única API: reinício invalida logins. Não há refresh
JWT, lembrar-me persistente, login social/SMS, confirmação de e-mail, recuperação de
senha, upload ou edição de perfil. emailVerified inicia false. O login verifica que
a conta não está desativada; /users/me também consulta o estado atual. Futuras rotas
protegidas deverão validar o estado e a autorização sobre cada recurso.

Erros: 400 validação/aceites, 401 credenciais/sessão, 403 CSRF/acesso,
409 conflito de cadastro, 429 limite de tentativas e 500 genérico inesperado.
Senhas, hashes e detalhes de SQL não são retornados. A política de senha do app
atual ainda precisa ser alinhada aos 15 caracteres ao conectar o frontend.

## Validação

O build Docker executa testes de casos de uso, segurança HTTP, sessões, hash,
limite de tentativas e arquitetura. A integração com PostgreSQL pode ser verificada
com `python3 scripts/verify-auth.py` depois de construir kampive-api:0.0.1-local.
O script exige Docker e o schema em ../kampive-infra/sql/schema.sql; cria containers
isolados, sem volumes persistentes, e os remove ao terminar. Não altera o banco local.
