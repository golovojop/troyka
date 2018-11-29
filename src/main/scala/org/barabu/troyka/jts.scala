package org.barabu.troyka

object jts {

  /**
    *
    */
  def notNull[T](v: T): Option[T] = {
    if(v == null) None else Some(v)
  }

}
