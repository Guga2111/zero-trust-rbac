# Zero Trust RBAC

Projeto de seminario sobre **Arquitetura Zero Trust** com **Role-Based Access Control (RBAC)**, demonstrando autenticacao moderna com OAuth2, DPoP (Demonstration of Proof-of-Possession), **Dual Authorization (Regra das Duas Chaves)** e **TOTP (Time-based One-Time Password)**.

## Arquitetura

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                        Docker Network (zerotrust-net)                        │
│                                                                              │
│  ┌──────────────────────┐          ┌───────────────────────────────────────┐ │
│  │   Keycloak (IdP)     │          │   Spring Boot API (Resource Server)  │ │
│  │   :8080              │          │   :8081                              │ │
│  │                      │          │                                       │ │
│  │  - Realm: zerotrust  │  JWKS    │  SecurityFilterChain:                │ │
│  │  - Roles: operator,  │◄─────────│                                       │ │
│  │    admin, auditor     │          │  ┌───────────────────────────────┐   │ │
│  │  - DPoP habilitado   │          │  │ 1. DPoP Validation            │   │ │
│  │                      │          │  │    (proof + jkt binding)      │   │ │
│  └──────────┬───────────┘          │  ├───────────────────────────────┤   │ │
│             │                      │  │ 2. JWT Authentication         │   │ │
│             │                      │  │    (signature + issuer)       │   │ │
│             │                      │  ├───────────────────────────────┤   │ │
│             │                      │  │ 3. Role Extraction            │   │ │
│             │                      │  │    (realm_access.roles)       │   │ │
│             │                      │  ├───────────────────────────────┤   │ │
│             │                      │  │ 4. Audit Filter               │   │ │
│             │                      │  │    (log de acesso)            │   │ │
│             │                      │  ├───────────────────────────────┤   │ │
│             │                      │  │ 5. @PreAuthorize (RBAC)       │   │ │
│             │                      │  ├───────────────────────────────┤   │ │
│             │                      │  │ 6. Dual Authorization         │   │ │
│             │                      │  │    + TOTP (HMAC-SHA1)         │   │ │
│             │                      │  └───────────────────────────────┘   │ │
│             │                      │                                       │ │
│             │                      │  Controllers:                         │ │
│             │                      │  - InfraController (servers/databases)│ │
│             │                      │  - OperationController (approve/reject│)│
│             │                      │  - TotpController (enroll/verify)     │ │
│             │                      └───────────────────────────────────────┘ │
│             │                                     ▲                         │
└─────────────┼─────────────────────────────────────┼─────────────────────────┘
              │                                     │
              │  1. Login (password + DPoP proof)   │  3. Request com:
              │     ──────────────────────────────► │     - Authorization: DPoP <token>
              │  2. Access Token (com jkt + roles)  │     - DPoP: <proof JWT>
              │     ◄────────────────────────────── │
              │                                     │
         ┌────┴─────────────────────────────────────┴────┐
         │                   Cliente                      │
         │              (Postman / App)                   │
         │                                               │
         │  - Gera par de chaves RSA-2048                │
         │  - Cria DPoP proof por request                │
         │  - Envia token com scheme DPoP                │
         └───────────────────────────────────────────────┘
```

## Fluxo da Regra das Duas Chaves (Dual Authorization)

Operacoes destrutivas (DELETE database, RESET databases) exigem aprovacao de **duas pessoas diferentes**:

```
  ADMIN (maria)                  Sistema                     AUDITOR (joao)
       │                            │                              │
       │  DELETE /api/databases/x   │                              │
       │ ──────────────────────────►│                              │
       │                            │                              │
       │  202 Accepted              │                              │
       │  { id: "abc-123",         │                              │
       │    status: AWAITING }      │                              │
       │ ◄──────────────────────────│                              │
       │                            │                              │
       │  (Admin pede ao Auditor    │                              │
       │   para aprovar)            │                              │
       │                            │                              │
       │                            │  POST /operations/abc-123/   │
       │                            │       approve                │
       │                            │  { "totpCode": "485923" }    │
       │                            │ ◄────────────────────────────│
       │                            │                              │
       │                            │  Validacoes:                 │
       │                            │  1. Aprovador != Solicitante │
       │                            │  2. Role = AUDITOR           │
       │                            │  3. TOTP valido (HMAC-SHA1)  │
       │                            │                              │
       │                            │  200 OK                      │
       │                            │  { status: APPROVED }        │
       │                            │ ────────────────────────────►│
