# Zero Trust RBAC

Projeto de seminário sobre **Arquitetura Zero Trust** com **Role-Based Access Control (RBAC)**, demonstrando autenticação moderna com OAuth2, DPoP (Demonstration of Proof-of-Possession) e validação de conformidade de dispositivo.

## Arquitetura

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          Docker Network (zerotrust-net)                │
│                                                                       │
│  ┌──────────────────────┐          ┌──────────────────────────────┐   │
│  │   Keycloak (IdP)     │          │   Spring Boot API (Resource  │   │
│  │   :8080              │          │   Server) :8081              │   │
│  │                      │          │                              │   │
│  │  - Realm: zerotrust  │  JWKS    │  SecurityFilterChain:        │   │
│  │  - Roles: reader,    │◄─────────│                              │   │
│  │    manager            │          │  ┌────────────────────────┐  │   │
│  │  - DPoP habilitado   │          │  │ 1. DPoP Validation     │  │   │
│  │  - Claims customiz.: │          │  │    (proof + jkt bind)  │  │   │
│  │    device_compliant   │          │  ├────────────────────────┤  │   │
│  │                      │          │  │ 2. JWT Authentication  │  │   │
│  └──────────┬───────────┘          │  │    (signature + issuer)│  │   │
│             │                      │  ├────────────────────────┤  │   │
│             │                      │  │ 3. Role Extraction     │  │   │
│             │                      │  │    (realm_access.roles)│  │   │
│             │                      │  ├────────────────────────┤  │   │
│             │                      │  │ 4. Audit Filter        │  │   │
│             │                      │  │    (log de acesso)     │  │   │
│             │                      │  ├────────────────────────┤  │   │
│             │                      │  │ 5. @PreAuthorize       │  │   │
│             │                      │  │    (RBAC por endpoint) │  │   │
│             │                      │  ├────────────────────────┤  │   │
│             │                      │  │ 6. Device Compliance   │  │   │
│             │                      │  │    (Zero Trust check)  │  │   │
│             │                      │  └────────────────────────┘  │   │
│             │                      │                              │   │
│             │                      │  Endpoints:                  │   │
│             │                      │  GET    /api/documents       │   │
│             │                      │  POST   /api/documents       │   │
│             │                      │  DELETE /api/documents/{id}  │   │
│             │                      └──────────────────────────────┘   │
│             │                                     ▲                   │
└─────────────┼─────────────────────────────────────┼───────────────────┘
              │                                     │
              │  1. Login (password + DPoP proof)   │  3. Request com:
              │     ─────────────────────────────►  │     - Authorization: DPoP <token>
              │  2. Access Token (com jkt + claims) │     - DPoP: <proof JWT>
              │     ◄─────────────────────────────  │
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

## Fluxo de Segurança

```
  Cliente                    Keycloak                    API
    │                           │                         │
    │  1. POST /token           │                         │
    │  + DPoP proof (pub key)   │                         │
    │  + credentials            │                         │
    │ ─────────────────────────►│                         │
    │                           │                         │
    │  2. Access Token          │                         │
    │  (JWT com jkt, roles,     │                         │
    │   device_compliant)       │                         │
    │ ◄─────────────────────────│                         │
    │                           │                         │
    │  3. GET /api/documents                              │
    │  Authorization: DPoP <token>                        │
    │  DPoP: <proof com ath, htm, htu>                    │
    │ ───────────────────────────────────────────────────► │
    │                                                     │
    │                           │  4. Validações:         │
    │                           │  ✓ DPoP proof válido?   │
    │                           │  ✓ jkt == pub key?      │
    │                           │  ✓ ath == hash(token)?  │
    │                           │  ✓ JWT assinado?        │
    │                           │  ✓ Issuer correto?      │
    │                           │  ✓ Role autorizada?     │
    │                           │  ✓ Device compliant?    │
    │                                                     │
    │  5. 200 OK / 401 / 403                              │
    │ ◄─────────────────────────────────────────────────── │
```

## Stack Tecnológica

| Componente | Tecnologia |
|------------|-----------|
| Linguagem | Java 21 |
| Framework | Spring Boot 4.1.1 |
| Segurança | Spring Security + OAuth2 Resource Server |
| IdP | Keycloak (latest) |
| Token Binding | DPoP (RFC 9449) |
| Containerização | Docker + Docker Compose |
| Testes | Postman Collection |

## Estrutura do Projeto

