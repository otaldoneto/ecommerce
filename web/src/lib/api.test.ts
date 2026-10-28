import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { ApiError, apiFetch } from "@/lib/api";

vi.mock("server-only", () => ({}));

describe("apiFetch", () => {
    beforeEach(() => {
        vi.stubEnv("API_URL", "http://api.test/");
    });

    afterEach(() => {
        vi.unstubAllEnvs();
        vi.unstubAllGlobals();
    });

    it("returns the parsed JSON body", async () => {
        const fetchMock = vi.fn().mockResolvedValue(Response.json({ name: "Camiseta" }));
        vi.stubGlobal("fetch", fetchMock);

        await expect(apiFetch("/products/camiseta")).resolves.toEqual({ name: "Camiseta" });
        expect(fetchMock.mock.calls[0][0]).toBe("http://api.test/products/camiseta");
    });

    it("throws ApiError with the problem detail", async () => {
        const problem = { title: "Not Found", status: 404, detail: "Product not found" };
        vi.stubGlobal("fetch", vi.fn().mockResolvedValue(Response.json(problem, { status: 404 })));

        const error = await apiFetch("/products/nope").catch((e: unknown) => e);

        expect(error).toBeInstanceOf(ApiError);
        expect(error).toMatchObject({ status: 404, message: "Product not found" });
    });

    it("fails fast when API_URL is missing", async () => {
        vi.stubEnv("API_URL", "");

        await expect(apiFetch("/products")).rejects.toThrow("API_URL is not set");
    });
});
