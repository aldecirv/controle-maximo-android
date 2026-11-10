# Auditoria — Controle Máximo Android

Data: 2026-11-10  
Base: `controle-máximo---gestão-de-validade-de-produtos.zip`.

## O que o original contém

- React/Vite + servidor Express + SQLite (`server.ts`, `server/db.ts`, services).
- Produtos, lotes, alertas, movimentações append-only, reservas, configurações.
- Dashboard, cadastro produto+lote, central de alertas, registro de baixa, vitrine pública, relatórios, testes e segurança.
- Regras: validade > fabricação, níveis ATENÇÃO/URGENTE/VENCIDO, bloqueio de baixa acima do saldo livre, TTL de 2h para reservas, DTO público sem custo/fornecedor.
- Seed com dados de demonstração e configuração padrão 7/2/5 dias.
- Recursos server-side: e-mail/push simulados, endpoint `/ofertas`, rate limiting por IP e testes em Node.

## Decisões Android

| Tema | Original | Android |
|---|---|---|
| Backend | Express + SQLite no servidor | SQLite local no aparelho |
| Autenticação | usuário padrão | MVP single-tenant local |
| Vitrine | rota pública `/ofertas` | tela nativa de pré-visualização e reservas |
| Alertas | job + logs de e-mail/push | cálculo local + central de alertas |
| Movimentos | append-only com triggers | mesmos triggers no SQLite Android |
| Reservas | TTL 2h + rate limit por IP | TTL 2h local; sem IP |
| Relatórios | SQL agregado + CSV | SQL agregado + CSV via FileProvider |
| Testes | Node + SQLite | JUnit/Robolectric + screenshots |
| Permissões | n/a | sem internet/armazenamento externo |

## O que foi implementado de verdade

- Banco SQLite com produtos, lotes, instituições, movimentos, reservas e configurações.
- Seed inicial e rotina de restauração/limpeza em Ajustes.
- Cálculo de alertas e atualização de status vencido.
- Cadastro com validação de datas e quantidades.
- Baixa transacional com bloqueio por saldo reservado.
- Triggers append-only.
- Reserva com TTL de 2h, código único e devolução de saldo.
- Relatórios e exportação CSV.
- Testes de regras e fluxo.

## O que foi deliberadamente omitido

- Servidor Express, conta multiusuário, sincronização, URL pública e rate limiting por IP: o app é offline/local.
- Envio real de e-mail e push: exigiria rede/credenciais; o original já simulava esse envio.
- Tela de testes do servidor: substituída por testes automatizados.
- Qualquer resultado fictício de venda, alerta ou reserva.

## Limitações

- Dados de exemplo são criados na primeira execução; use Ajustes para apagar.
- Sem backup em nuvem; `allowBackup=false`.
- Sem teste em dispositivo físico neste ambiente (nenhum aparelho conectado).
