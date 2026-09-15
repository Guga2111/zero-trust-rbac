# Guia de Testes — Zero Trust RBAC

Este guia detalha passo a passo como testar todos os cenarios do projeto usando Postman e Google Authenticator.

---

## Pre-requisitos

1. Docker rodando com `docker-compose up --build`
2. Keycloak configurado com realm `zerotrust`, client `api-client` e os 3 usuarios:
   - **carlos** (senha: 123, role: operator)
   - **maria** (senha: 123, role: admin)
   - **joao** (senha: 123, role: auditor)
3. Postman com a collection `Zero_Trust_RBAC_Seminary` importada
4. Google Authenticator instalado no celular (ou outro app TOTP como Authy, Microsoft Authenticator)

---

## Pasta 0 — Login

**Objetivo:** Autenticar os 3 usuarios e armazenar seus tokens.

### 0.1 — Login ADMIN (maria)

- Abra a request **"Login ADMIN (maria)"**
- Clique em **Send**
- **Esperado:** Status **200** e o campo `access_token` na resposta
- O token e salvo automaticamente na variavel `access_token_admin`
- Um par de chaves RSA e gerado e salvo em `dpop_private_key_admin` / `dpop_public_key_admin`

### 0.2 — Login AUDITOR (joao)

- Abra a request **"Login AUDITOR (joao)"**
- Clique em **Send**
- **Esperado:** Status **200**
- Token salvo em `access_token_auditor` com chaves DPoP proprias

### 0.3 — Login OPERATOR (carlos)

- Abra a request **"Login OPERATOR (carlos)"**
- Clique em **Send**
- **Esperado:** Status **200**
- Token salvo em `access_token_operator` com chaves DPoP proprias

> Cada usuario tem seu proprio token e par de chaves RSA. Isso e necessario porque o DPoP vincula o token a chave — se compartilhassem, o DPoP nao protegeria contra roubo de token.

---

## Pasta 1 — TOTP Setup

**Objetivo:** Configurar o segundo fator de autenticacao (TOTP) para o auditor joao.

### 1.1 — Enroll TOTP (joao)

- Abra a request **"Enroll TOTP (joao)"**
- Clique em **Send**
- **Esperado:** Status **200** com resposta:

```json
{
    "otpauthUri": "otpauth://totp/ZeroTrust:joao?secret=ABCDEF123456&issuer=ZeroTrust"
}
```

**Agora no Google Authenticator:**

1. Abra o app no celular
2. Toque no botao **"+"** (adicionar conta)
3. Selecione **"Inserir chave de configuracao"** (ou "Enter a setup key")
4. Preencha:
   - **Conta:** `ZeroTrust:joao` (ou qualquer nome que quiser)
   - **Chave:** copie apenas o valor do parametro `secret` da URI (ex: `ABCDEF123456`)
   - **Tipo de chave:** baseado em tempo (padrao)
5. Toque em **"Adicionar"**
6. O app comeca a mostrar codigos de 6 digitos que mudam a cada 30 segundos

> **Importante:** Anote ou salve o `secret` em algum lugar seguro. Se a aplicacao reiniciar (pois o segredo fica em memoria), sera necessario fazer o enroll novamente e reconfigurar o Google Authenticator.

### 1.2 — Verify TOTP (joao)

- Abra a request **"Verify TOTP (joao)"**
- Olhe o Google Authenticator e copie o codigo de 6 digitos atual
- No Postman, edite o **Body** da request:

```json
{
    "code": "482916"
}
```

Substitua `482916` pelo codigo que esta no seu app.

- Clique em **Send** (rapido, antes do codigo expirar)
- **Esperado:** Status **200** com:

```json
{
    "valid": true
}
```

Se retornar `"valid": false`, o codigo provavelmente expirou. Copie o novo codigo do app e tente novamente.

> Este passo e opcional — serve apenas para confirmar que o TOTP esta funcionando antes de usar nos testes reais.

---

## Pasta 2 — RBAC Basico

**Objetivo:** Validar que as permissoes de cada role funcionam corretamente.

Todas as requests desta pasta sao automaticas. Basta clicar **Send** em cada uma.

