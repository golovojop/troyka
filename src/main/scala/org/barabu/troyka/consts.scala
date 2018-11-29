package org.barabu.troyka

object consts {
  val INTENT_READ_DUMP = "org.barabu.troyka.INTENT_READ_DUMP"
  val ACTION_NEW_DUMP = "org.barabu.troyka.dump_new"

  val PACKAGE = "org.barabu.troyka"
  val DUMP_UID = PACKAGE + ".dump_uid"
  val DUMP_DATA = PACKAGE + ".dump_data"

  val LOG_ID = "MyLog"


  val DIR_NAME = "TROYKA"

  var RELEASE_DBG = true

  val STR_EMPTY = ""

  /**
    * State definitions
    */
  object AppStates extends Enumeration {
    type State = Value
    val S_IDLE, S_TAG_DUMP, S_FILE_DUMP, S_SAVE_DIALOG, S_FILE_SAVED, S_WAIT_DIALOG, S_BURNING, S_BURNED = Value
  }

  /**
    * Action definitions
    */
  sealed trait Action
  case class onCard()         extends Action
  case class onFile()         extends Action
  case class onBurn()         extends Action
  case class onBurnSuccess()  extends Action
  case class onBurnError()    extends Action
  case class onSave()         extends Action
  case class onCancel()       extends Action


  /**
    *
    */
  sealed trait DumpSource
  case class SourceFile() extends DumpSource
  case class SourceTag()  extends DumpSource

}
