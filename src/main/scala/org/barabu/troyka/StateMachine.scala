package org.barabu.troyka

import java.util.{Timer, TimerTask}

import android.view.View.OnClickListener
import android.view._
import android.app.Dialog
import Utils._
import android.content.DialogInterface
import android.support.v7.app.AlertDialog
import consts._

/**
  *
  * @param activity
  */
case class StateMachine(activity: android.support.v7.app.AppCompatActivity) {

  // Начальное состояние
  import AppStates._
  case class StateHolder(prev: State, now: State, a: Action, msg: String)
  var stateHolder = StateHolder(S_IDLE, S_IDLE, onCancel(), "")

  // Инициализация элементов экрана
  val txtView = activity.findViewById(R.id.text).asInstanceOf[android.widget.TextView]
  val btnSave = activity.findViewById(R.id.btnSave).asInstanceOf[android.widget.Button]
  val btnCancel = activity.findViewById(R.id.btnCancel).asInstanceOf[android.widget.Button]

  btnSave.setOnClickListener(activity.asInstanceOf[OnClickListener])
  btnCancel.setOnClickListener(activity.asInstanceOf[OnClickListener])

  // Диалог ожидания карты
  val dlgCardWait = new Dialog(activity)
  dlgCardWait.requestWindowFeature(Window.FEATURE_NO_TITLE)
  dlgCardWait.setContentView(R.layout.card_wait)
  val tvDlgCardWait = dlgCardWait.findViewById(R.id.tvCardWait).asInstanceOf[android.widget.TextView]
  // Диалог результата прожига
  val dlgBurnResult = new Dialog(activity)
  dlgBurnResult.requestWindowFeature(Window.FEATURE_NO_TITLE)
  dlgBurnResult.setContentView(R.layout.card_burn)
  val tvDlgCardBurn = dlgBurnResult.findViewById(R.id.tvCardBurn).asInstanceOf[android.widget.TextView]
  // Диалог сохранения файла
  val dlgSaveResult = new Dialog(activity)
  dlgSaveResult.requestWindowFeature(Window.FEATURE_NO_TITLE)
  dlgSaveResult.setContentView(R.layout.card_save)
  val tvDlgCardSave = dlgSaveResult.findViewById(R.id.tvCardSave).asInstanceOf[android.widget.TextView]


  logIt("StateMachine: constructor")

  /**
    *
    */
  def transition(action: Action, extra: String = STR_EMPTY): StateHolder = {
    logIt("StateMachine: transition")

    val stateNew = stateHolder.now match {
      case S_IDLE => action match {
        case c: onCard => activityTagDump(extra)
        case f: onFile => activityFileDump(extra)
        case _ => activityIdle(extra)
      }
      case S_TAG_DUMP => action match {
        case c: onCard => activityTagDump(extra)
        case f: onFile => activityFileDump(extra)
        case s: onSave => dialogSave(extra)
        case _ => activityIdle(extra)
      }
      case S_FILE_DUMP => action match {
        case c: onCard => activityTagDump(extra)
        case f: onFile => activityFileDump(extra)
        case b: onBurn => dialogWaitCard(extra)
        case _ => activityIdle(extra)
      }
      case S_WAIT_DIALOG => action match {
        case c: onCard => burningStarted(extra)
        case _ => activityIdle(extra)
      }
      case S_BURNING => action match {
        case bs: onBurnSuccess => dialogBurnSuccess(extra)
        case be: onBurnError => dialogBurnError(extra)
        case _ => activityIdle(extra)
      }
      case S_BURNED => action match {
        case _ => activityIdle(extra)
      }
      case S_SAVE_DIALOG => action match {
        case _ => activityIdle(extra)
      }
      case S_FILE_SAVED => action match {
        case _ => activityIdle(extra)
      }
    }
    stateHolder = StateHolder(stateHolder.now, stateNew, action, extra)
    logIt("CurrentState: " + stateHolder.now.toString)
    stateHolder
  }

  /**
    *
    */
  def activityIdle(info: String = STR_EMPTY): State = {
    logIt("StateMachine: activityIdle")
    txtView.setGravity(Gravity.LEFT | Gravity.TOP)
    txtView.setText(if(info.length == 0) "Ready" else info)

    btnSave.setVisibility(View.INVISIBLE)
    btnCancel.setVisibility(View.INVISIBLE)
    activity.asInstanceOf[MainActivity].activeDump = None
    S_IDLE
  }

  /**
    *
    */
  def activityTagDump(info: String): State = {
    logIt("StateMachine: activityTagDump")
    txtView.setGravity(Gravity.CENTER_VERTICAL)
    txtView.setText(info)
    btnSave.setText(activity.getString(R.string.button_Save))
    btnSave.setBackgroundResource(R.color.colorPrimary)
    btnSave.setVisibility(View.VISIBLE)
    btnCancel.setVisibility(View.VISIBLE)
    S_TAG_DUMP
  }

  def activityFileDump(info: String): State = {
    logIt("StateMachine: activityFileDump")
    txtView.setGravity(Gravity.CENTER_VERTICAL)
    txtView.setText(info)
    btnSave.setText(activity.getString(R.string.button_Burn))
    btnSave.setBackgroundResource(R.color.buttonBurn)
    btnSave.setVisibility(View.VISIBLE)
    btnCancel.setVisibility(View.VISIBLE)
    S_FILE_DUMP
  }

  def dialogWaitCard(info: String = STR_EMPTY): State = {
    logIt("StateMachine: dialogWaitCard")

    tvDlgCardWait.setText(info)
    dlgCardWait.show()
    // Диалог закроется через 6 секунд
    //dismissScheduler(dlgCardWait, 6000)
    S_WAIT_DIALOG
  }

  def burningStarted(info: String = STR_EMPTY): State = {
    logIt("StateMachine: dialogBurning")
    dlgCardWait.dismiss()
    S_BURNING
  }

  def dialogBurnSuccess(info: String = STR_EMPTY): State = {
    logIt("StateMachine: dialogBurnSuccess")
    tvDlgCardBurn.setText(info)
    dlgBurnResult.show()
    // Диалог закроется через 2 сек
    dismissScheduler(dlgBurnResult, 2000)
    S_BURNED
  }

  def dialogBurnError(info: String = STR_EMPTY): State = {
    logIt("StateMachine: dialogBurnError")
    tvDlgCardBurn.setText(info)
    dlgBurnResult.show()
    // Диалог закроется через 2 сек
    dismissScheduler(dlgBurnResult, 2000)
    S_BURNED
  }

  def dialogSave(info: String = STR_EMPTY): State = {
    logIt("StateMachine: dialogSave")
    tvDlgCardSave.setText(info)
    dlgSaveResult.show()
    // Диалог закроется через 2 сек
    dismissScheduler(dlgSaveResult, 2000)
    S_FILE_SAVED
  }

  /**
    * TODO: Закрытие окна диалога по таймеру
    */
  def dismissScheduler(dialog: Dialog, delay: Long = 5000) = {
    // Диалог закроется через 5 секунд
    val timer = new Timer()
    timer.schedule(new TimerTask {
      def run(): Unit = {
        dialog.dismiss()
        timer.cancel()
      }
    }, delay)
  }
}