### 2.1 — OPERATOR lista servidores

- **Send** → **Esperado: 200**
- carlos (operator) consegue listar servidores
- Resposta: `["srv-web-01", "srv-app-02", "srv-db-03"]`

### 2.2 — OPERATOR lista databases

- **Send** → **Esperado: 200**
- carlos (operator) consegue listar bancos
- Resposta: `["db-producao", "db-staging", "db-analytics"]`

### 2.3 — OPERATOR tenta provisionar database

- **Send** → **Esperado: 403 Forbidden**
- carlos (operator) **nao pode** criar recursos — apenas Admin pode

### 2.4 — ADMIN lista servidores

- **Send** → **Esperado: 200**
- maria (admin) tambem pode ler

### 2.5 — ADMIN provisiona database

- **Send** → **Esperado: 200**
- maria (admin) pode criar recursos
- Resposta: `"Database provisioned successfully by maria"`

---

## Pasta 3 — Sabotagem Frustrada (Fluxo Negativo)

**Objetivo:** Demonstrar que um admin agindo sozinho nao consegue executar operacoes destrutivas.

**Cenario:** maria (admin insider malicioso) tenta deletar o banco de producao.

### 3.1 — ADMIN solicita DELETE db-producao

- **Send** → **Esperado: 202 Accepted**
- O sistema **nao deleta** o banco — cria uma operacao pendente
- Resposta:

```json
{
    "id": "a1b2c3d4-...",
    "requestedBy": "maria",
    "operationType": "DELETE_DATABASE",
    "targetResource": "db-producao",
    "status": "AWAITING_APPROVAL"
}
```

- O `id` e salvo automaticamente na variavel `operation_id`

### 3.2 — ADMIN tenta aprovar propria operacao

- **Send** → **Esperado: 403 Forbidden**
- maria tem role ADMIN, nao AUDITOR — o `@PreAuthorize("hasRole('AUDITOR')")` bloqueia
- Mesmo se maria tivesse role AUDITOR, a validacao `aprovador != solicitante` bloquearia

### 3.3 — AUDITOR tenta aprovar com TOTP errado

- O body ja vem com `"totpCode": "000000"` (codigo invalido de proposito)
- **Send** → **Esperado: 403 Forbidden**
- joao e AUDITOR e e pessoa diferente de maria, mas o codigo TOTP esta errado
- Prova: nao basta ser auditor, precisa do codigo correto do celular

### 3.4 — AUDITOR consulta operacao pendente

- **Send** → **Esperado: 200** com `"status": "AWAITING_APPROVAL"`
- A operacao continua pendente — nenhuma das tentativas conseguiu aprova-la
- **Conclusao: sabotagem frustrada**

---

## Pasta 4 — Desbloqueio Seguro (Fluxo Positivo)

**Objetivo:** Demonstrar o fluxo legitimo onde Admin e Auditor colaboram para executar uma operacao critica.

### 4.1 — ADMIN solicita DELETE db-staging

- **Send** → **Esperado: 202 Accepted**
- Nova operacao pendente criada
- O `id` e salvo automaticamente na variavel `operation_id_2`

### 4.2 — AUDITOR aprova com TOTP valido

**Antes de enviar, faca o seguinte:**

1. Abra o **Google Authenticator** no celular
2. Encontre a entrada **ZeroTrust:joao**
3. Copie o codigo de 6 digitos atual
4. No Postman, edite o **Body**:

```json
{
    "totpCode": "739281"
}
```

Substitua `739281` pelo codigo real do app.

5. Clique em **Send** rapidamente
6. **Esperado: 200** com:

```json
{
    "id": "...",
    "status": "APPROVED",
    "resolvedBy": "joao",
    "resolvedAt": "2026-09-15T..."
}
```

> Se receber 403, o codigo provavelmente expirou. Copie o novo codigo e tente novamente. Voce tem aproximadamente 60 segundos de margem (o servidor aceita a janela atual e +-1 janela).

### 4.3 — Verificar operacao APPROVED

- **Send** → **Esperado: 200** com `"status": "APPROVED"`
- Confirma que a operacao foi aprovada com trilha de auditoria completa (quem aprovou e quando)

