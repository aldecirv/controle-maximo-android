# Revisão sênior — Controle Máximo 1.1.0

**Data:** 2026-11-11  
**Escopo:** MVP Android nativo (não o servidor Express original).  
**Método:** inspeção de código, 29 testes automatizados, screenshots Robolectric em 360/390/768/1440 dp e cenário de aceite em banco isolado.

> A especificação original é de um app web com React/servidor, Zod, rota `/ofertas`, e-mail/push, menu sanfona e safe-area de iPhone. O artefato auditado é nativo Android. Itens sem equivalente nativo são marcados como **N/A** ou **PARCIAL**, sem simular resultados.

## Resultado por épico

| Épico | Status | Resumo |
|---|---|---|
| E1 Cadastro | ⚠️ PARCIAL | Validações e unicidade corrigidas; falta máscara monetária e edição de produto. |
| E2 Alertas | ⚠️ PARCIAL | Job local 07:00 SP, fuso, classificação, filtros e logs; e-mail automático/push de servidor não existem. |
| E3 Vitrine | ✅ FUNCIONANDO (com ressalvas) | DTO público, limites, desconto, reserva, TTL, concorrência e consulta; rate limit por IP é N/A. |
| E4 Baixas | ✅ FUNCIONANDO (com ressalvas) | Venda/doação/descarte, PDF, motivo obrigatório, ajuste referenciado, atomicidade e imutabilidade; stepper visual não implementado. |
| E5 Relatórios | ✅ FUNCIONANDO | Agregações SQL, KPIs, período, CSV UTF-8 BOM e teste de 1.000 movimentações. |
| E6 Responsividade/menu | ⚠️ PARCIAL | Sem overflow em 360/390/768/1440; app usa bottom nav + folha “Mais”, não accordion/hambúrguer web. |

## E1 — Cadastro de produtos e lotes

**✅ Validado/corrigido**
- `data_validade > data_fabricacao` no formulário (picker + erro inline), no banco (`CHECK` e trigger) e na validação nativa.
- Quantidade inicial zero/negativa rejeitada no formulário, no banco e em todas as baixas/reservas.
- `quantidade_inicial = quantidade_disponivel` na criação.
- Código de lote único (índice/tabela + trigger case-insensitive), com erro junto ao campo.
- Datas por picker; teclado numérico nos campos de quantidade/custo/preço.
- Mensagens em português junto ao campo para os campos críticos.
- Testes de data, quantidade, código duplicado e banco.

**⚠️ Parcial**
- Não existe tela de edição de produto; portanto não há alteração retroativa de lotes (comportamento seguro, funcionalidade ausente).
- Campos monetários usam teclado decimal, mas não têm máscara `R$ 1.234,56`.
- “Zod/backend” não se aplica ao app offline; a barreira equivalente é a validação nativa + SQLite.

## E2 — Alertas de validade

**✅ Validado/corrigido**
- Classificação: 7 dias = ATENÇÃO, 2 dias = URGENTE, vencido com quantidade > 0 = VENCIDO.
- Fuso America/Sao_Paulo aplicado à data de referência, inclusive quando o instante UTC cai no dia anterior.
- Job local diário às 07:00 America/Sao_Paulo via WorkManager, com log e notificação local.
- Alertas calculados por lote; não há linhas duplicadas para o mesmo lote/nível/dia.
- Lote esgotado/zerado não gera alerta; baixa que zera o lote remove o alerta.
- Painel/central com filtros Todos/Atenção/Urgentes/Vencidos e ordenação por dias.
- Configuração de dias altera a janela de alerta; teste dedicado.
- Resumo por e-mail manual com produto, lote, validade, quantidade e assinatura “Equipe Controle Máximo Carrefour”.

**⚠️ Parcial**
- Não há envio automático de e-mail/push por servidor. O app é offline; a notificação é local e o e-mail é um resumo compartilhado via app de e-mail do usuário.
- O job local pode ser adiado pelo WorkManager se o aparelho estiver em Doze; não é um cron de servidor.

## E3 — Vitrine e reservas

**✅ Validado/corrigido**
- `PublicOffer` expõe somente nome, categoria, unidade, preço original/final, desconto, validade, dias e saldo. Custo, fornecedor, margem e reservado não entram no DTO.
- Limite configurável da vitrine (padrão 5 dias) e saldo livre > 0; testes com 4, 5, 6, 10 dias e lote esgotado.
- Desconto: 0%, 30% e 50% conferidos.
- Reserva valida nome/telefone/quantidade, gera código `CMC-XXXXXX` e incrementa reservado.
- TTL de 2h devolve a quantidade ao saldo livre; consulta por código mostra ativa/expirada.
- Reservas concorrentes não causam overshoot (lock de processo + transação + teste com duas threads).

