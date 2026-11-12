# Arquitetura do Craterborn

Mod para **Minecraft 1.20.1 + Forge 47.4.0** (mapeamentos oficiais, Java 17). Este documento explica
como as três bases (estrutura, chefe e transformação) foram organizadas, quais eventos cada uma usa e por
que o desenho evita os bugs mais comuns de mods de servidor.

Regra geral: **o servidor é a autoridade**. O cliente só desenha (HUD, modelo, partículas) e manda *pedidos*
(a tecla da investida). Nenhuma regra de jogo roda no cliente.

---

## 1. Pacotes

```
com.craterborn
├── Craterborn                  entrada do mod: registros, capability, rede, config
├── config/CraterbornConfig     config/craterborn-common.toml (balanceamento)
├── registry/                   DeferredRegisters e chaves
│   ├── ModEntities             crater_demon, abyssal_flame + atributos
│   ├── ModItems                demon_horn, ovo
│   ├── ModEffects              abyssal_burn
│   ├── ModStructures           StructureType e StructurePieceType da cratera
│   ├── ModDamageTypes          chaves dos tipos de dano (JSON no datapack)
│   ├── ModTags / ModCreativeTabs / ModBlocks
├── worldgen/                   (1) estrutura e "um chefe por cratera"
│   ├── CraterStructure         onde gerar (terreno seco e plano)
│   ├── CraterPiece             o que gerar (cratera + poço em hélice + arena)
│   ├── CraterRegistry          SavedData: estado de cada cratera
│   └── CraterBossSpawner       acorda o chefe quando um jogador entra na arena
├── entity/                     (2) chefe
│   ├── CraterDemonEntity       atributos, esquiva, teleporte, fúria, barra de chefe
│   ├── AbyssalFlameEntity      projétil (aplica Fogo Abissal)
│   └── ai/                     metas: Escape, Nova, Volley, BlinkStrike, Melee, Kite
├── effect/                     Fogo Abissal (DOT % vida máxima) + helper de acúmulos
├── capability/                 (3) DADOS do jogador demônio
│   ├── DemonData               estado + NBT + controle de sync
│   ├── DemonStage              tabela imutável dos 4 estágios
│   └── DemonCapability         registro, anexação, Clone, login/respawn
├── demon/                      (3) REGRAS do jogador demônio
│   ├── DemonEvents             roteador dos eventos do Forge
│   ├── DemonProgression        máquina de estados dos estágios + atributos
│   ├── SoulMetabolism          fome -> almas
│   ├── ShadowDash              investida (servidor)
│   ├── DemonWeakness           água
│   └── DemonHostility          aldeões e golens
├── network/                    SimpleChannel: SyncDemonDataPacket (S->C), ShadowDashPacket (C->S)
├── item/DemonHornItem          gatilho da transformação
├── command/CraterbornCommands  /craterborn (testes/admin)
├── client/                     só cliente: renderer, HUD de almas, tecla, espelho dos dados
└── gametest/                   testes automáticos (runGameTestServer)
```

Separação proposital entre `capability` (o que o jogador **é**) e `demon` (o que acontece com ele). Os dados
não sabem nada de eventos; as regras não guardam estado próprio.

---

## 2. Geração da estrutura

**Datapack** (`data/craterborn/worldgen/`):

| Arquivo | Papel |
|---|---|
| `structure/crater.json` | tipo `craterborn:crater`, biomas `#craterborn:has_crater`, etapa `surface_structures` |
| `structure_set/crater.json` | `random_spread` espaçamento 64 / separação 28 chunks (~1 a cada 1000 blocos), `exclusion_zone` de 10 chunks das vilas |
| `tags/worldgen/biome/has_crater.json` | biomas secos de superfície (planícies, deserto, savana, florestas...) |

Raridade é datapack (não config) porque o worldgen carrega antes da config e porque assim qualquer datapack
pode ajustar sem recompilar.