```

## Roles e Usuarios

| Role | Usuario | Senha | Permissoes |
|------|---------|-------|-----------|
| OPERATOR | carlos | 123 | Leitura: listar servidores e databases |
| ADMIN | maria | 123 | Leitura + solicitar operacoes criticas |
| AUDITOR | joao | 123 | Leitura + aprovar/rejeitar operacoes via TOTP |

## Endpoints

### Infraestrutura

| Metodo | Endpoint | Role Necessaria | Descricao |
|--------|----------|----------------|-----------|
| GET | `/api/servers` | OPERATOR, ADMIN, AUDITOR | Listar servidores |
| GET | `/api/databases` | OPERATOR, ADMIN, AUDITOR | Listar bancos de dados |
| POST | `/api/databases` | ADMIN | Provisionar novo banco |
| DELETE | `/api/databases/{id}` | ADMIN | Solicitar exclusao (cria operacao pendente) |
| PUT | `/api/databases/reset` | ADMIN | Solicitar reset geral (cria operacao pendente) |

### Operacoes (Dual Authorization)

| Metodo | Endpoint | Role Necessaria | Descricao |
|--------|----------|----------------|-----------|
| GET | `/api/operations` | AUDITOR | Listar operacoes pendentes |
| GET | `/api/operations/{id}` | ADMIN, AUDITOR | Detalhes de uma operacao |
| POST | `/api/operations/{id}/approve` | AUDITOR | Aprovar com codigo TOTP |
| POST | `/api/operations/{id}/reject` | AUDITOR | Rejeitar operacao |

### TOTP

| Metodo | Endpoint | Role Necessaria | Descricao |
|--------|----------|----------------|-----------|
| POST | `/api/totp/enroll` | AUDITOR | Gerar segredo TOTP (retorna URI otpauth://) |
| POST | `/api/totp/verify` | AUDITOR | Verificar codigo TOTP |

## Stack Tecnologica

| Componente | Tecnologia |
|------------|-----------|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Seguranca | Spring Security + OAuth2 Resource Server |
| IdP | Keycloak (latest) |
| Token Binding | DPoP (RFC 9449) |
| TOTP | java-otp (RFC 6238, HMAC-SHA1) |
| Containerizacao | Docker + Docker Compose |
| Testes | Postman Collection |

## Estrutura do Projeto

```
rbac/
├── src/main/java/com/zerotrust/rbac/
│   ├── RbacApplication.java
│   ├── config/
│   │   ├── SecurityConfig.java              # JWT, DPoP, RBAC config
│   │   └── AuditSecurityFilter.java         # Log de auditoria
│   ├── model/
│   │   ├── PendingOperation.java            # Operacao pendente de aprovacao
│   │   └── OperationStatus.java             # Enum: AWAITING, APPROVED, REJECTED
│   ├── service/
│   │   ├── PendingOperationService.java     # Ciclo de vida das operacoes
│   │   └── TotpService.java                 # TOTP: enroll, validate (HMAC-SHA1)
│   └── controller/
│       ├── InfraController.java             # Endpoints de infra (servers/databases)
│       ├── OperationController.java         # Fluxo de aprovacao/rejeicao
│       └── TotpController.java              # Enrollment e verificacao TOTP
├── src/main/resources/
│   └── application.yml
├── postman/
│   └── Zero_Trust_RBAC_Seminary.postman_collection.json
├── docker-compose.yml
├── Dockerfile
└── pom.xml
```

## Pre-requisitos

- Docker e Docker Compose
- Postman (para testes)
- App TOTP (Google Authenticator, Authy, etc.)

## Como Rodar

### 1. Subir os containers

```bash
docker-compose up --build
```

Isso inicia:
- **Keycloak** em `http://localhost:8080` (admin/admin)
- **API** em `http://localhost:8081`

### 2. Configurar o Keycloak

1. Acesse `http://localhost:8080/admin` (admin/admin)
2. Crie o realm **zerotrust**
3. Crie os realm roles: `operator`, `admin`, `auditor`
4. Crie os usuarios:

| Usuario | Senha | Realm Role |
|---------|-------|------------|
| carlos | 123 | operator |
| maria | 123 | admin |
| joao | 123 | auditor |

5. Crie o client `api-client` (Public client, Direct Access Grants habilitado)
6. Em **Realm Settings**, garanta que o **DPoP** esta habilitado

### 3. Importar a Collection do Postman

Importe o arquivo `postman/Zero_Trust_RBAC_Seminary.postman_collection.json` no Postman.

### 4. Configurar TOTP do Auditor

1. Execute "0 - Login" (todos os 3 logins)
2. Execute "1 - TOTP Setup > Enroll TOTP (joao)"
3. Copie a URI `otpauthUri` da resposta e escaneie no Google Authenticator
4. Teste com "Verify TOTP" usando o codigo do app

### 5. Executar os testes

Execute as pastas na ordem (0 a 7). Para os testes das pastas 4 e 5, insira o codigo TOTP do Google Authenticator no body da request antes de enviar.

## Testes (Postman Collection)

A collection contem testes organizados em 8 categorias:

### 0 - Login
| Teste | Cenario | Esperado |
|-------|---------|----------|
| Login ADMIN | maria (admin) com DPoP | 200 |
| Login AUDITOR | joao (auditor) com DPoP | 200 |
| Login OPERATOR | carlos (operator) com DPoP | 200 |

