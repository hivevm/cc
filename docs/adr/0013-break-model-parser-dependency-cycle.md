# ADR-0013: Break the `model` ↔ `parser` dependency cycle

- **Status:** 🟢 Accepted <!-- 🟡 Proposed | 🟢 Accepted | 🔴 Rejected | ⚪ Superseded by ADR-XXXX -->
- **Date:** 2026-07-18
- **Deciders:** Markus Brigl
- **Supersedes:** —
- **Superseded by:** —

## Context

The [specification](../SPECIFICATION.md) §4 stages the pipeline so that **stages 1–4 are a
language-independent front end** and stage 5 is the target-specific back end.
[ADR-0004](0004-multi-target-code-generation.md) reinforces this: the back ends render a **shared,
language-independent model**. The model (`org.hivevm.cc.model`) is therefore meant to be the stable,
foundational artifact of the front end — depended upon, depending on little.

The implementation contradicts this: `model` and `parser` form a **package cycle**.

- `org.hivevm.cc.parser` → `org.hivevm.cc.model`: **11** import edges. This is the natural direction
  — stage 2 parses the grammar and *builds* the model.
- `org.hivevm.cc.model` → `org.hivevm.cc.parser`: **11** import edges across **10** model classes.
  This is the edge that should not exist. It breaks down into five distinct couplings:

  | Coupled `parser` type | Model files | What the model actually needs |
  | --- | --- | --- |
  | `Token` | 7 (`Production`, `Action`, `Lookahead`, `NonTerminal`, `RExpression`, `TokenProduction`, transitively `RegExprSpec`) | The **full token contract via public fields**. Two uses: (a) location markers (`beginLine`/`beginColumn`) for error reporting, and (b) **verbatim token chains** — `List<Token>` for embedded actions, return types, parameter lists and throws clauses that the back ends re-emit into generated source by walking `image`, `next` and `specialToken` (37 `.next`, 25 `.image`, 14 `.specialToken` reads across the generators). A position-only value type does **not** cover use (b). |
  | `RegExprSpec` | `TokenProduction` | Nothing external — `RegExprSpec` is *itself* a model concept ("entries in the `respecs` vector of `TokenProduction`") that merely lives in the wrong package; it holds `RExpression`/`Action` (model) references. |
  | `ParserDescriptor` | `NodeScope` | `ParserDescriptor` *implements* the model interface `NodeDescriptor`; the model should not import its own implementor. |
  | `Options` | `NodeDescriptor` | A narrow config view. `Options` already `extends org.hivevm.core.Environment`; `NodeDescriptor.getNodeClass` needs only an option lookup. |
  | `JavaCCErrors` | `CharacterRange` | To report one validation error (invalid character range). `JavaCCErrors` is the process-global static error sink. |

Two facts sharpen the picture:

1. **`Token` is boilerplate, materialised into the generated tree.** It is not grammar-derived: it
   is copied verbatim (with package substitution) from the static template resource
   `src/main/resources/templates/java/Token.java` into every generated parser — including HiveVM CC's
   own self-hosted parser at `src/main/generated/org/hivevm/cc/parser/Token.java`
   ([ADR-0009](0009-self-hosting-bootstrap.md)). So the supposedly-foundational model depends on a
   *generated* artifact of a higher stage — an inversion of the intended layering — even though the
   class itself is fixed, hand-written boilerplate.
2. **A neutral base layer already exists.** `org.hivevm.core` holds `Environment` and `Version` and
   is depended upon by `parser` but **not yet** by `model`. It is the natural home for shared
   abstractions.

Consequences of the cycle today: the model cannot be compiled, unit-tested, or reused without
dragging in the parser layer and its static error sink; the front-end/back-end boundary the
specification mandates cannot be enforced by a package rule; and the "enforceable layering" that
[ADR-0012](0012-lexer-owns-dfa-construction.md) just made possible on the lexer/generator edge is
still blocked on this one.

**State of the art (researched).** This is the textbook violation of Robert C. Martin's **Acyclic
Dependencies Principle** — a component dependency graph must be a DAG. The three established
techniques for breaking such a cycle are (1) **dependency inversion** (flip one edge behind an
abstraction owned by the depended-upon side), (2) **merge** the two components, and (3) **extract a
shared abstraction** into a third, lower, stable component. SEI CERT `DCL60-J` codifies the same rule
for Java packages. The resolution below applies technique (3) for the load-bearing `Token` coupling,
technique (1) for `JavaCCErrors`, and simple **relocation to the correct layer** for the concepts
that are merely misplaced.

## Decision

We will make **`org.hivevm.cc.model` acyclic and downward-only**: it may depend on
`org.hivevm.core` and the JDK, and **must not import `org.hivevm.cc.parser`** (nor any later stage).
The reverse edge (`parser` → `model`) stays; stage 2 continues to build the model. Concretely:

