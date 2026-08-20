# Sims 4 Translator — App Android (TWA) via Termux + GitHub Actions

Esquece Android Studio. O fluxo aqui é: você gera a chave de assinatura no
Termux (2 comandos), sobe o projeto pro GitHub, e o **GitHub Actions**
(computador na nuvem, de graça) compila o APK assinado pra você. Você só
baixa o resultado.

## Passo 1 — Instalar o Java no Termux (só pra gerar a chave)

```bash
pkg update -y
pkg install openjdk-17 -y
```

## Passo 2 — Gerar sua keystore de assinatura

Isso só precisa ser feito UMA vez. Guarde esse arquivo e as senhas pra
sempre — toda atualização futura do app precisa da mesma chave.

```bash
keytool -genkey -v -keystore sims4translator.keystore \
  -alias sims4translator -keyalg RSA -keysize 2048 -validity 10000
```

Ele vai perguntar nome, organização, etc — pode preencher com qualquer
coisa ou deixar em branco. As únicas coisas importantes são as **senhas**
que ele pede no início (senha da keystore e senha da chave — podem ser
iguais).

## Passo 3 — Converter a keystore pra base64 (pra colar como Secret no GitHub)

```bash
base64 -w 0 sims4translator.keystore > keystore_base64.txt
cat keystore_base64.txt
```

Se der erro de "base64: command not found":
```bash
pkg install coreutils -y
```

Copie TODO o texto que aparecer (é uma linha gigante).

## Passo 4 — Subir esse projeto pro GitHub

Se ainda não tiver o `git` no Termux:
```bash
pkg install git -y
```

Dentro da pasta do projeto (depois de extrair o zip que te mandei):
```bash
git init
git add .
git commit -m "Setup do app Android via TWA"
git branch -M main
git remote add origin https://github.com/alucardyummy/SEU-REPO-AQUI.git
git push -u origin main
```

(Pode ser um repositório novo, tipo `Sims4Translator-App`, ou uma pasta
dentro do repositório que você já tem — sua escolha.)

## Passo 5 — Cadastrar os Secrets no GitHub

No repositório, vá em **Settings → Secrets and variables → Actions →
New repository secret** e crie estes 4:

| Nome do Secret       | Valor                                              |
|-----------------------|-----------------------------------------------------|
| `KEYSTORE_BASE64`     | o conteúdo do `keystore_base64.txt` (Passo 3)       |
| `KEYSTORE_PASSWORD`   | a senha da keystore que você definiu no Passo 2     |
| `KEY_ALIAS`           | `sims4translator` (ou o alias que você usou)        |
| `KEY_PASSWORD`        | a senha da chave (Passo 2)                          |

## Passo 6 — Rodar o build

Se você já tiver feito o `git push`, o workflow já deve ter disparado
sozinho (aba **Actions** do repositório). Se não, clique em **Actions →
Build APK → Run workflow**.

Espera uns 2-3 minutos. Quando terminar (bolinha verde ✅), entra no
resultado do workflow e baixa o artefato **`sims4translator-apk`** — é um
zip contendo o `app-release.apk` já assinado.

Transfere esse APK pro seu celular (ou já roda direto se o Termux for no
mesmo aparelho) e instala normalmente.

## Passo 7 — Configurar o assetlinks.json no seu site

Pra o Android confiar de verdade no app e esconder a barra de endereço do
navegador, edite `assetlinks.json` (raiz desse projeto) trocando o
placeholder pelo SHA-256 da sua chave:

```bash
keytool -list -v -keystore sims4translator.keystore -alias sims4translator
```
Copie o valor de `SHA256:`.

Depois, esse arquivo `assetlinks.json` precisa ficar acessível publicamente
em:
```
https://sims4androidtranslator.vercel.app/.well-known/assetlinks.json
```
Como seu site já roda no Vercel/Flask, adiciona uma rota simples no
`app.py` que sirva esse JSON nesse caminho (ou coloca o arquivo estático
em `.well-known/` se o Vercel servir estáticos direto). Sem isso o app
ainda funciona, só que mostra a barrinha do Chrome no topo.

## Resumo do que muda a cada atualização do app
- Editou algo no site (Flask/templates)? Não precisa rebuildar o app —
  ele só abre a URL, sempre pega a versão mais nova automaticamente.
- Só precisa rebuildar o APK se mudar ícone, nome do app, cor do tema,
  ou splash screen (essas coisas ficam no APK, não no site).