**`CraterStructure.findGenerationPoint`** recusa o ponto se: o centro estiver abaixo do mar ou com água,
alguma das 8 amostras da borda tiver água, ou o desnível passar de 14 blocos. Tamanhos são sorteados com o
`WorldgenRandom` do contexto (determinístico por seed + chunk).

**`CraterPiece`** é uma peça procedural única (sem template NBT):

1. **Caverna-arena** (elipsoide ~Y -28): piso achatado, casca de basalto/pedra-negra que **veda água e lava**
   de aquíferos, 4 pilares (cobertura contra a rajada do chefe), altar de obsidiana chorona.
2. **Poço**: escadaria em hélice de 2 blocos de largura (1 bloco de descida por bloco andado), miolo aberto e
   parede sempre sólida. Dentro da arena a hélice continua como escada suspensa até o chão.
3. **Cratera**: tigela parabólica que segue o terreno real (heightmap de worldgen), borda elevada e fogo das
   almas, que não se espalha (a cratera não incendeia a floresta vizinha).

Segurança do worldgen: `postProcess` roda uma vez por chunk; só escreve em `chunkBox ∩ boundingBox`; a
aleatoriedade dos blocos é um hash da posição, nunca o `RandomSource` do chunk. Resultado idêntico em
qualquer ordem de geração, sem costuras.

### Um chefe por estrutura

O chefe **não** nasce no worldgen. Uma entidade criada na geração não tem registro central: se outro mod a
remover, ou o chunk for regenerado, não dá para saber se aquela cratera "já teve" chefe.

```
CraterRegistry (SavedData por dimensão, chave = ChunkPos do início da estrutura)

  sem entrada ──jogador entra na arena──▶ AWAKENED(uuid) ──chefe morre──▶ DEFEATED (final)
                                            │      ▲
                                            └──────┘ chefe sumiu sem morrer por 10 s com jogador na arena
                                                      (órfão): novo chefe, novo uuid
```

Três travas garantem um único chefe vivo:

1. **`CraterBossSpawner`** (PlayerTick a cada 20 ticks, escalonado por jogador) só cria o chefe se não
   houver entrada, ou se o registrado sumiu por mais de 10 s **com um jogador na arena** (tolera o carregamento
   assíncrono de entidades). O uuid é gravado **antes** do `addFreshEntity`.
2. **`CraterDemonEntity.tick()`**: no primeiro tick o chefe chama `CraterRegistry.claim(id, uuid)`. Se a
   cratera foi derrotada ou o uuid oficial é outro, ele se descarta. Isso cobre o caso "o órfão reapareceu
   depois que um substituto foi criado".
3. **`CraterDemonEntity.die()`** marca DEFEATED só se a morte não foi cancelada por outro mod (`this.dead`).

Tudo roda na thread do servidor: não há corrida entre dois jogadores entrando no mesmo tick.

Comandos (usam a posição de quem executa; do console, `/execute positioned X Y Z run ...`):
`/craterborn crater info`, `reset` (volta a adormecida) e `wake` (roda a mesma lógica do despertar
automático, sem a carência de órfão).

---

## 3. IA do chefe

O Demônio da Cratera tem só 400 HP (config). Ele vence alvos de muita vida (Warden: 500 HP, 30 de dano)
com três mecânicas, não com status:

| Mecânica | Onde | Como |
|---|---|---|
| **Esquiva com cargas** | `CraterDemonEntity.tryEvade` | golpe ≥ 8 de dano (ou projétil) consome 1 carga e é **anulado** por um teleporte perpendicular à linha de ataque. 3 cargas, +1 a cada 4 s. Não esquiva dano sem atacante (DOT, queda), `/kill` nem golpes leves: contra jogadores as cargas acabam rápido. |
| **Kiting** | `DemonKiteGoal`, `DemonEscapeGoal` | contra "brutos" (≥150 HP ou ≥15 de dano) nunca troca golpes: mantém 9 a 16 blocos, circula, e teleporta para longe do centro da ameaça quando um bruto chega a 4,5 blocos ou 3 inimigos o cercam. |
| **DOT percentual** | `AbyssalBurnEffect` | Fogo Abissal: por segundo, `(0,5 + 1% da vida máx.) × acúmulos`, até 5 acúmulos. Num Warden: 27,5/s. Ignora armadura e imunidade a fogo, corta a cura pela metade. |

