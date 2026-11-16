# Divinum Leitura — Android 0.5.0

Aplicativo independente para rezar o Ofício, acompanhar a Missa e estudar Filosofia e Teologia. Android 8.0 ou superior. Mantido localmente, sem alterações no GitHub do Divinum Officium.

## O que mudou

- **Hoje:** celebração/cabeçalho litúrgico da fonte para a data e rubrica escolhidas, contagem das horas/Missa disponíveis offline e atalhos de leitura. O botão Hoje retorna à data atual. Antes do download, o app pede que se carregue a leitura; não inventa um santo ou tempo litúrgico. O cabeçalho é preservado no idioma fornecido pelo site.
- **Vários dias offline:** escolha 1, 3 ou 7 dias a partir da data selecionada. Cada dia tem oito horas e uma Missa, nas rubricas independentes das duas abas. O app conserva cada página concluída, tenta apenas as faltantes e oferece retomar o pacote pendente no início. Depois de três falhas seguidas, interrompe as solicitações para permitir nova tentativa mais tarde.
- **Roteiro de estudos:** as 16 obras organizadas em Iniciante, Intermediário e Avançado, com filtros por Fé e razão, Ética, Metafísica, Doutrina, Revelação e Espiritualidade. A ordem é uma sugestão. A conclusão é marcada pelo leitor e fica salva no aparelho.
- **Favoritos e notas:** o botão ☆ nos leitores de livros e dos pacotes diários guarda o ponto atual, trechos e anotações. Para guardar um trecho, copie o texto e cole no campo do diálogo. Pode editar, excluir, filtrar somente favoritos e pesquisar seus registros. Nos textos HTML, abrir um trecho permite localizá-lo e destacar as ocorrências com a busca nativa; no PDF, abre-se a página marcada.
- **Busca offline:** pesquisa sem diferenciação de acentos nas orações dos pacotes diários e nos livros HTML/PDF baixados. A primeira busca no PDF prepara um índice local em segundo plano. Um resultado de PDF abre a página correspondente. PDFs somente em imagem não recebem OCR. A busca geral mostra até 80 resultados e informa arquivos que não puderam ser lidos. Cópias MHTML antigas continuam em Salvos, fora dessa busca de conteúdo.
- **Lembretes opcionais:** horários locais configuráveis para as oito horas; todos desligados inicialmente. Em Android 13+, a autorização de notificações é solicitada pelo botão da tela. Funcionam sem internet, usam alarmes inexatos e podem atrasar conforme a economia de bateria do Android. O agendamento é recalculado após reinício, alteração de horário/fuso e atualização. Tocar no aviso abre a hora da data atual, usando a cópia offline quando existir.
- **Exportar/importar:** em Favoritos e notas, escolha o destino/origem usando o seletor de arquivos do Android. O JSON contém notas, favoritos, progresso, tema, idiomas, rubricas e tamanhos de texto. A importação valida o arquivo inteiro antes de uma gravação, une registros por identificador e prefere o mais recente. Não inclui livros, orações, cookies nem lembretes. Limite de 2 MB e 1.000 registros, com limites por campo e controle do total para manter o backup importável.

## Leituras e offline

Os pacotes usam Calendário Geral, modo leigo, texto expandido e Latim/Português. Os cálculos e textos vêm do motor original por HTTPS. Avisos e limitações de cada edição são preservados. Para obter os dados litúrgicos do início, baixe as leituras para a data/rubrica escolhidas.

O download ocorre na tela aberta, na sessão WebView do site. A conclusão só conta páginas reconhecidas, guardadas e com integridade conferida. A página de erro não é aceita como oração. Os arquivos ficam na área privada persistente, separados por data, versão e Ofício/Missa, com manifesto atômico e SHA-256. Imagens disponíveis são incorporadas quando possível; ausências são indicadas.

As horas baixadas abrem o leitor local com JavaScript, acesso a arquivos/conteúdo e cargas de rede desativados. Português, Latim, Ambos, fonte, tema, posição, notas e busca continuam funcionando sem conexão. Dias anteriores ficam em Biblioteca → Salvos. Uma nova data/rubrica requer outro pacote. As cópias não expiram à meia-noite.