**⚠️ Parcial / N/A**
- Não há endpoint HTTP público nem rate limiting por IP; a tela é local. Rate limiting por IP não se aplica.
- A especificação pede “≤5 dias”, mas o cenário de aceite usa um lote de 6 dias. O padrão permanece 5; para o passo 3 do aceite é necessário configurar a vitrine para 6 dias. Essa é uma **inconsistência da especificação**, não uma mudança de regra.

## E4 — Baixas

**✅ Validado/corrigido**
- Venda salva quantidade, valor e forma de pagamento.
- Doação permite instituição existente/cadastro inline; anexo PDF opcional (somente PDF, até 5 MB, cópia privada).
- Descarte exige motivo (vencido/avariado/outro), no formulário e no banco.
- Baixa em transação atômica com lock de processo; teste de duas vendas simultâneas.
- Quantidade acima do saldo livre é bloqueada com mensagem clara.
- Movimentações append-only: UPDATE/DELETE direto falham por trigger.
- Ajuste referencia uma movimentação original e corrige o saldo (quando aplicável).
- Histórico ordenado, sem edição/exclusão na UI.
- Lote zerado muda para `esgotado`; vencido permanece identificável.

**⚠️ Parcial**
- A tela é um formulário único com confirmação; não há stepper visual em 3 etapas como no web.
- O PDF é armazenado como metadado/cópia local; não há upload para nuvem.

## E5 — Relatórios

**✅ Validado/corrigido**
- KPIs e percentuais calculados por agregações SQL, não loops em memória.
- Filtro de período; teste com dataset fixo: 10 itens, 6 vendidos, 2 doados, 2 descartados = 80% de aproveitamento.
- Valor recuperado = vendas + valor social das doações.
- Categorias e formas de pagamento vêm de SQL real.
- CSV com BOM UTF-8 e CRLF para Excel; teste de conteúdo.
- Teste de performance com 1.000 movimentações.

**⚠️ Parcial**
- O gráfico é nativo em barras horizontais (não há biblioteca web); validado em 360/390/411 dp e layouts de tablet.
- Tabelas são cards nativos; não há tabela desktop.

## E6 — Responsividade e menu

**✅ Validado**
- Capturas em 360, 390, 768 e 1440 dp; sem scroll horizontal indevido nas telas verificadas.
- Cards empilhados, formulários em coluna, botões principais ≥44 dp, insets de sistema respeitados.
- Alertas em cards com badge; vitrine em lista de uma coluna no mobile.
- Menu nativo: bottom navigation + folha “Mais”.

**⚠️ Parcial / N/A**
- Não há menu sanfona/hambúrguer, accordion, ESC ou `aria-expanded` (são padrões web; o app é Android nativo).
- Não há stepper de 3 etapas na baixa.
- iPhone/safe-area não se aplica; Android usa WindowInsets.

## Cenário de aceite

| Passo | Status | Observação |
|---|---|---|
| 1. Cadastrar Iogurte Morango, 50 un, 6 dias, 30% | ✅ | Teste de aceite em banco isolado |
| 2. Alerta Atenção | ✅ | 6 dias ≤ 7 |
| 3. Aparecer na vitrine | ⚠️ | Padrão 5 dias: **não aparece**; teste passa com vitrine configurada para 6 dias |
| 4. Reservar 5 e obter CMC | ✅ | Código e saldo reservado |
| 5. Expirar 2h e devolver 5 | ✅ | `expireReservations` |
| 6. Vender 10, doar 5, descartar 3 | ✅ | Instituição, motivo avariado |
| 7. Saldo final 32 | ✅ | 50−10−5−3 |
| 8. Relatório 10/5/3 | ✅ | SQL agregado |
| 9. CSV | ✅ | BOM UTF-8 e conteúdo |
| 10. Mobile 390 + menu | ⚠️ | Layout validado; o “menu sanfona” é uma folha More nativa |

## Correções aplicadas (priorizadas)

1. **P0** — Constraints/triggers no SQLite: validade, quantidades, desconto, unicidade de código e motivo de descarte.
2. **P0** — Fuso America/Sao_Paulo e job local 07:00; logs.
3. **P0** — Relatórios com SQL agregado; CSV UTF-8 BOM.
4. **P0** — DTO público da vitrine sem dados internos.
5. **P0** — Lock de transação para baixa/reserva; testes concorrentes.
6. **P1** — Validação inline no cadastro, código único em tempo real.
7. **P1** — Ajuste com referência; PDF de doação; e-mail resumido manual.
8. **P1** — Testes de aceite, concorrência, performance e responsividade.

## Próxima versão (fora do escopo)

- E-mail/push automático exige backend/credenciais; rate limiting real exige endpoint público.
- Stepper visual de 3 etapas e menu sanfona (se o produto migrar para web/tablet).
- Máscara monetária e edição de produto com auditoria.
- Testes instrumentados em aparelho físico (Xiaomi/HyperOS, Android 11–15).
- Resolver a contradição “≤5 dias vs. cenário de 6 dias” no requisito.
