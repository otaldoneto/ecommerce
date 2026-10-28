import { describe, expect, it } from "vitest";
import { formatPrice } from "@/lib/format";

describe("formatPrice", () => {
    it("formats as Brazilian reais", () => {
        expect(formatPrice(59.9)).toBe("R$\u00a059,90");
        expect(formatPrice(1234.5)).toBe("R$\u00a01.234,50");
    });
});
