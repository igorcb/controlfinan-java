# ControlFinan (Java)

![Java](https://img.shields.io/badge/Java-21-ED8B00?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.5-6DB33F?logo=springboot&logoColor=white)
![Tailwind](https://img.shields.io/badge/Tailwind-4-06B6D4?logo=tailwindcss&logoColor=white)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-4169E1?logo=postgresql&logoColor=white)

Aplicação web de controle financeiro pessoal, pensada para quem trabalha como motorista de aplicativo. Cada usuário registra entradas (créditos) e saídas (débitos) em contas como dinheiro, bancos e carteiras de aplicativos, e acompanha o saldo por dia e por mês.

Esta é a versão em **Java** do [ControlFinan em Rails 8 + Hotwire](https://github.com/igorcb/controlfinan-rails8-hotwire). Ela tem **paridade de funcionalidades e de visual** com o original, assim como as versões em [Django](https://github.com/igorcb/controlfinan-django), [Laravel](https://github.com/igorcb/controlfinan-laravel) e [Go](https://github.com/igorcb/controlfinan-go): mesmas telas, mesmas regras, mesmas mensagens e **mesmo esquema de banco**. As versões podem usar os mesmos dados, inclusive as senhas.

Stack: **Spring Boot 3.5** (Spring MVC + Tomcat), **Thymeleaf** para o HTML, **JdbcTemplate** para o SQL e o `BCrypt` do `spring-security-crypto`.

## Funcionalidades

| Tela | Rota | O que faz |
|---|---|---|
| Conta Corrente | `/` | Lista os lançamentos, do mais recente para o mais antigo, 30 por página, com o saldo total |
| Saldo do dia | `/current_accounts?date=AAAA-MM-DD` | Filtra a listagem por um dia e mostra o saldo daquele dia |
| Novo lançamento | `/current_accounts/new` | Cadastra um crédito ou um débito |
| Detalhe, edição e exclusão | `/current_accounts/:id` | Mostra, altera ou remove um lançamento |
| Dashboard | `/dashboard` | Abas **Saldo Diário** e **Saldo Mensal** (corridas menos combustível, lavagem e telefone). As setas navegam entre os meses |
| JSON | `/current_accounts.json` e `/current_accounts/:id.json` | Os mesmos dados, para integrações |
| Cadastro, login e senha | `/registration/new`, `/session/new`, `/passwords/new` | Conta de usuário, com senha de no mínimo 6 caracteres |

Cada usuário só enxerga os próprios lançamentos.

## Como a paridade foi feita

| Rails 8 + Hotwire | Java |
|---|---|
| Autenticação nativa do Rails 8 (`has_secure_password`, tabela `sessions`, cookie `session_id`) | Mesmo modelo: tabela `sessions` e cookie assinado `session_id`. O `BCrypt` grava no mesmo formato do Rails (`$2a$`) |
| Proteção CSRF (`authenticity_token`) | Filtro que confere o token de cada POST, com o mesmo nome de campo |
| `rate_limit to: 10, within: 3.minutes` | Limite de 10 tentativas a cada 3 minutos por IP, em memória |
| Token de redefinição de senha (15 minutos) | Token assinado com validade de 15 minutos, invalidado quando a senha muda |
| `_method` para PATCH, PUT e DELETE | `HiddenHttpMethodFilter` do Spring, com o mesmo campo `_method` |
| Turbo (`data-turbo-confirm`) e Stimulus (abas) | JavaScript simples para a confirmação e as abas |
| jbuilder | JSON compacto com os mesmos campos e o mesmo formato (por exemplo, `"price":"150.0"`) |
| Tailwind CSS 4 | Tailwind CSS 4 (CLI standalone), com as mesmas classes |

Os valores em dinheiro usam `BigDecimal`. O Spring Security completo não foi usado: só o `BCrypt` dele, para manter o mesmo modelo de sessão do Rails.

**Verificação**, com o mesmo usuário e os mesmos dados (7.292 lançamentos) nos dois apps:

- Texto idêntico em todas as telas comparadas, incluindo paginação, filtro por dia, datas e meses inválidos, dashboard, formulários, mensagens de validação, erros de cadastro e login inválido.
- JSON idêntico byte a byte (lista, segunda página e detalhe).
- **14 telas idênticas pixel a pixel**: login, lista, filtro, dashboard, novo, detalhe e edição, no computador (1280 px) e no celular (390 px).
- Roteiro completo no navegador (Playwright), com 23 etapas: cadastro, login, lançamentos, edição, exclusão, dashboard, recuperação de senha e celular.

## Como rodar com Docker

```bash
docker compose up               # app em http://localhost:8004
```

O container de desenvolvimento compila o Tailwind e roda o app com `mvn spring-boot:run`. As tabelas são criadas na subida, com o mesmo esquema do app Rails. Depois de mudar código Java, reinicie com `docker compose restart web`. Os templates são recarregados sem reiniciar.

Imagem de produção: `Dockerfile.prod`, com o jar do Spring Boot numa JRE 21 Alpine. Configure:

- `DATABASE_URL` (JDBC), `DATABASE_USERNAME`, `DATABASE_PASSWORD` e `SECRET_KEY_BASE`.
- Opcionais: `BCRYPT_COST` (padrão 12), `DB_POOL` (padrão 10) e `PORT` (padrão 8000).

## Testes

35 testes com **JUnit 5**. O app sobe de verdade numa porta aleatória, os testes usam um cliente HTTP com cookies e um Postgres de verdade. Eles cobrem:

- Formatação de moeda e decimais.
- Senha no formato do Rails, incluindo um hash gravado pelo próprio Rails.
- Validações em pt-BR e saldos.
- Login, limite de tentativas, logout, CSRF, cadastro e redefinição de senha.
- Lançamentos: filtro, paginação, JSON, criar, editar, excluir e isolamento entre usuários.
- Dashboard.

```bash
docker compose exec web mvn test
```

Os testes usam o banco `controlfinan_java_test` (crie uma vez com `docker compose exec db psql -U app -c "create database controlfinan_java_test"`).

## Estrutura

```
src/main/java/com/controlfinan/
  model/       Entry, User, Repository (SQL com JdbcTemplate) e Format (moeda e decimais como no Rails)
  web/         controllers, RequestFilter (CSRF, sessão e login obrigatório) e Web (cookies, flash, limite, tokens)
src/main/resources/
  templates/   Thymeleaf com o mesmo HTML do app Rails
  schema.sql   o mesmo esquema do app Rails
src/test/      JUnit 5
assets/        fonte do Tailwind
```

## Benchmark

O benchmark que compara as seis versões do ControlFinan (Rails, Django, Laravel, Go, Java e Rust), com os mesmos dados e os mesmos limites de CPU e memória, fica no repositório do app de referência: [controlfinan-rails8-hotwire/benchmark](https://github.com/igorcb/controlfinan-rails8-hotwire/tree/main/benchmark).

## Licença

[MIT](LICENSE)
