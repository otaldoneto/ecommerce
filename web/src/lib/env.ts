import "server-only";

export function apiUrl(): string {
    const value = process.env.API_URL;
    if (!value) {
        throw new Error("API_URL is not set. Copy web/.env.example to web/.env.local.");
    }
    return value.replace(/\/+$/, "");
}
