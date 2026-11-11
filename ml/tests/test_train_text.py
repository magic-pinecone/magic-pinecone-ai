"""The <bos> handling of train_lora.py, which needs no GPU libraries to import."""

import train_lora


def test_gemma4_keeps_the_bos_of_the_chat_template():
    family = train_lora.FAMILIES["gemma4"]
    rendered = "<bos><|turn>user\nhi<turn|>\n"
    assert train_lora.training_text(rendered, family["strip_bos"]) == rendered


def test_gemma3_drops_it_because_its_tokenizer_adds_one():
    family = train_lora.FAMILIES["gemma3"]
    rendered = "<bos><start_of_turn>user\nhi<end_of_turn>\n"
    assert (
        train_lora.training_text(rendered, family["strip_bos"])
        == rendered[len("<bos>") :]
    )


def test_every_family_says_what_to_do_with_bos():
    assert all("strip_bos" in family for family in train_lora.FAMILIES.values())