---

## Pasta 5 — Reset Geral com Duas Chaves

**Objetivo:** Demonstrar que o reset de todos os bancos tambem exige dual authorization.

### 5.1 — ADMIN solicita RESET databases

- **Send** → **Esperado: 202 Accepted**
- Operacao pendente com `"operationType": "RESET_DATABASES"`
- O `id` e salvo automaticamente na variavel `operation_id_reset`

### 5.2 — AUDITOR aprova RESET com TOTP

**Antes de enviar:**

1. Abra o **Google Authenticator**
2. Copie o codigo atual de **ZeroTrust:joao**
3. Edite o **Body** no Postman:

```json
{
    "totpCode": "531847"
}
```

4. **Send** → **Esperado: 200** com `"status": "APPROVED"`

---

## Pasta 6 — DPoP Token Binding

**Objetivo:** Validar que tokens roubados ou proofs invalidos sao rejeitados.

Todas as requests sao automaticas. Basta clicar **Send** em cada uma.

### 6.1 — Sem header DPoP

- **Send** → **Esperado: 401**
- Envia o token sem proof de posse da chave — servidor rejeita

### 6.2 — Token roubado (chave diferente)

- **Send** → **Esperado: 401**
- Simula um atacante que interceptou o token de maria mas gera seu proprio par de chaves RSA
- O token contem um thumbprint (`jkt`) da chave original — a chave do atacante nao bate

### 6.3 — DPoP sem ath

- **Send** → **Esperado: 401**
- O proof nao contem o hash do access token (`ath`) — invalido

### 6.4 — ath incorreto (replay)

- **Send** → **Esperado: 401**
- O proof contem um hash falso — simula reutilizacao de um proof antigo com token diferente

### 6.5 — Scheme Bearer em vez de DPoP

- **Send** → **Esperado: 401**
- Usa `Authorization: Bearer` em vez de `Authorization: DPoP` — o token foi emitido como DPoP-bound e nao aceita Bearer

---

## Pasta 7 — Autenticacao

**Objetivo:** Validar que requests sem credenciais validas sao bloqueadas.

### 7.1 — Sem token

- **Send** → **Esperado: 401**
- Request completamente anonima — acesso negado

### 7.2 — Token adulterado

- **Send** → **Esperado: 401**
- Pega o token de maria e modifica caracteres no payload — a assinatura digital nao bate mais

---

## Resumo rapido

| Pasta | Interacao manual? | O que fazer |
|-------|-------------------|-------------|
| 0 - Login | Nao | Apenas Send |
| 1 - TOTP Setup | **Sim** | Registrar secret no Google Authenticator + inserir codigo no Verify |
| 2 - RBAC | Nao | Apenas Send |
| 3 - Sabotagem | Nao | Tudo automatico (TOTP errado de proposito) |
| 4 - Desbloqueio | **Sim** | Inserir codigo real do Google Authenticator no approve |
| 5 - Reset | **Sim** | Inserir codigo real do Google Authenticator no approve |
| 6 - DPoP | Nao | Apenas Send |
| 7 - Auth | Nao | Apenas Send |

---

## Troubleshooting

| Problema | Causa | Solucao |
|----------|-------|---------|
| Login retorna 401 | Credenciais ou client errado no Keycloak | Verificar se o usuario existe com a senha correta e se o client `api-client` tem Direct Access Grants |
| TOTP retorna `"valid": false` | Codigo expirou ou secret errado | Copiar novo codigo do app. Se persistir, fazer enroll novamente e reconfigurar o Google Authenticator |
| Approve retorna 403 "TOTP not enrolled" | Aplicacao reiniciou e perdeu o segredo | Fazer enroll novamente (POST /api/totp/enroll) e reconfigurar o app |
| Approve retorna 403 "Invalid TOTP code" | Codigo expirado | Copiar codigo novo e enviar rapido (tem ~60s de margem) |
| Qualquer request retorna 401 | Token expirado | Executar o login do usuario novamente (pasta 0) |
| DPoP tests passam com 200 em vez de 401 | Keycloak sem DPoP habilitado | Verificar configuracao de DPoP no realm/client |
