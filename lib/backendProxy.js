module.exports = async function proxyToBackend(request, response) {
    const backendUrl = process.env.BACKEND_URL;

    if (!backendUrl) {
        response.status(500).json({
            error: "BACKEND_URL is not configured for this Vercel deployment."
        });
        return;
    }

    const upstreamUrl = new URL(request.url, backendUrl.endsWith("/") ? backendUrl : `${backendUrl}/`);

    try {
        const upstream = await fetch(upstreamUrl, {
            headers: { accept: "application/json" }
        });
        const body = await upstream.text();
        response.status(upstream.status);
        response.setHeader("content-type", upstream.headers.get("content-type") || "application/json; charset=utf-8");
        response.send(body);
    } catch (error) {
        console.error("Unable to reach the credit bureau backend", error);
        response.status(502).json({
            error: "The scoring API is temporarily unavailable."
        });
    }
};