O download pode ser retomado após sair, recriar a tela ou reabrir o app. O atalho de pacote pendente preserva as datas e rubricas do download iniciado. Caso comece outro pacote, o atalho passa a apontar para ele; os arquivos do anterior continuam disponíveis em Salvos.

## Biblioteca

São 16 obras e documentos, com 10 itens em português, quatro em inglês e dois em latim. Todos mantêm idioma e fonte identificados. A seleção é inicial e não equivale a um currículo completo. As fontes e edições estão em FONTES-BIBLIOTECA.md.

Quatorze itens podem ser baixados sob solicitação. Catecismo completo e Suma Teológica são consultas online por capítulos. O PDF é guardado integralmente. Nos livros HTML baixados, recursos externos ficam bloqueados; a disponibilidade de imagens de origem não é garantida. As cópias MHTML feitas anteriormente continuam acessíveis em Salvos.

Os temas Claro, Escuro e Seguir o sistema permanecem compartilhados pelos leitores. As 15 rubricas do Ofício e oito da Missa continuam separadas, com busca e preferências independentes.

## Instalação e dados

O APK 0.5.0 usa o mesmo identificador org.divinumofficium.reader, versão interna 7 e certificado das versões anteriores. Instale por cima, sem desinstalar. A atualização conserva leituras, livros, preferências e rubricas.

Desinstalar ou limpar os dados apaga os downloads e os registros pessoais. A exportação manual permite guardar seus registros; os textos precisam ser baixados novamente em outro aparelho. O backup automático do Android segue desativado.

## Compilar e verificar

Use JDK 17/21, SDK Platform 35 e Build Tools 35.0.0. Configure ANDROID_HOME ou local.properties. No Windows, use gradlew.bat.

```sh
./gradlew assembleDebug testDebugUnitTest lintDebug
npm install
npx playwright install chromium
node tools/check-daily.cjs --capture
./gradlew testDebugUnitTest
node tools/check-daily.cjs --verify
npm run test:reader
```

CHROMIUM_PATH pode indicar um Chromium existente. O APK fica em app/build/outputs/apk/debug/app-debug.apk. Resultados Android estão em app/build/reports e imagens em app/build/previews; testes de navegador em build/previews. Consulte VALIDACAO.md para limites e roteiro no aparelho.

## Organização

- MainActivity: navegação, Hoje, Ofício, Missa, Biblioteca e cópias MHTML.
- DailyOfficeStore, DailyDownloadActivity e DailyReaderActivity: pacotes duráveis, download e leitura sem rede.
- TodayInfo: extração do cabeçalho recebido, sem calendário litúrgico local.
- StudyActivity e study.json: roteiro, filtros e progresso.
- PersonalStore, PersonalActivity e ReadingTools: registros, favoritos, busca no texto e backup validado.
- OfflineSearch e OfflineSearchActivity: busca em HTML/orações e extração/indexação local de PDF.
- ReminderPlanner, ReminderReceiver e ReminderActivity: preferências e avisos locais opcionais.
- BookCatalog/BookStore/BookReaderActivity: fontes, downloads e leitores de livros.
- AppAppearance e DailyActivity: temas e superfícies nativas.
- RubricCatalog, ReadingRequest e UrlPolicy: opções oficiais e consultas HTTPS.

## Privacidade e licenças

Permissões: INTERNET, POST_NOTIFICATIONS e RECEIVE_BOOT_COMPLETED. O app não tem publicidade, análise própria ou ponte JavaScript para código nativo. As fontes recebem solicitações quando são consultadas online. Registros e índices ficam no aparelho; o arquivo de backup só é escrito no destino escolhido pelo usuário.

A extração de PDF usa PDFBox-Android 2.0.27.0 (Apache 2.0) e suas dependências Bouncy Castle 1.72. Os avisos e licenças estão nos assets THIRD-PARTY.txt, NOTICE-PDFBox.txt, LICENSE-PDFBox.txt, LICENSE-Apache-2.0.txt e LICENSE-BouncyCastle.txt. O tamanho do APK aumentou por incluir a biblioteca de extração de PDF.

Código do aplicativo: MIT (LICENSE). Aviso do Divinum Officium: assets/LICENSE-DivinumOfficium.txt. Cada publicação mantém os direitos da edição de origem.
