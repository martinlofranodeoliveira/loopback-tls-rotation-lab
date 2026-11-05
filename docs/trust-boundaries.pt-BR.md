# Fronteiras de confiança

Desenvolvimento 0.1.0. [English](trust-boundaries.md).

## Entradas confiáveis

São confiáveis o JDK 21 e seus provedores padrão, keytool local, executável OpenSSL no PATH confiável, sistema operacional, ancestrais do workspace e código fixo do harness. Binários e configuração do host não podem estar sob controle de adversários. O launcher remove variáveis de injeção de opções Java e `OPENSSL_CONF`, mas não é uma sandbox de ambiente. Prefira um ambiente de desenvolvimento isolado, sem credenciais valiosas.

Não se carregam cacerts nem chaves do host. Os managers Java usam apenas stores explícitos das fixtures; a confiança do servidor vem de um store explicitamente vazio. Não se alteram defaults SSL globais nem propriedades globais de confiança. OpenSSL usa CA explícita, `-no-CApath -no-CAstore` e erro fatal por `-verify_return_error`. Também exige a resposta da aplicação: TLS bem-sucedido com outro serviço não basta.

## Bytes de pares locais não confiáveis

Outros processos locais podem acessar a porta efêmera loopback, ocupar as quatro admissões até timeout, provocar rejeições e enviar frames inválidos. Não podem enviar contextos, caminhos, algoritmos, comandos administrativos ou gerações pelo serviço. A aplicação não tem protocolo administrativo remoto. Limites reduzem exposição de recursos, mas não autenticam um proprietário hostil da máquina.

`localhost` é um nome lógico do certificado, não identidade pública. Conectar a 127.0.0.1 não verifica nome por si só. O cliente JSSE habilita identificação HTTPS ao envolver o socket com o nome lógico. `wrong.example` deve falhar mesmo com confiança A+B. As raízes A e B são testes sintéticos, sem autoridade fora da execução.

## Arquivos e material sensível

No harness, a única origem suportada de bundles é a PKI gerada localmente. A API limita leituras, rejeita symlink final e exige modo 0600, mas pressupõe diretórios ancestrais finitos e confiáveis. Não protege contra substituição hostil de ancestrais, mudanças de montagem, SO comprometido ou latência maliciosa de filesystem.

O arquivo privado de senha evita valores de senha na lista de argumentos. Arrays de senha e bytes de origem são zerados quando viável; cópias internas da JVM/provedor e caches do SO não têm apagamento garantido. Os PEMs públicos das CAs também ficam privados para evitar reutilização acidental ou evidência ruidosa. A remoção usa nomes fixos, sem percorrer árvore recursivamente. Falha de limpeza significa falha da demonstração. Encerramento externo abrupto pode deixar arquivos descartáveis no diretório privado ignorado pelo controle de versão.

## Evidência

Saídas de provedor, keytool e OpenSSL ficam somente em memória limitada e não são impressas nem persistidas integralmente. Evidência de sucesso usa nomes fixos e escalares normalizados; falhas mostram classe e código em maiúsculas. Não há telemetria, rastreamento, API de rede, workflow de upload de artefatos nem dados pessoais reais.

## O que não é comprovado

Inicialização do contexto não audita política de emissão, proveniência de chaves, revogação, responsabilidade operacional ou confiança pública. Contextos novos impedem retomada de sessão de mascarar este experimento, mas não definem invalidação de sessões retomadas. Conexões existentes sobrevivem intencionalmente à publicação até seus limites normais. Isso não equivale a revogação imediata de credenciais.