1. **Token — promote the boilerplate to a shared type in `org.hivevm.core`.** The model needs the
   full token contract (image, kind, `next`, `specialToken`, positions) via public-field access, both
   for location markers and for the verbatim token chains the back ends re-emit; a position-only
   value type is insufficient (see Context). We therefore make `Token` a **hand-written class in
   `org.hivevm.core`** with the same public fields, and have the model and the internal front-end /
   generator consumers reference `org.hivevm.core.Token` instead of `org.hivevm.cc.parser.Token`.
   Field-access sites are unchanged. HiveVM CC's self-hosted parser is generated to use
   `core.Token`, so `src/main/generated/.../parser/Token.java` is no longer emitted for the
   self-parser; the static `templates/java/Token.java` resource is retained unchanged for **users'**
   generated parsers, which remain self-contained (SPEC non-goal: no runtime dependency). This
   removes all 7 model→parser Token edges.

   *(Mechanism corrected before implementation: an earlier draft proposed a position-only value type
   adapted at the stage-2 boundary, leaving the generated `Token` untouched. That does not cover the
   verbatim-chain use, so it was replaced by the shared `core.Token` above. The decision — an
   acyclic, downward-only model — is unchanged.)*

2. **Relocate misplaced model concepts.** Move `RegExprSpec` into `org.hivevm.cc.model` — it is a
   model concept. Move or invert `ParserDescriptor` so the model no longer imports its own
   implementor (`NodeScope` depends on the `NodeDescriptor` abstraction it already owns; the concrete
   `ParserDescriptor` lives on the parser side or is folded into the model as data).

3. **Depend on the stable config abstraction.** `NodeDescriptor` (and any other model use of
   `parser.Options`) references `org.hivevm.core.Environment` (or a narrow config interface declared
   in `core`) rather than `parser.Options`. `Options` already extends `Environment`, so this narrows,
   not widens, the contract.

4. **Invert the error edge.** The model must not import the global static `JavaCCErrors`. The
   `CharacterRange` validation either (a) reports through an error-reporter abstraction supplied by
   the caller, or (b) moves to the semantic stage (§3) where such cross-checks belong. Either way the
   model reports *up through an abstraction*, never down into a concrete sink. (Retiring the
   process-global nature of `JavaCCErrors` itself is broader than this ADR and remains separate.)

5. **Enforce it.** Once the cycle is gone, add an automated package-dependency check (e.g. an
   ArchUnit test) asserting `model` does not depend on `parser`/later stages, so the boundary cannot
   silently regress.

## Consequences

- **The model becomes what the specification says it is:** a language-independent, self-contained
  artifact that can be compiled, unit-tested, and reused in isolation — directly serving SPEC §4 and
  ADR-0004, and unblocking unit tests for the model and semantic layers (a gap noted in review).
- **Enforceable layering.** With `model` acyclic and a package rule in place, the front-end boundary
  is guarded mechanically, completing the direction ADR-0012 set on the lexer/generator edge.
- **The self-hosted `Token` moves to `core`; users' `Token` does not.** Promoting `Token` to
  `org.hivevm.core` changes how the self-hosted parser is generated: `parser/Token.java` is no longer
  emitted into the checked-in generated tree, and generated references point at `core.Token`. The
  safety net is therefore **not** a pure byte-for-byte bootstrap diff for `Token`; it is "the
  regenerated tree differs only in the expected `Token` relocation, everything compiles, and the
  Java/C++/Rust generate-and-compile tests pass." The static `templates/java/Token.java` used for
  users' parsers is untouched, so generated user parsers stay self-contained.
- **Cost and risk.** The change touches ~10 model classes plus every parser site that constructs a
  model node (to pass source-location instead of `Token`). The `Token`→source-location adaptation is
  mechanical but broad. The `JavaCCErrors` inversion is the subtlest part and partially overlaps a
  larger global-state cleanup; it may be landed as its own step. As with ADR-0012, this is best done
  under a safety net — byte-identical bootstrap regeneration plus the Java/C++/Rust
  generate-and-compile tests — and split into reviewable steps (source-location first, relocations
  next, error-inversion last), not one big commit.
- **Follow-up enabled.** A clean `model` is the precondition for later addressing the remaining
  global-state residue (`JavaCCErrors`, `NodeScope` static collections) and for any future extraction
  of the front end into its own module.

## Alternatives considered

- **Merge `model` and `parser` into one package.** Rejected: the two have distinct responsibilities
  and the SPEC's staged pipeline treats them as separate stages; merging would erase a boundary the
  specification wants sharper, not gone, and their cohesion does not justify it (ADP technique 2 fits
  only tightly-cohesive pairs).
- **Position-only value type, `Token` adapted at the stage-2 boundary.** This was the original draft
  of decision 1. Rejected on investigation: the model does not hold `Token` only for positions — it
  holds verbatim `List<Token>` chains that the back ends re-serialise into generated source by
  walking `next`/`specialToken` via public fields. A position value cannot carry that, and a boundary
  adapter cannot wrap public-field access. The shared `core.Token` in decision 1 is the corrected
  mechanism; it is more bootstrap-sensitive but is the smallest change that actually severs all seven
  edges.
- **Leave the cycle and suppress the warning.** Rejected: it violates SPEC §4 and ADR-0004, keeps the
  model un-testable in isolation, and blocks the package-rule enforcement that ADR-0012's layering
  work was meant to make possible.
- **A shared static utility both packages call (for the error sink).** Rejected for the same reason
  ADR-0012 rejected it: it treats the symptom (the import) without inverting the dependency, and
  keeps the model reaching toward a concrete, process-global sink instead of reporting up through an
  abstraction.
