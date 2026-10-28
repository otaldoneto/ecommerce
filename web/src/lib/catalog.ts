import "server-only";
import { apiFetch } from "@/lib/api";
import type { Page, Product } from "@/lib/types";

export function listProducts(): Promise<Page<Product>> {
    return apiFetch<Page<Product>>("/products");
}
