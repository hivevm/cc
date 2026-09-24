# ADR-0029: One character model across the targets: code points, columns and escapes

- **Status:** 🟡 Proposed <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-09-24
- **Deciders:** Markus Brigl
- **Supersedes:** —
- **Superseded by:** —

## Context

The [specification](../SPECIFICATION.md) lists *full-Unicode lexing* among the features users expect
(§2, goal 5). It accepts that a capability may reach one back end before the others, as long as the
gap is tracked (§3). What a *character* is, however, is not a capability of one back end: it decides
which tokens a grammar matches and where they are reported. The three targets answer it differently
today, and nothing records that. This was found in the review of 2026-09-24.

**What the NFA and the string-literal DFA consume.**

- **The model.** Character lists and literals are Java `char`s (`SingleCharacter`, `CharacterRange`,
  `RStringLiteral`). They are UTF-16 code units, so a character outside the Basic Multilingual Plane
  is stored as two surrogates.
- **Java.** The generated lexer reads UTF-16 code units (`JavaCharStream`), the unit the model uses.
- **C++.** The reader decodes UTF-8 into code points. Since b8e110b1 this also holds for the first
  character of a token and for the string-literal DFA.
- **Rust.** The reader yields Rust `char`s, which are code points.

As a consequence, a literal such as `"\uD83D\uDE00"` (😀) is two surrogates in the model
but one code point in C++ and Rust, so it can never match there. This was confirmed for C++ on
2026-09-24: `ab 😀 cd` lexed `ab`, then a lexical error, then `cd`. The same reasoning applies
to Rust and to character ranges that cross U+FFFF, but that was not run: there is no Rust
toolchain in the dev container.

**How columns are counted.**

| Target | Unit of a column | Tab |
| --- | --- | --- |
| Java | UTF-16 code unit | 1 (`JavaCharStream.tabSize = 1`) |
| C++ | byte of UTF-8 (`updateLineColumn` runs in `readChar`) | 8 (`StringReader`) |
| Rust | code point | 1 |

Lines are 1-based everywhere. The same token therefore gets three different columns as soon as a
line holds a tab or a character beyond ASCII.

**How `\uXXXX` in the input is treated.** Java always decodes Java-style Unicode escapes before
lexing (`JavaCharStream`); there is no option to turn it off. The Rust template has the decoding
commented out but still consumes the backslash: `\u0041` reaches the lexer as `u`, `0`, `0`, `4`,
`1`, while the backslash stays in the token image. C++ passes the escape through unchanged.
Waggle has no option for this. JavaCC's `JAVA_UNICODE_ESCAPE` defaults to `false`, and the JavaCC
Java 1.1 grammar kept in this repository (`JavaGrammars/Java1.1.jj`) sets it to `true`, which is the
use case: lexing Java source, where the language defines these escapes.

**State of the art.**