```
rbac/
├── src/main/java/com/zerotrust/rbac/
│   ├── RbacApplication.java                # Entry point
│   ├── config/
│   │   ├── SecurityConfig.java             # JWT, DPoP, RBAC config
│   │   └── AuditSecurityFilter.java        # Log de auditoria
│   └── controller/
│       └── DocumentController.java         # Endpoints REST
├── src/main/resources/
│   └── application.yml                     # Configuração da aplicação
├── postman/
│   └── Zero_Trust_RBAC_Seminary.postman_collection.json
├── docker-compose.yml
├── Dockerfile
└── pom.xml
```

## Pré-requisitos

- Docker e Docker Compose
- Postman (para testes)

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
3. Crie os roles do realm: `reader` e `manager`
4. Crie os usuários:

| Usuário | Senha | Role | device_compliant (user attribute) |
|---------|-------|------|----------------------------------|
| maria | 123 | manager | true |
| joao | 123 | reader | — |

5. Em **Realm Settings > General**, garanta que o **DPoP** está habilitado

### 3. Importar a Collection do Postman

Importe o arquivo `postman/Zero_Trust_RBAC_Seminary.postman_collection.json` no Postman.

## Endpoints

| Método | Endpoint | Role Necessária | Device Compliance |
|--------|----------|----------------|-------------------|
| GET | `/api/documents` | READER ou MANAGER | Não |
| POST | `/api/documents` | MANAGER | Sim |
| DELETE | `/api/documents/{id}` | MANAGER | Sim |

## Testes (Postman Collection)

A collection contém **15+ testes** organizados em 5 categorias:

### 0 - Login
- Login como MANAGER (maria) e READER (joao)
- Geração automática de par de chaves RSA-2048
- Criação de DPoP proof para o endpoint de token

### 1 - RBAC
| Teste | Cenário | Esperado |
|-------|---------|----------|
| 1.1 | READER faz GET | 200 |
| 1.2 | READER faz POST | 403 |
| 1.3 | READER faz DELETE | 403 |
| 1.4 | MANAGER faz GET | 200 |
| 1.5 | MANAGER faz POST | 200 |
| 1.6 | MANAGER faz DELETE | 200 |

### 2 - DPoP (Token Binding)
| Teste | Cenário | Esperado |
|-------|---------|----------|
| 2.1 | Sem header DPoP | 401 |
| 2.2 | Token roubado (chave diferente) | 401 |
| 2.3 | DPoP sem claim `ath` | 401 |
| 2.4 | `ath` incorreto (replay attack) | 401 |
| 2.5 | Scheme Bearer em vez de DPoP | 401 |

### 3 - Device Compliance (Zero Trust)
| Teste | Cenário | Esperado |
|-------|---------|----------|
| 3.1 | Device compliant = true | 200 |
| 3.2 | Device compliant = false | 403 |

### 4 - Autenticação
| Teste | Cenário | Esperado |
|-------|---------|----------|
| 4.1 | Sem token | 401 |
| 4.2 | Token adulterado | 401 |

## Camadas de Segurança (Zero Trust)

O projeto implementa o princípio **"nunca confie, sempre verifique"** com 4 camadas:

1. **DPoP (Proof-of-Possession)** — O token é vinculado criptograficamente ao cliente. Mesmo se interceptado, não pode ser reutilizado por outro dispositivo.

2. **JWT com validação de assinatura e issuer** — Garante que o token foi emitido pelo Keycloak do realm correto e não foi adulterado.

3. **RBAC (Role-Based Access Control)** — Cada endpoint exige uma role específica. Leitores não podem criar/deletar documentos.

4. **Device Compliance** — Operações sensíveis (POST, DELETE) exigem que o dispositivo esteja em conformidade, validado pelo IdP via claim `device_compliant`.

Complementado por **Audit Logging** que registra todas as requisições autenticadas para rastreabilidade.

## Componentes do Código

### SecurityConfig.java
- Configura o `JwtDecoder` com validação de issuer do Keycloak
- Habilita DPoP com `.dPoP(Customizer.withDefaults())`
- Extrai roles do claim `realm_access.roles` e mapeia para `ROLE_<UPPERCASE>`
- Adiciona o filtro de auditoria após a autenticação

### DocumentController.java
- 3 endpoints protegidos com `@PreAuthorize`
- POST e DELETE validam o claim `device_compliant` do JWT
- Lança `AccessDeniedException` se o dispositivo não for confiável

### AuditSecurityFilter.java
- Filtro que executa uma vez por request (`OncePerRequestFilter`)
- Loga: usuário, roles, método HTTP e URI acessada
- Ignora usuários anônimos

## Licença

Projeto acadêmico desenvolvido para seminário de Cybersegurança.
