export type Category = {
    id: string;
    name: string;
    slug: string;
};

export type Product = {
    id: string;
    name: string;
    slug: string;
    description: string;
    price: number;
    stock: number;
    active: boolean;
    category: Category;
};

export type Page<T> = {
    content: T[];
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
};
