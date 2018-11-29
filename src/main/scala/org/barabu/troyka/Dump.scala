package org.barabu.troyka

import java.util.Date
import java.util.Calendar
import java.util.TimeZone
import java.text.SimpleDateFormat
import java.security.MessageDigest
import java.io._

import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.util.Log
import android.os.Environment
import android.content.Intent
import android.content.Context
import android.content.BroadcastReceiver
import android.support.v4.content.LocalBroadcastManager

import scala.util.{Failure, Success, Try}
import scala.io.Source
import scala.math.BigInt
import scala.collection.mutable.ArrayBuffer
import Implicits._
import Utils._
import consts._
import FileManager._

/**
  *
  */
class Dump(val uid: Array[Byte], val data: Array[Array[Byte]], val source: DumpSource) {

  /*                              0  1  2  3  4  5  6  7  8  9  a  b  c  d  e  f
  val extractionMap = Array[Byte](1, 1, 2, 2, 2, 0, 3, 0, 4, 4, 0, 0, 0, 0, 0, 0) */

  val NO_ID               = 0
  val VALIDATOR_ID        = 1
  val LAST_USAGE_ID       = 2
  val TRANSPORT_TYPE_ID   = 3
  val BALANCE_ID          = 4
  val TROYKA_CARD_ID      = 5
  val BLOCK_1_MAP = Array[Byte]  (VALIDATOR_ID, VALIDATOR_ID,
    LAST_USAGE_ID, LAST_USAGE_ID, LAST_USAGE_ID,
    NO_ID,
    TRANSPORT_TYPE_ID,
    NO_ID,
    BALANCE_ID, BALANCE_ID,
    NO_ID, NO_ID, NO_ID, NO_ID, NO_ID, NO_ID)
  val BLOCK_0_MAP = Array[Byte]  (NO_ID, NO_ID,
    TROYKA_CARD_ID, TROYKA_CARD_ID, TROYKA_CARD_ID, TROYKA_CARD_ID, TROYKA_CARD_ID,
    NO_ID, NO_ID, NO_ID, NO_ID, NO_ID, NO_ID, NO_ID, NO_ID, NO_ID)

  logIt("Dump: constructor")

  /**
    *
    * @param rawData
    * @param blockMap
    * @param id
    * @return
    */
  def extractor(rawData: Array[Byte], blockMap: Array[Byte], id: Int): BigInt = {
    val mapped: Array[(Byte, Byte)] = rawData.zip(blockMap)
    val filtered = mapped.filter(a => (a._2 == id))
    val extracted = filtered.unzip._1
    val bi = BigInt(extracted)
    bi
  }

  def getValidatorID: Int = {
    extractor(data(1), BLOCK_1_MAP, VALIDATOR_ID).toInt
  }

  def getLastUsageTime: String = {

    val sdf = new SimpleDateFormat("yyyy.MM.dd HH:mm")

    val MINUTE_MILLS: Long  = 60 * 1000

    // Параметры даты: 2015-12-31 00:00
    def zeroPoint: Calendar = {
      val c = Calendar.getInstance(/*TimeZone.getTimeZone("GMT+3")*/)
      c.set(2015, Calendar.DECEMBER, 31, 0/*00*/, 0/*00*/)
      c
    }

    // Дата в будущем от заданной
    def dateInFuture(dateFrom: Calendar, minutes: Int): Date = {
      val from = dateFrom.getTime()
      val result = new Date(from.getTime() + minutesToMills(minutes))
      result
    }

    def minutesToMills(mt: Int): Long = {
      mt.toLong * MINUTE_MILLS
    }

    val minutes = extractor(data(1), BLOCK_1_MAP, LAST_USAGE_ID).toInt / 2
    val d = dateInFuture(zeroPoint, minutes)
    sdf.format(d)
  }

  def getCadrID: String = {
    val rawData = extractor(data(0), BLOCK_0_MAP, TROYKA_CARD_ID)
    val mask = BigInt(0x0FFFFFFFF0L)
    val rawID = f"${(rawData & mask) >> 4}%010d"  // Строка из 10 цифр

    val (a, b) = rawID.splitAt(4)   // => 1234 567890
    val (d, e) = b.splitAt(3)       // => 1234 567 890
    a + ' ' + d + ' ' + e
  }

  def getTransportType: String = {
    extractor(data(1), BLOCK_1_MAP, TRANSPORT_TYPE_ID).toInt match {
      case 0x14 => "Metro"
      case 0x20 => "Bus"
      case _ => "Unknown"
    }
  }

  def getBalance: Int = {
    extractor(data(1), BLOCK_1_MAP, BALANCE_ID).toInt / 25
  }

  def hashMd5: Array[Byte] = {
    val a = ArrayBuffer[Array[Byte]]()

    a += uid
    for (b <- data) a+= b
    MessageDigest.getInstance("MD5").digest(a.toArray.flatten)
  }

  def info: String = {
    val a = ArrayBuffer[String]()

    a += s"Mifare card UID = ${bytes2hex(uid)}\n\n"

    a += "--- Sector 8 ---\n\n"

    for(ab <- data) a += (bytes2hex(ab) + "\n")

    val cn = "Card number:"
    val lu = "Last usage:"
    val lv = "Validator:"
    val tr = "Transport:"
    val cb = "Balance:"

    // Добавляем пробелы справа
    a += "\n\n"
    a += f"${cn}%-13s${getCadrID}\n"
    a += f"${lu}%-13s${getLastUsageTime}\n"
    a += f"${lv}%-13s${getValidatorID}\n"
    a += f"${tr}%-13s${getTransportType}\n"
    a += f"${cb}%-13s${getBalance} RUB\n"

    a.mkString
  }