Tipos de dano (JSON em `data/craterborn/damage_type/`):

| Tipo | Usado por | Armadura | Observação |
|---|---|---|---|
| `abyssal_fire` | pulso do DOT | ignora | também ignora o cooldown de invencibilidade; não é `is_fire` |
| `abyssal_flame` | Chama Abissal (projétil) | vale | `is_projectile`: escudo e Proteção contra Projéteis funcionam |
| `abyssal_blast` | Nova Abissal | vale | escudo funciona |
| `water_weakness` | água / poção de água | ignora | só afeta demônios e o chefe |

O impacto direto respeita armadura de propósito: contra jogadores bem equipados o perigo é o DOT acumulado
(que se cura com leite), não um golpe imbloqueável.

Metas (ordem = prioridade):

| # | Meta | Quando |
|---|---|---|
| 1 | `DemonEscapeGoal` | bruto colado ou cercado; teleporta e libera a rajada logo depois |
| 2 | `DemonNovaGoal` | 2+ inimigos perto (1+ na fúria) ou bruto perto: anel telegrafado de 1 s, explosão em área com acúmulos para **todos** |
| 3 | `DemonVolleyGoal` | 3 disparos (5 na fúria); cada disparo mira o inimigo com **menos acúmulos**, espalhando o DOT entre vários alvos |
| 4 | `DemonBlinkStrikeGoal` | teleporta às costas de um alvo frágil e golpeia; prioriza quem acabou de errar um golpe (contra-ataque após esquiva) |
| 5 | `DemonMeleeGoal` / `DemonKiteGoal` | corpo a corpo contra frágeis / kiting contra brutos (mutuamente exclusivos) |

Extras: fúria a 50% de vida (velocidade +25%, +1 carga, cooldowns menores, barra vermelha), fraqueza à água
(dano e teleporte para fora), cura fora de combate, coleira na arena (teleporta de volta se sair; não persegue
quem subiu o poço). A lista de inimigos é recalculada a cada 10 ticks e compartilhada pelas metas.

Os teleportes passam por `teleportTactical`: chão firme, sem colisão/líquido, dentro do raio da arena e numa
faixa vertical do altar (um bolsão de caverna natural acima da casca não é destino válido).

O teste `bossDefeatsTwoWardens` coloca o chefe contra dois Wardens enfurecidos numa sala fechada e só passa se
ele vencer. Numa bateria de 9 lutas ele venceu todas, terminando com 55% a 90% da vida, em 25 a 37 s.

---

## 4. Sistema de capability do jogador

### Ciclo de vida (Forge 1.20.1)

| Evento | Bus | O que faz |
|---|---|---|
| `RegisterCapabilitiesEvent` | mod | registra `DemonData` |
| `AttachCapabilitiesEvent<Entity>` | forge | anexa o `Provider` a todo `Player` (cliente e servidor) + `addListener(invalidate)` |
| `PlayerEvent.Clone` | forge | `reviveCaps()` no jogador antigo, `copyFrom`, `invalidateCaps()`; reaplica atributos e corrige a vida |
| `PlayerLoggedInEvent` / `PlayerRespawnEvent` / `PlayerChangedDimensionEvent` | forge | reaplica atributos (idempotente) e envia sync completo (o cliente recria o jogador nesses pontos) |

Na morte o demônio mantém estágio e marcos e volta com metade das almas (como a fome que reinicia).

### Os 4 estágios (`DemonStage`)

