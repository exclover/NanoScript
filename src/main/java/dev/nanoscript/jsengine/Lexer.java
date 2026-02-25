package dev.nanoscript.jsengine;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts raw JS source text into a flat list of Token objects.
 * Supports: numbers, strings (single/double quote + escape sequences),
 * template literals, all operators, comments, and all keywords.
 */
public class Lexer {

    private static final Map<String, TokenType> KEYWORDS = Map.ofEntries(
        Map.entry("var",        TokenType.VAR),
        Map.entry("let",        TokenType.LET),
        Map.entry("const",      TokenType.CONST),
        Map.entry("function",   TokenType.FUNCTION),
        Map.entry("return",     TokenType.RETURN),
        Map.entry("if",         TokenType.IF),
        Map.entry("else",       TokenType.ELSE),
        Map.entry("while",      TokenType.WHILE),
        Map.entry("do",         TokenType.DO),
        Map.entry("for",        TokenType.FOR),
        Map.entry("in",         TokenType.IN),
        Map.entry("of",         TokenType.OF),
        Map.entry("break",      TokenType.BREAK),
        Map.entry("continue",   TokenType.CONTINUE),
        Map.entry("new",        TokenType.NEW),
        Map.entry("delete",     TokenType.DELETE),
        Map.entry("typeof",     TokenType.TYPEOF),
        Map.entry("void",       TokenType.VOID),
        Map.entry("instanceof", TokenType.INSTANCEOF),
        Map.entry("throw",      TokenType.THROW),
        Map.entry("try",        TokenType.TRY),
        Map.entry("catch",      TokenType.CATCH),
        Map.entry("finally",    TokenType.FINALLY),
        Map.entry("switch",     TokenType.SWITCH),
        Map.entry("case",       TokenType.CASE),
        Map.entry("default",    TokenType.DEFAULT),
        Map.entry("this",       TokenType.THIS),
        Map.entry("true",       TokenType.TRUE),
        Map.entry("false",      TokenType.FALSE),
        Map.entry("null",       TokenType.NULL),
        Map.entry("undefined",  TokenType.NULL) // treat as null
    );

    private final String src;
    private int pos;
    private int line;

    public Lexer(String src) {
        this.src = src;
        this.pos = 0;
        this.line = 1;
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();
        while (true) {
            Token t = next();
            tokens.add(t);
            if (t.type() == TokenType.EOF) break;
        }
        return tokens;
    }

    // ──────────────────────────────────────────────────────────────────
    //  Core dispatch
    // ──────────────────────────────────────────────────────────────────

    private Token next() {
        skipWhitespaceAndComments();
        if (pos >= src.length()) return tok(TokenType.EOF, "");

        char c = src.charAt(pos);

        // Numbers
        if (Character.isDigit(c) || (c == '.' && peek(1) != '.' && isDigit(pos + 1)))
            return readNumber();

        // Strings
        if (c == '"' || c == '\'') return readString(c);
        if (c == '`') return readTemplate();

        // Identifiers & keywords
        if (Character.isLetter(c) || c == '_' || c == '$') return readIdent();

        // Operators & punctuation
        return readOperator();
    }

    // ──────────────────────────────────────────────────────────────────
    //  Whitespace & comments
    // ──────────────────────────────────────────────────────────────────

