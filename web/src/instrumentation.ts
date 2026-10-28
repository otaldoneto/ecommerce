export async function register() {
    if (process.env.NEXT_RUNTIME === "nodejs") {
        const { apiUrl } = await import("@/lib/env");
        apiUrl();
    }
}
