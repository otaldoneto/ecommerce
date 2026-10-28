"use client";

import { Button } from "@/components/ui/button";

export default function Error({ retry }: { error: unknown; retry: () => void }) {
    return (
        <main className="mx-auto w-full max-w-6xl flex-1 px-4 py-8">
            <h1 className="mb-2 text-2xl font-semibold">Algo deu errado</h1>
            <p className="mb-6 text-muted-foreground">Não foi possível carregar esta página.</p>
            <Button onClick={() => retry()}>Tentar de novo</Button>
        </main>
    );
}