    private void skipWhitespaceAndComments() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == '\n') { line++; pos++; }
            else if (Character.isWhitespace(c)) { pos++; }
            else if (c == '/' && pos + 1 < src.length()) {
                char n = src.charAt(pos + 1);
                if (n == '/') { // line comment
                    while (pos < src.length() && src.charAt(pos) != '\n') pos++;
                } else if (n == '*') { // block comment
                    pos += 2;
                    while (pos + 1 < src.length() && !(src.charAt(pos) == '*' && src.charAt(pos + 1) == '/')) {
                        if (src.charAt(pos) == '\n') line++;
                        pos++;
                    }
                    pos += 2;
                } else break;
            } else break;
        }
    }

    // ──────────────────────────────────────────────────────────────────
    //  Number
    // ──────────────────────────────────────────────────────────────────

    private Token readNumber() {
        int start = pos;
        // Hex
        if (charAt(pos) == '0' && (charAt(pos+1) == 'x' || charAt(pos+1) == 'X')) {
            pos += 2;
            while (pos < src.length() && isHexDigit(src.charAt(pos))) pos++;
            return tok(TokenType.NUMBER, src.substring(start, pos));
        }
        // Decimal
        while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        if (pos < src.length() && src.charAt(pos) == '.' && isDigit(pos + 1)) {
            pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        // Exponent
        if (pos < src.length() && (src.charAt(pos) == 'e' || src.charAt(pos) == 'E')) {
            pos++;
            if (pos < src.length() && (src.charAt(pos) == '+' || src.charAt(pos) == '-')) pos++;
            while (pos < src.length() && Character.isDigit(src.charAt(pos))) pos++;
        }
        return tok(TokenType.NUMBER, src.substring(start, pos));
    }

    // ──────────────────────────────────────────────────────────────────
    //  String literals
    // ──────────────────────────────────────────────────────────────────

    private Token readString(char quote) {
        pos++; // skip opening quote
        StringBuilder sb = new StringBuilder();
        while (pos < src.length() && src.charAt(pos) != quote) {
            char c = src.charAt(pos);
            if (c == '\\') {
                pos++;
                if (pos >= src.length()) break;
                char esc = src.charAt(pos);
                switch (esc) {
                    case 'n' -> sb.append('\n');
                    case 't' -> sb.append('\t');
                    case 'r' -> sb.append('\r');
                    case '\\' -> sb.append('\\');
                    case '\'' -> sb.append('\'');
                    case '"' -> sb.append('"');
                    case '`' -> sb.append('`');
                    case '0' -> sb.append('\0');
                    default -> { sb.append('\\'); sb.append(esc); }
                }
            } else {
                if (c == '\n') line++;
                sb.append(c);
            }
            pos++;
        }
        pos++; // skip closing quote
        return tok(TokenType.STRING, sb.toString());
    }

    // ──────────────────────────────────────────────────────────────────
    //  Template literals  `hello ${name}!`
    //  We inline-expand them to string + expression concatenation tokens.
    //  Strategy: emit a STRING token for each literal part,
    //  and re-tokenize expression parts inline.
    //  Instead, we emit a special TEMPLATE token with raw text and let the
    //  parser handle it. Format: literal\x00expr\x00literal\x00expr\x00...
    // ──────────────────────────────────────────────────────────────────

    private Token readTemplate() {
        pos++; // skip `
        StringBuilder raw = new StringBuilder();
        while (pos < src.length() && src.charAt(pos) != '`') {
            char c = src.charAt(pos);
            if (c == '$' && pos + 1 < src.length() && src.charAt(pos + 1) == '{') {
                raw.append('\u0001'); // separator: literal|expression
                pos += 2; // skip ${
                int depth = 1;
                while (pos < src.length() && depth > 0) {
                    char ec = src.charAt(pos);
                    if (ec == '{') depth++;
                    else if (ec == '}') { depth--; if (depth == 0) { pos++; break; } }
                    if (ec == '\n') line++;
                    raw.append(ec);
                    pos++;
                }
                raw.append('\u0001'); // end of expression
            } else {
                if (c == '\n') line++;
                if (c == '\\' && pos + 1 < src.length()) {
                    pos++;
                    char esc = src.charAt(pos);
                    switch (esc) {
                        case 'n' -> raw.append('\n');
                        case 't' -> raw.append('\t');
                        default -> raw.append(esc);
                    }
                } else {
                    raw.append(c);
                }
                pos++;
            }
        }
        pos++; // skip `
        return tok(TokenType.STRING, raw.toString()); // parser handles \u0001 markers
    }

    // ──────────────────────────────────────────────────────────────────
    //  Identifiers & keywords
    // ──────────────────────────────────────────────────────────────────

    private Token readIdent() {
        int start = pos;
        while (pos < src.length() && (Character.isLetterOrDigit(src.charAt(pos)) || src.charAt(pos) == '_' || src.charAt(pos) == '$'))
            pos++;
        String word = src.substring(start, pos);
        TokenType kw = KEYWORDS.get(word);
        return tok(kw != null ? kw : TokenType.IDENT, word);
    }

    // ──────────────────────────────────────────────────────────────────
    //  Operators & punctuation
    // ──────────────────────────────────────────────────────────────────

    private Token readOperator() {
        char c = src.charAt(pos++);
        return switch (c) {
            case '(' -> tok(TokenType.LPAREN, "(");
            case ')' -> tok(TokenType.RPAREN, ")");
            case '{' -> tok(TokenType.LBRACE, "{");
            case '}' -> tok(TokenType.RBRACE, "}");
            case '[' -> tok(TokenType.LBRACK, "[");
            case ']' -> tok(TokenType.RBRACK, "]");
            case ';' -> tok(TokenType.SEMI, ";");
            case ',' -> tok(TokenType.COMMA, ",");
            case ':' -> tok(TokenType.COLON, ":");
            case '?' -> match('?') ? tok(TokenType.NULLISH, "??") : tok(TokenType.QUESTION, "?");
            case '~' -> tok(TokenType.BIT_NOT, "~");
            case '^' -> match('=') ? tok(TokenType.PERCENT_ASSIGN, "^=") : tok(TokenType.BIT_XOR, "^");
            case '%' -> match('=') ? tok(TokenType.PERCENT_ASSIGN, "%=") : tok(TokenType.PERCENT, "%");
            case '&' -> match('&') ? tok(TokenType.AND, "&&") : match('=') ? tok(TokenType.ASSIGN, "&=") : tok(TokenType.BIT_AND, "&");
            case '|' -> match('|') ? tok(TokenType.OR, "||") : match('=') ? tok(TokenType.ASSIGN, "|=") : tok(TokenType.BIT_OR, "|");
            case '!' -> match('=') ? (match('=') ? tok(TokenType.STRICT_NEQ, "!==") : tok(TokenType.NEQ, "!=")) : tok(TokenType.NOT, "!");
            case '=' -> match('=') ? (match('=') ? tok(TokenType.STRICT_EQ, "===") : tok(TokenType.EQ, "==")) : match('>') ? tok(TokenType.ARROW, "=>") : tok(TokenType.ASSIGN, "=");
            case '<' -> match('<') ? (match('=') ? tok(TokenType.ASSIGN, "<<=") : tok(TokenType.LSHIFT, "<<")) : match('=') ? tok(TokenType.LTE, "<=") : tok(TokenType.LT, "<");
            case '>' -> {
                if (match('>')) {
                    if (match('>')) { yield match('=') ? tok(TokenType.ASSIGN, ">>>=") : tok(TokenType.URSHIFT, ">>>"); }
                    yield match('=') ? tok(TokenType.ASSIGN, ">>=") : tok(TokenType.RSHIFT, ">>");
                }
                yield match('=') ? tok(TokenType.GTE, ">=") : tok(TokenType.GT, ">");
            }
            case '+' -> match('+') ? tok(TokenType.PLUSPLUS, "++") : match('=') ? tok(TokenType.PLUS_ASSIGN, "+=") : tok(TokenType.PLUS, "+");
            case '-' -> match('-') ? tok(TokenType.MINUSMINUS, "--") : match('=') ? tok(TokenType.MINUS_ASSIGN, "-=") : tok(TokenType.MINUS, "-");
            case '*' -> match('*') ? (match('=') ? tok(TokenType.ASSIGN, "**=") : tok(TokenType.STARSTAR, "**")) : match('=') ? tok(TokenType.STAR_ASSIGN, "*=") : tok(TokenType.STAR, "*");
            case '/' -> match('=') ? tok(TokenType.SLASH_ASSIGN, "/=") : tok(TokenType.SLASH, "/");
            case '.' -> {
                if (pos + 1 < src.length() && src.charAt(pos) == '.' && src.charAt(pos+1) == '.') {
                    pos += 2; yield tok(TokenType.ELLIPSIS, "...");
                }
                yield tok(TokenType.DOT, ".");
            }
            default -> throw new JsError("Bilinmeyen karakter: '" + c + "' (satır " + line + ")");
        };
    }

    // ──────────────────────────────────────────────────────────────────
    //  Helpers
    // ──────────────────────────────────────────────────────────────────

    private boolean match(char expected) {
        if (pos < src.length() && src.charAt(pos) == expected) { pos++; return true; }
        return false;
    }

    private char charAt(int i) {
        return i < src.length() ? src.charAt(i) : '\0';
    }

    private char peek(int offset) {
        int i = pos + offset;
        return i < src.length() ? src.charAt(i) : '\0';
    }

    private boolean isDigit(int i) {
        return i < src.length() && Character.isDigit(src.charAt(i));
    }

    private boolean isHexDigit(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f') || (c >= 'A' && c <= 'F');
    }

    private Token tok(TokenType type, String value) {
        return new Token(type, value, line);
    }
}
