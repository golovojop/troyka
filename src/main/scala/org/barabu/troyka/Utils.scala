package org.barabu.troyka

import java.io._
import java.util.Calendar
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.security.MessageDigest

import android.util.Log
import org.barabu.troyka.consts._

import scala.util.{Failure, Success, Try}


object Utils {

  /**
    * Выводим логи в стадии разработки
    */
  def logIt(msg: String) = {
    if(RELEASE_DBG) Log.d(LOG_ID,msg)
  }

  /**
    * TODO: Конвертируем байтовый массив в строку
    * From here: https://gist.github.com/tmyymmt/3727124
    */
  def bytes2hex(bytes: Array[Byte], sep: Option[String] = None): String = {
    sep match {
      case None => bytes.map("%02x".format(_)).mkString
      case _ => bytes.map("%02x".format(_)).mkString(sep.get)
    }
  }

  /**
    * TODO: Конвертируем строки в байтовый массив
    * From here: https://gist.github.com/tmyymmt/3727124
    */
  def hex2bytes(hex: String): Array[Byte] = {
    hex.replaceAll("[^0-9A-Fa-f]", "").sliding(2, 2).toArray.map(Integer.parseInt(_, 16).toByte)
  }

  // TODO: Извлечь из произвольной строки hex-символы
  def extractHex(raw: String): String = {
    val hexAlphabet = "0123456789abcdefABCDEF"

    // TODO: Отфильтровать из строки все элементы кроме hex-цифр
    val result = raw.filter(ch => hexAlphabet.exists(_ == ch))
    result.toUpperCase
  }

  /**
    * TODO: Получить текущие дату и время
    */
  def timeStampCurrent: Timestamp = {
    val timeFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss")

    val nowRaw      = Calendar.getInstance.getTime
    val nowFormated = timeFormat.format(nowRaw)
    val timeStamp   = java.sql.Timestamp.valueOf(nowFormated)
    timeStamp
  }

  /**
    * TODO: Из Timestamp генерим md5 хеш.
    */
  def timeStampDigest(ts: Timestamp): Array[Byte] = {
    MessageDigest.getInstance("MD5").digest(ts.toString.getBytes)
  }

  /**
    * TODO: Альтернативный вариант определения UIDa с помощью команды id
    * https://stackoverflow.com/questions/4905743/android-how-to-gain-root-access-in-an-android-application
    */
  def getUid: String = {

    Try {
      // Выполнить команду id. Здесь не нужно получать su иначе uid будет равен 0
      val p = Runtime.getRuntime().exec("id")
      //           Get String     <-  Get Char    <-      Get Byte
      val is = new BufferedReader(new InputStreamReader(p.getInputStream()))

      val rawUid = is.readLine().split(" ")
      is.close()
      p.waitFor()
      rawUid(0)
    } match {
      case Success(uid) => uid
      case Failure(e) => "uid=?"
    }
  }

}
