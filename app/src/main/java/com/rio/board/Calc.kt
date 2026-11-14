package com.rio.board

import java.math.BigDecimal
import java.math.MathContext
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/**
 * Small expression calculator for the keyboard.
 * + - × ÷ ^ √ ( ) %   -   "200+10%" = 220, "-2^2" = -4, "2(3+4)" = 14,
 * unfinished input is forgiven: "5+" = 5, "(2+3" = 5.
 */
object Calc {

    private class V(val n: Double, val pct: Boolean = false)

    private class P(val s: String) {
        var i = 0
        fun peek(): Char = if (i < s.length) s[i] else '\u0000'
        fun eat(c: Char): Boolean {
            if (peek() == c) {
                i++
                return true
            }
            return false
        }
    }

    private fun num(v: V): Double = if (v.pct) v.n / 100.0 else v.n

    /** null = empty / invalid (for example division by zero). */
    fun eval(raw: String): Double? {
        var s = raw.replace('×', '*').replace('÷', '/').replace('−', '-').replace('–', '-').replace(" ", "")
        while (s.isNotEmpty() && s.last() in "+-*/^") s = s.dropLast(1)
        if (s.isEmpty()) return null
        val open = s.count { it == '(' } - s.count { it == ')' }
        if (open > 0) s += ")".repeat(open)
        return try {
            val p = P(s)
            val v = num(expr(p))
            if (p.i != s.length || v.isNaN() || v.isInfinite()) null else v
        } catch (e: Exception) {
            null
        }
    }

    private fun expr(p: P): V {
        var left = term(p)
        while (true) {
            val op = p.peek()
            if (op == '+' || op == '-') {
                p.i++
                val right = term(p)
                val l = num(left)
                // "200 + 10%" means 200 + 10 % of 200
                val r = if (right.pct) l * right.n / 100.0 else right.n
                left = V(if (op == '+') l + r else l - r)
            } else {
                break
            }
        }
        return left
    }

    private fun term(p: P): V {
        var left = unary(p)
        while (true) {
            if (p.eat('*')) {
                left = V(num(left) * num(unary(p)))
            } else if (p.eat('/')) {
                val r = num(unary(p))
                if (r == 0.0) throw ArithmeticException("div0")
                left = V(num(left) / r)
            } else if (p.peek() == '(' || p.peek() == '√') {
                left = V(num(left) * num(unary(p)))      // 2(3+4), 2√9
            } else {
                break
            }
        }
        return left
    }

    private fun unary(p: P): V {
        if (p.eat('-')) return V(-num(unary(p)))
        if (p.eat('+')) return unary(p)
        return power(p)
    }

    private fun power(p: P): V {
        val base = postfix(p)
        if (p.eat('^')) {
            val e = unary(p)
            return V(num(base).pow(num(e)))
        }
        return base
    }

    private fun postfix(p: P): V {
        var v = primary(p)
        while (p.peek() == '%') {
            p.i++
            v = V(v.n, true)
        }
        return v
    }

    private fun primary(p: P): V {
        val c = p.peek()
        if (c == '(') {
            p.i++
            val v = expr(p)
            if (!p.eat(')')) throw IllegalArgumentException("paren")
            return V(num(v))
        }
        if (c == '√') {
            p.i++
            val x = num(postfix(p))
            if (x < 0) throw ArithmeticException("sqrt")
            return V(sqrt(x))
        }
        val st = p.i
        while (p.peek().isDigit() || p.peek() == '.') p.i++
        if (st == p.i) throw IllegalArgumentException("number")
        return V(p.s.substring(st, p.i).toDouble())
    }

    fun format(v: Double): String {
        if (v == 0.0) return "0"
        if (v == Math.rint(v) && abs(v) < 1e15) return v.toLong().toString()
        val bd = BigDecimal(v).round(MathContext(12)).stripTrailingZeros()
        return if (abs(v) >= 1e15 || abs(v) < 1e-9) bd.toString() else bd.toPlainString()
    }
}
