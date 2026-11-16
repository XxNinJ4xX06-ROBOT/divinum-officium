# Validação — 0.5.0

## Executado

- Compilação Android, JUnit/Robolectric e lint, com APIs 26 e 35.
- 87 testes Android/JVM: os fluxos anteriores de rubricas, leitores, temas e pacotes diários, mais roteiro de estudos, dados pessoais, backup, busca PDF/HTML e lembretes.
- Backup: exportação e importação preservam registros/progresso/preferências; reimportar não duplica identificadores; registro mais recente prevalece. Fonte inválida, arquivo grande e preferência inválida são rejeitados antes da gravação. Downloads e lembretes ficam fora da exportação.
- Pontos de leitura: coordenadas de livro/PDF e dados de data/rubrica/hora são transportados para o leitor. Sem o pacote diário exato, o ponto não abre outra data por engano.
- Roteiro: os 16 livros aparecem uma única vez, com nível e tema válidos; progresso pode ser marcado e desmarcado e persiste.
- Hoje: cabeçalho recebido é exibido inclusive em capturas antigas, sem reutilizar informações de outra data/rubrica.
- Vários dias: pacote de sete dias atravessa o ano e pula os nove textos já guardados do primeiro dia, mantendo os parâmetros originais no atalho de retomada. Pacote concluído remove o atalho pendente.
- Busca sem rede: encontra textos de orações e HTML por termos sem acentos; exclui CSS/scripts da pesquisa; extrai texto de um PDF de prova, guarda o índice e retorna a página correspondente. Há teste da busca limitada a um livro.
- Lembretes: todos inicialmente desligados; agendamento/cancelamento de avisos opcionais; cálculo de horário no fuso America/Sao_Paulo, passagem da meia-noite e horário de verão. O receptor de boot apenas agenda; um lembrete ativado produz uma notificação local que abre a hora correta; desativado, não publica.
- Telas novas renderizadas em formato de celular pelo Robolectric: Hoje, roteiro, favoritos/notas, busca e lembretes, além das telas anteriores em ambos os temas.
- Chromium: 17 verificações de captura, incluindo cabeçalho litúrgico e preservação dos avisos; 49 verificações dos documentos offline produzidos pelo Java, com rede offline e JavaScript desligado, sem solicitações de rede; 62 verificações do leitor online em 320, 393 e 600 pixels.
- APK conferido quanto a nome de pacote, versão, permissões, integridade e certificado de atualização. Arquivo enviado ao Google Drive é baixado novamente para comparação byte a byte.

## Limites

Não há aparelho/emulador Android real neste ambiente. Robolectric simula o framework e callbacks de WebView; as verificações Chromium usam uma página representativa do gerador litúrgico. Isso não comprova o download do servidor público, a instalação, a seleção/cópia de texto, o seletor de documentos ou a entrega de alarmes em um celular real.

O site público retornou HTTP 403 neste ambiente durante a validação anterior. O fluxo diário usa a sessão WebView do aparelho, mantém erros/timeout visíveis e preserva o que já foi guardado. A conferência do download real e do modo avião precisa ser feita no aparelho.

A extração PDF foi testada com um PDF sintético de texto; não com todas as fontes, codificações e formatos de publicação existentes. PDFs só em imagem não recebem OCR. O PDF real de Confissões foi validado como arquivo em uma etapa anterior, sem substituir o teste do renderizador/extrator Android no celular. A primeira indexação pode ser lenta e é executada em segundo plano.

Os alarmes são inexatos. Android, modo de economia de bateria, fuso e políticas do fabricante podem atrasar a entrega. Os testes comprovam a configuração e o cálculo do horário, não a entrega sob todas essas condições.

As capturas preservam os textos/avisos recebidos, sem corrigir uma edição incompleta da fonte. Hoje mostra o cabeçalho da rubrica do Ofício selecionada depois de uma leitura ter sido guardada. Livros online por capítulos e cópias MHTML anteriores conservam suas limitações; a nova busca geral cobre pacotes diários e livros baixados HTML/PDF.

O backup manual leva registros e preferências. Livros, orações, imagens, índices PDF, cookies e configurações dos lembretes não são transferidos.

## Reproduzir

```sh
node tools/check-daily.cjs --capture
./gradlew assembleDebug testDebugUnitTest lintDebug
node tools/check-daily.cjs --verify
node tools/check-reader.cjs
```

Requer JDK 17/21, SDK 35, Node e Playwright/Chromium. CHROMIUM_PATH pode indicar um navegador instalado. Os relatórios e imagens ficam em app/build e build/previews. A fixture não é uma resposta atual do servidor público.

## Conferir no celular

1. Atualizar sem desinstalar e conferir v0.5.0 no cabeçalho.
2. Com internet, escolher rubricas/data e baixar um dia até 9/9. Repetir com três ou sete dias; sair no meio e usar Retomar download iniciado.
3. Ativar modo avião, fechar/reabrir e conferir todas as horas/Missa, a data e o texto final. Trocar rubrica/data não deve abrir um pacote incorreto.
4. Abrir Hoje e conferir o cabeçalho contra a fonte; usar Voltar para hoje.
5. Na Biblioteca, abrir Roteiro de estudos, filtrar nível/tema e marcar uma leitura; fechar/reabrir para conferir a persistência.
6. Nos leitores, usar ☆ para favoritar um ponto e guardar um trecho/nota. Abrir por Favoritos e notas, editar, filtrar e localizar o trecho. No PDF, verificar a página marcada.
7. Baixar um HTML e o PDF de Confissões; buscar um termo conhecido sem internet. Conferir abertura do resultado e da página do PDF.
8. Exportar os dados pelo seletor Android; importar de volta e conferir ausência de duplicatas. Em outro aparelho, os registros devem aparecer, mas os textos precisam ser baixados novamente.
9. Ativar só um lembrete de teste e autorizar notificações; escolher um horário próximo. Conferir a entrega, tocar no aviso e desativar depois. Verificar também após reiniciar o aparelho.

Nenhuma alteração no GitHub.