| Estágio | Almas | Investida | Bônus | Desbloqueia | Requisito (config) |
|---|---|---|---|---|---|
| 1 Maculado | 20 | - | +1 dano | barra de almas, fraqueza à água, hostilidade | devorar o Chifre |
| 2 Sombra | 30 | 1 carga / 3 s | +5% vel. | Investida Sombria | 150 almas colhidas |
| 3 Infernal | 40 | 1 / 2 s | +4 vida, +2 dano, +8% | golpes com Fogo Abissal (3 acúm.), imune a fogo | 600 almas + 1 Demônio da Cratera abatido **já como demônio** |
| 4 Arquidemônio | 50 | 2 / 1,5 s | +8 vida, +3 dano, +10% | golpes (5 acúm.), imune a lava | 2000 almas + 1 Warden abatido como demônio |

"Almas colhidas" é o total da vida toda (não o saldo), então gastar almas não atrasa a evolução.

### Gerenciando os estágios sem bugs (`DemonProgression`)

- **Único ponto de escrita**: só `DemonProgression.setStage` muda o estágio durante o jogo.
- **Orientado a eventos, não a tick**: `checkAdvancement` roda quando um marco muda (abate, chefe, Warden,
  chifre, comando). Zero custo por tick.
- **Monotônico em laço**: sobe quantos estágios os marcos permitirem, disparando o efeito de entrada de cada um.
- **Atributos idempotentes**: modificador com UUID fixo, removido e recolocado. Rodar 3 vezes = rodar 1 vez.
  São *permanentes* (vão no NBT) de propósito: o vanilla carrega atributos antes da vida, então a vida extra
  não é cortada ao relogar. No Clone a vida é corrigida explicitamente (o `restoreFrom` já a truncou).

### Fome -> barra de almas (`SoulMetabolism`, sem mixin)

No fim de cada `PlayerTickEvent` (depois do `FoodData.tick` vanilla) lê a exaustão acumulada (correr, pular,
minerar, apanhar) mais os pontos de fome que o vanilla descontou, converte em almas (0,25 por exaustão = mesmo
ritmo da fome) e **fixa a fome em 17** com saturação 0:

- 17 < 18: a regeneração vanilla nunca dispara; a nossa usa almas (≥ 50% da barra).
- 17 > 6: o jogador corre. Sem almas a fome é fixada em 6 e ele não corre mais; a inanição passa a ser nossa,
  com as mesmas regras de dificuldade do vanilla.
- Comida mortal é bloqueada (`LivingEntityUseItemEvent.Start`, nos dois lados); a tag
  `craterborn:demon_edible` (maçãs douradas) libera itens pelos efeitos.
- Almas vêm de abates (`LivingDeathEvent`): ¼ da vida máxima da vítima (1 a 40), mais para jogadores,
  aldeões, Wardens e chefes. Mortes por DOT contam para quem feriu por último (`getKillCredit`).

### Investida Sombria (`ShadowDash`)

Tecla `V` -> `ShadowDashPacket(frente, lado)` -> servidor valida estágio, cargas, almas e estado (vivo, não
montado, não dormindo, sem élitro), saneia os floats (NaN/infinito/fora de [-1,1]) e aplica a velocidade com
`hurtMarked = true` (o mesmo caminho do knockback, sem "moved too quickly"). Por 8 ticks o demônio atravessa
golpes e projéteis (`LivingAttackEvent` cancelado).

### Fraqueza à água e hostilidade

- Tocando água: dano/s (tipo `craterborn:water_weakness`, ignora armadura), Fraqueza e Lentidão. Chuva: drena
  almas. Molhado: +25% de dano recebido (`LivingHurtEvent`). Poção de água arremessada fere demônios.
- Golens de ferro ganham uma meta de alvo para demônios no `EntityJoinLevelEvent` (a entidade é recriada a
  cada carga de chunk, então a meta nunca duplica).
- Aldeões usam Brain, não Goals: recebem `HEARD_BELL_TIME` (a memória do sino da vila) e correm para se
  esconder. Nenhum sensor apaga essa memória, então o comportamento é estável. Comércio é recusado.

### Eventos usados

