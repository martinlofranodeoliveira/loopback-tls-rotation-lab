# Loopback TLS Rotation Lab

Versão de desenvolvimento **0.1.0**, por **Lofrano Tecnologia**. Código original Java 21, licença MIT. [English](README.md) · [Página oficial](https://lofranotecnologia.com.br) · [Repositório](https://github.com/martinlofranodeoliveira/loopback-tls-rotation-lab)

Experimento JSSE sem interface gráfica: substituir o contexto escolhido para **novos accepts**, preservando o contexto capturado por conexões existentes. Cada resposta informa a geração capturada. Não é um terminador TLS de produção, autoridade certificadora, certificação de segurança ou alegação de confiança pública.

## Instalação e execução

Use Linux com permissões POSIX, diretório de trabalho confiável, JDK **21** com `javac` e `keytool`, GNU Make e OpenSSL **3.x** com suporte a `-no-CAstore`. A execução rejeita outras versões principais do Java. Não há Maven, Gradle, JUnit, bibliotecas baixadas, contas, credenciais fornecidas nem dependência de modelo/API.

Em Debian/Ubuntu, caso faltem as ferramentas:

```sh
sudo apt-get update
sudo apt-get install --yes --no-install-recommends make openssl
```

Instale uma distribuição JDK 21 pelo mecanismo normal de pacotes/imagens do seu ambiente. Verifique e execute:

```sh
java -version
javac -version
openssl version
make test
make demo
```

Os dois alvos compilam antes de executar todas as verificações explícitas, sem depender de `assert` habilitado. A demonstração também exige os casos negativos. Chaves sintéticas são geradas sequencialmente; o serviço usa porta efêmera em loopback IPv4; clientes Java separados e OpenSSL verificam os resultados; os processos são aguardados e o diretório de chaves é removido. Os comandos de teste/demo usam somente rede loopback. Instalação de pacotes e preparação das ferramentas de CI são etapas separadas com acesso à rede.

As duas últimas linhas esperadas de `make demo`, depois da compilação e das verificações nomeadas:

```text
PASS — all declared checks; generation=2; sockets=0; children=0; fixturesRemoved=true
{"version":"0.1.0","mode":"demo","result":"pass","generation":2,"activeSockets":0,"ownedChildren":0,"fixturesRemoved":true,"checks":24}
```

O JSON também fica em `build/evidence.json`. Não contém caminhos, portas, senhas, certificados, identificadores de processo, horários ou diagnósticos brutos. `test` altera apenas o campo `mode`. São exemplos de saída esperada, não resultados de implantação ou desempenho.

## Exemplos pequenos

O executável suportado é o harness. A API pública de seleção é `ContextRegistry`:

```java
try (ContextRegistry contexts = new ContextRegistry(initialBundle, ownedPassword)) {
    ContextRegistry.Candidate candidate = contexts.loadCandidate(replacementBundle);
    long expected = contexts.status().generation();
    ContextRegistry.Publication result = contexts.publish(expected, candidate);
}
java.util.Arrays.fill(ownedPassword, '\0');
```

As variáveis representam fixtures geradas localmente, não chaves do host. Em um chamador real, limpe o array do chamador em `finally`. O registro guarda uma cópia. Arquivo inválido gera `IOException` normalizada; publicação com geração divergente retorna `STALE_GENERATION`. Nenhuma delas altera a geração selecionada. O candidato opaco não expõe `SSLContext` nem sua fábrica.

Uma conexão validada envia `hello` com LF e recebe:

```text
GEN 1 BYTES 5
```

Depois da troca, um novo accept responde `GEN 2 BYTES 5`. A conexão capturada na geração 1 continua informando geração 1. Uma barreira positiva de admissão também comprova o caso aceito **antes** da publicação e negociado **depois** dela.

## Motivação

O `accept()` não bloqueante e a captura da geração compartilham a trava de publicação. A espera no selector fica fora dela. Apenas trocar uma referência a `SSLServerSocket` não demonstraria esse contrato. Leitura e inicialização completa do candidato ocorrem antes da publicação; nenhum contexto mutável é entregue ao chamador.

Clientes conectam fisicamente a `127.0.0.1`, usam `localhost` como nome lógico e habilitam identificação HTTPS e somente TLS 1.3. Cada nova sondagem usa processo/contexto novo e confiança explícita das fixtures. Bundles A, B e A+B demonstram a transição sem confiança padrão nem reutilização de sessão mascarando o certificado. OpenSSL verifica de forma independente a cadeia, o nome e a resposta da aplicação.

## Limites e fronteiras

- Quatro conexões admitidas, inclusive handshakes parados; quatro workers e fila limitada. O quinto socket aceito para rejeição é fechado imediatamente, sem admissão. O backlog do SO é separado.
- Frames LF com até 1024 **bytes** de conteúdo, UTF-8 estrito, CR proibido, 16 trocas por conexão. Respostas limitadas a 64 bytes antes de LF.
- Conexão/handshake: 2 segundos; leitura ociosa: 8 segundos; orçamento monotônico do controlador: 300 segundos incluindo geração de chaves. Encerramento e coleta têm esperas finitas adicionais.
- Escritas pequenas bloqueantes não possuem deadline de escrita do SO. No deadline global o controlador fecha sockets registrados e encerra somente seus próprios objetos `Process`. Falta de escalonamento, bloqueios de provedor, disco ou SO podem atrasar observação e limpeza. Não há garantia de tempo real.
- Templates fixos keytool usam RSA-2048/SHA256withRSA, duas autoridades sintéticas descartáveis e folhas com SAN localhost. Nunca reutilize as chaves. Senhas são aleatórias e geradas durante a execução, sem valores de senha nos argumentos.
- Leituras limitadas rejeitam link simbólico no componente final e exigem modo privado POSIX. Diretórios ancestrais finitos e confiáveis são pressupostos; não é uma sandbox contra filesystem hostil. Morte abrupta da JVM/SO pode impedir `finally`; qualquer sobra em `build/private` continua sendo material privado descartável.
- Publicação existe somente em memória. Reinício exige bundle inicial explícito e volta à geração 1, sem watcher nem seleção persistida.
- Fora do escopo: mTLS, CA externa/ACME, revogação, endpoints remotos, semântica de rotação com retomada de sessão e auditoria completa de política de certificados. Não se alega conformidade ampla com padrões.

Consulte o [contrato preciso](docs/contract.md), [desenho](docs/design.md), [fronteiras em português](docs/trust-boundaries.pt-BR.md), [PKI](docs/pki.md) e [testes](docs/testing.md). Contato geral pela [página oficial](https://lofranotecnologia.com.br). Relatos de segurança pelo [canal privado do GitHub](https://github.com/martinlofranodeoliveira/loopback-tls-rotation-lab/security/advisories/new).

Os probes negativos distinguem evidência de falha de transporte: um timeout de leitura produz `CLIENT_ERROR` / código 20, e um EOF TCP antes de qualquer certificado também é falha de setup. `VERIFY_FAILED` / código 10 exige uma exceção padrão de certificado na cadeia limitada de causas. As duas distinções têm regressões com processos e peers reais; um intervalo silencioso nunca prova fechamento.
