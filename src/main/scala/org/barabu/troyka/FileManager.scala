package org.barabu.troyka

import java.util.Date
import java.util.Calendar
import java.util.TimeZone
import java.text.SimpleDateFormat
import java.io.BufferedWriter
import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.io.OutputStreamWriter
import java.text.SimpleDateFormat
import java.util.Date

import android.os.Environment
import consts._

object FileManager {

  /**
    * TODO: Получить каталог приложения
    */
  def getAppDir: File = {
    new File(Environment.getExternalStorageDirectory(), DIR_NAME)
  }

  /**
    * Получить каталог дампов карты
    */
  def getCardDir(cadrId: String): File = {
    val dirTroyka = getAppDir
    if(!dirTroyka.exists()) dirTroyka.mkdir()

    // Корневая директория карты
    val dirCard = new File(dirTroyka, cadrId)
    if(!dirCard.exists()) dirCard.mkdir()
    dirCard
  }

  /**
    * TODO: Создать имя файла в виде 20170521_152012_500RUB.dump
    */
  def createFileName(rub: Int): String = {
    val sdf_t = (new SimpleDateFormat("HHmmss")).format(new Date())
    val sdf_d = (new SimpleDateFormat("yyyyMMdd")).format(new Date())
    s"${File.separator}${sdf_d}_${sdf_t}_${rub}_RUB.dump"
  }

  /**
    * TODO: Создаем имя системного каталога "/mnt/sdcard"
    * @return
    */
  def createRootDirName : String = {
    Environment.getExternalStorageDirectory().toString()
  }

  /**
    *
    * @return
    */
  def createAppDir: String = createRootDirName + File.separator + DIR_NAME

  /**
    *
    * @param f
    * @return
    */
  def createFullPath(f: String) = createAppDir + File.separator + f

}
