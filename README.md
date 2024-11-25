![Status](https://img.shields.io/github/actions/workflow/status/hivevm/cc/main.yml?label=Build)
[![License](https://img.shields.io/badge/License-BSD%203%20Clause-green.svg)](https://opensource.org/license/bsd-3-clause)

# HiveVM - Parser Generator

HiveVM Compiler-Compiler (HiveVM CC) is a fork of JavaCC 7.0.13.
The overall goal is to maintain *mostly* compatibility to JavaCC, but:
* The code itself should be better maintainable
* Organize 

A parser generator is a tool that reads a grammar specification and converts it to a Java program that can recognize matches to the grammar.

In addition to the parser generator itself, HiveVM CC provides other standard capabilities related to parser generation such as tree building (via a tool called JJTree included with HiveVM CC), actions and debugging.

All you need to run a HiveVM CC parser, once generated, is a Java Runtime Environment (JRE).


## Configure Settings

The plugin implements the HiveVM CC generated.

~~~
plugins {
  id "org.hivevm.cc" version "1.0.0"
}

parserProject {
  target       = 'java'

  task {
    name       = 'tree'
    output     = 'src/main/generated'
    jjtFile    = 'src/main/resources/JJTree.jjt'
    excludes   = [ 'BNF', 'BNFAction', 'BNFDeclaration', 'BNFNodeScope',
      'ExpansionNodeScope', 'NodeDescriptor', 'OptionBinding' ]
  }

  task {
    name       = 'parser'
    output     = 'src/main/generated'
    jjFile     = 'src/main/resources/JavaCC.jj'
  }
}
~~~


## Features

* HiveVM CC generates top-down ([recursive descent](https://en.wikipedia.org/wiki/Recursive_descent_parser)) parsers as opposed to bottom-up parsers generated by [YACC](https://en.wikipedia.org/wiki/Yacc)-like tools. This allows the use of more general grammars, although [left-recursion](https://en.wikipedia.org/wiki/Left_recursion) is disallowed. Top-down parsers have a number of other advantages (besides more general grammars) such as being easier to debug, having the ability to parse to any [non-terminal](https://en.wikipedia.org/wiki/Terminal_and_nonterminal_symbols) in the grammar, and also having the ability to pass values (attributes) both up and down the parse tree during parsing.

* By default, HiveVM CC generates an `LL(1)` parser. However, there may be portions of grammar that are not `LL(1)`. HiveVM CC offers the capabilities of syntactic and semantic lookahead to resolve shift-shift ambiguities locally at these points. For example, the parser is `LL(k)` only at such points, but remains `LL(1)` everywhere else for better performance. Shift-reduce and reduce-reduce conflicts are not an issue for top-down parsers.

* HiveVM CC generates parsers that are 100% pure Java, so there is no runtime dependency on HiveVM CC and no special porting effort required to run on different machine platforms.

* HiveVM CC allows [extended BNF](https://en.wikipedia.org/wiki/Extended_Backus%E2%80%93Naur_form) specifications - such as `(A)*`, `(A)+` etc - within the lexical and the grammar specifications. Extended BNF relieves the need for left-recursion to some extent. In fact, extended BNF is often easier to read as in `A ::= y(x)*` versus `A ::= Ax|y`.

* The lexical specifications (such as regular expressions, strings) and the grammar specifications (the BNF) are both written together in the same file. It makes grammars easier to read since it is possible to use regular expressions inline in the grammar specification, and also easier to maintain.

* The [lexical analyzer](https://en.wikipedia.org/wiki/Lexical_analysis) of HiveVM CC can handle full Unicode input, and lexical specifications may also include any Unicode character. This facilitates descriptions of language elements such as Java identifiers that allow certain Unicode characters (that are not ASCII), but not others.

* HiveVM CC offers [Lex](https://en.wikipedia.org/wiki/Lex_(software))-like lexical state and lexical action capabilities. Specific aspects in HiveVM CC that are superior to other tools are the first class status it offers concepts such as `TOKEN`, `MORE`, `SKIP` and state changes. This allows cleaner specifications as well as better error and warning messages from HiveVM CC.

* Tokens that are defined as *special tokens* in the lexical specification are ignored during parsing, but these tokens are available for processing by the tools. A useful application of this is in the processing of comments.

* Lexical specifications can define tokens not to be case-sensitive either at the global level for the entire lexical specification, or on an individual lexical specification basis.

* HiveVM CC comes with JJTree, an extremely powerful tree building pre-processor.

* HiveVM CC also includes JJDoc, a tool that converts grammar files to documentation files, optionally in HTML.

* HiveVM CC offers many options to customize its behavior and the behavior of the generated parsers. Examples of such options are the kinds of Unicode processing to perform on the input stream, the number of tokens of ambiguity checking to perform etc.

* HiveVM CC error reporting is among the best in parser generators. HiveVM CC generated parsers are able to clearly point out the location of parse errors with complete diagnostic information.

* Using options `DEBUG_PARSER`, `DEBUG_LOOKAHEAD`, and `DEBUG_TOKEN_MANAGER`, users can get in-depth analysis of the parsing and the token processing steps.

* The HiveVM CC release includes a wide range of examples including Java and HTML grammars. The examples, along with their documentation, are a great way to get acquainted with HiveVM CC.


## Example

This example recognizes matching braces followed by zero or more line terminators and then an end of file.

Examples of legal strings in this grammar are:

`{}`, `{{{{{}}}}}` // ... etc

Examples of illegal strings are:

`{}{}`, `}{}}`, `{ }`, `{x}` // ... etc

### Grammar
```java
PARSER_BEGIN(Example)

/** Simple brace matcher. */
public class Example {

  /** Main entry point. */
  public static void main(String args[]) throws ParseException {
    Example parser = new Example(System.in);
    parser.Input();
  }

}

PARSER_END(Example)

/** Root production. */
void Input() :
{}
{
  MatchedBraces() ("\n"|"\r")* <EOF>
}

/** Brace matching production. */
void MatchedBraces() :
{}
{
  "{" [ MatchedBraces() ] "}"
}
```

### Output
```java
$ java Example
{{}}<return>
```

```java
$ java Example
{x<return>
Lexical error at line 1, column 2.  Encountered: "x"
TokenMgrError: Lexical error at line 1, column 2.  Encountered: "x" (120), after : ""
        at ExampleTokenManager.getNextToken(ExampleTokenManager.java:146)
        at Example.getToken(Example.java:140)
        at Example.MatchedBraces(Example.java:51)
        at Example.Input(Example.java:10)
        at Example.main(Example.java:6)
```

```java
$ java Example
{}}<return>
ParseException: Encountered "}" at line 1, column 3.
Was expecting one of:
    <EOF>
    "\n" ...
    "\r" ...
        at Example.generateParseException(Example.java:184)
        at Example.jj_consume_token(Example.java:126)
        at Example.Input(Example.java:32)
        at Example.main(Example.java:6)
```

## License

HiveVM CC is an open source project released under the [BSD 3-Clause License](LICENSE). The JavaCC project was originally developed at Sun Microsystems Inc. by [Sreeni Viswanadha](https://github.com/kaikalur) and [Sriram Sankar](https://twitter.com/sankarsearch).

