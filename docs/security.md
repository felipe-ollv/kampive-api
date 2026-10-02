# Segurança

Referência: [OWASP Top 10:2025](https://owasp.org/Top10/2025/).
Controles iniciais, sem declaração de conformidade completa ou auditoria.

| Área | Base atual | Próximas funcionalidades |
| --- | --- | --- |
| A01 Controle de acesso | Negação por padrão, health GET público; documentação GET pública somente quando habilitada (perfil local) | Autorização por recurso e testes entre usuários |
| A02 Configuração | Sem usuário padrão/Basic/formulário; CSRF ativo; sem CORS permissivo; multipart desativado | Allowlist CORS e política CSRF conforme autenticação |
| A03 Cadeia de suprimentos | Wrapper oficial, versões e imagens por digest, testes no build | Scanner de dependências/imagens e revisão de CVEs |
| A04 Criptografia | Credenciais fora do Git, role restrita | HTTPS, secret manager, hash de senhas e armazenamento móvel seguro |
| A05 Injeção | Sem SQL de requisições; validação disponível | Queries parametrizadas e allowlist de filtros/ordenação |
| A06 Design | Núcleo isolado e limites de módulos | Threat modeling, quotas, limites de fotos e ownership |
| A07 Autenticação | Nenhum fluxo exposto | Identidade, expiração, revogação e proteção contra força bruta |
| A08 Integridade | Imagens por digest e build testado | Validação de conteúdo/tamanho dos uploads e URLs assinadas |
| A09 Logs e alertas | Logs de infraestrutura; erros HTTP sanitizados | Auditoria com correlação, redação de segredos e alertas |
| A10 Exceções | Problemas JSON genéricos e health sem detalhes | Timeouts, falhas externas e testes de abuso |

Os requisitos também valem para o frontend. Validação local não substitui a do servidor.
Segredos administrativos nunca chegam ao app. A persistência demonstrativa do frontend
deve ser revisada antes de armazenar credenciais reais.

## Containers locais

API como UID 10001, filesystem readonly, /tmp em tmpfs, capabilities removidas e
no-new-privileges. Portas apenas em localhost; banco em volume persistente. A API
usa role distinta do administrador, sem DDL. Quem acessa o daemon Docker pode ler
variáveis de ambiente: .env é conveniência local, não uma estratégia de produção.

Não liberar endpoints com autenticação simulada. Remover denyAll apenas ao implementar
e testar autenticação/autorização reais. Esta entrega não realiza deploy público.
