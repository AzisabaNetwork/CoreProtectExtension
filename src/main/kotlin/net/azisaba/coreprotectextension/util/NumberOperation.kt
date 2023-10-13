package net.azisaba.coreprotectextension.util

import java.math.BigDecimal
import java.math.BigInteger

data class NumberOperation<T : Number>(val type: Type, val number: T) {
    enum class Type(val op: String) {
        GreaterOrEqual(">="),
        LessOrEqual("<="),
        NotEqual("!="),
        Equal("="),
        Greater(">"),
        Less("<"),
    }

    override fun toString(): String = "${type.op}$number"

    companion object {
        inline fun <reified T : Number> parse(str: String): NumberOperation<T> {
            val type = Type.entries.find { str.startsWith(it.op) } ?: error("Invalid operation: $str")
            val rawNumber = str.substring(type.op.length)
            val number = when (T::class) {
                Long::class -> rawNumber.toLong()
                Int::class -> rawNumber.toInt()
                Byte::class -> rawNumber.toByte()
                Short::class -> rawNumber.toShort()
                Float::class -> rawNumber.toFloat()
                Double::class -> rawNumber.toDouble()
                ULong::class -> rawNumber.toULong()
                UInt::class -> rawNumber.toUInt()
                UByte::class -> rawNumber.toUByte()
                UShort::class -> rawNumber.toUShort()
                BigDecimal::class -> rawNumber.toBigDecimal()
                BigInteger::class -> rawNumber.toBigInteger()
                else -> error("Unsupported number class: ${T::class.simpleName}")
            } as T
            return NumberOperation(type, number)
        }
    }
}
