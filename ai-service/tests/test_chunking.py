from app.chunking import chunk_page, chunk_pages, clean_text


def make_text(n_words: int) -> str:
    """n words; every tenth word ends a sentence."""
    words = []
    for i in range(n_words):
        word = f"word{i}"
        if i % 10 == 9:
            word += "."
        words.append(word)
    return " ".join(words)


def test_short_page_is_one_chunk():
    assert len(chunk_page(make_text(50), size=200, overlap=40)) == 1


def test_long_page_is_split_and_chunks_overlap():
    chunks = chunk_page(make_text(500), size=200, overlap=40)
    assert len(chunks) >= 3
    for before, after in zip(chunks, chunks[1:]):
        # the next chunk begins with words that the previous chunk already contained
        assert after.split()[0] in before.split()


def test_no_words_are_lost():
    text = make_text(500)
    chunks = chunk_page(text, size=200, overlap=40)
    assert set(" ".join(chunks).split()) == set(text.split())


def test_chunks_are_not_much_bigger_than_the_size():
    chunks = chunk_page(make_text(500), size=200, overlap=40)
    assert all(len(c.split()) <= 210 for c in chunks)


def test_clean_text_removes_decoration_and_page_numbers():
    raw = "Intro to OOP\n..........\n12\nClasses and objects"
    assert clean_text(raw) == "Intro to OOP Classes and objects"


def test_clean_text_joins_hyphenated_words():
    assert clean_text("informa-\ntion is power") == "information is power"


def test_pages_keep_their_numbers_and_chunks_are_numbered_in_order():
    pages = [{"page_number": 1, "text": make_text(30)},
             {"page_number": 2, "text": make_text(30)}]
    chunks = chunk_pages(pages, size=200, overlap=40)
    assert [c["page_number"] for c in chunks] == [1, 2]
    assert [c["chunk_index"] for c in chunks] == [0, 1]


def test_tiny_pages_are_skipped():
    assert chunk_pages([{"page_number": 1, "text": "Contents"}]) == []