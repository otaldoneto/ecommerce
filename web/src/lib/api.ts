import "server-only";
import { apiUrl } from "@/lib/env";

export class ApiError extends Error {
    constructor(
        readonly status: number,
        message: string,
    ) {
        super(message);
        this.name = "ApiError";
    }
}

export async function apiFetch<T>(path: string, init?: RequestInit): Promise<T> {
    const headers = new Headers(init?.headers);
    headers.set("Accept", "application/json");
    if (init?.body && !headers.has("Content-Type")) {
        headers.set("Content-Type", "application/json");
    }

    const response = await fetch(`${apiUrl()}${path}`, { ...init, headers });
    if (!response.ok) {
        throw new ApiError(response.status, await errorMessage(response));
    }
    if (response.status === 204) {
        return undefined as T;
    }
    return (await response.json()) as T;
}

async function errorMessage(response: Response): Promise<string> {
    try {
        const problem = (await response.json()) as { detail?: string; title?: string };
        return problem.detail ?? problem.title ?? response.statusText;
    } catch {
        return response.statusText;
    }
}
