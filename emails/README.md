# E-mails transacionais Kampive

Templates HTML responsivos e alternativas em texto, independentes de provedor. O layout usa tabelas de apresentação, estilos inline, coluna fluida com máximo de 560 px, ajuste de margens/títulos abaixo de 600 px, fallback de largura para Outlook e link longo com quebra de linha. Fontes de sistema evitam depender de carregamento externo. Não há JavaScript, formulários, rastreamento ou CSS externo.

## Banco

Aplicar uma vez `docs/sql/email-templates.sql` como administrador. Cria `kampive.email_template` e publica a versão 1 dos dois templates reais. Nenhum destinatário, senha, token ou credencial do provedor é armazenado nessa tabela. `kampive_app` tem somente SELECT. O envio deverá consultar a versão ativa por código e idioma; não editar o SQL de instalação para atualizar bancos já provisionados.

Códigos: `password-reset` e `password-changed`. Idioma inicial: `pt-BR`.

As cópias `.html` e `.txt` desta pasta são fontes versionadas e estão reproduzidas no SQL inicial. Para uma revisão futura, atualizar as fontes, criar um novo SQL de publicação e inserir uma nova versão inativa. Após revisar, desativar a anterior e ativar a nova na mesma transação. A restrição única impede duas versões ativas. Preservar versões anteriores para rollback. Templates são conteúdo administrativo confiável: somente a equipe pode editar HTML e posicionar variáveis.

## Variáveis

| Variável | Uso |
| --- | --- |
| `first_name` | Nome do destinatário, até 180 caracteres; escapado no HTML. |
| `logo_url` | URL HTTPS pública da marca, definida pela configuração do backend. |
| `support_url` | URL HTTPS do canal de suporte, definida pela configuração do backend. |
| `reset_url` | Somente recuperação: URL HTTPS gerada pelo backend com token temporário. Nunca aceitar uma URL enviada pelo usuário como destino. |
| `expires_minutes` | Somente recuperação: validade real do token, em minutos inteiros positivos, informada pelo serviço de recuperação. |

Não há domínio, canal de suporte nem validade definitiva inventados nos templates. O remetente e essas configurações serão definidos na integração de e-mail. Para o logo, hospedar uma versão horizontal/compacta em HTTPS; o atributo alt mantém o nome Kampive se imagens estiverem bloqueadas.

A porta interna `EmailTemplates.render(code, variables)` retorna UUID/versão, assunto, HTML e texto a partir do banco. O renderer rejeita variáveis ausentes/desconhecidas, URLs sem HTTPS/com credenciais, caracteres de controle e sintaxe de expressões. Valores não são interpretados recursivamente. O resultado renderizado contém dados privados: não registrar corpo, URL de recuperação ou parâmetros em logs.

O futuro adapter do provedor deve enviar HTML + texto como alternativas MIME. Não há endpoint público de templates. O fluxo de recuperação, tokens e fila de envio está descrito em `docs/password-recovery.md`; o adapter do provedor ainda está indisponível.

## Verificação

O build Docker executa testes do renderer com os HTMLs reais, inclusive escaping de nomes e URLs, rejeição de parâmetros inválidos e ausência de avaliação recursiva. O SQL é transacional. A estrutura foi preparada para telas pequenas, mas a renderização final em Gmail, Apple Mail e Outlook deve ser conferida com envios de teste após escolher o provedor; suporte a cantos arredondados e estilos varia entre clientes.
