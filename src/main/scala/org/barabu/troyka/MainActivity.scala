/**
  * Материалы:
  * ---------------------------------------
  * По поводу intent-фильтра и его meta-данных:
  * https://developer.android.com/reference/android/nfc/NfcAdapter#ACTION_TECH_DISCOVERED
  *
  * Хороший пример кода
  * https://gist.github.com/daichan4649/3246599
  *
  */

package org.barabu.troyka

import java.io.File

import android.os.{Bundle, Environment}
import android.support.v7.app.AppCompatActivity
import android.content.Context
import android.widget.Toast
import android.nfc.tech.MifareClassic
import android.content.Intent
import android.nfc._
import android.app._
import android.content._
import android.view.View.OnClickListener
import android.view._
import android.widget._
import android.text.TextUtils
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.AdapterView.OnItemClickListener
import android.support.v4.content.LocalBroadcastManager

import scala.util.{Failure, Success, Try}
import Implicits._
import jts._
import Utils._
import consts._
import Dump._
import FileManager._
import consts.AppStates._

import scala.collection.mutable.ArrayBuffer

class MainActivity extends AppCompatActivity with OnClickListener {
  // allows accessing `.value` on TR.resource.constants
  implicit val context = this

  var nfcAdapter = None: Option[NfcAdapter]
  var activeDump = None: Option[Dump]
  var optionsMenu = None: Option[SubMenu]
  val menuItems = ArrayBuffer[String]()

  // Индикатор режима записи
  var burnMode = false

  // Текущее состояние программы
  var stateMachine = None: Option[StateMachine]

