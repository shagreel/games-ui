import { getTemplate } from './template';

// The site is retired. Every request is served the "moved" notice, which
// redirects to the new location. No authentication path remains.
export async function onRequest(): Promise<Response> {
    return new Response(getTemplate(), {
        status: 200,
        headers: {
            'Content-type': 'text/html; charset=utf-8',
            'Cache-Control': 'no-store',
        },
    });
}
