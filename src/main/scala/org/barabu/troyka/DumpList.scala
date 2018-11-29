package org.barabu.troyka

import java.io.File

import android.support.v7.app.AppCompatActivity
import android.view.View
import android.os.Bundle
import android.content.Intent
import android.widget.{Adapter, AdapterView, ArrayAdapter, ListView}

import FileManager._
import Implicits._
import jts._
import Utils._
import consts._
import Dump._

class DumpList extends AppCompatActivity {

  var lvDumps = None: Option[android.widget.ListView]
  var tvFile = None: Option[android.widget.TextView]
  var cardID = None: Option[String]

  // https://developer.android.com/reference/android/app/Activity.html#RESULT_OK
  val RESULT_OK = -1


  override def onCreate(savedInstanceState: Bundle): Unit = {
    logIt("DumpList: onCreate")
    super.onCreate(savedInstanceState)
    setContentView(R.layout.dump_list)

    lvDumps = Some(findViewById(R.id.lvDumps).asInstanceOf[android.widget.ListView])
    tvFile = Some(findViewById(R.id.dumpFile).asInstanceOf[android.widget.TextView])
    cardID = Some(getIntent.getStringExtra(getString(R.string.card_id)))

    // Каталог дампов карты
    val dirCard = new File(getAppDir, cardID.head)
    // Список файлов
    val files: Array[File] = dirCard.listFiles().filter(_.isFile)
    // Запоняем ListView списком файлов
    //val adapter = new ArrayAdapter[String](this, android.R.layout.simple_list_item_1, files.map(_.getName))
    val adapter = new ArrayAdapter[String](this, R.layout.listitem, files.map(_.getName))

    lvDumps.head.setAdapter(adapter)

    // Обработчик кликов
    lvDumps.head.setOnItemClickListener(new AdapterView.OnItemClickListener {

      override def onItemClick(parent: AdapterView[_], view: View, position: Int, id: Long): Unit = {
        logIt("DumpList: onItemClick")

        // Прочитать имя выбранного файла
        val file = view.asInstanceOf[android.widget.TextView].getText
        // Отправить результат в родительское окно.
        // Возвращаем номер карты и имя выбранного файла
        val intent = new Intent()
        intent.putExtra(getString(R.string.card_id), cardID.head)
        intent.putExtra(getString(R.string.dump_file), file)
        setResult(RESULT_OK, intent)
        finish()
      }
    })

  }
}
