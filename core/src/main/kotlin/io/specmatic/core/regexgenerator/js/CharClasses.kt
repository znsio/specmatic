package io.specmatic.core.regexgenerator.js
import org.cornutum.regexpgen.GenOptions
import org.cornutum.regexpgen.js.NoneOfGen


/**
 * Creates [CharClassGen] instances for standard character classes.
 */
class CharClasses

/**
 * Creates a new CharClasses instance.
 */(private val options_: GenOptions) {
    /**
     * Returns the character class represented by "/d"
     */
    fun digit(): CharClassGen {
        return AnyOfGen(options_, '0', '9')
    }

    /**
     * Returns the character class represented by "/D"
     */
    fun nonDigit(): CharClassGen {
        return NoneOfGen(digit())
    }

    /**
     * Returns the character class represented by "/w"
     */
    fun word(): CharClassGen {
        val word = AnyOfGen(options_, '0', '9')
        word.addAll('A', 'Z')
        word.addAll('a', 'z')
        word.add('_')
        return word
    }

    /**
     * Returns the character class represented by "/W"
     */
    fun nonWord(): CharClassGen {
        return NoneOfGen(word())
    }

    /**
     * Returns the character class represented by "/s"
     */
    fun space(): CharClassGen {
        val space = AnyOfGen(options_)
        space.add(' ')
        space.add('\f')
        space.add('\n')
        space.add('\r')
        space.add('\t')
        space.add(0x000b.toChar())
        space.add(0x00a0.toChar())
        space.add(0x1680.toChar())
        space.add(0x2028.toChar())
        space.add(0x2029.toChar())
        space.add(0x202f.toChar())
        space.add(0x205f.toChar())
        space.add(0x3000.toChar())
        space.add(0xfeff.toChar())
        space.addAll(0x2000.toChar(), 0x200a.toChar())

        return space
    }

    /**
     * Returns the character class represented by "/S"
     */
    fun nonSpace(): CharClassGen {
        return NoneOfGen(space())
    }
}