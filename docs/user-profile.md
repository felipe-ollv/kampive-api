# Perfil do usuário

Aplicar **manualmente** `docs/sql/user-profile.sql` após `campsite-community.sql`, antes de publicar esta versão. O serviço não executa DDL e a role da aplicação não recebe DDL.

- `GET /api/v1/users/me` inclui `cityUuid`, `cityName`, `divisionName`, `countryName` e `travelStyles`.
- `PUT /api/v1/users/me` substitui `name`, `countryUuid`, `cityUuid`, `bio`, `travelStyles` e `photoUrl`. Exige sessão ativa e CSRF. Não altera email, propriedade ou verificação.
- Nome: 3–150 caracteres; apresentação: até 500; localização opcional, com cidade pertencente ao país. Estilos são valores únicos de `tent`, `motorhome`, `trailer`, `chalet`.
- Foto: `null` remove, valor atual preserva, data URI JPEG/PNG altera. Até 2 MiB e 16 megapixels de entrada. A API decodifica, limita a 512 px, reencoda JPEG sem metadados e armazena em `photo_url`. Não baixa URLs remotas nem aceita URIs locais. O limite de tamanho HTTP do gateway deve comportar um JSON de até 2,8 MB.
- `DELETE /api/v1/users/me` recebe `{ "password": "senha atual", "confirmation": "EXCLUIR" }`. Exige CSRF, senha correta e conta ativa; limita tentativas por conta. Transação remove credenciais, identidades sociais, favoritos e notificações, limpa dados pessoais e evidências privadas de reivindicação e anonimiza o cadastro. Invalida a sessão atual. Sessões antigas deixam de obter perfil ou executar as escritas que exigem conta ativa.
- Contribuições, vínculos e aceites permanecem referenciando o UUID anonimizado para manter integridade. Esse UUID não permite login; o email original é liberado para um novo cadastro. Não usar este fluxo para apagar conteúdo editorial de campings.

Testes: `UserHttpTest`, `ProfilePhotoTest`, suíte existente de identidade e arquitetura. Executar `./mvnw verify` com JDK 21 ou o build Docker descrito no README. Verificar integração com PostgreSQL e simulador após aplicar o SQL; testes HTTP com mocks não validam permissões SQL reais.
