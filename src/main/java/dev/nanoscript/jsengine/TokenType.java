package dev.nanoscript.jsengine;

public enum TokenType {
    // ── Literals ──────────────────────────────────────────────────
    NUMBER, STRING, TRUE, FALSE, NULL,

    // ── Identifiers & Keywords ────────────────────────────────────
    IDENT,
    VAR, LET, CONST,
    FUNCTION, RETURN, ARROW,      // =>
    IF, ELSE,
    WHILE, DO, FOR, IN, OF, BREAK, CONTINUE,
    NEW, DELETE, TYPEOF, VOID, INSTANCEOF,
    THROW, TRY, CATCH, FINALLY,
    SWITCH, CASE, DEFAULT,
    THIS,

    // ── Separators ────────────────────────────────────────────────
    LPAREN, RPAREN,       // ( )
    LBRACE, RBRACE,       // { }
    LBRACK, RBRACK,       // [ ]
    SEMI, COLON, COMMA, DOT, ELLIPSIS, QUESTION,

    // ── Assignment ────────────────────────────────────────────────
    ASSIGN,          // =
    PLUS_ASSIGN,     // +=
    MINUS_ASSIGN,    // -=
    STAR_ASSIGN,     // *=
    SLASH_ASSIGN,    // /=
    PERCENT_ASSIGN,  // %=

    // ── Arithmetic ────────────────────────────────────────────────
    PLUS, MINUS, STAR, SLASH, PERCENT, STARSTAR,

    // ── Comparison ────────────────────────────────────────────────
    EQ, NEQ, STRICT_EQ, STRICT_NEQ,
    LT, GT, LTE, GTE,

    // ── Logical ───────────────────────────────────────────────────
    AND, OR, NOT,
    NULLISH,         // ??

    // ── Bitwise ───────────────────────────────────────────────────
    BIT_AND, BIT_OR, BIT_XOR, BIT_NOT,
    LSHIFT, RSHIFT, URSHIFT,

    // ── Update ────────────────────────────────────────────────────
    PLUSPLUS, MINUSMINUS,

    // ── End ───────────────────────────────────────────────────────
    EOF
}
