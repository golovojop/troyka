package org.barabu.troyka

object Implicits {
  implicit def intToByte(i: Int): Byte = i.toByte

}
