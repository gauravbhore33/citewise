import numpy as np

from app.embedding import embed_passages, embed_query

sentences = [
    "An apple is a sweet fruit that grows on trees.",
    "A cake is a sweet baked dessert.",
    "Potato chips are salty and crunchy snacks.",
    "A class is a blueprint for creating objects in object-oriented programming.",
    "A primary key uniquely identifies each row in a database table.",
]

queries = [
    "Which food is sweet?",
    "How do I identify a row in a table?",
    "What is a blueprint for objects?",
    "What is the capital of France?",
]

vectors = np.array(embed_passages(sentences))
print("shape of the vectors:", vectors.shape)   # (5, 384): five texts, 384 numbers each

for query in queries:
    q = np.array(embed_query(query))
    similarity = vectors @ q / (np.linalg.norm(vectors, axis=1) * np.linalg.norm(q))
    print("\nQuery:", query)
    for sentence, score in sorted(zip(sentences, similarity), key=lambda pair: -pair[1]):
        print(f"  {score:.3f}  {sentence}")