  /**
    * onCreate
    * @param savedInstanceState
    */
  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)
    setContentView(R.layout.main)

    logIt("onCreate")

    // Получить NFC адаптер
    val nfcManager: NfcManager = getSystemService(Context.NFC_SERVICE).asInstanceOf[NfcManager]
    nfcAdapter = notNull[NfcAdapter](nfcManager.getDefaultAdapter)

    // Установить начальное состояние программы
    stateMachine = Some(StateMachine(this))
    stateMachine.head.transition(onCancel())
  }

  /**
    * onResume
    */
  override def onResume: Unit = {
    super.onResume()

    logIt("onResume")

    // Check NFC capability
    val nfcEnabled = nfcAdapter match {
      case Some(a) => {
        registerNfcFilter(a)
        a.isEnabled
      }
      case None => false
    }

    // NFC Error dialog
    if(!nfcEnabled) {
      val builder = new AlertDialog.Builder(this)
      builder.setMessage(R.string.error_no_nfc)
      builder.setCancelable(false)
      val d = builder.create()
      d.requestWindowFeature(Window.FEATURE_NO_TITLE)
      d.show()
    }
  }

  /**
    *
    */
  override def onPause(): Unit = {
    super.onPause()
    logIt("onPause")

    nfcAdapter match {
      case Some(a) => unregisterNfcFilter(a)
      case _ => logIt("onPause: NFC Adapter is null")
    }
  }

  /**
    * TODO: Сразу создаем выпадающее подменю "Card dumps", которое будет включать элементы
    * TODO: в виде номеров карт Тройка.
    */
  override def onCreateOptionsMenu(menu: Menu): Boolean = {
    logIt("onCreateOptionsMenu")
    val subMenu = menu.addSubMenu(R.string.action_dumps)
    optionsMenu = Some(subMenu)

    Try {
      val dirTroyka = getAppDir

      dirTroyka.exists() match {
        case true => {
          // Прочитать имена КАТАЛОГОВ в папке TROYKA
          // и составить из них пункты меню
          val subDirs: Array[File] = dirTroyka.listFiles().filter(_.isDirectory)
          if(subDirs.size != 0)
            for (d <- subDirs) { optionsMenu.head.add(d.getName); menuItems += d.getName }
          else
            throw new RuntimeException
        }
        case _ => throw new RuntimeException
      }
    } /*match {
      case Success(_) => true
      case Failure(_) => false
    }*/

    super.onCreateOptionsMenu(menu)
  }

  /**
    * TODO: Когда юзер выбирает номер карты, то должно открыться активити
    * TODO: со списком её дампов. Дочернее активити должно вернуть имя выбранного
    * TODO: файла
    */
  override def onOptionsItemSelected(item: MenuItem): Boolean = {
    logIt("onOptionsItemSelected")

    // Получить выбранный cardID
    val cardId = item.getTitle()
    menuItems.find(_ == cardId) match {
      case Some(card) => {
        val intent = new Intent(this, classOf[DumpList])
        intent.putExtra(getString(R.string.card_id), card)
        startActivityForResult(intent, 1)
        super.onOptionsItemSelected(item)
      }
      case None => false
    }
  }

  /**
    * TODO: Дочерняя активити должна вернуть имя выбранного файла
    */
  override def onActivityResult(requestCode: Int, resultCode: Int, intent: Intent): Unit = {
    logIt("onActivityResult")

    notNull[Intent](intent) match {
      case Some(i) => {
        val file = i.getStringExtra(getString(R.string.dump_file))
        val cadrID = i.getStringExtra(getString(R.string.card_id))

        Dump.fromFile(cadrID, file) match {
          case Success(dump) => {
            activeDump = Some(dump)
            stateMachine.head.transition(onFile(), dump.info)
          }
          case Failure(e) => logIt("onActivityResult: dump error")
        }
      }
      case None => logIt("onActivityResult: null result")
    }
  }

  /**
    * TODO: Button onClick handler
    * @param v
    */
  override def onClick(v: android.view.View) = {

    v.getId match {
      case R.id.btnSave => {

        val button = v.asInstanceOf[android.widget.Button]

        // Нужны кавычки `burn`, `save`
        // https://stackoverflow.com/questions/7078022/why-does-pattern-matching-in-scala-not-work-with-variables
        val burn = getString(R.string.button_Burn)
        val save = getString(R.string.button_Save)

        button.getText().toString match {
          case `burn` => {
            burnMode = true
            stateMachine.head.transition(onBurn(), s"Waiting for card\n\n${activeDump.head.getCadrID}")
          }
          case `save` => {
            Dump.toFile(activeDump.head) match {
              case Success(t) =>{
                updateMenu(activeDump.head.getCadrID)
                stateMachine.head.transition(onSave(), s"Dump stored in file\n\n${t}")
                stateMachine.head.transition(onCancel())
              }
              case Failure(e) => {
                stateMachine.head.transition(onSave(), s"IO error:\n\n${e}")
                stateMachine.head.transition(onCancel())
              }
            }
          }
        }
      }
      case _ /*R.id.btnCancel*/ => {
        logIt("onClick: Cansel clicked")
        stateMachine.head.transition(onCancel())
      }
    }
  }

  /**
    * This method gets called, when a new Intent gets associated with the CURRENT
    * activity instance. Instead of creating a new activity, onNewIntent will be called.
    * @param intent
    */
  override def onNewIntent(intent: Intent): Unit = {
    logIt("onNewIntent")
    handleIntent(intent)
  }

  /**
    *
    * @param intent
    */
  def handleIntent(intent: Intent) = {

    val action = intent.getAction
    logIt("handleIntent: " + action)

    Try {

      action match {
        case NfcAdapter.ACTION_TECH_DISCOVERED => {

          notNull[Tag](intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)) match {

            case Some(tag) => {
              logIt("handleIntent: tag is extracted")

              if (burnMode) {
                stateMachine.head.transition(onCard()) // --> S_BURNING

                Dump.burn(tag, activeDump.head) match {
                  case Success(s) => stateMachine.head.transition(onBurnSuccess(), s)
                  case Failure(e) => stateMachine.head.transition(onBurnError(), e.getMessage)
                }

                burnMode = false
                stateMachine.head.transition(onCancel())
              }
              else {
                Dump.fromTag(tag) match {
                  case Success(dump) => {
                    activeDump = Some(dump)
                    stateMachine.head.transition(onCard(), dump.info)
                  }
                  case Failure(_) => stateMachine.head.transition(onCancel(), getString(R.string.error_dump_read))
                }
              }
            }
            case None => {
              logIt("handleIntent: tag is not extracted")
              stateMachine.head.transition(onCancel(), getString(R.string.error_dump_read))
            }
          }
        }
        case _ => logIt("handleIntent: unknown intent action")
      }
    }
  }

  /**
    *
    * @param adapter
    */
  def registerNfcFilter(adapter: NfcAdapter) = {

    // Activity который нужно вызывать
    val intent = new Intent(this, getClass())
    intent.setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)

    val pIntent = PendingIntent.getActivity(this, 0, intent, 0)

    /**
      * Вместо
      * techList(0)(0) = MifareClassic.class.getName()
      * https://alvinalexander.com/scala/scala-equivalent-of-java-class-getclass-classof
      */
    val techList = Array.ofDim[String](1,1)
    techList(0)(0) = classOf[MifareClassic].getName()

    // The same filter as in our manifest
    val intentFilter = Array[IntentFilter](new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED))
    intentFilter(0).addCategory(Intent.CATEGORY_DEFAULT)

    // May be IntentFilter.MalformedMimeTypeException exception
    Try {
      intentFilter(0).addDataType("*/*")
    } match {
      case Success(_) => adapter.enableForegroundDispatch(this, pIntent, intentFilter, techList)
      case Failure(_) => logIt("registerNfcFilter: IntentFilter error")
    }

  }

  /**
    *
    * @param adapter
    */
  def unregisterNfcFilter(adapter: NfcAdapter) = {
    adapter.disableForegroundDispatch(this)
  }

  /**
    * TODO: Добавить новый пункт меню
    * @return
    */
  def updateMenu(item: String) = {

    menuItems.find(_ == item) match {
      case None => {
        optionsMenu.head.add(item)
        menuItems += item
      }
      case _ => item
    }
  }

}