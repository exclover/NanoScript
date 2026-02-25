package dev.nanoscript.jsengine;

import java.util.ArrayList;
import java.util.List;

/**
 * Recursive Descent Parser
 * Token listesini alır, AST (Node.Program) üretir.
 *
 * Precedence (düşükten yükseğe):
 *   Assignment  =  +=  -=  ...
 *   Ternary     ? :
 *   LogicalOr   ||  ??
 *   LogicalAnd  &&
 *   BitOr       |
 *   BitXor      ^
 *   BitAnd      &
 *   Equality    ==  !=  ===  !==
 *   Relational  <  >  <=  >=  instanceof  in
 *   Shift       <<  >>  >>>
 *   Additive    +  -
 *   Multiplicative  *  /  %
 *   Exponent    **
 *   Unary       !  -  +  ~  typeof  void  delete
 *   Postfix/Update  ++  --
 *   Call/Member foo()  .prop  [idx]
 *   Primary     literals  identifiers  (expr)  function  arrow  []  {}
 */
public class Parser {

    private final List<Token> tokens;
    private int pos;

    public Parser(List<Token> tokens) {
        this.tokens = tokens;
        this.pos = 0;
    }

    public Node.Program parse() {
        List<Node> body = new ArrayList<>();
        while (!at(TokenType.EOF)) {
            Node stmt = parseStatement();
            if (stmt != null) body.add(stmt);
        }
        return new Node.Program(body);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Statements
    // ──────────────────────────────────────────────────────────────────

    private Node parseStatement() {
        Token t = peek();
        return switch (t.type()) {
            case SEMI       -> { advance(); yield null; }
            case LBRACE     -> parseBlock();
            case VAR, LET, CONST -> parseVarDecl();
            case FUNCTION   -> parseFuncDecl();
            case IF         -> parseIf();
            case WHILE      -> parseWhile();
            case DO         -> parseDoWhile();
            case FOR        -> parseFor();
            case RETURN     -> parseReturn();
            case BREAK      -> { advance(); skipSemi(); yield new Node.BreakStmt(null); }
            case CONTINUE   -> { advance(); skipSemi(); yield new Node.ContinueStmt(null); }
            case THROW      -> parseThrow();
            case TRY        -> parseTry();
            case SWITCH     -> parseSwitch();
            default         -> parseExprStmt();
        };
    }

    private Node.Block parseBlock() {
        consume(TokenType.LBRACE);
        List<Node> body = new ArrayList<>();
        while (!at(TokenType.RBRACE) && !at(TokenType.EOF)) {
            Node s = parseStatement();
            if (s != null) body.add(s);
        }
        consume(TokenType.RBRACE);
        return new Node.Block(body);
    }

    private Node parseVarDecl() {
        String kind = advance().value();
        List<Node.VarDecl.Declarator> decls = new ArrayList<>();
        do {
            String name = consume(TokenType.IDENT).value();
            Node init = null;
            if (match(TokenType.ASSIGN)) init = parseAssignment();
            decls.add(new Node.VarDecl.Declarator(name, init));
        } while (match(TokenType.COMMA));
        skipSemi();
        return new Node.VarDecl(kind, decls);
    }

    private Node parseFuncDecl() {
        consume(TokenType.FUNCTION);
        String name = consume(TokenType.IDENT).value();
        List<String> params = parseParams();
        Node body = parseBlock();
        return new Node.FuncDecl(name, params, body);
    }

    private Node parseIf() {
        consume(TokenType.IF);
        consume(TokenType.LPAREN);
        Node test = parseExpression();
        consume(TokenType.RPAREN);
        Node then = parseStatement();
        Node else_ = null;
        if (match(TokenType.ELSE)) else_ = parseStatement();
        return new Node.IfStmt(test, then, else_);
    }

    private Node parseWhile() {
        consume(TokenType.WHILE);
        consume(TokenType.LPAREN);
        Node test = parseExpression();
        consume(TokenType.RPAREN);
        Node body = parseStatement();
        return new Node.WhileStmt(test, body);
    }

    private Node parseDoWhile() {
        consume(TokenType.DO);
        Node body = parseStatement();
        consume(TokenType.WHILE);
        consume(TokenType.LPAREN);
        Node test = parseExpression();
        consume(TokenType.RPAREN);
        skipSemi();
        return new Node.DoWhileStmt(body, test);
    }

    private Node parseFor() {
        consume(TokenType.FOR);
        consume(TokenType.LPAREN);

        // Detect for..in or for..of
        if ((at(TokenType.VAR) || at(TokenType.LET) || at(TokenType.CONST))) {
            int save = pos;
            String kind = advance().value();
            if (at(TokenType.IDENT)) {
                String varName = advance().value();
                if (at(TokenType.IN) || at(TokenType.OF)) {
                    boolean isOf = at(TokenType.OF);
                    advance();
                    Node obj = parseExpression();
                    consume(TokenType.RPAREN);
                    Node body = parseStatement();
                    return new Node.ForInStmt(kind, varName, obj, body, isOf);
                }
            }
            pos = save; // backtrack
        }

        Node init = null;
        if (!at(TokenType.SEMI)) {
            if (at(TokenType.VAR) || at(TokenType.LET) || at(TokenType.CONST)) {
                init = parseVarDecl();
                pos--; // unread semi consumed by varDecl
            } else {
                init = parseExpression();
            }
        }
        consume(TokenType.SEMI);
        Node test = at(TokenType.SEMI) ? null : parseExpression();
        consume(TokenType.SEMI);
        Node update = at(TokenType.RPAREN) ? null : parseExpression();
        consume(TokenType.RPAREN);
        Node body = parseStatement();
        return new Node.ForStmt(init, test, update, body);
    }

    private Node parseReturn() {
        int line = peek().line();
        consume(TokenType.RETURN);
        Node value = null;
        // No value if next token is on a new line, semi, or }
        if (!at(TokenType.SEMI) && !at(TokenType.RBRACE) && !at(TokenType.EOF)) {
            value = parseExpression();
        }
        skipSemi();
        return new Node.ReturnStmt(value);
    }

    private Node parseThrow() {
        consume(TokenType.THROW);
        Node value = parseExpression();
        skipSemi();
        return new Node.ThrowStmt(value);
    }

    private Node parseTry() {
        consume(TokenType.TRY);
        Node body = parseBlock();
        String catchVar = null;
        Node catchBody = null;
        Node finallyBody = null;
        if (match(TokenType.CATCH)) {
            consume(TokenType.LPAREN);
            catchVar = consume(TokenType.IDENT).value();
            consume(TokenType.RPAREN);
            catchBody = parseBlock();
        }
        if (match(TokenType.FINALLY)) {
            finallyBody = parseBlock();
        }
        return new Node.TryStmt(body, catchVar, catchBody, finallyBody);
    }

    private Node parseSwitch() {
        consume(TokenType.SWITCH);
        consume(TokenType.LPAREN);
        Node disc = parseExpression();
        consume(TokenType.RPAREN);
        consume(TokenType.LBRACE);
        List<Node.SwitchStmt.SwitchCase> cases = new ArrayList<>();
        while (!at(TokenType.RBRACE) && !at(TokenType.EOF)) {
            Node test = null;
            if (match(TokenType.CASE)) {
                test = parseExpression();
                consume(TokenType.COLON);
            } else {
                consume(TokenType.DEFAULT);
                consume(TokenType.COLON);
            }
            List<Node> body = new ArrayList<>();
            while (!at(TokenType.CASE) && !at(TokenType.DEFAULT) && !at(TokenType.RBRACE) && !at(TokenType.EOF)) {
                Node s = parseStatement();
                if (s != null) body.add(s);
            }
            cases.add(new Node.SwitchStmt.SwitchCase(test, body));
        }
        consume(TokenType.RBRACE);
        return new Node.SwitchStmt(disc, cases);
    }

    private Node parseExprStmt() {
        Node expr = parseExpression();
        skipSemi();
        return new Node.ExprStmt(expr);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Expressions (precedence climbing)
    // ──────────────────────────────────────────────────────────────────

    private Node parseExpression() {
        return parseAssignment();
    }

    private Node parseAssignment() {
        Node left = parseTernary();
        TokenType t = peek().type();
        String op = switch (t) {
            case ASSIGN        -> "=";
            case PLUS_ASSIGN   -> "+=";
            case MINUS_ASSIGN  -> "-=";
            case STAR_ASSIGN   -> "*=";
            case SLASH_ASSIGN  -> "/=";
            case PERCENT_ASSIGN -> "%=";
            default            -> null;
        };
        if (op != null) {
            advance();
            Node right = parseAssignment();
            return new Node.Assign(left, op, right);
        }
        return left;
    }

    private Node parseTernary() {
        Node test = parseLogicalOr();
        if (match(TokenType.QUESTION)) {
            Node then = parseAssignment();
            consume(TokenType.COLON);
            Node else_ = parseAssignment();
            return new Node.Ternary(test, then, else_);
        }
        return test;
    }

    private Node parseLogicalOr() {
        Node left = parseLogicalAnd();
        while (at(TokenType.OR) || at(TokenType.NULLISH)) {
            String op = advance().value();
            Node right = parseLogicalAnd();
            left = new Node.Logical(op, left, right);
        }
        return left;
    }

    private Node parseLogicalAnd() {
        Node left = parseBitOr();
        while (at(TokenType.AND)) {
            advance();
            Node right = parseBitOr();
            left = new Node.Logical("&&", left, right);
        }
        return left;
    }

    private Node parseBitOr() {
        Node left = parseBitXor();
        while (at(TokenType.BIT_OR)) {
            advance();
            left = new Node.Binary("|", left, parseBitXor());
        }
        return left;
    }

    private Node parseBitXor() {
        Node left = parseBitAnd();
        while (at(TokenType.BIT_XOR)) {
            advance();
            left = new Node.Binary("^", left, parseBitAnd());
        }
        return left;
    }

    private Node parseBitAnd() {
        Node left = parseEquality();
        while (at(TokenType.BIT_AND)) {
            advance();
            left = new Node.Binary("&", left, parseEquality());
        }
        return left;
    }

    private Node parseEquality() {
        Node left = parseRelational();
        while (true) {
            String op = switch (peek().type()) {
                case EQ         -> "==";
                case NEQ        -> "!=";
                case STRICT_EQ  -> "===";
                case STRICT_NEQ -> "!==";
                default         -> null;
            };
            if (op == null) break;
            advance();
            left = new Node.Binary(op, left, parseRelational());
        }
        return left;
    }

    private Node parseRelational() {
        Node left = parseShift();
        while (true) {
            String op = switch (peek().type()) {
                case LT          -> "<";
                case GT          -> ">";
                case LTE         -> "<=";
                case GTE         -> ">=";
                case INSTANCEOF  -> "instanceof";
                case IN          -> "in";
                default          -> null;
            };
            if (op == null) break;
            advance();
            left = new Node.Binary(op, left, parseShift());
        }
        return left;
    }

    private Node parseShift() {
        Node left = parseAdditive();
        while (at(TokenType.LSHIFT) || at(TokenType.RSHIFT) || at(TokenType.URSHIFT)) {
            String op = advance().value();
            left = new Node.Binary(op, left, parseAdditive());
        }
        return left;
    }

    private Node parseAdditive() {
        Node left = parseMultiplicative();
        while (at(TokenType.PLUS) || at(TokenType.MINUS)) {
            String op = advance().value();
            left = new Node.Binary(op, left, parseMultiplicative());
        }
        return left;
    }

    private Node parseMultiplicative() {
        Node left = parseExponent();
        while (at(TokenType.STAR) || at(TokenType.SLASH) || at(TokenType.PERCENT)) {
            String op = advance().value();
            left = new Node.Binary(op, left, parseExponent());
        }
        return left;
    }

    private Node parseExponent() {
        Node left = parseUnary();
        if (at(TokenType.STARSTAR)) {
            advance();
            Node right = parseExponent(); // right-associative
            return new Node.Binary("**", left, right);
        }
        return left;
    }

    private Node parseUnary() {
        return switch (peek().type()) {
            case NOT      -> { advance(); yield new Node.Unary("!", parseUnary(), true); }
            case MINUS    -> { advance(); yield new Node.Unary("-", parseUnary(), true); }
            case PLUS     -> { advance(); yield new Node.Unary("+", parseUnary(), true); }
            case BIT_NOT  -> { advance(); yield new Node.Unary("~", parseUnary(), true); }
            case TYPEOF   -> { advance(); yield new Node.Unary("typeof", parseUnary(), true); }
            case VOID     -> { advance(); yield new Node.Unary("void", parseUnary(), true); }
            case DELETE   -> { advance(); yield new Node.Unary("delete", parseUnary(), true); }
            case PLUSPLUS  -> { advance(); yield new Node.Update("++", parseUnary(), true); }
            case MINUSMINUS -> { advance(); yield new Node.Update("--", parseUnary(), true); }
            default       -> parsePostfix();
        };
    }

    private Node parsePostfix() {
        Node expr = parseCallMember();
        if (at(TokenType.PLUSPLUS)) { advance(); return new Node.Update("++", expr, false); }
        if (at(TokenType.MINUSMINUS)) { advance(); return new Node.Update("--", expr, false); }
        return expr;
    }

    private Node parseCallMember() {
        Node expr = parsePrimary();
        while (true) {
            if (at(TokenType.LPAREN)) {
                expr = new Node.Call(expr, parseArgList());
            } else if (match(TokenType.DOT)) {
                String prop = consume(TokenType.IDENT).value();
                expr = new Node.Member(expr, prop);
            } else if (at(TokenType.LBRACK)) {
                advance();
                Node key = parseExpression();
                consume(TokenType.RBRACK);
                expr = new Node.Index(expr, key);
            } else {
                break;
            }
        }
        return expr;
    }

    private Node parsePrimary() {
        Token t = peek();
        return switch (t.type()) {
            case NULL     -> { advance(); yield new Node.Lit(null); }
            case TRUE     -> { advance(); yield new Node.Lit(Boolean.TRUE); }
            case FALSE    -> { advance(); yield new Node.Lit(Boolean.FALSE); }
            case NUMBER   -> { advance(); yield new Node.Lit(parseNumber(t.value())); }
            case STRING   -> { advance(); yield parseStringLit(t.value()); }
            case IDENT    -> { advance(); yield new Node.Ident(t.value()); }
            case THIS     -> { advance(); yield new Node.Ident("this"); }
            case FUNCTION -> parseFuncExpr();
            case NEW      -> parseNew();
            case LBRACK   -> parseArrayLit();
            case LBRACE   -> parseObjLit();
            case LPAREN   -> {
                advance();
                // Detect arrow function: (a, b) =>
                int save = pos;
                List<String> arrowParams = tryParseArrowParams();
                if (arrowParams != null && at(TokenType.ARROW)) {
                    advance(); // consume =>
                    Node body = at(TokenType.LBRACE) ? parseBlock() : parseAssignment();
                    yield new Node.ArrowFunc(arrowParams, body);
                }
                pos = save;
                Node expr = parseExpression();
                consume(TokenType.RPAREN);
                yield expr;
            }
            default -> throw new JsError("Beklenmeyen token: " + t, t.line());
        };
    }

    /** Try to parse arrow function parameter list inside already-consumed ( */
    private List<String> tryParseArrowParams() {
        List<String> params = new ArrayList<>();
        if (at(TokenType.RPAREN)) {
            advance();
            return params;
        }
        while (at(TokenType.IDENT)) {
            params.add(advance().value());
            if (!match(TokenType.COMMA)) break;
        }
        if (at(TokenType.RPAREN)) {
            advance();
            return params;
        }
        return null; // Not an arrow function param list
    }

    private Node parseFuncExpr() {
        consume(TokenType.FUNCTION);
        String name = at(TokenType.IDENT) ? advance().value() : null;
        List<String> params = parseParams();
        Node body = parseBlock();
        return new Node.FuncExpr(name, params, body);
    }

    private Node parseNew() {
        consume(TokenType.NEW);
        Node callee = parseCallMember();
        List<Node> args = at(TokenType.LPAREN) ? parseArgList() : List.of();
        return new Node.New_(callee, args);
    }

    private Node parseArrayLit() {
        consume(TokenType.LBRACK);
        List<Node> elements = new ArrayList<>();
        while (!at(TokenType.RBRACK) && !at(TokenType.EOF)) {
            if (match(TokenType.COMMA)) { elements.add(new Node.Lit(null)); continue; }
            if (at(TokenType.ELLIPSIS)) { advance(); elements.add(new Node.Spread(parseAssignment())); }
            else elements.add(parseAssignment());
            if (!match(TokenType.COMMA)) break;
        }
        consume(TokenType.RBRACK);
        return new Node.ArrayLit(elements);
    }

    private Node parseObjLit() {
        consume(TokenType.LBRACE);
        List<Node.ObjLit.ObjProp> props = new ArrayList<>();
        while (!at(TokenType.RBRACE) && !at(TokenType.EOF)) {
            String key;
            boolean computed = false;
            if (at(TokenType.LBRACK)) {
                advance(); computed = true;
                key = "__computed__";
            } else {
                key = switch (peek().type()) {
                    case IDENT, STRING, NUMBER -> advance().value();
                    default -> consume(TokenType.IDENT).value(); // error
                };
            }
            Node value;
            if (computed) {
                // [expr]: value  — simplified: key is a string representation
                Node keyExpr = parseExpression();
                consume(TokenType.RBRACK);
                consume(TokenType.COLON);
                value = parseAssignment();
                props.add(new Node.ObjLit.ObjProp("[computed]", value, true));
                if (!match(TokenType.COMMA)) break;
                continue;
            } else if (match(TokenType.COLON)) {
                value = parseAssignment();
            } else if (at(TokenType.LPAREN)) {
                // Method shorthand: { foo(a) { ... } }
                List<String> params = parseParams();
                Node body = parseBlock();
                value = new Node.FuncExpr(key, params, body);
            } else {
                // Shorthand: { x } → { x: x }
                value = new Node.Ident(key);
            }
            props.add(new Node.ObjLit.ObjProp(key, value, false));
            if (!match(TokenType.COMMA)) break;
        }
        consume(TokenType.RBRACE);
        return new Node.ObjLit(props);
    }

    private List<Node> parseArgList() {
        consume(TokenType.LPAREN);
        List<Node> args = new ArrayList<>();
        while (!at(TokenType.RPAREN) && !at(TokenType.EOF)) {
            if (at(TokenType.ELLIPSIS)) { advance(); args.add(new Node.Spread(parseAssignment())); }
            else args.add(parseAssignment());
            if (!match(TokenType.COMMA)) break;
        }
        consume(TokenType.RPAREN);
        return args;
    }

    private List<String> parseParams() {
        consume(TokenType.LPAREN);
        List<String> params = new ArrayList<>();
        while (!at(TokenType.RPAREN) && !at(TokenType.EOF)) {
            if (at(TokenType.ELLIPSIS)) { advance(); } // rest param: consume & add
            params.add(consume(TokenType.IDENT).value());
            if (!match(TokenType.COMMA)) break;
        }
        consume(TokenType.RPAREN);
        return params;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Template literal: value is interleaved literal/\x01expr\x01/literal
    // ──────────────────────────────────────────────────────────────────

    private Node parseStringLit(String raw) {
        if (!raw.contains("\u0001")) return new Node.Lit(raw);
        // Template literal with expressions
        List<Node> parts = new ArrayList<>();
        String[] segments = raw.split("\u0001", -1);
        for (int i = 0; i < segments.length; i++) {
            if (i % 2 == 0) {
                // literal string part
                if (!segments[i].isEmpty()) parts.add(new Node.Lit(segments[i]));
            } else {
                // expression part — re-parse
                try {
                    List<Token> exprTokens = new Lexer(segments[i]).tokenize();
                    Node expr = new Parser(exprTokens).parseExpression();
                    parts.add(expr);
                } catch (Exception e) {
                    parts.add(new Node.Lit("[error]"));
                }
            }
        }
        return new Node.Template(parts);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────────

    private double parseNumber(String s) {
        try {
            if (s.startsWith("0x") || s.startsWith("0X"))
                return Long.parseLong(s.substring(2), 16);
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            return Double.NaN;
        }
    }

    private Token peek() { return tokens.get(Math.min(pos, tokens.size() - 1)); }

    private Token advance() { return tokens.get(pos++); }

    private boolean at(TokenType type) { return peek().type() == type; }

    private boolean match(TokenType type) {
        if (at(type)) { advance(); return true; }
        return false;
    }

    private Token consume(TokenType type) {
        if (!at(type))
            throw new JsError("Beklenen: " + type + ", bulunan: " + peek().type() + " ('" + peek().value() + "')", peek().line());
        return advance();
    }

    private void skipSemi() { match(TokenType.SEMI); }
}
