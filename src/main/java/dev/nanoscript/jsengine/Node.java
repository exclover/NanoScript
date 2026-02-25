package dev.nanoscript.jsengine;

import java.util.List;

/**
 * Tüm AST (Abstract Syntax Tree) düğüm tipleri burada tanımlıdır.
 * Java sealed interface + records ile tip güvenli AST.
 */
public sealed interface Node permits
    Node.Program,
    // Statements
    Node.Block, Node.ExprStmt, Node.VarDecl, Node.FuncDecl,
    Node.IfStmt, Node.WhileStmt, Node.DoWhileStmt, Node.ForStmt,
    Node.ForInStmt, Node.ReturnStmt, Node.BreakStmt, Node.ContinueStmt,
    Node.ThrowStmt, Node.TryStmt, Node.SwitchStmt,
    // Expressions
    Node.Assign, Node.Binary, Node.Logical, Node.Unary, Node.Update,
    Node.Ternary, Node.Call, Node.Member, Node.Index, Node.New_,
    Node.Ident, Node.Lit, Node.ArrayLit, Node.ObjLit,
    Node.FuncExpr, Node.ArrowFunc, Node.Spread, Node.Template
{

    // ──────────────────────────────────────────────────────────────────
    //  Top-level
    // ──────────────────────────────────────────────────────────────────

    record Program(List<Node> body)         implements Node {}
    record Block(List<Node> body)            implements Node {}
    record ExprStmt(Node expr)              implements Node {}

    // ──────────────────────────────────────────────────────────────────
    //  Declarations
    // ──────────────────────────────────────────────────────────────────

    /** var/let/const x = expr, y = expr */
    record VarDecl(String kind, List<Declarator> decls) implements Node {
        record Declarator(String name, Node init) {}
    }

    /** function foo(a, b) { ... } */
    record FuncDecl(String name, List<String> params, Node body) implements Node {}

    // ──────────────────────────────────────────────────────────────────
    //  Control flow statements
    // ──────────────────────────────────────────────────────────────────

    record IfStmt(Node test, Node then, Node else_)          implements Node {}
    record WhileStmt(Node test, Node body)                   implements Node {}
    record DoWhileStmt(Node body, Node test)                 implements Node {}
    record ForStmt(Node init, Node test, Node update, Node body) implements Node {}
    record ForInStmt(String kind, String var, Node obj, Node body, boolean isOf) implements Node {}
    record ReturnStmt(Node value)                            implements Node {}
    record BreakStmt(String label)                           implements Node {}
    record ContinueStmt(String label)                        implements Node {}
    record ThrowStmt(Node value)                             implements Node {}
    record TryStmt(Node body, String catchVar, Node catchBody, Node finallyBody) implements Node {}

    record SwitchStmt(Node disc, List<SwitchCase> cases)     implements Node {
        record SwitchCase(Node test, List<Node> body) {} // test==null → default
    }

    // ──────────────────────────────────────────────────────────────────
    //  Expressions
    // ──────────────────────────────────────────────────────────────────

    /** x = expr  /  x += expr  /  x -= expr  etc. */
    record Assign(Node target, String op, Node value)        implements Node {}

    /** a + b  /  a * b  /  a === b  etc. */
    record Binary(String op, Node left, Node right)          implements Node {}

    /** a && b  /  a || b  /  a ?? b */
    record Logical(String op, Node left, Node right)         implements Node {}

    /** !x  /  -x  /  typeof x  /  void x  /  ~x */
    record Unary(String op, Node operand, boolean prefix)    implements Node {}

    /** x++  /  ++x  /  x--  /  --x */
    record Update(String op, Node operand, boolean prefix)   implements Node {}

    /** test ? a : b */
    record Ternary(Node test, Node then, Node else_)         implements Node {}

    /** foo(a, b) */
    record Call(Node callee, List<Node> args)                implements Node {}

    /** obj.prop */
    record Member(Node obj, String prop)                     implements Node {}

    /** obj[expr] */
    record Index(Node obj, Node key)                         implements Node {}

    /** new Foo(args) */
    record New_(Node callee, List<Node> args)                implements Node {}

    /** foo */
    record Ident(String name)                                implements Node {}

    /** 42  /  "hello"  /  true  /  null */
    record Lit(Object value)                                 implements Node {} // Double, String, Boolean, or null

    /** [1, 2, 3] */
    record ArrayLit(List<Node> elements)                     implements Node {}

    /** { key: value, ... } */
    record ObjLit(List<ObjProp> props)                       implements Node {
        record ObjProp(String key, Node value, boolean computed) {}
    }

    /** function(a, b) { ... }  (anonymous or named) */
    record FuncExpr(String name, List<String> params, Node body) implements Node {}

    /** (a, b) => expr  or  (a, b) => { ... } */
    record ArrowFunc(List<String> params, Node body)         implements Node {}

    /** ...expr  (spread in call/array) */
    record Spread(Node expr)                                 implements Node {}

    /** `hello ${name}!`  — parts alternates between string literals and expressions */
    record Template(List<Node> parts)                        implements Node {}
}