| Evento | Uso |
|---|---|
| `PlayerTickEvent` (END, servidor) | metabolismo (todo tick), água e fogo (a cada 20 t), aldeões (a cada 20 t, defasado 10 t), timers, sync |
| `LivingAttackEvent` | **cancelar** antes do dano: i-frames da investida, imunidade a fogo/lava |
| `LivingHurtEvent` | **modificar** o dano: molhado; golpe do demônio aplica Fogo Abissal |
| `LivingDeathEvent` (LOW) | colheita de almas e marcos -> `checkAdvancement` |
| `LivingHealEvent` | Fogo Abissal corta a cura |
| `LivingEntityUseItemEvent.Start` | bloqueia comida mortal |
| `PlayerInteractEvent.EntityInteract` | aldeões recusam comércio |
| `EntityJoinLevelEvent` | metas extras em golens e mercadores |
| `ProjectileImpactEvent` | poção de água fere demônios |

Cada handler sai cedo (lado errado, não-demônio). O trabalho mais caro (procurar aldeões) roda a cada
20 ticks por jogador demônio e é escalonado pelo id do jogador para não cair tudo no mesmo tick.

### Rede

- `SyncDemonDataPacket` (S->C, ~15 bytes): só para o próprio jogador. Enviado quando muda o estágio, as
  cargas ou as almas **em meias-unidades** (a resolução da HUD): o dreno contínuo não gera um pacote por tick.
- `ShadowDashPacket` (C->S): só o pedido.
- Mudou um pacote? Suba `ModNetwork.PROTOCOL`.

---

## 5. Testes

```bash
./gradlew runGameTestServer
```

| Teste | Verifica |
|---|---|
| `bossDefeatsTwoWardens` | o chefe (400 HP) vence dois Wardens enfurecidos |
| `abyssalBurnScalesWithMaxHealth` | 5 acúmulos num Warden = 27,5 por pulso; cura pela metade |
| `onlyOneBossPerCrater` | duplicata se descarta; cratera derrotada não aceita chefe novo |
| `demonStagesAdvanceByMilestones` | chifre, metabolismo, marcos dos 4 estágios, investida, atributos idempotentes, NBT, Clone, reset |
| `golemHuntsDemons` | golem ignora humano e caça o mesmo jogador depois da transformação |

Notas sobre os testes (armadilhas do GameTest no Forge 1.20.1):

- O template é carregado 1 bloco acima da origem: `y=1` relativo é o piso, entidades nascem em `y=2`.
- `helper.spawn()` não chama `finalizeSpawn`; sem isso o Warden não recebe `DIG_COOLDOWN` e passa o teste
  tentando se enterrar. Os testes usam `ForgeEventFactory.onFinalizeSpawn`, como o `/summon`.
- `makeMockServerPlayerInLevel()` quebra no Forge (o hook de rede acessa um canal nulo); use `FakePlayer`.
- O mundo do GameTest é plano e raso, então a cratera não gera nele (comportamento correto). A geração foi
  validada num servidor dedicado com mundo normal: `/locate`, blocos do altar/poço/parede, recarga do chunk
  após reiniciar, despertar, duplicata descartada e estado DEFEATED persistente.

Comandos úteis em jogo (nível 2):

```
/locate structure craterborn:crater
/craterborn demon info
/craterborn demon stage @s 3
/craterborn demon souls @s 500
/craterborn demon reset @s
/craterborn crater info
/execute positioned X Y Z run craterborn crater wake
```

---

## 6. Próximos passos sugeridos

- Modelo e animações próprios do chefe (hoje: modelo humanoide vanilla com textura e olhos emissivos próprios).
- Ritual de cura (voltar a humano) usando `DemonData.reset()` + `DemonProgression.setStage(..., 0, ...)`.
- Sincronizar o estágio para jogadores próximos (`PacketDistributor.TRACKING_ENTITY`) se houver visual de
  demônio em terceira pessoa (chifres, olhos).
- Avanços (advancements) para cada estágio, disparados em `DemonProgression.setStage`.
