# Lista Mercado

App Android pessoal para organizar as compras de mercado e descobrir **onde cada coisa sai mais barata**.

## O que o app faz

| Aba | Para quê |
| --- | --- |
| **Listas** | Checklists de compras. Marque os itens enquanto põe no carrinho. Cada lista mostra o **mercado mais barato** para ela e o preço conhecido de cada item. Pelo menu ⋮ dá para adicionar de uma vez todos os produtos que você "sempre compra". |
| **Compras** | Histórico do que você comprou, onde e quanto pagou, com o gasto por mês. Abrindo uma lista e tocando em **Registrar compra**, os itens marcados já vêm preenchidos (e com o último preço conhecido no mercado escolhido). |
| **Promoções** | Promoções e preços por mercado, com validade. Dá para cadastrar à mão ou **importar um encarte**. |
| **Comparar** | Ranking dos mercados para a sua cesta (produtos ⭐ "sempre compro") ou para qualquer lista: total estimado, itens em promoção, quanto economiza e quanto daria comprando cada item no mercado mais barato. |
| **Cadastros** | Produtos (marque ⭐ os que você sempre compra) e mercados. Tocando num produto você vê o preço atual em cada mercado, as promoções e o histórico de preços pagos. |

### Importar encarte (PDF, foto ou texto)

1. Em **Promoções → Importar encarte**, escolha um PDF, uma imagem ou cole o texto das ofertas.
   Também funciona pelo **Compartilhar** de outros apps: no WhatsApp, abra o PDF do mercado → compartilhar → *Lista Mercado*.
2. O app lê o encarte **no próprio celular, sem internet**:
   - PDF com texto: lê as palavras e a posição delas na página (cada preço é ligado ao nome do produto logo acima ou ao lado);
   - PDF escaneado ou foto: reconhece o texto com OCR (ML Kit) e faz a mesma associação.
3. As ofertas são comparadas com os produtos que você já tem cadastrados (ex.: o seu "Arroz Tio João" reconhece "ARROZ TIO JOÃO TIPO 1 5KG"). As dos seus produtos já vêm marcadas.
4. Confira o mercado, a validade (detectada automaticamente quando o encarte diz "válido até…"), ajuste o que precisar e salve.

As promoções salvas passam a contar na comparação imediatamente. Quando a validade vence, o app volta a usar o último preço que você pagou naquele mercado.

### Como o "preço atual" é calculado

Para cada produto em cada mercado:
1. a promoção válida mais barata (com data de validade em vigor);
2. se não houver, o preço mais recente: o último pago numa compra ou um preço informado sem validade.

Na comparação, quando um mercado não tem preço para algum item da cesta, o total estimado usa a média dos outros mercados para aquele item (e o app avisa quantos itens foram estimados).

## Instalar no celular

Toda vez que o código é enviado ao GitHub, o GitHub Actions compila o app:

1. Abra a aba **Actions** do repositório → execução mais recente de **Build APK**.
2. Em **Artifacts**, baixe `lista-mercado-apk` (vem em .zip; dentro está o `lista-mercado.apk`).
3. No Android, abra o APK e permita "instalar apps desconhecidos" quando pedir.

Para ter um link direto de download, crie uma tag (`git tag v1.0.0 && git push origin v1.0.0`): o APK aparece na página **Releases**.

O APK é sempre assinado com a mesma chave (`app/listamercado.keystore`), então as versões novas instalam por cima da antiga **sem perder os dados**. Os dados ficam só no celular (banco SQLite) e entram no backup automático do Android.

## Desenvolvimento

- Kotlin, Jetpack Compose (Material 3), Room, Navigation Compose
- PdfBox-Android (texto de PDF) e ML Kit Text Recognition (OCR offline)
- `core/`: módulo Kotlin puro com a lógica testável — leitura de preços dos encartes (`OfferParser`), correspondência de nomes de produtos (`ProductMatcher`) e comparação de preços (`PriceComparator`)

```bash
./gradlew :core:test            # testes da lógica
./gradlew :app:assembleRelease  # gera app/build/outputs/apk/release/app-release.apk
```

Requer JDK 17 e Android SDK (compileSdk 35). Abrindo a pasta no Android Studio, tudo é configurado automaticamente.
