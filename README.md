# CustomScoreboard

Plugin de scoreboard (barra lateral) para Spigot/Paper, com título animado e
linhas 100% configuráveis via `config.yml`, inspirado no estilo geral das
scoreboards de servidores de Survival (título, moedas, poder/kills/mortes,
clã, mundo e rodapé com site) — mas escrito do zero, sem copiar textos ou
código de nenhum servidor específico.

## Requisitos

- Java 21
- Maven 3.9+
- Servidor Spigot ou Paper (o `pom.xml` está configurado para 1.21.1; ajuste
  a propriedade `spigot.version` se seu servidor for outra versão, ex.
  `1.20.4-R0.1-SNAPSHOT`).

> Observação: "Java 26" e "Spigot 26.2" não são versões existentes hoje.
> Este projeto usa Java 21 (LTS atual) e a API do Spigot na versão do
> Minecraft (ex.: 1.21.x). Troque a versão no `pom.xml` para bater
> exatamente com o `.jar` do seu servidor.

## Como compilar

Como o Spigot API não fica disponível no Maven Central (precisa vir do
BuildTools oficial da SpigotMC ou do repositório deles), rode isto numa
máquina com acesso à internet:

```bash
mvn clean package
```

O `.jar` final aparece em `target/CustomScoreboard.jar`.

Se preferir, use o BuildTools oficial (`https://www.spigotmc.org/wiki/buildtools/`)
para gerar o `spigot-api` local antes de compilar, caso o repositório remoto
do Spigot esteja fora do ar.

## Instalação

1. Copie `CustomScoreboard.jar` para a pasta `plugins/` do servidor.
2. (Opcional) Instale `Vault` + um plugin de economia, para o placeholder `%coins%`.
3. (Opcional) Instale `PlaceholderAPI` e as expansions que quiser usar
   (ex.: clãs, mcMMO, etc.) para usar placeholders como `%clans_name%`.
4. Inicie o servidor. O arquivo `config.yml` será gerado em
   `plugins/CustomScoreboard/config.yml`.
5. Edite as linhas e cores como quiser, depois rode `/scoreboard reload`.

## Comandos

| Comando              | Descrição                                  | Permissão                |
|-----------------------|---------------------------------------------|---------------------------|
| `/scoreboard reload`  | Recarrega o config.yml                      | `customscoreboard.admin` |
| `/scoreboard on`      | Liga a scoreboard para o próprio jogador    | `customscoreboard.use`   |
| `/scoreboard off`     | Desliga a scoreboard para o próprio jogador | `customscoreboard.use`   |

## Placeholders internos

`%player%`, `%world%`, `%online%`, `%maxonline%`, `%date%`, `%kills%`,
`%deaths%`, `%kdr%`, `%playtime%`, `%coins%` (via Vault).

Com `use-placeholderapi: true` no config, qualquer placeholder de qualquer
expansion instalada também funciona (ex.: `%clans_name%`, `%mcmmo_power_level%`).