- **ANTLR 4.7** moved the lexers of all its runtimes from UTF-16 code units to code points up to
  U+10FFFF. It deprecated the Java streams that "only support Unicode code points up to U+FFFF"
  ([ANTLR: Unicode](https://github.com/antlr/antlr4/blob/master/doc/unicode.md)).
- **The Language Server Protocol 3.17** counts a position's character offset in a negotiated unit.
  UTF-16 is the mandatory default, and UTF-32 "may also be used for an encoding-agnostic
  representation of character offsets", since UTF-32 units are code points
  ([LSP 3.17, PositionEncodingKind](https://microsoft.github.io/language-server-protocol/specifications/lsp/3.17/specification/)).
  The protocol says nothing about tabs: a tab is one unit.
- **JavaCC** has `JAVA_UNICODE_ESCAPE` default to `false`. Its current `SimpleCharStream` template
  starts with `tabSize = 1`
  ([template](https://raw.githubusercontent.com/javacc/javacc/master/src/main/resources/templates/SimpleCharStream.template));
  older generated streams used 8, which is where the C++ value comes from.

## Decision

We will give all three targets one character model.

1. **The unit of lexing is the Unicode code point, U+0000 to U+10FFFF.** A character list or
   literal in a grammar denotes code points. A surrogate pair written in a literal (`"\uD83D\uDE00"`)
   is the one code point it encodes. The model and the automaton (stages 2 to 4) carry code points,
   not UTF-16 code units. Each generated lexer hands its automaton code points: the Java runtime
   reads a surrogate pair as one code point, C++ decodes UTF-8 (as it does now), and Rust reads
   `char`s (as it does now). A lone surrogate in the input is a lexical error in every target.
2. **Positions count code points.** Lines and columns are 1-based. A column advances by one per code
   point, and a tab counts as one column in every target. The runtimes keep the tab-size setter they
   have, so a caller can still ask for another width, but the default is 1 everywhere. C++ changes
   from 8, and from counting bytes to counting code points. This is the LSP's UTF-32 unit, which a
   tool can convert to UTF-16 when it needs to.
3. **`\uXXXX` in the input is text unless the grammar asks otherwise.** A new option,
   `JAVA_UNICODE_ESCAPE` (default `false`), turns on Java-style escape decoding before lexing, with
   JavaCC's meaning. The Java target implements it with the decoding it has today, which then runs
   only when the option is set. Until C++ and Rust implement it, they reject a grammar that sets it
   with a `GenerationException`, and the README lists the gap (§3).

## Consequences

- **The same grammar matches the same input in every target**, including characters beyond the
  BMP, and reports a token at the same line and column. Full-Unicode lexing (§2) becomes true for
  the targets that read code points already, and for Java.
- **Stage 2 to 4 change their alphabet from `char` to `int`.** `SingleCharacter`, `CharacterRange`,
  the case folding tables of `RCharacterList`, the NFA move tables and the string-literal DFA's
  case labels are written for 16-bit characters. The per-state bit vectors above 255 (`jjbitVec`,
  `jjCanMove`) are indexed by the high byte of a `char` and must grow to cover the higher planes.
  This is the largest part of the work, and the generated lexers change for every grammar that uses
  a character beyond U+FFFF.
- **Generated Java output changes.** The lexer's `curChar` becomes a code point. Java also stops
  decoding `\uXXXX` by default. That is a visible change for a grammar that relied on it: such a
  grammar has to set `JAVA_UNICODE_ESCAPE: true`. It belongs in the release notes.
- **C++ columns change for every line with a tab or a character beyond ASCII.** Callers that
  compensated for byte columns must stop.
- **Rust keeps its model.** Its runtime still needs the repairs from the review (end of input, the
  4096-character buffer, `get_image`). Those are bugs, not decisions, and are outside this ADR.
- **Tests.** `GeneratedLexerTest` (Java) and the C++ runs in `CppCompilesTest` get one input with a
  character beyond the BMP, a tab and an escape, and compare kinds, images, lines and columns
  across both targets. Rust joins when a toolchain is available.

## Alternatives considered

**Keep UTF-16 code units everywhere.** This is Java's native unit and what the model uses today, so
Java would not change. C++ and Rust would have to transcode their input to UTF-16 and match
surrogates. A character range that crosses U+FFFF would then have to be split into surrogate
sequences, which is what ANTLR moved away from. Columns would be in a unit neither C++ nor Rust uses
natively.

**Count bytes of UTF-8.** This is C++'s native unit and LSP's UTF-8 kind. The automaton would have to
match multi-byte sequences, so a character list becomes a small byte automaton. Java would have to
encode its input. The complexity lands in stage 4, and the positions are meaningless for any tool
that works in characters.

**Leave each target its own model and document the differences.** This is what §3 would tolerate
for a missing feature. Here, though, it means one grammar accepts different languages depending on
the target, which is not a gap but a divergence, and the specification's full-Unicode goal stays
false for C++ and Rust.

**Drop `\uXXXX` decoding entirely.** This is simpler, but it removes the one use case JavaCC had
the option for: lexing Java source. That grammar would have to encode the escape in its token
definitions, which JavaCC grammars for Java never did.
