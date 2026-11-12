# Como compilar o Craterborn

Mod para **Minecraft 1.20.1 + Forge 47.4.0**, compilado com ForgeGradle 6 e **Java 17**.
Escolha **um** dos caminhos. O Caminho 1 não exige instalar nada no seu computador.

## Caminho 1: GitHub Actions (recomendado)

1. Crie uma conta em github.com e um repositório novo (pode ser privado).
2. **Extraia o `.zip`** do projeto. A pasta certa é a que tem o arquivo `build.gradle` dentro.
   Envie **todo o conteúdo** dessa pasta para o repositório, incluindo a pasta oculta `.github`.
   Pelo site: *Add file → Upload files*, arraste tudo e clique em *Commit changes*.
   - Se a `.github` não subir, use *Add file → Create new file*, digite `.github/workflows/build.yml`
     como nome e cole o conteúdo do `build.yml` desta pasta.
   - O site aceita poucos arquivos por envio (em torno de 100). Nas próximas etapas o projeto terá mais
     que isso: suba por partes (uma pasta por vez) ou use o GitHub Desktop (<https://desktop.github.com>).
3. Abra a aba **Actions**. O fluxo **Build mod** começa sozinho (o primeiro build leva vários minutos).
   Se não começar: *Build mod → Run workflow*.
4. Quando terminar com ✅, abra a execução, role até **Artifacts** e baixe **craterborn-jar**.
   Vem dentro de um `.zip`: extraia e pegue o `.jar`.
5. Se der ❌: abra a execução, clique no passo marcado em vermelho e me envie o texto do erro.

## Caminho 2: compilar no seu computador

1. Instale o **JDK 17** (Temurin): <https://adoptium.net/temurin/releases/?version=17>
2. Abra um terminal na pasta do projeto e aponte o `JAVA_HOME` para o JDK 17 **só nesse terminal**
   (o seu Java 22 continua intacto):
   - Windows (PowerShell), ajustando a pasta ao número da sua versão:
     ```
     $env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-17.0.x.x-hotspot"
     .\gradlew build
     ```
   - Linux/macOS:
     ```
     JAVA_HOME=/caminho/do/jdk-17 ./gradlew build
     ```
3. O mod fica em `build/libs/craterborn-0.1.0.jar`.

O primeiro build baixa e prepara o Minecraft (vários minutos, precisa de internet).
Rodar o Gradle direto no Java 22 **não foi testado**; use o JDK 17 como acima.

## Instalar o mod

1. Instale o **Forge 1.20.1** (recomendado: 47.4.0) com o instalador oficial de
   <https://files.minecraftforge.net>, opção *Install client*. Se o instalador der erro com o seu Java 22,
   rode-o com o JDK 17: `caminho-do-jdk17/bin/java -jar forge-1.20.1-47.4.0-installer.jar`.
2. Abra o launcher, escolha o perfil do Forge e inicie o jogo uma vez (isso cria a pasta `mods`).
3. Copie o `.jar` para `.minecraft/mods` e inicie de novo.
   O launcher oficial do Minecraft 1.20.1 já usa o Java 17 que vem junto com ele. Em outros launchers,
   configure Java 17 para essa instância.

## Configuração

Na primeira execução o jogo cria `config/craterborn-common.toml`, com o balanceamento do chefe, do Fogo
Abissal, do jogador demônio e dos marcos de cada estágio. A raridade das crateras fica no datapack
(`data/craterborn/worldgen/structure_set/crater.json`).

## Testes e arquitetura

- `./gradlew runGameTestServer` roda os testes automáticos (o chefe contra dois Wardens, a regra de um chefe
  por cratera, os estágios do demônio, etc.).
- A organização do código, os eventos usados e o porquê de cada decisão estão em `ARQUITETURA.md`.
