import { Suspense } from "react";
import { ProductGrid } from "@/components/product-grid";

export default function Home() {
  return (
      <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
        <h1 className="mb-6 text-2xl font-semibold">Produtos</h1>
        <Suspense fallback={<p className="text-muted-foreground">Carregando produtos…</p>}>
          <ProductGrid />
        </Suspense>
      </main>
  );
}
