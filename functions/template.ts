const NEW_SITE_URL = 'https://bglehi.adobe.com/';
const REDIRECT_SECONDS = 5;

export function getTemplate(): string {
    return `<!doctype html>
<html lang="en" data-theme="dark">
  <head>
    <meta charset="utf-8">
    <meta name="viewport" content="width=device-width, initial-scale=1">
    <meta http-equiv="refresh" content="${REDIRECT_SECONDS};url=${NEW_SITE_URL}">
    <meta name="robots" content="noindex">
    <title>The game checkout site has moved</title>
    <meta name="description" content="The game checkout site has moved to ${NEW_SITE_URL}">
    <style>
      :root { color-scheme: dark; }
      body {
        margin: 0;
        min-height: 100vh;
        display: flex;
        align-items: center;
        justify-content: center;
        background: #11191f;
        color: #e8eef2;
        font-family: system-ui, -apple-system, Segoe UI, Roboto, Helvetica, Arial, sans-serif;
        text-align: center;
        padding: 1.5rem;
        box-sizing: border-box;
      }
      main { max-width: 34rem; }
      h1 { font-size: 1.7rem; margin: 0 0 1rem; line-height: 1.25; }
      p { font-size: 1.05rem; line-height: 1.6; margin: 0.5rem 0; color: #b9c6cf; }
      a { color: #7cc4ff; }
      .cta {
        display: inline-block;
        margin-top: 1.5rem;
        padding: 0.65rem 1.25rem;
        border-radius: 8px;
        background: #2b6cb0;
        color: #fff;
        text-decoration: none;
        font-weight: 600;
      }
    </style>
  </head>
  <body>
    <main>
      <h1>This game checkout site has moved</h1>
      <p>Checkout now lives at <a href="${NEW_SITE_URL}">${NEW_SITE_URL}</a>.</p>
      <p>You'll be redirected automatically in ${REDIRECT_SECONDS} seconds.</p>
      <a class="cta" href="${NEW_SITE_URL}">Go there now</a>
    </main>
    <script>
      setTimeout(function () {
        window.location.replace('${NEW_SITE_URL}');
      }, ${REDIRECT_SECONDS * 1000});
    </script>
  </body>
</html>`;
}
