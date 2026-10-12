# Jogo da Memória Online com Java RMI

Jogo da memória multijogador (Trabalho 2 de Sistemas Distribuídos, CEFET). Um **middleware** central e vários
**clientes** com interface gráfica, cada um em seu próprio processo, se comunicam por Java RMI.

Este guia vale para **Linux, macOS e Windows**. Todos os comandos usam apenas `mvn` e `java`, que funcionam
da mesma forma no Terminal (Linux/macOS), no Prompt de Comando e no PowerShell (Windows).

## 0. Execução rápida (apenas com os jars)

A pasta `executaveis/` já traz os jars prontos, então não é preciso compilar nada. Basta ter o **JDK 17 ou 21**
instalado (confira com `java -version`).

- `executaveis/middleware-all.jar`
- `executaveis/cliente-all.jar`

Na raiz do projeto:

1. **Middleware.** Em um terminal:

   ```
   java -jar executaveis/middleware-all.jar
   ```

   Responda às perguntas (IP, porta e tamanho do tabuleiro). Para testar na mesma máquina, use `localhost`,
   porta `1099` e o tabuleiro sugerido, aceitando os padrões com **Enter**. Deixe esse terminal aberto.

2. **Clientes.** Abra um terminal por jogador (de 2 a 4) e execute em cada um:

   ```
   java -jar executaveis/cliente-all.jar
   ```

   Na tela inicial, informe um nome diferente para cada jogador, mantenha host `localhost` e porta `1099`, clique
   em **Conectar** e, na sala de espera, em **Pronto**.

Para jogar em máquinas diferentes, copie os jars para cada máquina e siga a seção 4 (IP do middleware, campo
**Meu IP** e firewall).

**Se algo der errado** (por exemplo, erro de versão do Java como `UnsupportedClassVersionError`, jar corrompido
ou incompatível com o seu ambiente), as próximas seções explicam como compilar o projeto novamente e gerar jars
novos com o Maven. Os detalhes de execução e a lista de problemas comuns também estão nelas.

## 1. Pré-requisitos

- **JDK 17 ou 21.** Confira com `java -version`.
- **Apache Maven 3.x.** Confira com `mvn -v`. Na primeira compilação o Maven baixa dependências, então é preciso
  ter acesso à internet.

## 2. Compilação

Na raiz do projeto, uma vez:

```
mvn -q clean package
```

Isso gera dois jars executáveis:

- `middleware/target/middleware-all.jar`
- `cliente/target/cliente-all.jar`

## 3. Execução na mesma máquina

### 3.1 Middleware

Em um terminal, na raiz do projeto:

```
java -jar middleware/target/middleware-all.jar
```

Ele faz três perguntas. **Enter** aceita o valor sugerido entre parênteses:

| Pergunta | Padrão | O que informar |
| --- | --- | --- |
| IP deste host | endereço detectado | Para testar na mesma máquina, use `localhost`. |
| Porta do Registry | `1099` | Inteiro de 1 a 65535. |
| Linhas e colunas do tabuleiro | `2` e `3` | O total de posições deve ser **par**, de **4 a 24**. Por exemplo, 2×3, 3×4 ou 4×4. Combinações inválidas (como 3×3) são recusadas e a pergunta se repete. |

Quando aparecer `Middleware do Jogo da Memória pronto.`, deixe esse terminal aberto. `Ctrl+C` encerra o servidor.

### 3.2 Clientes

Abra **um terminal por jogador** (de 2 a 4 jogadores) e execute em cada um:

```
java -jar cliente/target/cliente-all.jar
```

Na tela inicial de cada cliente, preencha:

| Campo | Padrão | Descrição |
| --- | --- | --- |
| Nome | vazio | Nome do jogador, de 1 a 20 caracteres, único na sala. |
| Host | `localhost` | Máquina onde o middleware está rodando. |
| Porta | `1099` | A mesma porta informada ao middleware. |
| Timeout (s) | `10` | Tempo máximo para localizar o middleware (1 a 600). |
| Meu IP | vazio | Deixe vazio na mesma máquina. Veja a seção 4 para máquinas diferentes. |

Clique em **Conectar** e, na sala de espera, em **Pronto**. A partida começa quando há pelo menos 2 jogadores e
todos estão prontos. Para jogar de novo, feche a sessão pela tela de sumário e reconecte, ou reinicie o middleware.

## 4. Execução em máquinas diferentes

Exemplo: middleware em `192.168.0.10` e clientes em `192.168.0.20` e `192.168.0.30`. Use os IPs reais da sua rede
(no Linux/macOS, `hostname -I` ou `ifconfig`; no Windows, `ipconfig`).

1. **Compile** o projeto em cada máquina (seção 2), ou copie os jars gerados.
2. **Middleware** (`192.168.0.10`): execute o jar e, na pergunta do IP, digite o IP **da própria máquina do
   middleware** (`192.168.0.10`), não `localhost`.
3. **Clientes** (`192.168.0.20`, `192.168.0.30`): execute o jar e preencha:
   - **Host**: `192.168.0.10` (IP do middleware);
   - **Porta**: a mesma do middleware;
   - **Meu IP**: o IP **da própria máquina do cliente**. Ele é obrigatório, porque o middleware chama o cliente
     de volta (callback) e precisa de um endereço alcançável.
4. **Firewall.** Permita conexões de entrada:
   - na máquina do middleware: porta TCP do Registry (padrão `1099`) e as portas dos objetos remotos, que são
     sorteadas a cada execução;
   - nas máquinas dos clientes: conexões vindas do middleware (a porta do callback também é sorteada).

   No Windows, aceite o aviso do Firewall do Windows Defender na primeira execução do `java`, permitindo redes
   privadas. No Linux/macOS, libere o `java` no firewall ou desative-o temporariamente para o teste.

O campo **Meu IP** só é aplicado na primeira tentativa de conexão do processo e depois fica somente leitura. Se
precisar corrigi-lo, feche o cliente e abra de novo.

## 5. Problemas comuns

| Sintoma | O que fazer |
| --- | --- |
| `Connection refused` na tela de conexão | Confira se o middleware está rodando e se host e porta estão corretos. Teste a porta: `nc -vz 192.168.0.10 1099` (Linux/macOS) ou `Test-NetConnection 192.168.0.10 -Port 1099` (PowerShell). |
| A conexão abre, mas as chamadas travam ou falham para um IP estranho (`127.0.0.1`, `127.0.1.1`) | Reinicie o middleware e informe o IP real da máquina, alcançável pelos clientes. |
| Entrada rejeitada com `CALLBACK_INVALIDO` | Feche e abra o cliente, preenchendo **Meu IP** com o IP da máquina do cliente, e libere o firewall dessa máquina. |
| Funciona com `localhost`, mas não entre máquinas | Falta o IP correto em uma das pontas: no middleware (pergunta do IP) ou no cliente (**Meu IP**). |
| Middleware encerra com código 1: porta em uso | Outro programa usa a porta. Encerre-o ou informe outra porta ao middleware (e aos clientes). |
| Middleware encerra com código 2 | A entrada terminou sem IP, porta ou tabuleiro. Execute em um terminal interativo. |
| Entrada rejeitada: nome em uso, sala cheia ou partida em andamento | Use nomes diferentes (maiúsculas e minúsculas contam como iguais) e aguarde o fim da sessão, ou reinicie o middleware. |
| O cliente fecha logo ao abrir, com o aviso de ambiente gráfico | Execute em uma sessão com interface gráfica. |
