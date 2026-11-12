// The site is retired and login is disabled. Any login attempt is redirected
// to the new site; no auth cookie is ever set.
export async function onRequest(): Promise<Response> {
    return new Response('', {
        status: 302,
        headers: {
            'Cache-Control': 'no-store',
            Location: 'https://bglehi.adobe.com/',
        },
    });
}