  def pack: Array[String] = {
    val a = ArrayBuffer[String]()

    a += bytes2hex(uid)
    for (b <- data) a += bytes2hex(b)
    a += bytes2hex(hashMd5)
    a.toArray
  }
}

/**
  *
  */
object Dump {

  val BLOCK_COUNT = 4
  val BLOCK_SIZE = MifareClassic.BLOCK_SIZE
  val SECTOR_INDEX = 8

  val KEY_A = Array[Byte](0xA7, 0x3F, 0x5D, 0xC1, 0xD3, 0x33)
  val KEY_B = Array[Byte](0xE3, 0x51, 0x73, 0x49, 0x4A, 0x81)
  val KEY_0 = Array[Byte](0x00, 0x00, 0x00, 0x00, 0x00, 0x00)


  /**
    * @param tag
    * @return
    */
  def fromTag(tag: Tag): Try[Dump] = {
    val data = Array.ofDim[Byte](BLOCK_COUNT, BLOCK_SIZE)

    Try {

      getMifareClassic(tag) match {
        case Some(mfc) => {
          for(i <- 0 until BLOCK_COUNT) {
            data(i) = mfc.readBlock(mfc.sectorToBlock(SECTOR_INDEX) + i)
            logIt("fromTag: " + bytes2hex(data(i)) )
          }
          mfc.close()
          apply(tag.getId(), data, SourceTag())
        }
        case None => throw new RuntimeException
      }
    }
  }

  def fromFile(cadrID: String, fn: String): Try[Dump] = {

    Try {
      logIt("Dump.fromFile")

      // Прочитать файл построчно.
      // Hex-строки конвертируем в Byte-массивы
      //val file = Source.fromFile((new File(getCardDir(cadrID), fn)).getAbsolutePath)
      val file = Source.fromFile(new File(getCardDir(cadrID), fn))
      val rawBytes = file.getLines.toArray.map(hex2bytes(_))
      file.close

      // Массив должен состоять из 6 элементов: uid, 4 x blocks, hash
      if (rawBytes.size != 6) throw new RuntimeException("Invalid dump size")

      // Упаковываем uid, 4 x blocks в плоский массив
      val flatBytes = rawBytes.flatten.take(rawBytes(0).size + rawBytes(1).size * 4)

      // Вычисляем его hash Md5
      val hashCheck = MessageDigest.getInstance("MD5").digest(flatBytes)

      // Сверяем хэши
      if (bytes2hex(rawBytes(5)) == bytes2hex(hashCheck))
        apply(rawBytes(0), rawBytes.slice(1, 5), SourceFile())
      else {
        logIt("Dump.fromFile: Invalid hash")
        throw new RuntimeException("Invalid dump data")
      }
    }
  }

  /**
    * https://stackoverflow.com/questions/8152125/how-to-create-text-file-and-insert-data-to-that-file-on-android
    * @param dump
    * @return
    */
  def toFile(dump: Dump): Try[String] = {

    Try{
      // Директория карты
      val dirCard = getCardDir(dump.getCadrID)

      // Полное имя файла
      val file = new File(dirCard, createFileName(dump.getBalance))
      val bw = new BufferedWriter(new FileWriter(file))
      for(s <- dump.pack) bw.write(s + "\n")
      bw.flush; bw.close
      file.getName
    }
  }

  /**
    *
    * @param tag
    * @param dump
    */
  def burn(tag: Tag, dump: Dump): Try[String] = {
    Try {
      if(!tag.getId().sameElements(dump.uid)) throw new RuntimeException("Invalid card number")

      getMifareClassic(tag) match {
        case Some(mfc) => {
          val numBlocksToWrite = BLOCK_COUNT - 1
          val startBlockIndex = mfc.sectorToBlock(SECTOR_INDEX)

          for (i <- 0 until numBlocksToWrite) {
            mfc.writeBlock(startBlockIndex + i, dump.data(i))
          }
          mfc.close()
          s"Card ${dump.getCadrID}\n\nburned successfully"
        }
        case None => throw new RuntimeException("Tag IO error")
      }
    }
  }

  /**
    *
    * @param tag
    * @return
    */
  def getMifareClassic(tag: Tag): Option[MifareClassic] = {

    val mfc = MifareClassic.get(tag)
    mfc.connect()

    if(authenticateSector(mfc, SECTOR_INDEX, KEY_0, KEY_0) || authenticateSector(mfc, SECTOR_INDEX, KEY_A, KEY_B)) {
      Some(mfc)
    }
    else {
      None
    }
  }

  /**
    *
    * @param ix
    * @param keyA
    * @param keyB
    * @return
    */
  def authenticateSector(mfc: MifareClassic, ix: Int, keyA: Array[Byte], keyB: Array[Byte]): Boolean = {
    mfc.authenticateSectorWithKeyA(ix, keyA) && mfc.authenticateSectorWithKeyB(ix, keyB)
  }

  /**
    *
    * @param data
    * @return
    */
  def apply(uid: Array[Byte], data: Array[Array[Byte]], source: DumpSource) = {
    logIt("Object Dump.apply")
    new Dump(uid, data, source)
  }
}
