# API do portal administrativo

Aplicar `docs/sql/manager.sql` uma vez após `administrator-alerts.sql`. A migração é aditiva: cria auditoria e revisões para controle de concorrência, sem modificar decisões existentes.

As rotas `/api/v1/admin` exigem conta ativa, e-mail confirmado e concessão administrativa atual no banco. Mutações também exigem CSRF. Não há autorização pelo e-mail enviado pelo cliente nem concessão via perfil. Os UUIDs dos revisores são obtidos da sessão. Funções SECURITY DEFINER possuem search_path restrito e conferem a concessão novamente antes de escrever.

- GET `/admin/me`: autorização atual.
- GET `/admin/{requests|campings|reviews|users|history}?page=0&size=25`: tamanho de 1 a 100, ordenação determinística por data e ID, totais e itens no mesmo snapshot. Filtros parametrizados: query, status, kind, id, userId, ownerId, from e to. Busca ignora caixa e acentos portugueses; filtros precedem LIMIT/OFFSET. Páginas fora do total são corrigidas para a última página válida; tamanho/página/data inválidos retornam 400. Dados privados com Cache-Control no-store. Listagens de camping omitem content; consultar id para editar. Histórico consultado separadamente por id da solicitação (tipo:UUID).
- POST `/admin/requests/{suggestion|claim|registration}/{uuid}/decision`: `{revision,accept,notes,response,categoryUuid,cityUuid,existingUuid}`. Resposta obrigatória. Indicação aceita cria rascunho sem proprietário ou vincula camping existente. Vínculo impede autoaprovação e encerra concorrentes. Cadastro publica somente quando os campos obrigatórios estiverem completos.
- POST `/admin/campings`, PUT `/admin/campings/{uuid}`: `{revision,content}`; Content segue o contrato existente de campings. Criação é sempre rascunho, sem proprietário. Salvar preserva o status.
- POST `/admin/campings/{uuid}/status`: `{revision,status}` para published/archived. Campings pending_review precisam da decisão da solicitação.
- POST `/admin/reviews/{uuid}/status`: `{revision,status}` para published/hidden.
- POST `/admin/photos`: multipart `photo`. Reutiliza processamento JPEG/PNG, remoção de metadados e limites existentes.

Erro 409 significa conflito de revisão ou estado; 400 dados/referências incompletos; 403 falta de permissão/CSRF; 404 registro ausente. Notas e resposta ficam separadas. Auditoria é gravada na mesma transação da operação. Funções de concessão não foram expostas. O app não ganha acesso às novas consultas por estar autenticado.

Publicação exige cidade oficial, endereço, coordenadas, descrição, contato e foto. A API preserva valores não editados enviados pelo frontend, mas o conteúdo completo é substituído atomicamente. A versão bloqueia edição perdida. Fotos locais não públicas ficam acessíveis ao autor/proprietário elegível ou administrador, conforme a autorização existente ampliada.

O portal é servido no gateway HTTPS existente em `/manager/`, com override versionado no repositório kampive-manager. Os alertas administrativos de novas solicitações continuam usando a fila configurada anteriormente.

Validação: Docker build executa testes Java; o script verify-auth.py aplica a migração em PostgreSQL descartável e invoca verify-manager.py. O usuário optou por testar manualmente os fluxos desta etapa; o teste de integração completo não foi executado nesta entrega.

Paginação: o primeiro teste descartável encontrou erro na projeção compacta, corrigido. Build final e 98 testes Java passaram; repetição da integração não autorizada. Não considerar a integração final validada até concluir o teste manual ou repetir verify-auth.py.
