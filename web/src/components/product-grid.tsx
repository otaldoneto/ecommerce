import { connection } from "next/server";
import { Badge } from "@/components/ui/badge";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { listProducts } from "@/lib/catalog";
import { formatPrice } from "@/lib/format";

export async function ProductGrid() {
    await connection();
    const { content: products } = await listProducts();

    if (products.length === 0) {
        return <p className="text-muted-foreground">Nenhum produto disponível.</p>;
    }

    return (
        <ul className="grid gap-4 sm:grid-cols-2 lg:grid-cols-3">
            {products.map((product) => (
                <li key={product.id}>
                    <Card>
                        <CardHeader>
                            <Badge variant="secondary">{product.category.name}</Badge>
                            <CardTitle>{product.name}</CardTitle>
                        </CardHeader>
                        <CardContent className="text-lg font-semibold">{formatPrice(product.price)}</CardContent>
                    </Card>
                </li>
            ))}
        </ul>
    );
}