### 1 - TOTP Setup
| Teste | Cenario | Esperado |
|-------|---------|----------|
| Enroll TOTP | Auditor registra segredo TOTP | 200 + otpauthUri |
| Verify TOTP | Auditor testa codigo do app | 200 + valid: true/false |

### 2 - RBAC Basico
| Teste | Cenario | Esperado |
|-------|---------|----------|
| OPERATOR lista servidores | GET /api/servers | 200 |
| OPERATOR lista databases | GET /api/databases | 200 |
| OPERATOR provisiona database | POST /api/databases | 403 |
| ADMIN lista servidores | GET /api/servers | 200 |
| ADMIN provisiona database | POST /api/databases | 200 |

### 3 - Sabotagem Frustrada (Fluxo Negativo)
| Teste | Cenario | Esperado |
|-------|---------|----------|
| ADMIN solicita DELETE | DELETE /api/databases/db-producao | 202 (pendente) |
| ADMIN tenta aprovar | POST /operations/{id}/approve | 403 (nao e AUDITOR) |
| AUDITOR TOTP errado | POST /operations/{id}/approve | 403 (TOTP invalido) |
| AUDITOR consulta operacao | GET /operations/{id} | 200 (AWAITING) |

### 4 - Desbloqueio Seguro (Fluxo Positivo)
| Teste | Cenario | Esperado |
|-------|---------|----------|
| ADMIN solicita DELETE | DELETE /api/databases/db-staging | 202 (pendente) |
| AUDITOR aprova com TOTP | POST /operations/{id}/approve | 200 (APPROVED) |
| Verificar status | GET /operations/{id} | 200 (APPROVED) |

### 5 - Reset Geral com Duas Chaves
| Teste | Cenario | Esperado |
|-------|---------|----------|
| ADMIN solicita RESET | PUT /api/databases/reset | 202 (pendente) |
| AUDITOR aprova com TOTP | POST /operations/{id}/approve | 200 (APPROVED) |

### 6 - DPoP Token Binding
| Teste | Cenario | Esperado |
|-------|---------|----------|
| Sem header DPoP | Request sem proof | 401 |
| Token roubado | Chave diferente da vinculada | 401 |
| Sem claim ath | DPoP proof sem ath | 401 |
| ath incorreto | Replay attack simulado | 401 |
| Scheme Bearer | Bearer em vez de DPoP | 401 |

### 7 - Autenticacao
| Teste | Cenario | Esperado |
|-------|---------|----------|
| Sem token | Request anonima | 401 |
| Token adulterado | Payload modificado | 401 |

## Camadas de Seguranca (Zero Trust)

O projeto implementa o principio **"nunca confie, sempre verifique"** com 5 camadas:

1. **DPoP (Proof-of-Possession)** — O token e vinculado criptograficamente ao cliente via par de chaves RSA. Mesmo se interceptado, nao pode ser reutilizado por outro dispositivo (RFC 9449).

2. **JWT com validacao de assinatura e issuer** — Garante que o token foi emitido pelo Keycloak do realm correto e nao foi adulterado.

3. **RBAC (Role-Based Access Control)** — Cada endpoint exige roles especificas. Operadores nao podem executar operacoes destrutivas. Apenas Admins podem solicitar e apenas Auditores podem aprovar.

4. **Dual Authorization (Regra das Duas Chaves)** — Operacoes destrutivas exigem duas pessoas: o Admin solicita e o Auditor aprova. O solicitante nunca pode aprovar sua propria operacao (separacao de responsabilidades).

5. **TOTP (Time-based One-Time Password)** — A aprovacao do Auditor exige um codigo TOTP gerado pelo Google Authenticator. Usa HMAC-SHA1 com janela de 30 segundos (RFC 6238). Garante que mesmo com o token JWT roubado do auditor, a aprovacao exige posse fisica do dispositivo TOTP.

Complementado por **Audit Logging** que registra todas as requisicoes e operacoes criticas para rastreabilidade.

## TOTP — RFC 6238

O TOTP (Time-based One-Time Password) e a segunda chave criptografica do sistema de dual authorization:

- **Algoritmo**: HMAC-SHA1
- **Janela de tempo**: 30 segundos
- **Digitos**: 6
- **Tolerancia**: +-1 janela (aceita codigos de 30s antes/depois)
- **Enrollment**: O auditor faz `POST /api/totp/enroll` e recebe uma URI `otpauth://` que escaneia no Google Authenticator
- **Validacao**: No momento da aprovacao, o codigo TOTP e validado contra o segredo do auditor

O segredo TOTP e armazenado em memoria (ConcurrentHashMap). Em producao, seria persistido em banco de dados com criptografia.

## Licenca

Projeto academico desenvolvido para seminario de Cyberseguranca.
